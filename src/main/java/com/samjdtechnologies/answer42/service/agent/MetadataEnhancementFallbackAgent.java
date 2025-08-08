package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.db.MetadataVerification;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.MetadataVerificationRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Ollama-based fallback agent for metadata enhancement.
 * This agent provides local processing capabilities when cloud providers are unavailable.
 * 
 * Optimized for local Ollama models with simplified metadata enhancement:
 * - Basic keyword extraction suitable for local model processing
 * - Simplified categorization focused on core academic fields
 * - Fallback-specific error handling and user notifications
 * - Content truncation to prevent resource exhaustion
 * 
 * Provides essential metadata enhancement functionality with local processing constraints.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class MetadataEnhancementFallbackAgent extends OllamaBasedAgent {
    
    // Metadata enhancement types optimized for local models
    private static final Map<String, String> LOCAL_ENHANCEMENT_TYPES = Map.of(
        "keywords", "Extract basic keywords and key terms",
        "categories", "Identify primary academic categories",
        "summary_tags", "Generate summary-based tags",
        "full", "Complete metadata enhancement with local constraints"
    );
    
    // Predefined academic categories for local processing
    private static final List<String> BASIC_ACADEMIC_CATEGORIES = List.of(
        "Computer Science", "Mathematics", "Physics", "Chemistry", "Biology",
        "Medicine", "Engineering", "Social Sciences", "Humanities", "Business",
        "Psychology", "Environmental Science", "Materials Science", "Economics"
    );
    
    private final PaperRepository paperRepository;
    private final MetadataVerificationRepository metadataVerificationRepository;
    private final ObjectMapper objectMapper;
    
    public MetadataEnhancementFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                           APIRateLimiter rateLimiter, PaperRepository paperRepository,
                                           MetadataVerificationRepository metadataVerificationRepository) {
        super(aiConfig, threadConfig, rateLimiter);
        this.paperRepository = paperRepository;
        this.metadataVerificationRepository = metadataVerificationRepository;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.METADATA_ENHANCER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing metadata enhancement with Ollama fallback for task %s", task.getId());
        
        try {
            // Extract task input with null safety
            JsonNode input = task.getInput();
            if (input == null) {
                return AgentResult.failure(task.getId(), "FALLBACK: No input data provided");
            }
            
            JsonNode paperIdNode = input.get("paperId");
            String paperId = paperIdNode != null ? paperIdNode.asText() : "unknown";
            
            // Match the original MetadataEnhancementAgent's expected input structure
            JsonNode titleNode = input.get("title");
            String title = titleNode != null ? titleNode.asText() : "";
            
            JsonNode doiNode = input.get("doi");
            String doi = doiNode != null ? doiNode.asText() : "";
            
            JsonNode authorsNode = input.get("authors");
            String authors = "";
            if (authorsNode != null) {
                if (authorsNode.isArray()) {
                    List<String> authorList = new ArrayList<>();
                    authorsNode.forEach(node -> {
                        if (node.isTextual()) {
                            authorList.add(node.asText());
                        }
                    });
                    authors = String.join(", ", authorList);
                } else if (authorsNode.isTextual()) {
                    authors = authorsNode.asText();
                }
            }
            
            // Set default enhancement type for fallback processing
            String enhancementType = "full";
            
            if (title.isEmpty()) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: No title provided for metadata enhancement");
            }
            
            // Validate enhancement type
            if (!LOCAL_ENHANCEMENT_TYPES.containsKey(enhancementType.toLowerCase())) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Unknown enhancement type %s for fallback, defaulting to full", enhancementType);
                enhancementType = "full";
            }
            
            // Validate content is suitable for local processing
            if (!isContentSuitableForLocalProcessing(title, doi, authors)) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: Content not sufficient for local metadata processing");
            }
            
            // Generate metadata using local model
            Map<String, Object> metadata = performLocalMetadataEnhancement(
                paperId, title, doi, authors, enhancementType);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Metadata Enhancement");
            metadata.put("processingNote", fallbackNote);
            
            // Save metadata verification to database
            if (!paperId.equals("unknown")) {
                saveMetadataVerification(paperId, title, doi, authors, metadata, enhancementType);
            }
            
            // Create result data with fallback indicators
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("enhancementType", enhancementType);
            resultData.put("metadata", metadata);
            resultData.put("fallbackUsed", true);
            resultData.put("fallbackProvider", "OLLAMA");
            resultData.put("primaryFailureReason", "Cloud providers temporarily unavailable");
            
            return AgentResult.success(task.getId(), resultData);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Ollama fallback processing failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), handleLocalProcessingError(e, task.getId()));
        }
    }
    
    /**
     * Performs local metadata enhancement using Ollama.
     * 
     * @param paperId The paper ID for logging
     * @param title The paper title
     * @param doi The paper DOI
     * @param authors The paper authors
     * @param enhancementType The type of enhancement to perform
     * @return Enhanced metadata
     */
    private Map<String, Object> performLocalMetadataEnhancement(String paperId, String title, 
                                                              String doi, String authors, 
                                                              String enhancementType) {
        LoggingUtil.info(LOG, "performLocalMetadataEnhancement", 
            "Generating %s metadata enhancement using Ollama for paper %s", 
            enhancementType, paperId);
        
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            switch (enhancementType.toLowerCase()) {
                case "keywords" -> {
                    List<String> keywords = extractKeywords(title, doi, authors);
                    metadata.put("keywords", keywords);
                }
                case "categories" -> {
                    List<String> categories = identifyCategories(title, doi, authors);
                    metadata.put("categories", categories);
                }
                case "summary_tags" -> {
                    List<String> tags = generateSummaryTags(title, doi, authors);
                    metadata.put("summaryTags", tags);
                }
                default -> {
                    // Full enhancement
                    metadata.put("keywords", extractKeywords(title, doi, authors));
                    metadata.put("categories", identifyCategories(title, doi, authors));
                    metadata.put("summaryTags", generateSummaryTags(title, doi, authors));
                    metadata.put("readabilityScore", assessReadability(title, doi, authors));
                    metadata.put("technicalLevel", assessTechnicalLevel(title, doi, authors));
                }
            }
            
            // Add processing metadata
            metadata.put("enhancementQuality", "local_processing");
            metadata.put("processingMethod", "ollama_fallback");
            
            return metadata;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalMetadataEnhancement", 
                "Local metadata enhancement failed for paper %s", e, paperId);
            return createLocalFallbackMetadata(enhancementType, e.getMessage());
        }
    }
    
    /**
     * Extracts keywords using local processing.
     */
    private List<String> extractKeywords(String title, String doi, String authors) {
        // Combine all text for processing
        String combinedText = combineTextForProcessing(title, doi, authors);
        
        // Create simplified prompt for keyword extraction
        String promptText = String.format(
            "Extract 5-8 important keywords from this academic text:\n\n%s\n\n" +
            "Return only the keywords, one per line. Focus on technical terms and main concepts.",
            truncateForLocalProcessing(combinedText, MAX_LOCAL_CONTENT_LENGTH / 2)
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String keywordResponse = response.getResult().getOutput().getText();
            
            return parseKeywordsFromResponse(keywordResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractKeywords", 
                "Keyword extraction failed, using fallback method: %s", e.getMessage());
            return extractKeywordsFallback(title, doi, authors);
        }
    }
    
    /**
     * Identifies academic categories using local processing.
     */
    private List<String> identifyCategories(String title, String doi, String authors) {
        String combinedText = combineTextForProcessing(title, doi, authors);
        
        String promptText = String.format(
            "Identify 1-3 academic categories for this research paper:\n\n%s\n\n" +
            "Choose from these categories: %s\n" +
            "Return only the category names, one per line.",
            truncateForLocalProcessing(combinedText, MAX_LOCAL_CONTENT_LENGTH / 2),
            String.join(", ", BASIC_ACADEMIC_CATEGORIES)
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String categoryResponse = response.getResult().getOutput().getText();
            
            return parseCategoriesFromResponse(categoryResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "identifyCategories", 
                "Category identification failed, using fallback method: %s", e.getMessage());
            return identifyCategoriesFallback(title, doi, authors);
        }
    }
    
    /**
     * Generates summary tags using local processing.
     */
    private List<String> generateSummaryTags(String title, String doi, String authors) {
        String combinedText = combineTextForProcessing(title, doi, authors);
        
        String promptText = String.format(
            "Generate 4-6 descriptive tags for this academic paper:\n\n%s\n\n" +
            "Tags should describe the research method, topic, or approach. " +
            "Return only the tags, one per line. Keep tags concise (1-3 words each).",
            truncateForLocalProcessing(combinedText, MAX_LOCAL_CONTENT_LENGTH / 2)
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String tagResponse = response.getResult().getOutput().getText();
            
            return parseTagsFromResponse(tagResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "generateSummaryTags", 
                "Tag generation failed, using fallback method: %s", e.getMessage());
            return generateTagsFallback(title, doi, authors);
        }
    }
    
    /**
     * Assesses readability for local processing.
     */
    private double assessReadability(String title, String doi, String authors) {
        // Simple readability assessment based on text characteristics
        String combinedText = combineTextForProcessing(title, doi, authors);
        
        if (combinedText.isEmpty()) {
            return 5.0; // Neutral score
        }
        
        // Basic metrics
        int wordCount = combinedText.split("\\s+").length;
        int sentenceCount = combinedText.split("[.!?]+").length;
        int complexWords = countComplexWords(combinedText);
        
        // Simple readability score (1-10 scale)
        double avgWordsPerSentence = wordCount > 0 ? (double) wordCount / Math.max(sentenceCount, 1) : 0;
        double complexWordRatio = wordCount > 0 ? (double) complexWords / wordCount : 0;
        
        double readabilityScore = 8.0 - (avgWordsPerSentence / 5.0) - (complexWordRatio * 3.0);
        return Math.max(1.0, Math.min(10.0, readabilityScore));
    }
    
    /**
     * Assesses technical level for local processing.
     */
    private String assessTechnicalLevel(String title, String doi, String authors) {
        String combinedText = combineTextForProcessing(title, doi, authors);
        
        // Simple technical level assessment based on keywords
        String lowerText = combinedText.toLowerCase();
        int technicalIndicators = 0;
        
        // Check for technical terms
        String[] highTechTerms = {"algorithm", "neural", "machine learning", "statistical", "computational", 
                                 "optimization", "methodology", "analysis", "framework", "model"};
        
        for (String term : highTechTerms) {
            if (lowerText.contains(term)) {
                technicalIndicators++;
            }
        }
        
        if (technicalIndicators >= 5) {
            return "high";
        } else if (technicalIndicators >= 2) {
            return "medium";
        } else {
            return "low";
        }
    }
    
    /**
     * Combines text sources for processing with priority.
     */
    private String combineTextForProcessing(String title, String doi, String authors) {
        StringBuilder combined = new StringBuilder();
        
        if (!title.isEmpty()) {
            combined.append("Title: ").append(title).append("\n\n");
        }
        
        if (!doi.isEmpty()) {
            combined.append("DOI: ").append(doi).append("\n\n");
        }
        
        if (!authors.isEmpty()) {
            combined.append("Authors: ").append(authors).append("\n\n");
        }
        
        return combined.toString();
    }
    
    /**
     * Parses keywords from model response.
     */
    private List<String> parseKeywordsFromResponse(String response) {
        List<String> keywords = new ArrayList<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.trim().split("\\n");
            for (String line : lines) {
                String keyword = line.trim().replaceAll("^[-*•]\\s*", "");
                if (!keyword.isEmpty() && keyword.length() <= 50) {
                    keywords.add(keyword);
                }
                if (keywords.size() >= 8) break; // Limit for local processing
            }
        }
        
        return keywords.isEmpty() ? List.of("academic research", "analysis") : keywords;
    }
    
    /**
     * Parses categories from model response.
     */
    private List<String> parseCategoriesFromResponse(String response) {
        List<String> categories = new ArrayList<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.trim().split("\\n");
            for (String line : lines) {
                String category = line.trim().replaceAll("^[-*•]\\s*", "");
                if (BASIC_ACADEMIC_CATEGORIES.contains(category)) {
                    categories.add(category);
                }
                if (categories.size() >= 3) break; // Limit for local processing
            }
        }
        
        return categories.isEmpty() ? List.of("Computer Science") : categories;
    }
    
    /**
     * Parses tags from model response.
     */
    private List<String> parseTagsFromResponse(String response) {
        List<String> tags = new ArrayList<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.trim().split("\\n");
            for (String line : lines) {
                String tag = line.trim().replaceAll("^[-*•]\\s*", "");
                if (!tag.isEmpty() && tag.length() <= 30) {
                    tags.add(tag);
                }
                if (tags.size() >= 6) break; // Limit for local processing
            }
        }
        
        return tags.isEmpty() ? List.of("research", "academic", "analysis") : tags;
    }
    
    /**
     * Fallback keyword extraction using simple text analysis.
     */
    private List<String> extractKeywordsFallback(String title, String abstractText, String content) {
        List<String> keywords = new ArrayList<>();
        
        // Extract from title first
        if (!title.isEmpty()) {
            String[] titleWords = title.toLowerCase().split("\\s+");
            for (String word : titleWords) {
                if (word.length() > 4 && !isCommonWord(word)) {
                    keywords.add(word);
                    if (keywords.size() >= 4) break;
                }
            }
        }
        
        // Add generic research keywords
        keywords.add("academic research");
        keywords.add("analysis");
        
        return keywords;
    }
    
    /**
     * Fallback category identification using simple text matching.
     */
    private List<String> identifyCategoriesFallback(String title, String abstractText, String content) {
        String combinedText = (title + " " + abstractText).toLowerCase();
        
        for (String category : BASIC_ACADEMIC_CATEGORIES) {
            String categoryLower = category.toLowerCase();
            if (combinedText.contains(categoryLower) || 
                combinedText.contains(categoryLower.replace(" ", ""))) {
                return List.of(category);
            }
        }
        
        return List.of("Computer Science"); // Default fallback
    }
    
    /**
     * Fallback tag generation using simple analysis.
     */
    private List<String> generateTagsFallback(String title, String abstractText, String content) {
        return List.of("research", "academic", "analysis", "study");
    }
    
    /**
     * Creates fallback metadata when processing fails.
     */
    private Map<String, Object> createLocalFallbackMetadata(String enhancementType, String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackMetadata", 
            "Creating fallback metadata due to: %s", errorReason);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("keywords", List.of("local processing", "fallback", "academic"));
        metadata.put("categories", List.of("Computer Science"));
        metadata.put("summaryTags", List.of("research", "local"));
        metadata.put("readabilityScore", 5.0);
        metadata.put("technicalLevel", "medium");
        metadata.put("enhancementQuality", "fallback_only");
        metadata.put("processingMethod", "local_fallback");
        metadata.put("processingNote", "Local fallback metadata - " + errorReason);
        
        return metadata;
    }
    
    /**
     * Validates that content is suitable for local processing.
     */
    private boolean isContentSuitableForLocalProcessing(String title, String abstractText, String content) {
        int totalLength = title.length() + abstractText.length() + content.length();
        
        if (totalLength == 0) {
            return false;
        }
        
        // Check for extremely large content
        if (totalLength > MAX_LOCAL_CONTENT_LENGTH * 2) {
            LoggingUtil.warn(LOG, "isContentSuitableForLocalProcessing", 
                "Total content length %d exceeds local processing limits", totalLength);
            // Still allow processing with truncation
        }
        
        return true;
    }
    
    /**
     * Counts complex words for readability assessment.
     */
    private int countComplexWords(String text) {
        String[] words = text.toLowerCase().split("\\s+");
        int complexCount = 0;
        
        for (String word : words) {
            if (word.length() > 6 || word.matches(".*[aeiou].*[aeiou].*[aeiou].*")) {
                complexCount++;
            }
        }
        
        return complexCount;
    }
    
    /**
     * Checks if a word is a common word that shouldn't be a keyword.
     */
    private boolean isCommonWord(String word) {
        String[] commonWords = {"this", "that", "with", "have", "will", "from", "they", "know", 
                               "want", "been", "good", "much", "some", "time", "very", "when", 
                               "come", "here", "just", "like", "long", "make", "many", "over", "such"};
        
        for (String commonWord : commonWords) {
            if (word.equals(commonWord)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input != null && input.has("enhancementType")) {
            String enhancementType = input.get("enhancementType").asText();
            
            // Local processing is generally faster but less sophisticated
            long baseSeconds = switch (enhancementType.toLowerCase()) {
                case "keywords" -> 15;        // Fast for local
                case "categories" -> 10;      // Very fast for local
                case "summary_tags" -> 20;    // Moderate for local
                default -> 45;                // Full enhancement
            };
            
            return Duration.ofSeconds(baseSeconds);
        }
        
        return Duration.ofSeconds(30); // Conservative estimate for local processing
    }
    
    /**
     * Saves metadata verification record to the database.
     * 
     * @param paperId The paper ID
     * @param title The paper title
     * @param doi The paper DOI
     * @param authors The paper authors
     * @param metadata The enhanced metadata
     * @param enhancementType The type of enhancement performed
     */
    @Transactional
    private void saveMetadataVerification(String paperId, String title, String doi, 
                                        String authors, Map<String, Object> metadata, 
                                        String enhancementType) {
        try {
            // Parse paper ID to UUID
            UUID paperUuid;
            try {
                paperUuid = UUID.fromString(paperId);
            } catch (IllegalArgumentException e) {
                LoggingUtil.warn(LOG, "saveMetadataVerification", 
                    "Invalid paper UUID format: %s", paperId);
                return;
            }
            
            // Check if paper exists
            Optional<Paper> paperOpt = paperRepository.findById(paperUuid);
            if (paperOpt.isEmpty()) {
                LoggingUtil.warn(LOG, "saveMetadataVerification", 
                    "Paper not found for UUID: %s", paperId);
                return;
            }
            
            Paper paper = paperOpt.get();
            
            // Create metadata verification record using actual database schema
            MetadataVerification verification = new MetadataVerification();
            verification.setPaper(paper);  // paper_id foreign key
            verification.setSource("OLLAMA_FALLBACK");  // source column
            verification.setConfidence(0.7);  // confidence score for local processing
            verification.setVerifiedAt(Instant.now());  // verified_at timestamp
            verification.setMatchedBy("title_doi_match");  // matched_by identifier
            verification.setIdentifierUsed(doi.isEmpty() ? title : doi);  // identifier_used
            
            // Create comprehensive metadata JSONB object
            Map<String, Object> metadataJson = new HashMap<>();
            metadataJson.put("originalTitle", title);
            metadataJson.put("originalDoi", doi);
            metadataJson.put("originalAuthors", authors);
            metadataJson.put("enhancementType", enhancementType);
            metadataJson.put("fallbackUsed", true);
            metadataJson.put("fallbackReason", "Cloud providers unavailable - using Ollama fallback");
            metadataJson.put("processingNotes", "Local metadata enhancement using " + enhancementType + " mode");
            
            // Add all enhanced metadata
            if (metadata.containsKey("keywords")) {
                metadataJson.put("extractedKeywords", metadata.get("keywords"));
            }
            
            if (metadata.containsKey("categories")) {
                metadataJson.put("extractedCategories", metadata.get("categories"));
            }
            
            if (metadata.containsKey("summaryTags")) {
                metadataJson.put("extractedTags", metadata.get("summaryTags"));
            }
            
            if (metadata.containsKey("readabilityScore")) {
                metadataJson.put("readabilityScore", metadata.get("readabilityScore"));
            }
            
            if (metadata.containsKey("technicalLevel")) {
                metadataJson.put("technicalLevel", metadata.get("technicalLevel"));
            }
            
            // Add processing metadata
            metadataJson.put("enhancementQuality", metadata.get("enhancementQuality"));
            metadataJson.put("processingMethod", metadata.get("processingMethod"));
            
            // Convert Map to JsonNode for JSONB column
            JsonNode metadataJsonNode = objectMapper.valueToTree(metadataJson);
            verification.setMetadata(metadataJsonNode);  // metadata JSONB column
            
            // Save to database
            metadataVerificationRepository.save(verification);
            
            LoggingUtil.info(LOG, "saveMetadataVerification", 
                "Saved metadata verification record for paper %s with enhancement type %s", 
                paperId, enhancementType);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveMetadataVerification", 
                "Failed to save metadata verification for paper %s", e, paperId);
        }
    }
    
    /**
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for metadata enhancement. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses simplified analysis optimized for local models with basic enhancement capabilities.";
    }
}

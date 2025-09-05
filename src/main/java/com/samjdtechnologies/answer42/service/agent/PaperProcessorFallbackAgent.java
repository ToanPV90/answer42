package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
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
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.db.PaperContent;
import com.samjdtechnologies.answer42.model.db.PaperSection;
import com.samjdtechnologies.answer42.model.db.PaperTag;
import com.samjdtechnologies.answer42.model.db.Tag;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.PaperContentRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.repository.PaperSectionRepository;
import com.samjdtechnologies.answer42.repository.PaperTagRepository;
import com.samjdtechnologies.answer42.repository.TagRepository;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Ollama-based fallback agent for paper processing.
 * This agent provides local processing capabilities when cloud providers are unavailable.
 * 
 * Optimized for local Ollama models with simplified paper processing:
 * - Basic structural analysis suitable for local model processing
 * - Simplified content extraction focused on core elements
 * - Fallback-specific error handling and user notifications
 * - Content truncation to prevent resource exhaustion
 * 
 * Provides essential paper processing functionality with local processing constraints.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class PaperProcessorFallbackAgent extends OllamaBasedAgent {
    
    // Processing modes optimized for local models
    private static final Map<String, String> LOCAL_PROCESSING_MODES = Map.of(
        "basic", "Extract title, authors, and abstract only",
        "standard", "Extract core metadata and basic content structure",
        "detailed", "Extract comprehensive metadata with content analysis",
        "full", "Complete paper processing with local constraints"
    );
    
    private final PaperRepository paperRepository;
    private final PaperContentRepository paperContentRepository;
    private final PaperSectionRepository paperSectionRepository;
    private final TagRepository tagRepository;
    private final PaperTagRepository paperTagRepository;
    
    public PaperProcessorFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                      APIRateLimiter rateLimiter,
                                      PaperRepository paperRepository,
                                      PaperContentRepository paperContentRepository,
                                      PaperSectionRepository paperSectionRepository,
                                      TagRepository tagRepository,
                                      PaperTagRepository paperTagRepository) {
        super(aiConfig, threadConfig, rateLimiter);
        this.paperRepository = paperRepository;
        this.paperContentRepository = paperContentRepository;
        this.paperSectionRepository = paperSectionRepository;
        this.tagRepository = tagRepository;
        this.paperTagRepository = paperTagRepository;
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.PAPER_PROCESSOR;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing paper with Ollama fallback for task %s", task.getId());
        
        try {
            // Extract task input with null safety
            JsonNode input = task.getInput();
            if (input == null) {
                return AgentResult.failure(task.getId(), "FALLBACK: No input data provided");
            }
            
            JsonNode paperIdNode = input.get("paperId");
            String paperId = paperIdNode != null ? paperIdNode.asText() : "unknown";
            
            // Handle both field names - tasklet uses "textContent", fallback agent expects "rawContent"
            JsonNode rawContentNode = input.get("rawContent");
            if (rawContentNode == null) {
                rawContentNode = input.get("textContent");
            }
            String rawContent = rawContentNode != null ? rawContentNode.asText() : "";
            
            JsonNode processingModeNode = input.get("processingMode");
            String processingMode = processingModeNode != null ? processingModeNode.asText() : "standard";
            
            if (rawContent.isEmpty()) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: No content provided for paper processing");
            }
            
            // Validate processing mode
            if (!LOCAL_PROCESSING_MODES.containsKey(processingMode.toLowerCase())) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Unknown processing mode %s for fallback, defaulting to standard", processingMode);
                processingMode = "standard";
            }
            
            // Validate content is suitable for local processing
            if (!isPaperContentSuitableForLocalProcessing(rawContent)) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: Paper too complex for local processing");
            }
            
            // Process paper using local model
            Map<String, Object> processedData = performLocalPaperProcessing(
                paperId, rawContent, processingMode);
            
            // **CRITICAL: Save processed data to database tables (matching original PaperProcessorAgent)**
            saveFallbackResultsToDatabase(paperId, processedData, rawContent);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Paper Processing");
            processedData.put("processingNote", fallbackNote);
            
            // Create result data with fallback indicators (matching original agent structure)
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("extractedText", processedData.get("cleanedText"));
            resultData.put("structure", processedData.get("structure"));
            resultData.put("sections", processedData.get("sections"));
            resultData.put("metadata", processedData.get("metadata"));
            resultData.put("processingNotes", processedData.get("processingNote"));
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
     * Performs local paper processing using Ollama.
     * 
     * @param paperId The paper ID for logging
     * @param rawContent The raw paper content
     * @param processingMode The processing mode to use
     * @return Processed paper data
     */
    private Map<String, Object> performLocalPaperProcessing(String paperId, String rawContent, 
                                                           String processingMode) {
        LoggingUtil.info(LOG, "performLocalPaperProcessing", 
            "Processing paper %s with mode %s using Ollama", paperId, processingMode);
        
        Map<String, Object> processedData = new HashMap<>();
        
        try {
            switch (processingMode.toLowerCase()) {
                case "basic" -> {
                    processedData.putAll(extractBasicMetadata(rawContent));
                }
                case "detailed" -> {
                    processedData.putAll(extractBasicMetadata(rawContent));
                    processedData.putAll(extractContentStructure(rawContent));
                    processedData.putAll(extractKeyFindings(rawContent));
                }
                case "full" -> {
                    processedData.putAll(extractBasicMetadata(rawContent));
                    processedData.putAll(extractContentStructure(rawContent));
                    processedData.putAll(extractKeyFindings(rawContent));
                    processedData.putAll(performBasicAnalysis(rawContent));
                }
                default -> { // standard
                    processedData.putAll(extractBasicMetadata(rawContent));
                    processedData.putAll(extractContentStructure(rawContent));
                }
            }
            
            // Add processing metadata
            processedData.put("processingQuality", "local_processing");
            processedData.put("processingMethod", "ollama_fallback");
            processedData.put("contentLength", rawContent.length());
            processedData.put("truncated", rawContent.length() > MAX_LOCAL_CONTENT_LENGTH);
            
            // Add cleaned text and structure for database operations
            processedData.put("cleanedText", cleanTextFallback(rawContent));
            processedData.put("structure", createBasicStructure(processedData));
            processedData.put("metadata", createProcessingMetadata(processedData));
            
            return processedData;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalPaperProcessing", 
                "Local paper processing failed for paper %s", e, paperId);
            return createLocalFallbackProcessedData(processingMode, e.getMessage());
        }
    }
    
    /**
     * Extracts basic metadata from paper content.
     */
    private Map<String, Object> extractBasicMetadata(String content) {
        String processableContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH / 2);
        
        String promptText = String.format(
            "Extract the following information from this academic paper:\n\n%s\n\n" +
            "Please provide:\n" +
            "1. Title:\n" +
            "2. Authors:\n" +
            "3. Abstract:\n" +
            "4. Publication Year:\n" +
            "5. Journal/Conference:\n\n" +
            "Format each item clearly on separate lines.",
            processableContent
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String metadataResponse = response.getResult().getOutput().getText();
            
            return parseBasicMetadata(metadataResponse, content);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractBasicMetadata", 
                "Basic metadata extraction failed, using fallback method: %s", e.getMessage());
            return extractBasicMetadataFallback(content);
        }
    }
    
    /**
     * Extracts content structure from paper.
     */
    private Map<String, Object> extractContentStructure(String content) {
        String processableContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH / 3);
        
        String promptText = String.format(
            "Analyze the structure of this academic paper and identify main sections:\n\n%s\n\n" +
            "List the main sections in order (e.g., Introduction, Methods, Results, Discussion, Conclusion). " +
            "One section per line, just the section names.",
            processableContent
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String structureResponse = response.getResult().getOutput().getText();
            
            return parseContentStructure(structureResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractContentStructure", 
                "Content structure extraction failed, using fallback method: %s", e.getMessage());
            return extractContentStructureFallback(content);
        }
    }
    
    /**
     * Extracts key findings from paper.
     */
    private Map<String, Object> extractKeyFindings(String content) {
        String processableContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH / 3);
        
        String promptText = String.format(
            "Identify 3-5 key findings or contributions from this academic paper:\n\n%s\n\n" +
            "List the main findings, one per line. Be concise and focus on the most important results or contributions.",
            processableContent
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String findingsResponse = response.getResult().getOutput().getText();
            
            return parseKeyFindings(findingsResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractKeyFindings", 
                "Key findings extraction failed, using fallback method: %s", e.getMessage());
            return extractKeyFindingsFallback(content);
        }
    }
    
    /**
     * Performs basic analysis of paper content.
     */
    private Map<String, Object> performBasicAnalysis(String content) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Simple analysis metrics
        String[] words = content.split("\\s+");
        String[] sentences = content.split("[.!?]+");
        
        analysis.put("wordCount", words.length);
        analysis.put("sentenceCount", sentences.length);
        analysis.put("averageWordsPerSentence", words.length / Math.max(sentences.length, 1));
        
        // Estimate complexity
        String complexity = estimateComplexity(content);
        analysis.put("complexity", complexity);
        
        // Estimate research domain
        String domain = estimateResearchDomain(content);
        analysis.put("researchDomain", domain);
        
        return analysis;
    }
    
    /**
     * Parses basic metadata from model response.
     */
    private Map<String, Object> parseBasicMetadata(String response, String originalContent) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.trim().split("\\n");
            
            for (String line : lines) {
                if (line.toLowerCase().startsWith("title:")) {
                    metadata.put("title", line.substring("title:".length()).trim());
                } else if (line.toLowerCase().startsWith("authors:")) {
                    metadata.put("authors", line.substring("authors:".length()).trim());
                } else if (line.toLowerCase().startsWith("abstract:")) {
                    metadata.put("abstract", line.substring("abstract:".length()).trim());
                } else if (line.toLowerCase().startsWith("publication year:")) {
                    metadata.put("publicationYear", line.substring("publication year:".length()).trim());
                } else if (line.toLowerCase().startsWith("journal")) {
                    metadata.put("venue", line.substring(line.indexOf(":") + 1).trim());
                }
            }
        }
        
        // Fill in missing data with fallback extraction
        if (!metadata.containsKey("title")) {
            metadata.put("title", extractTitleFallback(originalContent));
        }
        if (!metadata.containsKey("authors")) {
            metadata.put("authors", "Authors not identified");
        }
        if (!metadata.containsKey("abstract")) {
            metadata.put("abstract", extractAbstractFallback(originalContent));
        }
        
        return metadata;
    }
    
    /**
     * Parses content structure from model response.
     */
    private Map<String, Object> parseContentStructure(String response) {
        Map<String, Object> structure = new HashMap<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] sections = response.trim().split("\\n");
            java.util.List<String> sectionList = new java.util.ArrayList<>();
            
            for (String section : sections) {
                String cleanSection = section.trim().replaceAll("^[-*•]\\s*", "");
                if (!cleanSection.isEmpty() && cleanSection.length() <= 50) {
                    sectionList.add(cleanSection);
                }
                if (sectionList.size() >= 10) break; // Limit for local processing
            }
            
            structure.put("sections", sectionList);
            structure.put("sectionCount", sectionList.size());
        } else {
            structure.put("sections", java.util.List.of("Introduction", "Methods", "Results", "Discussion", "Conclusion"));
            structure.put("sectionCount", 5);
        }
        
        return structure;
    }
    
    /**
     * Parses key findings from model response.
     */
    private Map<String, Object> parseKeyFindings(String response) {
        Map<String, Object> findings = new HashMap<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.trim().split("\\n");
            java.util.List<String> findingsList = new java.util.ArrayList<>();
            
            for (String line : lines) {
                String finding = line.trim().replaceAll("^[-*•]\\s*", "");
                if (!finding.isEmpty() && finding.length() <= 200) {
                    findingsList.add(finding);
                }
                if (findingsList.size() >= 5) break; // Limit for local processing
            }
            
            findings.put("keyFindings", findingsList);
            findings.put("findingsCount", findingsList.size());
        } else {
            findings.put("keyFindings", java.util.List.of("Key findings require detailed analysis"));
            findings.put("findingsCount", 1);
        }
        
        return findings;
    }
    
    /**
     * Fallback metadata extraction using simple text analysis.
     */
    private Map<String, Object> extractBasicMetadataFallback(String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", extractTitleFallback(content));
        metadata.put("authors", "Authors not identified");
        metadata.put("abstract", extractAbstractFallback(content));
        metadata.put("publicationYear", "Year not identified");
        metadata.put("venue", "Venue not identified");
        return metadata;
    }
    
    /**
     * Extracts title using simple heuristics.
     */
    private String extractTitleFallback(String content) {
        String[] lines = content.split("\\n");
        
        // Look for title in first few lines
        for (int i = 0; i < Math.min(10, lines.length); i++) {
            String line = lines[i].trim();
            if (line.length() > 10 && line.length() < 200 && 
                !line.toLowerCase().contains("abstract") &&
                !line.toLowerCase().contains("introduction") &&
                !line.matches(".*\\d{4}.*")) { // Avoid lines with years
                return line;
            }
        }
        
        return "Title not identified";
    }
    
    /**
     * Extracts abstract using simple heuristics.
     */
    private String extractAbstractFallback(String content) {
        String lowerContent = content.toLowerCase();
        int abstractStart = lowerContent.indexOf("abstract");
        
        if (abstractStart != -1) {
            int nextSection = lowerContent.indexOf("introduction", abstractStart);
            if (nextSection == -1) {
                nextSection = lowerContent.indexOf("keywords", abstractStart);
            }
            if (nextSection == -1) {
                nextSection = Math.min(abstractStart + 1000, content.length());
            }
            
            String abstractText = content.substring(abstractStart, nextSection).trim();
            // Remove "abstract" prefix
            abstractText = abstractText.replaceFirst("(?i)abstract\\s*:?\\s*", "");
            
            return abstractText.length() > 50 ? abstractText : "Abstract not identified";
        }
        
        return "Abstract not identified";
    }
    
    /**
     * Fallback content structure extraction.
     */
    private Map<String, Object> extractContentStructureFallback(String content) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("sections", java.util.List.of("Introduction", "Methods", "Results", "Discussion", "Conclusion"));
        structure.put("sectionCount", 5);
        return structure;
    }
    
    /**
     * Fallback key findings extraction.
     */
    private Map<String, Object> extractKeyFindingsFallback(String content) {
        Map<String, Object> findings = new HashMap<>();
        findings.put("keyFindings", java.util.List.of("Detailed analysis required for key findings extraction"));
        findings.put("findingsCount", 1);
        return findings;
    }
    
    /**
     * Estimates paper complexity based on content characteristics.
     */
    private String estimateComplexity(String content) {
        String lowerContent = content.toLowerCase();
        int technicalTerms = 0;
        
        String[] complexTerms = {"algorithm", "methodology", "statistical", "experimental", 
                                "theoretical", "computational", "analysis", "framework"};
        
        for (String term : complexTerms) {
            if (lowerContent.contains(term)) {
                technicalTerms++;
            }
        }
        
        if (technicalTerms >= 5) {
            return "high";
        } else if (technicalTerms >= 2) {
            return "medium";
        } else {
            return "low";
        }
    }
    
    /**
     * Estimates research domain based on content.
     */
    private String estimateResearchDomain(String content) {
        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("computer") || lowerContent.contains("algorithm") || 
            lowerContent.contains("software") || lowerContent.contains("data")) {
            return "Computer Science";
        } else if (lowerContent.contains("medical") || lowerContent.contains("clinical") || 
                  lowerContent.contains("patient") || lowerContent.contains("disease")) {
            return "Medicine";
        } else if (lowerContent.contains("neural") || lowerContent.contains("brain") || 
                  lowerContent.contains("cognitive") || lowerContent.contains("psychology")) {
            return "Neuroscience/Psychology";
        } else if (lowerContent.contains("engineering") || lowerContent.contains("design") || 
                  lowerContent.contains("system") || lowerContent.contains("control")) {
            return "Engineering";
        } else {
            return "General Science";
        }
    }
    
    /**
     * Creates fallback processed data when processing fails.
     */
    private Map<String, Object> createLocalFallbackProcessedData(String processingMode, String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackProcessedData", 
            "Creating fallback processed data due to: %s", errorReason);
        
        Map<String, Object> fallbackData = new HashMap<>();
        fallbackData.put("title", "Paper processing incomplete - local fallback");
        fallbackData.put("authors", "Processing failed");
        fallbackData.put("abstract", "Paper requires detailed analysis - fallback processing used");
        fallbackData.put("sections", java.util.List.of("Content analysis pending"));
        fallbackData.put("keyFindings", java.util.List.of("Analysis requires cloud provider"));
        fallbackData.put("processingQuality", "fallback_only");
        fallbackData.put("processingMethod", "local_fallback");
        fallbackData.put("processingNote", "Local fallback processing - " + errorReason);
        
        return fallbackData;
    }
    
    /**
     * Validates that paper content is suitable for local processing.
     */
    private boolean isPaperContentSuitableForLocalProcessing(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check for extremely large papers
        if (content.length() > MAX_LOCAL_CONTENT_LENGTH * 3) {
            LoggingUtil.warn(LOG, "isPaperContentSuitableForLocalProcessing", 
                "Paper content length %d exceeds local processing limits", content.length());
            // Still allow processing with truncation
        }
        
        // Check for minimum content length
        if (content.length() < 500) {
            LoggingUtil.warn(LOG, "isPaperContentSuitableForLocalProcessing", 
                "Paper content too short for meaningful processing: %d characters", content.length());
            return false;
        }
        
        return true;
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input != null && input.has("processingMode")) {
            String processingMode = input.get("processingMode").asText();
            
            // Local processing is generally faster but less sophisticated
            long baseSeconds = switch (processingMode.toLowerCase()) {
                case "basic" -> 20;     // Fast for local
                case "detailed" -> 60;  // Moderate for local
                case "full" -> 90;      // Still reasonable for local
                default -> 45;          // Standard processing
            };
            
            return Duration.ofSeconds(baseSeconds);
        }
        
        return Duration.ofSeconds(45); // Conservative estimate for local processing
    }
    
    /**
     * Cleans and normalizes text content (fallback version).
     */
    private String cleanTextFallback(String text) {
        if (text == null) return "";
        
        return text.trim()
            .replaceAll("\\s+", " ")           // Collapse multiple spaces
            .replaceAll("\\n{3,}", "\n\n")     // Collapse multiple newlines
            .replaceAll("\\r", "")             // Remove carriage returns
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""); // Remove control chars
    }
    
    /**
     * Creates basic structure data for database storage.
     */
    private Map<String, Object> createBasicStructure(Map<String, Object> processedData) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("type", "fallback_processed");
        structure.put("processingMode", "ollama_fallback");
        structure.put("qualityLevel", "local_processing");
        
        // Extract structure indicators from processed data
        @SuppressWarnings("unchecked")
        List<String> sections = (List<String>) processedData.get("sections");
        if (sections != null) {
            structure.put("sectionCount", sections.size());
            structure.put("hasIntroduction", sections.stream().anyMatch(s -> s.toLowerCase().contains("introduction")));
            structure.put("hasConclusion", sections.stream().anyMatch(s -> s.toLowerCase().contains("conclusion")));
            structure.put("hasMethods", sections.stream().anyMatch(s -> s.toLowerCase().contains("method")));
        }
        
        return structure;
    }
    
    /**
     * Creates processing metadata for database storage.
     */
    private Map<String, Object> createProcessingMetadata(Map<String, Object> processedData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingTimestamp", System.currentTimeMillis());
        metadata.put("processor", "PaperProcessorFallbackAgent");
        metadata.put("aiProvider", "OLLAMA");
        metadata.put("processingMode", "fallback");
        metadata.put("contentLength", processedData.get("contentLength"));
        metadata.put("truncated", processedData.get("truncated"));
        
        return metadata;
    }
    
    /**
     * **CRITICAL: Save fallback results to database tables (matching original PaperProcessorAgent).**
     * This ensures the fallback agent populates the same database tables as the original.
     */
    @Transactional
    private void saveFallbackResultsToDatabase(String paperId, Map<String, Object> processedData, String rawContent) {
        try {
            UUID paperUuid = UUID.fromString(paperId);
            Optional<Paper> paperOpt = paperRepository.findById(paperUuid);
            
            if (paperOpt.isEmpty()) {
                LoggingUtil.warn(LOG, "saveFallbackResultsToDatabase", 
                    "Paper not found with ID %s, skipping database save", paperId);
                return;
            }
            
            Paper paper = paperOpt.get();
            
            // Save processed content to paper_content table
            saveFallbackPaperContent(paper, processedData);
            
            // Save paper sections to paper_sections table  
            saveFallbackPaperSections(paper, processedData);
            
            // Extract and save technical terms as tags to tags and paper_tags tables
            saveFallbackContentTags(paper, processedData, rawContent);
            
            LoggingUtil.info(LOG, "saveFallbackResultsToDatabase", 
                "Successfully saved fallback processed content for paper %s", paperId);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveFallbackResultsToDatabase", 
                "Failed to save fallback processed content for paper %s", e, paperId);
            // Don't rethrow - this is supplementary data, main processing should continue
        }
    }
    
    /**
     * Saves the processed content to paper_content table (fallback version).
     */
    private void saveFallbackPaperContent(Paper paper, Map<String, Object> processedData) {
        String cleanedText = (String) processedData.getOrDefault("cleanedText", "");
        
        // Check if content already exists by paper ID
        Optional<PaperContent> existingContent = paperContentRepository.findByPaperId(paper.getId());
        
        if (existingContent.isPresent()) {
            // Update existing content
            PaperContent paperContent = existingContent.get();
            paperContent.setContent(cleanedText);
            paperContentRepository.save(paperContent);
        } else {
            // Create new content
            PaperContent paperContent = new PaperContent(paper.getId(), cleanedText);
            paperContentRepository.save(paperContent);
        }
        
        LoggingUtil.info(LOG, "saveFallbackPaperContent", 
            "Saved fallback paper content for paper %s", paper.getId());
    }
    
    /**
     * Saves individual paper sections to paper_sections table (fallback version).
     */
    private void saveFallbackPaperSections(Paper paper, Map<String, Object> processedData) {
        // Delete existing sections by paper ID first
        paperSectionRepository.deleteByPaperId(paper.getId());
        
        @SuppressWarnings("unchecked")
        List<String> sections = (List<String>) processedData.get("sections");
        if (sections == null || sections.isEmpty()) {
            return;
        }
        
        List<PaperSection> paperSections = new ArrayList<>();
        
        int sectionIndex = 1;
        for (String sectionTitle : sections) {
            if (sectionTitle != null && !sectionTitle.trim().isEmpty()) {
                // For fallback, we don't have detailed section content, so use title as content
                PaperSection section = new PaperSection(
                    paper.getId(), 
                    sectionTitle, 
                    "Section identified by fallback processing: " + sectionTitle, 
                    sectionIndex++
                );
                paperSections.add(section);
            }
        }
        
        if (!paperSections.isEmpty()) {
            paperSectionRepository.saveAll(paperSections);
            LoggingUtil.info(LOG, "saveFallbackPaperSections", 
                "Saved %d fallback sections for paper %s", paperSections.size(), paper.getId());
        }
    }
    
    /**
     * Extracts and saves technical terms as tags (fallback version).
     */
    private void saveFallbackContentTags(Paper paper, Map<String, Object> processedData, String rawContent) {
        try {
            List<String> technicalTerms = extractTechnicalTermsFallback(rawContent, processedData);
            
            for (String term : technicalTerms) {
                if (term.length() >= 3 && term.length() <= 50) { // Reasonable tag length
                    String normalizedTerm = term.toLowerCase().trim();
                    
                    // Find or create tag (system-generated, no user)
                    Optional<Tag> existingTag = tagRepository.findByNameIgnoreCase(normalizedTerm);
                    Tag tag = existingTag.orElseGet(() -> {
                        Tag newTag = new Tag(normalizedTerm, "#6B7280"); // Default color
                        return tagRepository.save(newTag);
                    });
                    
                    // Create paper-tag relationship if it doesn't exist
                    if (!paperTagRepository.existsByIdPaperIdAndIdTagId(paper.getId(), tag.getId())) {
                        PaperTag paperTag = new PaperTag(paper.getId(), tag.getId());
                        paperTagRepository.save(paperTag);
                    }
                }
            }
            
            LoggingUtil.info(LOG, "saveFallbackContentTags", 
                "Processed %d technical terms for paper %s", technicalTerms.size(), paper.getId());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveFallbackContentTags", 
                "Failed to save fallback content tags for paper %s", e, paper.getId());
        }
    }
    
    /**
     * Extracts technical terms and concepts from the processed content (fallback version).
     */
    private List<String> extractTechnicalTermsFallback(String rawContent, Map<String, Object> processedData) {
        List<String> terms = new ArrayList<>();
        
        // Extract from research domain and complexity info
        String domain = (String) processedData.get("researchDomain");
        if (domain != null) {
            terms.add(domain.toLowerCase());
        }
        
        String complexity = (String) processedData.get("complexity");
        if (complexity != null) {
            terms.add(complexity + " complexity");
        }
        
        // Extract terms from content using simple heuristics
        String[] words = rawContent.toLowerCase().split("\\s+");
        
        for (String word : words) {
            // Clean the word
            word = word.replaceAll("[^a-zA-Z\\-]", "").trim();
            
            if (word.length() >= 4 && word.length() <= 25) {
                // Add words that look like technical terms
                if (isTechnicalTermFallback(word)) {
                    terms.add(word);
                }
            }
        }
        
        // Remove duplicates and limit to reasonable number
        return terms.stream()
                   .distinct()
                   .limit(20) // Limit for fallback processing
                   .toList();
    }
    
    /**
     * Simple heuristic to identify potential technical terms (fallback version).
     */
    private boolean isTechnicalTermFallback(String word) {
        // Skip common words
        if (isCommonWordFallback(word)) {
            return false;
        }
        
        // Look for technical patterns
        return word.contains("algorithm") || word.contains("neural") || 
               word.contains("machine") || word.contains("learning") ||
               word.contains("method") || word.contains("analysis") ||
               word.contains("model") || word.contains("system") ||
               word.contains("research") || word.contains("data") ||
               word.endsWith("tion") || word.endsWith("ing") ||
               (word.length() > 6 && !word.matches(".*[aeiou]{3,}.*")); // Complex words
    }
    
    /**
     * Checks if a word is a common non-technical word (fallback version).
     */
    private boolean isCommonWordFallback(String word) {
        String[] commonWords = {
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "had", 
            "her", "was", "one", "our", "out", "day", "had", "his", "how", "man",
            "new", "now", "old", "see", "two", "way", "who", "boy", "did", "its",
            "let", "put", "say", "she", "too", "use", "this", "that", "with", "from",
            "have", "been", "were", "said", "each", "which", "their", "time", "will"
        };
        
        for (String common : commonWords) {
            if (word.equals(common)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for paper processing. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses simplified analysis optimized for local models with basic paper processing capabilities.";
    }
}

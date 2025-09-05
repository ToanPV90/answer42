package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.samjdtechnologies.answer42.model.db.Citation;
import com.samjdtechnologies.answer42.model.db.CitationVerification;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.CitationRepository;
import com.samjdtechnologies.answer42.repository.CitationVerificationRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Ollama-based fallback agent for citation formatting.
 * This agent provides local processing capabilities when cloud providers are unavailable.
 * 
 * Optimized for local Ollama models with simplified citation formatting:
 * - Basic citation format detection and standardization
 * - Simplified formatting focused on common citation styles
 * - Fallback-specific error handling and user notifications
 * - Content truncation to prevent resource exhaustion
 * 
 * Provides essential citation formatting functionality with local processing constraints.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class CitationFormatterFallbackAgent extends OllamaBasedAgent {
    
    // Citation styles optimized for local models
    private static final Map<String, String> LOCAL_CITATION_STYLES = Map.of(
        "apa", "APA style formatting with basic author-date format",
        "mla", "MLA style formatting with author-page format", 
        "chicago", "Chicago style formatting with footnote/bibliography format",
        "ieee", "IEEE style formatting with numbered references",
        "harvard", "Harvard style formatting with author-date format"
    );
    
    // Citation patterns for detection
    private static final Pattern AUTHOR_YEAR_PATTERN = Pattern.compile(
        "\\b([A-Z][a-z]+(?: [A-Z][a-z]*)*(?:,? (?:et al\\.?|& [A-Z][a-z]+))?),? \\(?([12]\\d{3})\\)?");
    
    private static final Pattern DOI_PATTERN = Pattern.compile(
        "(?:doi:|DOI:?\\s*|https?://(?:dx\\.)?doi\\.org/)([10]\\.[^\\s]+)");
    
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[^\\s]+");
    
    private final CitationRepository citationRepository;
    private final CitationVerificationRepository citationVerificationRepository;
    private final PaperRepository paperRepository;
    private final ObjectMapper objectMapper;
    
    public CitationFormatterFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                         APIRateLimiter rateLimiter,
                                         CitationRepository citationRepository,
                                         CitationVerificationRepository citationVerificationRepository,
                                         PaperRepository paperRepository,
                                         ObjectMapper objectMapper) {
        super(aiConfig, threadConfig, rateLimiter);
        this.citationRepository = citationRepository;
        this.citationVerificationRepository = citationVerificationRepository;
        this.paperRepository = paperRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.CITATION_FORMATTER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing citation formatting with Ollama fallback for task %s", task.getId());
        
        try {
            // Extract task input with null safety
            JsonNode input = task.getInput();
            if (input == null) {
                return AgentResult.failure(task.getId(), "FALLBACK: No input data provided");
            }
            
            JsonNode itemIdNode = input.get("itemId");
            String itemId = itemIdNode != null ? itemIdNode.asText() : "unknown";
            
            JsonNode rawCitationsNode = input.get("rawCitations");
            String rawCitations = rawCitationsNode != null ? rawCitationsNode.asText() : "";
            
            JsonNode targetStyleNode = input.get("targetStyle");
            String targetStyle = targetStyleNode != null ? targetStyleNode.asText() : "apa";
            
            JsonNode sourceFormatNode = input.get("sourceFormat");
            String sourceFormat = sourceFormatNode != null ? sourceFormatNode.asText() : "mixed";
            
            if (rawCitations.isEmpty()) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: No citations provided for formatting");
            }
            
            // Validate target style
            if (!LOCAL_CITATION_STYLES.containsKey(targetStyle.toLowerCase())) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Unknown citation style %s for fallback, defaulting to APA", targetStyle);
                targetStyle = "apa";
            }
            
            // Validate content is suitable for local processing
            if (!isCitationContentSuitableForLocalProcessing(rawCitations)) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: Citation content not suitable for local processing");
            }
            
            // Format citations using local model
            Map<String, Object> formattingResults = performLocalCitationFormatting(
                itemId, rawCitations, targetStyle, sourceFormat);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Citation Formatting");
            formattingResults.put("processingNote", fallbackNote);
            
            // Save citation verification records to database
            try {
                saveCitationVerifications(itemId, formattingResults);
                LoggingUtil.info(LOG, "processWithConfig", 
                    "Successfully saved citation verification records for task %s", task.getId());
            } catch (Exception e) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Failed to save citation verification records for task %s: %s", task.getId(), e.getMessage());
                // Continue processing - don't fail the entire task for database issues
            }
            
            // Create result data with fallback indicators
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("itemId", itemId);
            resultData.put("targetStyle", targetStyle);
            resultData.put("sourceFormat", sourceFormat);
            resultData.put("formattingResults", formattingResults);
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
     * Performs local citation formatting using Ollama.
     * 
     * @param itemId The item ID for logging
     * @param rawCitations The raw citations to format
     * @param targetStyle The target citation style
     * @param sourceFormat The source format of citations
     * @return Citation formatting results
     */
    private Map<String, Object> performLocalCitationFormatting(String itemId, String rawCitations, 
                                                             String targetStyle, String sourceFormat) {
        LoggingUtil.info(LOG, "performLocalCitationFormatting", 
            "Formatting citations for item %s to %s style using Ollama", itemId, targetStyle);
        
        Map<String, Object> formattingResults = new HashMap<>();
        
        try {
            // Step 1: Extract individual citations
            List<String> extractedCitations = extractIndividualCitations(rawCitations);
            formattingResults.put("extractedCount", extractedCitations.size());
            
            // Step 2: Format each citation using local model
            List<Map<String, Object>> formattedCitations = new ArrayList<>();
            
            for (int i = 0; i < Math.min(extractedCitations.size(), 20); i++) { // Limit for local processing
                String citation = extractedCitations.get(i);
                Map<String, Object> formatted = formatSingleCitation(citation, targetStyle, i + 1);
                formattedCitations.add(formatted);
            }
            
            formattingResults.put("formattedCitations", formattedCitations);
            formattingResults.put("processedCount", formattedCitations.size());
            
            // Step 3: Generate bibliography
            List<String> bibliography = generateBibliography(formattedCitations, targetStyle);
            formattingResults.put("bibliography", bibliography);
            
            // Step 4: Validate formatting quality
            Map<String, Object> qualityMetrics = validateFormattingQuality(formattedCitations, targetStyle);
            formattingResults.put("qualityMetrics", qualityMetrics);
            
            // Add processing metadata
            formattingResults.put("formattingQuality", "local_processing");
            formattingResults.put("processingMethod", "ollama_fallback");
            formattingResults.put("contentLength", rawCitations.length());
            formattingResults.put("truncated", rawCitations.length() > MAX_LOCAL_CONTENT_LENGTH);
            
            return formattingResults;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalCitationFormatting", 
                "Local citation formatting failed for item %s", e, itemId);
            return createLocalFallbackFormattingResults(targetStyle, e.getMessage());
        }
    }
    
    /**
     * Extracts individual citations from raw text.
     */
    private List<String> extractIndividualCitations(String rawCitations) {
        List<String> citations = new ArrayList<>();
        
        // Split by common citation separators
        String[] potentialCitations = rawCitations.split("(?:\\n\\s*\\n|\\n\\d+\\.|\\[\\d+\\])");
        
        for (String citation : potentialCitations) {
            String cleaned = citation.trim();
            if (cleaned.length() > 20 && cleaned.length() < 1000) { // Reasonable citation length
                citations.add(cleaned);
            }
        }
        
        // If no clear separations found, try other patterns
        if (citations.isEmpty()) {
            // Try splitting by periods followed by capital letters (common in bibliographies)
            String[] sentences = rawCitations.split("\\. (?=[A-Z])");
            for (String sentence : sentences) {
                String cleaned = sentence.trim();
                if (cleaned.length() > 20) {
                    citations.add(cleaned.endsWith(".") ? cleaned : cleaned + ".");
                }
            }
        }
        
        return citations.isEmpty() ? List.of(rawCitations) : citations;
    }
    
    /**
     * Formats a single citation using local processing.
     */
    private Map<String, Object> formatSingleCitation(String citation, String targetStyle, int index) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("originalCitation", citation);
        formatted.put("index", index);
        
        try {
            // Extract citation components
            Map<String, String> components = extractCitationComponents(citation);
            formatted.put("components", components);
            
            // Format using local model
            String processableContent = truncateForLocalProcessing(citation, MAX_LOCAL_CONTENT_LENGTH / 10);
            
            String promptText = String.format(
                "Format this citation in %s style:\n\n%s\n\n" +
                "Provide the properly formatted citation following %s guidelines. " +
                "Include author, title, publication, year, and other relevant details in the correct order.",
                targetStyle.toUpperCase(), processableContent, targetStyle.toUpperCase()
            );
            
            Prompt prompt = createFallbackPrompt(promptText, Map.of());
            ChatResponse response = executePrompt(prompt);
            String formattedText = response.getResult().getOutput().getText().trim();
            
            formatted.put("formattedCitation", formattedText);
            formatted.put("style", targetStyle);
            formatted.put("formattingMethod", "ollama_model");
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "formatSingleCitation", 
                "Citation formatting failed for index %d, using fallback method: %s", index, e.getMessage());
            
            // Fallback to rule-based formatting
            String fallbackFormatted = formatCitationFallback(citation, targetStyle);
            formatted.put("formattedCitation", fallbackFormatted);
            formatted.put("formattingMethod", "rule_based_fallback");
        }
        
        return formatted;
    }
    
    /**
     * Extracts components from a citation using regex patterns.
     */
    private Map<String, String> extractCitationComponents(String citation) {
        Map<String, String> components = new HashMap<>();
        
        // Extract DOI
        Matcher doiMatcher = DOI_PATTERN.matcher(citation);
        if (doiMatcher.find()) {
            components.put("doi", doiMatcher.group(1));
        }
        
        // Extract URL
        Matcher urlMatcher = URL_PATTERN.matcher(citation);
        if (urlMatcher.find()) {
            components.put("url", urlMatcher.group());
        }
        
        // Extract author and year
        Matcher authorYearMatcher = AUTHOR_YEAR_PATTERN.matcher(citation);
        if (authorYearMatcher.find()) {
            components.put("author", authorYearMatcher.group(1));
            components.put("year", authorYearMatcher.group(2));
        }
        
        // Extract title (simple heuristic - look for quoted or title-case text)
        Pattern titlePattern = Pattern.compile("\"([^\"]+)\"|'([^']+)'|\\b([A-Z][^.]*[a-z][^.]*)\\.?");
        Matcher titleMatcher = titlePattern.matcher(citation);
        if (titleMatcher.find()) {
            String title = titleMatcher.group(1) != null ? titleMatcher.group(1) : 
                          titleMatcher.group(2) != null ? titleMatcher.group(2) : titleMatcher.group(3);
            if (title != null && title.length() > 10) {
                components.put("title", title);
            }
        }
        
        return components;
    }
    
    /**
     * Formats citation using rule-based fallback method.
     */
    private String formatCitationFallback(String citation, String targetStyle) {
        Map<String, String> components = extractCitationComponents(citation);
        
        switch (targetStyle.toLowerCase()) {
            case "apa" -> {
                return formatAPAFallback(components, citation);
            }
            case "mla" -> {
                return formatMLAFallback(components, citation);
            }
            case "chicago" -> {
                return formatChicagoFallback(components, citation);
            }
            case "ieee" -> {
                return formatIEEEFallback(components, citation);
            }
            default -> {
                return formatGenericFallback(components, citation);
            }
        }
    }
    
    /**
     * Formats citation in APA style using extracted components.
     */
    private String formatAPAFallback(Map<String, String> components, String originalCitation) {
        StringBuilder apa = new StringBuilder();
        
        if (components.containsKey("author")) {
            apa.append(components.get("author"));
        } else {
            apa.append("Author, A.");
        }
        
        if (components.containsKey("year")) {
            apa.append(" (").append(components.get("year")).append(").");
        } else {
            apa.append(" (Year).");
        }
        
        if (components.containsKey("title")) {
            apa.append(" ").append(components.get("title"));
            if (!components.get("title").endsWith(".")) {
                apa.append(".");
            }
        } else {
            apa.append(" Title of work.");
        }
        
        if (components.containsKey("doi")) {
            apa.append(" https://doi.org/").append(components.get("doi"));
        } else if (components.containsKey("url")) {
            apa.append(" Retrieved from ").append(components.get("url"));
        }
        
        return apa.toString();
    }
    
    /**
     * Formats citation in MLA style using extracted components.
     */
    private String formatMLAFallback(Map<String, String> components, String originalCitation) {
        StringBuilder mla = new StringBuilder();
        
        if (components.containsKey("author")) {
            mla.append(components.get("author")).append(".");
        } else {
            mla.append("Author, First.");
        }
        
        if (components.containsKey("title")) {
            mla.append(" \"").append(components.get("title")).append(".\"");
        } else {
            mla.append(" \"Title of Work.\"");
        }
        
        mla.append(" Publication");
        
        if (components.containsKey("year")) {
            mla.append(", ").append(components.get("year"));
        }
        
        if (components.containsKey("url")) {
            mla.append(", ").append(components.get("url"));
        }
        
        mla.append(".");
        
        return mla.toString();
    }
    
    /**
     * Formats citation in Chicago style using extracted components.
     */
    private String formatChicagoFallback(Map<String, String> components, String originalCitation) {
        StringBuilder chicago = new StringBuilder();
        
        if (components.containsKey("author")) {
            chicago.append(components.get("author")).append(".");
        } else {
            chicago.append("Author, First.");
        }
        
        if (components.containsKey("title")) {
            chicago.append(" \"").append(components.get("title")).append(".\"");
        } else {
            chicago.append(" \"Title of Work.\"");
        }
        
        chicago.append(" Journal/Publication");
        
        if (components.containsKey("year")) {
            chicago.append(" (").append(components.get("year")).append(")");
        }
        
        chicago.append(".");
        
        return chicago.toString();
    }
    
    /**
     * Formats citation in IEEE style using extracted components.
     */
    private String formatIEEEFallback(Map<String, String> components, String originalCitation) {
        StringBuilder ieee = new StringBuilder();
        
        if (components.containsKey("author")) {
            ieee.append(components.get("author")).append(",");
        } else {
            ieee.append("F. Author,");
        }
        
        if (components.containsKey("title")) {
            ieee.append(" \"").append(components.get("title")).append(",\"");
        } else {
            ieee.append(" \"Title of work,\"");
        }
        
        ieee.append(" Journal");
        
        if (components.containsKey("year")) {
            ieee.append(", ").append(components.get("year"));
        }
        
        ieee.append(".");
        
        return ieee.toString();
    }
    
    /**
     * Generic fallback formatting.
     */
    private String formatGenericFallback(Map<String, String> components, String originalCitation) {
        if (originalCitation.length() > 500) {
            return originalCitation.substring(0, 500) + "... [Citation formatting requires detailed processing]";
        }
        
        return originalCitation + " [Formatted with local fallback]";
    }
    
    /**
     * Generates bibliography from formatted citations.
     */
    private List<String> generateBibliography(List<Map<String, Object>> formattedCitations, String style) {
        List<String> bibliography = new ArrayList<>();
        
        for (Map<String, Object> citation : formattedCitations) {
            String formatted = (String) citation.get("formattedCitation");
            if (formatted != null && !formatted.trim().isEmpty()) {
                bibliography.add(formatted);
            }
        }
        
        return bibliography;
    }
    
    /**
     * Validates formatting quality.
     */
    private Map<String, Object> validateFormattingQuality(List<Map<String, Object>> formattedCitations, String style) {
        Map<String, Object> quality = new HashMap<>();
        
        int totalCitations = formattedCitations.size();
        int successfullyFormatted = 0;
        int withComponents = 0;
        
        for (Map<String, Object> citation : formattedCitations) {
            String formatted = (String) citation.get("formattedCitation");
            if (formatted != null && !formatted.contains("[Formatted with local fallback]")) {
                successfullyFormatted++;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> components = (Map<String, String>) citation.get("components");
            if (components != null && components.size() > 2) {
                withComponents++;
            }
        }
        
        double successRate = totalCitations > 0 ? (double) successfullyFormatted / totalCitations : 0;
        double componentRate = totalCitations > 0 ? (double) withComponents / totalCitations : 0;
        
        quality.put("totalCitations", totalCitations);
        quality.put("successfullyFormatted", successfullyFormatted);
        quality.put("successRate", successRate);
        quality.put("componentExtractionRate", componentRate);
        quality.put("overallQuality", (successRate + componentRate) / 2);
        quality.put("qualityGrade", getQualityGrade((successRate + componentRate) / 2));
        
        return quality;
    }
    
    /**
     * Converts numeric quality score to letter grade.
     */
    private String getQualityGrade(double score) {
        if (score >= 0.9) return "A";
        else if (score >= 0.8) return "B";
        else if (score >= 0.7) return "C";
        else if (score >= 0.6) return "D";
        else return "F";
    }
    
    /**
     * Creates fallback formatting results when processing fails.
     */
    private Map<String, Object> createLocalFallbackFormattingResults(String targetStyle, String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackFormattingResults", 
            "Creating fallback formatting results due to: %s", errorReason);
        
        Map<String, Object> fallbackResults = new HashMap<>();
        fallbackResults.put("extractedCount", 0);
        fallbackResults.put("processedCount", 0);
        fallbackResults.put("formattedCitations", List.of());
        fallbackResults.put("bibliography", List.of("Citation formatting incomplete - local fallback"));
        
        Map<String, Object> qualityMetrics = new HashMap<>();
        qualityMetrics.put("totalCitations", 0);
        qualityMetrics.put("successfullyFormatted", 0);
        qualityMetrics.put("successRate", 0.0);
        qualityMetrics.put("overallQuality", 0.5);
        qualityMetrics.put("qualityGrade", "F");
        
        fallbackResults.put("qualityMetrics", qualityMetrics);
        fallbackResults.put("formattingQuality", "fallback_only");
        fallbackResults.put("processingMethod", "local_fallback");
        fallbackResults.put("processingNote", "Local fallback citation formatting - " + errorReason);
        
        return fallbackResults;
    }
    
    /**
     * Validates that citation content is suitable for local processing.
     */
    private boolean isCitationContentSuitableForLocalProcessing(String citations) {
        if (citations == null || citations.trim().isEmpty()) {
            return false;
        }
        
        // Check for minimum content length
        if (citations.length() < 20) {
            LoggingUtil.warn(LOG, "isCitationContentSuitableForLocalProcessing", 
                "Citation content too short for processing: %d characters", citations.length());
            return false;
        }
        
        // Check for extremely large content
        if (citations.length() > MAX_LOCAL_CONTENT_LENGTH * 2) {
            LoggingUtil.warn(LOG, "isCitationContentSuitableForLocalProcessing", 
                "Citation content length %d may impact local processing quality", citations.length());
            // Still allow processing with truncation
        }
        
        return true;
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input != null && input.has("rawCitations")) {
            String citations = input.get("rawCitations").asText();
            int estimatedCitations = Math.max(1, citations.split("\\n").length / 3);
            
            // Local processing is generally faster but less sophisticated
            long baseSeconds = 10 + (estimatedCitations * 5); // 5 seconds per citation estimate
            
            return Duration.ofSeconds(Math.min(baseSeconds, 120)); // Cap at 2 minutes
        }
        
        return Duration.ofSeconds(30); // Default estimate for local processing
    }
    
    /**
     * Saves citation verification records to the database.
     * This method creates citation and citation verification records based on the formatting results.
     *
     * @param itemId The paper/item ID being processed
     * @param formattingResults The formatting results containing citation information
     */
    @Transactional
    private void saveCitationVerifications(String itemId, Map<String, Object> formattingResults) {
        LoggingUtil.info(LOG, "saveCitationVerifications", 
            "Saving citation verification records for item: %s", itemId);
        
        try {
            // Parse itemId as UUID to find the associated paper
            UUID paperId;
            try {
                paperId = UUID.fromString(itemId);
            } catch (IllegalArgumentException e) {
                LoggingUtil.warn(LOG, "saveCitationVerifications", 
                    "Invalid UUID format for itemId %s, cannot save verification records", itemId);
                return;
            }
            
            // Find the paper
            Optional<Paper> paperOptional = paperRepository.findById(paperId);
            if (paperOptional.isEmpty()) {
                LoggingUtil.warn(LOG, "saveCitationVerifications", 
                    "Paper not found for ID %s, cannot save citation verification records", paperId);
                return;
            }
            
            Paper paper = paperOptional.get();
            
            // Extract formatted citations from results
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> formattedCitations = 
                (List<Map<String, Object>>) formattingResults.get("formattedCitations");
            
            if (formattedCitations == null || formattedCitations.isEmpty()) {
                LoggingUtil.info(LOG, "saveCitationVerifications", 
                    "No formatted citations found for paper %s", paperId);
                return;
            }
            
            int savedCount = 0;
            Instant verificationTime = Instant.now();
            
            // Process each formatted citation
            for (Map<String, Object> citationData : formattedCitations) {
                try {
                    Citation savedCitation = saveCitationRecord(paper, citationData);
                    if (savedCitation != null) {
                        CitationVerification verification = createCitationVerification(
                            savedCitation, paper, citationData, verificationTime);
                        citationVerificationRepository.save(verification);
                        savedCount++;
                    }
                } catch (Exception e) {
                    LoggingUtil.warn(LOG, "saveCitationVerifications", 
                        "Failed to save citation verification for item %s: %s", itemId, e.getMessage());
                    // Continue processing other citations
                }
            }
            
            LoggingUtil.info(LOG, "saveCitationVerifications", 
                "Successfully saved %d citation verification records for paper %s", savedCount, paperId);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveCitationVerifications", 
                "Error saving citation verifications for item %s", e, itemId);
            throw e; // Re-throw to allow caller to handle
        }
    }
    
    /**
     * Saves a citation record to the database.
     *
     * @param paper The paper containing the citation
     * @param citationData The citation data from formatting results
     * @return The saved Citation entity, or null if saving failed
     */
    private Citation saveCitationRecord(Paper paper, Map<String, Object> citationData) {
        try {
            // Extract citation text (formatted or original)
            String formattedCitation = (String) citationData.get("formattedCitation");
            String originalCitation = (String) citationData.get("originalCitation");
            String citationText = formattedCitation != null ? formattedCitation : originalCitation;
            
            if (citationText == null || citationText.trim().isEmpty()) {
                LoggingUtil.warn(LOG, "saveCitationRecord", 
                    "No citation text found for paper %s", paper.getId());
                return null;
            }
            
            // Build structured citation data
            Map<String, Object> structuredData = new HashMap<>();
            
            // Extract components if available
            @SuppressWarnings("unchecked")
            Map<String, String> components = (Map<String, String>) citationData.get("components");
            if (components != null) {
                structuredData.put("doi", components.get("doi"));
                structuredData.put("title", components.get("title"));
                structuredData.put("authors", components.get("author") != null ? 
                    List.of(components.get("author")) : List.of());
                structuredData.put("year", components.get("year"));
                structuredData.put("url", components.get("url"));
            }
            
            // Add formatting metadata
            structuredData.put("formattingMethod", citationData.get("formattingMethod"));
            structuredData.put("style", citationData.get("style"));
            structuredData.put("fallbackProcessed", true);
            structuredData.put("confidence", 0.7); // Default confidence for local processing
            structuredData.put("formattedText", formattedCitation);
            structuredData.put("originalText", originalCitation);
            
            // Convert to JsonNode
            JsonNode citationDataNode = objectMapper.valueToTree(structuredData);
            
            // Create citation using constructor
            Citation citation = new Citation(paper.getId(), citationDataNode, citationText);
            
            return citationRepository.save(citation);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveCitationRecord", 
                "Error saving citation record for paper %s", e, paper.getId());
            return null;
        }
    }
    
    /**
     * Creates a citation verification record.
     *
     * @param citation The saved citation entity
     * @param paper The paper containing the citation
     * @param citationData The citation data from formatting results
     * @param verificationTime The verification timestamp
     * @return The created CitationVerification entity
     */
    private CitationVerification createCitationVerification(Citation citation, Paper paper, 
                                                          Map<String, Object> citationData, 
                                                          Instant verificationTime) {
        CitationVerification verification = new CitationVerification();
        verification.setCitation(citation);
        verification.setPaperId(paper.getId());
        verification.setVerificationDate(verificationTime);
        verification.setVerificationSource("ollama_fallback");
        
        // Extract DOI and other identifiers from components
        @SuppressWarnings("unchecked")
        Map<String, String> components = (Map<String, String>) citationData.get("components");
        if (components != null) {
            verification.setDoi(components.get("doi"));
            // Note: semantic_scholar_id and arxiv_id would need more sophisticated extraction
        }
        
        // Set verification status and confidence based on formatting method
        String formattingMethod = (String) citationData.get("formattingMethod");
        if ("ollama_model".equals(formattingMethod)) {
            verification.setVerified(true);
            verification.setConfidence(0.7); // Moderate confidence for local processing
        } else if ("rule_based_fallback".equals(formattingMethod)) {
            verification.setVerified(true);
            verification.setConfidence(0.5); // Lower confidence for rule-based processing
        } else {
            verification.setVerified(false);
            verification.setConfidence(0.3); // Low confidence for unknown methods
        }
        
        // Create merged metadata combining citation and verification info
        Map<String, Object> mergedMetadata = new HashMap<>();
        mergedMetadata.put("formattingMethod", formattingMethod);
        mergedMetadata.put("originalCitation", citationData.get("originalCitation"));
        mergedMetadata.put("formattedCitation", citationData.get("formattedCitation"));
        mergedMetadata.put("processingAgent", "CitationFormatterFallbackAgent");
        mergedMetadata.put("fallbackProcessing", true);
        mergedMetadata.put("verificationTime", verificationTime.toString());
        
        if (components != null) {
            mergedMetadata.putAll(components);
        }
        
        try {
            JsonNode metadataNode = objectMapper.valueToTree(mergedMetadata);
            verification.setMergedMetadata(metadataNode);
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "createCitationVerification", 
                "Failed to serialize verification metadata: %s", e.getMessage());
        }
        
        return verification;
    }
    
    /**
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for citation formatting. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses simplified formatting optimized for local models with basic citation style support.";
    }
}

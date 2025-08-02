package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
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
    
    public PaperProcessorFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                                     AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
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
            // Extract task input
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            String rawContent = input.has("rawContent") ? input.get("rawContent").asText() : "";
            String processingMode = input.has("processingMode") ? 
                input.get("processingMode").asText() : "standard";
            
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
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Paper Processing");
            processedData.put("processingNote", fallbackNote);
            
            // Create result data with fallback indicators
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("processingMode", processingMode);
            resultData.put("processedData", processedData);
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
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for paper processing. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses simplified analysis optimized for local models with basic paper processing capabilities.";
    }
}

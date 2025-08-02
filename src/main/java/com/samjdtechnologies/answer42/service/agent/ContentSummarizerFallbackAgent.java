package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.agent.SummaryResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.agent.SummaryConfig;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Ollama-based fallback agent for content summarization.
 * This agent provides local processing capabilities when cloud providers are unavailable.
 * 
 * Optimized for local Ollama models:
 * - Simplified prompt engineering for local model performance
 * - Content truncation for resource-constrained processing
 * - Fallback-specific error handling and user notifications
 * - Reduced complexity while maintaining essential functionality
 * 
 * Provides the same interface as ContentSummarizerAgent but with local processing constraints.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class ContentSummarizerFallbackAgent extends OllamaBasedAgent {
    
    // Simplified summary configurations optimized for local models
    private static final Map<String, SummaryConfig> LOCAL_SUMMARY_CONFIGS = Map.of(
        "brief", new SummaryConfig(1, 30, "Single sentence capturing main finding"),
        "standard", new SummaryConfig(50, 100, "Clear overview of methodology and results"),
        "detailed", new SummaryConfig(150, 250, "Comprehensive summary with key insights")
    );
    
    public ContentSummarizerFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                        AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.CONTENT_SUMMARIZER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing content summarization with Ollama fallback for task %s", task.getId());
        
        try {
            // Extract task input
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            String textContent = input.has("textContent") ? input.get("textContent").asText() : null;
            String summaryType = input.has("summaryType") ? input.get("summaryType").asText() : "standard";
            
            if (textContent == null || textContent.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), "FALLBACK: No text content provided for summarization");
            }
            
            // Validate content is suitable for local processing
            if (!isContentSuitableForLocalProcessing(textContent)) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: Content not suitable for local processing - too large or complex");
            }
            
            // Validate summary type
            if (!LOCAL_SUMMARY_CONFIGS.containsKey(summaryType.toLowerCase())) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Unknown summary type %s for fallback, defaulting to standard", summaryType);
                summaryType = "standard";
            }
            
            // Generate summary using local model
            SummaryResult summaryResult = performLocalSummarization(textContent, summaryType, paperId);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Content Summarization");
            String enhancedSummary = fallbackNote + "\n\n" + summaryResult.getContent();
            
            // Create result data with fallback indicators
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("summaryType", summaryType);
            resultData.put("summary", enhancedSummary);
            resultData.put("wordCount", summaryResult.getWordCount());
            resultData.put("keyFindings", summaryResult.getKeyFindings());
            resultData.put("qualityScore", summaryResult.getQualityScore());
            resultData.put("processingNotes", summaryResult.getProcessingNotes());
            resultData.put("fallbackUsed", true);
            resultData.put("fallbackProvider", "OLLAMA");
            
            AgentResult result = AgentResult.success(task.getId(), resultData);
            // Mark as fallback result
            resultData.put("usedFallback", true);
            resultData.put("primaryFailureReason", "Cloud providers temporarily unavailable");
            return result;
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Ollama fallback processing failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), handleLocalProcessingError(e, task.getId()));
        }
    }
    
    /**
     * Performs local content summarization using Ollama.
     * 
     * @param textContent The content to summarize
     * @param summaryType The type of summary (brief, standard, detailed)
     * @param paperId The paper ID for logging
     * @return The summarized content with metadata
     */
    private SummaryResult performLocalSummarization(String textContent, String summaryType, String paperId) {
        LoggingUtil.info(LOG, "performLocalSummarization", 
            "Generating %s summary using Ollama for paper %s", summaryType, paperId);
        
        // Truncate content for local processing
        String processableContent = truncateForLocalProcessing(textContent, MAX_LOCAL_CONTENT_LENGTH);
        
        // Create simplified prompt optimized for local models
        String promptText = buildLocalSummaryPrompt(summaryType, processableContent);
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String summaryContent = response.getResult().getOutput().getText();
            
            if (summaryContent == null || summaryContent.trim().isEmpty()) {
                return createLocalFallbackSummary(textContent, summaryType, "Empty response from local model");
            }
            
            // Clean and process the summary
            summaryContent = cleanSummaryContent(summaryContent);
            
            return createSummaryResult(summaryContent, summaryType, textContent, processableContent);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalSummarization", 
                "Local summarization failed for task %s", e, paperId);
            return createLocalFallbackSummary(textContent, summaryType, e.getMessage());
        }
    }
    
    /**
     * Builds simplified summary prompts optimized for local Ollama models.
     */
    private String buildLocalSummaryPrompt(String summaryType, String content) {
        SummaryConfig config = LOCAL_SUMMARY_CONFIGS.get(summaryType.toLowerCase());
        
        return switch (summaryType.toLowerCase()) {
            case "brief" -> String.format(
                "Summarize this academic paper in 1-2 sentences (max %d words):\n\n%s\n\n" +
                "Focus on: What was studied and what was found. Be clear and direct.",
                config.getMaxWords(), content
            );
            
            case "detailed" -> String.format(
                "Create a detailed summary of this academic paper (%d-%d words):\n\n%s\n\n" +
                "Include: 1) Research problem 2) Methods used 3) Key findings 4) Significance\n" +
                "Write clearly and focus on the most important points.",
                config.getMinWords(), config.getMaxWords(), content
            );
            
            default -> String.format(
                "Summarize this academic paper (%d-%d words):\n\n%s\n\n" +
                "Include the research topic, main method, and key results. Be concise but informative.",
                config.getMinWords(), config.getMaxWords(), content
            );
        };
    }
    
    /**
     * Creates a structured summary result from the local model response.
     */
    private SummaryResult createSummaryResult(String summaryContent, String summaryType, 
                                            String originalContent, String processedContent) {
        
        int wordCount = countWords(summaryContent);
        double compressionRatio = calculateCompressionRatio(originalContent, summaryContent);
        
        // Simple key findings extraction for local processing
        List<String> keyFindings = extractSimpleKeyFindings(summaryContent);
        
        // Local quality assessment
        double qualityScore = assessLocalSummaryQuality(summaryContent, summaryType, wordCount);
        
        // Add processing metadata for transparency
        String processingNote = String.format(
            "Generated using local Ollama model. Original: %d chars, Processed: %d chars. " +
            "Content may be simplified compared to cloud processing.",
            originalContent.length(), processedContent.length()
        );
        
        return SummaryResult.builder()
            .content(summaryContent)
            .summaryType(summaryType)
            .wordCount(wordCount)
            .compressionRatio(compressionRatio)
            .keyFindings(keyFindings)
            .qualityScore(qualityScore)
            .processingNotes(processingNote)
            .build();
    }
    
    /**
     * Creates fallback summary when local processing fails.
     */
    private SummaryResult createLocalFallbackSummary(String originalContent, String summaryType, String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackSummary", 
            "Creating fallback summary due to: %s", errorReason);
        
        String fallbackContent = switch (summaryType.toLowerCase()) {
            case "brief" -> "Academic content processed with local fallback. Key findings require cloud analysis.";
            case "detailed" -> "FALLBACK PROCESSING: This academic paper contains research content that requires " +
                             "detailed analysis. The paper presents methodology and findings, but comprehensive " +
                             "summarization needs cloud provider availability for optimal results.";
            default -> "FALLBACK PROCESSING: Academic paper analyzed using local model. Content contains " +
                      "research findings and methodology. Full analysis available when cloud providers return.";
        };
        
        return SummaryResult.builder()
            .content(fallbackContent)
            .summaryType(summaryType)
            .wordCount(countWords(fallbackContent))
            .compressionRatio(0.1)
            .keyFindings(List.of("Local fallback processing used", "Limited analysis due to constraints"))
            .qualityScore(4.0) // Lower score for fallback
            .processingNotes("Local fallback summary - " + errorReason)
            .build();
    }
    
    /**
     * Cleans and formats the summary content from local model.
     */
    private String cleanSummaryContent(String content) {
        if (content == null) return "";
        
        return content.trim()
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("^(Summary:|SUMMARY:|Brief:|Standard:|Detailed:)\\s*", "")
            .replaceAll("\\[.*?\\]", "") // Remove any bracketed metadata
            .trim();
    }
    
    /**
     * Simple word counting for local processing.
     */
    private int countWords(String text) {
        return text != null ? text.trim().split("\\s+").length : 0;
    }
    
    /**
     * Calculates compression ratio for transparency.
     */
    private double calculateCompressionRatio(String original, String summary) {
        int originalWords = countWords(original);
        int summaryWords = countWords(summary);
        return originalWords > 0 ? (double) summaryWords / originalWords : 0.0;
    }
    
    /**
     * Extracts simple key findings for local processing.
     */
    private List<String> extractSimpleKeyFindings(String summary) {
        // Simplified extraction for local processing
        List<String> findings = new java.util.ArrayList<>();
        
        if (summary.toLowerCase().contains("research") || summary.toLowerCase().contains("study")) {
            findings.add("Research study identified");
        }
        if (summary.toLowerCase().contains("method") || summary.toLowerCase().contains("approach")) {
            findings.add("Methodology described");
        }
        if (summary.toLowerCase().contains("result") || summary.toLowerCase().contains("finding")) {
            findings.add("Key results summarized");
        }
        if (summary.toLowerCase().contains("significant") || summary.toLowerCase().contains("important")) {
            findings.add("Significant findings noted");
        }
        
        return findings.isEmpty() ? List.of("Content processed with local analysis") : findings;
    }
    
    /**
     * Assesses summary quality for local processing.
     */
    private double assessLocalSummaryQuality(String summary, String summaryType, int wordCount) {
        double score = 6.0; // Base score for local processing (lower than cloud)
        
        // Check word count appropriateness for local configs
        SummaryConfig config = LOCAL_SUMMARY_CONFIGS.get(summaryType.toLowerCase());
        boolean wordCountAppropriate = wordCount >= config.getMinWords() && wordCount <= config.getMaxWords();
        
        if (wordCountAppropriate) {
            score += 1.0;
        }
        
        // Check for academic indicators
        if (summary.toLowerCase().contains("research") || summary.toLowerCase().contains("study")) {
            score += 0.5;
        }
        
        if (summary.toLowerCase().contains("method") || summary.toLowerCase().contains("result")) {
            score += 0.5;
        }
        
        // Penalize if too short or generic
        if (wordCount < 10) {
            score -= 1.0;
        }
        
        return Math.min(8.0, Math.max(3.0, score)); // Cap quality for local processing
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input != null && input.has("textContent")) {
            String summaryType = input.has("summaryType") ? input.get("summaryType").asText() : "standard";
            
            // Local processing is generally faster but less sophisticated
            long baseSeconds = switch (summaryType.toLowerCase()) {
                case "brief" -> 15;   // Faster for local
                case "detailed" -> 45; // Still faster than cloud
                default -> 30;
            };
            
            return Duration.ofSeconds(baseSeconds);
        }
        
        return Duration.ofMinutes(1); // Conservative estimate for local processing
    }
    
    /**
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for content summarization. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses optimized prompts for local models with content truncation for performance.";
    }
    
    /**
     * Validates that the input is appropriate for content summarization.
     */
    private boolean isValidForSummarization(String textContent) {
        if (textContent == null || textContent.trim().isEmpty()) {
            return false;
        }
        
        // Check minimum content length for meaningful summarization  
        if (textContent.trim().length() < 100) {
            LoggingUtil.warn(LOG, "isValidForSummarization", 
                "Content too short for meaningful summarization: %d characters", textContent.length());
            return false;
        }
        
        return isContentSuitableForLocalProcessing(textContent);
    }
}

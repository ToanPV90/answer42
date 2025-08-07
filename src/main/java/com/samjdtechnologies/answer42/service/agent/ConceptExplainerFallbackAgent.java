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
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Ollama-based fallback agent for concept explanation.
 * This agent provides local processing capabilities when cloud providers are unavailable.
 * 
 * Optimized for local Ollama models with simplified concept explanation:
 * - Basic terminology definitions suitable for local model processing
 * - Simplified explanations focused on core concepts
 * - Fallback-specific error handling and user notifications
 * - Content truncation to prevent resource exhaustion
 * 
 * Provides essential concept explanation functionality with local processing constraints.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class ConceptExplainerFallbackAgent extends OllamaBasedAgent {
    
    
    public ConceptExplainerFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                        APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, rateLimiter);
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.CONCEPT_EXPLAINER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing concept explanation with Ollama fallback for task %s", task.getId());
        
        try {
            // Extract task input with null safety
            JsonNode input = task.getInput();
            if (input == null) {
                return AgentResult.failure(task.getId(), "FALLBACK: No input data provided");
            }
            
            JsonNode paperIdNode = input.get("paperId");
            String paperId = paperIdNode != null ? paperIdNode.asText() : "unknown";
            
            // Match the original ConceptExplainerAgent's expected input structure
            JsonNode contentNode = input.get("content");
            String content = contentNode != null ? contentNode.asText() : null;
            
            if (content == null || content.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: No content provided for concept explanation");
            }
            
            // For fallback processing, we'll provide a simplified concept explanation
            // rather than trying to do the full complex analysis of the original agent
            LoggingUtil.info(LOG, "processWithConfig", 
                "Fallback agent processing content of %d characters", content.length());
            
            // Instead of rejecting content, truncate if needed and attempt processing
            if (content.length() > MAX_LOCAL_CONTENT_LENGTH * 2) {
                LoggingUtil.info(LOG, "processWithConfig", 
                    "Truncating large content from %d to %d characters for local processing", 
                    content.length(), MAX_LOCAL_CONTENT_LENGTH);
                content = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH);
            }
            
            // Generate simplified explanation using local model
            String explanation = performLocalContentAnalysis(content, paperId);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Concept Explanation");
            String enhancedExplanation = fallbackNote + "\n\n" + explanation;
            
            // Create result data that matches the expected ConceptExplanationResult structure
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("content", content);
            resultData.put("fallbackExplanation", enhancedExplanation);
            resultData.put("fallbackUsed", true);
            resultData.put("fallbackProvider", "OLLAMA");
            resultData.put("primaryFailureReason", "Cloud providers temporarily unavailable");
            resultData.put("processingNote", "Simplified concept explanation due to fallback processing");
            
            return AgentResult.success(task.getId(), resultData);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Ollama fallback processing failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), handleLocalProcessingError(e, task.getId()));
        }
    }
    
    
    /**
     * Cleans and formats the explanation content from local model.
     */
    private String cleanExplanationContent(String content) {
        if (content == null) return "";
        
        return content.trim()
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("^(Explanation:|EXPLANATION:|Concept:|Definition:)\\s*", "")
            .replaceAll("\\[.*?\\]", "") // Remove any bracketed metadata
            .trim();
    }
    
    /**
     * Performs local content analysis for concept explanation using Ollama.
     */
    private String performLocalContentAnalysis(String content, String paperId) {
        LoggingUtil.info(LOG, "performLocalContentAnalysis", 
            "Analyzing content for concept explanation using Ollama for paper %s", paperId);
        
        // Truncate content for local processing
        String processableContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH);
        
        // Create simplified prompt optimized for local models
        String promptText = buildContentAnalysisPrompt(processableContent);
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String analysisContent = response.getResult().getOutput().getText();
            
            if (analysisContent == null || analysisContent.trim().isEmpty()) {
                return createLocalFallbackContentAnalysis("Empty response from local model");
            }
            
            // Clean and process the analysis
            analysisContent = cleanExplanationContent(analysisContent);
            
            return analysisContent;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalContentAnalysis", 
                "Local content analysis failed for paper %s", e, paperId);
            return createLocalFallbackContentAnalysis(e.getMessage());
        }
    }
    
    /**
     * Builds content analysis prompt for concept explanation.
     */
    private String buildContentAnalysisPrompt(String content) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this academic content and identify key concepts that need explanation:\n\n");
        prompt.append(content);
        prompt.append("\n\nProvide a simplified explanation focusing on:\n");
        prompt.append("1. The main concepts and terminology used\n");
        prompt.append("2. Brief definitions of technical terms\n");
        prompt.append("3. Basic context for understanding the content\n\n");
        prompt.append("Keep explanations clear and concise. Focus on essential concepts only.");
        
        return prompt.toString();
    }
    
    /**
     * Creates fallback content analysis when local processing fails.
     */
    private String createLocalFallbackContentAnalysis(String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackContentAnalysis", 
            "Creating fallback content analysis due to: %s", errorReason);
        
        return "FALLBACK PROCESSING: This academic content contains technical concepts and terminology " +
               "that require detailed analysis for proper explanation. The content includes domain-specific " +
               "terms, methodologies, and concepts that would benefit from comprehensive explanation. " +
               "Detailed concept explanations will be available when cloud providers return.";
    }

    /**
     * Validates that the content is suitable for local processing.
     */
    @Override
    protected boolean isContentSuitableForLocalProcessing(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check for extremely large content that might cause issues
        if (content.length() > MAX_LOCAL_CONTENT_LENGTH * 2) {
            LoggingUtil.warn(LOG, "isContentSuitableForLocalProcessing", 
                "Content too large for local processing: %d characters", content.length());
            return false;
        }
        
        return true;
    }
    
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input != null && input.has("explanationLevel")) {
            String explanationLevel = input.get("explanationLevel").asText();
            
            // Local processing is generally faster but less sophisticated
            long baseSeconds = switch (explanationLevel.toLowerCase()) {
                case "basic" -> 10;     // Very fast for local
                case "detailed" -> 30;  // Still faster than cloud
                default -> 20;
            };
            
            return Duration.ofSeconds(baseSeconds);
        }
        
        return Duration.ofSeconds(20); // Conservative estimate for local processing
    }
    
}

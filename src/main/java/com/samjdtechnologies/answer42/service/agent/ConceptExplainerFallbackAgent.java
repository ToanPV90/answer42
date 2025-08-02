package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    // Explanation complexity levels optimized for local models
    private static final Map<String, String> LOCAL_EXPLANATION_LEVELS = Map.of(
        "basic", "Simple definition with minimal context",
        "standard", "Clear explanation with basic examples", 
        "detailed", "Comprehensive explanation with context and examples"
    );
    
    public ConceptExplainerFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                                       AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
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
            // Extract task input
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            String concept = input.has("concept") ? input.get("concept").asText() : null;
            String context = input.has("context") ? input.get("context").asText() : "";
            String explanationLevel = input.has("explanationLevel") ? 
                input.get("explanationLevel").asText() : "standard";
            
            if (concept == null || concept.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: No concept provided for explanation");
            }
            
            // Validate explanation level
            if (!LOCAL_EXPLANATION_LEVELS.containsKey(explanationLevel.toLowerCase())) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Unknown explanation level %s for fallback, defaulting to standard", explanationLevel);
                explanationLevel = "standard";
            }
            
            // Validate content is suitable for local processing
            if (!isConceptSuitableForLocalProcessing(concept, context)) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: Concept too complex for local processing");
            }
            
            // Generate explanation using local model
            String explanation = performLocalConceptExplanation(concept, context, explanationLevel, paperId);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Concept Explanation");
            String enhancedExplanation = fallbackNote + "\n\n" + explanation;
            
            // Create result data with fallback indicators
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("concept", concept);
            resultData.put("explanation", enhancedExplanation);
            resultData.put("explanationLevel", explanationLevel);
            resultData.put("context", context);
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
     * Performs local concept explanation using Ollama.
     * 
     * @param concept The concept to explain
     * @param context Additional context for the explanation
     * @param explanationLevel The level of explanation (basic, standard, detailed)
     * @param paperId The paper ID for logging
     * @return The concept explanation
     */
    private String performLocalConceptExplanation(String concept, String context, 
                                                String explanationLevel, String paperId) {
        LoggingUtil.info(LOG, "performLocalConceptExplanation", 
            "Generating %s explanation for concept '%s' using Ollama for paper %s", 
            explanationLevel, concept, paperId);
        
        // Truncate context for local processing
        String processableContext = truncateForLocalProcessing(context, MAX_LOCAL_CONTENT_LENGTH / 2);
        
        // Create simplified prompt optimized for local models
        String promptText = buildLocalExplanationPrompt(concept, processableContext, explanationLevel);
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String explanationContent = response.getResult().getOutput().getText();
            
            if (explanationContent == null || explanationContent.trim().isEmpty()) {
                return createLocalFallbackExplanation(concept, explanationLevel, "Empty response from local model");
            }
            
            // Clean and process the explanation
            explanationContent = cleanExplanationContent(explanationContent);
            
            return explanationContent;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalConceptExplanation", 
                "Local concept explanation failed for task %s", e, paperId);
            return createLocalFallbackExplanation(concept, explanationLevel, e.getMessage());
        }
    }
    
    /**
     * Builds simplified explanation prompts optimized for local Ollama models.
     */
    private String buildLocalExplanationPrompt(String concept, String context, String explanationLevel) {
        String levelDescription = LOCAL_EXPLANATION_LEVELS.get(explanationLevel.toLowerCase());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("Explain the concept: %s\n\n", concept));
        
        if (!context.isEmpty()) {
            prompt.append(String.format("Context: %s\n\n", context));
        }
        
        prompt.append(String.format("Explanation level: %s (%s)\n\n", explanationLevel, levelDescription));
        
        switch (explanationLevel.toLowerCase()) {
            case "basic" -> prompt.append(
                "Provide a simple, clear definition. Keep it concise and easy to understand. " +
                "Focus on what it is, not complex details."
            );
            
            case "detailed" -> prompt.append(
                "Provide a comprehensive explanation including:\n" +
                "1. What the concept is\n" +
                "2. Why it's important\n" +
                "3. How it works (if applicable)\n" +
                "4. A simple example\n" +
                "Keep explanations clear and well-structured."
            );
            
            default -> prompt.append(
                "Provide a clear explanation that includes:\n" +
                "1. A clear definition\n" +
                "2. Basic context or importance\n" +
                "3. A simple example if helpful\n" +
                "Make it accessible but informative."
            );
        }
        
        return prompt.toString();
    }
    
    /**
     * Creates fallback explanation when local processing fails.
     */
    private String createLocalFallbackExplanation(String concept, String explanationLevel, String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackExplanation", 
            "Creating fallback explanation for concept '%s' due to: %s", concept, errorReason);
        
        return switch (explanationLevel.toLowerCase()) {
            case "basic" -> String.format(
                "FALLBACK: '%s' is a concept that requires detailed analysis. " +
                "Full explanation available when cloud providers return.", concept
            );
            
            case "detailed" -> String.format(
                "FALLBACK PROCESSING: The concept '%s' is an important academic term that requires " +
                "comprehensive analysis to explain properly. This includes understanding its definition, " +
                "context, applications, and significance in the field. " +
                "Detailed explanation will be available when cloud providers return.", concept
            );
            
            default -> String.format(
                "FALLBACK: '%s' is an academic concept that needs proper analysis for explanation. " +
                "This concept has specific meaning and applications in its field. " +
                "Complete explanation available when cloud providers return.", concept
            );
        };
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
     * Validates that the concept is suitable for local processing.
     */
    private boolean isConceptSuitableForLocalProcessing(String concept, String context) {
        if (concept == null || concept.trim().isEmpty()) {
            return false;
        }
        
        // Check for extremely complex concepts that might cause issues
        if (concept.length() > 200) {
            LoggingUtil.warn(LOG, "isConceptSuitableForLocalProcessing", 
                "Concept too long for local processing: %d characters", concept.length());
            return false;
        }
        
        // Check context size
        if (context != null && context.length() > MAX_LOCAL_CONTENT_LENGTH) {
            LoggingUtil.warn(LOG, "isConceptSuitableForLocalProcessing", 
                "Context too large for local processing: %d characters", context.length());
            // Still allow processing with truncated context
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
    
    /**
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for concept explanation. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses simplified prompts optimized for local models with basic explanation capabilities.";
    }
}

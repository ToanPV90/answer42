package com.samjdtechnologies.answer42.service.agent;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Abstract base class for agents using Ollama local models.
 * Provides Ollama-specific prompt optimization and error handling optimized for local processing.
 * 
 * This class serves as the foundation for all Ollama-based fallback agents, providing:
 * - Local model-optimized prompt engineering
 * - Fallback-specific error handling and logging
 * - Content truncation for resource-constrained local processing
 * - Integration with existing retry policies and rate limiting
 */
public abstract class OllamaBasedAgent extends AbstractConfigurableAgent {
    
    /**
     * Maximum content length for local processing to prevent resource exhaustion.
     */
    protected static final int MAX_LOCAL_CONTENT_LENGTH = 8000;
    
    @Autowired(required = false)
    @Qualifier("ollamaChatClient")
    private ChatClient ollamaChatClient;

    /**
     * Creates a new Ollama-based agent with the provided configuration.
     * 
     * @param aiConfig The AI configuration containing Ollama client setup
     * @param threadConfig Thread pool configuration for async processing
     * @param retryPolicy Retry policy for handling failures
     * @param rateLimiter Rate limiter for controlling request frequency
     */
    protected OllamaBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                              AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        
        LoggingUtil.info(LOG, "OllamaBasedAgent", 
            "Initialized Ollama-based fallback agent %s", getAgentType());
    }
    
    @Override
    public AIProvider getProvider() {
        return AIProvider.OLLAMA;
    }
    
    @Override
    protected ChatClient getConfiguredChatClient() {
        if (ollamaChatClient != null) {
            return ollamaChatClient;
        }
        
        LoggingUtil.warn(LOG, "getConfiguredChatClient", 
            "Ollama chat client not available - may not be enabled");
        throw new IllegalStateException("Ollama chat client not available. Check if Ollama is enabled and configured properly.");
    }
    
    /**
     * Optimizes prompts for Ollama local models.
     * Local models benefit from simpler, more direct prompts with clear instructions.
     * 
     * @param basePrompt The base prompt to optimize
     * @param variables Variables to include in the prompt
     * @return Optimized prompt for local model processing
     */
    protected Prompt optimizePromptForOllama(String basePrompt, Map<String, Object> variables) {
        String optimizedPrompt = basePrompt + "\n\n" +
            "Please provide a concise, direct response. " +
            "Focus on clarity and accuracy. " +
            "Be specific and avoid unnecessary elaboration.";
            
        return new Prompt(new UserMessage(optimizedPrompt));
    }
    
    /**
     * Creates fallback-optimized prompts that work well with smaller local models.
     * These prompts are specifically designed for fallback scenarios when cloud providers fail.
     * 
     * @param basePrompt The base prompt for the task
     * @param variables Variables to include in the prompt
     * @return Fallback-optimized prompt
     */
    protected Prompt createFallbackPrompt(String basePrompt, Map<String, Object> variables) {
        String fallbackPrompt = "FALLBACK MODE - Local Processing\n\n" + basePrompt + 
            "\n\nProvide the best possible response using available local capabilities. " +
            "Focus on core functionality and essential information.";
            
        return optimizePromptForOllama(fallbackPrompt, variables);
    }
    
    /**
     * Truncates content for local processing to prevent resource exhaustion.
     * This is critical for maintaining performance with resource-constrained local models.
     * 
     * @param content The content to truncate
     * @param maxLength Maximum allowed length
     * @return Truncated content with indication if truncation occurred
     */
    protected String truncateForLocalProcessing(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        
        if (content.length() <= maxLength) {
            return content;
        }
        
        String truncated = content.substring(0, maxLength - 100);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > maxLength / 2) {
            truncated = truncated.substring(0, lastSpace);
        }
        
        return truncated + "\n\n[CONTENT TRUNCATED FOR LOCAL PROCESSING - Original length: " + 
               content.length() + " characters]";
    }
    
    /**
     * Creates a simple processing prompt optimized for local models.
     * Reduces complexity while maintaining essential functionality.
     * 
     * @param task The task description
     * @param content The content to process
     * @return Simple, direct prompt for local processing
     */
    protected Prompt createSimplePrompt(String task, String content) {
        String truncatedContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH);
        
        String prompt = String.format(
            "Task: %s\n\n" +
            "Content:\n%s\n\n" +
            "Instructions: Provide a clear, concise response. " +
            "Focus on the essential information only.",
            task, truncatedContent
        );
        
        return new Prompt(new UserMessage(prompt));
    }
    
    /**
     * Handles local processing errors with appropriate fallback messaging.
     * 
     * @param e The exception that occurred
     * @param taskId The ID of the task that failed
     * @return Error message indicating local processing failure
     */
    protected String handleLocalProcessingError(Exception e, String taskId) {
        String errorMessage = String.format(
            "FALLBACK PROCESSING FAILED - Task %s could not be completed using local Ollama model. " +
            "Error: %s. Please try again later when cloud providers are available.",
            taskId, e.getMessage()
        );
        
        LoggingUtil.error(LOG, "handleLocalProcessingError", 
            "Local Ollama processing failed for task %s", e, taskId);
            
        return errorMessage;
    }
    
    /**
     * Validates that content is suitable for local processing.
     * 
     * @param content The content to validate
     * @return true if content can be processed locally, false otherwise
     */
    protected boolean isContentSuitableForLocalProcessing(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check for extremely long content that might cause issues
        if (content.length() > MAX_LOCAL_CONTENT_LENGTH * 3) {
            LoggingUtil.warn(LOG, "isContentSuitableForLocalProcessing", 
                "Content length %d exceeds local processing limits", content.length());
            return false;
        }
        
        return true;
    }
    
    /**
     * Creates a fallback processing note for users when local processing is used.
     * 
     * @param originalTask The original task description
     * @return User-friendly note about fallback processing
     */
    protected String createFallbackProcessingNote(String originalTask) {
        return String.format(
            "⚠️ FALLBACK MODE: This result was generated using local AI processing " +
            "because cloud providers were temporarily unavailable. " +
            "Quality may be reduced compared to normal cloud processing. " +
            "Task: %s", originalTask
        );
    }
}

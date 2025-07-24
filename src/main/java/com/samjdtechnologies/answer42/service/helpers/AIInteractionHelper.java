package com.samjdtechnologies.answer42.service.helpers;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Helper class for AI interactions.
 * Handles prompt creation and interaction with AI models.
 */
@Component
public class AIInteractionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AIInteractionHelper.class);
    
    private final AIConfig aiConfig;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param aiConfig configuration for AI providers
     */
    public AIInteractionHelper(AIConfig aiConfig) {
        this.aiConfig = aiConfig;
    }
    
    /**
     * Create a system prompt based on the chat mode and papers.
     * 
     * @param mode the chat mode
     * @param papers the papers for context
     * @return the system prompt
     */
    public String createSystemPrompt(ChatMode mode, List<Paper> papers) {
        StringBuilder prompt = new StringBuilder();
        
        // Basic role definition
        prompt.append("You are a scholarly research assistant specialized in academic papers. ");
        
        // Add mode-specific instructions
        switch (mode) {
            case CHAT:
                prompt.append("You're helping with a specific research paper. ");
                break;
            case CROSS_REFERENCE:
                prompt.append("You're comparing and contrasting multiple research papers. ");
                break;
            case RESEARCH_EXPLORER:
                prompt.append("You're helping explore a research area using multiple papers as context. ");
                break;
            default:
                prompt.append("You're helping with academic research. ");
        }
        
        // Add general guidance
        prompt.append("Respond with academic rigor. Be concise but thorough. ");
        prompt.append("Support claims with evidence from the papers. ");
        prompt.append("Acknowledge limitations and uncertainties. ");
        
        // Add paper details if available
        if (!papers.isEmpty()) {
            prompt.append("\n\nYou have access to the following papers:\n");
            
            for (int i = 0; i < papers.size(); i++) {
                Paper paper = papers.get(i);
                prompt.append(i + 1).append(". \"").append(paper.getTitle()).append("\" ");
                
                if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
                    prompt.append("by ").append(String.join(", ", paper.getAuthors())).append(". ");
                }
                
                if (paper.getPaperAbstract() != null && !paper.getPaperAbstract().isEmpty()) {
                    prompt.append("\nAbstract: ").append(paper.getPaperAbstract());
                }
                
                prompt.append("\n\n");
            }
        }
        
        // Mode-specific final instructions
        switch (mode) {
            case CHAT:
                prompt.append("Focus your answers on the specific paper content. ");
                break;
            case CROSS_REFERENCE:
                prompt.append("Focus on comparing methodologies, findings, and implications across the papers. ");
                prompt.append("Highlight agreements, disagreements, and complementary insights. ");
                break;
            case RESEARCH_EXPLORER:
                prompt.append("Help explore key concepts, gaps, and future directions in this research area. ");
                break;
        }
        
        return prompt.toString();
    }
    
    /**
     * Exception indicating that the AI provider is currently unavailable or timing out.
     */
    public static class AITimeoutException extends RuntimeException {
        private final AIProvider provider;
        
        /**
         * Constructor for creating an AITimeoutException with provider information.
         * 
         * @param provider the AI provider that timed out
         * @param message the error message
         * @param cause the underlying cause of the timeout
         */
        public AITimeoutException(AIProvider provider, String message, Throwable cause) {
            super(message, cause);
            this.provider = provider;
        }
        
        /**
         * Get the AI provider that caused the timeout.
         * 
         * @return the AI provider that timed out
         */
        public AIProvider getProvider() {
            return provider;
        }
    }
    
    /**
     * Get a response from the appropriate AI provider.
     * 
     * @param provider the AI provider to use
     * @param messages the message history
     * @return the AI response
     * @throws AITimeoutException if the AI provider times out or is unavailable
     */
    public String getAIResponse(AIProvider provider, List<Message> messages) {
        LoggingUtil.info(LOG, "getAIResponse", "Getting response from %s with %d messages", 
                provider, messages.size());
        
        ChatClient chatClient;
        
        // Get the appropriate client based on provider
        switch (provider) {
            case ANTHROPIC:
                chatClient = aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
                break;
            case OPENAI:
                chatClient = aiConfig.openAiChatClient(aiConfig.openAiChatModel(aiConfig.openAiApi()));
                break;
            case PERPLEXITY:
                chatClient = aiConfig.perplexityChatClient(aiConfig.perplexityChatModel(aiConfig.perplexityApi()));
                break;
            default:
                LoggingUtil.warn(LOG, "getAIResponse", "Unknown provider %s, falling back to Anthropic", provider);
                chatClient = aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
        }
        
        try {
            // Use the fluent API pattern with ChatClient
            String response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
            
            // Clean response from unwanted markers
            response = cleanResponseMarkers(response);
            
            return response;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            
            // Check if this is a timeout or connection error
            boolean isTimeout = isTimeoutError(e);
            
            if (isTimeout) {
                // Log as warning (not error) for timeouts
                LoggingUtil.warn(LOG, "getAIResponse", "%s API timeout or connection error: %s", provider, errorMsg);
                
                // Throw a specific exception that can be caught by callers
                throw new AITimeoutException(provider, 
                        provider + " API is currently busy. Please try again later.", e);
            } else {
                // For other errors, continue with current behavior
                LoggingUtil.error(LOG, "getAIResponse", "Error getting AI response from %s: %s", provider, errorMsg, e);
                return "I'm sorry, I encountered an error while processing your request: " + errorMsg;
            }
        }
    }
    
    /**
     * Check if an exception is related to timeout or connection issues.
     * 
     * @param e the exception to check
     * @return true if this is a timeout or connection error
     */
    private boolean isTimeoutError(Exception e) {
        String errorMsg = e.getMessage();
        if (errorMsg == null) {
            return false;
        }
        
        // Check common timeout and connection error patterns
        return errorMsg.contains("ReadTimeoutException") ||
               errorMsg.contains("I/O error on POST request") ||
               errorMsg.contains("Connection timed out") ||
               errorMsg.contains("connect timed out") ||
               errorMsg.contains("failed to connect") ||
               errorMsg.contains("Connection refused");
    }
    
    /**
     * Convert chat messages to Spring AI message format.
     * 
     * @param messages the chat messages
     * @param systemPrompt the system prompt to prepend
     * @return list of Spring AI messages
     */
    public List<Message> convertToAIMessages(List<com.samjdtechnologies.answer42.model.db.ChatMessage> messages, 
                                          String systemPrompt) {
        List<Message> aiMessages = new ArrayList<>();
        
        // Add system message with instructions
        aiMessages.add(new SystemMessage(systemPrompt));
        
        // Add previous messages
        for (com.samjdtechnologies.answer42.model.db.ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                aiMessages.add(new UserMessage(message.getContent()));
            } else if ("assistant".equals(message.getRole())) {
                aiMessages.add(new AssistantMessage(message.getContent()));
            }
        }
        
        return aiMessages;
    }
    
    /**
     * Clean response markers that may be present in AI responses.
     * This removes technical artifacts like <<HUMAN_CONVERSATION_END>> from Claude responses.
     * 
     * @param response the raw AI response
     * @return the cleaned response
     */
    private String cleanResponseMarkers(String response) {
        if (response == null) {
            return "";
        }
        
        // Remove Claude-specific markers
        String cleaned = response.replaceAll("<<HUMAN_CONVERSATION_END>>", "");
        
        // Remove any other markers that might appear
        cleaned = cleaned.replaceAll("<<[A-Z_]+>>", "");
        
        // Trim any whitespace that might be left after marker removal
        return cleaned.trim();
    }
}

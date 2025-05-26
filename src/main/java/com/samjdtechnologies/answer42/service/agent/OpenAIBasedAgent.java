package com.samjdtechnologies.answer42.service.agent;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;

/**
 * Base class for agents that use OpenAI GPT models.
 * Provides OpenAI-specific optimizations and configurations with retry policy integration.
 */
public abstract class OpenAIBasedAgent extends AbstractConfigurableAgent {

    protected OpenAIBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                              AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
    }

    @Override
    protected ChatClient getConfiguredChatClient() {
        // Uses AIConfig's OpenAI chat client with user-specific API keys
        return aiConfig.openAiChatClient(aiConfig.openAiChatModel(aiConfig.openAiApi()));
    }

    @Override
    public AIProvider getProvider() {
        return AIProvider.OPENAI;
    }

    /**
     * OpenAI-specific prompt optimization for structured outputs.
     */
    protected Prompt optimizePromptForOpenAI(String basePrompt, Map<String, Object> variables) {
        // Add OpenAI-specific optimizations like structured output formatting
        String optimizedPrompt = basePrompt + 
            "\n\nProvide output in well-structured format with clear sections and formatting.";
        PromptTemplate template = new PromptTemplate(optimizedPrompt);
        return template.create(variables);
    }

    /**
     * OpenAI-specific prompt for JSON output.
     */
    protected Prompt createJsonPrompt(String basePrompt, Map<String, Object> variables) {
        String jsonPrompt = basePrompt + 
            "\n\nReturn your response as valid JSON with the following structure: " +
            "{ \"status\": \"success|error\", \"data\": {...}, \"message\": \"...\" }";
        PromptTemplate template = new PromptTemplate(jsonPrompt);
        return template.create(variables);
    }

    /**
     * OpenAI-specific prompt for analysis tasks.
     */
    protected Prompt createAnalysisPrompt(String basePrompt, Map<String, Object> variables) {
        String analysisPrompt = basePrompt + 
            "\n\nProvide a detailed analysis with:\n" +
            "1. Summary of key findings\n" +
            "2. Supporting evidence\n" +
            "3. Confidence level (1-10)\n" +
            "4. Recommendations or next steps";
        PromptTemplate template = new PromptTemplate(analysisPrompt);
        return template.create(variables);
    }
}

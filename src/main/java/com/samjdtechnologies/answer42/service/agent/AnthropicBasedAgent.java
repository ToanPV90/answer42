package com.samjdtechnologies.answer42.service.agent;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.enums.AIProvider;

/**
 * Base class for agents that use Anthropic Claude models.
 * Provides Anthropic-specific optimizations and configurations.
 */
public abstract class AnthropicBasedAgent extends AbstractConfigurableAgent {

    protected AnthropicBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig) {
        super(aiConfig, threadConfig);
    }

    @Override
    protected ChatClient getConfiguredChatClient() {
        // Uses AIConfig's Anthropic chat client with user-specific API keys
        return aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
    }

    @Override
    public AIProvider getProvider() {
        return AIProvider.ANTHROPIC;
    }

    /**
     * Anthropic-specific prompt optimization for reasoning tasks.
     */
    protected Prompt optimizePromptForAnthropic(String basePrompt, Map<String, Object> variables) {
        // Add Anthropic-specific optimizations like thinking steps
        String optimizedPrompt = basePrompt + 
            "\n\nPlease think step by step and provide detailed reasoning for your analysis.";
        PromptTemplate template = new PromptTemplate(optimizedPrompt);
        return template.create(variables);
    }

    /**
     * Anthropic-specific prompt for quality assessment.
     */
    protected Prompt createQualityPrompt(String basePrompt, Map<String, Object> variables) {
        String qualityPrompt = basePrompt + 
            "\n\nProvide a thorough quality assessment including:\n" +
            "1. Accuracy verification\n" +
            "2. Completeness evaluation\n" +
            "3. Consistency check\n" +
            "4. Overall quality score (1-10)\n" +
            "5. Specific recommendations for improvement";
        PromptTemplate template = new PromptTemplate(qualityPrompt);
        return template.create(variables);
    }

    /**
     * Anthropic-specific prompt for summarization tasks.
     */
    protected Prompt createSummaryPrompt(String basePrompt, Map<String, Object> variables) {
        String summaryPrompt = basePrompt + 
            "\n\nCreate a comprehensive summary that:\n" +
            "1. Captures the main points accurately\n" +
            "2. Maintains appropriate length and detail\n" +
            "3. Uses clear, accessible language\n" +
            "4. Preserves important context and nuance";
        PromptTemplate template = new PromptTemplate(summaryPrompt);
        return template.create(variables);
    }
}

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
 * Base class for agents that use Perplexity models.
 * Provides Perplexity-specific optimizations for research and fact-checking with retry policy integration.
 */
public abstract class PerplexityBasedAgent extends AbstractConfigurableAgent {

    protected PerplexityBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                  AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
    }

    @Override
    protected ChatClient getConfiguredChatClient() {
        // Uses AIConfig's Perplexity chat client with user-specific API keys
        return aiConfig.perplexityChatClient(aiConfig.perplexityChatModel(aiConfig.perplexityApi()));
    }

    @Override
    public AIProvider getProvider() {
        return AIProvider.PERPLEXITY;
    }

    /**
     * Perplexity-specific prompt optimization for research queries.
     */
    protected Prompt optimizePromptForPerplexity(String basePrompt, Map<String, Object> variables) {
        // Add Perplexity-specific optimizations for research
        String optimizedPrompt = basePrompt + 
            "\n\nProvide current, well-sourced information with citations and confidence levels.";
        PromptTemplate template = new PromptTemplate(optimizedPrompt);
        return template.create(variables);
    }

    /**
     * Perplexity-specific prompt for fact verification.
     */
    protected Prompt createFactCheckPrompt(String basePrompt, Map<String, Object> variables) {
        String factCheckPrompt = basePrompt + 
            "\n\nVerify these facts against current, authoritative sources:\n" +
            "1. Confirm accuracy with multiple sources\n" +
            "2. Identify any conflicting information\n" +
            "3. Provide confidence levels (high/medium/low)\n" +
            "4. Include publication dates and source quality\n" +
            "5. Flag any outdated or disputed claims";
        PromptTemplate template = new PromptTemplate(factCheckPrompt);
        return template.create(variables);
    }

    /**
     * Perplexity-specific prompt for research discovery.
     */
    protected Prompt createResearchPrompt(String basePrompt, Map<String, Object> variables) {
        String researchPrompt = basePrompt + 
            "\n\nConduct comprehensive research including:\n" +
            "1. Current state of research on this topic\n" +
            "2. Recent developments and findings\n" +
            "3. Key researchers and institutions\n" +
            "4. Emerging trends and future directions\n" +
            "5. Related topics and cross-references\n" +
            "Include proper citations with links where available.";
        PromptTemplate template = new PromptTemplate(researchPrompt);
        return template.create(variables);
    }
}

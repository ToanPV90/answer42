package com.samjdtechnologies.answer42.config;

// Import correct classes for Spring AI 1.0.0-M8
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.support.RetryTemplate;

import io.micrometer.observation.ObservationRegistry;

@Configuration
public class AIConfig {

    @Value("${spring.ai.anthropic.api-key:test-key}")
    private String anthropicApiKey;

    @Value("${spring.ai.anthropic.chat.options.model}")
    private String anthropicModel;

    @Value("${spring.ai.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${spring.ai.openai.api-key:test-key}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.chat.completions-path}")
    private String openaiCompletionsPath;

    @Value("${spring.ai.openai.chat.options.model}")
    private String openaiModel;

    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }
    
    @Bean
    public ToolCallbackResolver toolCallbackResolver() {
        // Create a concrete implementation of ToolCallbackResolver
        return new ToolCallbackResolver() {
            @Override
            public ToolCallback resolve(String toolName) {
                return null;
            }
        };
    }
    
    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(false);
    }
    
    @Bean
    public AnthropicApi anthropicApi() {
        return new AnthropicApi(anthropicApiKey);
    }
    
    @Bean
    public ToolCallingManager toolCallingManager() {
        return new DefaultToolCallingManager(
            observationRegistry(),
            toolCallbackResolver(),
            toolExecutionExceptionProcessor()
        );
    }
    
    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder().build();
    }
    
    @Bean
    @Primary
    public AnthropicChatModel anthropicChatModel(AnthropicApi anthropicApi) {
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(anthropicModel)
                .build();
        
        return new AnthropicChatModel(
                anthropicApi, 
                options, 
                toolCallingManager(), 
                retryTemplate(), 
                observationRegistry());
    }
    
    @Bean
    @Primary
    public ChatClient anthropicChatClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel).build();
    }
    
    @Bean
    public OpenAiApi openAiApi() {
        // Use the builder pattern which properly handles all required parameters
        // including the ResponseErrorHandler
        return OpenAiApi.builder()
            .apiKey(openaiApiKey)
            .baseUrl(openaiBaseUrl)
            .build();
    }
    
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(openaiModel)
                .build();
        
        return new OpenAiChatModel(
                openAiApi, 
                options, 
                toolCallingManager(), 
                retryTemplate(), 
                observationRegistry());
    }
    
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }
}

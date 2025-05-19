package com.samjdtechnologies.answer42.config;

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

    /**
     * Creates a no-operation observation registry used for the AI model components.
     * 
     * @return A no-operation ObservationRegistry instance
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }
    
    /**
     * Creates a tool callback resolver for handling AI tool calls.
     * This implementation returns null for all tool requests as we're not
     * implementing custom tools in this configuration.
     * 
     * @return A ToolCallbackResolver implementation
     */
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
    
    /**
     * Creates a tool execution exception processor for handling errors during tool calls.
     * 
     * @return A ToolExecutionExceptionProcessor that processes exceptions during tool execution
     */
    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(false);
    }
    
    /**
     * Creates an Anthropic API client with the configured API key.
     * 
     * @return An AnthropicApi client for making requests to Anthropic's services
     */
    @Bean
    public AnthropicApi anthropicApi() {
        return new AnthropicApi(anthropicApiKey);
    }
    
    /**
     * Creates a tool calling manager for handling AI model tool integrations.
     * 
     * @return A ToolCallingManager that manages tool calls from AI models
     */
    @Bean
    public ToolCallingManager toolCallingManager() {
        return new DefaultToolCallingManager(
            observationRegistry(),
            toolCallbackResolver(),
            toolExecutionExceptionProcessor()
        );
    }
    
    /**
     * Creates a default retry template for handling transient failures in API calls.
     * 
     * @return A RetryTemplate with default settings
     */
    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder().build();
    }
    
    /**
     * Creates and configures the primary Anthropic chat model used for AI interactions.
     * 
     * @param anthropicApi The Anthropic API client
     * @return A configured AnthropicChatModel using the specified model name
     */
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
    
    /**
     * Creates the primary chat client using the Anthropic chat model.
     * 
     * @param anthropicChatModel The configured Anthropic chat model
     * @return A ChatClient that uses the Anthropic model for conversations
     */
    @Bean
    @Primary
    public ChatClient anthropicChatClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel).build();
    }
    
    /**
     * Creates an OpenAI API client with the configured API key and base URL.
     * 
     * @return An OpenAiApi client for making requests to OpenAI's services
     */
    @Bean
    public OpenAiApi openAiApi() {
        // Use the builder pattern which properly handles all required parameters
        // including the ResponseErrorHandler
        return OpenAiApi.builder()
            .apiKey(openaiApiKey)
            .baseUrl(openaiBaseUrl)
            .build();
    }
    
    /**
     * Creates and configures an OpenAI chat model for AI interactions.
     * 
     * @param openAiApi The OpenAI API client
     * @return A configured OpenAiChatModel using the specified model name
     */
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
    
    /**
     * Creates a chat client using the OpenAI chat model.
     * 
     * @param openAiChatModel The configured OpenAI chat model
     * @return A ChatClient that uses the OpenAI model for conversations
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }
}

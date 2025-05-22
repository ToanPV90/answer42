package com.samjdtechnologies.answer42.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.model.daos.UserPreferences;
import com.samjdtechnologies.answer42.service.UserPreferencesService;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import io.micrometer.observation.ObservationRegistry;

@Configuration
public class AIConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AIConfig.class);
    
    @Value("${spring.ai.anthropic.api-key:test-key}")
    private String anthropicApiKey;

    @Value("${spring.ai.anthropic.chat.options.model}")
    private String anthropicModel;
    
    @Value("${spring.ai.anthropic.chat.options.max-tokens:4000}")
    private int anthropicMaxTokens;
    
    @Value("${spring.ai.anthropic.chat.options.temperature:0.7}")
    private double anthropicTemperature;

    @Value("${spring.ai.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${spring.ai.openai.api-key:test-key}")
    private String openaiApiKey;
    
    @Value("${spring.ai.openai.chat.options.max-tokens:4000}")
    private int openaiMaxTokens;
    
    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private double openaiTemperature;
    
    @Value("${spring.ai.perplexity.base-url}")
    private String perplexityBaseUrl;

    @Value("${spring.ai.perplexity.api-key:test-key}")
    private String perplexityApiKey;
    
    @Value("${spring.ai.perplexity.chat.options.max-tokens:4000}")
    private int perplexityMaxTokens;
    
    @Value("${spring.ai.perplexity.chat.options.temperature:0.7}")
    private double perplexityTemperature;

    @Value("${spring.ai.openai.chat.completions-path}")
    private String openaiCompletionsPath;

    @Value("${spring.ai.openai.chat.options.model}")
    private String openaiModel;
    
    private final UserPreferencesService userPreferencesService;
    
    // Current user-specific API keys (cached after login)
    private String currentOpenaiApiKey;
    private String currentAnthropicApiKey;
    private String currentPerplexityApiKey;
    
    /**
     * Constructs a new AIConfig with the necessary dependencies.
     * Initializes API keys to system defaults from properties.
     * 
     * @param userPreferencesService service for accessing user preferences including API keys
     */
    public AIConfig(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
        
        // Initialize with system defaults
        this.currentOpenaiApiKey = openaiApiKey;
        this.currentAnthropicApiKey = anthropicApiKey;
        this.currentPerplexityApiKey = perplexityApiKey;
    }
    
    /**
     * Updates API keys based on user preferences when a user logs in.
     * This method should be called from the authentication process.
     * 
     * @param user The user who has logged in
     */
    public void updateKeysForUser(User user) {
        if (user == null) {
            LoggingUtil.warn(LOG, "updateKeysForUser", "Attempted to update keys for null user");
            resetToSystemDefaults();
            return;
        }
        
        try {
            UserPreferences prefs = userPreferencesService.getByUserId(user.getId());
            
            if (prefs == null) {
                LoggingUtil.debug(LOG, "updateKeysForUser", "No preferences found for user %s", user.getId());
                resetToSystemDefaults();
                return;
            }
            
            // Update OpenAI API key if user has one
            if (prefs.getOpenaiApiKey() != null && !prefs.getOpenaiApiKey().trim().isEmpty()) {
                this.currentOpenaiApiKey = prefs.getOpenaiApiKey();
                LoggingUtil.info(LOG, "updateKeysForUser", "Using custom OpenAI API key for user %s", user.getId());
            } else {
                this.currentOpenaiApiKey = openaiApiKey;
            }
            
            // Update Anthropic API key if user has one
            if (prefs.getAnthropicApiKey() != null && !prefs.getAnthropicApiKey().trim().isEmpty()) {
                this.currentAnthropicApiKey = prefs.getAnthropicApiKey();
                LoggingUtil.info(LOG, "updateKeysForUser", "Using custom Anthropic API key for user %s", user.getId());
            } else {
                this.currentAnthropicApiKey = anthropicApiKey;
            }
            
            // Update Perplexity API key if user has one
            if (prefs.getPerplexityApiKey() != null && !prefs.getPerplexityApiKey().trim().isEmpty()) {
                this.currentPerplexityApiKey = prefs.getPerplexityApiKey();
                LoggingUtil.info(LOG, "updateKeysForUser", "Using custom Perplexity API key for user %s", user.getId());
            } else {
                this.currentPerplexityApiKey = perplexityApiKey;
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updateKeysForUser", "Error updating API keys for user %s", e, user.getId());
            resetToSystemDefaults();
        }
    }
    
    /**
     * Resets all API keys to system defaults.
     * This should be called when a user logs out.
     */
    public void resetToSystemDefaults() {
        this.currentOpenaiApiKey = openaiApiKey;
        this.currentAnthropicApiKey = anthropicApiKey;
        this.currentPerplexityApiKey = perplexityApiKey;
        LoggingUtil.debug(LOG, "resetToSystemDefaults", "API keys reset to system defaults");
    }
    
    /**
     * Gets the current OpenAI API key (either user's custom key or system default).
     * 
     * @return The current OpenAI API key
     */
    private String getOpenAIKey() {
        // Always fall back to system default if current key is null
        return (currentOpenaiApiKey != null && !currentOpenaiApiKey.isEmpty()) 
            ? currentOpenaiApiKey 
            : openaiApiKey;
    }
    
    /**
     * Gets the current Anthropic API key (either user's custom key or system default).
     * 
     * @return The current Anthropic API key
     */
    private String getAnthropicKey() {
        // Always fall back to system default if current key is null
        return (currentAnthropicApiKey != null && !currentAnthropicApiKey.isEmpty()) 
            ? currentAnthropicApiKey 
            : anthropicApiKey;
    }
    
    /**
     * Gets the current Perplexity API key (either user's custom key or system default).
     * 
     * @return The current Perplexity API key
     */
    private String getPerplexityKey() {
        // Always fall back to system default if current key is null
        return (currentPerplexityApiKey != null && !currentPerplexityApiKey.isEmpty()) 
            ? currentPerplexityApiKey 
            : perplexityApiKey;
    }

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
     * Creates an Anthropic API client with the appropriate API key.
     * 
     * @return An AnthropicApi client for making requests to Anthropic's services
     */
    @Bean
    public AnthropicApi anthropicApi() {
        return new AnthropicApi(getAnthropicKey());
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
                .maxTokens(anthropicMaxTokens)
                .temperature(anthropicTemperature)
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
     * Creates an OpenAI API client with the appropriate API key and base URL.
     * This is used for standard OpenAI operations.
     * 
     * @return An OpenAiApi client for making requests to OpenAI's services
     */
    @Bean
    public OpenAiApi openAiApi() {
        // Make sure we never pass null to the API key
        String apiKey = getOpenAIKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = "dummy-key";  // Provide a non-null fallback for initialization
        }
        
        return OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(openaiBaseUrl)
            .build();
    }
    
    /**
     * Creates an OpenAI API client configured for Perplexity.
     * This uses the Perplexity API key but the OpenAI client structure.
     * 
     * @return An OpenAiApi client configured for making requests to Perplexity
     */
    @Bean
    public OpenAiApi perplexityApi() {
        // Make sure we never pass null to the API key
        String apiKey = getPerplexityKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = "dummy-key";  // Provide a non-null fallback for initialization
        }
        
        return OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(perplexityBaseUrl)
            .build();
    }
    
    /**
     * Creates and configures an OpenAI chat model for Perplexity.
     * 
     * @param perplexityApi The Perplexity API client
     * @return A configured OpenAiChatModel for Perplexity
     */
    @Bean
    public OpenAiChatModel perplexityChatModel(OpenAiApi perplexityApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("llama-3.1-sonar-small-128k-online")  // Use Perplexity's model
                .maxTokens(perplexityMaxTokens)
                .temperature(perplexityTemperature)
                .build();
        
        return new OpenAiChatModel(
                perplexityApi, 
                options, 
                toolCallingManager(), 
                retryTemplate(), 
                observationRegistry());
    }
    
    /**
     * Creates a chat client using the Perplexity chat model.
     * 
     * @param perplexityChatModel The configured Perplexity chat model
     * @return A ChatClient that uses the Perplexity model for conversations
     */
    @Bean
    public ChatClient perplexityChatClient(OpenAiChatModel perplexityChatModel) {
        return ChatClient.builder(perplexityChatModel).build();
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
                .maxTokens(openaiMaxTokens)
                .temperature(openaiTemperature)
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

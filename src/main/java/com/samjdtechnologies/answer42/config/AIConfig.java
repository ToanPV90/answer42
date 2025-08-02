package com.samjdtechnologies.answer42.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.support.RetryTemplate;

import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.model.db.UserPreferences;
import com.samjdtechnologies.answer42.service.UserPreferencesService;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
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
    
    @Value("${spring.ai.token-logging.enabled:true}")
    private boolean tokenLoggingEnabled;
    
    @Value("${spring.ai.perplexity.chat.options.model:llama-3.1-sonar-small-128k-online}")
    private String perplexityModel;
    
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.ollama.chat.options.model:llama2}")
    private String ollamaModel;
    
    @Value("${spring.ai.ollama.chat.options.max-tokens:4000}")
    private int ollamaMaxTokens;
    
    @Value("${spring.ai.ollama.chat.options.temperature:0.7}")
    private double ollamaTemperature;
    
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
     * Logs token usage information and estimated cost from a chat response.
     * 
     * @param response The chat response containing usage metadata
     * @param modelName The name of the model used for the request
     */
    public void logTokenUsage(ChatResponse response, String modelName) {
        if (!tokenLoggingEnabled || response == null || response.getMetadata() == null) {
            return;
        }
        
        var usage = response.getMetadata().getUsage();
        if (usage != null) {
            long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
            long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
            
            double estimatedCost = calculateCost(modelName, promptTokens, completionTokens);
            
            LoggingUtil.info(LOG, "logTokenUsage", 
                "Model: %s | Tokens - Prompt: %d, Completion: %d, Total: %d | Estimated Cost: $%.4f", 
                modelName, promptTokens, completionTokens, totalTokens, estimatedCost);
        }
    }
    
    /**
     * Calculates the estimated cost based on model pricing and token usage.
     * Prices are approximate and based on standard API pricing as of 2024.
     * 
     * @param modelName The name of the model used
     * @param promptTokens Number of prompt tokens
     * @param completionTokens Number of completion tokens
     * @return Estimated cost in USD
     */
    private double calculateCost(String modelName, long promptTokens, long completionTokens) {
        double promptCostPer1K = 0.0;
        double completionCostPer1K = 0.0;
        
        // Pricing per 1K tokens (approximate, update as needed)
        switch (modelName.toLowerCase()) {
            case "anthropic claude":
            case "claude-3-sonnet-20240229":
            case "claude-3-haiku-20240307":
                promptCostPer1K = 0.003;      // $3/1M tokens = $0.003/1K
                completionCostPer1K = 0.015;  // $15/1M tokens = $0.015/1K
                break;
            case "claude-3-opus-20240229":
                promptCostPer1K = 0.015;      // $15/1M tokens
                completionCostPer1K = 0.075;  // $75/1M tokens
                break;
            case "claude-4-opus":
            case "claude-opus-4-20250514":
                promptCostPer1K = 0.015;      // $15/1M tokens (estimated, similar to Claude 3 Opus)
                completionCostPer1K = 0.075;  // $75/1M tokens (estimated, similar to Claude 3 Opus)
                break;
            case "claude-4-sonnet":
            case "claude-sonnet-4-20250514":
                promptCostPer1K = 0.003;      // $3/1M tokens (estimated, similar to Claude 3 Sonnet)
                completionCostPer1K = 0.015;  // $15/1M tokens (estimated, similar to Claude 3 Sonnet)
                break;
            case "gpt-4":
            case "gpt-4-0314":
                promptCostPer1K = 0.03;       // $30/1M tokens
                completionCostPer1K = 0.06;   // $60/1M tokens
                break;
            case "gpt-4.1":
            case "gpt-4.1-turbo":
                promptCostPer1K = 0.008;      // $8/1M tokens (estimated)
                completionCostPer1K = 0.024;  // $24/1M tokens (estimated)
                break;
            case "gpt-4.1-mini":
                promptCostPer1K = 0.0001;     // $0.1/1M tokens (estimated)
                completionCostPer1K = 0.0004; // $0.4/1M tokens (estimated)
                break;
            case "gpt-4.1-nano":
                promptCostPer1K = 0.00005;    // $0.05/1M tokens (estimated)
                completionCostPer1K = 0.0002; // $0.2/1M tokens (estimated)
                break;
            case "gpt-4-turbo":
            case "gpt-4-turbo-preview":
            case "gpt-4-turbo-2024-04-09":
                promptCostPer1K = 0.01;       // $10/1M tokens
                completionCostPer1K = 0.03;   // $30/1M tokens
                break;
            case "gpt-4o":
            case "gpt-4o-2024-05-13":
                promptCostPer1K = 0.005;      // $5/1M tokens
                completionCostPer1K = 0.015;  // $15/1M tokens
                break;
            case "gpt-4o-mini":
            case "gpt-4o-mini-2024-07-18":
                promptCostPer1K = 0.00015;    // $0.15/1M tokens
                completionCostPer1K = 0.0006; // $0.6/1M tokens
                break;
            case "o1-preview":
            case "o1-preview-2024-09-12":
                promptCostPer1K = 0.015;      // $15/1M tokens
                completionCostPer1K = 0.06;   // $60/1M tokens
                break;
            case "o1-mini":
            case "o1-mini-2024-09-12":
                promptCostPer1K = 0.003;      // $3/1M tokens
                completionCostPer1K = 0.012;  // $12/1M tokens
                break;
            case "gpt-3.5-turbo":
                promptCostPer1K = 0.0005;     // $0.5/1M tokens
                completionCostPer1K = 0.0015; // $1.5/1M tokens
                break;
            case "sonar":
            case "llama-3.1-sonar-small-128k-online":
                promptCostPer1K = 0.0002;     // Perplexity pricing
                completionCostPer1K = 0.0002;
                break;
            default:
                // Generic pricing for unknown models
                promptCostPer1K = 0.001;
                completionCostPer1K = 0.002;
                LoggingUtil.debug(LOG, "calculateCost", "Unknown model pricing for: %s, using generic rates", modelName);
                break;
        }
        
        double promptCost = (promptTokens / 1000.0) * promptCostPer1K;
        double completionCost = (completionTokens / 1000.0) * completionCostPer1K;
        
        return promptCost + completionCost;
    }

    /**
     * Creates an observation registry with token usage logging capabilities.
     * 
     * @return An ObservationRegistry configured for token tracking
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        if (!tokenLoggingEnabled) {
            return ObservationRegistry.NOOP;
        }
        
        ObservationRegistry registry = ObservationRegistry.create();
        
        // Add custom observation handler for token counting
        registry.observationConfig()
            .observationHandler(new ObservationHandler<Observation.Context>() {
                @Override
                public boolean supportsContext(Observation.Context context) {
                    String name = context.getName();
                    return name != null && name.contains("spring.ai");
                }
                
                @Override
                public void onStop(Observation.Context context) {
                    if (tokenLoggingEnabled) {
                        context.getAllKeyValues().forEach(keyValue -> {
                            String key = keyValue.getKey();
                            if (key.contains("token") || key.contains("usage")) {
                                LoggingUtil.debug(LOG, "observationHandler", 
                                    "AI Observation: %s = %s", key, keyValue.getValue());
                            }
                        });
                    }
                }
            });
        
        return registry;
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
    @SuppressWarnings("deprecation")
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
                .model(perplexityModel)  // Use configured Perplexity model
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
    
    /**
     * Creates an Ollama API client for local model processing.
     * This bean is conditionally created only when Ollama is enabled.
     * 
     * @return An OllamaApi client configured with the specified base URL
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
    public OllamaApi ollamaApi() {
        return OllamaApi.builder()
            .baseUrl(ollamaBaseUrl)
            .build();
    }
    
    /**
     * Creates and configures an Ollama chat model for local fallback processing.
     * This bean is conditionally created only when Ollama is enabled.
     * 
     * @param ollamaApi The Ollama API client
     * @return A configured OllamaChatModel for local processing
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        OllamaOptions options = OllamaOptions.builder()
            .model(ollamaModel)
            .temperature(ollamaTemperature)
            .build();
        
        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(options)
            .build();
    }
    
    /**
     * Creates a chat client using the Ollama chat model for local fallback.
     * This bean is conditionally created only when Ollama is enabled.
     * 
     * @param ollamaChatModel The configured Ollama chat model
     * @return A ChatClient that uses the Ollama model for local conversations
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).build();
    }
}

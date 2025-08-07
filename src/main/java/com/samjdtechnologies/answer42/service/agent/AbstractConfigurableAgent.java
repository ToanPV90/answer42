package com.samjdtechnologies.answer42.service.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.agent.ProcessingMetrics;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.LoadStatus;
import com.samjdtechnologies.answer42.model.interfaces.AIAgent;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Abstract base class for all configurable AI agents.
 * Integrates with AIConfig for user-aware API key management, ThreadConfig for async processing,
 * and AgentRetryPolicy for enterprise-grade resilience with circuit breaker protection.
 */
public abstract class AbstractConfigurableAgent implements AIAgent {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractConfigurableAgent.class);

    protected final AIConfig aiConfig;
    protected final Executor taskExecutor;
    protected final ChatClient chatClient;
    protected final AgentRetryPolicy retryPolicy;
    protected final APIRateLimiter rateLimiter;
    
    // Token usage tracking for enterprise-grade cost monitoring
    private final LongAdder totalInputTokens = new LongAdder();
    private final LongAdder totalOutputTokens = new LongAdder();
    private final LongAdder totalRequests = new LongAdder();
    private final AtomicLong totalCostMicroCents = new AtomicLong(0); // Store in micro-cents for precision
    private final Instant instanceStartTime = Instant.now();
    
    // Static aggregated tracking across all agent instances
    private static final Map<String, LongAdder> globalInputTokens = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> globalOutputTokens = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> globalRequests = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> globalCosts = new ConcurrentHashMap<>();

    protected AbstractConfigurableAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                      AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        this.aiConfig = aiConfig;
        this.taskExecutor = threadConfig.taskExecutor();
        
        // For fallback agents, defer chat client initialization to avoid startup failures
        ChatClient tempChatClient = null;
        try {
            tempChatClient = getConfiguredChatClient();
            LoggingUtil.info(LOG, "AbstractConfigurableAgent", 
                "Successfully initialized agent %s with provider %s", 
                getAgentType(), getProvider());
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "AbstractConfigurableAgent", 
                "Chat client not available during initialization for agent %s (provider: %s) - will attempt lazy initialization: %s", 
                getAgentType(), getProvider(), e.getMessage());
        }
        
        this.chatClient = tempChatClient;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;

        // Initialize global tracking maps for this agent type
        String agentKey = getAgentType().toString() + "_" + getProvider().toString();
        globalInputTokens.putIfAbsent(agentKey, new LongAdder());
        globalOutputTokens.putIfAbsent(agentKey, new LongAdder());
        globalRequests.putIfAbsent(agentKey, new LongAdder());
        globalCosts.putIfAbsent(agentKey, new AtomicLong(0));

        LoggingUtil.info(LOG, "AbstractConfigurableAgent", 
            "Initialized agent %s with provider %s, retry policy, rate limiter, and token usage tracking", 
            getAgentType(), getProvider());
    }

    /**
     * Gets the appropriate chat client based on agent type from AIConfig.
     */
    protected abstract ChatClient getConfiguredChatClient();

    @Override
    public CompletableFuture<AgentResult> process(AgentTask task) {
        // Validate task requirements first
        try {
            validateTaskRequirements(task);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "process", 
                "Task validation failed for agent %s: %s", getAgentType(), e.getMessage());
            return CompletableFuture.completedFuture(AgentResult.failure(task.getId(), e.getMessage()));
        }

        // Execute with retry policy and circuit breaker protection (fallback agents execute directly)
        if (retryPolicy == null) {
            LoggingUtil.debug(LOG, "process", "Fallback agent %s executing directly without retry policy", getAgentType());
            return executeAgentLogic(task);
        }
        
        return retryPolicy.executeWithRetry(getAgentType(), () -> executeAgentLogic(task), task);
    }

    /**
     * Internal agent execution logic wrapped with retry policy.
     */
    private CompletableFuture<AgentResult> executeAgentLogic(AgentTask task) {
        return CompletableFuture.supplyAsync(() -> {
            Instant startTime = Instant.now();
            
            try {
                LoggingUtil.info(LOG, "executeAgentLogic", 
                    "Agent %s processing task %s using %s provider", 
                    getAgentType(), task.getId(), getProvider());

                // Process with configuration
                AgentResult result = processWithConfig(task);

                // Add processing metrics
                ProcessingMetrics metrics = createProcessingMetrics(startTime);
                result = enrichWithMetrics(result, metrics);

                LoggingUtil.info(LOG, "executeAgentLogic", 
                    "Agent %s completed task %s successfully in %d ms", 
                    getAgentType(), task.getId(), 
                    Duration.between(startTime, Instant.now()).toMillis());

                return result;

            } catch (Exception e) {
                LoggingUtil.error(LOG, "executeAgentLogic", 
                    "Agent %s failed to process task %s", e, getAgentType(), task.getId());
                
                // Determine if this is a retryable exception
                if (isRetryableException(e)) {
                    LoggingUtil.warn(LOG, "executeAgentLogic", 
                        "Retryable exception occurred for agent %s: %s", getAgentType(), e.getMessage());
                } else {
                    LoggingUtil.error(LOG, "executeAgentLogic", 
                        "Non-retryable exception occurred for agent %s: %s", getAgentType(), e.getMessage());
                }
                
                throw new RuntimeException("Agent processing failed: " + e.getMessage(), e);
            }
        }, taskExecutor);
    }

    /**
     * Determines if an exception should be retried.
     * Subclasses can override for agent-specific retry logic.
     */
    protected boolean isRetryableException(Exception e) {
        if (e == null) {
            return false;
        }
        
        // Check the entire exception chain for retryable conditions
        return isRetryableExceptionInChain(e);
    }
    
    /**
     * Recursively checks the exception chain for retryable conditions.
     */
    private boolean isRetryableExceptionInChain(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        String message = throwable.getMessage();
        String className = throwable.getClass().getName();
        if (message == null) {
            message = "";
        }
        
        // Network-related errors that are typically transient - check class name too
        if (throwable instanceof java.net.SocketTimeoutException ||
            throwable instanceof java.net.ConnectException ||
            throwable instanceof java.io.IOException ||
            className.contains("ReadTimeoutException") ||
            className.contains("WriteTimeoutException") ||
            className.contains("TimeoutException") ||
            className.contains("ConnectTimeoutException")) {
            LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
                "Detected retryable network exception: %s", throwable.getClass().getSimpleName());
            return true;
        }
        
        // Spring's ResourceAccessException usually wraps network issues
        if (throwable instanceof org.springframework.web.client.ResourceAccessException) {
            LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
                "Detected Spring ResourceAccessException, checking cause and message");
            // Also check message for I/O errors
            if (message.contains("I/O error") || message.contains("timeout")) {
                LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
                    "ResourceAccessException with retryable message: %s", message);
                return true;
            }
            return isRetryableExceptionInChain(throwable.getCause());
        }
        
        // AI provider specific errors in message - improved detection
        if (message.contains("timeout") ||
            message.contains("rate limit") ||
            message.contains("throttle") ||
            message.contains("503") ||
            message.contains("502") ||
            message.contains("504") ||
            message.contains("overloaded") ||
            message.contains("capacity") ||
            message.contains("I/O error") ||
            message.contains("Connection reset") ||
            message.contains("Connection refused") ||
            message.contains("Read timed out") ||
            message.contains("connect timed out")) {
            LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
                "Detected retryable error pattern in message: %s", message);
            return true;
        }
        
        // Anthropic-specific acceleration limit detection (429 with usage increase rate)
        if (message.contains("429") && 
            (message.contains("rate_limit_error") || 
             message.contains("usage increase rate") || 
             message.contains("acceleration limit"))) {
            LoggingUtil.info(LOG, "isRetryableExceptionInChain", 
                "Detected Anthropic acceleration limit error - will fallback to Ollama: %s", message);
            return true;
        }
        
        // Other Anthropic rate limit patterns
        if (message.contains("rate_limit_error") && message.contains("429")) {
            LoggingUtil.info(LOG, "isRetryableExceptionInChain", 
                "Detected Anthropic rate limit error - retryable for Ollama fallback: %s", message);
            return true;
        }
        
        // Authentication errors are not retryable
        if (message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthorized") ||
            message.contains("forbidden") ||
            message.contains("invalid_api_key")) {
            LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
                "Detected non-retryable auth error: %s", message);
            return false;
        }
        
        // Check the cause if we haven't found a match yet
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            boolean causeRetryable = isRetryableExceptionInChain(cause);
            if (causeRetryable) {
                LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
                    "Found retryable exception in cause chain from %s", throwable.getClass().getSimpleName());
                return true;
            }
        }
        
        // Default: don't retry unknown exceptions
        LoggingUtil.debug(LOG, "isRetryableExceptionInChain", 
            "Exception not identified as retryable: %s - %s", 
            throwable.getClass().getSimpleName(), message);
        return false;
    }

    /**
     * Core processing logic that subclasses must implement.
     */
    protected abstract AgentResult processWithConfig(AgentTask task);

    /**
     * Validates that the agent can handle this specific task.
     */
    protected void validateTaskRequirements(AgentTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        if (!canHandle(task)) {
            throw new IllegalArgumentException(
                String.format("Agent %s cannot handle task with input: %s", 
                    getAgentType(), task.getInput()));
        }
    }

    @Override
    public boolean canHandle(AgentTask task) {
        return task != null && task.getInput() != null;
    }

    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        // Default implementation - subclasses should override with specific estimates
        return Duration.ofMinutes(2);
    }

    @Override
    public LoadStatus getLoadStatus() {
        if (taskExecutor instanceof ThreadPoolTaskExecutor executor) {
            int activeThreads = executor.getActiveCount();
            int maxThreads = executor.getMaxPoolSize();
            double loadPercentage = maxThreads > 0 ? (double) activeThreads / maxThreads : 0.0;

            if (loadPercentage > 0.9) {
                return LoadStatus.HIGH;
            } else if (loadPercentage > 0.6) {
                return LoadStatus.MEDIUM;
            } else {
                return LoadStatus.LOW;
            }
        }
        return LoadStatus.LOW;
    }

    /**
     * Executes a chat interaction using the configured chat client with comprehensive token usage tracking.
     * This method includes rate limiting and is protected by retry policy when called from processWithConfig.
     */
    protected ChatResponse executePrompt(Prompt prompt) {
        String operation = String.format("chat completion request to %s", getProvider());
        
        try {
            // Lazy initialization of chat client if not available during construction
            ChatClient clientToUse = chatClient;
            if (clientToUse == null) {
                try {
                    clientToUse = getConfiguredChatClient();
                    LoggingUtil.info(LOG, "executePrompt", 
                        "Lazy-initialized chat client for %s provider", getProvider());
                } catch (Exception initEx) {
                    LoggingUtil.error(LOG, "executePrompt", 
                        "Failed to lazy-initialize chat client for %s provider", initEx, getProvider());
                    throw new RuntimeException("AI provider not available: " + initEx.getMessage(), initEx);
                }
            }
            
            // Acquire rate limit permit before making API call
            rateLimiter.acquirePermit(getProvider()).join();
            
            LoggingUtil.debug(LOG, "executePrompt", 
                "Acquired rate limit permit for %s provider", getProvider());
            
            // Execute the prompt and track token usage
            ChatResponse response = clientToUse.prompt(prompt).call().chatResponse();
            recordTokenUsage(response);
            
            return response;
        } catch (Exception e) {
            // Provide detailed, contextual error information
            String detailedError = analyzeAndFormatException(e, operation);
            
            LoggingUtil.error(LOG, "executePrompt", 
                "Failed to execute %s: %s", operation, detailedError);
            
            throw new RuntimeException(detailedError, e);
        }
    }
    
    /**
     * Analyzes an exception and provides a detailed, user-friendly error message with context.
     */
    private String analyzeAndFormatException(Throwable e, String operation) {
        StringBuilder errorBuilder = new StringBuilder();
        
        // Start with the operation context
        errorBuilder.append(String.format("Failed %s - ", operation));
        
        // Analyze the exception chain to provide specific, helpful information
        Throwable rootCause = getRootCause(e);
        
        if (rootCause instanceof java.net.SocketTimeoutException ||
            rootCause.getClass().getName().contains("ReadTimeoutException") ||
            rootCause.getClass().getName().contains("TimeoutException")) {
            
            errorBuilder.append(String.format(
                "🕐 Network timeout occurred while waiting for response from %s API. " +
                "This usually indicates network connectivity issues or high API load. " +
                "The request will be automatically retried with exponential backoff.", 
                getProvider()));
                
        } else if (rootCause instanceof java.net.ConnectException ||
                   rootCause.getClass().getName().contains("ConnectException")) {
            
            errorBuilder.append(String.format(
                "🔌 Cannot establish connection to %s API. " +
                "Please check your internet connection and verify the API endpoint is accessible. " +
                "This error will trigger automatic retry with circuit breaker protection.", 
                getProvider()));
                
        } else if (rootCause instanceof java.io.IOException &&
                   (e.getMessage() != null && e.getMessage().contains("I/O error"))) {
            
            errorBuilder.append(String.format(
                "📡 Network I/O error during %s API communication. " +
                "This could be due to network instability, firewall restrictions, or temporary API issues. " +
                "The system will attempt to retry this operation automatically.", 
                getProvider()));
                
        } else if (e instanceof org.springframework.web.client.ResourceAccessException) {
            
            errorBuilder.append(String.format(
                "🌐 Resource access error when connecting to %s API. " +
                "This typically indicates network-level issues (DNS resolution, routing, or connectivity). " +
                "The operation will be retried with fallback to Ollama if configured.", 
                getProvider()));
                
        } else if (e.getMessage() != null && 
                  (e.getMessage().contains("429") || e.getMessage().contains("rate limit"))) {
            
            errorBuilder.append(String.format(
                "⏱️ Rate limit exceeded for %s API. " +
                "Too many requests have been made recently. " +
                "The system will automatically retry with exponential backoff and may fallback to Ollama.", 
                getProvider()));
                
        } else if (e.getMessage() != null && 
                  (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
            
            errorBuilder.append(String.format(
                "🔍 %s API endpoint not found (HTTP 404). " +
                "This usually indicates incorrect API configuration: wrong base URL, invalid model name, or unsupported endpoint. " +
                "Please verify your %s configuration including API base URL and model selection. " +
                "This error is not retryable and requires configuration review.", 
                getProvider(), getProvider()));
                
        } else if (e.getMessage() != null && 
                  (e.getMessage().contains("401") || e.getMessage().contains("unauthorized"))) {
            
            errorBuilder.append(String.format(
                "🔑 Authentication failed for %s API. " +
                "Please verify your API key is valid and has proper permissions. " +
                "This error is not retryable and requires configuration review.", 
                getProvider()));
                
        } else if (e.getMessage() != null && 
                  (e.getMessage().contains("503") || e.getMessage().contains("502") || e.getMessage().contains("504"))) {
            
            errorBuilder.append(String.format(
                "🔧 %s API service is temporarily unavailable (HTTP %s). " +
                "The API provider is experiencing technical difficulties. " +
                "This will trigger automatic retry and potential fallback to Ollama.", 
                getProvider(), extractHttpCode(e.getMessage())));
                
        } else {
            // Generic case with more context
            String rootMessage = rootCause.getMessage() != null ? rootCause.getMessage() : "Unknown error";
            errorBuilder.append(String.format(
                "⚠️ Unexpected error during %s API communication: %s (%s). " +
                "This may be retryable depending on the specific error type.", 
                getProvider(), rootMessage, rootCause.getClass().getSimpleName()));
        }
        
        // Add technical details for debugging
        if (rootCause != e) {
            errorBuilder.append(String.format(" [Root cause: %s]", rootCause.getClass().getSimpleName()));
        }
        
        return errorBuilder.toString();
    }
    
    /**
     * Extracts HTTP status code from error message if present.
     */
    private String extractHttpCode(String message) {
        if (message.contains("503")) return "503 Service Unavailable";
        if (message.contains("502")) return "502 Bad Gateway";
        if (message.contains("504")) return "504 Gateway Timeout";
        if (message.contains("429")) return "429 Too Many Requests";
        if (message.contains("401")) return "401 Unauthorized";
        if (message.contains("403")) return "403 Forbidden";
        return "5xx Server Error";
    }
    
    /**
     * Gets the root cause of an exception chain.
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    /**
     * Records token usage from ChatResponse and calculates costs.
     * Tracks both instance-level and global statistics for enterprise monitoring.
     */
    private void recordTokenUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            LoggingUtil.debug(LOG, "recordTokenUsage", 
                "No usage metadata available for %s provider", getProvider());
            return;
        }

        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            LoggingUtil.debug(LOG, "recordTokenUsage", 
                "No usage information available for %s provider", getProvider());
            return;
        }

        try {
            // Extract token counts with null safety - using correct Spring AI Usage API methods
            Integer inputTokens = usage.getPromptTokens();
            Integer outputTokens = usage.getCompletionTokens(); // Correct method name for Spring AI
            Integer totalTokens = usage.getTotalTokens();

            if (inputTokens == null) inputTokens = 0;
            if (outputTokens == null) outputTokens = 0;
            if (totalTokens == null) totalTokens = inputTokens + outputTokens;

            // Record instance-level statistics
            totalInputTokens.add(inputTokens);
            totalOutputTokens.add(outputTokens);
            totalRequests.increment();

            // Record global statistics
            String agentKey = getAgentType().toString() + "_" + getProvider().toString();
            globalInputTokens.get(agentKey).add(inputTokens);
            globalOutputTokens.get(agentKey).add(outputTokens);
            globalRequests.get(agentKey).increment();

            // Calculate and record costs
            long costMicroCents = calculateCostMicroCents(inputTokens, outputTokens);
            totalCostMicroCents.addAndGet(costMicroCents);
            globalCosts.get(agentKey).addAndGet(costMicroCents);

            // Log token usage for monitoring
            BigDecimal costDollars = BigDecimal.valueOf(costMicroCents)
                .divide(BigDecimal.valueOf(100_000_000), 6, RoundingMode.HALF_UP);

            LoggingUtil.debug(LOG, "recordTokenUsage", 
                "Agent %s (%s): Input=%d, Output=%d, Total=%d tokens, Cost=$%.6f", 
                getAgentType(), getProvider(), inputTokens, outputTokens, totalTokens, costDollars);

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "recordTokenUsage", 
                "Error recording token usage for %s provider: %s", getProvider(), e.getMessage());
        }
    }

    /**
     * Calculates cost in micro-cents (1/100,000,000 of a dollar) for maximum precision.
     * Uses provider-specific pricing models.
     */
    private long calculateCostMicroCents(int inputTokens, int outputTokens) {
        switch (getProvider()) {
            case OPENAI:
                // GPT-4o pricing: $2.50 per 1M input tokens, $10.00 per 1M output tokens
                return (long) (inputTokens * 2.5 + outputTokens * 10.0);
                
            case ANTHROPIC:
                // Claude-3.5-Sonnet pricing: $3.00 per 1M input tokens, $15.00 per 1M output tokens
                return (long) (inputTokens * 3.0 + outputTokens * 15.0);
                
            case PERPLEXITY:
                // Perplexity pricing: $1.00 per 1M input tokens, $1.00 per 1M output tokens (estimated)
                return (long) (inputTokens * 1.0 + outputTokens * 1.0);
                
            case OLLAMA:
                // Local inference - no direct cost, but could factor in electricity/compute costs
                return 0L;
                
            default:
                // Unknown provider - use conservative estimate based on GPT-4 pricing
                return (long) (inputTokens * 3.0 + outputTokens * 15.0);
        }
    }

    /**
     * Gets current token usage statistics for this agent instance.
     */
    public Map<String, Object> getTokenUsageStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        long inputTokens = totalInputTokens.sum();
        long outputTokens = totalOutputTokens.sum();
        long totalTokens = inputTokens + outputTokens;
        long requests = totalRequests.sum();
        long costMicroCents = totalCostMicroCents.get();
        
        BigDecimal costDollars = BigDecimal.valueOf(costMicroCents)
            .divide(BigDecimal.valueOf(100_000_000), 6, RoundingMode.HALF_UP);
        
        stats.put("agentType", getAgentType().toString());
        stats.put("provider", getProvider().toString());
        stats.put("inputTokens", inputTokens);
        stats.put("outputTokens", outputTokens);
        stats.put("totalTokens", totalTokens);
        stats.put("totalRequests", requests);
        stats.put("totalCostDollars", costDollars);
        stats.put("avgTokensPerRequest", requests > 0 ? (double) totalTokens / requests : 0.0);
        stats.put("avgCostPerRequest", requests > 0 ? costDollars.divide(BigDecimal.valueOf(requests), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        stats.put("instanceUptimeMinutes", Duration.between(instanceStartTime, Instant.now()).toMinutes());
        
        return stats;
    }

    /**
     * Gets global token usage statistics across all agent instances of this type.
     */
    public static Map<String, Object> getGlobalTokenUsageStatistics() {
        Map<String, Object> globalStats = new ConcurrentHashMap<>();
        
        long totalGlobalInputTokens = 0;
        long totalGlobalOutputTokens = 0;
        long totalGlobalRequests = 0;
        long totalGlobalCost = 0;
        
        Map<String, Map<String, Object>> byAgentType = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, LongAdder> entry : globalInputTokens.entrySet()) {
            String agentKey = entry.getKey();
            
            long inputTokens = entry.getValue().sum();
            long outputTokens = globalOutputTokens.get(agentKey).sum();
            long requests = globalRequests.get(agentKey).sum();
            long cost = globalCosts.get(agentKey).get();
            
            totalGlobalInputTokens += inputTokens;
            totalGlobalOutputTokens += outputTokens;
            totalGlobalRequests += requests;
            totalGlobalCost += cost;
            
            Map<String, Object> agentStats = new ConcurrentHashMap<>();
            agentStats.put("inputTokens", inputTokens);
            agentStats.put("outputTokens", outputTokens);
            agentStats.put("totalTokens", inputTokens + outputTokens);
            agentStats.put("totalRequests", requests);
            agentStats.put("totalCostDollars", BigDecimal.valueOf(cost).divide(BigDecimal.valueOf(100_000_000), 6, RoundingMode.HALF_UP));
            
            byAgentType.put(agentKey, agentStats);
        }
        
        globalStats.put("totalInputTokens", totalGlobalInputTokens);
        globalStats.put("totalOutputTokens", totalGlobalOutputTokens);
        globalStats.put("totalTokens", totalGlobalInputTokens + totalGlobalOutputTokens);
        globalStats.put("totalRequests", totalGlobalRequests);
        globalStats.put("totalCostDollars", BigDecimal.valueOf(totalGlobalCost).divide(BigDecimal.valueOf(100_000_000), 6, RoundingMode.HALF_UP));
        globalStats.put("byAgentType", byAgentType);
        
        return globalStats;
    }

    /**
     * Resets token usage statistics for this agent instance.
     */
    public void resetTokenUsageStatistics() {
        totalInputTokens.reset();
        totalOutputTokens.reset();
        totalRequests.reset();
        totalCostMicroCents.set(0);
        
        LoggingUtil.info(LOG, "resetTokenUsageStatistics", 
            "Reset token usage statistics for agent %s (%s)", getAgentType(), getProvider());
    }

    /**
     * Gets token usage statistics formatted for logging.
     */
    public String getTokenUsageString() {
        Map<String, Object> stats = getTokenUsageStatistics();
        return String.format("Tokens: %d in, %d out, %d total | Requests: %d | Cost: $%.6f | Avg/req: %.1f tokens, $%.6f", 
            (Long) stats.get("inputTokens"), (Long) stats.get("outputTokens"), (Long) stats.get("totalTokens"),
            (Long) stats.get("totalRequests"), (BigDecimal) stats.get("totalCostDollars"),
            (Double) stats.get("avgTokensPerRequest"), (BigDecimal) stats.get("avgCostPerRequest"));
    }

    /**
     * Gets circuit breaker status for this agent.
     * Fallback agents return N/A status.
     */
    public String getCircuitBreakerStatus() {
        if (retryPolicy == null) {
            return "N/A (Fallback Agent)";
        }
        return retryPolicy.getCircuitBreakerStatus(getAgentType()).name();
    }

    /**
     * Gets retry statistics for this agent.
     * Fallback agents return N/A statistics.
     */
    public String getRetryStatistics() {
        if (retryPolicy == null) {
            return "N/A (Fallback Agent)";
        }
        var stats = retryPolicy.getAgentRetryStatistics(getAgentType());
        return String.format("Attempts: %d, Retries: %d, Success Rate: %.2f%%", 
            stats.getTotalAttempts(), stats.getTotalRetries(), stats.getOverallSuccessRate() * 100);
    }


    /**
     * Creates processing metrics for this agent execution.
     */
    protected ProcessingMetrics createProcessingMetrics(Instant startTime) {
        return ProcessingMetrics.builder()
            .agentType(getAgentType())
            .provider(getProvider())
            .startTime(startTime)
            .endTime(Instant.now())
            .processingTime(Duration.between(startTime, Instant.now()))
            .threadPoolStatus(getCurrentThreadPoolStatus())
            .build();
    }

    /**
     * Gets current thread pool status for metrics.
     */
    private ProcessingMetrics.ThreadPoolLoadStatus getCurrentThreadPoolStatus() {
        if (taskExecutor instanceof ThreadPoolTaskExecutor executor) {
            return ProcessingMetrics.ThreadPoolLoadStatus.builder()
                .activeThreads(executor.getActiveCount())
                .poolSize(executor.getPoolSize())
                .maximumPoolSize(executor.getMaxPoolSize())
                .queueSize(executor.getThreadPoolExecutor().getQueue().size())
                .build();
        }
        return ProcessingMetrics.ThreadPoolLoadStatus.builder().build();
    }

    /**
     * Enriches agent result with processing metrics by creating new result.
     */
    private AgentResult enrichWithMetrics(AgentResult result, ProcessingMetrics metrics) {
        return AgentResult.builder()
            .taskId(result.getTaskId())
            .success(result.isSuccess())
            .errorMessage(result.getErrorMessage())
            .resultData(result.getResultData())
            .metrics(metrics)
            .timestamp(result.getTimestamp())
            .processingTime(result.getProcessingTime())
            .usedFallback(result.isUsedFallback())
            .primaryFailureReason(result.getPrimaryFailureReason())
            .build();
    }

    /**
     * Creates a standardized task identifier for logging.
     */
    protected String getTaskIdentifier(AgentTask task) {
        return String.format("%s[%s]", getAgentType(), task.getId());
    }
}

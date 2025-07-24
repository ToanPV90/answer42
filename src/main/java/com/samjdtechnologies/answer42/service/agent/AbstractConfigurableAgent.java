package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.agent.ProcessingMetrics;
import com.samjdtechnologies.answer42.model.db.AgentTask;
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

    protected AbstractConfigurableAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                      AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        this.aiConfig = aiConfig;
        this.taskExecutor = threadConfig.taskExecutor();
        this.chatClient = getConfiguredChatClient();
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;

        LoggingUtil.info(LOG, "AbstractConfigurableAgent", 
            "Initialized agent %s with provider %s, retry policy, and rate limiter", 
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

        // Execute with retry policy and circuit breaker protection
        return retryPolicy.executeWithRetry(getAgentType(), () -> executeAgentLogic(task));
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
     * Executes a chat interaction using the configured chat client.
     * This method includes rate limiting and is protected by retry policy when called from processWithConfig.
     */
    protected ChatResponse executePrompt(Prompt prompt) {
        try {
            // Acquire rate limit permit before making API call
            rateLimiter.acquirePermit(getProvider()).join();
            
            LoggingUtil.debug(LOG, "executePrompt", 
                "Acquired rate limit permit for %s provider", getProvider());
            
            return chatClient.prompt(prompt).call().chatResponse();
        } catch (Exception e) {
            LoggingUtil.error(LOG, "executePrompt", 
                "Failed to execute prompt with %s provider", e, getProvider());
            throw new RuntimeException("AI provider communication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a chat interaction with explicit retry protection.
     * Use this for critical operations that need extra retry protection.
     */
    protected CompletableFuture<ChatResponse> executePromptWithRetry(Prompt prompt) {
        return retryPolicy.executeWithRetry(getAgentType(), () -> 
            CompletableFuture.supplyAsync(() -> executePrompt(prompt), taskExecutor)
        );
    }

    /**
     * Gets circuit breaker status for this agent.
     */
    public String getCircuitBreakerStatus() {
        return retryPolicy.getCircuitBreakerStatus(getAgentType()).name();
    }

    /**
     * Gets retry statistics for this agent.
     */
    public String getRetryStatistics() {
        var stats = retryPolicy.getAgentRetryStatistics(getAgentType());
        return String.format("Attempts: %d, Retries: %d, Success Rate: %.2f%%", 
            stats.getTotalAttempts(), stats.getTotalRetries(), stats.getSuccessRate() * 100);
    }

    /**
     * Determines if an exception should be retried.
     * Subclasses can override for agent-specific retry logic.
     */
    protected boolean isRetryableException(Exception e) {
        if (e == null) {
            return false;
        }
        
        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        
        // Network-related errors that are typically transient
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.io.IOException) {
            return true;
        }
        
        // AI provider specific errors
        if (message.contains("timeout") ||
            message.contains("rate limit") ||
            message.contains("throttle") ||
            message.contains("503") ||
            message.contains("502") ||
            message.contains("504") ||
            message.contains("overloaded") ||
            message.contains("capacity")) {
            return true;
        }
        
        // Authentication errors are not retryable
        if (message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthorized") ||
            message.contains("forbidden") ||
            message.contains("invalid_api_key")) {
            return false;
        }
        
        // Default: don't retry unknown exceptions
        return false;
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

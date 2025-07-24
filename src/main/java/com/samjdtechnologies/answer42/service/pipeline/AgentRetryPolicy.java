package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentRetryStatistics;
import com.samjdtechnologies.answer42.model.agent.RetryMetrics;
import com.samjdtechnologies.answer42.model.agent.RetryStatistics;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Retry mechanism for transient failures with exponential backoff and jitter.
 * Integrates with AgentCircuitBreaker for comprehensive failure protection.
 * Provides configurable retry policies for different agent types with comprehensive statistics tracking.
 */
@Component
public class AgentRetryPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(AgentRetryPolicy.class);
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final double JITTER_FACTOR = 0.1; // 10% jitter
    
    private final Executor taskExecutor;
    private final AgentCircuitBreaker circuitBreaker;
    
    // Statistics tracking
    private final AtomicLong totalAttempts = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final AtomicLong successfulRetries = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong circuitBreakerTrips = new AtomicLong(0);
    private final Map<AgentType, RetryMetrics> agentMetrics = new ConcurrentHashMap<>();
    private final ZonedDateTime startTime = ZonedDateTime.now();
    
    public AgentRetryPolicy(ThreadConfig threadConfig, AgentCircuitBreaker circuitBreaker) {
        this.taskExecutor = threadConfig.taskExecutor();
        this.circuitBreaker = circuitBreaker;
    }
    
    /**
     * Execute operation with default retry policy and circuit breaker protection.
     */
    public <T> CompletableFuture<T> executeWithRetry(
            Supplier<CompletableFuture<T>> operation) {
        return executeWithRetryAndCircuitBreaker(null, operation, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY);
    }
    
    /**
     * Execute operation with agent-specific retry policy and circuit breaker protection.
     */
    public <T> CompletableFuture<T> executeWithRetry(
            AgentType agentType,
            Supplier<CompletableFuture<T>> operation) {
        
        RetryConfiguration config = getRetryConfigForAgent(agentType);
        return executeWithRetryAndCircuitBreaker(agentType, operation, config.maxRetries, config.initialDelay);
    }
    
    /**
     * Execute operation with custom retry policy and circuit breaker protection.
     */
    public <T> CompletableFuture<T> executeWithRetry(
            AgentType agentType,
            Supplier<CompletableFuture<T>> operation,
            int maxRetries,
            Duration initialDelay) {
        
        return executeWithRetryAndCircuitBreaker(agentType, operation, maxRetries, initialDelay);
    }
    
    /**
     * Execute operation with integrated retry policy and circuit breaker protection.
     */
    public <T> CompletableFuture<T> executeWithRetryAndCircuitBreaker(
            AgentType agentType,
            Supplier<CompletableFuture<T>> operation,
            int maxRetries,
            Duration initialDelay) {
        
        // First check circuit breaker state
        if (agentType != null) {
            AgentCircuitBreaker.CircuitBreakerStatus status = circuitBreaker.getCircuitBreakerStatus(agentType);
            if (status == AgentCircuitBreaker.CircuitBreakerStatus.OPEN) {
                circuitBreakerTrips.incrementAndGet();
                LoggingUtil.warn(LOG, "executeWithRetryAndCircuitBreaker", 
                    "Circuit breaker is OPEN for agent %s, skipping retry attempts", agentType);
                return CompletableFuture.failedFuture(
                    new AgentCircuitBreaker.CircuitBreakerOpenException("Agent " + agentType + " is unavailable"));
            }
        }
        
        return executeWithRetry(agentType, operation, 0, maxRetries, initialDelay);
    }
    
    /**
     * Internal retry implementation with exponential backoff, circuit breaker integration, and statistics tracking.
     */
    private <T> CompletableFuture<T> executeWithRetry(
            AgentType agentType,
            Supplier<CompletableFuture<T>> operation,
            int attemptNumber,
            int maxRetries,
            Duration initialDelay) {
        
        // Record attempt
        recordAttempt(agentType, attemptNumber > 0);
        
        LoggingUtil.debug(LOG, "executeWithRetry", 
            "Executing operation attempt %d/%d for agent %s", 
            attemptNumber + 1, maxRetries + 1, agentType);
        
        // Wrap operation with circuit breaker if agent type is specified
        Supplier<CompletableFuture<T>> protectedOperation = agentType != null ?
            () -> circuitBreaker.executeWithCircuitBreaker(agentType, operation) :
            operation;
        
        return protectedOperation.get()
            .whenComplete((result, throwable) -> {
                if (throwable == null) {
                    // Success - record it
                    recordSuccess(agentType, attemptNumber > 0);
                }
            })
            .exceptionallyCompose(throwable -> {
                // Check if this is a circuit breaker exception
                if (throwable instanceof AgentCircuitBreaker.CircuitBreakerOpenException) {
                    circuitBreakerTrips.incrementAndGet();
                    LoggingUtil.warn(LOG, "executeWithRetry", 
                        "Circuit breaker opened for agent %s, stopping retries", agentType);
                    recordFailure(agentType);
                    return CompletableFuture.failedFuture(throwable);
                }
                
                if (attemptNumber >= maxRetries || !isRetryableException(throwable)) {
                    LoggingUtil.warn(LOG, "executeWithRetry", 
                        "Operation failed after %d attempts, not retrying: %s", 
                        attemptNumber + 1, throwable.getMessage());
                    
                    // Record final failure
                    recordFailure(agentType);
                    return CompletableFuture.failedFuture(throwable);
                }
                
                // Calculate delay with exponential backoff and jitter
                Duration delay = calculateDelay(initialDelay, attemptNumber);
                
                LoggingUtil.warn(LOG, "executeWithRetry", 
                    "Operation failed (attempt %d/%d), retrying in %dms: %s", 
                    attemptNumber + 1, maxRetries + 1, delay.toMillis(), throwable.getMessage());
                
                // Schedule retry with delay
                return delayedExecution(delay)
                    .thenCompose(v -> executeWithRetry(agentType, operation, attemptNumber + 1, maxRetries, initialDelay));
            });
    }
    
    /**
     * Record statistics for retry operations.
     */
    private void recordAttempt(AgentType agentType, boolean isRetry) {
        totalAttempts.incrementAndGet();
        if (isRetry) {
            totalRetries.incrementAndGet();
        }
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.computeIfAbsent(agentType, k -> RetryMetrics.builder().build());
            metrics.getTotalAttempts().incrementAndGet();
            if (isRetry) {
                metrics.getTotalRetries().incrementAndGet();
            }
        }
    }
    
    /**
     * Record successful retry outcome.
     */
    private void recordSuccess(AgentType agentType, boolean wasRetry) {
        if (wasRetry) {
            successfulRetries.incrementAndGet();
            
            if (agentType != null) {
                RetryMetrics metrics = agentMetrics.get(agentType);
                if (metrics != null) {
                    metrics.getSuccessfulRetries().incrementAndGet();
                }
            }
        }
    }
    
    /**
     * Record failed operation outcome.
     */
    private void recordFailure(AgentType agentType) {
        failedOperations.incrementAndGet();
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.get(agentType);
            if (metrics != null) {
                metrics.getFailedOperations().incrementAndGet();
            }
        }
    }
    
    /**
     * Get comprehensive retry statistics for monitoring.
     */
    public RetryStatistics getRetryStatistics() {
        long total = totalRetries.get();
        long successful = successfulRetries.get();
        double successRate = total > 0 ? (double) successful / total : 0.0;
        
        return RetryStatistics.builder()
            .totalAttempts(totalAttempts.get())
            .totalRetries(total)
            .successfulRetries(successful)
            .failedOperations(failedOperations.get())
            .successRate(successRate)
            .uptime(Duration.between(startTime, ZonedDateTime.now()))
            .trackedAgents(agentMetrics.size())
            .build();
    }
    
    /**
     * Get retry statistics for a specific agent type.
     */
    public AgentRetryStatistics getAgentRetryStatistics(AgentType agentType) {
        RetryMetrics metrics = agentMetrics.get(agentType);
        if (metrics == null) {
            return AgentRetryStatistics.builder()
                .agentType(agentType)
                .totalAttempts(0)
                .totalRetries(0)
                .successfulRetries(0)
                .successRate(0.0)
                .build();
        }
        
        long total = metrics.getTotalRetries().get();
        long successful = metrics.getSuccessfulRetries().get();
        double successRate = total > 0 ? (double) successful / total : 0.0;
        
        return AgentRetryStatistics.builder()
            .agentType(agentType)
            .totalAttempts(metrics.getTotalAttempts().get())
            .totalRetries(total)
            .successfulRetries(successful)
            .successRate(successRate)
            .build();
    }
    
    /**
     * Get circuit breaker status for an agent.
     */
    public AgentCircuitBreaker.CircuitBreakerStatus getCircuitBreakerStatus(AgentType agentType) {
        return circuitBreaker.getCircuitBreakerStatus(agentType);
    }
    
    /**
     * Reset circuit breaker for an agent (for manual recovery).
     */
    public void resetCircuitBreaker(AgentType agentType) {
        circuitBreaker.resetCircuitBreaker(agentType);
        LoggingUtil.info(LOG, "resetCircuitBreaker", 
            "Reset circuit breaker for agent %s via retry policy", agentType);
    }
    
    /**
     * Get circuit breaker trip count.
     */
    public long getCircuitBreakerTrips() {
        return circuitBreakerTrips.get();
    }
    
    /**
     * Reset statistics (for testing or periodic cleanup).
     */
    public void resetStatistics() {
        totalAttempts.set(0);
        totalRetries.set(0);
        successfulRetries.set(0);
        failedOperations.set(0);
        circuitBreakerTrips.set(0);
        agentMetrics.values().forEach(RetryMetrics::reset);
        
        LoggingUtil.info(LOG, "resetStatistics", "Reset all retry statistics");
    }
    
    /**
     * Scheduled task to log statistics periodically.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logStatistics() {
        RetryStatistics stats = getRetryStatistics();
        
        LoggingUtil.info(LOG, "logStatistics", 
            "Retry Statistics - Total Attempts: %d, Total Retries: %d, Successful Retries: %d, Success Rate: %.2f%%, Failed Operations: %d, Circuit Breaker Trips: %d",
            stats.getTotalAttempts(), stats.getTotalRetries(), stats.getSuccessfulRetries(), 
            stats.getSuccessRate() * 100, stats.getFailedOperations(), circuitBreakerTrips.get());
        
        // Log per-agent statistics if there are any
        if (!agentMetrics.isEmpty()) {
            LoggingUtil.info(LOG, "logStatistics", "Per-agent retry statistics:");
            for (AgentType agentType : agentMetrics.keySet()) {
                AgentRetryStatistics agentStats = getAgentRetryStatistics(agentType);
                AgentCircuitBreaker.CircuitBreakerStatus cbStatus = getCircuitBreakerStatus(agentType);
                LoggingUtil.info(LOG, "logStatistics", 
                    "  %s - Attempts: %d, Retries: %d, Success Rate: %.2f%%, Circuit Breaker: %s",
                    agentType, agentStats.getTotalAttempts(), agentStats.getTotalRetries(), 
                    agentStats.getSuccessRate() * 100, cbStatus);
            }
        }
    }
    
    /**
     * Calculate delay with exponential backoff and jitter.
     */
    private Duration calculateDelay(Duration initialDelay, int attemptNumber) {
        // Exponential backoff: delay = initialDelay * 2^attemptNumber
        long delayMs = initialDelay.toMillis() * (long) Math.pow(2, attemptNumber);
        
        // Add jitter to prevent thundering herd
        double jitter = 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * JITTER_FACTOR;
        delayMs = (long) (delayMs * jitter);
        
        // Cap maximum delay at 30 seconds
        delayMs = Math.min(delayMs, 30000);
        
        return Duration.ofMillis(delayMs);
    }
    
    /**
     * Create a CompletableFuture that completes after the specified delay.
     */
    private CompletableFuture<Void> delayedExecution(Duration delay) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delay.toMillis());
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, taskExecutor);
        
        return future;
    }
    
    /**
     * Determine if an exception is retryable by checking the entire exception chain.
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        // Check the entire exception chain for retryable conditions
        Throwable current = throwable;
        while (current != null) {
            if (isRetryableExceptionType(current)) {
                LoggingUtil.debug(LOG, "isRetryableException", 
                    "Found retryable exception in chain: %s", current.getClass().getSimpleName());
                return true;
            }
            
            String message = current.getMessage();
            if (message != null && isRetryableMessage(message)) {
                LoggingUtil.debug(LOG, "isRetryableException", 
                    "Found retryable message in chain: %s", message);
                return true;
            }
            
            current = current.getCause();
        }
        
        // Check for non-retryable conditions in the entire chain
        current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && isNonRetryableMessage(message)) {
                LoggingUtil.debug(LOG, "isRetryableException", 
                    "Found non-retryable message in chain: %s", message);
                return false;
            }
            
            if (current instanceof AgentCircuitBreaker.CircuitBreakerOpenException) {
                return false;
            }
            
            current = current.getCause();
        }
        
        // Default: don't retry unknown exceptions
        LoggingUtil.debug(LOG, "isRetryableException", 
            "Exception not identified as retryable: %s", throwable.getClass().getSimpleName());
        return false;
    }
    
    /**
     * Check if exception type is retryable.
     */
    private boolean isRetryableExceptionType(Throwable throwable) {
        // Network-related errors that are typically transient
        if (throwable instanceof java.net.SocketTimeoutException ||
            throwable instanceof java.net.ConnectException ||
            throwable instanceof java.io.IOException ||
            throwable instanceof io.netty.handler.timeout.ReadTimeoutException ||
            throwable instanceof io.netty.handler.timeout.WriteTimeoutException ||
            throwable instanceof java.net.SocketException ||
            throwable instanceof java.net.UnknownHostException) {
            return true;
        }
        
        // Spring web client exceptions
        if (throwable instanceof org.springframework.web.client.ResourceAccessException ||
            throwable instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if exception message indicates a retryable condition.
     */
    private boolean isRetryableMessage(String message) {
        String lowerMessage = message.toLowerCase();
        
        // HTTP client errors that might be transient
        if (lowerMessage.contains("timeout") ||
            lowerMessage.contains("connection") ||
            lowerMessage.contains("503") ||
            lowerMessage.contains("502") ||
            lowerMessage.contains("504") ||
            lowerMessage.contains("rate limit") ||
            lowerMessage.contains("throttle") ||
            lowerMessage.contains("i/o error")) {
            return true;
        }
        
        // AI provider specific errors
        if (lowerMessage.contains("quota") ||
            lowerMessage.contains("overloaded") ||
            lowerMessage.contains("capacity") ||
            lowerMessage.contains("temporarily unavailable") ||
            lowerMessage.contains("service unavailable") ||
            lowerMessage.contains("internal server error")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if exception message indicates a non-retryable condition.
     */
    private boolean isNonRetryableMessage(String message) {
        String lowerMessage = message.toLowerCase();
        
        // Authentication/authorization errors are not retryable
        if (lowerMessage.contains("401") ||
            lowerMessage.contains("403") ||
            lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("forbidden") ||
            lowerMessage.contains("invalid_api_key") ||
            lowerMessage.contains("authentication failed") ||
            lowerMessage.contains("access denied")) {
            return true;
        }
        
        // Client errors that won't be fixed by retrying
        if (lowerMessage.contains("400") ||
            lowerMessage.contains("bad request") ||
            lowerMessage.contains("malformed") ||
            lowerMessage.contains("invalid request")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get retry configuration for specific agent types.
     */
    private RetryConfiguration getRetryConfigForAgent(AgentType agentType) {
        switch (agentType) {
            case PAPER_PROCESSOR:
                // Paper processing is expensive, fewer retries with longer delays
                return new RetryConfiguration(2, Duration.ofSeconds(3));
                
            case METADATA_ENHANCER:
                // External API calls can be flaky, more retries
                return new RetryConfiguration(4, Duration.ofSeconds(2));
                
            case CONTENT_SUMMARIZER:
            case CONCEPT_EXPLAINER:
                // AI processing can have transient issues
                return new RetryConfiguration(3, Duration.ofSeconds(2));
                
            case QUALITY_CHECKER:
                // Quality checks are less critical, fewer retries
                return new RetryConfiguration(2, Duration.ofSeconds(1));
                
            case CITATION_FORMATTER:
                // Citation formatting is deterministic, minimal retries
                return new RetryConfiguration(1, Duration.ofSeconds(1));
                
            case PERPLEXITY_RESEARCHER:
                // External research API, more tolerant of failures
                return new RetryConfiguration(4, Duration.ofSeconds(3));
                
            default:
                return new RetryConfiguration(DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY);
        }
    }
    
    /**
     * Configuration for retry behavior.
     */
    private static class RetryConfiguration {
        final int maxRetries;
        final Duration initialDelay;
        
        RetryConfiguration(int maxRetries, Duration initialDelay) {
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
        }
    }
}

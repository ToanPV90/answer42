# APIRateLimiter Class Breakdown

## Overview

The `APIRateLimiter` class is a Spring component that manages API rate limiting across different AI providers to prevent quota exhaustion and ensure fair usage distribution across concurrent operations.

## Package & Imports

- **Package**: `com.samjdtechnologies.answer42.service.pipeline`
- **Key Dependencies**:
  - Spring Framework (`@Component`)
  - Java Concurrency (`CompletableFuture`, `ConcurrentHashMap`, `Executor`)
  - Custom models (`AIProvider`, `LoggingUtil`, `ThreadConfig`)

## Class Structure

### Fields

- `rateLimiters`: Map storing rate limiters for each AI provider
- `taskExecutor`: Executor for handling asynchronous operations
- `LOG`: Logger instance for the class

### Constructor

- Takes `ThreadConfig` dependency
- Initializes task executor
- Calls `initializeProviderLimiters()` to set up provider-specific limits

## Rate Limiter Configuration

### Provider Limits

| Provider   | Requests/Second | Requests/Minute |
| ---------- | --------------- | --------------- |
| OpenAI     | 3               | 200             |
| Anthropic  | 5               | 1000            |
| Perplexity | 10              | 600             |

## Core Methods

### Permit Acquisition

- **`acquirePermit(AIProvider)`**: Acquires single permit, returns `CompletableFuture<Void>`
- **`acquirePermits(AIProvider, int)`**: Acquires multiple permits for batch operations
- Both methods handle immediate acquisition or asynchronous waiting

### Status Monitoring

- **`getStatus()`**: Returns status map for all providers
- **`getStatus(AIProvider)`**: Returns status for specific provider
- **`isHighLoad(AIProvider)`**: Checks if provider is under high load
- **`isIdle(AIProvider)`**: Checks if provider is idle
- **`getLoadPercentage(AIProvider)`**: Returns load percentage (0.0-1.0)

### Management Operations

- **`resetLimiters()`**: Resets all rate limiters
- **`resetLimiter(AIProvider)`**: Resets specific provider's limiter
- **`getConfiguredProviders()`**: Returns set of configured providers
- **`isProviderConfigured(AIProvider)`**: Checks if provider is configured

### Utility Methods

- **`getStatusSummary()`**: Returns formatted string with all provider statuses

## Key Features

### Asynchronous Operation

- Uses `CompletableFuture` for non-blocking permit acquisition
- Leverages thread pool executor for concurrent operations

### Error Handling

- Proper interrupt handling in waiting scenarios
- Comprehensive logging with `LoggingUtil`
- Graceful handling of unconfigured providers

### Monitoring & Observability

- Real-time status monitoring
- Load percentage calculations
- Queue length tracking
- Request count tracking

## Dependencies

- **`ProviderRateLimiter`**: Individual rate limiter implementation
- **`RateLimiterStatus`**: Status data structure
- **`ThreadConfig`**: Thread pool configuration
- **`AIProvider`**: Enum defining AI providers

## Usage Pattern

1. Inject `APIRateLimiter` into services needing rate limiting
2. Call `acquirePermit(provider)` before making API calls
3. Monitor status using various status methods
4. Reset limiters if needed for testing/emergency situations

## Full Source Code

```java
package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Manages API rate limiting across AI providers to prevent quota exhaustion
 * and ensure fair usage distribution across concurrent operations.
 */
@Component
public class APIRateLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(APIRateLimiter.class);

    private final Map<AIProvider, ProviderRateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final Executor taskExecutor;

    public APIRateLimiter(ThreadConfig threadConfig) {
        this.taskExecutor = threadConfig.taskExecutor();
        initializeProviderLimiters();
    }

    /**
     * Initialize rate limiters for each AI provider with appropriate limits.
     */
    private void initializeProviderLimiters() {
        // OpenAI: 3 requests per second, 200 requests per minute
        rateLimiters.put(AIProvider.OPENAI, 
            new ProviderRateLimiter(AIProvider.OPENAI, 3, Duration.ofSeconds(1), 200, Duration.ofMinutes(1)));

        // Anthropic: 5 requests per second, 1000 requests per minute  
        rateLimiters.put(AIProvider.ANTHROPIC, 
            new ProviderRateLimiter(AIProvider.ANTHROPIC, 5, Duration.ofSeconds(1), 1000, Duration.ofMinutes(1)));

        // Perplexity: 10 requests per second, 600 requests per minute
        rateLimiters.put(AIProvider.PERPLEXITY, 
            new ProviderRateLimiter(AIProvider.PERPLEXITY, 10, Duration.ofSeconds(1), 600, Duration.ofMinutes(1)));

        LoggingUtil.info(LOG, "initializeProviderLimiters", 
            "Initialized rate limiters for %d AI providers", rateLimiters.size());
    }

    /**
     * Acquire a permit for the specified AI provider.
     * Returns immediately if permit is available, otherwise waits asynchronously.
     */
    public CompletableFuture<Void> acquirePermit(AIProvider provider) {
        ProviderRateLimiter limiter = rateLimiters.get(provider);
        if (limiter == null) {
            LoggingUtil.warn(LOG, "acquirePermit", 
                "No rate limiter configured for provider %s", provider);
            return CompletableFuture.completedFuture(null);
        }

        // Try immediate acquisition
        if (limiter.tryAcquire()) {
            LoggingUtil.debug(LOG, "acquirePermit", 
                "Immediate permit acquired for provider %s", provider);
            return CompletableFuture.completedFuture(null);
        }

        // Wait asynchronously for permit
        LoggingUtil.debug(LOG, "acquirePermit", 
            "Waiting for permit for provider %s", provider);

        return CompletableFuture.supplyAsync(() -> {
            try {
                limiter.acquire();
                LoggingUtil.debug(LOG, "acquirePermit", 
                    "Permit acquired after wait for provider %s", provider);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for rate limit permit", e);
            }
        }, taskExecutor);
    }

    /**
     * Acquire multiple permits for batch operations.
     */
    public CompletableFuture<Void> acquirePermits(AIProvider provider, int permits) {
        if (permits <= 1) {
            return acquirePermit(provider);
        }

        ProviderRateLimiter limiter = rateLimiters.get(provider);
        if (limiter == null) {
            LoggingUtil.warn(LOG, "acquirePermits", 
                "No rate limiter configured for provider %s", provider);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                limiter.acquire(permits);
                LoggingUtil.debug(LOG, "acquirePermits", 
                    "Acquired %d permits for provider %s", permits, provider);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for rate limit permits", e);
            }
        }, taskExecutor);
    }

    /**
     * Get current rate limiter status for monitoring.
     */
    public Map<AIProvider, RateLimiterStatus> getStatus() {
        Map<AIProvider, RateLimiterStatus> status = new ConcurrentHashMap<>();

        rateLimiters.forEach((provider, limiter) -> {
            status.put(provider, new RateLimiterStatus(
                provider,
                limiter.getAvailablePermits(),
                limiter.getQueueLength(),
                limiter.getRequestsInLastMinute(),
                limiter.getLastRequestTime()
            ));
        });

        return status;
    }

    /**
     * Get status for a specific provider.
     */
    public RateLimiterStatus getStatus(AIProvider provider) {
        ProviderRateLimiter limiter = rateLimiters.get(provider);
        if (limiter == null) {
            LoggingUtil.warn(LOG, "getStatus", 
                "No rate limiter configured for provider %s", provider);
            return null;
        }

        return new RateLimiterStatus(
            provider,
            limiter.getAvailablePermits(),
            limiter.getQueueLength(),
            limiter.getRequestsInLastMinute(),
            limiter.getLastRequestTime()
        );
    }

    /**
     * Check if a provider is currently under high load.
     */
    public boolean isHighLoad(AIProvider provider) {
        RateLimiterStatus status = getStatus(provider);
        return status != null && status.isHighLoad();
    }

    /**
     * Check if a provider is currently idle.
     */
    public boolean isIdle(AIProvider provider) {
        RateLimiterStatus status = getStatus(provider);
        return status != null && status.isIdle();
    }

    /**
     * Get load percentage for a provider (0.0 to 1.0).
     */
    public double getLoadPercentage(AIProvider provider) {
        RateLimiterStatus status = getStatus(provider);
        return status != null ? status.getLoadPercentage() : 0.0;
    }

    /**
     * Reset rate limiters (for testing or emergency situations).
     */
    public void resetLimiters() {
        LoggingUtil.warn(LOG, "resetLimiters", "Resetting all rate limiters");
        rateLimiters.values().forEach(ProviderRateLimiter::reset);
    }

    /**
     * Reset rate limiter for a specific provider.
     */
    public void resetLimiter(AIProvider provider) {
        ProviderRateLimiter limiter = rateLimiters.get(provider);
        if (limiter != null) {
            LoggingUtil.warn(LOG, "resetLimiter", "Resetting rate limiter for provider %s", provider);
            limiter.reset();
        }
    }

    /**
     * Get all configured providers.
     */
    public Set<AIProvider> getConfiguredProviders() {
        return rateLimiters.keySet();
    }

    /**
     * Check if a provider is configured.
     */
    public boolean isProviderConfigured(AIProvider provider) {
        return rateLimiters.containsKey(provider);
    }

    /**
     * Get summary statistics for all providers.
     */
    public String getStatusSummary() {
        StringBuilder summary = new StringBuilder("Rate Limiter Status:\n");

        getStatus().forEach((provider, status) -> {
            summary.append(String.format("  %s: %d available, %d queued, %.1f%% load\n",
                provider,
                status.getAvailablePermits(),
                status.getQueueLength(),
                status.getLoadPercentage() * 100
            ));
        });

        return summary.toString();
    }
}
```
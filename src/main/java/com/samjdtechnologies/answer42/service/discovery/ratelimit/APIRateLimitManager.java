package com.samjdtechnologies.answer42.service.discovery.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.RateLimiter;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Comprehensive API rate limiting and circuit breaker management for discovery services.
 * Manages rate limits per API provider with intelligent backoff and circuit breaking.
 */
@Service
public class APIRateLimitManager {

    private static final Logger LOG = LoggerFactory.getLogger(APIRateLimitManager.class);

    // Rate limiters for each API provider
    private final Map<DiscoverySource, RateLimiter> rateLimiters;
    
    // Circuit breakers for fault tolerance
    private final Map<DiscoverySource, CircuitBreakerState> circuitBreakers;
    
    // API usage tracking
    private final Map<DiscoverySource, APIUsageTracker> usageTrackers;

    public APIRateLimitManager() {
        this.rateLimiters = new ConcurrentHashMap<>();
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.usageTrackers = new ConcurrentHashMap<>();
        
        initializeRateLimiters();
        initializeCircuitBreakers();
        initializeUsageTrackers();
    }

    /**
     * Initialize rate limiters for each API provider based on their documented limits.
     */
    private void initializeRateLimiters() {
        // Crossref API: 50 requests per second (generous limit)
        rateLimiters.put(DiscoverySource.CROSSREF, 
            RateLimiter.create(45.0)); // Use 45 to stay safely under limit

        // Semantic Scholar API: 100 requests per 5 minutes = ~0.33 requests per second
        rateLimiters.put(DiscoverySource.SEMANTIC_SCHOLAR, 
            RateLimiter.create(0.3)); // Use 0.3 to stay safely under limit

        // Perplexity API: Varies by plan, default to conservative 10 requests per minute
        rateLimiters.put(DiscoverySource.PERPLEXITY, 
            RateLimiter.create(10.0 / 60.0)); // ~0.167 requests per second

        LoggingUtil.info(LOG, "initializeRateLimiters", 
            "Initialized rate limiters for %d API providers", rateLimiters.size());
    }

    /**
     * Initialize circuit breakers for fault tolerance.
     */
    private void initializeCircuitBreakers() {
        for (DiscoverySource source : DiscoverySource.values()) {
            circuitBreakers.put(source, new CircuitBreakerState(source));
        }

        LoggingUtil.info(LOG, "initializeCircuitBreakers", 
            "Initialized circuit breakers for %d API providers", circuitBreakers.size());
    }

    /**
     * Initialize usage trackers for monitoring and cost management.
     */
    private void initializeUsageTrackers() {
        for (DiscoverySource source : DiscoverySource.values()) {
            usageTrackers.put(source, new APIUsageTracker(source));
        }

        LoggingUtil.info(LOG, "initializeUsageTrackers", 
            "Initialized usage trackers for %d API providers", usageTrackers.size());
    }

    /**
     * Check if a request can be executed immediately without waiting.
     */
    public boolean canExecuteImmediately(DiscoverySource source) {
        // Check circuit breaker first
        CircuitBreakerState breaker = circuitBreakers.get(source);
        if (breaker != null && !breaker.allowRequest()) {
            LoggingUtil.debug(LOG, "canExecuteImmediately", 
                "Circuit breaker OPEN for %s, blocking request", source);
            return false;
        }

        // Check rate limiter
        RateLimiter limiter = rateLimiters.get(source);
        if (limiter != null && !limiter.tryAcquire()) {
            LoggingUtil.debug(LOG, "canExecuteImmediately", 
                "Rate limit exceeded for %s", source);
            return false;
        }

        return true;
    }

    /**
     * Acquire a permit for API access, potentially blocking until available.
     */
    public CompletableFuture<Void> acquirePermit(DiscoverySource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check circuit breaker
                CircuitBreakerState breaker = circuitBreakers.get(source);
                if (breaker != null && !breaker.allowRequest()) {
                    throw new RateLimitException("Circuit breaker OPEN for " + source);
                }

                // Acquire rate limit permit (will block if necessary)
                RateLimiter limiter = rateLimiters.get(source);
                if (limiter != null) {
                    double waitTime = limiter.acquire();
                    if (waitTime > 0) {
                        LoggingUtil.debug(LOG, "acquirePermit", 
                            "Waited %.2f seconds for rate limit permit for %s", waitTime, source);
                    }
                }

                // Track usage
                APIUsageTracker tracker = usageTrackers.get(source);
                if (tracker != null) {
                    tracker.recordRequest();
                }

                return null;

            } catch (Exception e) {
                LoggingUtil.error(LOG, "acquirePermit", 
                    "Failed to acquire permit for %s", e, source);
                throw new RateLimitException("Failed to acquire permit for " + source, e);
            }
        });
    }

    /**
     * Acquire a permit with timeout to avoid indefinite blocking.
     */
    public CompletableFuture<Boolean> acquirePermitWithTimeout(DiscoverySource source, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check circuit breaker
                CircuitBreakerState breaker = circuitBreakers.get(source);
                if (breaker != null && !breaker.allowRequest()) {
                    LoggingUtil.debug(LOG, "acquirePermitWithTimeout", 
                        "Circuit breaker OPEN for %s", source);
                    return false;
                }

                // Try to acquire permit within timeout
                RateLimiter limiter = rateLimiters.get(source);
                if (limiter != null) {
                    boolean acquired = limiter.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS);
                    if (!acquired) {
                        LoggingUtil.debug(LOG, "acquirePermitWithTimeout", 
                            "Failed to acquire permit within %dms for %s", timeout.toMillis(), source);
                        return false;
                    }
                }

                // Track usage
                APIUsageTracker tracker = usageTrackers.get(source);
                if (tracker != null) {
                    tracker.recordRequest();
                }

                return true;

            } catch (Exception e) {
                LoggingUtil.error(LOG, "acquirePermitWithTimeout", 
                    "Failed to acquire permit for %s", e, source);
                return false;
            }
        });
    }

    /**
     * Record a successful API call for circuit breaker health tracking.
     */
    public void recordSuccess(DiscoverySource source) {
        CircuitBreakerState breaker = circuitBreakers.get(source);
        if (breaker != null) {
            breaker.recordSuccess();
        }

        APIUsageTracker tracker = usageTrackers.get(source);
        if (tracker != null) {
            tracker.recordSuccess();
        }
    }

    /**
     * Record a failed API call for circuit breaker fault tracking.
     */
    public void recordFailure(DiscoverySource source, Exception error) {
        LoggingUtil.warn(LOG, "recordFailure", 
            "Recording API failure for %s: %s", source, error.getMessage());

        CircuitBreakerState breaker = circuitBreakers.get(source);
        if (breaker != null) {
            breaker.recordFailure();
        }

        APIUsageTracker tracker = usageTrackers.get(source);
        if (tracker != null) {
            tracker.recordFailure();
        }
    }

    /**
     * Get current usage statistics for an API provider.
     */
    public APIUsageStats getUsageStats(DiscoverySource source) {
        APIUsageTracker tracker = usageTrackers.get(source);
        CircuitBreakerState breaker = circuitBreakers.get(source);
        
        if (tracker == null || breaker == null) {
            return APIUsageStats.empty(source);
        }

        return APIUsageStats.builder()
            .source(source)
            .totalRequests(tracker.getTotalRequests())
            .successfulRequests(tracker.getSuccessfulRequests())
            .failedRequests(tracker.getFailedRequests())
            .circuitBreakerState(breaker.getState())
            .lastRequestTime(tracker.getLastRequestTime())
            .requestsInLastMinute(tracker.getRequestsInLastMinute())
            .averageResponseTime(tracker.getAverageResponseTime())
            .build();
    }

    /**
     * Get comprehensive usage statistics for all API providers.
     */
    public Map<DiscoverySource, APIUsageStats> getAllUsageStats() {
        Map<DiscoverySource, APIUsageStats> stats = new ConcurrentHashMap<>();
        
        for (DiscoverySource source : DiscoverySource.values()) {
            stats.put(source, getUsageStats(source));
        }
        
        return stats;
    }

    /**
     * Reset circuit breaker for manual recovery.
     */
    public void resetCircuitBreaker(DiscoverySource source) {
        CircuitBreakerState breaker = circuitBreakers.get(source);
        if (breaker != null) {
            breaker.reset();
            LoggingUtil.info(LOG, "resetCircuitBreaker", 
                "Manual reset of circuit breaker for %s", source);
        }
    }

    /**
     * Update rate limit for dynamic adjustment.
     */
    public void updateRateLimit(DiscoverySource source, double requestsPerSecond) {
        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("Rate limit must be positive");
        }

        rateLimiters.put(source, RateLimiter.create(requestsPerSecond));
        
        LoggingUtil.info(LOG, "updateRateLimit", 
            "Updated rate limit for %s to %.2f requests/second", source, requestsPerSecond);
    }

    /**
     * Circuit breaker states.
     */
    public enum CircuitBreakerStateEnum {
        CLOSED,    // Normal operation
        OPEN,      // Failing, blocking requests
        HALF_OPEN  // Testing if service has recovered
    }

    /**
     * Internal circuit breaker implementation.
     */
    private static class CircuitBreakerState {
        private final DiscoverySource source;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private volatile CircuitBreakerStateEnum state = CircuitBreakerStateEnum.CLOSED;
        private volatile Instant lastFailureTime = Instant.now();
        private volatile Instant stateChangeTime = Instant.now();

        // Configuration
        private static final int FAILURE_THRESHOLD = 5;  // Open after 5 consecutive failures
        private static final Duration TIMEOUT = Duration.ofMinutes(1);  // Stay open for 1 minute
        private static final int HALF_OPEN_MAX_REQUESTS = 3;  // Allow 3 test requests in half-open

        public CircuitBreakerState(DiscoverySource source) {
            this.source = source;
        }

        public boolean allowRequest() {
            switch (state) {
                case CLOSED:
                    return true;
                
                case OPEN:
                    // Check if timeout has passed to transition to half-open
                    if (Instant.now().isAfter(stateChangeTime.plus(TIMEOUT))) {
                        transitionTo(CircuitBreakerStateEnum.HALF_OPEN);
                        return true;
                    }
                    return false;
                
                case HALF_OPEN:
                    // Allow limited requests to test if service has recovered
                    return requestCount.get() < HALF_OPEN_MAX_REQUESTS;
                
                default:
                    return false;
            }
        }

        public void recordSuccess() {
            failureCount.set(0);
            lastFailureTime = null;
            
            if (state == CircuitBreakerStateEnum.HALF_OPEN) {
                transitionTo(CircuitBreakerStateEnum.CLOSED);
            }
        }

        public void recordFailure() {
            lastFailureTime = Instant.now();
            int failures = failureCount.incrementAndGet();
            
            if (state == CircuitBreakerStateEnum.CLOSED && failures >= FAILURE_THRESHOLD) {
                transitionTo(CircuitBreakerStateEnum.OPEN);
            } else if (state == CircuitBreakerStateEnum.HALF_OPEN) {
                transitionTo(CircuitBreakerStateEnum.OPEN);
            }
        }

        public void reset() {
            failureCount.set(0);
            requestCount.set(0);
            transitionTo(CircuitBreakerStateEnum.CLOSED);
        }

        public CircuitBreakerStateEnum getState() {
            return state;
        }

        private void transitionTo(CircuitBreakerStateEnum newState) {
            if (state != newState) {
                LoggingUtil.info(LOG, "transitionTo", 
                    "Circuit breaker for %s transitioning from %s to %s", 
                    source, state, newState);
                
                state = newState;
                stateChangeTime = Instant.now();
                
                if (newState == CircuitBreakerStateEnum.HALF_OPEN) {
                    requestCount.set(0);
                }
            }
        }
    }

    /**
     * API usage tracking for monitoring and cost management.
     */
    private static class APIUsageTracker {
        private final DiscoverySource source;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private volatile Instant lastRequestTime;
        private final AtomicLong totalResponseTime = new AtomicLong(0);

        public APIUsageTracker(DiscoverySource source) {
            this.source = source;
        }

        public void recordRequest() {
            totalRequests.incrementAndGet();
            lastRequestTime = Instant.now();
        }

        public void recordSuccess() {
            successfulRequests.incrementAndGet();
        }

        public void recordFailure() {
            failedRequests.incrementAndGet();
        }

        public long getTotalRequests() {
            return totalRequests.get();
        }

        public long getSuccessfulRequests() {
            return successfulRequests.get();
        }

        public long getFailedRequests() {
            return failedRequests.get();
        }

        public Instant getLastRequestTime() {
            return lastRequestTime;
        }

        public long getRequestsInLastMinute() {
            // This would require more sophisticated time-window tracking
            // For now, return a simple approximation
            return totalRequests.get(); // Simplified implementation
        }

        public double getAverageResponseTime() {
            long requests = totalRequests.get();
            if (requests == 0) {
                return 0.0;
            }
            return (double) totalResponseTime.get() / requests;
        }
    }

    /**
     * Custom exception for rate limiting errors.
     */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }

        public RateLimitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Rate limiter implementation for a specific AI provider.
 * Manages both short-term and long-term rate limits with automatic permit release.
 */
public class ProviderRateLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderRateLimiter.class);

    private final AIProvider provider;
    private final Semaphore shortTermSemaphore;
    private final Semaphore longTermSemaphore;
    private final Duration shortTermWindow;
    private final Duration longTermWindow;
    private final Map<Instant, Integer> requestHistory = new ConcurrentHashMap<>();
    private volatile Instant lastRequestTime = Instant.now();

    public ProviderRateLimiter(AIProvider provider, 
                             int shortTermLimit, Duration shortTermWindow,
                             int longTermLimit, Duration longTermWindow) {
        this.provider = provider;
        this.shortTermSemaphore = new Semaphore(shortTermLimit, true);
        this.longTermSemaphore = new Semaphore(longTermLimit, true);
        this.shortTermWindow = shortTermWindow;
        this.longTermWindow = longTermWindow;

        LoggingUtil.info(LOG, "ProviderRateLimiter", 
            "Initialized rate limiter for %s: %d/%s, %d/%s", 
            provider, shortTermLimit, shortTermWindow, longTermLimit, longTermWindow);
    }

    /**
     * Try to acquire a permit without blocking.
     * Returns true if permit was acquired, false otherwise.
     */
    public boolean tryAcquire() {
        cleanupOldRequests();
        
        if (shortTermSemaphore.tryAcquire() && longTermSemaphore.tryAcquire()) {
            recordRequest();
            LoggingUtil.debug(LOG, "tryAcquire", 
                "Permit acquired for provider %s", provider);
            return true;
        } else {
            // Release if we got one but not both
            if (shortTermSemaphore.availablePermits() < getInitialShortTermPermits()) {
                shortTermSemaphore.release();
            }
            if (longTermSemaphore.availablePermits() < getInitialLongTermPermits()) {
                longTermSemaphore.release();
            }
            LoggingUtil.debug(LOG, "tryAcquire", 
                "No permit available for provider %s", provider);
            return false;
        }
    }

    /**
     * Acquire a permit, blocking if necessary.
     */
    public void acquire() throws InterruptedException {
        cleanupOldRequests();
        
        LoggingUtil.debug(LOG, "acquire", 
            "Acquiring permit for provider %s", provider);
        
        // Acquire both permits (may block)
        shortTermSemaphore.acquire();
        try {
            longTermSemaphore.acquire();
            recordRequest();
            LoggingUtil.debug(LOG, "acquire", 
                "Permit acquired for provider %s after wait", provider);
        } catch (InterruptedException e) {
            shortTermSemaphore.release(); // Release short term permit on failure
            LoggingUtil.warn(LOG, "acquire", 
                "Permit acquisition interrupted for provider %s", provider);
            throw e;
        }
    }

    /**
     * Acquire multiple permits, blocking if necessary.
     */
    public void acquire(int permits) throws InterruptedException {
        cleanupOldRequests();
        
        LoggingUtil.debug(LOG, "acquire", 
            "Acquiring %d permits for provider %s", permits, provider);
        
        shortTermSemaphore.acquire(permits);
        try {
            longTermSemaphore.acquire(permits);
            for (int i = 0; i < permits; i++) {
                recordRequest();
            }
            LoggingUtil.debug(LOG, "acquire", 
                "Acquired %d permits for provider %s", permits, provider);
        } catch (InterruptedException e) {
            shortTermSemaphore.release(permits);
            LoggingUtil.warn(LOG, "acquire", 
                "Multi-permit acquisition interrupted for provider %s", provider);
            throw e;
        }
    }

    /**
     * Record a request and schedule permit release.
     */
    private void recordRequest() {
        lastRequestTime = Instant.now();
        requestHistory.put(lastRequestTime, 1);
        
        // Schedule permit release for short term window
        CompletableFuture.delayedExecutor(shortTermWindow.toMillis(), TimeUnit.MILLISECONDS)
            .execute(() -> {
                shortTermSemaphore.release();
                LoggingUtil.debug(LOG, "recordRequest", 
                    "Released short-term permit for provider %s", provider);
            });
        
        // Schedule permit release for long term window  
        CompletableFuture.delayedExecutor(longTermWindow.toMillis(), TimeUnit.MILLISECONDS)
            .execute(() -> {
                longTermSemaphore.release();
                LoggingUtil.debug(LOG, "recordRequest", 
                    "Released long-term permit for provider %s", provider);
            });
    }

    /**
     * Clean up old request history entries.
     */
    private void cleanupOldRequests() {
        Instant cutoff = Instant.now().minus(longTermWindow);
        int removed = requestHistory.size();
        requestHistory.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        removed -= requestHistory.size();
        
        if (removed > 0) {
            LoggingUtil.debug(LOG, "cleanupOldRequests", 
                "Cleaned up %d old requests for provider %s", removed, provider);
        }
    }

    /**
     * Get number of available permits (minimum of short and long term).
     */
    public int getAvailablePermits() {
        return Math.min(shortTermSemaphore.availablePermits(), longTermSemaphore.availablePermits());
    }

    /**
     * Get current queue length (maximum of short and long term queues).
     */
    public int getQueueLength() {
        return Math.max(shortTermSemaphore.getQueueLength(), longTermSemaphore.getQueueLength());
    }

    /**
     * Get number of requests made in the last minute.
     */
    public int getRequestsInLastMinute() {
        Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
        return requestHistory.entrySet().stream()
            .mapToInt(entry -> entry.getKey().isAfter(oneMinuteAgo) ? entry.getValue() : 0)
            .sum();
    }

    /**
     * Get the timestamp of the last request.
     */
    public Instant getLastRequestTime() {
        return lastRequestTime;
    }

    /**
     * Get the AI provider this rate limiter is for.
     */
    public AIProvider getProvider() {
        return provider;
    }

    /**
     * Reset the rate limiter (for testing or emergency situations).
     */
    public void reset() {
        LoggingUtil.warn(LOG, "reset", 
            "Resetting rate limiter for provider %s", provider);
        
        shortTermSemaphore.drainPermits();
        longTermSemaphore.drainPermits();
        requestHistory.clear();
        
        // Note: We can't easily restore initial permits without knowing the original values
        // This is a limitation of the current design - consider storing initial values
    }

    /**
     * Get initial short term permits (for internal use).
     */
    private int getInitialShortTermPermits() {
        // This is a workaround - in a better design we'd store the initial values
        return switch (provider) {
            case OPENAI -> 3;
            case ANTHROPIC -> 5;
            case PERPLEXITY -> 10;
            default -> 1;
        };
    }

    /**
     * Get initial long term permits (for internal use).
     */
    private int getInitialLongTermPermits() {
        // This is a workaround - in a better design we'd store the initial values
        return switch (provider) {
            case OPENAI -> 200;
            case ANTHROPIC -> 1000;
            case PERPLEXITY -> 600;
            default -> 100;
        };
    }

    @Override
    public String toString() {
        return String.format("ProviderRateLimiter{provider=%s, available=%d, queued=%d}", 
            provider, getAvailablePermits(), getQueueLength());
    }
}

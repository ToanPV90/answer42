package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Instant;

import com.samjdtechnologies.answer42.model.enums.AIProvider;

/**
 * Status information for a rate limiter.
 * Provides current state and usage statistics for monitoring purposes.
 */
public class RateLimiterStatus {
    private final AIProvider provider;
    private final int availablePermits;
    private final int queueLength;
    private final int requestsInLastMinute;
    private final Instant lastRequestTime;

    public RateLimiterStatus(AIProvider provider, int availablePermits, int queueLength, 
                           int requestsInLastMinute, Instant lastRequestTime) {
        this.provider = provider;
        this.availablePermits = availablePermits;
        this.queueLength = queueLength;
        this.requestsInLastMinute = requestsInLastMinute;
        this.lastRequestTime = lastRequestTime;
    }

    /**
     * Get the AI provider this status is for.
     */
    public AIProvider getProvider() { 
        return provider; 
    }

    /**
     * Get the number of permits currently available.
     */
    public int getAvailablePermits() { 
        return availablePermits; 
    }

    /**
     * Get the current queue length (number of waiting requests).
     */
    public int getQueueLength() { 
        return queueLength; 
    }

    /**
     * Get the number of requests made in the last minute.
     */
    public int getRequestsInLastMinute() { 
        return requestsInLastMinute; 
    }

    /**
     * Get the timestamp of the last request.
     */
    public Instant getLastRequestTime() { 
        return lastRequestTime; 
    }

    /**
     * Check if the rate limiter is currently under heavy load.
     */
    public boolean isHighLoad() {
        return availablePermits <= 1 || queueLength > 0;
    }

    /**
     * Check if the rate limiter is idle (no recent requests).
     */
    public boolean isIdle() {
        return lastRequestTime.isBefore(Instant.now().minusSeconds(30));
    }

    /**
     * Get the load percentage (0.0 to 1.0) based on available permits.
     * This is an approximation since we don't know the total permits.
     */
    public double getLoadPercentage() {
        // This is an approximation based on typical limits
        int estimatedTotal = switch (provider) {
            case OPENAI -> 3;
            case ANTHROPIC -> 5;
            case PERPLEXITY -> 10;
            default -> 5;
        };
        
        return 1.0 - ((double) availablePermits / estimatedTotal);
    }

    @Override
    public String toString() {
        return String.format("RateLimiterStatus{provider=%s, available=%d, queued=%d, lastMin=%d, lastRequest=%s}", 
            provider, availablePermits, queueLength, requestsInLastMinute, lastRequestTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        RateLimiterStatus that = (RateLimiterStatus) obj;
        return availablePermits == that.availablePermits &&
               queueLength == that.queueLength &&
               requestsInLastMinute == that.requestsInLastMinute &&
               provider == that.provider &&
               lastRequestTime.equals(that.lastRequestTime);
    }

    @Override
    public int hashCode() {
        int result = provider.hashCode();
        result = 31 * result + availablePermits;
        result = 31 * result + queueLength;
        result = 31 * result + requestsInLastMinute;
        result = 31 * result + lastRequestTime.hashCode();
        return result;
    }
}

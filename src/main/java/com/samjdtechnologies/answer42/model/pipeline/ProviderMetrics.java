package com.samjdtechnologies.answer42.model.pipeline;

import java.time.Duration;

import com.samjdtechnologies.answer42.model.enums.AIProvider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metrics tracking for AI provider performance and usage.
 * Tracks API calls, response times, token usage, and success rates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderMetrics {
    private AIProvider provider;
    private long totalApiCalls;
    private long successfulApiCalls;
    private long totalResponseTimeMs;
    private long totalTokensUsed;
    private long minResponseTimeMs;
    private long maxResponseTimeMs;
    private long totalExecutions;
    private long successfulExecutions;
    private long totalExecutionTimeMs;

    /**
     * Record an API call to this provider.
     */
    public void recordApiCall(Duration responseTime, boolean success, int tokenCount) {
        totalApiCalls++;
        
        if (success) {
            successfulApiCalls++;
        }
        
        long timeMs = responseTime.toMillis();
        totalResponseTimeMs += timeMs;
        totalTokensUsed += tokenCount;
        
        // Update min/max response times
        if (minResponseTimeMs == 0 || timeMs < minResponseTimeMs) {
            minResponseTimeMs = timeMs;
        }
        if (timeMs > maxResponseTimeMs) {
            maxResponseTimeMs = timeMs;
        }
    }

    /**
     * Record an agent execution using this provider.
     */
    public void recordExecution(Duration executionTime, boolean success) {
        totalExecutions++;
        
        if (success) {
            successfulExecutions++;
        }
        
        totalExecutionTimeMs += executionTime.toMillis();
    }

    /**
     * Get API success rate as percentage (0.0 to 1.0).
     */
    public double getApiSuccessRate() {
        return totalApiCalls > 0 ? (double) successfulApiCalls / totalApiCalls : 0.0;
    }

    /**
     * Get execution success rate as percentage (0.0 to 1.0).
     */
    public double getExecutionSuccessRate() {
        return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions : 0.0;
    }

    /**
     * Get average API response time.
     */
    public Duration getAverageResponseTime() {
        return totalApiCalls > 0 ? 
            Duration.ofMillis(totalResponseTimeMs / totalApiCalls) : 
            Duration.ZERO;
    }

    /**
     * Get average execution time.
     */
    public Duration getAverageExecutionTime() {
        return totalExecutions > 0 ? 
            Duration.ofMillis(totalExecutionTimeMs / totalExecutions) : 
            Duration.ZERO;
    }

    /**
     * Get minimum response time.
     */
    public Duration getMinResponseTime() {
        return Duration.ofMillis(minResponseTimeMs);
    }

    /**
     * Get maximum response time.
     */
    public Duration getMaxResponseTime() {
        return Duration.ofMillis(maxResponseTimeMs);
    }

    /**
     * Get number of failed API calls.
     */
    public long getFailedApiCalls() {
        return totalApiCalls - successfulApiCalls;
    }

    /**
     * Get average tokens per API call.
     */
    public double getAverageTokensPerCall() {
        return totalApiCalls > 0 ? (double) totalTokensUsed / totalApiCalls : 0.0;
    }

    /**
     * Get number of failed executions.
     */
    public long getFailedExecutions() {
        return totalExecutions - successfulExecutions;
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        totalApiCalls = 0;
        successfulApiCalls = 0;
        totalResponseTimeMs = 0;
        totalTokensUsed = 0;
        minResponseTimeMs = 0;
        maxResponseTimeMs = 0;
        totalExecutions = 0;
        successfulExecutions = 0;
        totalExecutionTimeMs = 0;
    }

    /**
     * Get a summary string for monitoring.
     */
    public String getSummary() {
        return String.format(
            "ProviderMetrics[provider=%s, apiCalls=%d, apiSuccess=%.1f%%, avgResponse=%dms, tokens=%d, executions=%d, execSuccess=%.1f%%]",
            provider != null ? provider.name() : "unknown",
            totalApiCalls,
            getApiSuccessRate() * 100,
            getAverageResponseTime().toMillis(),
            totalTokensUsed,
            totalExecutions,
            getExecutionSuccessRate() * 100
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }
}

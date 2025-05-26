package com.samjdtechnologies.answer42.model.pipeline;

import java.time.Duration;

import com.samjdtechnologies.answer42.model.enums.AgentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metrics tracking for individual agent performance.
 * Tracks execution counts, success rates, and timing statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetrics {
    private AgentType agentType;
    private long totalExecutions;
    private long successfulExecutions;
    private long totalExecutionTimeMs;
    private long minExecutionTimeMs;
    private long maxExecutionTimeMs;

    /**
     * Record a single agent execution.
     */
    public void recordExecution(Duration executionTime, boolean success) {
        totalExecutions++;
        
        if (success) {
            successfulExecutions++;
        }
        
        long timeMs = executionTime.toMillis();
        totalExecutionTimeMs += timeMs;
        
        // Update min/max execution times
        if (minExecutionTimeMs == 0 || timeMs < minExecutionTimeMs) {
            minExecutionTimeMs = timeMs;
        }
        if (timeMs > maxExecutionTimeMs) {
            maxExecutionTimeMs = timeMs;
        }
    }

    /**
     * Get success rate as percentage (0.0 to 1.0).
     */
    public double getSuccessRate() {
        return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions : 0.0;
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
     * Get minimum execution time.
     */
    public Duration getMinExecutionTime() {
        return Duration.ofMillis(minExecutionTimeMs);
    }

    /**
     * Get maximum execution time.
     */
    public Duration getMaxExecutionTime() {
        return Duration.ofMillis(maxExecutionTimeMs);
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
        totalExecutions = 0;
        successfulExecutions = 0;
        totalExecutionTimeMs = 0;
        minExecutionTimeMs = 0;
        maxExecutionTimeMs = 0;
    }

    /**
     * Get a summary string for monitoring.
     */
    public String getSummary() {
        return String.format(
            "AgentMetrics[type=%s, executions=%d, success=%.1f%%, avgTime=%dms]",
            agentType != null ? agentType.name() : "unknown",
            totalExecutions,
            getSuccessRate() * 100,
            getAverageExecutionTime().toMillis()
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }
}

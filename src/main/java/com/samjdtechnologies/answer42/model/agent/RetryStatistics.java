package com.samjdtechnologies.answer42.model.agent;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Overall retry statistics for monitoring.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryStatistics {
    
    private long totalAttempts;
    private long totalRetries;
    private long successfulRetries;
    private long failedOperations;
    private double successRate;
    private Duration uptime;
    private int trackedAgents;
    
    @Override
    public String toString() {
        return String.format(
            "RetryStatistics{attempts=%d, retries=%d, successful=%d, failed=%d, successRate=%.2f%%, uptime=%s, agents=%d}",
            totalAttempts, totalRetries, successfulRetries, failedOperations, 
            successRate * 100, uptime, trackedAgents
        );
    }
}

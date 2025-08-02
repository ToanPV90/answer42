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
    private long successfulOperations;
    private long successfulRetries;
    private long failedOperations;
    private double overallSuccessRate;
    private double retrySuccessRate;
    private Duration uptime;
    private int trackedAgents;
    
    @Override
    public String toString() {
        return String.format(
            "RetryStatistics{attempts=%d, retries=%d, successfulOps=%d, successfulRetries=%d, failed=%d, overallSuccess=%.2f%%, retrySuccess=%.2f%%, uptime=%s, agents=%d}",
            totalAttempts, totalRetries, successfulOperations, successfulRetries, failedOperations, 
            overallSuccessRate * 100, retrySuccessRate * 100, uptime, trackedAgents
        );
    }
}

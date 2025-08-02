package com.samjdtechnologies.answer42.model.agent;

import com.samjdtechnologies.answer42.model.enums.AgentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent-specific retry statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRetryStatistics {
    
    private AgentType agentType;
    private long totalAttempts;
    private long totalRetries;
    private long successfulOperations;
    private long successfulRetries;
    private double overallSuccessRate;
    private double retrySuccessRate;
    
    // Phase 5.2: Fallback metrics
    private long fallbackAttempts;
    private long fallbackSuccesses;
    private double fallbackSuccessRate;
    private String preferredFallbackModel;
    
    @Override
    public String toString() {
        return String.format(
            "AgentRetryStats{agent=%s, attempts=%d, retries=%d, successfulOps=%d, successfulRetries=%d, overallSuccess=%.2f%%, retrySuccess=%.2f%%}",
            agentType, totalAttempts, totalRetries, successfulOperations, successfulRetries, 
            overallSuccessRate * 100, retrySuccessRate * 100
        );
    }
}

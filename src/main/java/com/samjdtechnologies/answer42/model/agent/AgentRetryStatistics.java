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
    private long successfulRetries;
    private double successRate;
    
    @Override
    public String toString() {
        return String.format(
            "AgentRetryStats{agent=%s, attempts=%d, retries=%d, successful=%d, successRate=%.2f%%}",
            agentType, totalAttempts, totalRetries, successfulRetries, successRate * 100
        );
    }
}

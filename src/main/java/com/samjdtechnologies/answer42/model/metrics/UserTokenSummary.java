package com.samjdtechnologies.answer42.model.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of token usage for a specific user.
 * Used for user rankings and overview displays.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTokenSummary {
    
    /**
     * User identifier.
     */
    private UUID userId;
    
    /**
     * Total tokens consumed by this user.
     */
    private int totalTokens;
    
    /**
     * Total cost incurred by this user.
     */
    private BigDecimal totalCost;
    
    /**
     * Number of requests made by this user.
     */
    private int requestCount;
    
    /**
     * When this user was last active.
     */
    private LocalDateTime lastActivity;
    
    /**
     * User's efficiency ratio (average output/input tokens).
     */
    private Double averageEfficiencyRatio;
    
    /**
     * Most frequently used provider by this user.
     */
    private String mostUsedProvider;
    
    /**
     * Most frequently used agent by this user.
     */
    private String mostUsedAgent;
    
    /**
     * Get average cost per token for this user.
     */
    public BigDecimal getAverageCostPerToken() {
        if (totalTokens == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(BigDecimal.valueOf(totalTokens), 8, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Get average cost per request for this user.
     */
    public BigDecimal getAverageCostPerRequest() {
        if (requestCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(BigDecimal.valueOf(requestCount), 6, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Get average tokens per request for this user.
     */
    public Double getAverageTokensPerRequest() {
        if (requestCount == 0) {
            return 0.0;
        }
        return (double) totalTokens / requestCount;
    }
}

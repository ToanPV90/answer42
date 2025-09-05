package com.samjdtechnologies.answer42.model.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.samjdtechnologies.answer42.model.db.TokenMetrics;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.TokenMetricsService.RunningTotal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed token usage statistics for a specific user.
 * Provides comprehensive breakdown and analysis for individual user activity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTokenStatistics {
    
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
     * Breakdown by AI provider for this user.
     */
    private Map<AIProvider, RunningTotal> providerBreakdown;
    
    /**
     * Breakdown by agent type for this user.
     */
    private Map<AgentType, RunningTotal> agentBreakdown;
    
    /**
     * Recent activity for this user.
     */
    private List<TokenMetrics> recentActivity;
    
    /**
     * When this user was last active.
     */
    private LocalDateTime lastActivity;
    
    /**
     * User's efficiency metrics.
     */
    private UserEfficiencyMetrics efficiencyMetrics;
    
    /**
     * User's usage trends over time.
     */
    private Map<String, BigDecimal> dailyUsageTrends;
    
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
     * Get total number of requests made by this user.
     */
    public int getTotalRequests() {
        return recentActivity != null ? recentActivity.size() : 0;
    }
    
    /**
     * Get user's most used provider.
     */
    public AIProvider getMostUsedProvider() {
        if (providerBreakdown == null || providerBreakdown.isEmpty()) {
            return null;
        }
        
        return providerBreakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                    (a, b) -> Integer.compare(a.getTotalTokens(), b.getTotalTokens())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Get user's most used agent type.
     */
    public AgentType getMostUsedAgent() {
        if (agentBreakdown == null || agentBreakdown.isEmpty()) {
            return null;
        }
        
        return agentBreakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                    (a, b) -> Integer.compare(a.getTotalTokens(), b.getTotalTokens())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Get provider with highest cost for this user.
     */
    public AIProvider getMostExpensiveProvider() {
        if (providerBreakdown == null || providerBreakdown.isEmpty()) {
            return null;
        }
        
        return providerBreakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                    (a, b) -> a.getTotalCost().compareTo(b.getTotalCost())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Get average tokens per request for this user.
     */
    public Double getAverageTokensPerRequest() {
        int requestCount = getTotalRequests();
        if (requestCount == 0) {
            return 0.0;
        }
        return (double) totalTokens / requestCount;
    }
    
    /**
     * Get usage activity level based on recent activity.
     */
    public ActivityLevel getActivityLevel() {
        if (lastActivity == null) {
            return ActivityLevel.INACTIVE;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayAgo = now.minusDays(1);
        LocalDateTime oneWeekAgo = now.minusDays(7);
        LocalDateTime oneMonthAgo = now.minusMonths(1);
        
        if (lastActivity.isAfter(oneDayAgo)) {
            return ActivityLevel.VERY_ACTIVE;
        } else if (lastActivity.isAfter(oneWeekAgo)) {
            return ActivityLevel.ACTIVE;
        } else if (lastActivity.isAfter(oneMonthAgo)) {
            return ActivityLevel.MODERATE;
        } else {
            return ActivityLevel.INACTIVE;
        }
    }
    
    /**
     * User efficiency metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserEfficiencyMetrics {
        private Double averageEfficiencyRatio; // output/input tokens
        private Double averageProcessingTime;
        private Double successRate;
        private Map<AIProvider, Double> providerEfficiency;
        private Map<AgentType, Double> agentEfficiency;
    }
    
    /**
     * User activity level enum.
     */
    public enum ActivityLevel {
        VERY_ACTIVE("Very Active", "Used within last 24 hours"),
        ACTIVE("Active", "Used within last week"),
        MODERATE("Moderate", "Used within last month"),
        INACTIVE("Inactive", "No recent usage");
        
        private final String displayName;
        private final String description;
        
        ActivityLevel(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

package com.samjdtechnologies.answer42.model.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.samjdtechnologies.answer42.model.db.TokenMetrics;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.TokenMetricsService.RunningTotal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comprehensive token usage statistics aggregating data across providers, agents, and users.
 * Used for dashboard displays, reporting, and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatistics {
    
    /**
     * Global totals across all providers and agents.
     */
    private RunningTotal globalTotals;
    
    /**
     * Breakdown by AI provider.
     */
    private Map<AIProvider, RunningTotal> providerBreakdown;
    
    /**
     * Breakdown by agent type.
     */
    private Map<AgentType, RunningTotal> agentBreakdown;
    
    /**
     * Top users by token usage.
     */
    private List<UserTokenSummary> topUsersByUsage;
    
    /**
     * Recent token usage activity.
     */
    private List<TokenMetrics> recentActivity;
    
    /**
     * Daily usage trends over time.
     */
    private Map<String, BigDecimal> dailyTrends;
    
    /**
     * Cost breakdown by provider.
     */
    private Map<String, BigDecimal> costBreakdown;
    
    /**
     * When these statistics were last calculated.
     */
    private LocalDateTime lastUpdated;
    
    /**
     * Performance metrics.
     */
    private PerformanceMetrics performanceMetrics;
    
    /**
     * Cost efficiency metrics.
     */
    private CostEfficiencyMetrics costEfficiencyMetrics;
    
    /**
     * Provider comparison data.
     */
    private List<ProviderComparison> providerComparisons;
    
    /**
     * Get total cost across all providers.
     */
    public BigDecimal getTotalCost() {
        return globalTotals != null ? globalTotals.getTotalCost() : BigDecimal.ZERO;
    }
    
    /**
     * Get total tokens across all providers.
     */
    public int getTotalTokens() {
        return globalTotals != null ? globalTotals.getTotalTokens() : 0;
    }
    
    /**
     * Get total requests across all providers.
     */
    public int getTotalRequests() {
        return globalTotals != null ? globalTotals.getRequestCount() : 0;
    }
    
    /**
     * Get average cost per token.
     */
    public BigDecimal getAverageCostPerToken() {
        if (globalTotals == null || globalTotals.getTotalTokens() == 0) {
            return BigDecimal.ZERO;
        }
        return globalTotals.getTotalCost().divide(
            BigDecimal.valueOf(globalTotals.getTotalTokens()), 8, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Get most used provider.
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
     * Get most used agent type.
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
     * Performance metrics for token operations.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private Double averageTokensPerSecond;
        private Map<AIProvider, Double> providerPerformance;
        private Map<AgentType, Double> agentPerformance;
        private Double averageProcessingTimeMs;
    }
    
    /**
     * Cost efficiency metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostEfficiencyMetrics {
        private BigDecimal averageCostPerToken;
        private Map<AIProvider, BigDecimal> providerCostEfficiency;
        private Double averageEfficiencyRatio;
        private Map<AgentType, Double> agentEfficiencyRatio;
    }
    
    /**
     * Provider comparison data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderComparison {
        private AIProvider provider;
        private int totalTokens;
        private BigDecimal totalCost;
        private int requestCount;
        private BigDecimal averageCostPerToken;
        private Double averageTokensPerSecond;
        private Double reliability; // Success rate
    }
}

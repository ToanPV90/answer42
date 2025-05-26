package com.samjdtechnologies.answer42.service.discovery.ratelimit;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;

import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.service.discovery.ratelimit.APIRateLimitManager.CircuitBreakerStateEnum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics and metrics for API usage tracking.
 * Provides comprehensive monitoring data for rate limiting and circuit breaker management.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class APIUsageStats {

    private DiscoverySource source;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private CircuitBreakerStateEnum circuitBreakerState;
    private Instant lastRequestTime;
    private long requestsInLastMinute;
    private double averageResponseTime;

    /**
     * Creates empty statistics for a source.
     */
    public static APIUsageStats empty(DiscoverySource source) {
        return APIUsageStats.builder()
            .source(source)
            .totalRequests(0L)
            .successfulRequests(0L)
            .failedRequests(0L)
            .circuitBreakerState(CircuitBreakerStateEnum.CLOSED)
            .lastRequestTime(null)
            .requestsInLastMinute(0L)
            .averageResponseTime(0.0)
            .build();
    }

    /**
     * Calculate success rate as a percentage.
     */
    public double getSuccessRate() {
        if (totalRequests == 0) {
            return 100.0;
        }
        return (double) successfulRequests / totalRequests * 100.0;
    }

    /**
     * Calculate failure rate as a percentage.
     */
    public double getFailureRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) failedRequests / totalRequests * 100.0;
    }

    /**
     * Check if the API is healthy based on circuit breaker state and success rate.
     */
    public boolean isHealthy() {
        return circuitBreakerState == CircuitBreakerStateEnum.CLOSED && 
               getSuccessRate() >= 95.0;
    }

    /**
     * Check if the API is experiencing issues.
     */
    public boolean hasIssues() {
        return circuitBreakerState == CircuitBreakerStateEnum.OPEN || 
               getFailureRate() > 10.0;
    }

    /**
     * Get a human-readable status description.
     */
    public String getStatusDescription() {
        if (isHealthy()) {
            return "Healthy - Operating normally";
        } else if (circuitBreakerState == CircuitBreakerStateEnum.OPEN) {
            return "Unhealthy - Circuit breaker OPEN";
        } else if (circuitBreakerState == CircuitBreakerStateEnum.HALF_OPEN) {
            return "Recovering - Circuit breaker HALF-OPEN";
        } else if (getFailureRate() > 20.0) {
            return "Degraded - High failure rate";
        } else if (getFailureRate() > 10.0) {
            return "Warning - Elevated failure rate";
        } else {
            return "Normal - Minor issues detected";
        }
    }

    /**
     * Get usage summary for logging and monitoring.
     */
    public String getUsageSummary() {
        return String.format(
            "%s: %d total requests (%.1f%% success), Circuit: %s, Avg Response: %.0fms",
            source,
            totalRequests,
            getSuccessRate(),
            circuitBreakerState,
            averageResponseTime
        );
    }

    /**
     * Check if usage statistics indicate rate limiting is working effectively.
     */
    public boolean isRateLimitingEffective() {
        // Rate limiting is effective if we have reasonable request volumes
        // and good success rates without overwhelming the API
        return totalRequests > 0 && 
               getSuccessRate() > 90.0 && 
               circuitBreakerState != CircuitBreakerStateEnum.OPEN;
    }

    /**
     * Enhanced cost estimation with realistic pricing models and usage patterns.
     */
    public CostEstimate getEstimatedCost() {
        if (source == null) {
            return CostEstimate.builder()
                .directCost(0.0)
                .opportunityCost(0.0)
                .totalCost(0.0)
                .currency("USD")
                .period("month")
                .tier("Unknown")
                .notes("Source not specified")
                .build();
        }

        return switch (source) {
            case CROSSREF -> calculateCrossrefCost();
            case SEMANTIC_SCHOLAR -> calculateSemanticScholarCost();
            case PERPLEXITY -> calculatePerplexityCost();
            default -> CostEstimate.builder()
                .directCost(0.0)
                .opportunityCost(0.0)
                .totalCost(0.0)
                .currency("USD")
                .period("month")
                .tier("Unknown")
                .notes("Unknown source: " + source)
                .build();
        };
    }

    /**
     * Calculate Crossref API costs (free but with fair use considerations).
     */
    private CostEstimate calculateCrossrefCost() {
        // Crossref is free but has fair use guidelines
        // Calculate opportunity cost if we exceed limits and need commercial access
        
        double fairUseThreshold = 50000; // Monthly fair use threshold
        double commercialCostPerRequest = 0.001; // $0.001 per request for commercial access
        
        double monthlyCost = 0.0;
        double opportunityCost = 0.0;
        
        if (totalRequests > fairUseThreshold) {
            // If exceeding fair use, calculate commercial pricing
            opportunityCost = (totalRequests - fairUseThreshold) * commercialCostPerRequest;
        }
        
        return CostEstimate.builder()
            .directCost(monthlyCost)
            .opportunityCost(opportunityCost)
            .totalCost(monthlyCost + opportunityCost)
            .currency("USD")
            .period("month")
            .tier("Fair Use")
            .notes("Free tier with fair use policy. Commercial pricing if exceeded.")
            .build();
    }

    /**
     * Calculate Semantic Scholar API costs (free with rate limits).
     */
    private CostEstimate calculateSemanticScholarCost() {
        // Semantic Scholar is free but limited
        // Calculate potential costs if needing premium access or higher limits
        
        double freeRequestLimit = 1000; // Monthly free limit estimate
        double premiumCostPerRequest = 0.002; // Estimated premium pricing
        double enterpriseMonthlyCost = 500.0; // Estimated enterprise tier
        
        double monthlyCost = 0.0;
        double opportunityCost = 0.0;
        String tier = "Free";
        
        if (totalRequests > freeRequestLimit) {
            if (totalRequests > 10000) {
                // Enterprise tier would be more cost-effective
                monthlyCost = enterpriseMonthlyCost;
                tier = "Enterprise";
            } else {
                // Premium per-request pricing
                opportunityCost = (totalRequests - freeRequestLimit) * premiumCostPerRequest;
                tier = "Premium";
            }
        }
        
        return CostEstimate.builder()
            .directCost(monthlyCost)
            .opportunityCost(opportunityCost)
            .totalCost(monthlyCost + opportunityCost)
            .currency("USD")
            .period("month")
            .tier(tier)
            .notes("Free tier with rate limits. Premium/Enterprise available for higher usage.")
            .build();
    }

    /**
     * Calculate Perplexity API costs with realistic pricing tiers.
     */
    private CostEstimate calculatePerplexityCost() {
        // Perplexity Pro: $20/month for 300 searches/day = ~9000/month
        // Enterprise: Custom pricing, estimated $200-500/month
        // API usage: Estimated $0.01-0.05 per search depending on complexity
        
        double freeLimit = 0; // No free tier for API
        double proCostPerMonth = 20.0;
        double proRequestLimit = 9000; // 300/day * 30 days
        double apiCostPerRequest = 0.03; // $0.03 per API search (mid-range estimate)
        double enterpriseMonthlyCost = 300.0; // Mid-range enterprise estimate
        double enterpriseRequestLimit = 50000; // High volume enterprise limit
        
        double monthlyCost = 0.0;
        double opportunityCost = 0.0;
        String tier = "Pay-per-use";
        
        if (totalRequests <= proRequestLimit) {
            // Pro tier might be more cost-effective
            double payPerUseCost = totalRequests * apiCostPerRequest;
            if (payPerUseCost > proCostPerMonth) {
                monthlyCost = proCostPerMonth;
                tier = "Pro";
            } else {
                monthlyCost = payPerUseCost;
                tier = "Pay-per-use";
            }
        } else if (totalRequests <= enterpriseRequestLimit) {
            // Compare Pro + overage vs Enterprise
            double proWithOverage = proCostPerMonth + 
                ((totalRequests - proRequestLimit) * apiCostPerRequest * 1.5); // 50% overage penalty
            
            if (proWithOverage > enterpriseMonthlyCost) {
                monthlyCost = enterpriseMonthlyCost;
                tier = "Enterprise";
            } else {
                monthlyCost = proWithOverage;
                tier = "Pro + Overage";
            }
        } else {
            // High volume enterprise with overage
            monthlyCost = enterpriseMonthlyCost;
            opportunityCost = (totalRequests - enterpriseRequestLimit) * apiCostPerRequest * 2.0; // 100% overage
            tier = "Enterprise + Overage";
        }
        
        // Add peak time surcharge if applicable
        double peakTimeSurcharge = calculatePeakTimeSurcharge(monthlyCost);
        monthlyCost += peakTimeSurcharge;
        
        // Add failure cost (failed requests may still incur partial charges)
        double failureCost = failedRequests * apiCostPerRequest * 0.3; // 30% charge for failed requests
        opportunityCost += failureCost;
        
        return CostEstimate.builder()
            .directCost(monthlyCost)
            .opportunityCost(opportunityCost)
            .totalCost(monthlyCost + opportunityCost)
            .currency("USD")
            .period("month")
            .tier(tier)
            .notes(String.format("Includes peak time surcharge: $%.2f, failure costs: $%.2f", 
                peakTimeSurcharge, failureCost))
            .build();
    }

    /**
     * Calculate peak time surcharge based on usage patterns.
     */
    private double calculatePeakTimeSurcharge(double baseCost) {
        if (lastRequestTime == null) {
            return 0.0;
        }
        
        ZonedDateTime requestTime = ZonedDateTime.ofInstant(lastRequestTime, ZoneOffset.UTC);
        int hour = requestTime.getHour();
        
        // Peak hours: 9 AM - 5 PM UTC (business hours)
        if (hour >= 9 && hour <= 17) {
            return baseCost * 0.15; // 15% peak time surcharge
        }
        
        return 0.0;
    }

    /**
     * Check if the API is approaching rate limits based on recent usage.
     */
    public boolean isApproachingLimits() {
        if (source == null) {
            return false;
        }

        return switch (source) {
            case CROSSREF -> requestsInLastMinute > 2500; // 45 RPS * 60 = 2700, warn at 2500
            case SEMANTIC_SCHOLAR -> requestsInLastMinute > 15; // 0.3 RPS * 60 = 18, warn at 15
            case PERPLEXITY -> requestsInLastMinute > 8; // 0.167 RPS * 60 = 10, warn at 8
            default -> false; // Conservative default for unknown sources
        };
    }

    /**
     * Get recommended action based on current statistics.
     */
    public String getRecommendedAction() {
        if (circuitBreakerState == CircuitBreakerStateEnum.OPEN) {
            return "Wait for circuit breaker timeout or investigate API issues";
        } else if (isApproachingLimits()) {
            return "Reduce request rate to avoid hitting API limits";
        } else if (getFailureRate() > 20.0) {
            return "Investigate API failures and consider temporary rate reduction";
        } else if (averageResponseTime > 5000) {
            return "API response times are slow, consider implementing caching";
        } else if (isHealthy()) {
            return "Continue current usage pattern";
        } else {
            return "Monitor closely for any developing issues";
        }
    }

    /**
     * Get cost optimization recommendations.
     */
    public String getCostOptimizationRecommendation() {
        CostEstimate cost = getEstimatedCost();
        
        if (cost.getTotalCost() == 0.0) {
            return "Currently using free tier efficiently";
        } else if (cost.getTotalCost() > 100.0) {
            return "High cost detected. Consider caching, request batching, or tier optimization";
        } else if (cost.getOpportunityCost() > cost.getDirectCost()) {
            return "Opportunity costs exceed direct costs. Consider upgrading to higher tier";
        } else if (getFailureRate() > 5.0) {
            return "High failure rate increasing costs. Improve error handling and retry logic";
        } else {
            return "Cost usage appears optimized for current tier";
        }
    }

    /**
     * Create a detailed metrics report.
     */
    public String getDetailedReport() {
        CostEstimate cost = getEstimatedCost();
        
        StringBuilder report = new StringBuilder();
        report.append(String.format("=== API Usage Report: %s ===\n", source));
        report.append(String.format("Total Requests: %,d\n", totalRequests));
        report.append(String.format("Successful: %,d (%.1f%%)\n", successfulRequests, getSuccessRate()));
        report.append(String.format("Failed: %,d (%.1f%%)\n", failedRequests, getFailureRate()));
        report.append(String.format("Circuit Breaker: %s\n", circuitBreakerState));
        report.append(String.format("Last Request: %s\n", 
            lastRequestTime != null ? lastRequestTime.toString() : "Never"));
        report.append(String.format("Recent Activity: %d requests/minute\n", requestsInLastMinute));
        report.append(String.format("Avg Response Time: %.0f ms\n", averageResponseTime));
        
        report.append("\n=== Cost Analysis ===\n");
        report.append(String.format("Current Tier: %s\n", cost.getTier()));
        report.append(String.format("Direct Cost: $%.4f %s\n", cost.getDirectCost(), cost.getCurrency()));
        report.append(String.format("Opportunity Cost: $%.4f %s\n", cost.getOpportunityCost(), cost.getCurrency()));
        report.append(String.format("Total Cost: $%.4f %s/%s\n", cost.getTotalCost(), cost.getCurrency(), cost.getPeriod()));
        report.append(String.format("Cost Notes: %s\n", cost.getNotes()));
        
        report.append(String.format("\nStatus: %s\n", getStatusDescription()));
        report.append(String.format("Recommendation: %s\n", getRecommendedAction()));
        report.append(String.format("Cost Optimization: %s\n", getCostOptimizationRecommendation()));
        
        return report.toString();
    }

    /**
     * Cost estimate breakdown for detailed analysis.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CostEstimate {
        private double directCost;        // Actual charges from provider
        private double opportunityCost;   // Potential costs from overages, failures, etc.
        private double totalCost;         // Combined cost
        private String currency;          // Cost currency (USD, EUR, etc.)
        private String period;            // Cost period (month, day, year)
        private String tier;              // Current pricing tier
        private String notes;             // Additional cost information
        
        /**
         * Calculate cost per request.
         */
        public double getCostPerRequest(long totalRequests) {
            if (totalRequests == 0) {
                return 0.0;
            }
            return totalCost / totalRequests;
        }
        
        /**
         * Calculate potential savings with tier optimization.
         */
        public double getPotentialSavings() {
            return Math.max(0, opportunityCost - (directCost * 0.1)); // 10% buffer
        }
        
        /**
         * Check if current tier is cost-optimal.
         */
        public boolean isOptimalTier() {
            return opportunityCost <= directCost * 0.2; // 20% tolerance
        }
    }
}

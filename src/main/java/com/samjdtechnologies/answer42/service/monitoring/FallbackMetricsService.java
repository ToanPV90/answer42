package com.samjdtechnologies.answer42.service.monitoring;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import lombok.Data;

/**
 * Service for tracking and reporting fallback usage metrics.
 * Provides real-time monitoring of primary/fallback provider performance.
 */
@Service
public class FallbackMetricsService {
    
    private static final Logger logger = LoggingUtil.getLogger(FallbackMetricsService.class);
    
    private final Map<String, AtomicLong> fallbackCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> primarySuccessCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalRequestCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFallbackUsage = new ConcurrentHashMap<>();
    
    /**
     * Record a successful primary provider request.
     */
    public void recordPrimarySuccess(String agentType, AIProvider provider) {
        String key = buildKey(agentType, provider);
        primarySuccessCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        totalRequestCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        LoggingUtil.debug(logger, "recordPrimarySuccess", 
                         "Recorded primary success for agent: %s with provider: %s", 
                         agentType, provider);
    }
    
    /**
     * Record a fallback usage event.
     */
    public void recordFallbackUsage(String agentType, AIProvider primaryProvider, 
                                  String failureReason, AgentResult result) {
        String key = buildKey(agentType, primaryProvider);
        fallbackCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        totalRequestCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        lastFallbackUsage.put(key, Instant.now());
        
        LoggingUtil.info(logger, "recordFallbackUsage", 
                        "Fallback used for agent: %s primary: %s reason: %s success: %s", 
                        agentType, primaryProvider, failureReason, result.isSuccess());
    }
    
    /**
     * Get fallback rate for a specific agent and provider combination.
     */
    public double getFallbackRate(String agentType, AIProvider provider) {
        String key = buildKey(agentType, provider);
        long totalRequests = totalRequestCounts.getOrDefault(key, new AtomicLong(0)).get();
        long fallbackUsages = fallbackCounts.getOrDefault(key, new AtomicLong(0)).get();
        
        return totalRequests > 0 ? (double) fallbackUsages / totalRequests : 0.0;
    }
    
    /**
     * Get primary success rate for a specific agent and provider combination.
     */
    public double getPrimarySuccessRate(String agentType, AIProvider provider) {
        String key = buildKey(agentType, provider);
        long totalRequests = totalRequestCounts.getOrDefault(key, new AtomicLong(0)).get();
        long primarySuccesses = primarySuccessCounts.getOrDefault(key, new AtomicLong(0)).get();
        
        return totalRequests > 0 ? (double) primarySuccesses / totalRequests : 0.0;
    }
    
    /**
     * Get comprehensive metrics for all agents and providers.
     */
    public Map<String, FallbackMetrics> getAllMetrics() {
        Map<String, FallbackMetrics> metrics = new HashMap<>();
        
        // Collect all unique keys
        totalRequestCounts.keySet().forEach(key -> {
            String[] parts = key.split(":");
            if (parts.length == 2) {
                String agentType = parts[0];
                AIProvider provider = AIProvider.valueOf(parts[1]);
                
                FallbackMetrics metric = buildMetrics(agentType, provider);
                metrics.put(key, metric);
            }
        });
        
        return metrics;
    }
    
    /**
     * Get metrics for a specific agent type across all providers.
     */
    public Map<AIProvider, FallbackMetrics> getMetricsForAgent(String agentType) {
        Map<AIProvider, FallbackMetrics> metrics = new HashMap<>();
        
        for (AIProvider provider : AIProvider.values()) {
            FallbackMetrics metric = buildMetrics(agentType, provider);
            if (metric.getTotalRequests() > 0) {
                metrics.put(provider, metric);
            }
        }
        
        return metrics;
    }
    
    /**
     * Get aggregated metrics across all agents for dashboard display.
     */
    public FallbackSummary getSummaryMetrics() {
        long totalRequests = 0;
        long totalFallbacks = 0;
        long totalPrimarySuccesses = 0;
        Instant latestFallback = null;
        
        for (AtomicLong count : totalRequestCounts.values()) {
            totalRequests += count.get();
        }
        
        for (AtomicLong count : fallbackCounts.values()) {
            totalFallbacks += count.get();
        }
        
        for (AtomicLong count : primarySuccessCounts.values()) {
            totalPrimarySuccesses += count.get();
        }
        
        for (Instant usage : lastFallbackUsage.values()) {
            if (latestFallback == null || usage.isAfter(latestFallback)) {
                latestFallback = usage;
            }
        }
        
        double fallbackRate = totalRequests > 0 ? (double) totalFallbacks / totalRequests : 0.0;
        double primarySuccessRate = totalRequests > 0 ? (double) totalPrimarySuccesses / totalRequests : 0.0;
        
        return FallbackSummary.builder()
            .totalRequests(totalRequests)
            .totalFallbacks(totalFallbacks)
            .totalPrimarySuccesses(totalPrimarySuccesses)
            .overallFallbackRate(fallbackRate)
            .overallPrimarySuccessRate(primarySuccessRate)
            .lastFallbackUsage(latestFallback)
            .build();
    }
    
    /**
     * Reset all metrics (useful for testing or periodic resets).
     */
    public void resetMetrics() {
        fallbackCounts.clear();
        primarySuccessCounts.clear();
        totalRequestCounts.clear();
        lastFallbackUsage.clear();
        
        LoggingUtil.info(logger, "resetMetrics", "Fallback metrics reset");
    }
    
    /**
     * Get metrics for the last N hours.
     */
    public FallbackSummary getMetricsForPeriod(int hours) {
        // For now, return current metrics. In a production environment,
        // you'd want to store timestamped metrics for historical analysis
        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        
        // Filter based on last usage time (simple implementation)
        long recentFallbacks = lastFallbackUsage.values().stream()
            .filter(usage -> usage.isAfter(cutoff))
            .mapToLong(usage -> 1L)
            .sum();
            
        return FallbackSummary.builder()
            .totalRequests(getTotalRequestsInPeriod(hours))
            .totalFallbacks(recentFallbacks)
            .overallFallbackRate(recentFallbacks > 0 ? (double) recentFallbacks / getTotalRequestsInPeriod(hours) : 0.0)
            .build();
    }
    
    /**
     * Check if fallback usage has exceeded threshold for alerting.
     */
    public boolean isFallbackUsageHigh(String agentType, AIProvider provider, double threshold) {
        double rate = getFallbackRate(agentType, provider);
        return rate > threshold;
    }
    
    private String buildKey(String agentType, AIProvider provider) {
        return agentType + ":" + provider.name();
    }
    
    private FallbackMetrics buildMetrics(String agentType, AIProvider provider) {
        String key = buildKey(agentType, provider);
        
        long totalRequests = totalRequestCounts.getOrDefault(key, new AtomicLong(0)).get();
        long fallbackUsages = fallbackCounts.getOrDefault(key, new AtomicLong(0)).get();
        long primarySuccesses = primarySuccessCounts.getOrDefault(key, new AtomicLong(0)).get();
        Instant lastUsage = lastFallbackUsage.get(key);
        
        double fallbackRate = totalRequests > 0 ? (double) fallbackUsages / totalRequests : 0.0;
        double primarySuccessRate = totalRequests > 0 ? (double) primarySuccesses / totalRequests : 0.0;
        
        return FallbackMetrics.builder()
            .agentType(agentType)
            .provider(provider)
            .totalRequests(totalRequests)
            .fallbackUsages(fallbackUsages)
            .primarySuccesses(primarySuccesses)
            .fallbackRate(fallbackRate)
            .primarySuccessRate(primarySuccessRate)
            .lastFallbackUsage(lastUsage)
            .build();
    }
    
    private long getTotalRequestsInPeriod(int hours) {
        // Simplified implementation - in production, you'd track timestamped requests
        return totalRequestCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
    }
    
    /**
     * Metrics for a specific agent/provider combination.
     */
    @Data
    @lombok.Builder
    public static class FallbackMetrics {
        private final String agentType;
        private final AIProvider provider;
        private final long totalRequests;
        private final long fallbackUsages;
        private final long primarySuccesses;
        private final double fallbackRate;
        private final double primarySuccessRate;
        private final Instant lastFallbackUsage;
        
        public LocalDateTime getLastFallbackUsageLocal() {
            return lastFallbackUsage != null 
                ? LocalDateTime.ofInstant(lastFallbackUsage, ZoneId.systemDefault())
                : null;
        }
    }
    
    /**
     * Summary metrics across all agents and providers.
     */
    @Data
    @lombok.Builder
    public static class FallbackSummary {
        private final long totalRequests;
        private final long totalFallbacks;
        private final long totalPrimarySuccesses;
        private final double overallFallbackRate;
        private final double overallPrimarySuccessRate;
        private final Instant lastFallbackUsage;
        
        public LocalDateTime getLastFallbackUsageLocal() {
            return lastFallbackUsage != null 
                ? LocalDateTime.ofInstant(lastFallbackUsage, ZoneId.systemDefault())
                : null;
        }
        
        public boolean isHealthy(double fallbackThreshold) {
            return overallFallbackRate <= fallbackThreshold;
        }
    }
}

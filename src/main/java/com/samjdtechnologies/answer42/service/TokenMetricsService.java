package com.samjdtechnologies.answer42.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.TokenMetrics;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.metrics.TokenStatistics;
import com.samjdtechnologies.answer42.model.metrics.UserTokenStatistics;
import com.samjdtechnologies.answer42.model.metrics.UserTokenSummary;
import com.samjdtechnologies.answer42.repository.TokenMetricsRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Comprehensive token usage and cost tracking service with running totals.
 * Provides real-time metrics, colorized logging, and persistent storage.
 */
@Service
@Transactional
public class TokenMetricsService {
    
    private static final Logger LOG = LoggingUtil.getLogger(TokenMetricsService.class);
    
    private final TokenMetricsRepository tokenMetricsRepository;
    
    // In-memory running totals for real-time tracking
    private final Map<AIProvider, RunningTotal> providerTotals = new ConcurrentHashMap<>();
    private final Map<AgentType, RunningTotal> agentTotals = new ConcurrentHashMap<>();
    private final Map<String, RunningTotal> userTotals = new ConcurrentHashMap<>(); // userId -> totals
    
    // Global running totals
    private volatile RunningTotal globalTotals = new RunningTotal();
    
    public TokenMetricsService(TokenMetricsRepository tokenMetricsRepository) {
        this.tokenMetricsRepository = tokenMetricsRepository;
        initializeRunningTotals();
    }
    
    /**
     * Record token usage with comprehensive metrics tracking.
     */
    public TokenMetrics recordTokenUsage(
            UUID userId,
            AIProvider provider, 
            AgentType agentType,
            String taskId,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost) {
        
        TokenMetrics metrics = TokenMetrics.builder()
                .userId(userId)
                .provider(provider)
                .agentType(agentType)
                .taskId(taskId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .estimatedCost(estimatedCost)
                .timestamp(LocalDateTime.now())
                .build();
        
        // Persist to database
        TokenMetrics saved = tokenMetricsRepository.save(metrics);
        
        // Update running totals
        updateRunningTotals(userId.toString(), provider, agentType, inputTokens, outputTokens, estimatedCost);
        
        // Log with colorized output
        LoggingUtil.info(LOG, "recordTokenUsage", 
            "📊 TOKEN USAGE | Provider: %s | Agent: %s | Task: %s | Input: %,d tokens | Output: %,d tokens | Total: %,d tokens | Cost: $%.4f | User: %s", 
            provider, agentType, taskId, inputTokens, outputTokens, 
            inputTokens + outputTokens, estimatedCost, userId);
        
        return saved;
    }
    
    /**
     * Update in-memory running totals for real-time tracking.
     */
    private void updateRunningTotals(String userId, AIProvider provider, AgentType agentType, 
                                   int inputTokens, int outputTokens, BigDecimal cost) {
        int totalTokens = inputTokens + outputTokens;
        
        // Update provider totals
        providerTotals.computeIfAbsent(provider, k -> new RunningTotal())
                     .addUsage(inputTokens, outputTokens, cost);
        
        // Update agent totals
        agentTotals.computeIfAbsent(agentType, k -> new RunningTotal())
                  .addUsage(inputTokens, outputTokens, cost);
        
        // Update user totals
        userTotals.computeIfAbsent(userId, k -> new RunningTotal())
                 .addUsage(inputTokens, outputTokens, cost);
        
        // Update global totals
        globalTotals.addUsage(inputTokens, outputTokens, cost);
        
        LoggingUtil.debug(LOG, "updateRunningTotals", 
            "📈 RUNNING TOTALS UPDATED | Provider %s: %,d total tokens ($%.4f) | Agent %s: %,d total tokens | Global: %,d total tokens ($%.4f)", 
            provider, providerTotals.get(provider).getTotalTokens(), providerTotals.get(provider).getTotalCost(),
            agentType, agentTotals.get(agentType).getTotalTokens(),
            globalTotals.getTotalTokens(), globalTotals.getTotalCost());
    }
    
    /**
     * Get comprehensive token statistics with running totals.
     */
    public TokenStatistics getComprehensiveStatistics() {
        return TokenStatistics.builder()
                .globalTotals(globalTotals.copy())
                .providerBreakdown(copyProviderTotals())
                .agentBreakdown(copyAgentTotals())
                .topUsersByUsage(getTopUsersByUsage(10))
                .recentActivity(getRecentActivity(50))
                .dailyTrends(getDailyTrends(30))
                .costBreakdown(getCostBreakdown())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    /**
     * Get token statistics for a specific user.
     */
    public UserTokenStatistics getUserStatistics(UUID userId) {
        String userKey = userId.toString();
        RunningTotal userTotal = userTotals.getOrDefault(userKey, new RunningTotal());
        
        List<TokenMetrics> userMetrics = tokenMetricsRepository.findByUserIdOrderByTimestampDesc(userId);
        Map<AIProvider, RunningTotal> userProviderBreakdown = calculateUserProviderBreakdown(userMetrics);
        Map<AgentType, RunningTotal> userAgentBreakdown = calculateUserAgentBreakdown(userMetrics);
        
        return UserTokenStatistics.builder()
                .userId(userId)
                .totalTokens(userTotal.getTotalTokens())
                .totalCost(userTotal.getTotalCost())
                .providerBreakdown(userProviderBreakdown)
                .agentBreakdown(userAgentBreakdown)
                .recentActivity(userMetrics.stream().limit(20).collect(Collectors.toList()))
                .lastActivity(userMetrics.isEmpty() ? null : userMetrics.get(0).getTimestamp())
                .build();
    }
    
    /**
     * Get real-time provider statistics.
     */
    public Map<AIProvider, RunningTotal> getProviderStatistics() {
        return copyProviderTotals();
    }
    
    /**
     * Get real-time agent statistics.
     */
    public Map<AgentType, RunningTotal> getAgentStatistics() {
        return copyAgentTotals();
    }
    
    /**
     * Get global running totals.
     */
    public RunningTotal getGlobalTotals() {
        return globalTotals.copy();
    }
    
    /**
     * Reset running totals (admin function).
     */
    public void resetRunningTotals() {
        providerTotals.clear();
        agentTotals.clear();
        userTotals.clear();
        globalTotals = new RunningTotal();
        
        LoggingUtil.warn(LOG, "resetRunningTotals", "🔄 All running totals have been reset");
    }
    
    /**
     * Print comprehensive colored statistics to logs.
     */
    public void logComprehensiveStatistics() {
        TokenStatistics stats = getComprehensiveStatistics();
        
        LoggingUtil.info(LOG, "logStatistics", 
            "🌟 === COMPREHENSIVE TOKEN STATISTICS ===");
        
        LoggingUtil.info(LOG, "logStatistics", 
            "🌍 GLOBAL TOTALS: %,d total tokens | $%.4f total cost | %,d requests", 
            stats.getGlobalTotals().getTotalTokens(),
            stats.getGlobalTotals().getTotalCost(),
            stats.getGlobalTotals().getRequestCount());
        
        LoggingUtil.info(LOG, "logStatistics", "🔧 PROVIDER BREAKDOWN:");
        stats.getProviderBreakdown().entrySet().stream()
             .sorted(Map.Entry.<AIProvider, RunningTotal>comparingByValue(
                 (a, b) -> Integer.compare(b.getTotalTokens(), a.getTotalTokens())))
             .forEach(entry -> {
                 AIProvider provider = entry.getKey();
                 RunningTotal total = entry.getValue();
                 LoggingUtil.info(LOG, "logStatistics", 
                     "  • %s: %,d tokens ($%.4f) - %,d requests", 
                     provider, total.getTotalTokens(), total.getTotalCost(), total.getRequestCount());
             });
        
        LoggingUtil.info(LOG, "logStatistics", "🤖 AGENT BREAKDOWN:");
        stats.getAgentBreakdown().entrySet().stream()
             .sorted(Map.Entry.<AgentType, RunningTotal>comparingByValue(
                 (a, b) -> Integer.compare(b.getTotalTokens(), a.getTotalTokens())))
             .forEach(entry -> {
                 AgentType agent = entry.getKey();
                 RunningTotal total = entry.getValue();
                 LoggingUtil.info(LOG, "logStatistics", 
                     "  • %s: %,d tokens ($%.4f) - %,d requests", 
                     agent, total.getTotalTokens(), total.getTotalCost(), total.getRequestCount());
             });
        
        LoggingUtil.info(LOG, "logStatistics", "💰 COST BREAKDOWN:");
        BigDecimal totalCost = stats.getGlobalTotals().getTotalCost();
        stats.getProviderBreakdown().forEach((provider, total) -> {
            if (total.getTotalCost().compareTo(BigDecimal.ZERO) > 0) {
                double percentage = total.getTotalCost().divide(totalCost, 4, BigDecimal.ROUND_HALF_UP)
                                          .multiply(BigDecimal.valueOf(100)).doubleValue();
                LoggingUtil.info(LOG, "logStatistics", 
                    "  • %s: $%.4f (%.2f%% of total cost)", 
                    provider, total.getTotalCost(), percentage);
            }
        });
    }
    
    /**
     * Initialize running totals from database on startup.
     */
    private void initializeRunningTotals() {
        LoggingUtil.info(LOG, "initializeRunningTotals", "🚀 Initializing token metrics from database...");
        
        try {
            // Load recent metrics (last 30 days) for running totals
            LocalDateTime since = LocalDateTime.now().minusDays(30);
            List<TokenMetrics> recentMetrics = tokenMetricsRepository.findByTimestampAfterOrderByTimestampDesc(since);
            
            for (TokenMetrics metric : recentMetrics) {
                updateRunningTotals(
                    metric.getUserId().toString(),
                    metric.getProvider(),
                    metric.getAgentType(),
                    metric.getInputTokens(),
                    metric.getOutputTokens(),
                    metric.getEstimatedCost()
                );
            }
            
            LoggingUtil.info(LOG, "initializeRunningTotals", 
                "✅ Initialized running totals from %,d recent metrics. Global total: %,d tokens ($%.4f)", 
                recentMetrics.size(), globalTotals.getTotalTokens(), globalTotals.getTotalCost());
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "initializeRunningTotals", 
                "❌ Failed to initialize running totals from database", e);
        }
    }
    
    /**
     * Periodically log statistics (every 5 minutes).
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduledStatisticsLogging() {
        if (globalTotals.getRequestCount() > 0) {
            LoggingUtil.info(LOG, "scheduledLogging", 
                "⏰ PERIODIC UPDATE | Global: %,d tokens ($%.4f) | Active providers: %d | Active agents: %d", 
                globalTotals.getTotalTokens(), globalTotals.getTotalCost(),
                providerTotals.size(), agentTotals.size());
        }
    }
    
    // Helper methods for data transformation
    
    private Map<AIProvider, RunningTotal> copyProviderTotals() {
        Map<AIProvider, RunningTotal> copy = new HashMap<>();
        providerTotals.forEach((provider, total) -> copy.put(provider, total.copy()));
        return copy;
    }
    
    private Map<AgentType, RunningTotal> copyAgentTotals() {
        Map<AgentType, RunningTotal> copy = new HashMap<>();
        agentTotals.forEach((agent, total) -> copy.put(agent, total.copy()));
        return copy;
    }
    
    private List<UserTokenSummary> getTopUsersByUsage(int limit) {
        return userTotals.entrySet().stream()
                .sorted(Map.Entry.<String, RunningTotal>comparingByValue(
                    (a, b) -> Integer.compare(b.getTotalTokens(), a.getTotalTokens())))
                .limit(limit)
                .map(entry -> UserTokenSummary.builder()
                    .userId(UUID.fromString(entry.getKey()))
                    .totalTokens(entry.getValue().getTotalTokens())
                    .totalCost(entry.getValue().getTotalCost())
                    .requestCount(entry.getValue().getRequestCount())
                    .build())
                .collect(Collectors.toList());
    }
    
    private List<TokenMetrics> getRecentActivity(int limit) {
        return tokenMetricsRepository.findTop50ByOrderByTimestampDesc().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    private Map<String, BigDecimal> getDailyTrends(int days) {
        // Implementation for daily trends calculation
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<TokenMetrics> metrics = tokenMetricsRepository.findByTimestampAfterOrderByTimestampDesc(since);
        
        return metrics.stream()
                .collect(Collectors.groupingBy(
                    m -> m.getTimestamp().toLocalDate().toString(),
                    Collectors.reducing(BigDecimal.ZERO,
                        TokenMetrics::getEstimatedCost,
                        BigDecimal::add)));
    }
    
    private Map<String, BigDecimal> getCostBreakdown() {
        Map<String, BigDecimal> breakdown = new HashMap<>();
        providerTotals.forEach((provider, total) -> 
            breakdown.put(provider.name(), total.getTotalCost()));
        return breakdown;
    }
    
    private Map<AIProvider, RunningTotal> calculateUserProviderBreakdown(List<TokenMetrics> userMetrics) {
        return userMetrics.stream()
                .collect(Collectors.groupingBy(TokenMetrics::getProvider,
                    Collectors.reducing(new RunningTotal(),
                        metric -> new RunningTotal(metric.getInputTokens(), 
                                                 metric.getOutputTokens(), 
                                                 metric.getEstimatedCost()),
                        RunningTotal::add)));
    }
    
    private Map<AgentType, RunningTotal> calculateUserAgentBreakdown(List<TokenMetrics> userMetrics) {
        return userMetrics.stream()
                .collect(Collectors.groupingBy(TokenMetrics::getAgentType,
                    Collectors.reducing(new RunningTotal(),
                        metric -> new RunningTotal(metric.getInputTokens(), 
                                                 metric.getOutputTokens(), 
                                                 metric.getEstimatedCost()),
                        RunningTotal::add)));
    }
    
    // Inner classes for data structures
    
    public static class RunningTotal {
        private volatile int inputTokens = 0;
        private volatile int outputTokens = 0;
        private volatile BigDecimal totalCost = BigDecimal.ZERO;
        private volatile int requestCount = 0;
        
        public RunningTotal() {}
        
        public RunningTotal(int inputTokens, int outputTokens, BigDecimal cost) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalCost = cost;
            this.requestCount = 1;
        }
        
        public synchronized void addUsage(int inputTokens, int outputTokens, BigDecimal cost) {
            this.inputTokens += inputTokens;
            this.outputTokens += outputTokens;
            this.totalCost = this.totalCost.add(cost);
            this.requestCount++;
        }
        
        public RunningTotal add(RunningTotal other) {
            RunningTotal result = new RunningTotal();
            result.inputTokens = this.inputTokens + other.inputTokens;
            result.outputTokens = this.outputTokens + other.outputTokens;
            result.totalCost = this.totalCost.add(other.totalCost);
            result.requestCount = this.requestCount + other.requestCount;
            return result;
        }
        
        public RunningTotal copy() {
            RunningTotal copy = new RunningTotal();
            copy.inputTokens = this.inputTokens;
            copy.outputTokens = this.outputTokens;
            copy.totalCost = this.totalCost;
            copy.requestCount = this.requestCount;
            return copy;
        }
        
        // Getters
        public int getInputTokens() { return inputTokens; }
        public int getOutputTokens() { return outputTokens; }
        public int getTotalTokens() { return inputTokens + outputTokens; }
        public BigDecimal getTotalCost() { return totalCost; }
        public int getRequestCount() { return requestCount; }
    }
    
    // Additional data classes would be defined here...
    // (TokenStatistics, UserTokenStatistics, UserTokenSummary, etc.)
}

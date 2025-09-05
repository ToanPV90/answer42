package com.samjdtechnologies.answer42.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.TokenMetrics;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;

/**
 * Repository for TokenMetrics entity providing comprehensive query methods
 * for token usage analytics, cost tracking, and performance monitoring.
 */
@Repository
public interface TokenMetricsRepository extends JpaRepository<TokenMetrics, UUID> {
    
    // Basic finders
    List<TokenMetrics> findByUserIdOrderByTimestampDesc(UUID userId);
    List<TokenMetrics> findByProviderOrderByTimestampDesc(AIProvider provider);
    List<TokenMetrics> findByAgentTypeOrderByTimestampDesc(AgentType agentType);
    List<TokenMetrics> findByTaskIdOrderByTimestampDesc(String taskId);
    
    // Time-based queries
    List<TokenMetrics> findByTimestampAfterOrderByTimestampDesc(LocalDateTime timestamp);
    List<TokenMetrics> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    List<TokenMetrics> findTop50ByOrderByTimestampDesc();
    
    // User-specific queries
    List<TokenMetrics> findByUserIdAndTimestampAfterOrderByTimestampDesc(UUID userId, LocalDateTime timestamp);
    List<TokenMetrics> findByUserIdAndProviderOrderByTimestampDesc(UUID userId, AIProvider provider);
    List<TokenMetrics> findByUserIdAndAgentTypeOrderByTimestampDesc(UUID userId, AgentType agentType);
    
    // Provider and agent combinations
    List<TokenMetrics> findByProviderAndAgentTypeOrderByTimestampDesc(AIProvider provider, AgentType agentType);
    
    // Success/failure queries
    List<TokenMetrics> findBySuccessOrderByTimestampDesc(Boolean success);
    List<TokenMetrics> findByUserIdAndSuccessOrderByTimestampDesc(UUID userId, Boolean success);
    
    // Paper-specific queries
    List<TokenMetrics> findByPaperIdOrderByTimestampDesc(UUID paperId);
    List<TokenMetrics> findByUserIdAndPaperIdOrderByTimestampDesc(UUID userId, UUID paperId);
    
    // Session-based queries
    List<TokenMetrics> findBySessionIdOrderByTimestampDesc(String sessionId);
    
    // Pageable queries for large datasets
    Page<TokenMetrics> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);
    Page<TokenMetrics> findByProviderOrderByTimestampDesc(AIProvider provider, Pageable pageable);
    Page<TokenMetrics> findByAgentTypeOrderByTimestampDesc(AgentType agentType, Pageable pageable);
    
    // Aggregation queries using JPQL
    
    @Query("SELECT SUM(tm.totalTokens) FROM TokenMetrics tm WHERE tm.userId = :userId")
    Long getTotalTokensByUser(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(tm.estimatedCost) FROM TokenMetrics tm WHERE tm.userId = :userId")
    java.math.BigDecimal getTotalCostByUser(@Param("userId") UUID userId);
    
    @Query("SELECT COUNT(tm) FROM TokenMetrics tm WHERE tm.userId = :userId")
    Long getRequestCountByUser(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(tm.totalTokens) FROM TokenMetrics tm WHERE tm.provider = :provider")
    Long getTotalTokensByProvider(@Param("provider") AIProvider provider);
    
    @Query("SELECT SUM(tm.estimatedCost) FROM TokenMetrics tm WHERE tm.provider = :provider")
    java.math.BigDecimal getTotalCostByProvider(@Param("provider") AIProvider provider);
    
    @Query("SELECT SUM(tm.totalTokens) FROM TokenMetrics tm WHERE tm.agentType = :agentType")
    Long getTotalTokensByAgent(@Param("agentType") AgentType agentType);
    
    @Query("SELECT SUM(tm.estimatedCost) FROM TokenMetrics tm WHERE tm.agentType = :agentType")
    java.math.BigDecimal getTotalCostByAgent(@Param("agentType") AgentType agentType);
    
    @Query("SELECT SUM(tm.totalTokens) FROM TokenMetrics tm")
    Long getTotalTokensGlobal();
    
    @Query("SELECT SUM(tm.estimatedCost) FROM TokenMetrics tm")
    java.math.BigDecimal getTotalCostGlobal();
    
    @Query("SELECT COUNT(tm) FROM TokenMetrics tm")
    Long getTotalRequestsGlobal();
    
    // Time-based aggregations
    @Query("SELECT SUM(tm.totalTokens) FROM TokenMetrics tm WHERE tm.timestamp >= :since")
    Long getTotalTokensSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT SUM(tm.estimatedCost) FROM TokenMetrics tm WHERE tm.timestamp >= :since")
    java.math.BigDecimal getTotalCostSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT SUM(tm.totalTokens) FROM TokenMetrics tm WHERE tm.userId = :userId AND tm.timestamp >= :since")
    Long getTotalTokensByUserSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT SUM(tm.estimatedCost) FROM TokenMetrics tm WHERE tm.userId = :userId AND tm.timestamp >= :since")
    java.math.BigDecimal getTotalCostByUserSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
    
    // Performance analytics
    @Query("SELECT AVG(tm.processingTimeMs) FROM TokenMetrics tm WHERE tm.processingTimeMs IS NOT NULL AND tm.provider = :provider")
    Double getAverageProcessingTimeByProvider(@Param("provider") AIProvider provider);
    
    @Query("SELECT AVG(tm.processingTimeMs) FROM TokenMetrics tm WHERE tm.processingTimeMs IS NOT NULL AND tm.agentType = :agentType")
    Double getAverageProcessingTimeByAgent(@Param("agentType") AgentType agentType);
    
    @Query("SELECT AVG(CAST(tm.totalTokens AS double) / (tm.processingTimeMs / 1000.0)) FROM TokenMetrics tm WHERE tm.processingTimeMs IS NOT NULL AND tm.processingTimeMs > 0")
    Double getAverageTokensPerSecond();
    
    // Cost efficiency queries
    @Query("SELECT AVG(tm.estimatedCost / tm.totalTokens) FROM TokenMetrics tm WHERE tm.totalTokens > 0 AND tm.estimatedCost > 0")
    java.math.BigDecimal getAverageCostPerToken();
    
    @Query("SELECT AVG(tm.estimatedCost / tm.totalTokens) FROM TokenMetrics tm WHERE tm.totalTokens > 0 AND tm.estimatedCost > 0 AND tm.provider = :provider")
    java.math.BigDecimal getAverageCostPerTokenByProvider(@Param("provider") AIProvider provider);
    
    // Provider comparison queries
    @Query("SELECT tm.provider, COUNT(tm), SUM(tm.totalTokens), SUM(tm.estimatedCost) FROM TokenMetrics tm GROUP BY tm.provider")
    List<Object[]> getProviderStatistics();
    
    @Query("SELECT tm.agentType, COUNT(tm), SUM(tm.totalTokens), SUM(tm.estimatedCost) FROM TokenMetrics tm GROUP BY tm.agentType")
    List<Object[]> getAgentStatistics();
    
    // Daily/hourly trends
    @Query("SELECT DATE(tm.timestamp), SUM(tm.totalTokens), SUM(tm.estimatedCost) FROM TokenMetrics tm WHERE tm.timestamp >= :since GROUP BY DATE(tm.timestamp) ORDER BY DATE(tm.timestamp)")
    List<Object[]> getDailyUsageTrends(@Param("since") LocalDateTime since);
    
    @Query("SELECT EXTRACT(HOUR FROM tm.timestamp), SUM(tm.totalTokens) FROM TokenMetrics tm WHERE tm.timestamp >= :since GROUP BY EXTRACT(HOUR FROM tm.timestamp) ORDER BY EXTRACT(HOUR FROM tm.timestamp)")
    List<Object[]> getHourlyUsageTrends(@Param("since") LocalDateTime since);
    
    // Top users queries
    @Query("SELECT tm.userId, SUM(tm.totalTokens), SUM(tm.estimatedCost), COUNT(tm) FROM TokenMetrics tm GROUP BY tm.userId ORDER BY SUM(tm.totalTokens) DESC")
    List<Object[]> getTopUsersByTokenUsage(Pageable pageable);
    
    @Query("SELECT tm.userId, SUM(tm.totalTokens), SUM(tm.estimatedCost), COUNT(tm) FROM TokenMetrics tm GROUP BY tm.userId ORDER BY SUM(tm.estimatedCost) DESC")
    List<Object[]> getTopUsersByCost(Pageable pageable);
    
    // Failure analysis
    @Query("SELECT COUNT(tm) FROM TokenMetrics tm WHERE tm.success = false")
    Long getFailureCount();
    
    @Query("SELECT tm.provider, COUNT(tm) FROM TokenMetrics tm WHERE tm.success = false GROUP BY tm.provider")
    List<Object[]> getFailureCountByProvider();
    
    @Query("SELECT tm.agentType, COUNT(tm) FROM TokenMetrics tm WHERE tm.success = false GROUP BY tm.agentType")
    List<Object[]> getFailureCountByAgent();
    
    // Efficiency queries
    @Query("SELECT AVG(CAST(tm.outputTokens AS double) / tm.inputTokens) FROM TokenMetrics tm WHERE tm.inputTokens > 0")
    Double getAverageEfficiencyRatio();
    
    @Query("SELECT AVG(CAST(tm.outputTokens AS double) / tm.inputTokens) FROM TokenMetrics tm WHERE tm.inputTokens > 0 AND tm.agentType = :agentType")
    Double getAverageEfficiencyRatioByAgent(@Param("agentType") AgentType agentType);
    
    // Clean up old data
    void deleteByTimestampBefore(LocalDateTime timestamp);
    
    @Query("DELETE FROM TokenMetrics tm WHERE tm.timestamp < :retentionDate")
    void cleanupOldMetrics(@Param("retentionDate") LocalDateTime retentionDate);
}

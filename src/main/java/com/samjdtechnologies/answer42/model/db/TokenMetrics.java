package com.samjdtechnologies.answer42.model.db;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing token usage metrics for AI operations.
 * Tracks token consumption, costs, and performance metrics across providers and agents.
 */
@Entity
@Table(name = "token_metrics", schema = "answer42")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class TokenMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private AIProvider provider;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 100)
    private AgentType agentType;
    
    @Column(name = "task_id", length = 100)
    private String taskId;
    
    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens;
    
    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens;
    
    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;
    
    @Column(name = "estimated_cost", precision = 10, scale = 6)
    private BigDecimal estimatedCost;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "paper_id")
    private UUID paperId;
    
    @Column(name = "model_name", length = 100)
    private String modelName;
    
    @Column(name = "temperature")
    private Double temperature;
    
    @Column(name = "max_tokens")
    private Integer maxTokens;
    
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = true;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (totalTokens == null && inputTokens != null && outputTokens != null) {
            totalTokens = inputTokens + outputTokens;
        }
        if (estimatedCost == null) {
            estimatedCost = BigDecimal.ZERO;
        }
        if (success == null) {
            success = true;
        }
    }
    
    /**
     * Calculate cost per token for analysis.
     */
    public BigDecimal getCostPerToken() {
        if (totalTokens == null || totalTokens == 0 || estimatedCost == null) {
            return BigDecimal.ZERO;
        }
        return estimatedCost.divide(BigDecimal.valueOf(totalTokens), 8, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Get tokens per second if processing time is available.
     */
    public Double getTokensPerSecond() {
        if (totalTokens == null || processingTimeMs == null || processingTimeMs == 0) {
            return null;
        }
        return (double) totalTokens / (processingTimeMs / 1000.0);
    }
    
    /**
     * Check if this was a fallback operation.
     */
    public boolean isFallbackOperation() {
        return provider == AIProvider.OLLAMA;
    }
    
    /**
     * Get efficiency ratio (output tokens / input tokens).
     */
    public Double getEfficiencyRatio() {
        if (inputTokens == null || inputTokens == 0 || outputTokens == null) {
            return null;
        }
        return (double) outputTokens / inputTokens;
    }
}

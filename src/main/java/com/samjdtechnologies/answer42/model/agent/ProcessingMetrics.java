package com.samjdtechnologies.answer42.model.agent;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;

import lombok.Builder;
import lombok.Data;

/**
 * Metrics collected during agent processing operations.
 * Implements Serializable for Spring Batch compatibility.
 */
@Data
@Builder
public class ProcessingMetrics implements Serializable {
    private static final long serialVersionUID = 1L;
    private final AgentType agentType;
    private final AIProvider provider;
    private final Instant startTime;
    private final Instant endTime;
    private final Duration processingTime;
    private final TokenUsage tokenUsage;
    private final ThreadPoolLoadStatus threadPoolStatus;
    private final AgentMemoryUsage memoryUsage;
    private final String model;
    private final boolean fromCache;
    
    /**
     * Token usage information for AI operations.
     */
    @Data
    @Builder
    public static class TokenUsage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int inputTokens;
        private final int outputTokens;
        private final int totalTokens;
        private final String model;
        private final String provider;
        private final double cost;
    }
    
    /**
     * Thread pool load status at time of processing.
     */
    @Data
    @Builder
    public static class ThreadPoolLoadStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int activeThreads;
        private final int poolSize;
        private final int maximumPoolSize;
        private final int queueSize;
        @Builder.Default
        private final double loadPercentage = 0.0;
        
        /**
         * Calculate load percentage.
         */
        public double calculateLoadPercentage() {
            return maximumPoolSize > 0 ? (double) activeThreads / maximumPoolSize : 0.0;
        }
    }
    
    /**
     * Agent memory usage statistics.
     */
    @Data
    @Builder
    public static class AgentMemoryUsage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final long memoryEntries;
        private final long totalMemorySize;
        private final double memoryEfficiency;
    }
    
    /**
     * Calculate processing duration from start and end times.
     */
    public Duration calculateDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return processingTime != null ? processingTime : Duration.ZERO;
    }
    
    /**
     * Create basic metrics with minimal information.
     */
    public static ProcessingMetrics basic(AgentType agentType, AIProvider provider) {
        return ProcessingMetrics.builder()
            .agentType(agentType)
            .provider(provider)
            .startTime(Instant.now())
            .build();
    }
    
    /**
     * Create metrics with token usage.
     */
    public static ProcessingMetrics withTokenUsage(
            AgentType agentType, 
            AIProvider provider, 
            TokenUsage tokenUsage) {
        return ProcessingMetrics.builder()
            .agentType(agentType)
            .provider(provider)
            .startTime(Instant.now())
            .tokenUsage(tokenUsage)
            .build();
    }
}

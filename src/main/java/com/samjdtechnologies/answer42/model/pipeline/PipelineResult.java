package com.samjdtechnologies.answer42.model.pipeline;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.samjdtechnologies.answer42.model.enums.PipelineStatus;
import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a complete pipeline execution.
 * Contains all stage results and overall pipeline metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResult {
    
    private UUID pipelineId;
    private UUID paperId;
    private UUID userId;
    private PipelineStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Duration totalProcessingTime;
    
    private Map<StageType, StageResult> stageResults;
    private List<String> warnings;
    private List<String> errors;
    private String errorMessage;
    
    private PipelineMetrics metrics;
    
    /**
     * Check if the pipeline completed successfully.
     */
    public boolean isSuccess() {
        return status == PipelineStatus.COMPLETED;
    }
    
    /**
     * Check if the pipeline failed.
     */
    public boolean isFailed() {
        return status == PipelineStatus.FAILED;
    }
    
    /**
     * Get a specific stage result.
     */
    public StageResult getStageResult(StageType stageType) {
        return stageResults != null ? stageResults.get(stageType) : null;
    }
    
    /**
     * Check if a stage completed successfully.
     */
    public boolean isStageSuccessful(StageType stageType) {
        StageResult result = getStageResult(stageType);
        return result != null && result.isSuccess();
    }
    
    /**
     * Get all successful stage results.
     */
    public Map<StageType, StageResult> getSuccessfulStages() {
        return stageResults.entrySet().stream()
            .filter(entry -> entry.getValue().isSuccess())
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }
    
    /**
     * Get all failed stage results.
     */
    public Map<StageType, StageResult> getFailedStages() {
        return stageResults.entrySet().stream()
            .filter(entry -> !entry.getValue().isSuccess())
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }
    
    /**
     * Calculate success percentage.
     */
    public double getSuccessPercentage() {
        if (stageResults == null || stageResults.isEmpty()) {
            return 0.0;
        }
        
        long successfulStages = stageResults.values().stream()
            .mapToLong(result -> result.isSuccess() ? 1 : 0)
            .sum();
        
        return (double) successfulStages / stageResults.size() * 100.0;
    }
    
    /**
     * Create a successful pipeline result.
     */
    public static PipelineResult success(
            UUID pipelineId, 
            UUID paperId, 
            Map<StageType, StageResult> stageResults) {
        return PipelineResult.builder()
            .pipelineId(pipelineId)
            .paperId(paperId)
            .status(PipelineStatus.COMPLETED)
            .stageResults(stageResults)
            .endTime(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create a failed pipeline result.
     */
    public static PipelineResult failure(
            UUID pipelineId, 
            UUID paperId, 
            String errorMessage) {
        return PipelineResult.builder()
            .pipelineId(pipelineId)
            .paperId(paperId)
            .status(PipelineStatus.FAILED)
            .errorMessage(errorMessage)
            .endTime(LocalDateTime.now())
            .build();
    }
    
    /**
     * Pipeline execution metrics.
     */
    @Data
    @Builder
    public static class PipelineMetrics {
        private int totalStages;
        private int successfulStages;
        private int failedStages;
        private Duration totalTime;
        private Duration averageStageTime;
        private long totalTokensUsed;
        private double totalCost;
        private int retryCount;
    }
}

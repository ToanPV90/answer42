package com.samjdtechnologies.answer42.model.pipeline;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.samjdtechnologies.answer42.model.enums.PipelineStatus;
import com.samjdtechnologies.answer42.model.enums.StageStatus;
import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the current state of a pipeline execution.
 * Tracks progress, stage completion, and intermediate results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineState {
    
    private UUID id;
    private UUID paperId;
    private UUID userId;
    private PipelineConfiguration configuration;
    private PipelineStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    @Builder.Default
    private Map<StageType, StageStatus> stageStatuses = new HashMap<>();
    
    @Builder.Default
    private Map<StageType, StageResult> stageResults = new HashMap<>();
    
    private String errorMessage;
    private Double progressPercentage;
    private StageType currentStage;
    
    /**
     * Update the status of a specific stage.
     */
    public void updateStage(StageType stage, StageStatus status) {
        stageStatuses.put(stage, status);
        updateProgress();
    }
    
    /**
     * Add a result for a completed stage.
     */
    public void addStageResult(StageType stage, StageResult result) {
        stageResults.put(stage, result);
        stageStatuses.put(stage, StageStatus.COMPLETED);
        updateProgress();
    }
    
    /**
     * Calculate and update overall progress percentage.
     */
    private void updateProgress() {
        if (configuration == null) {
            progressPercentage = 0.0;
            return;
        }
        
        int totalStages = configuration.getRequiredStages().size();
        long completedStages = stageStatuses.values().stream()
            .mapToLong(status -> status == StageStatus.COMPLETED ? 1 : 0)
            .sum();
        
        progressPercentage = totalStages > 0 ? (double) completedStages / totalStages * 100.0 : 0.0;
        
        // Update current stage
        currentStage = stageStatuses.entrySet().stream()
            .filter(entry -> entry.getValue() == StageStatus.RUNNING)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if the pipeline is complete.
     */
    public boolean isComplete() {
        return status == PipelineStatus.COMPLETED || status == PipelineStatus.FAILED;
    }
    
    /**
     * Mark the pipeline as failed with an error message.
     */
    public void markFailed(String errorMessage) {
        this.status = PipelineStatus.FAILED;
        this.errorMessage = errorMessage;
        this.endTime = LocalDateTime.now();
    }
    
    /**
     * Mark the pipeline as completed successfully.
     */
    public void markCompleted() {
        this.status = PipelineStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        this.progressPercentage = 100.0;
    }
}

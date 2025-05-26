package com.samjdtechnologies.answer42.model.pipeline;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Progress update for pipeline execution.
 * Used to communicate progress information to UI and monitoring systems.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineProgressUpdate {

    /**
     * Unique identifier for the pipeline.
     */
    private UUID pipelineId;

    /**
     * Progress percentage (0-100).
     */
    private double progressPercentage;

    /**
     * Current stage being executed.
     */
    private StageType currentStage;

    /**
     * Status message for the current progress.
     */
    private String statusMessage;

    /**
     * Timestamp of this progress update.
     */
    @Builder.Default
    private ZonedDateTime timestamp = ZonedDateTime.now();

    /**
     * Estimated time remaining in seconds.
     */
    private Long estimatedTimeRemainingSeconds;

    /**
     * Whether the pipeline has completed successfully.
     */
    @Builder.Default
    private boolean completed = false;

    /**
     * Whether the pipeline has failed.
     */
    @Builder.Default
    private boolean failed = false;

    /**
     * Error message if the pipeline failed.
     */
    private String errorMessage;

    /**
     * Number of stages completed.
     */
    private int stagesCompleted;

    /**
     * Total number of stages.
     */
    private int totalStages;

    /**
     * Create a progress update for a specific stage.
     */
    public static PipelineProgressUpdate forStage(UUID pipelineId, StageType stage, double progress) {
        return PipelineProgressUpdate.builder()
            .pipelineId(pipelineId)
            .currentStage(stage)
            .progressPercentage(progress)
            .statusMessage("Processing " + stage.getDisplayName())
            .build();
    }

    /**
     * Create a completion update.
     */
    public static PipelineProgressUpdate completed(UUID pipelineId) {
        return PipelineProgressUpdate.builder()
            .pipelineId(pipelineId)
            .progressPercentage(100.0)
            .statusMessage("Pipeline completed successfully")
            .completed(true)
            .build();
    }

    /**
     * Create a failure update.
     */
    public static PipelineProgressUpdate failed(UUID pipelineId, String errorMessage) {
        return PipelineProgressUpdate.builder()
            .pipelineId(pipelineId)
            .statusMessage("Pipeline failed: " + errorMessage)
            .errorMessage(errorMessage)
            .failed(true)
            .build();
    }

    /**
     * Create an initialization update.
     */
    public static PipelineProgressUpdate initializing(UUID pipelineId) {
        return PipelineProgressUpdate.builder()
            .pipelineId(pipelineId)
            .progressPercentage(0.0)
            .statusMessage("Initializing pipeline...")
            .build();
    }

    /**
     * Check if this update represents a terminal state.
     */
    public boolean isTerminal() {
        return completed || failed;
    }

    /**
     * Get formatted progress string.
     */
    public String getFormattedProgress() {
        if (completed) {
            return "100% - Completed";
        }
        if (failed) {
            return "Failed";
        }
        return String.format("%.1f%% - %s", progressPercentage, 
            currentStage != null ? currentStage.getDisplayName() : "Processing");
    }

    /**
     * Get estimated time remaining as formatted string.
     */
    public String getFormattedTimeRemaining() {
        if (estimatedTimeRemainingSeconds == null || estimatedTimeRemainingSeconds <= 0) {
            return "Unknown";
        }
        
        long minutes = estimatedTimeRemainingSeconds / 60;
        long seconds = estimatedTimeRemainingSeconds % 60;
        
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Get stage progress string.
     */
    public String getStageProgress() {
        if (totalStages > 0) {
            return String.format("%d/%d stages", stagesCompleted, totalStages);
        }
        return "Processing...";
    }
}

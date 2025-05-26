package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.enums.PipelineStatus;
import com.samjdtechnologies.answer42.model.enums.StageStatus;
import com.samjdtechnologies.answer42.model.enums.StageType;
import com.samjdtechnologies.answer42.model.pipeline.PipelineConfiguration;
import com.samjdtechnologies.answer42.model.pipeline.PipelineProgressUpdate;
import com.samjdtechnologies.answer42.model.pipeline.PipelineState;
import com.samjdtechnologies.answer42.model.pipeline.StageResult;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Manages pipeline state and progress tracking.
 * Handles state persistence, progress updates, and WebSocket notifications.
 */
@Component
public class PipelineStateManager {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineStateManager.class);

    private final Map<UUID, PipelineState> activePipelines = new ConcurrentHashMap<>();
    private final PaperRepository paperRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskScheduler taskScheduler;

    public PipelineStateManager(
            PaperRepository paperRepository,
            Optional<SimpMessagingTemplate> messagingTemplate,
            ThreadPoolTaskScheduler taskScheduler) {
        this.paperRepository = paperRepository;
        this.messagingTemplate = messagingTemplate.orElse(null);
        this.taskScheduler = taskScheduler;
    }

    /**
     * Initialize a new pipeline.
     */
    public PipelineState initializePipeline(UUID paperId, UUID userId, PipelineConfiguration config) {
        LoggingUtil.info(LOG, "initializePipeline",
            "Initializing pipeline for paper %s", paperId);

        PipelineState state = PipelineState.builder()
            .id(UUID.randomUUID())
            .paperId(paperId)
            .userId(userId)
            .configuration(config)
            .status(PipelineStatus.INITIALIZING)
            .startTime(ZonedDateTime.now())
            .build();

        activePipelines.put(state.getId(), state);

        // Update paper status
        updatePaperStatus(paperId, "PIPELINE_INITIATED");

        // Send initial progress update
        sendProgressUpdate(state, PipelineProgressUpdate.initializing(state.getId()));

        LoggingUtil.info(LOG, "initializePipeline",
            "Initialized pipeline %s for paper %s", state.getId(), paperId);

        return state;
    }

    /**
     * Update stage status and notify listeners.
     */
    public void updateStageStatus(UUID pipelineId, StageType stageType, StageStatus status) {
        PipelineState state = activePipelines.get(pipelineId);
        if (state == null) {
            LoggingUtil.warn(LOG, "updateStageStatus",
                "Pipeline %s not found for stage update", pipelineId);
            return;
        }

        // Update stage status using the correct method
        state.updateStage(stageType, status);

        // Calculate overall progress
        double progress = calculateProgress(state);

        // Create progress update
        PipelineProgressUpdate update = PipelineProgressUpdate.builder()
            .pipelineId(pipelineId)
            .currentStage(stageType)
            .progressPercentage(progress)
            .statusMessage(getStatusMessage(stageType, status))
            .stagesCompleted(getCompletedStageCount(state))
            .totalStages(getTotalStageCount(state))
            .build();

        // Send progress update
        sendProgressUpdate(state, update);

        LoggingUtil.info(LOG, "updateStageStatus",
            "Updated stage %s to %s for pipeline %s (%.1f%% complete)",
            stageType, status, pipelineId, progress);
    }

    /**
     * Mark pipeline as completed.
     */
    public void completePipeline(UUID pipelineId, Map<StageType, StageResult> results) {
        PipelineState state = activePipelines.get(pipelineId);
        if (state == null) {
            LoggingUtil.warn(LOG, "completePipeline",
                "Pipeline %s not found for completion", pipelineId);
            return;
        }

        state.setStatus(PipelineStatus.COMPLETED);
        state.setEndTime(ZonedDateTime.now());
        state.setStageResults(results);

        // Update paper status
        updatePaperStatus(state.getPaperId(), "COMPLETED");

        // Send completion update
        sendProgressUpdate(state, PipelineProgressUpdate.completed(pipelineId));

        // Remove from active pipelines after a delay
        schedulePipelineCleanup(pipelineId);

        LoggingUtil.info(LOG, "completePipeline",
            "Completed pipeline %s for paper %s", pipelineId, state.getPaperId());
    }

    /**
     * Mark pipeline as failed.
     */
    public void failPipeline(UUID pipelineId, String errorMessage) {
        PipelineState state = activePipelines.get(pipelineId);
        if (state == null) {
            LoggingUtil.warn(LOG, "failPipeline",
                "Pipeline %s not found for failure", pipelineId);
            return;
        }

        state.setStatus(PipelineStatus.FAILED);
        state.setEndTime(ZonedDateTime.now());
        state.setErrorMessage(errorMessage);

        // Update paper status
        updatePaperStatus(state.getPaperId(), "FAILED");

        // Send failure update
        sendProgressUpdate(state, PipelineProgressUpdate.failed(pipelineId, errorMessage));

        // Remove from active pipelines after a delay
        schedulePipelineCleanup(pipelineId);

        LoggingUtil.error(LOG, "failPipeline",
            "Failed pipeline %s for paper %s: %s", pipelineId, state.getPaperId(), errorMessage);
    }

    /**
     * Get pipeline state.
     */
    public Optional<PipelineState> getPipelineState(UUID pipelineId) {
        return Optional.ofNullable(activePipelines.get(pipelineId));
    }

    /**
     * Get all active pipelines.
     */
    public List<PipelineState> getActivePipelines() {
        return List.copyOf(activePipelines.values());
    }

    /**
     * Calculate progress percentage based on completed stages.
     */
    private double calculateProgress(PipelineState state) {
        Map<StageType, StageStatus> stageStatuses = state.getStageStatuses();
        if (stageStatuses.isEmpty()) {
            return 0.0;
        }

        long completedStages = stageStatuses.values().stream()
            .mapToLong(status -> status == StageStatus.COMPLETED ? 1 : 0)
            .sum();

        return (double) completedStages / stageStatuses.size() * 100.0;
    }

    /**
     * Get status message for a stage and status.
     */
    private String getStatusMessage(StageType stageType, StageStatus status) {
        switch (status) {
            case PENDING:
                return "Waiting for " + stageType.getDisplayName();
            case RUNNING:
                return "Running " + stageType.getDisplayName();
            case COMPLETED:
                return "Completed " + stageType.getDisplayName();
            case FAILED:
                return "Failed " + stageType.getDisplayName();
            case SKIPPED:
                return "Skipped " + stageType.getDisplayName();
            default:
                return "Unknown status for " + stageType.getDisplayName();
        }
    }

    /**
     * Get count of completed stages.
     */
    private int getCompletedStageCount(PipelineState state) {
        return (int) state.getStageStatuses().values().stream()
            .mapToLong(status -> status == StageStatus.COMPLETED ? 1 : 0)
            .sum();
    }

    /**
     * Get total stage count.
     */
    private int getTotalStageCount(PipelineState state) {
        return state.getStageStatuses().size();
    }

    /**
     * Send progress update via WebSocket.
     */
    private void sendProgressUpdate(PipelineState state, PipelineProgressUpdate update) {
        if (messagingTemplate != null) {
            try {
                String destination = "/topic/pipeline/" + state.getPaperId();
                messagingTemplate.convertAndSend(destination, update);

                LoggingUtil.debug(LOG, "sendProgressUpdate",
                    "Sent progress update for pipeline %s: %.1f%%",
                    state.getId(), update.getProgressPercentage());

            } catch (Exception e) {
                LoggingUtil.warn(LOG, "sendProgressUpdate",
                    "Failed to send progress update for pipeline %s: %s",
                    state.getId(), e.getMessage());
            }
        }
    }

    /**
     * Update paper status in database.
     */
    private void updatePaperStatus(UUID paperId, String status) {
        try {
            paperRepository.findById(paperId).ifPresent(paper -> {
                paper.setProcessingStatus(status);
                paper.setUpdatedAt(ZonedDateTime.now());
                paperRepository.save(paper);
            });
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "updatePaperStatus",
                "Failed to update paper status for %s: %s", paperId, e.getMessage());
        }
    }

    /**
     * Schedule cleanup of completed pipeline using ThreadConfig's TaskScheduler.
     */
    private void schedulePipelineCleanup(UUID pipelineId) {
        try {
            // Schedule cleanup after 1 hour using a Date instead of ZonedDateTime
            Date cleanupTime = new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
            
            taskScheduler.schedule(() -> {
                activePipelines.remove(pipelineId);
                LoggingUtil.debug(LOG, "schedulePipelineCleanup",
                    "Cleaned up pipeline %s", pipelineId);
            }, cleanupTime);

            LoggingUtil.debug(LOG, "schedulePipelineCleanup",
                "Scheduled cleanup for pipeline %s in 1 hour", pipelineId);

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "schedulePipelineCleanup",
                "Failed to schedule cleanup for pipeline %s: %s", pipelineId, e.getMessage());
        }
    }
}

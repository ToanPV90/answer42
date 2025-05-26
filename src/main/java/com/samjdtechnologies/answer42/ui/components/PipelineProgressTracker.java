package com.samjdtechnologies.answer42.ui.components;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.enums.StageStatus;
import com.samjdtechnologies.answer42.service.AgentTaskService;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

/**
 * Component for tracking and displaying real-time pipeline processing progress.
 * Shows overall progress and individual agent status with visual indicators.
 */
@Component
public class PipelineProgressTracker extends VerticalLayout {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineProgressTracker.class);
    
    private final AgentTaskService taskService;
    private ScheduledExecutorService scheduler;
    private UUID paperId;
    private UI ui;
    
    // UI Components
    private Span statusLabel;
    private ProgressBar overallProgress;
    private VerticalLayout agentDetails;
    private Map<String, HorizontalLayout> agentComponents;
    private ZonedDateTime startTime;
    
    /**
     * Creates a new PipelineProgressTracker.
     * 
     * @param taskService The agent task service for monitoring progress
     */
    public PipelineProgressTracker(AgentTaskService taskService) {
        this.taskService = taskService;
        this.agentComponents = new HashMap<>();
        this.startTime = ZonedDateTime.now();
        
        addClassName("pipeline-progress-container");
        setSpacing(true);
        setPadding(true);
        setWidthFull();
        
        initializeComponents();
        
        LoggingUtil.debug(LOG, "PipelineProgressTracker", "Progress tracker initialized");
    }
    
    /**
     * Initializes the UI components for progress tracking.
     */
    private void initializeComponents() {
        // Overall progress section
        statusLabel = new Span("Initializing pipeline...");
        statusLabel.addClassName("pipeline-status-label");
        
        overallProgress = new ProgressBar(0.0, 100.0);
        overallProgress.setValue(0.0);
        overallProgress.setWidthFull();
        overallProgress.addClassName("pipeline-overall-progress");
        
        // Agent details section
        H3 agentTitle = new H3("Processing Stages");
        agentTitle.addClassName("agent-progress-title");
        
        agentDetails = new VerticalLayout();
        agentDetails.addClassName("agent-progress-details");
        agentDetails.setSpacing(true);
        agentDetails.setPadding(false);
        
        // Create progress components for each agent
        createAgentProgressComponents();
        
        add(statusLabel, overallProgress, agentTitle, agentDetails);
    }
    
    /**
     * Creates progress components for each agent in the pipeline.
     */
    private void createAgentProgressComponents() {
        String[] agents = {
            "paper-processor", "metadata-enhancer", "content-summarizer",
            "concept-explainer", "quality-checker", "citation-formatter"
        };
        
        String[] agentDisplayNames = {
            "Text Extraction", "Metadata Enhancement", "Content Summarization",
            "Concept Explanation", "Quality Assessment", "Citation Formatting"
        };
        
        for (int i = 0; i < agents.length; i++) {
            String agentId = agents[i];
            String displayName = agentDisplayNames[i];
            
            HorizontalLayout agentItem = createAgentProgressItem(agentId, displayName);
            agentComponents.put(agentId, agentItem);
            agentDetails.add(agentItem);
        }
    }
    
    /**
     * Creates a progress item for an individual agent.
     * 
     * @param agentId The agent identifier
     * @param displayName The display name for the agent
     * @return The agent progress item layout
     */
    private HorizontalLayout createAgentProgressItem(String agentId, String displayName) {
        HorizontalLayout agentItem = new HorizontalLayout();
        agentItem.addClassName("agent-progress-item");
        agentItem.setAlignItems(Alignment.CENTER);
        agentItem.setWidthFull();
        
        // Status icon
        Icon statusIcon = VaadinIcon.CLOCK.create();
        statusIcon.addClassName("agent-status-icon");
        statusIcon.addClassName("pending");
        statusIcon.setSize("20px");
        
        // Agent name
        Span agentName = new Span(displayName);
        agentName.addClassName("agent-name");
        agentName.getStyle().set("flex-grow", "1");
        
        // Status text
        Span agentStatus = new Span("Pending");
        agentStatus.addClassName("agent-status");
        agentStatus.addClassName("pending");
        
        agentItem.add(statusIcon, agentName, agentStatus);
        
        return agentItem;
    }
    
    /**
     * Starts monitoring pipeline progress for the specified paper.
     * 
     * @param paperId The ID of the paper being processed
     * @param ui The UI instance for updates
     */
    public void startMonitoring(UUID paperId, UI ui) {
        this.paperId = paperId;
        this.ui = ui;
        this.startTime = ZonedDateTime.now();
        
        LoggingUtil.info(LOG, "startMonitoring", "Starting progress monitoring for paper %s", paperId);
        
        // Update initial status
        ui.access(() -> {
            statusLabel.setText("Pipeline processing started...");
            overallProgress.setValue(5.0); // Initial progress
        });
        
        // Start the monitoring scheduler
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateProgress();
            } catch (Exception e) {
                LoggingUtil.error(LOG, "startMonitoring", "Error updating progress: %s", e.getMessage());
            }
        }, 1, 3, TimeUnit.SECONDS); // Update every 3 seconds
    }
    
    /**
     * Updates the progress display based on current pipeline status.
     */
    private void updateProgress() {
        if (ui == null || paperId == null) {
            return;
        }
        
        try {
            // Get current pipeline status from task service
            PipelineProgressStatus status = getPipelineStatus();
            
            ui.access(() -> {
                updateOverallProgress(status);
                updateAgentStatuses(status);
                
                // Stop monitoring if completed or failed
                if (status.isComplete()) {
                    stopMonitoring();
                }
            });
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updateProgress", "Error updating progress: %s", e.getMessage());
        }
    }
    
    /**
     * Updates the overall progress bar and status label.
     * 
     * @param status The current pipeline status
     */
    private void updateOverallProgress(PipelineProgressStatus status) {
        double progressValue = status.getProgressPercentage();
        overallProgress.setValue(progressValue);
        
        // Update status text
        String statusText = status.getCurrentStage();
        if (status.isComplete()) {
            if (status.isSuccess()) {
                statusText = "Pipeline processing completed successfully!";
                statusLabel.addClassName("success");
            } else {
                statusText = "Pipeline processing failed.";
                statusLabel.addClassName("error");
            }
        } else {
            // Add elapsed time
            Duration elapsed = Duration.between(startTime, ZonedDateTime.now());
            String elapsedText = formatDuration(elapsed);
            statusText += " (Elapsed: " + elapsedText + ")";
        }
        
        statusLabel.setText(statusText);
    }
    
    /**
     * Updates individual agent status indicators.
     * 
     * @param status The current pipeline status
     */
    private void updateAgentStatuses(PipelineProgressStatus status) {
        for (Map.Entry<String, HorizontalLayout> entry : agentComponents.entrySet()) {
            String agentId = entry.getKey();
            HorizontalLayout agentItem = entry.getValue();
            
            Icon statusIcon = (Icon) agentItem.getComponentAt(0);
            Span agentStatus = (Span) agentItem.getComponentAt(2);
            
            StageStatus agentStageStatus = status.getAgentStatus(agentId);
            
            updateAgentComponent(statusIcon, agentStatus, agentStageStatus);
        }
    }
    
    /**
     * Updates an individual agent component based on its status.
     * 
     * @param statusIcon The status icon to update
     * @param agentStatus The status text to update
     * @param stageStatus The current stage status
     */
    private void updateAgentComponent(Icon statusIcon, Span agentStatus, StageStatus stageStatus) {
        // Remove all existing status classes
        statusIcon.removeClassName("pending");
        statusIcon.removeClassName("processing");
        statusIcon.removeClassName("completed");
        statusIcon.removeClassName("failed");
        
        agentStatus.removeClassName("pending");
        agentStatus.removeClassName("processing");
        agentStatus.removeClassName("completed");
        agentStatus.removeClassName("failed");
        
        switch (stageStatus) {
            case COMPLETED:
                statusIcon.getElement().setAttribute("icon", "vaadin:check-circle");
                statusIcon.addClassName("completed");
                agentStatus.setText("Completed");
                agentStatus.addClassName("completed");
                break;
                
            case FAILED:
                statusIcon.getElement().setAttribute("icon", "vaadin:exclamation-circle");
                statusIcon.addClassName("failed");
                agentStatus.setText("Failed");
                agentStatus.addClassName("failed");
                break;
                
            case RUNNING:
                statusIcon.getElement().setAttribute("icon", "vaadin:cog");
                statusIcon.addClassName("processing");
                agentStatus.setText("Processing...");
                agentStatus.addClassName("processing");
                break;
                
            case PENDING:
            default:
                statusIcon.getElement().setAttribute("icon", "vaadin:clock");
                statusIcon.addClassName("pending");
                agentStatus.setText("Pending");
                agentStatus.addClassName("pending");
                break;
        }
    }
    
    /**
     * Gets the current pipeline status (mock implementation).
     * In real implementation, this would query the task service.
     * 
     * @return The current pipeline status
     */
    private PipelineProgressStatus getPipelineStatus() {
        // This is a simplified mock implementation
        // In reality, this would query AgentTaskService for actual status
        
        Duration elapsed = Duration.between(startTime, ZonedDateTime.now());
        long elapsedSeconds = elapsed.getSeconds();
        
        // Simulate progress over time
        double progress = Math.min(elapsedSeconds * 2.0, 100.0); // 2% per second
        
        // Mock agent statuses based on elapsed time
        Map<String, StageStatus> agentStatuses = new HashMap<>();
        agentStatuses.put("paper-processor", elapsedSeconds > 5 ? StageStatus.COMPLETED : 
                         elapsedSeconds > 2 ? StageStatus.RUNNING : StageStatus.PENDING);
        agentStatuses.put("metadata-enhancer", elapsedSeconds > 10 ? StageStatus.COMPLETED : 
                         elapsedSeconds > 7 ? StageStatus.RUNNING : StageStatus.PENDING);
        agentStatuses.put("content-summarizer", elapsedSeconds > 20 ? StageStatus.COMPLETED : 
                         elapsedSeconds > 15 ? StageStatus.RUNNING : StageStatus.PENDING);
        agentStatuses.put("concept-explainer", elapsedSeconds > 30 ? StageStatus.COMPLETED : 
                         elapsedSeconds > 25 ? StageStatus.RUNNING : StageStatus.PENDING);
        agentStatuses.put("quality-checker", elapsedSeconds > 40 ? StageStatus.COMPLETED : 
                         elapsedSeconds > 35 ? StageStatus.RUNNING : StageStatus.PENDING);
        agentStatuses.put("citation-formatter", elapsedSeconds > 50 ? StageStatus.COMPLETED : 
                         elapsedSeconds > 45 ? StageStatus.RUNNING : StageStatus.PENDING);
        
        String currentStage = "Initializing";
        if (elapsedSeconds > 45) currentStage = "Citation Formatting";
        else if (elapsedSeconds > 35) currentStage = "Quality Assessment";
        else if (elapsedSeconds > 25) currentStage = "Concept Explanation";
        else if (elapsedSeconds > 15) currentStage = "Content Summarization";
        else if (elapsedSeconds > 7) currentStage = "Metadata Enhancement";
        else if (elapsedSeconds > 2) currentStage = "Text Extraction";
        
        boolean isComplete = progress >= 100.0;
        boolean isSuccess = isComplete;
        
        return new PipelineProgressStatus(progress, currentStage, agentStatuses, isComplete, isSuccess);
    }
    
    /**
     * Stops the progress monitoring.
     */
    public void stopMonitoring() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            LoggingUtil.info(LOG, "stopMonitoring", "Progress monitoring stopped for paper %s", paperId);
        }
    }
    
    /**
     * Formats a duration for display.
     * 
     * @param duration The duration to format
     * @return Formatted duration string
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Cleanup when component is detached.
     */
    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        stopMonitoring();
    }
    
    /**
     * Inner class to represent pipeline progress status.
     */
    public static class PipelineProgressStatus {
        private final double progressPercentage;
        private final String currentStage;
        private final Map<String, StageStatus> agentStatuses;
        private final boolean complete;
        private final boolean success;
        
        public PipelineProgressStatus(double progressPercentage, String currentStage, 
                                    Map<String, StageStatus> agentStatuses, boolean complete, boolean success) {
            this.progressPercentage = progressPercentage;
            this.currentStage = currentStage;
            this.agentStatuses = agentStatuses;
            this.complete = complete;
            this.success = success;
        }
        
        public double getProgressPercentage() { return progressPercentage; }
        public String getCurrentStage() { return currentStage; }
        public StageStatus getAgentStatus(String agentId) { 
            return agentStatuses.getOrDefault(agentId, StageStatus.PENDING); 
        }
        public boolean isComplete() { return complete; }
        public boolean isSuccess() { return success; }
    }
}

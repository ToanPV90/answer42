package com.samjdtechnologies.answer42.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.model.daos.AgentMemoryStore;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.events.AgentTaskEvent;
import com.samjdtechnologies.answer42.repository.AgentMemoryStoreRepository;
import com.samjdtechnologies.answer42.repository.AgentTaskRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import jakarta.persistence.EntityNotFoundException;

/**
 * Production-ready service for managing agent task lifecycle and coordination.
 * Provides task creation, execution tracking, performance monitoring, and Spring Event integration.
 */
@Service
@Transactional
public class AgentTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentTaskService.class);

    private final AgentTaskRepository agentTaskRepository;
    private final AgentMemoryStoreRepository memoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentTaskService(
            AgentTaskRepository agentTaskRepository,
            AgentMemoryStoreRepository memoryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.agentTaskRepository = agentTaskRepository;
        this.memoryRepository = memoryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new agent task and stores it in the database.
     * Publishes a TASK_CREATED event via Spring's event system.
     */
    public AgentTask createTask(String taskId, String agentId, UUID userId, JsonNode input) {
        LoggingUtil.info(LOG, "createTask", "Creating task %s for agent %s and user %s", taskId, agentId, userId);

        try {
            AgentTask task = AgentTask.builder()
                .id(taskId)
                .agentId(agentId)
                .userId(userId)
                .input(input)
                .status("pending")
                .createdAt(Instant.now())
                .build();

            AgentTask savedTask = agentTaskRepository.save(task);

            // Publish Spring Application Event
            eventPublisher.publishEvent(AgentTaskEvent.created(this, savedTask));

            LoggingUtil.info(LOG, "createTask", "Successfully created task %s", taskId);
            return savedTask;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "createTask", "Failed to create task %s", e, taskId);
            throw new RuntimeException("Failed to create agent task", e);
        }
    }

    /**
     * Updates task status to processing and records start time.
     * Publishes a TASK_STARTED event via Spring's event system.
     */
    public AgentTask startTask(String taskId) {
        LoggingUtil.info(LOG, "startTask", "Starting task %s", taskId);

        return agentTaskRepository.findById(taskId)
            .map(task -> {
                task.markStarted();
                AgentTask savedTask = agentTaskRepository.save(task);
                
                // Publish Spring Application Event
                eventPublisher.publishEvent(AgentTaskEvent.started(this, savedTask));
                
                LoggingUtil.info(LOG, "startTask", "Successfully started task %s", taskId);
                return savedTask;
            })
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    }

    /**
     * Marks task as completed with result.
     * Publishes a TASK_COMPLETED event via Spring's event system.
     */
    public AgentTask completeTask(String taskId, JsonNode result) {
        LoggingUtil.info(LOG, "completeTask", "Completing task %s", taskId);

        return agentTaskRepository.findById(taskId)
            .map(task -> {
                task.markCompleted(result);
                AgentTask savedTask = agentTaskRepository.save(task);

                // Update agent memory if this was a paper processing task
                if ("paper-processor".equals(task.getAgentId())) {
                    updateProcessedPapersMemory(task);
                }

                // Publish Spring Application Event
                eventPublisher.publishEvent(AgentTaskEvent.completed(this, savedTask));
                
                LoggingUtil.info(LOG, "completeTask", "Successfully completed task %s in %d ms", 
                    taskId, task.getProcessingDuration().toMillis());
                return savedTask;
            })
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    }

    /**
     * Marks task as failed with error message.
     * Publishes a TASK_FAILED event via Spring's event system.
     */
    public AgentTask failTask(String taskId, String errorMessage) {
        LoggingUtil.error(LOG, "failTask", "Failing task %s: %s", null, taskId, errorMessage);

        return agentTaskRepository.findById(taskId)
            .map(task -> {
                task.markFailed(errorMessage);
                AgentTask savedTask = agentTaskRepository.save(task);
                
                // Publish Spring Application Event
                eventPublisher.publishEvent(AgentTaskEvent.failed(this, savedTask));
                
                return savedTask;
            })
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    }

    /**
     * Marks task as timed out.
     * Publishes a TASK_TIMEOUT event via Spring's event system.
     */
    public AgentTask timeoutTask(String taskId, String timeoutMessage) {
        LoggingUtil.warn(LOG, "timeoutTask", "Task %s timed out: %s", taskId, timeoutMessage);

        return agentTaskRepository.findById(taskId)
            .map(task -> {
                task.markFailed("Task timed out: " + timeoutMessage);
                AgentTask savedTask = agentTaskRepository.save(task);
                
                // Publish Spring Application Event for timeout
                eventPublisher.publishEvent(AgentTaskEvent.timeout(this, savedTask));
                
                return savedTask;
            })
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    }

    /**
     * Checks if a paper has already been processed by looking at agent memory.
     */
    public boolean isPaperAlreadyProcessed(String paperId) {
        try {
            return memoryRepository.findProcessedPapersMemory()
                .map(memory -> memory.hasPaperBeenProcessed(paperId))
                .orElse(false);
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "isPaperAlreadyProcessed", 
                "Error checking processed papers memory: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Updates the processed papers memory after successful paper processing.
     */
    private void updateProcessedPapersMemory(AgentTask task) {
        try {
            JsonNode input = task.getInput();
            if (input.has("paperId")) {
                String paperId = input.get("paperId").asText();

                AgentMemoryStore memory = memoryRepository.findProcessedPapersMemory()
                    .orElse(AgentMemoryStore.createProcessedPapersMemory(new ArrayList<>()));

                memory.addProcessedPaperId(paperId);
                memoryRepository.save(memory);

                LoggingUtil.info(LOG, "updateProcessedPapersMemory", 
                    "Updated processed papers memory with paper %s", paperId);
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updateProcessedPapersMemory", 
                "Failed to update processed papers memory", e);
        }
    }

    /**
     * Gets all active tasks for load balancing decisions.
     */
    public List<AgentTask> getActiveTasks() {
        try {
            return agentTaskRepository.findByStatus("pending")
                .stream()
                .sorted(Comparator.comparing(AgentTask::getCreatedAt))
                .collect(Collectors.toList());
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getActiveTasks", "Failed to retrieve active tasks", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets task performance metrics for monitoring.
     */
    public Map<String, Object> getTaskMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            metrics.put("activeTasks", agentTaskRepository.countActiveTasks());
            metrics.put("activeTasksByAgent", agentTaskRepository.getActiveTaskCountsByAgent());

            // Recent failure rates
            Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
            metrics.put("recentFailures", agentTaskRepository.getFailureCountsByAgent(oneDayAgo));

            // Average processing times
            Map<String, Double> avgTimes = new HashMap<>();
            List<String> agentTypes = Arrays.asList(
                "paper-processor", "content-summarizer", "concept-explainer",
                "quality-checker", "citation-formatter", "metadata-enhancer", "perplexity-researcher"
            );
            
            for (String agentType : agentTypes) {
                Double avgTime = agentTaskRepository.getAverageProcessingTimeByAgent(agentType);
                if (avgTime != null) {
                    avgTimes.put(agentType, avgTime);
                }
            }
            metrics.put("averageProcessingTimes", avgTimes);

            return metrics;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "getTaskMetrics", "Failed to retrieve task metrics", e);
            return new HashMap<>();
        }
    }

    /**
     * Gets tasks for a specific workflow.
     */
    public List<AgentTask> getTasksForWorkflow(String workflowId) {
        try {
            return agentTaskRepository.findByWorkflowId(workflowId);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getTasksForWorkflow", 
                "Failed to retrieve tasks for workflow %s", e, workflowId);
            return new ArrayList<>();
        }
    }

    /**
     * Gets completed tasks for a specific paper.
     */
    public List<AgentTask> getCompletedTasksForPaper(String paperId) {
        try {
            return agentTaskRepository.findCompletedTasksForPaper(paperId);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getCompletedTasksForPaper", 
                "Failed to retrieve completed tasks for paper %s", e, paperId);
            return new ArrayList<>();
        }
    }

    /**
     * Gets tasks by user and status.
     */
    public List<AgentTask> getTasksByUserAndStatus(UUID userId, String status) {
        try {
            return agentTaskRepository.findByUserIdAndStatus(userId, status);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getTasksByUserAndStatus", 
                "Failed to retrieve tasks for user %s with status %s", e, userId, status);
            return new ArrayList<>();
        }
    }

    /**
     * Cleans up old completed tasks to prevent database bloat.
     * Runs every hour via Spring's @Scheduled annotation.
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupOldTasks() {
        try {
            Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS); // Keep tasks for 7 days
            int deletedCount = agentTaskRepository.deleteCompletedTasksOlderThan(cutoff);
            
            if (deletedCount > 0) {
                LoggingUtil.info(LOG, "cleanupOldTasks", "Cleaned up %d old completed tasks", deletedCount);
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "cleanupOldTasks", "Failed to cleanup old tasks", e);
        }
    }

    /**
     * Handles task timeouts by marking them as failed.
     * Runs every 5 minutes via Spring's @Scheduled annotation.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void handleTaskTimeouts() {
        try {
            Instant timeoutThreshold = Instant.now().minus(300, ChronoUnit.SECONDS); // 5 minute timeout
            List<AgentTask> timedOutTasks = agentTaskRepository.findTimedOutTasks(timeoutThreshold);

            for (AgentTask task : timedOutTasks) {
                timeoutTask(task.getId(), "Task exceeded 5 minute processing limit");
            }

            if (!timedOutTasks.isEmpty()) {
                LoggingUtil.warn(LOG, "handleTaskTimeouts", "Handled %d timed out tasks", timedOutTasks.size());
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "handleTaskTimeouts", "Failed to handle task timeouts", e);
        }
    }
}

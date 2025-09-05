package com.samjdtechnologies.answer42.model.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;

import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentTaskEventType;

import lombok.Getter;

/**
 * Spring Application Event for agent task lifecycle events.
 * Published when agent tasks change state (created, started, completed, failed).
 */
@Getter
public class AgentTaskEvent extends ApplicationEvent {

    private final AgentTaskEventType eventType;
    private final String taskId;
    private final String agentId;
    private final UUID userId;
    private final String status;
    private final Instant eventTimestamp; // Renamed to avoid conflict with ApplicationEvent.getTimestamp()
    private final AgentTask task;

    public AgentTaskEvent(Object source, AgentTaskEventType eventType, AgentTask task) {
        super(source);
        this.eventType = eventType;
        this.taskId = task.getId();
        this.agentId = task.getAgentId();
        this.userId = task.getUserId();
        this.status = task.getStatus();
        this.eventTimestamp = Instant.now();
        this.task = task;
    }

    /**
     * Factory method for task created events.
     */
    public static AgentTaskEvent created(Object source, AgentTask task) {
        return new AgentTaskEvent(source, AgentTaskEventType.TASK_CREATED, task);
    }

    /**
     * Factory method for task started events.
     */
    public static AgentTaskEvent started(Object source, AgentTask task) {
        return new AgentTaskEvent(source, AgentTaskEventType.TASK_STARTED, task);
    }

    /**
     * Factory method for task completed events.
     */
    public static AgentTaskEvent completed(Object source, AgentTask task) {
        return new AgentTaskEvent(source, AgentTaskEventType.TASK_COMPLETED, task);
    }

    /**
     * Factory method for task failed events.
     */
    public static AgentTaskEvent failed(Object source, AgentTask task) {
        return new AgentTaskEvent(source, AgentTaskEventType.TASK_FAILED, task);
    }

    /**
     * Factory method for task timeout events.
     */
    public static AgentTaskEvent timeout(Object source, AgentTask task) {
        return new AgentTaskEvent(source, AgentTaskEventType.TASK_TIMEOUT, task);
    }

    @Override
    public String toString() {
        return String.format("AgentTaskEvent{type=%s, taskId=%s, agentId=%s, userId=%s, status=%s, timestamp=%s}",
            eventType, taskId, agentId, userId, status, eventTimestamp);
    }
}

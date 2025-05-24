package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of agent task event types for Spring event system.
 * Used to categorize different agent task lifecycle events.
 */
public enum AgentTaskEventType {
    
    /**
     * Event fired when an agent task is created.
     */
    TASK_CREATED("Task Created"),
    
    /**
     * Event fired when an agent task starts processing.
     */
    TASK_STARTED("Task Started"),
    
    /**
     * Event fired when an agent task completes successfully.
     */
    TASK_COMPLETED("Task Completed"),
    
    /**
     * Event fired when an agent task fails.
     */
    TASK_FAILED("Task Failed"),
    
    /**
     * Event fired when an agent task times out.
     */
    TASK_TIMEOUT("Task Timeout"),
    
    /**
     * Event fired when an agent task is cancelled.
     */
    TASK_CANCELLED("Task Cancelled");

    private final String displayName;

    AgentTaskEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

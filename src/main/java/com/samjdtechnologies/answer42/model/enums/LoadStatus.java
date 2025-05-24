package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of load status levels for agents.
 * Used to indicate current capacity and availability of agents.
 */
public enum LoadStatus {
    LOW("Low load - agent has plenty of capacity"),
    MEDIUM("Medium load - agent is moderately busy"),
    HIGH("High load - agent is near capacity"),
    OVERLOADED("Overloaded - agent should not accept new tasks"),
    UNAVAILABLE("Unavailable - agent is offline or in error state");

    private final String description;

    LoadStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if the agent can accept new tasks.
     */
    public boolean canAcceptTasks() {
        return this == LOW || this == MEDIUM;
    }

    /**
     * Check if the agent should be prioritized for new tasks.
     */
    public boolean shouldPrioritize() {
        return this == LOW;
    }

    /**
     * Check if the agent needs attention (high load or issues).
     */
    public boolean needsAttention() {
        return this == HIGH || this == OVERLOADED || this == UNAVAILABLE;
    }
}

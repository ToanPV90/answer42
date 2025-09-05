package com.samjdtechnologies.answer42.model.interfaces;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.LoadStatus;

/**
 * Base interface for all AI agents in the multi-agent pipeline.
 * Each agent implements specific processing capabilities while following consistent patterns.
 */
public interface AIAgent {
    
    /**
     * Unique identifier for this agent type.
     */
    AgentType getAgentType();

    /**
     * AI provider this agent uses.
     */
    AIProvider getProvider();

    /**
     * Process the given task and return results.
     */
    CompletableFuture<AgentResult> process(AgentTask task);

    /**
     * Validate that this agent can handle the given task.
     */
    boolean canHandle(AgentTask task);

    /**
     * Get estimated processing time for the task.
     */
    Duration estimateProcessingTime(AgentTask task);

    /**
     * Get current load/capacity status.
     */
    LoadStatus getLoadStatus();
}

package com.samjdtechnologies.answer42.model.pipeline;

import java.time.Duration;
import java.util.List;

import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a stage in the pipeline execution plan.
 * Contains stage metadata, dependencies, and execution configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StageDefinition {

    /**
     * The type of stage being executed.
     */
    private StageType type;

    /**
     * The agent responsible for executing this stage.
     */
    private AgentType agentType;

    /**
     * List of stages that must complete before this stage can execute.
     */
    @Builder.Default
    private List<StageType> dependencies = List.of();

    /**
     * Whether this stage can execute in parallel with other stages.
     */
    @Builder.Default
    private boolean parallel = false;

    /**
     * Whether this stage is required for pipeline completion.
     */
    @Builder.Default
    private boolean required = true;

    /**
     * Priority level for execution (higher numbers execute first).
     */
    @Builder.Default
    private int priority = 0;

    /**
     * Estimated execution time for this stage.
     */
    @Builder.Default
    private Duration estimatedDuration = Duration.ofMinutes(2);

    /**
     * Maximum allowed execution time before timeout.
     */
    @Builder.Default
    private Duration maxDuration = Duration.ofMinutes(10);

    /**
     * Whether this stage can be retried on failure.
     */
    @Builder.Default
    private boolean retryable = true;

    /**
     * Maximum number of retry attempts.
     */
    @Builder.Default
    private int maxRetries = 2;

    /**
     * Check if all dependencies are satisfied.
     */
    public boolean areDependenciesSatisfied(List<StageType> completedStages) {
        return completedStages.containsAll(dependencies);
    }

    /**
     * Get the stage name for display purposes.
     */
    public String getDisplayName() {
        return type.getDisplayName();
    }

    /**
     * Get the stage description.
     */
    public String getDescription() {
        return String.format("%s using %s agent", type.getDescription(), agentType.getDisplayName());
    }

    /**
     * Check if this stage should execute before another stage.
     */
    public boolean shouldExecuteBefore(StageDefinition other) {
        // Higher priority executes first
        if (this.priority != other.priority) {
            return this.priority > other.priority;
        }
        
        // If this stage is a dependency of the other, it should execute first
        return other.dependencies.contains(this.type);
    }
}

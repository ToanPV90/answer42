package com.samjdtechnologies.answer42.model.pipeline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Execution plan for a pipeline, containing all stages and their execution order.
 * Manages stage dependencies and parallel execution opportunities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {

    /**
     * All stages in the execution plan.
     */
    private List<StageDefinition> stages = new ArrayList<>();

    /**
     * Total estimated execution time for the entire plan.
     */
    private Duration totalEstimatedTime;

    /**
     * Maximum allowed execution time for the entire plan.
     */
    private Duration maxExecutionTime;

    /**
     * Whether parallel execution is enabled.
     */
    private boolean parallelExecutionEnabled = true;

    /**
     * Get stages grouped by their execution level (dependency depth).
     */
    public List<List<StageDefinition>> getExecutionLevels() {
        Map<Integer, List<StageDefinition>> levelMap = stages.stream()
            .collect(Collectors.groupingBy(this::calculateExecutionLevel));

        return levelMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    /**
     * Calculate the execution level of a stage based on its dependencies.
     */
    private int calculateExecutionLevel(StageDefinition stage) {
        if (stage.getDependencies().isEmpty()) {
            return 0;
        }

        return stage.getDependencies().stream()
            .mapToInt(depType -> {
                StageDefinition depStage = findStageByType(depType);
                return depStage != null ? calculateExecutionLevel(depStage) + 1 : 0;
            })
            .max()
            .orElse(0);
    }

    /**
     * Find a stage by its type.
     */
    private StageDefinition findStageByType(StageType type) {
        return stages.stream()
            .filter(stage -> stage.getType() == type)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the next stages ready for execution.
     */
    public List<StageDefinition> getNextReadyStages(List<StageType> completedStages) {
        return stages.stream()
            .filter(stage -> !completedStages.contains(stage.getType()))
            .filter(stage -> stage.areDependenciesSatisfied(completedStages))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(Collectors.toList());
    }

    /**
     * Check if the plan is complete.
     */
    public boolean isComplete(List<StageType> completedStages) {
        return stages.stream()
            .filter(StageDefinition::isRequired)
            .allMatch(stage -> completedStages.contains(stage.getType()));
    }

    /**
     * Calculate progress percentage.
     */
    public double calculateProgress(List<StageType> completedStages) {
        long requiredStages = stages.stream()
            .filter(StageDefinition::isRequired)
            .count();

        long completedRequired = stages.stream()
            .filter(StageDefinition::isRequired)
            .filter(stage -> completedStages.contains(stage.getType()))
            .count();

        return requiredStages > 0 ? (double) completedRequired / requiredStages * 100.0 : 100.0;
    }

    /**
     * Add a stage to the execution plan.
     */
    public void addStage(StageType stageType, AgentType agentType) {
        stages.add(StageDefinition.builder()
            .type(stageType)
            .agentType(agentType)
            .required(true)
            .build());
        recalculateTimings();
    }

    /**
     * Add a stage with dependencies.
     */
    public void addStage(StageType stageType, AgentType agentType, List<StageType> dependencies) {
        stages.add(StageDefinition.builder()
            .type(stageType)
            .agentType(agentType)
            .dependencies(dependencies)
            .required(true)
            .build());
        recalculateTimings();
    }

    /**
     * Add a parallel stage.
     */
    public void addParallelStage(StageType stageType, AgentType agentType, List<StageType> dependencies) {
        stages.add(StageDefinition.builder()
            .type(stageType)
            .agentType(agentType)
            .dependencies(dependencies)
            .parallel(true)
            .required(true)
            .build());
        recalculateTimings();
    }

    /**
     * Recalculate timing estimates.
     */
    private void recalculateTimings() {
        totalEstimatedTime = stages.stream()
            .map(StageDefinition::getEstimatedDuration)
            .reduce(Duration.ZERO, Duration::plus);
        
        maxExecutionTime = totalEstimatedTime.multipliedBy(2);
    }

    /**
     * Builder pattern for creating execution plans.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ExecutionPlan plan = new ExecutionPlan();

        public Builder addStage(StageType stageType, AgentType agentType) {
            plan.addStage(stageType, agentType);
            return this;
        }

        public Builder addStage(StageType stageType, AgentType agentType, List<StageType> dependencies) {
            plan.addStage(stageType, agentType, dependencies);
            return this;
        }

        public Builder addParallelStage(StageType stageType, AgentType agentType, List<StageType> dependencies) {
            plan.addParallelStage(stageType, agentType, dependencies);
            return this;
        }

        public Builder parallelExecutionEnabled(boolean enabled) {
            plan.parallelExecutionEnabled = enabled;
            return this;
        }

        public ExecutionPlan build() {
            plan.recalculateTimings();
            return plan;
        }
    }
}

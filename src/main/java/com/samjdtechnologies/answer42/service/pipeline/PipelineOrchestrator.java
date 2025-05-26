package com.samjdtechnologies.answer42.service.pipeline;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.LoadStatus;
import com.samjdtechnologies.answer42.model.enums.StageStatus;
import com.samjdtechnologies.answer42.model.enums.StageType;
import com.samjdtechnologies.answer42.model.interfaces.AIAgent;
import com.samjdtechnologies.answer42.model.pipeline.ExecutionPlan;
import com.samjdtechnologies.answer42.model.pipeline.PipelineConfiguration;
import com.samjdtechnologies.answer42.model.pipeline.PipelineException;
import com.samjdtechnologies.answer42.model.pipeline.PipelineProgressUpdate;
import com.samjdtechnologies.answer42.model.pipeline.PipelineResult;
import com.samjdtechnologies.answer42.model.pipeline.PipelineState;
import com.samjdtechnologies.answer42.model.pipeline.StageDefinition;
import com.samjdtechnologies.answer42.model.pipeline.StageResult;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Central orchestrator for the multi-agent pipeline.
 * Coordinates all agent activities and manages pipeline execution flow.
 */
@Service
@Transactional
public class PipelineOrchestrator {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineOrchestrator.class);
    
    private final Map<AgentType, AIAgent> agents = new ConcurrentHashMap<>();
    private final Executor taskExecutor; // From ThreadConfig
    private final AIConfig aiConfig; // Integration with existing AIConfig
    private final PipelineStateManager stateManager;

    public PipelineOrchestrator(
            ThreadConfig threadConfig,
            AIConfig aiConfig,
            PipelineStateManager stateManager) {
        this.taskExecutor = threadConfig.taskExecutor();
        this.aiConfig = aiConfig;
        this.stateManager = stateManager;
    }

    /**
     * Executes the complete paper processing pipeline using ThreadConfig executor.
     */
    @Async("taskExecutor") // Uses ThreadConfig's taskExecutor bean
    public CompletableFuture<PipelineResult> processPaper(
            UUID paperId, 
            UUID userId,
            PipelineConfiguration config,
            Consumer<PipelineProgressUpdate> progressCallback) {

        LoggingUtil.info(LOG, "processPaper", 
            "Starting pipeline for paper %s with config %s", paperId, config);

        try {
            // Initialize pipeline state with user ID
            PipelineState state = stateManager.initializePipeline(paperId, userId, config);

            // Create execution plan using AIConfig providers
            ExecutionPlan plan = createExecutionPlan(config);

            // Execute pipeline stages with ThreadConfig executor
            return executeStages(state, plan, progressCallback);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "processPaper", 
                "Failed to start pipeline for paper %s", e, paperId);
            throw new PipelineException("Pipeline initialization failed", e);
        }
    }

    private ExecutionPlan createExecutionPlan(PipelineConfiguration config) {
        ExecutionPlan.Builder builder = ExecutionPlan.builder();

        // Stage 1: Text Extraction (Required)
        builder.addStage(StageType.TEXT_EXTRACTION, AgentType.PAPER_PROCESSOR);

        // Stage 2: Metadata Enhancement (Parallel)
        if (config.isIncludeMetadataEnhancement()) {
            builder.addStage(StageType.METADATA_ENHANCEMENT, AgentType.METADATA_ENHANCER);
        }

        // Stage 3: Content Analysis (Sequential)
        builder.addStage(StageType.CONTENT_ANALYSIS, AgentType.CONTENT_SUMMARIZER);
        builder.addStage(StageType.CONCEPT_EXTRACTION, AgentType.CONCEPT_EXPLAINER);

        // Stage 4: Citation Processing (Conditional)
        if (config.isIncludeCitationProcessing()) {
            builder.addStage(StageType.CITATION_PROCESSING, AgentType.CITATION_FORMATTER);
        }

        // Stage 5: Research Discovery (Conditional)
        if (config.isIncludeResearchDiscovery()) {
            builder.addStage(StageType.RESEARCH_DISCOVERY, AgentType.RELATED_PAPER_DISCOVERY);
        }

        // Stage 6: Perplexity Research (Conditional - for fact verification and external research)
        if (config.isIncludePerplexityResearch()) {
            builder.addStage(StageType.PERPLEXITY_RESEARCH, AgentType.PERPLEXITY_RESEARCHER);
        }

        // Stage 6: Quality Verification (Always last)
        builder.addStage(StageType.QUALITY_CHECK, AgentType.QUALITY_CHECKER);

        return builder.build();
    }

    private CompletableFuture<PipelineResult> executeStages(
            PipelineState state, 
            ExecutionPlan plan, 
            Consumer<PipelineProgressUpdate> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<StageDefinition> stages = plan.getStages();
                
                for (int i = 0; i < stages.size(); i++) {
                    StageDefinition stage = stages.get(i);
                    
                    // Update progress
                    double progress = (double) i / stages.size() * 100;
                    progressCallback.accept(PipelineProgressUpdate.forStage(
                        state.getId(), stage.getType(), progress));
                    
                    // Execute stage
                    StageResult result = executeStage(stage, state);
                    
                    // Update state
                    stateManager.updateStageStatus(
                        state.getId(), 
                        stage.getType(), 
                        result.isSuccess() ? StageStatus.COMPLETED : StageStatus.FAILED
                    );
                    
                    if (!result.isSuccess()) {
                        throw new PipelineException(
                            "Stage " + stage.getType() + " failed: " + result.getErrorMessage());
                    }
                    
                    // Store stage result in state
                    state.addStageResult(stage.getType(), result);
                }
                
                // Final progress update
                progressCallback.accept(PipelineProgressUpdate.completed(state.getId()));
                
                return PipelineResult.success(state.getPaperId(), state.getUserId(), state.getStageResults());
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "executeStages", 
                    "Pipeline execution failed for paper %s", e, state.getPaperId());
                return PipelineResult.failure(state.getPaperId(), state.getUserId(), e.getMessage());
            }
        }, taskExecutor);
    }

    private StageResult executeStage(StageDefinition stage, PipelineState state) {
        AIAgent agent = agents.get(stage.getAgentType());
        if (agent == null) {
            throw new PipelineException("No agent found for type: " + stage.getAgentType());
        }

        // Create agent task from stage definition
        AgentTask agentTask = createAgentTask(stage, state);

        try {
            // Execute agent processing
            CompletableFuture<AgentResult> future = agent.process(agentTask);
            AgentResult result = future.get(); // Wait for completion
            
            if (result.isSuccess()) {
                return StageResult.success(stage.getType(), result.getResultData());
            } else {
                return StageResult.failure(stage.getType(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeStage", 
                "Stage %s execution failed", e, stage.getType());
            return StageResult.failure(stage.getType(), e.getMessage());
        }
    }

    private AgentTask createAgentTask(StageDefinition stage, PipelineState state) {
        // Create appropriate task based on stage type and agent
        switch (stage.getAgentType()) {
            case PAPER_PROCESSOR:
                return AgentTask.createPaperProcessingTask(
                    generateTaskId(), 
                    state.getUserId(), 
                    state.getPaperId().toString()
                );
            case CONTENT_SUMMARIZER:
                return AgentTask.createSummaryTask(
                    generateTaskId(),
                    state.getUserId(),
                    state.getPaperId().toString(),
                    "standard"
                );
            case CITATION_FORMATTER:
                return AgentTask.createCitationFormattingTask(
                    generateTaskId(),
                    state.getUserId(),
                    state.getPaperId().toString(),
                    Arrays.asList("APA", "MLA")
                );
            case RELATED_PAPER_DISCOVERY:
                return AgentTask.createRelatedPaperDiscoveryTask(
                    generateTaskId(),
                    state.getUserId(),
                    state.getPaperId().toString(),
                    "comprehensive"
                );
            case PERPLEXITY_RESEARCHER:
                return AgentTask.builder()
                    .id(generateTaskId())
                    .agentId(stage.getAgentType().getAgentId())
                    .userId(state.getUserId())
                    .input(JsonNodeFactory.instance.objectNode()
                        .put("paperId", state.getPaperId().toString())
                        .put("researchType", "fact_verification"))
                    .status("pending")
                    .build();
            default:
                // Generic task creation for other agents
                return AgentTask.builder()
                    .id(generateTaskId())
                    .agentId(stage.getAgentType().getAgentId())
                    .userId(state.getUserId())
                    .status("pending")
                    .build();
        }
    }

    /**
     * Register an agent with the orchestrator.
     */
    public void registerAgent(AIAgent agent) {
        agents.put(agent.getAgentType(), agent);
        LoggingUtil.info(LOG, "registerAgent", 
            "Registered agent %s using provider %s", 
            agent.getAgentType(), agent.getProvider());
    }

    /**
     * Get status of all registered agents.
     */
    public Map<AgentType, LoadStatus> getAgentStatuses() {
        Map<AgentType, LoadStatus> statuses = new ConcurrentHashMap<>();
        agents.forEach((type, agent) -> statuses.put(type, agent.getLoadStatus()));
        return statuses;
    }

    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + Math.random();
    }
}

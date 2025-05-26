package com.samjdtechnologies.answer42.batch.tasklets;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.service.agent.PerplexityResearchAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet that wraps PerplexityResearchAgent for external research and fact verification.
 * Conducts comprehensive research using Perplexity API and stores results in execution context.
 */
@Component
public class PerplexityResearchTasklet extends BaseAgentTasklet {

    private final PerplexityResearchAgent researchAgent;

    public PerplexityResearchTasklet(PerplexityResearchAgent researchAgent) {
        this.researchAgent = researchAgent;
        
        LoggingUtil.info(LOG, "PerplexityResearchTasklet", 
            "Initialized Perplexity Research Tasklet for Spring Batch pipeline");
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting Perplexity research for paper %s", paperId);

            // Get previous step results for context
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");
            AgentResult summaryResult = getPreviousStepResult(chunkContext, "contentSummarizerResult");

            // Create research agent task with comprehensive parameters
            AgentTask researchTask = createResearchAgentTask(paperId, userId, paperResult, summaryResult);

            // Execute research using PerplexityResearchAgent
            CompletableFuture<AgentResult> future = researchAgent.process(researchTask);
            AgentResult result = future.get(); // Wait for completion

            if (result.isSuccess()) {
                LoggingUtil.info(LOG, "execute", "Successfully completed Perplexity research for paper %s", paperId);
            } else {
                LoggingUtil.warn(LOG, "execute", 
                    "Perplexity research failed for paper %s: %s", paperId, result.getErrorMessage());
            }

            // Store result using base class method
            storeStepResult(chunkContext, "perplexityResearchResult", result);

            logProcessingComplete("PerplexityResearch", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "PerplexityResearch", "perplexityResearchResult", e);
            throw new RuntimeException("Perplexity research failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create research agent task from previous step results.
     */
    private AgentTask createResearchAgentTask(UUID paperId, UUID userId, AgentResult paperResult, AgentResult summaryResult) {
        String taskId = String.format("perplexity_research_%d", System.currentTimeMillis());

        // Create input JSON with research parameters
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("operation", "external_research");

        // Extract text content from paper processor result
        String textContent = extractStringFromResult(paperResult, "textContent", "extractedText", "content", "text");
        if (textContent != null && !textContent.trim().isEmpty()) {
            // Truncate for context (first 2000 chars)
            String context = textContent.length() > 2000 ? textContent.substring(0, 2000) + "..." : textContent;
            input.put("paperContext", context);
        }

        // Extract summary for claims if available
        if (summaryResult != null && summaryResult.isSuccess() && summaryResult.getResultData() != null) {
            String detailedSummary = extractStringFromResult(summaryResult, "detailedSummary", "summary", "content");
            if (detailedSummary != null && !detailedSummary.trim().isEmpty()) {
                input.put("summaryContent", detailedSummary);
            }
        }

        // Research configuration
        input.put("verifyFacts", true);
        input.put("findRelatedPapers", true);
        input.put("analyzeTrends", true);
        input.put("researchScope", "MODERATE");

        return AgentTask.builder()
            .id(taskId)
            .agentId("perplexity-researcher")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }
}

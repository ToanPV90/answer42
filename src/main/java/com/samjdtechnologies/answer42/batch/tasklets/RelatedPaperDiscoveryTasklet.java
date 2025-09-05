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
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.service.agent.RelatedPaperDiscoveryAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for related paper discovery using RelatedPaperDiscoveryAgent.
 * Discovers related academic papers through multiple sources including Crossref, Semantic Scholar, and Perplexity.
 */
@Component
public class RelatedPaperDiscoveryTasklet extends BaseAgentTasklet {

    private final RelatedPaperDiscoveryAgent discoveryAgent;

    public RelatedPaperDiscoveryTasklet(RelatedPaperDiscoveryAgent discoveryAgent) {
        this.discoveryAgent = discoveryAgent;
        
        LoggingUtil.info(LOG, "RelatedPaperDiscoveryTasklet", 
            "Initialized Related Paper Discovery Tasklet for Spring Batch pipeline");
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting related paper discovery for paper %s", paperId);

            // Get previous step results for context
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");
            AgentResult summaryResult = getPreviousStepResult(chunkContext, "contentSummarizerResult");

            // Create discovery agent task with comprehensive parameters
            AgentTask discoveryTask = createDiscoveryAgentTask(paperId, userId, paperResult, summaryResult);

            // Execute discovery using RelatedPaperDiscoveryAgent
            CompletableFuture<AgentResult> future = discoveryAgent.process(discoveryTask);
            AgentResult result = future.get(); // Wait for completion

            if (result.isSuccess()) {
                LoggingUtil.info(LOG, "execute", "Successfully completed related paper discovery for paper %s", paperId);
            } else {
                LoggingUtil.warn(LOG, "execute", 
                    "Related paper discovery failed for paper %s: %s", paperId, result.getErrorMessage());
            }

            // Store result using base class method
            storeStepResult(chunkContext, "relatedPaperDiscoveryResult", result);

            logProcessingComplete("RelatedPaperDiscovery", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "RelatedPaperDiscovery", "relatedPaperDiscoveryResult", e);
            throw new RuntimeException("Related paper discovery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create discovery agent task from previous step results.
     */
    private AgentTask createDiscoveryAgentTask(UUID paperId, UUID userId, AgentResult paperResult, AgentResult summaryResult) {
        String taskId = String.format("related_paper_discovery_%d", System.currentTimeMillis());

        // Create input JSON with discovery parameters
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("operation", "related_paper_discovery");

        // Extract title from paper processor result for better discovery
        String title = extractStringFromResult(paperResult, "title", "paperTitle", "documentTitle");
        if (title != null && !title.trim().isEmpty()) {
            input.put("title", title);
        }

        // Extract text content from paper processor result
        String textContent = extractStringFromResult(paperResult, "textContent", "extractedText", "content", "text");
        if (textContent != null && !textContent.trim().isEmpty()) {
            // Truncate for context (first 3000 chars for better discovery context)
            String context = textContent.length() > 3000 ? textContent.substring(0, 3000) + "..." : textContent;
            input.put("paperContext", context);
        }

        // Extract summary for better keyword extraction if available
        if (summaryResult != null && summaryResult.isSuccess() && summaryResult.getResultData() != null) {
            String summary = extractStringFromResult(summaryResult, "standardSummary", "summary", "content");
            if (summary != null && !summary.trim().isEmpty()) {
                input.put("summaryContent", summary);
            }
        }

        // Discovery configuration - using comprehensive discovery
        input.put("configurationType", "comprehensive");
        input.put("maxTotalPapers", 50);
        input.put("maxPapersPerSource", 20);
        input.put("minimumRelevanceScore", 0.3);
        input.put("enableAISynthesis", true);
        input.put("parallelExecution", true);

        return AgentTask.builder()
            .id(taskId)
            .agentId("related-paper-discovery")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }
}

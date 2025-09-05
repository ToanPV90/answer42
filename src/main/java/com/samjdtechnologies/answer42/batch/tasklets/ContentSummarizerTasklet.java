package com.samjdtechnologies.answer42.batch.tasklets;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
import com.samjdtechnologies.answer42.service.agent.ContentSummarizerAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for content summarization using ContentSummarizerAgent.
 * Integrates with the multi-agent pipeline to provide comprehensive summarization.
 */
@Component
public class ContentSummarizerTasklet extends BaseAgentTasklet {

    private final ContentSummarizerAgent contentSummarizerAgent;

    public ContentSummarizerTasklet(ContentSummarizerAgent contentSummarizerAgent) {
        this.contentSummarizerAgent = contentSummarizerAgent;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods - MUCH CLEANER!
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting content summarization for paper %s", paperId);

            // Get previous step results using base class method
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");
            validatePreviousStepResult(paperResult, "Paper processor");

            // Extract text content using base class utility
            String textContent = extractStringFromResult(paperResult, "textContent", "extractedText", "content", "text");
            if (textContent == null || textContent.trim().isEmpty()) {
                throw new RuntimeException("No text content available for summarization");
            }

            LoggingUtil.info(LOG, "execute", "Extracted text content: %d characters", textContent.length());

            // Generate multiple summary types
            Map<String, AgentResult> summaryResults = generateAllSummaryTypes(paperId, textContent, userId);

            // Create combined result
            AgentResult combinedResult = createCombinedSummaryResult(paperId, summaryResults);

            // Store result using base class method
            storeStepResult(chunkContext, "contentSummarizerResult", combinedResult);

            logProcessingComplete("ContentSummarizer", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "ContentSummarizer", "contentSummarizerResult", e);
            throw new RuntimeException("Content summarization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates all summary types using the ContentSummarizerAgent.
     */
    private Map<String, AgentResult> generateAllSummaryTypes(UUID paperId, String textContent, UUID userId) {
        Map<String, AgentResult> results = new HashMap<>();
        String[] summaryTypes = {"brief", "standard", "detailed"};

        for (String summaryType : summaryTypes) {
            try {
                LoggingUtil.info(LOG, "generateAllSummaryTypes", 
                    "Generating %s summary for paper %s", summaryType, paperId);

                // Create agent task for this summary type
                AgentTask summaryTask = createSummarizerTask(paperId, textContent, summaryType, userId);

                // Execute summarization using the agent
                CompletableFuture<AgentResult> future = contentSummarizerAgent.process(summaryTask);
                AgentResult result = future.get(); // Wait for completion

                results.put(summaryType, result);

                if (result.isSuccess()) {
                    LoggingUtil.info(LOG, "generateAllSummaryTypes", 
                        "Successfully generated %s summary", summaryType);
                } else {
                    LoggingUtil.warn(LOG, "generateAllSummaryTypes", 
                        "Failed to generate %s summary: %s", summaryType, result.getErrorMessage());
                }

            } catch (Exception e) {
                LoggingUtil.error(LOG, "generateAllSummaryTypes", 
                    "Error generating %s summary", e, summaryType);
                
                // Create failure result for this summary type
                results.put(summaryType, AgentResult.failure(
                    "summarizer_" + summaryType, "Summary generation failed: " + e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Creates an agent task for summarization.
     */
    private AgentTask createSummarizerTask(UUID paperId, String textContent, String summaryType, UUID userId) {
        String taskId = String.format("content_summarizer_%s_%d", summaryType, System.currentTimeMillis());

        // Create input JSON with all required fields
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("textContent", textContent);
        input.put("summaryType", summaryType);
        input.put("operation", "content_summarization");

        return AgentTask.builder()
            .id(taskId)
            .agentId("content-summarizer")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Creates a combined result from all summary types.
     */
    private AgentResult createCombinedSummaryResult(UUID paperId, Map<String, AgentResult> summaryResults) {
        Map<String, Object> combinedData = new HashMap<>();
        combinedData.put("paperId", paperId.toString());
        combinedData.put("timestamp", Instant.now().toString());

        boolean overallSuccess = true;
        StringBuilder errorMessages = new StringBuilder();

        // Process each summary type result
        for (Map.Entry<String, AgentResult> entry : summaryResults.entrySet()) {
            String summaryType = entry.getKey();
            AgentResult result = entry.getValue();

            if (result.isSuccess() && result.getResultData() != null) {
                // Extract summary data from successful result
                Map<String, Object> summaryData = result.getResultData();
                
                // Store summary content
                Object summary = summaryData.get("summary");
                if (summary != null) {
                    combinedData.put(summaryType + "Summary", summary);
                }

                // Store additional metadata
                Object wordCount = summaryData.get("wordCount");
                if (wordCount != null) {
                    combinedData.put(summaryType + "WordCount", wordCount);
                }

                Object keyFindings = summaryData.get("keyFindings");
                if (keyFindings != null) {
                    combinedData.put(summaryType + "KeyFindings", keyFindings);
                }

                Object qualityScore = summaryData.get("qualityScore");
                if (qualityScore != null) {
                    combinedData.put(summaryType + "QualityScore", qualityScore);
                }

            } else {
                overallSuccess = false;
                String errorMsg = result.getErrorMessage() != null ? 
                    result.getErrorMessage() : "Unknown error";
                errorMessages.append(String.format("%s: %s; ", summaryType, errorMsg));
                
                // Store failure info
                combinedData.put(summaryType + "Error", errorMsg);
            }

            // Store processing status
            combinedData.put(summaryType + "Success", result.isSuccess());
        }

        // Create final result
        if (overallSuccess) {
            combinedData.put("status", "completed");
            combinedData.put("allSummariesGenerated", true);
            
            return AgentResult.builder()
                .taskId("content_summarizer_combined")
                .success(true)
                .resultData(combinedData)
                .processingTime(Duration.ofMillis(System.currentTimeMillis()))
                .build();
        } else {
            combinedData.put("status", "partial_failure");
            combinedData.put("allSummariesGenerated", false);
            combinedData.put("errors", errorMessages.toString());
            
            return AgentResult.builder()
                .taskId("content_summarizer_combined")
                .success(false)
                .resultData(combinedData)
                .errorMessage("Some summaries failed: " + errorMessages.toString())
                .processingTime(Duration.ofMillis(System.currentTimeMillis()))
                .build();
        }
    }
}

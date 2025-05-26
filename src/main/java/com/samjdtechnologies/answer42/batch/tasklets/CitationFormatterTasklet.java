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
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.service.agent.CitationFormatterAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for citation formatting using CitationFormatterAgent.
 * Integrates with the multi-agent pipeline to extract, format, and validate citations
 * from academic papers in multiple citation styles.
 */
@Component
public class CitationFormatterTasklet extends BaseAgentTasklet {

    private final CitationFormatterAgent citationFormatterAgent;

    public CitationFormatterTasklet(CitationFormatterAgent citationFormatterAgent) {
        this.citationFormatterAgent = citationFormatterAgent;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting citation formatting for paper %s", paperId);

            // Get previous step results
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");
            validatePreviousStepResult(paperResult, "Paper processor");

            // Extract text content for citation analysis
            String textContent = extractStringFromResult(paperResult, "textContent", "extractedText", "content", "text");
            if (textContent == null || textContent.trim().isEmpty()) {
                throw new RuntimeException("No text content available for citation formatting");
            }

            // Extract title and other metadata for better citation extraction
            String title = extractStringFromResult(paperResult, "title", "paperTitle", "documentTitle");

            LoggingUtil.info(LOG, "execute", "Extracted text content: %d characters for citation analysis", 
                textContent.length());

            // Create agent task for citation formatting
            AgentTask citationTask = createCitationFormatterTask(paperId, textContent, title, userId);

            // Execute citation formatting using the agent
            CompletableFuture<AgentResult> future = citationFormatterAgent.process(citationTask);
            AgentResult agentResult = future.get(); // Wait for completion

            if (!agentResult.isSuccess()) {
                throw new RuntimeException("Citation formatting failed: " + agentResult.getErrorMessage());
            }

            // Create simplified result for storage
            AgentResult combinedResult = createCitationFormattingResult(paperId, agentResult);

            // Store result using base class method
            storeStepResult(chunkContext, "citationFormatterResult", combinedResult);

            logProcessingComplete("CitationFormatter", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "CitationFormatter", "citationFormatterResult", e);
            throw new RuntimeException("Citation formatting failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an agent task for citation formatting using the format expected by CitationFormatterAgent.
     */
    private AgentTask createCitationFormatterTask(UUID paperId, String textContent, 
                                                 String title, UUID userId) {
        
        String taskId = String.format("citation_formatter_%d", System.currentTimeMillis());

        // Create input JSON with the format expected by CitationFormatterAgent
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("content", textContent); // CitationFormatterAgent expects "content" field
        input.put("operation", "citation_formatting");

        // Add optional context fields if available
        if (title != null && !title.trim().isEmpty()) {
            input.put("title", title);
        }

        // Request multiple citation styles
        input.put("requestedStyles", "APA,MLA,Chicago,IEEE"); // Common academic styles

        return AgentTask.builder()
            .id(taskId)
            .agentId("citation-formatter")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Creates a simplified result from the citation formatting for storage in the job context.
     */
    private AgentResult createCitationFormattingResult(UUID paperId, AgentResult agentResult) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("paperId", paperId.toString());
        resultData.put("timestamp", Instant.now().toString());
        resultData.put("operation", "citation_formatting_complete");

        // Extract citation data from the agent result
        if (agentResult.getResultData() != null) {
            Object citationData = agentResult.getResultData();
            
            if (citationData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> citationMap = (Map<String, Object>) citationData;
                
                // Store formatted citations by style
                if (citationMap.containsKey("formattedCitations")) {
                    resultData.put("formattedCitations", citationMap.get("formattedCitations"));
                }
                
                // Store raw citations found
                if (citationMap.containsKey("rawCitations")) {
                    resultData.put("rawCitations", citationMap.get("rawCitations"));
                }
                
                // Store citation count
                if (citationMap.containsKey("citationCount")) {
                    resultData.put("citationCount", citationMap.get("citationCount"));
                }
                
                // Store validation results
                if (citationMap.containsKey("validationResults")) {
                    resultData.put("validationResults", citationMap.get("validationResults"));
                }
                
                // Store supported styles
                if (citationMap.containsKey("supportedStyles")) {
                    resultData.put("supportedStyles", citationMap.get("supportedStyles"));
                }
                
                // Store bibliography entries
                if (citationMap.containsKey("bibliography")) {
                    resultData.put("bibliography", citationMap.get("bibliography"));
                }
                
                // Store DOI validation results
                if (citationMap.containsKey("doiValidation")) {
                    resultData.put("doiValidation", citationMap.get("doiValidation"));
                }
            }
        }

        resultData.put("status", "completed");
        resultData.put("success", true);

        return AgentResult.builder()
            .taskId("citation_formatter_combined")
            .success(true)
            .resultData(resultData)
            .processingTime(Duration.ofMillis(System.currentTimeMillis()))
            .build();
    }
}

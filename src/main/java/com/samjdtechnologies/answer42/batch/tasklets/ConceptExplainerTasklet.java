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
import com.samjdtechnologies.answer42.model.concept.ConceptExplanationResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.service.agent.ConceptExplainerAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for concept explanation using ConceptExplainerAgent.
 * Integrates with the multi-agent pipeline to provide technical term explanations
 * and concept relationship mapping for academic papers.
 */
@Component
public class ConceptExplainerTasklet extends BaseAgentTasklet {

    private final ConceptExplainerAgent conceptExplainerAgent;

    public ConceptExplainerTasklet(ConceptExplainerAgent conceptExplainerAgent) {
        this.conceptExplainerAgent = conceptExplainerAgent;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting concept explanation for paper %s", paperId);

            // Get previous step results
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");
            validatePreviousStepResult(paperResult, "Paper processor");

            // Extract text content using base class utility
            String textContent = extractStringFromResult(paperResult, "textContent", "extractedText", "content", "text");
            if (textContent == null || textContent.trim().isEmpty()) {
                throw new RuntimeException("No text content available for concept explanation");
            }

            // Extract title and abstract for better context
            String title = extractStringFromResult(paperResult, "title", "paperTitle", "documentTitle");
            String abstractText = extractStringFromResult(paperResult, "abstract", "abstractText", "summary");

            LoggingUtil.info(LOG, "execute", "Extracted text content: %d characters", textContent.length());

            // Create agent task for concept explanation
            AgentTask conceptTask = createConceptExplainerTask(paperId, textContent, title, abstractText, userId);

            // Execute concept explanation using the agent
            CompletableFuture<AgentResult> future = conceptExplainerAgent.process(conceptTask);
            AgentResult agentResult = future.get(); // Wait for completion

            if (!agentResult.isSuccess()) {
                throw new RuntimeException("Concept explanation failed: " + agentResult.getErrorMessage());
            }

            // Extract the ConceptExplanationResult from the agent result
            Object resultData = agentResult.getResultData();
            if (!(resultData instanceof ConceptExplanationResult)) {
                throw new RuntimeException("Unexpected result type from ConceptExplainerAgent: " + 
                    (resultData != null ? resultData.getClass().getSimpleName() : "null"));
            }

            ConceptExplanationResult explanationResult = (ConceptExplanationResult) resultData;

            // Create a simplified result for storage
            AgentResult combinedResult = createConceptExplanationResult(paperId, explanationResult);

            // Store result using base class method
            storeStepResult(chunkContext, "conceptExplainerResult", combinedResult);

            logProcessingComplete("ConceptExplainer", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "ConceptExplainer", "conceptExplainerResult", e);
            throw new RuntimeException("Concept explanation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an agent task for concept explanation using the format expected by ConceptExplainerAgent.
     */
    private AgentTask createConceptExplainerTask(UUID paperId, String textContent, 
                                                String title, String abstractText, UUID userId) {
        
        String taskId = String.format("concept_explainer_%d", System.currentTimeMillis());

        // Create input JSON with the format expected by ConceptExplainerAgent
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("content", textContent); // ConceptExplainerAgent expects "content" field
        input.put("operation", "concept_explanation");

        // Add optional context fields if available
        if (title != null && !title.trim().isEmpty()) {
            input.put("title", title);
        }

        if (abstractText != null && !abstractText.trim().isEmpty()) {
            input.put("abstract", abstractText);
        }

        return AgentTask.builder()
            .id(taskId)
            .agentId("concept-explainer")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Creates a simplified result from the ConceptExplanationResult for storage in the job context.
     */
    private AgentResult createConceptExplanationResult(UUID paperId, ConceptExplanationResult explanationResult) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("paperId", paperId.toString());
        resultData.put("timestamp", Instant.now().toString());
        resultData.put("operation", "concept_explanation_complete");

        // Extract key information from the ConceptExplanationResult
        if (explanationResult.getExplanations() != null) {
            Map<String, Object> explanationsByLevel = new HashMap<>();
            
            explanationResult.getExplanations().forEach((level, explanations) -> {
                Map<String, Object> levelData = new HashMap<>();
                
                if (explanations.getExplanations() != null) {
                    levelData.put("explanations", explanations.getExplanations());
                    levelData.put("termCount", explanations.getExplanations().size());
                }
                
                levelData.put("educationLevel", level.getDisplayName());
                explanationsByLevel.put(level.name().toLowerCase(), levelData);
            });
            
            resultData.put("explanationsByLevel", explanationsByLevel);
        }

        // Add relationship map if available
        if (explanationResult.getRelationshipMap() != null) {
            Map<String, Object> relationshipData = new HashMap<>();
            relationshipData.put("nodeCount", explanationResult.getRelationshipMap().getNodes().size());
            relationshipData.put("edgeCount", explanationResult.getRelationshipMap().getEdges().size());
            resultData.put("conceptRelationships", relationshipData);
        }

        // Add quality metrics
        resultData.put("overallQualityScore", explanationResult.getOverallQualityScore());
        
        // Add metadata
        if (explanationResult.getProcessingMetadata() != null) {
            resultData.put("metadata", explanationResult.getProcessingMetadata());
        }

        resultData.put("status", "completed");
        resultData.put("success", true);

        return AgentResult.builder()
            .taskId("concept_explainer_combined")
            .success(true)
            .resultData(resultData)
            .processingTime(Duration.ofMillis(System.currentTimeMillis()))
            .build();
    }
}

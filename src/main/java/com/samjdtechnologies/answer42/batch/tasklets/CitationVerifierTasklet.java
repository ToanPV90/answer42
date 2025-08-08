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
import com.samjdtechnologies.answer42.service.agent.CitationVerifierAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for citation verification using CitationVerifierAgent.
 * Integrates with the multi-agent pipeline to verify citations against external sources
 * using AI-enhanced fuzzy matching and external APIs like Semantic Scholar.
 */
@Component
public class CitationVerifierTasklet extends BaseAgentTasklet {

    private final CitationVerifierAgent citationVerifierAgent;

    public CitationVerifierTasklet(CitationVerifierAgent citationVerifierAgent) {
        this.citationVerifierAgent = citationVerifierAgent;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting citation verification for paper %s", paperId);

            // Get previous step results - CitationVerifier typically runs after CitationFormatter
            AgentResult citationFormatterResult = getPreviousStepResult(chunkContext, "citationFormatterResult");
            if (citationFormatterResult != null) {
                validatePreviousStepResult(citationFormatterResult, "Citation formatter");
                LoggingUtil.info(LOG, "execute", "Found citation formatter results for verification");
            } else {
                LoggingUtil.info(LOG, "execute", "No previous citation formatter results found, proceeding with verification");
            }

            // Create agent task for citation verification
            AgentTask verificationTask = createCitationVerifierTask(paperId, userId);

            // Execute citation verification using the agent
            CompletableFuture<AgentResult> future = citationVerifierAgent.process(verificationTask);
            AgentResult agentResult = future.get(); // Wait for completion

            if (!agentResult.isSuccess()) {
                LoggingUtil.warn(LOG, "execute", "Citation verification failed for paper %s: %s", 
                    paperId, agentResult.getErrorMessage());
                // Don't throw exception - citation verification failure shouldn't stop the pipeline
            }

            // Create simplified result for storage
            AgentResult combinedResult = createCitationVerificationResult(paperId, agentResult);

            // Store result using base class method
            storeStepResult(chunkContext, "citationVerifierResult", combinedResult);

            logProcessingComplete("CitationVerifier", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "CitationVerifier", "citationVerifierResult", e);
            // For citation verification, we might want to continue pipeline even if this fails
            LoggingUtil.warn(LOG, "execute", "Citation verification failed but continuing pipeline", e);
            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Creates an agent task for citation verification using the format expected by CitationVerifierAgent.
     */
    private AgentTask createCitationVerifierTask(UUID paperId, UUID userId) {
        
        String taskId = String.format("citation_verifier_%d", System.currentTimeMillis());

        // Create input JSON with the format expected by CitationVerifierAgent
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("operation", "citation_verification");

        // Add verification options
        input.put("verifyAgainstSemanticScholar", true);
        input.put("verifyAgainstCrossref", true);
        input.put("verifyAgainstArxiv", true);
        input.put("useAIFuzzyMatching", true);
        input.put("confidenceThreshold", 0.7); // 70% confidence threshold for verification

        return AgentTask.builder()
            .id(taskId)
            .agentId("citation-verifier")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Creates a simplified result from the citation verification for storage in the job context.
     */
    private AgentResult createCitationVerificationResult(UUID paperId, AgentResult agentResult) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("paperId", paperId.toString());
        resultData.put("timestamp", Instant.now().toString());
        resultData.put("operation", "citation_verification_complete");

        // Set default values for failed verification
        boolean success = agentResult.isSuccess();
        int verificationsCount = 0;
        int verifiedCount = 0;
        double verificationRate = 0.0;

        // Extract verification data from the agent result
        if (success && agentResult.getResultData() != null) {
            Object verificationData = agentResult.getResultData();
            
            if (verificationData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> verificationMap = (Map<String, Object>) verificationData;
                
                // Store verification counts
                if (verificationMap.containsKey("verificationsCount")) {
                    Object count = verificationMap.get("verificationsCount");
                    verificationsCount = (count instanceof Number) ? ((Number) count).intValue() : 0;
                }
                
                if (verificationMap.containsKey("verifiedCount")) {
                    Object count = verificationMap.get("verifiedCount");
                    verifiedCount = (count instanceof Number) ? ((Number) count).intValue() : 0;
                }
                
                if (verificationMap.containsKey("verificationRate")) {
                    Object rate = verificationMap.get("verificationRate");
                    verificationRate = (rate instanceof Number) ? ((Number) rate).doubleValue() : 0.0;
                }
                
                // Store detailed verification results
                if (verificationMap.containsKey("verifications")) {
                    resultData.put("verifications", verificationMap.get("verifications"));
                }
                
                // Store verification summary by source
                if (verificationMap.containsKey("verificationsBySource")) {
                    resultData.put("verificationsBySource", verificationMap.get("verificationsBySource"));
                }
                
                // Store confidence statistics
                if (verificationMap.containsKey("averageConfidence")) {
                    resultData.put("averageConfidence", verificationMap.get("averageConfidence"));
                }
                
                // Store error details if any
                if (verificationMap.containsKey("errors")) {
                    resultData.put("errors", verificationMap.get("errors"));
                }
                
                // Store processing metrics
                if (verificationMap.containsKey("processingTime")) {
                    resultData.put("processingTime", verificationMap.get("processingTime"));
                }
            }
        }

        // Store summary statistics
        resultData.put("verificationsCount", verificationsCount);
        resultData.put("verifiedCount", verifiedCount);
        resultData.put("verificationRate", verificationRate);
        resultData.put("status", success ? "completed" : "failed");
        resultData.put("success", success);

        // Add error message if verification failed
        if (!success && agentResult.getErrorMessage() != null) {
            resultData.put("errorMessage", agentResult.getErrorMessage());
        }

        return AgentResult.builder()
            .taskId("citation_verifier_combined")
            .success(true) // Always return success at tasklet level to continue pipeline
            .resultData(resultData)
            .processingTime(Duration.ofMillis(System.currentTimeMillis()))
            .build();
    }
}

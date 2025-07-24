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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.service.agent.QualityCheckerAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for quality checking using QualityCheckerAgent.
 * Integrates with the multi-agent pipeline to perform comprehensive quality assessment
 * of generated content and analysis results.
 */
@Component
public class QualityCheckerTasklet extends BaseAgentTasklet {

    private final QualityCheckerAgent qualityCheckerAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QualityCheckerTasklet(QualityCheckerAgent qualityCheckerAgent) {
        this.qualityCheckerAgent = qualityCheckerAgent;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Starting quality checking for paper %s", paperId);

            // Get previous step results for quality assessment
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");
            validatePreviousStepResult(paperResult, "Paper processor");

            AgentResult summaryResult = getPreviousStepResult(chunkContext, "contentSummarizerResult");
            AgentResult conceptResult = getPreviousStepResult(chunkContext, "conceptExplainerResult");

            // Extract original content for comparison
            String originalContent = extractStringFromResult(paperResult, "textContent", "extractedText", "content", "text");
            if (originalContent == null || originalContent.trim().isEmpty()) {
                throw new RuntimeException("No original content available for quality checking");
            }

            LoggingUtil.info(LOG, "execute", "Extracted original content: %d characters", originalContent.length());

            // Perform quality checking on all generated content
            AgentResult qualityResult = performQualityChecking(paperId, originalContent, 
                summaryResult, conceptResult, userId);

            if (!qualityResult.isSuccess()) {
                LoggingUtil.warn(LOG, "execute", "Quality checking completed with issues: %s", 
                    qualityResult.getErrorMessage());
            }

            // Store result using base class method
            storeStepResult(chunkContext, "qualityCheckerResult", qualityResult);

            logProcessingComplete("QualityChecker", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "QualityChecker", "qualityCheckerResult", e);
            throw new RuntimeException("Quality checking failed: " + e.getMessage(), e);
        }
    }

    /**
     * Performs comprehensive quality checking on all generated content.
     */
    private AgentResult performQualityChecking(UUID paperId, String originalContent,
                                              AgentResult summaryResult, AgentResult conceptResult,
                                              UUID userId) {
        
        try {
            LoggingUtil.info(LOG, "performQualityChecking", 
                "Performing quality checks for paper %s", paperId);

            // Create agent task for quality checking
            AgentTask qualityTask = createQualityCheckTask(paperId, originalContent, 
                summaryResult, conceptResult, userId);

            // Execute quality checking using the agent
            CompletableFuture<AgentResult> future = qualityCheckerAgent.process(qualityTask);
            AgentResult agentResult = future.get(); // Wait for completion

            if (agentResult.isSuccess()) {
                LoggingUtil.info(LOG, "performQualityChecking", 
                    "Quality checking completed successfully for paper %s", paperId);
            } else {
                LoggingUtil.warn(LOG, "performQualityChecking", 
                    "Quality checking found issues for paper %s: %s", 
                    paperId, agentResult.getErrorMessage());
            }

            return createQualityCheckResult(paperId, agentResult);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "performQualityChecking", 
                "Quality checking failed for paper %s", e, paperId);
            
            return AgentResult.builder()
                .taskId("quality_checker_error")
                .success(false)
                .errorMessage("Quality checking failed: " + e.getMessage())
                .processingTime(Duration.ofMillis(System.currentTimeMillis()))
                .build();
        }
    }

    /**
     * Creates an agent task for quality checking.
     */
    private AgentTask createQualityCheckTask(UUID paperId, String originalContent,
                                            AgentResult summaryResult, AgentResult conceptResult,
                                            UUID userId) {
        
        String taskId = String.format("quality_checker_%d", System.currentTimeMillis());

        // Create input JSON with all content to be quality checked
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("originalContent", originalContent);
        input.put("operation", "quality_assessment");

        // Add summary content if available
        if (summaryResult != null && summaryResult.isSuccess() && summaryResult.getResultData() != null) {
            Map<String, Object> summaryData = summaryResult.getResultData();
            
            // Add different summary types
            if (summaryData.containsKey("briefSummary")) {
                input.put("briefSummary", summaryData.get("briefSummary").toString());
            }
            if (summaryData.containsKey("standardSummary")) {
                input.put("standardSummary", summaryData.get("standardSummary").toString());
            }
            if (summaryData.containsKey("detailedSummary")) {
                input.put("detailedSummary", summaryData.get("detailedSummary").toString());
            }
        }

        // Add concept explanation content if available
        if (conceptResult != null && conceptResult.isSuccess() && conceptResult.getResultData() != null) {
            Map<String, Object> conceptData = conceptResult.getResultData();
            
            if (conceptData.containsKey("explanationsByLevel")) {
                input.set("conceptExplanations", objectMapper.valueToTree(conceptData.get("explanationsByLevel")));
            }
        }

        return AgentTask.builder()
            .id(taskId)
            .agentId("quality-checker")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Creates a quality check result from the agent response.
     */
    private AgentResult createQualityCheckResult(UUID paperId, AgentResult agentResult) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("paperId", paperId.toString());
        resultData.put("timestamp", Instant.now().toString());
        resultData.put("operation", "quality_assessment_complete");

        if (agentResult.isSuccess() && agentResult.getResultData() != null) {
            // Extract quality assessment data
            Object qualityData = agentResult.getResultData();
            if (qualityData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> qualityMap = (Map<String, Object>) qualityData;
                
                // Store quality scores and assessments
                if (qualityMap.containsKey("overallQualityScore")) {
                    resultData.put("overallQualityScore", qualityMap.get("overallQualityScore"));
                }
                
                if (qualityMap.containsKey("accuracyScore")) {
                    resultData.put("accuracyScore", qualityMap.get("accuracyScore"));
                }
                
                if (qualityMap.containsKey("consistencyScore")) {
                    resultData.put("consistencyScore", qualityMap.get("consistencyScore"));
                }
                
                if (qualityMap.containsKey("completenessScore")) {
                    resultData.put("completenessScore", qualityMap.get("completenessScore"));
                }
                
                // Store quality issues if any
                if (qualityMap.containsKey("qualityIssues")) {
                    resultData.put("qualityIssues", qualityMap.get("qualityIssues"));
                }
                
                // Store recommendations
                if (qualityMap.containsKey("recommendations")) {
                    resultData.put("recommendations", qualityMap.get("recommendations"));
                }
                
                // Store quality grade
                if (qualityMap.containsKey("qualityGrade")) {
                    resultData.put("qualityGrade", qualityMap.get("qualityGrade"));
                }
            }
            
            resultData.put("status", "completed");
            resultData.put("success", true);
            
            return AgentResult.builder()
                .taskId("quality_checker_combined")
                .success(true)
                .resultData(resultData)
                .processingTime(Duration.ofMillis(System.currentTimeMillis()))
                .build();
                
        } else {
            // Quality checking failed or found major issues
            resultData.put("status", "failed");
            resultData.put("success", false);
            resultData.put("error", agentResult.getErrorMessage());
            
            return AgentResult.builder()
                .taskId("quality_checker_combined")
                .success(false)
                .resultData(resultData)
                .errorMessage(agentResult.getErrorMessage())
                .processingTime(Duration.ofMillis(System.currentTimeMillis()))
                .build();
        }
    }
}

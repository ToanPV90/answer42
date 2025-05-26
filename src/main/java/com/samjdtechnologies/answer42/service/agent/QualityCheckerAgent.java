package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.QualityCheckType;
import com.samjdtechnologies.answer42.model.quality.QualityCheckResult;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.samjdtechnologies.answer42.util.QualityResponseParser;

/**
 * Quality Checker Agent using Anthropic Claude for accuracy verification and hallucination detection.
 * Performs comprehensive quality checks including accuracy, consistency, bias detection, and more.
 */
@Component
public class QualityCheckerAgent extends AnthropicBasedAgent {
    
    private static final int MAX_CONTENT_LENGTH = 8000; // Claude context limit consideration
    private final QualityResponseParser responseParser;
    
    public QualityCheckerAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                              AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter,
                              QualityResponseParser responseParser) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        this.responseParser = responseParser;
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.QUALITY_CHECKER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", "Starting quality check for task %s", task.getId());
        
        try {
            String generatedContent = responseParser.extractGeneratedContent(task);
            String sourceContent = responseParser.extractSourceContent(task);
            
            if (generatedContent == null || generatedContent.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), "No generated content provided for quality checking");
            }
            
            // Perform multiple quality checks in parallel
            List<CompletableFuture<QualityCheckResult>> checkFutures = createQualityCheckTasks(
                generatedContent, sourceContent);
            
            // Wait for all checks to complete
            CompletableFuture.allOf(checkFutures.toArray(new CompletableFuture[0])).join();
            
            // Collect results
            List<QualityCheckResult> results = checkFutures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            // Generate overall quality assessment
            QualityCheckResult overallAssessment = generateOverallAssessment(results, generatedContent);
            
            // Create comprehensive quality report
            Map<String, Object> qualityReport = createQualityReport(results, overallAssessment);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Quality check completed for task %s with overall score: %.2f", 
                task.getId(), overallAssessment.getScore());
            
            return AgentResult.success(task.getId(), qualityReport);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Quality check failed for task %s: %s", e, task.getId(), e.getMessage());
            return AgentResult.failure(task.getId(), "Quality check failed: " + e.getMessage());
        }
    }
    
    /**
     * Create parallel quality check tasks.
     */
    private List<CompletableFuture<QualityCheckResult>> createQualityCheckTasks(
            String generatedContent, String sourceContent) {
        
        List<CompletableFuture<QualityCheckResult>> futures = new ArrayList<>();
        
        // Accuracy verification (if source content available)
        if (sourceContent != null && !sourceContent.trim().isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> 
                verifyAccuracy(generatedContent, sourceContent), taskExecutor));
        }
        
        // Consistency checking
        futures.add(CompletableFuture.supplyAsync(() -> 
            checkConsistency(generatedContent), taskExecutor));
        
        // Bias detection
        futures.add(CompletableFuture.supplyAsync(() -> 
            detectBias(generatedContent), taskExecutor));
        
        // Hallucination detection
        if (sourceContent != null && !sourceContent.trim().isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> 
                detectHallucinations(generatedContent, sourceContent), taskExecutor));
        }
        
        // Logical coherence
        futures.add(CompletableFuture.supplyAsync(() -> 
            checkLogicalCoherence(generatedContent), taskExecutor));
        
        return futures;
    }
    
    /**
     * Verify accuracy against source material.
     */
    private QualityCheckResult verifyAccuracy(String generatedContent, String sourceContent) {
        try {
            String truncatedGenerated = truncateContent(generatedContent);
            String truncatedSource = truncateContent(sourceContent);
            
            Prompt accuracyPrompt = new Prompt(String.format("""
                Verify the accuracy of the generated content against the source material.
                
                SOURCE MATERIAL:
                %s
                
                GENERATED CONTENT:
                %s
                
                Analyze the following aspects:
                1. Factual accuracy - are all claims supported by the source?
                2. Numerical accuracy - are statistics and figures correct?
                3. Contextual accuracy - is information presented in proper context?
                4. Completeness - are important details missing or oversimplified?
                
                Provide your assessment as JSON:
                {
                    "score": 0.85,
                    "issues": [
                        {
                            "type": "FACTUAL_ERROR",
                            "description": "Specific issue description",
                            "location": "Where in content",
                            "confidence": 0.9
                        }
                    ],
                    "summary": "Overall accuracy assessment"
                }
                """, truncatedSource, truncatedGenerated));
            
            ChatClient.ChatClientRequestSpec response = chatClient.prompt(accuracyPrompt);
            String result = response.call().content();
            
            return responseParser.parseQualityCheckResponse(QualityCheckType.ACCURACY, result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyAccuracy", "Accuracy check failed: %s", e, e.getMessage());
            return QualityCheckResult.failure(QualityCheckType.ACCURACY, e.getMessage());
        }
    }
    
    /**
     * Check internal consistency of content.
     */
    private QualityCheckResult checkConsistency(String content) {
        try {
            String truncatedContent = truncateContent(content);
            
            Prompt consistencyPrompt = new Prompt(String.format("""
                Analyze the internal consistency of this content.
                
                CONTENT:
                %s
                
                Check for:
                1. Internal contradictions within the content
                2. Inconsistent use of terminology or concepts
                3. Logical flow and coherence
                4. Consistent voice and style
                
                Provide assessment as JSON with score, issues array, and summary.
                """, truncatedContent));
            
            ChatClient.ChatClientRequestSpec response = chatClient.prompt(consistencyPrompt);
            String result = response.call().content();
            
            return responseParser.parseQualityCheckResponse(QualityCheckType.CONSISTENCY, result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "checkConsistency", "Consistency check failed: %s", e, e.getMessage());
            return QualityCheckResult.failure(QualityCheckType.CONSISTENCY, e.getMessage());
        }
    }
    
    /**
     * Detect bias in content.
     */
    private QualityCheckResult detectBias(String content) {
        try {
            String truncatedContent = truncateContent(content);
            
            Prompt biasPrompt = new Prompt(String.format("""
                Analyze this content for potential bias.
                
                CONTENT:
                %s
                
                Look for:
                1. Gender bias or stereotypes
                2. Cultural or ethnic bias
                3. Confirmation bias (selective evidence)
                4. Language bias or exclusionary terms
                
                Provide assessment as JSON with score, issues array, and summary.
                """, truncatedContent));
            
            ChatClient.ChatClientRequestSpec response = chatClient.prompt(biasPrompt);
            String result = response.call().content();
            
            return responseParser.parseQualityCheckResponse(QualityCheckType.BIAS_DETECTION, result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "detectBias", "Bias detection failed: %s", e, e.getMessage());
            return QualityCheckResult.failure(QualityCheckType.BIAS_DETECTION, e.getMessage());
        }
    }
    
    /**
     * Detect hallucinations by comparing with source.
     */
    private QualityCheckResult detectHallucinations(String generatedContent, String sourceContent) {
        try {
            String truncatedGenerated = truncateContent(generatedContent);
            String truncatedSource = truncateContent(sourceContent);
            
            Prompt hallucinationPrompt = new Prompt(String.format("""
                Detect potential hallucinations in the generated content by comparing with source.
                
                SOURCE:
                %s
                
                GENERATED:
                %s
                
                Look for:
                1. Facts not supported by the source
                2. Made-up statistics or data
                3. Fabricated quotes or attributions
                4. Non-existent references
                
                Provide assessment as JSON with score, issues array, and summary.
                """, truncatedSource, truncatedGenerated));
            
            ChatClient.ChatClientRequestSpec response = chatClient.prompt(hallucinationPrompt);
            String result = response.call().content();
            
            return responseParser.parseQualityCheckResponse(QualityCheckType.HALLUCINATION_DETECTION, result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "detectHallucinations", "Hallucination detection failed: %s", e, e.getMessage());
            return QualityCheckResult.failure(QualityCheckType.HALLUCINATION_DETECTION, e.getMessage());
        }
    }
    
    /**
     * Check logical coherence of content.
     */
    private QualityCheckResult checkLogicalCoherence(String content) {
        try {
            String truncatedContent = truncateContent(content);
            
            Prompt coherencePrompt = new Prompt(String.format("""
                Analyze the logical coherence of this content.
                
                CONTENT:
                %s
                
                Evaluate:
                1. Logical flow of arguments
                2. Validity of reasoning
                3. Clear cause-and-effect relationships
                4. Appropriate use of evidence
                
                Provide assessment as JSON with score, issues array, and summary.
                """, truncatedContent));
            
            ChatClient.ChatClientRequestSpec response = chatClient.prompt(coherencePrompt);
            String result = response.call().content();
            
            return responseParser.parseQualityCheckResponse(QualityCheckType.LOGICAL_COHERENCE, result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "checkLogicalCoherence", "Coherence check failed: %s", e, e.getMessage());
            return QualityCheckResult.failure(QualityCheckType.LOGICAL_COHERENCE, e.getMessage());
        }
    }
    
    /**
     * Generate overall quality assessment from individual checks.
     */
    private QualityCheckResult generateOverallAssessment(List<QualityCheckResult> results, String content) {
        if (results.isEmpty()) {
            return QualityCheckResult.failure(QualityCheckType.OVERALL_QUALITY, "No quality checks completed");
        }
        
        // Calculate weighted average score
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (QualityCheckResult result : results) {
            if (result.isPassed()) {
                double weight = result.getCheckType().getWeight();
                totalScore += result.getScore() * weight;
                totalWeight += weight;
            }
        }
        
        double overallScore = totalWeight > 0 ? totalScore / totalWeight : 0.0;
        
        // Collect all issues
        List<com.samjdtechnologies.answer42.model.quality.QualityIssue> allIssues = results.stream()
            .filter(result -> result.getIssues() != null)
            .flatMap(result -> result.getIssues().stream())
            .collect(Collectors.toList());
        
        return QualityCheckResult.withIssues(
            QualityCheckType.OVERALL_QUALITY,
            overallScore,
            allIssues,
            generateRecommendations(allIssues)
        );
    }
    
    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > MAX_CONTENT_LENGTH ? 
            content.substring(0, MAX_CONTENT_LENGTH) + "..." : content;
    }
    
    private List<String> generateRecommendations(List<com.samjdtechnologies.answer42.model.quality.QualityIssue> issues) {
        return issues.stream()
            .filter(com.samjdtechnologies.answer42.model.quality.QualityIssue::isActionable)
            .map(issue -> "Address " + issue.getType().getDisplayName() + ": " + issue.getShortDescription())
            .limit(5)
            .collect(Collectors.toList());
    }
    
    private Map<String, Object> createQualityReport(List<QualityCheckResult> results, QualityCheckResult overall) {
        Map<String, Object> report = new HashMap<>();
        report.put("overallScore", overall.getScore());
        report.put("overallGrade", overall.getGrade());
        report.put("passed", overall.isPassed());
        report.put("checkResults", results);
        report.put("totalIssues", overall.getIssues() != null ? overall.getIssues().size() : 0);
        report.put("criticalIssues", overall.hasCriticalIssues());
        report.put("recommendations", overall.getRecommendations());
        report.put("timestamp", System.currentTimeMillis());
        return report;
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        String content = responseParser.extractGeneratedContent(task);
        int contentLength = content != null ? content.length() : 1000;
        
        long baseSeconds = 90;
        long contentSeconds = Math.min(contentLength / 2000, 180);
        long checkSeconds = 75; // 5 checks * 15 seconds each
        
        return Duration.ofSeconds(baseSeconds + contentSeconds + checkSeconds);
    }
}

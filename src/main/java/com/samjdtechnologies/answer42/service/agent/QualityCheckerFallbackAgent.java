package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Ollama-based fallback agent for quality checking.
 * This agent provides local processing capabilities when cloud providers are unavailable.
 * 
 * Optimized for local Ollama models with simplified quality checking:
 * - Basic quality metrics suitable for local model processing
 * - Simplified validation focused on core quality indicators
 * - Fallback-specific error handling and user notifications
 * - Content truncation to prevent resource exhaustion
 * 
 * Provides essential quality checking functionality with local processing constraints.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class QualityCheckerFallbackAgent extends OllamaBasedAgent {
    
    // Quality check types optimized for local models
    private static final Map<String, String> LOCAL_QUALITY_CHECKS = Map.of(
        "basic", "Basic content validation and structure check",
        "standard", "Standard quality metrics and completeness check",
        "detailed", "Detailed quality analysis with recommendations",
        "comprehensive", "Comprehensive quality assessment with local constraints"
    );
    
    // Quality thresholds for local processing
    private static final Map<String, Integer> QUALITY_THRESHOLDS = Map.of(
        "minimumWordCount", 100,
        "maximumWordCount", 50000,
        "minimumSections", 2,
        "maximumSections", 20
    );
    
    public QualityCheckerFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                      APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, rateLimiter);
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.QUALITY_CHECKER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing quality check with Ollama fallback for task %s", task.getId());
        
        try {
            // Extract task input
            JsonNode input = task.getInput();
            String itemId = input.get("itemId").asText();
            String content = input.has("content") ? input.get("content").asText() : "";
            String checkType = input.has("checkType") ? 
                input.get("checkType").asText() : "standard";
            
            if (content.isEmpty()) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: No content provided for quality checking");
            }
            
            // Validate check type
            if (!LOCAL_QUALITY_CHECKS.containsKey(checkType.toLowerCase())) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Unknown check type %s for fallback, defaulting to standard", checkType);
                checkType = "standard";
            }
            
            // Validate content is suitable for local processing
            if (!isContentSuitableForQualityCheck(content)) {
                return AgentResult.failure(task.getId(), 
                    "FALLBACK: Content not suitable for local quality checking");
            }
            
            // Perform quality check using local model
            Map<String, Object> qualityResults = performLocalQualityCheck(
                itemId, content, checkType);
            
            // Add fallback processing note
            String fallbackNote = createFallbackProcessingNote("Quality Check");
            qualityResults.put("processingNote", fallbackNote);
            
            // Create result data with fallback indicators
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("itemId", itemId);
            resultData.put("checkType", checkType);
            resultData.put("qualityResults", qualityResults);
            resultData.put("fallbackUsed", true);
            resultData.put("fallbackProvider", "OLLAMA");
            resultData.put("primaryFailureReason", "Cloud providers temporarily unavailable");
            
            return AgentResult.success(task.getId(), resultData);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Ollama fallback processing failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), handleLocalProcessingError(e, task.getId()));
        }
    }
    
    /**
     * Performs local quality check using Ollama.
     * 
     * @param itemId The item ID for logging
     * @param content The content to check
     * @param checkType The type of quality check to perform
     * @return Quality check results
     */
    private Map<String, Object> performLocalQualityCheck(String itemId, String content, 
                                                        String checkType) {
        LoggingUtil.info(LOG, "performLocalQualityCheck", 
            "Performing %s quality check using Ollama for item %s", checkType, itemId);
        
        Map<String, Object> qualityResults = new HashMap<>();
        
        try {
            switch (checkType.toLowerCase()) {
                case "basic" -> {
                    qualityResults.putAll(performBasicQualityCheck(content));
                }
                case "detailed" -> {
                    qualityResults.putAll(performBasicQualityCheck(content));
                    qualityResults.putAll(performContentAnalysis(content));
                    qualityResults.putAll(generateQualityRecommendations(content));
                }
                case "comprehensive" -> {
                    qualityResults.putAll(performBasicQualityCheck(content));
                    qualityResults.putAll(performContentAnalysis(content));
                    qualityResults.putAll(generateQualityRecommendations(content));
                    qualityResults.putAll(performStructuralAnalysis(content));
                }
                default -> { // standard
                    qualityResults.putAll(performBasicQualityCheck(content));
                    qualityResults.putAll(performContentAnalysis(content));
                }
            }
            
            // Calculate overall quality score
            double overallScore = calculateOverallQualityScore(qualityResults);
            qualityResults.put("overallQualityScore", overallScore);
            qualityResults.put("qualityGrade", getQualityGrade(overallScore));
            
            // Add processing metadata
            qualityResults.put("checkQuality", "local_processing");
            qualityResults.put("processingMethod", "ollama_fallback");
            qualityResults.put("contentLength", content.length());
            qualityResults.put("truncated", content.length() > MAX_LOCAL_CONTENT_LENGTH);
            
            return qualityResults;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "performLocalQualityCheck", 
                "Local quality check failed for item %s", e, itemId);
            return createLocalFallbackQualityResults(checkType, e.getMessage());
        }
    }
    
    /**
     * Performs basic quality checks using simple metrics.
     */
    private Map<String, Object> performBasicQualityCheck(String content) {
        Map<String, Object> basicChecks = new HashMap<>();
        
        // Basic metrics
        String[] words = content.split("\\s+");
        String[] sentences = content.split("[.!?]+");
        String[] paragraphs = content.split("\\n\\s*\\n");
        
        int wordCount = words.length;
        int sentenceCount = sentences.length;
        int paragraphCount = paragraphs.length;
        
        basicChecks.put("wordCount", wordCount);
        basicChecks.put("sentenceCount", sentenceCount);
        basicChecks.put("paragraphCount", paragraphCount);
        
        // Word count validation
        boolean wordCountValid = wordCount >= QUALITY_THRESHOLDS.get("minimumWordCount") && 
                                wordCount <= QUALITY_THRESHOLDS.get("maximumWordCount");
        basicChecks.put("wordCountValid", wordCountValid);
        basicChecks.put("wordCountScore", wordCountValid ? 100 : Math.max(0, 100 - Math.abs(wordCount - 1000) / 10));
        
        // Structure validation
        boolean hasMinimumStructure = paragraphCount >= 3;
        basicChecks.put("hasMinimumStructure", hasMinimumStructure);
        basicChecks.put("structureScore", hasMinimumStructure ? 100 : 60);
        
        // Readability metrics
        double avgWordsPerSentence = sentenceCount > 0 ? (double) wordCount / sentenceCount : 0;
        basicChecks.put("averageWordsPerSentence", avgWordsPerSentence);
        basicChecks.put("readabilityScore", calculateReadabilityScore(avgWordsPerSentence));
        
        return basicChecks;
    }
    
    /**
     * Performs content analysis using local processing.
     */
    private Map<String, Object> performContentAnalysis(String content) {
        String processableContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH / 2);
        
        String promptText = String.format(
            "Analyze the quality of this content and identify any issues:\n\n%s\n\n" +
            "Please assess:\n" +
            "1. Clarity: Is the content clear and well-written?\n" +
            "2. Completeness: Does the content seem complete?\n" +
            "3. Coherence: Is the content well-organized and logical?\n" +
            "4. Issues: Any obvious problems or areas for improvement?\n\n" +
            "Provide brief answers for each point.",
            processableContent
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String analysisResponse = response.getResult().getOutput().getText();
            
            return parseContentAnalysis(analysisResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "performContentAnalysis", 
                "Content analysis failed, using fallback method: %s", e.getMessage());
            return performContentAnalysisFallback(content);
        }
    }
    
    /**
     * Generates quality recommendations using local processing.
     */
    private Map<String, Object> generateQualityRecommendations(String content) {
        String processableContent = truncateForLocalProcessing(content, MAX_LOCAL_CONTENT_LENGTH / 3);
        
        String promptText = String.format(
            "Review this content and suggest 3-5 specific improvements:\n\n%s\n\n" +
            "Focus on practical suggestions for:\n" +
            "- Clarity and readability\n" +
            "- Structure and organization\n" +
            "- Completeness and accuracy\n\n" +
            "List each recommendation on a separate line.",
            processableContent
        );
        
        Prompt prompt = createFallbackPrompt(promptText, Map.of());
        
        try {
            ChatResponse response = executePrompt(prompt);
            String recommendationsResponse = response.getResult().getOutput().getText();
            
            return parseQualityRecommendations(recommendationsResponse);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "generateQualityRecommendations", 
                "Quality recommendations failed, using fallback method: %s", e.getMessage());
            return generateQualityRecommendationsFallback();
        }
    }
    
    /**
     * Performs structural analysis of content.
     */
    private Map<String, Object> performStructuralAnalysis(String content) {
        Map<String, Object> structuralAnalysis = new HashMap<>();
        
        // Analyze section headers
        List<String> headers = extractHeaders(content);
        structuralAnalysis.put("headerCount", headers.size());
        structuralAnalysis.put("headers", headers);
        structuralAnalysis.put("hasProperStructure", headers.size() >= 3);
        
        // Analyze content distribution
        String[] sections = content.split("(?i)(introduction|conclusion|abstract|summary|references)");
        structuralAnalysis.put("sectionCount", sections.length);
        structuralAnalysis.put("averageSectionLength", sections.length > 0 ? content.length() / sections.length : 0);
        
        // Check for common academic structure
        boolean hasIntroduction = content.toLowerCase().contains("introduction");
        boolean hasConclusion = content.toLowerCase().contains("conclusion");
        boolean hasReferences = content.toLowerCase().contains("references") || content.toLowerCase().contains("bibliography");
        
        structuralAnalysis.put("hasIntroduction", hasIntroduction);
        structuralAnalysis.put("hasConclusion", hasConclusion);
        structuralAnalysis.put("hasReferences", hasReferences);
        
        int structureScore = (hasIntroduction ? 30 : 0) + (hasConclusion ? 30 : 0) + 
                           (hasReferences ? 20 : 0) + (headers.size() >= 3 ? 20 : 10);
        structuralAnalysis.put("structuralScore", Math.min(100, structureScore));
        
        return structuralAnalysis;
    }
    
    /**
     * Parses content analysis response from local model.
     */
    private Map<String, Object> parseContentAnalysis(String response) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String lowerResponse = response.toLowerCase();
            
            // Simple parsing based on keywords
            analysis.put("clarityScore", lowerResponse.contains("clear") || lowerResponse.contains("well-written") ? 85 : 70);
            analysis.put("completenessScore", lowerResponse.contains("complete") || lowerResponse.contains("comprehensive") ? 85 : 70);
            analysis.put("coherenceScore", lowerResponse.contains("logical") || lowerResponse.contains("organized") ? 85 : 70);
            
            // Extract any issues mentioned
            List<String> issues = new ArrayList<>();
            if (lowerResponse.contains("unclear") || lowerResponse.contains("confusing")) {
                issues.add("Content clarity needs improvement");
            }
            if (lowerResponse.contains("incomplete") || lowerResponse.contains("missing")) {
                issues.add("Content appears incomplete");
            }
            if (lowerResponse.contains("disorganized") || lowerResponse.contains("scattered")) {
                issues.add("Content organization needs improvement");
            }
            
            analysis.put("identifiedIssues", issues.isEmpty() ? List.of("No major issues identified") : issues);
            analysis.put("analysisText", response.trim());
        } else {
            analysis.put("clarityScore", 75);
            analysis.put("completenessScore", 75);
            analysis.put("coherenceScore", 75);
            analysis.put("identifiedIssues", List.of("Analysis requires detailed review"));
            analysis.put("analysisText", "Local analysis completed with basic metrics");
        }
        
        return analysis;
    }
    
    /**
     * Parses quality recommendations from model response.
     */
    private Map<String, Object> parseQualityRecommendations(String response) {
        Map<String, Object> recommendations = new HashMap<>();
        
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.trim().split("\\n");
            List<String> recommendationList = new ArrayList<>();
            
            for (String line : lines) {
                String recommendation = line.trim().replaceAll("^[-*•]\\s*", "");
                if (!recommendation.isEmpty() && recommendation.length() <= 200) {
                    recommendationList.add(recommendation);
                }
                if (recommendationList.size() >= 5) break; // Limit for local processing
            }
            
            recommendations.put("recommendations", recommendationList.isEmpty() ? 
                List.of("Review for clarity and completeness") : recommendationList);
            recommendations.put("recommendationCount", recommendationList.size());
        } else {
            recommendations.put("recommendations", List.of("Review for clarity and completeness", 
                                                         "Improve structure and organization", 
                                                         "Verify content accuracy"));
            recommendations.put("recommendationCount", 3);
        }
        
        return recommendations;
    }
    
    /**
     * Fallback content analysis using simple heuristics.
     */
    private Map<String, Object> performContentAnalysisFallback(String content) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Simple heuristic-based analysis
        int wordCount = content.split("\\s+").length;
        boolean hasVariedSentences = content.contains(",") && content.contains(";");
        boolean hasQuestions = content.contains("?");
        
        analysis.put("clarityScore", hasVariedSentences ? 80 : 70);
        analysis.put("completenessScore", wordCount > 500 ? 80 : 65);
        analysis.put("coherenceScore", content.contains("\n\n") ? 75 : 65);
        analysis.put("identifiedIssues", List.of("Basic quality check completed"));
        analysis.put("analysisText", "Fallback analysis using simple metrics");
        
        return analysis;
    }
    
    /**
     * Fallback quality recommendations.
     */
    private Map<String, Object> generateQualityRecommendationsFallback() {
        Map<String, Object> recommendations = new HashMap<>();
        
        recommendations.put("recommendations", List.of(
            "Review content for clarity and readability",
            "Ensure proper structure and organization",
            "Verify completeness and accuracy",
            "Check for grammatical errors",
            "Improve paragraph transitions"
        ));
        recommendations.put("recommendationCount", 5);
        
        return recommendations;
    }
    
    /**
     * Extracts headers from content using simple heuristics.
     */
    private List<String> extractHeaders(String content) {
        List<String> headers = new ArrayList<>();
        String[] lines = content.split("\\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Look for potential headers (short lines, often capitalized)
            if (trimmed.length() > 3 && trimmed.length() < 100 && 
                !trimmed.endsWith(".") && !trimmed.endsWith(",") &&
                (trimmed.equals(trimmed.toUpperCase()) || 
                 Character.isUpperCase(trimmed.charAt(0)))) {
                headers.add(trimmed);
                if (headers.size() >= 10) break; // Limit for processing
            }
        }
        
        return headers;
    }
    
    /**
     * Calculates readability score based on sentence length.
     */
    private double calculateReadabilityScore(double avgWordsPerSentence) {
        if (avgWordsPerSentence < 10) {
            return 90; // Very readable
        } else if (avgWordsPerSentence < 15) {
            return 85; // Good readability
        } else if (avgWordsPerSentence < 20) {
            return 75; // Moderate readability
        } else if (avgWordsPerSentence < 25) {
            return 65; // Challenging
        } else {
            return 50; // Difficult to read
        }
    }
    
    /**
     * Calculates overall quality score from individual metrics.
     */
    private double calculateOverallQualityScore(Map<String, Object> qualityResults) {
        double totalScore = 0;
        int scoreCount = 0;
        
        // Aggregate available scores
        if (qualityResults.containsKey("wordCountScore")) {
            totalScore += ((Number) qualityResults.get("wordCountScore")).doubleValue() * 0.15;
            scoreCount++;
        }
        if (qualityResults.containsKey("structureScore")) {
            totalScore += ((Number) qualityResults.get("structureScore")).doubleValue() * 0.20;
            scoreCount++;
        }
        if (qualityResults.containsKey("readabilityScore")) {
            totalScore += ((Number) qualityResults.get("readabilityScore")).doubleValue() * 0.20;
            scoreCount++;
        }
        if (qualityResults.containsKey("clarityScore")) {
            totalScore += ((Number) qualityResults.get("clarityScore")).doubleValue() * 0.15;
            scoreCount++;
        }
        if (qualityResults.containsKey("completenessScore")) {
            totalScore += ((Number) qualityResults.get("completenessScore")).doubleValue() * 0.15;
            scoreCount++;
        }
        if (qualityResults.containsKey("coherenceScore")) {
            totalScore += ((Number) qualityResults.get("coherenceScore")).doubleValue() * 0.15;
            scoreCount++;
        }
        
        return scoreCount > 0 ? totalScore : 75.0; // Default score if no metrics available
    }
    
    /**
     * Converts numeric quality score to letter grade.
     */
    private String getQualityGrade(double score) {
        if (score >= 90) return "A";
        else if (score >= 80) return "B";
        else if (score >= 70) return "C";
        else if (score >= 60) return "D";
        else return "F";
    }
    
    /**
     * Creates fallback quality results when processing fails.
     */
    private Map<String, Object> createLocalFallbackQualityResults(String checkType, String errorReason) {
        LoggingUtil.warn(LOG, "createLocalFallbackQualityResults", 
            "Creating fallback quality results due to: %s", errorReason);
        
        Map<String, Object> fallbackResults = new HashMap<>();
        fallbackResults.put("overallQualityScore", 70.0);
        fallbackResults.put("qualityGrade", "C");
        fallbackResults.put("wordCountValid", true);
        fallbackResults.put("hasMinimumStructure", true);
        fallbackResults.put("clarityScore", 70);
        fallbackResults.put("completenessScore", 70);
        fallbackResults.put("coherenceScore", 70);
        fallbackResults.put("recommendations", List.of("Content requires detailed quality review"));
        fallbackResults.put("identifiedIssues", List.of("Quality check incomplete - fallback processing used"));
        fallbackResults.put("checkQuality", "fallback_only");
        fallbackResults.put("processingMethod", "local_fallback");
        fallbackResults.put("processingNote", "Local fallback quality check - " + errorReason);
        
        return fallbackResults;
    }
    
    /**
     * Validates that content is suitable for quality checking.
     */
    private boolean isContentSuitableForQualityCheck(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check for minimum content length
        if (content.length() < 50) {
            LoggingUtil.warn(LOG, "isContentSuitableForQualityCheck", 
                "Content too short for quality checking: %d characters", content.length());
            return false;
        }
        
        // Check for extremely large content
        if (content.length() > MAX_LOCAL_CONTENT_LENGTH * 2) {
            LoggingUtil.warn(LOG, "isContentSuitableForQualityCheck", 
                "Content length %d may impact local processing quality", content.length());
            // Still allow processing with truncation
        }
        
        return true;
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input != null && input.has("checkType")) {
            String checkType = input.get("checkType").asText();
            
            // Local processing is generally faster but less sophisticated
            long baseSeconds = switch (checkType.toLowerCase()) {
                case "basic" -> 15;           // Fast for local
                case "detailed" -> 45;        // Moderate for local
                case "comprehensive" -> 75;   // Still reasonable for local
                default -> 30;                // Standard checking
            };
            
            return Duration.ofSeconds(baseSeconds);
        }
        
        return Duration.ofSeconds(30); // Conservative estimate for local processing
    }
    
    /**
     * Returns a description of this agent for logging and monitoring.
     */
    protected String getAgentDescription() {
        return "Ollama-based fallback agent for quality checking. " +
               "Provides local processing when cloud providers are unavailable. " +
               "Uses simplified analysis optimized for local models with basic quality assessment capabilities.";
    }
}

package com.samjdtechnologies.answer42.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.IssueType;
import com.samjdtechnologies.answer42.model.enums.QualityCheckType;
import com.samjdtechnologies.answer42.model.quality.QualityCheckResult;
import com.samjdtechnologies.answer42.model.quality.QualityIssue;

/**
 * Utility class for parsing quality check responses and extracting content from agent tasks.
 */
@Component
public class QualityResponseParser {
    
    private static final Logger LOG = LoggingUtil.getLogger(QualityResponseParser.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern SCORE_PATTERN = Pattern.compile("\"score\"\\s*:\\s*([0-9.]+)");
    
    /**
     * Extract generated content from agent task.
     */
    public String extractGeneratedContent(AgentTask task) {
        JsonNode input = task.getInput();
        
        // Try different possible field names for generated content
        if (input.has("generatedContent")) {
            return input.get("generatedContent").asText();
        }
        if (input.has("content")) {
            return input.get("content").asText();
        }
        if (input.has("text")) {
            return input.get("text").asText();
        }
        if (input.has("summary")) {
            return input.get("summary").asText();
        }
        if (input.has("result")) {
            JsonNode result = input.get("result");
            if (result.isTextual()) {
                return result.asText();
            }
            if (result.has("content")) {
                return result.get("content").asText();
            }
        }
        
        LoggingUtil.warn(LOG, "extractGeneratedContent", 
            "No generated content found in task input");
        
        return null;
    }
    
    /**
     * Extract source content from agent task.
     */
    public String extractSourceContent(AgentTask task) {
        JsonNode input = task.getInput();
        
        // Try different possible field names for source content
        if (input.has("sourceContent")) {
            return input.get("sourceContent").asText();
        }
        if (input.has("originalContent")) {
            return input.get("originalContent").asText();
        }
        if (input.has("paperContent")) {
            return input.get("paperContent").asText();
        }
        if (input.has("document")) {
            JsonNode doc = input.get("document");
            if (doc.isTextual()) {
                return doc.asText();
            }
            if (doc.has("content")) {
                return doc.get("content").asText();
            }
            if (doc.has("textContent")) {
                return doc.get("textContent").asText();
            }
        }
        if (input.has("paper")) {
            JsonNode paper = input.get("paper");
            if (paper.has("textContent")) {
                return paper.get("textContent").asText();
            }
            if (paper.has("content")) {
                return paper.get("content").asText();
            }
        }
        
        LoggingUtil.debug(LOG, "extractSourceContent", 
            "No source content found in task input");
        
        return null;
    }
    
    /**
     * Parse AI response into QualityCheckResult.
     */
    public QualityCheckResult parseQualityCheckResponse(QualityCheckType checkType, String response) {
        try {
            // First try to extract JSON from response
            String jsonString = extractJsonFromResponse(response);
            
            if (jsonString != null) {
                return parseJsonResponse(checkType, jsonString);
            }
            
            // Fallback to pattern-based extraction
            return parsePatternBasedResponse(checkType, response);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseQualityCheckResponse", 
                "Failed to parse response for %s: %s", e, checkType, e.getMessage());
            return QualityCheckResult.failure(checkType, "Response parsing failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract JSON string from AI response.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        // Look for JSON pattern in the response
        Matcher matcher = JSON_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }
        
        // Try to find JSON-like content between markers
        String[] jsonMarkers = {"```json", "```", "{", "}"};
        int startIndex = -1;
        int endIndex = -1;
        
        for (String marker : jsonMarkers) {
            int index = response.indexOf(marker);
            if (index != -1) {
                if (marker.equals("{")) {
                    startIndex = index;
                }
                if (marker.equals("}")) {
                    endIndex = index + 1;
                    break;
                }
            }
        }
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex);
        }
        
        return null;
    }
    
    /**
     * Parse JSON response into QualityCheckResult.
     */
    private QualityCheckResult parseJsonResponse(QualityCheckType checkType, String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            
            double score = extractScoreFromJson(jsonNode);
            List<QualityIssue> issues = extractIssuesFromJson(jsonNode);
            String summary = extractSummaryFromJson(jsonNode);
            
            return QualityCheckResult.withIssues(
                checkType, 
                score, 
                issues, 
                generateRecommendationsFromIssues(issues)
            );
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseJsonResponse", 
                "JSON parsing failed: %s", e, e.getMessage());
            throw new RuntimeException("JSON parsing failed", e);
        }
    }
    
    /**
     * Parse response using pattern matching fallback.
     */
    private QualityCheckResult parsePatternBasedResponse(QualityCheckType checkType, String response) {
        double score = extractScoreFromText(response);
        List<QualityIssue> issues = extractIssuesFromText(response);
        String summary = extractSummaryFromText(response);
        
        return QualityCheckResult.withIssues(
            checkType, 
            score, 
            issues, 
            generateRecommendationsFromIssues(issues)
        );
    }
    
    /**
     * Extract score from JSON node.
     */
    private double extractScoreFromJson(JsonNode jsonNode) {
        if (jsonNode.has("score")) {
            return jsonNode.get("score").asDouble();
        }
        if (jsonNode.has("rating")) {
            return jsonNode.get("rating").asDouble();
        }
        if (jsonNode.has("quality_score")) {
            return jsonNode.get("quality_score").asDouble();
        }
        
        return 0.5; // Default moderate score
    }
    
    /**
     * Extract score from text using pattern matching.
     */
    public double extractScoreFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.5;
        }
        
        // Try to find score pattern
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                // Normalize score to 0-1 range
                if (score > 1.0) {
                    score = score / 100.0; // Assume percentage
                }
                return Math.max(0.0, Math.min(1.0, score));
            } catch (NumberFormatException e) {
                LoggingUtil.warn(LOG, "extractScoreFromText", 
                    "Failed to parse score: %s", matcher.group(1));
            }
        }
        
        // Look for qualitative indicators
        String lowerText = text.toLowerCase();
        if (lowerText.contains("excellent") || lowerText.contains("outstanding")) {
            return 0.95;
        }
        if (lowerText.contains("good") || lowerText.contains("high quality")) {
            return 0.85;
        }
        if (lowerText.contains("acceptable") || lowerText.contains("adequate")) {
            return 0.75;
        }
        if (lowerText.contains("poor") || lowerText.contains("low quality")) {
            return 0.45;
        }
        if (lowerText.contains("unacceptable") || lowerText.contains("failed")) {
            return 0.25;
        }
        
        return 0.5; // Default
    }
    
    /**
     * Extract issues from JSON node.
     */
    public List<QualityIssue> extractIssuesFromJson(JsonNode jsonNode) {
        List<QualityIssue> issues = new ArrayList<>();
        
        if (jsonNode.has("issues") && jsonNode.get("issues").isArray()) {
            for (JsonNode issueNode : jsonNode.get("issues")) {
                QualityIssue issue = parseIssueFromJson(issueNode);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }
        
        return issues;
    }
    
    /**
     * Extract issues from text using pattern matching.
     */
    public List<QualityIssue> extractIssuesFromText(String text) {
        List<QualityIssue> issues = new ArrayList<>();
        
        // Look for common issue indicators
        String[] issuePatterns = {
            "error", "mistake", "incorrect", "inaccurate", "inconsistent",
            "contradictory", "biased", "unclear", "missing", "fabricated"
        };
        
        String lowerText = text.toLowerCase();
        for (String pattern : issuePatterns) {
            if (lowerText.contains(pattern)) {
                // Create a generic issue
                IssueType type = mapPatternToIssueType(pattern);
                QualityIssue issue = QualityIssue.fromAIDetection(
                    type,
                    "Quality issue detected: " + pattern,
                    "Content analysis",
                    0.7,
                    "PatternMatcher"
                );
                issues.add(issue);
            }
        }
        
        return issues;
    }
    
    /**
     * Parse individual issue from JSON node.
     */
    private QualityIssue parseIssueFromJson(JsonNode issueNode) {
        try {
            String typeString = issueNode.has("type") ? issueNode.get("type").asText() : "UNCLEAR_LANGUAGE";
            String description = issueNode.has("description") ? issueNode.get("description").asText() : "Quality issue detected";
            String location = issueNode.has("location") ? issueNode.get("location").asText() : "Unknown";
            double confidence = issueNode.has("confidence") ? issueNode.get("confidence").asDouble() : 0.7;
            
            IssueType type = parseIssueType(typeString);
            
            return QualityIssue.fromAIDetection(type, description, location, confidence, "AI Analysis");
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "parseIssueFromJson", 
                "Failed to parse issue: %s", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract summary from JSON node.
     */
    public String extractSummaryFromJson(JsonNode jsonNode) {
        if (jsonNode.has("summary")) {
            return jsonNode.get("summary").asText();
        }
        if (jsonNode.has("assessment")) {
            return jsonNode.get("assessment").asText();
        }
        if (jsonNode.has("conclusion")) {
            return jsonNode.get("conclusion").asText();
        }
        
        return "Quality check completed";
    }
    
    /**
     * Extract summary from text.
     */
    public String extractSummaryFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Quality check completed";
        }
        
        // Look for summary section
        String[] summaryMarkers = {"summary:", "assessment:", "conclusion:", "overall:"};
        String lowerText = text.toLowerCase();
        
        for (String marker : summaryMarkers) {
            int index = lowerText.indexOf(marker);
            if (index != -1) {
                String remaining = text.substring(index + marker.length()).trim();
                // Take first sentence or paragraph
                int endIndex = Math.min(remaining.indexOf('.') + 1, 200);
                if (endIndex > 0) {
                    return remaining.substring(0, endIndex).trim();
                }
            }
        }
        
        // Take first 200 characters as summary
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
    
    /**
     * Map pattern string to IssueType enum.
     */
    private IssueType mapPatternToIssueType(String pattern) {
        return switch (pattern.toLowerCase()) {
            case "error", "mistake", "incorrect" -> IssueType.FACTUAL_ERROR;
            case "inaccurate" -> IssueType.STATISTICAL_ERROR;
            case "inconsistent" -> IssueType.INTERNAL_CONTRADICTION;
            case "contradictory" -> IssueType.INTERNAL_CONTRADICTION;
            case "biased" -> IssueType.CONFIRMATION_BIAS;
            case "unclear" -> IssueType.UNCLEAR_LANGUAGE;
            case "missing" -> IssueType.MISSING_CONTEXT;
            case "fabricated" -> IssueType.FABRICATED_FACT;
            default -> IssueType.UNCLEAR_LANGUAGE;
        };
    }
    
    /**
     * Parse IssueType from string.
     */
    private IssueType parseIssueType(String typeString) {
        try {
            return IssueType.valueOf(typeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            LoggingUtil.warn(LOG, "parseIssueType", 
                "Unknown issue type: %s, defaulting to UNCLEAR_LANGUAGE", typeString);
            return IssueType.UNCLEAR_LANGUAGE;
        }
    }
    
    /**
     * Generate recommendations from issues.
     */
    private List<String> generateRecommendationsFromIssues(List<QualityIssue> issues) {
        return issues.stream()
            .filter(QualityIssue::isActionable)
            .map(issue -> "Address " + issue.getType().getDisplayName() + ": " + issue.getShortDescription())
            .limit(5)
            .toList();
    }
}

package com.samjdtechnologies.answer42.model.quality;

import com.samjdtechnologies.answer42.model.enums.IssueSeverity;
import com.samjdtechnologies.answer42.model.enums.IssueType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a specific quality issue found during content analysis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityIssue {
    
    private String issueId;
    private IssueSeverity severity;
    private IssueType type;
    private String description;
    private String location; // Where in the content the issue was found
    private String originalText;
    private String suggestedFix;
    private double confidence; // 0.0 to 1.0
    private String source; // What check detected this issue
    
    /**
     * Create a critical quality issue.
     */
    public static QualityIssue critical(IssueType type, String description, String location) {
        return QualityIssue.builder()
            .issueId(generateIssueId())
            .severity(IssueSeverity.CRITICAL)
            .type(type)
            .description(description)
            .location(location)
            .confidence(0.9)
            .build();
    }
    
    /**
     * Create a major quality issue.
     */
    public static QualityIssue major(IssueType type, String description, String location) {
        return QualityIssue.builder()
            .issueId(generateIssueId())
            .severity(IssueSeverity.MAJOR)
            .type(type)
            .description(description)
            .location(location)
            .confidence(0.8)
            .build();
    }
    
    /**
     * Create a minor quality issue.
     */
    public static QualityIssue minor(IssueType type, String description, String location) {
        return QualityIssue.builder()
            .issueId(generateIssueId())
            .severity(IssueSeverity.MINOR)
            .type(type)
            .description(description)
            .location(location)
            .confidence(0.7)
            .build();
    }
    
    /**
     * Create a quality issue with original text and suggested fix.
     */
    public static QualityIssue withFix(IssueSeverity severity, IssueType type, 
                                     String description, String originalText, String suggestedFix) {
        return QualityIssue.builder()
            .issueId(generateIssueId())
            .severity(severity)
            .type(type)
            .description(description)
            .originalText(originalText)
            .suggestedFix(suggestedFix)
            .confidence(0.8)
            .build();
    }
    
    /**
     * Create a quality issue from AI detection.
     */
    public static QualityIssue fromAIDetection(IssueType type, String description, 
                                              String location, double confidence, String source) {
        return QualityIssue.builder()
            .issueId(generateIssueId())
            .severity(type.getDefaultSeverity())
            .type(type)
            .description(description)
            .location(location)
            .confidence(confidence)
            .source(source)
            .build();
    }
    
    /**
     * Generate a unique issue ID.
     */
    private static String generateIssueId() {
        return "QI_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    /**
     * Check if this issue requires immediate attention.
     */
    public boolean requiresImmediateAttention() {
        return severity == IssueSeverity.CRITICAL || 
               (severity == IssueSeverity.MAJOR && confidence >= 0.9);
    }
    
    /**
     * Get display text for the issue.
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity.getDisplayName()).append("] ");
        sb.append(type.getDisplayName()).append(": ");
        sb.append(description);
        
        if (location != null && !location.trim().isEmpty()) {
            sb.append(" (Location: ").append(location).append(")");
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this issue is actionable (has a suggested fix).
     */
    public boolean isActionable() {
        return suggestedFix != null && !suggestedFix.trim().isEmpty();
    }
    
    /**
     * Get the risk level of this issue.
     */
    public String getRiskLevel() {
        return switch (severity) {
            case CRITICAL -> "HIGH";
            case MAJOR -> "MEDIUM";
            case MINOR -> "LOW";
            case INFO -> "NONE";
        };
    }
    
    /**
     * Check if this issue affects publication readiness.
     */
    public boolean affectsPublicationReadiness() {
        return severity.blocksPublication() || type.affectsCredibility();
    }
    
    /**
     * Get a shortened description for UI display.
     */
    public String getShortDescription() {
        if (description == null || description.length() <= 100) {
            return description;
        }
        return description.substring(0, 97) + "...";
    }
    
    /**
     * Get confidence level as a percentage string.
     */
    public String getConfidencePercentage() {
        return String.format("%.0f%%", confidence * 100);
    }
    
    /**
     * Get the priority score for sorting issues.
     */
    public int getPriorityScore() {
        int severityScore = severity.getPriority() * 100;
        int confidenceScore = (int) (confidence * 10);
        int typeScore = type.isCriticalForAccuracy() ? 10 : 0;
        
        return severityScore + confidenceScore + typeScore;
    }
    
    /**
     * Get CSS class for styling this issue in UI.
     */
    public String getCssClass() {
        return "quality-issue quality-issue-" + severity.name().toLowerCase();
    }
}

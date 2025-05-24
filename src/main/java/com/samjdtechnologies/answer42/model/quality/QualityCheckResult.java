package com.samjdtechnologies.answer42.model.quality;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.samjdtechnologies.answer42.model.enums.IssueSeverity;
import com.samjdtechnologies.answer42.model.enums.QualityCheckType;
import com.samjdtechnologies.answer42.model.enums.QualityGrade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a quality check operation by the QualityCheckerAgent.
 * Represents findings from accuracy, consistency, bias, and hallucination checks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityCheckResult {
    
    private QualityCheckType checkType;
    private double score; // 0.0 to 1.0
    private QualityGrade grade;
    private String summary;
    private List<QualityIssue> issues;
    private List<String> recommendations;
    private Map<String, Object> metadata;
    private Instant checkedAt;
    private boolean passed;
    private String source;
    
    /**
     * Create a successful quality check result.
     */
    public static QualityCheckResult success(QualityCheckType type, double score, String summary) {
        return QualityCheckResult.builder()
            .checkType(type)
            .score(score)
            .grade(determineGrade(score))
            .summary(summary)
            .passed(score >= 0.7) // 70% threshold for passing
            .checkedAt(Instant.now())
            .build();
    }
    
    /**
     * Create a failed quality check result.
     */
    public static QualityCheckResult failure(QualityCheckType type, String error) {
        return QualityCheckResult.builder()
            .checkType(type)
            .score(0.0)
            .grade(QualityGrade.F)
            .summary("Quality check failed: " + error)
            .passed(false)
            .checkedAt(Instant.now())
            .build();
    }
    
    /**
     * Create a quality check result with issues.
     */
    public static QualityCheckResult withIssues(QualityCheckType type, double score, 
                                               List<QualityIssue> issues, List<String> recommendations) {
        return QualityCheckResult.builder()
            .checkType(type)
            .score(score)
            .grade(determineGrade(score))
            .issues(issues)
            .recommendations(recommendations)
            .passed(score >= 0.7 && issues.stream().noneMatch(i -> i.getSeverity() == IssueSeverity.CRITICAL))
            .checkedAt(Instant.now())
            .build();
    }
    
    /**
     * Determine quality grade based on score.
     */
    private static QualityGrade determineGrade(double score) {
        if (score >= 0.95) return QualityGrade.A_PLUS;
        if (score >= 0.90) return QualityGrade.A;
        if (score >= 0.85) return QualityGrade.A_MINUS;
        if (score >= 0.80) return QualityGrade.B_PLUS;
        if (score >= 0.75) return QualityGrade.B;
        if (score >= 0.70) return QualityGrade.B_MINUS;
        if (score >= 0.65) return QualityGrade.C_PLUS;
        if (score >= 0.60) return QualityGrade.C;
        if (score >= 0.55) return QualityGrade.C_MINUS;
        if (score >= 0.50) return QualityGrade.D;
        return QualityGrade.F;
    }
    
    /**
     * Check if this result has critical issues.
     */
    public boolean hasCriticalIssues() {
        return issues != null && issues.stream()
            .anyMatch(issue -> issue.getSeverity() == IssueSeverity.CRITICAL);
    }
    
    /**
     * Get count of issues by severity.
     */
    public long getIssueCountBySeverity(IssueSeverity severity) {
        if (issues == null) return 0;
        return issues.stream()
            .filter(issue -> issue.getSeverity() == severity)
            .count();
    }
    
    /**
     * Add metadata to the result.
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Check if the quality check result is acceptable for publication.
     */
    public boolean isAcceptableForPublication() {
        return passed && score >= 0.8 && !hasCriticalIssues();
    }
}

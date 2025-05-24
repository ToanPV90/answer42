package com.samjdtechnologies.answer42.model.enums;

/**
 * Severity levels for quality issues found during content analysis.
 */
public enum IssueSeverity {
    
    /**
     * Critical issues that must be addressed immediately.
     * These prevent content from being acceptable for any use.
     */
    CRITICAL("Critical", "Must be fixed immediately", "#dc3545", 4),
    
    /**
     * Major issues that significantly impact quality.
     * These should be addressed before publication.
     */
    MAJOR("Major", "Should be fixed before publication", "#fd7e14", 3),
    
    /**
     * Minor issues that affect quality but don't prevent use.
     * These can be addressed in subsequent revisions.
     */
    MINOR("Minor", "Can be addressed in revisions", "#ffc107", 2),
    
    /**
     * Informational issues or suggestions for improvement.
     * These are optional enhancements.
     */
    INFO("Info", "Optional improvement suggestions", "#17a2b8", 1);
    
    private final String displayName;
    private final String description;
    private final String colorCode;
    private final int priority; // Higher number = higher priority
    
    IssueSeverity(String displayName, String description, String colorCode, int priority) {
        this.displayName = displayName;
        this.description = description;
        this.colorCode = colorCode;
        this.priority = priority;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    public int getPriority() {
        return priority;
    }
    
    /**
     * Check if this severity level blocks publication.
     */
    public boolean blocksPublication() {
        return this == CRITICAL || this == MAJOR;
    }
    
    /**
     * Check if this severity level requires immediate attention.
     */
    public boolean requiresImmediateAttention() {
        return this == CRITICAL;
    }
    
    /**
     * Check if this severity level affects quality score.
     */
    public boolean affectsQualityScore() {
        return this != INFO;
    }
    
    /**
     * Get the weight of this severity in quality scoring.
     */
    public double getQualityWeight() {
        return switch (this) {
            case CRITICAL -> -0.30; // Major negative impact
            case MAJOR -> -0.15; // Moderate negative impact
            case MINOR -> -0.05; // Small negative impact
            case INFO -> 0.0; // No impact
        };
    }
    
    /**
     * Get icon name for UI display.
     */
    public String getIconName() {
        return switch (this) {
            case CRITICAL -> "exclamation-triangle";
            case MAJOR -> "exclamation-circle";
            case MINOR -> "info-circle";
            case INFO -> "lightbulb";
        };
    }
    
    /**
     * Compare severity levels by priority.
     */
    public boolean isMoreSevereThan(IssueSeverity other) {
        return this.priority > other.priority;
    }
    
    /**
     * Compare severity levels by priority.
     */
    public boolean isLessSevereThan(IssueSeverity other) {
        return this.priority < other.priority;
    }
    
    /**
     * Get display text with icon and name.
     */
    public String getDisplayText() {
        return getIconName() + " " + displayName;
    }
}

package com.samjdtechnologies.answer42.model.enums;

/**
 * Types of quality checks performed by the QualityCheckerAgent.
 */
public enum QualityCheckType {
    
    /**
     * Accuracy verification against source material.
     */
    ACCURACY("Accuracy Check", "Verifies factual accuracy against source documents"),
    
    /**
     * Internal consistency checking.
     */
    CONSISTENCY("Consistency Check", "Checks for internal contradictions and logical consistency"),
    
    /**
     * Bias detection and analysis.
     */
    BIAS_DETECTION("Bias Detection", "Identifies potential bias in content and language"),
    
    /**
     * Hallucination detection.
     */
    HALLUCINATION_DETECTION("Hallucination Detection", "Detects AI-generated content that isn't supported by sources"),
    
    /**
     * Logical coherence assessment.
     */
    LOGICAL_COHERENCE("Logical Coherence", "Evaluates logical flow and reasoning quality"),
    
    /**
     * Citation accuracy verification.
     */
    CITATION_ACCURACY("Citation Accuracy", "Verifies accuracy of citations and references"),
    
    /**
     * Completeness assessment.
     */
    COMPLETENESS("Completeness Check", "Assesses whether important information is missing"),
    
    /**
     * Overall quality assessment.
     */
    OVERALL_QUALITY("Overall Quality", "Comprehensive quality assessment across all dimensions");
    
    private final String displayName;
    private final String description;
    
    QualityCheckType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this is a critical quality check type.
     */
    public boolean isCritical() {
        return this == ACCURACY || this == HALLUCINATION_DETECTION || this == CITATION_ACCURACY;
    }
    
    /**
     * Get the weight of this check type in overall quality scoring.
     */
    public double getWeight() {
        return switch (this) {
            case ACCURACY -> 0.25;
            case HALLUCINATION_DETECTION -> 0.20;
            case CONSISTENCY -> 0.15;
            case LOGICAL_COHERENCE -> 0.15;
            case CITATION_ACCURACY -> 0.10;
            case BIAS_DETECTION -> 0.08;
            case COMPLETENESS -> 0.07;
            case OVERALL_QUALITY -> 1.0;
        };
    }
}

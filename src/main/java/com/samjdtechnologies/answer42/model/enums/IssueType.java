package com.samjdtechnologies.answer42.model.enums;

/**
 * Types of quality issues that can be detected during content analysis.
 */
public enum IssueType {
    
    // Accuracy Issues
    FACTUAL_ERROR("Factual Error", "Incorrect factual information", QualityCheckType.ACCURACY),
    STATISTICAL_ERROR("Statistical Error", "Incorrect numerical data or statistics", QualityCheckType.ACCURACY),
    ATTRIBUTION_ERROR("Attribution Error", "Incorrect attribution of quotes or claims", QualityCheckType.ACCURACY),
    TEMPORAL_ERROR("Temporal Error", "Incorrect dates or time sequences", QualityCheckType.ACCURACY),
    
    // Consistency Issues
    INTERNAL_CONTRADICTION("Internal Contradiction", "Contradictory statements within content", QualityCheckType.CONSISTENCY),
    TERMINOLOGY_INCONSISTENCY("Terminology Inconsistency", "Inconsistent use of terms or concepts", QualityCheckType.CONSISTENCY),
    STYLE_INCONSISTENCY("Style Inconsistency", "Inconsistent writing style or formatting", QualityCheckType.CONSISTENCY),
    VOICE_INCONSISTENCY("Voice Inconsistency", "Inconsistent narrative voice or perspective", QualityCheckType.CONSISTENCY),
    
    // Bias Issues
    GENDER_BIAS("Gender Bias", "Biased language or assumptions based on gender", QualityCheckType.BIAS_DETECTION),
    CULTURAL_BIAS("Cultural Bias", "Biased assumptions about cultures or ethnicities", QualityCheckType.BIAS_DETECTION),
    CONFIRMATION_BIAS("Confirmation Bias", "Selective presentation of supporting evidence", QualityCheckType.BIAS_DETECTION),
    LANGUAGE_BIAS("Language Bias", "Biased or exclusionary language", QualityCheckType.BIAS_DETECTION),
    
    // Hallucination Issues
    FABRICATED_FACT("Fabricated Fact", "Made-up facts not supported by sources", QualityCheckType.HALLUCINATION_DETECTION),
    FABRICATED_QUOTE("Fabricated Quote", "Made-up quotes or statements", QualityCheckType.HALLUCINATION_DETECTION),
    FABRICATED_REFERENCE("Fabricated Reference", "Non-existent citations or references", QualityCheckType.HALLUCINATION_DETECTION),
    FABRICATED_DATA("Fabricated Data", "Made-up statistical or numerical data", QualityCheckType.HALLUCINATION_DETECTION),
    
    // Logical Coherence Issues
    NON_SEQUITUR("Non Sequitur", "Conclusion doesn't follow from premises", QualityCheckType.LOGICAL_COHERENCE),
    CIRCULAR_REASONING("Circular Reasoning", "Circular or self-referential logic", QualityCheckType.LOGICAL_COHERENCE),
    FALSE_DICHOTOMY("False Dichotomy", "Oversimplified either/or reasoning", QualityCheckType.LOGICAL_COHERENCE),
    LOGICAL_FALLACY("Logical Fallacy", "Other logical reasoning errors", QualityCheckType.LOGICAL_COHERENCE),
    
    // Citation Issues
    MISSING_CITATION("Missing Citation", "Claims that need but lack citations", QualityCheckType.CITATION_ACCURACY),
    INCORRECT_CITATION("Incorrect Citation", "Citations with wrong information", QualityCheckType.CITATION_ACCURACY),
    INACCESSIBLE_SOURCE("Inaccessible Source", "Citations to unavailable sources", QualityCheckType.CITATION_ACCURACY),
    OUTDATED_SOURCE("Outdated Source", "Citations to outdated or superseded sources", QualityCheckType.CITATION_ACCURACY),
    
    // Completeness Issues
    MISSING_CONTEXT("Missing Context", "Important contextual information missing", QualityCheckType.COMPLETENESS),
    INCOMPLETE_ANALYSIS("Incomplete Analysis", "Analysis lacking important aspects", QualityCheckType.COMPLETENESS),
    MISSING_METHODOLOGY("Missing Methodology", "Insufficient methodological details", QualityCheckType.COMPLETENESS),
    MISSING_LIMITATIONS("Missing Limitations", "Failure to discuss limitations", QualityCheckType.COMPLETENESS),
    
    // General Quality Issues
    UNCLEAR_LANGUAGE("Unclear Language", "Ambiguous or confusing language", QualityCheckType.OVERALL_QUALITY),
    POOR_ORGANIZATION("Poor Organization", "Poor structure or organization", QualityCheckType.OVERALL_QUALITY),
    INSUFFICIENT_DETAIL("Insufficient Detail", "Lacks necessary detail or explanation", QualityCheckType.OVERALL_QUALITY),
    FORMATTING_ERROR("Formatting Error", "Formatting or presentation issues", QualityCheckType.OVERALL_QUALITY);
    
    private final String displayName;
    private final String description;
    private final QualityCheckType category;
    
    IssueType(String displayName, String description, QualityCheckType category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public QualityCheckType getCategory() {
        return category;
    }
    
    /**
     * Check if this issue type is critical for accuracy.
     */
    public boolean isCriticalForAccuracy() {
        return this == FACTUAL_ERROR || this == FABRICATED_FACT || 
               this == FABRICATED_QUOTE || this == FABRICATED_DATA;
    }
    
    /**
     * Check if this issue type affects credibility.
     */
    public boolean affectsCredibility() {
        return category == QualityCheckType.ACCURACY || 
               category == QualityCheckType.HALLUCINATION_DETECTION ||
               category == QualityCheckType.CITATION_ACCURACY;
    }
    
    /**
     * Get the default severity for this issue type.
     */
    public IssueSeverity getDefaultSeverity() {
        return switch (this) {
            case FACTUAL_ERROR, FABRICATED_FACT, FABRICATED_QUOTE, FABRICATED_DATA -> IssueSeverity.CRITICAL;
            case STATISTICAL_ERROR, ATTRIBUTION_ERROR, INTERNAL_CONTRADICTION, 
                 CONFIRMATION_BIAS, FABRICATED_REFERENCE, INCORRECT_CITATION -> IssueSeverity.MAJOR;
            case TEMPORAL_ERROR, TERMINOLOGY_INCONSISTENCY, GENDER_BIAS, CULTURAL_BIAS,
                 NON_SEQUITUR, MISSING_CITATION, MISSING_CONTEXT -> IssueSeverity.MINOR;
            default -> IssueSeverity.INFO;
        };
    }
    
    /**
     * Get icon name for UI display.
     */
    public String getIconName() {
        return switch (category) {
            case ACCURACY -> "shield-exclamation";
            case CONSISTENCY -> "arrows-alt";
            case BIAS_DETECTION -> "balance-scale";
            case HALLUCINATION_DETECTION -> "eye-slash";
            case LOGICAL_COHERENCE -> "sitemap";
            case CITATION_ACCURACY -> "quote-right";
            case COMPLETENESS -> "puzzle-piece";
            case OVERALL_QUALITY -> "star";
        };
    }
}

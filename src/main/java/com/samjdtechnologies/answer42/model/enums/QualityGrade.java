package com.samjdtechnologies.answer42.model.enums;

/**
 * Quality grades assigned to content based on assessment scores.
 */
public enum QualityGrade {
    
    A_PLUS("A+", "Exceptional Quality", "Outstanding content with no significant issues", 95, 100),
    A("A", "Excellent Quality", "High-quality content with minimal minor issues", 90, 94),
    A_MINUS("A-", "Very Good Quality", "Very good content with few minor issues", 85, 89),
    B_PLUS("B+", "Good Quality", "Good content with some minor issues", 80, 84),
    B("B", "Acceptable Quality", "Acceptable content with moderate issues", 75, 79),
    B_MINUS("B-", "Fair Quality", "Fair content requiring some improvements", 70, 74),
    C_PLUS("C+", "Below Average", "Below average content with notable issues", 65, 69),
    C("C", "Poor Quality", "Poor content requiring significant improvements", 60, 64),
    C_MINUS("C-", "Very Poor", "Very poor content with major issues", 55, 59),
    D("D", "Unacceptable", "Unacceptable content with severe issues", 50, 54),
    F("F", "Failed", "Failed quality check with critical issues", 0, 49);
    
    private final String symbol;
    private final String displayName;
    private final String description;
    private final int minScore;
    private final int maxScore;
    
    QualityGrade(String symbol, String displayName, String description, int minScore, int maxScore) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getMinScore() {
        return minScore;
    }
    
    public int getMaxScore() {
        return maxScore;
    }
    
    /**
     * Check if this grade indicates passing quality.
     */
    public boolean isPassing() {
        return this.ordinal() <= B_MINUS.ordinal(); // B- and above
    }
    
    /**
     * Check if this grade indicates acceptable quality for publication.
     */
    public boolean isAcceptableForPublication() {
        return this.ordinal() <= B.ordinal(); // B and above
    }
    
    /**
     * Check if this grade indicates excellent quality.
     */
    public boolean isExcellent() {
        return this.ordinal() <= A.ordinal(); // A and above
    }
    
    /**
     * Check if this grade requires immediate attention.
     */
    public boolean requiresImmediateAttention() {
        return this.ordinal() >= C.ordinal(); // C and below
    }
    
    /**
     * Get the numeric midpoint of this grade's score range.
     */
    public double getMidpointScore() {
        return (minScore + maxScore) / 2.0;
    }
    
    /**
     * Get a color code for UI display (CSS color names).
     */
    public String getColorCode() {
        return switch (this) {
            case A_PLUS, A -> "#28a745"; // Green
            case A_MINUS, B_PLUS -> "#6f42c1"; // Purple
            case B, B_MINUS -> "#007bff"; // Blue
            case C_PLUS, C -> "#fd7e14"; // Orange
            case C_MINUS, D -> "#dc3545"; // Red
            case F -> "#6c757d"; // Gray
        };
    }
    
    /**
     * Get display text with symbol and name.
     */
    public String getDisplayText() {
        return symbol + " - " + displayName;
    }
    
    /**
     * Get grade from score percentage.
     */
    public static QualityGrade fromScore(double scorePercentage) {
        int score = (int) Math.round(scorePercentage * 100);
        
        for (QualityGrade grade : QualityGrade.values()) {
            if (score >= grade.minScore && score <= grade.maxScore) {
                return grade;
            }
        }
        
        return F; // Default to F if score is somehow out of range
    }
}

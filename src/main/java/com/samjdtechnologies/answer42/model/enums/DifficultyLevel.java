package com.samjdtechnologies.answer42.model.enums;

/**
 * Difficulty levels for concept explanations.
 */
public enum DifficultyLevel {
    EASY("Easy", 1, "Simple concept that's easy to understand"),
    MEDIUM("Medium", 2, "Moderately complex concept requiring some background"),
    HARD("Hard", 3, "Complex concept requiring significant background knowledge"),
    EXPERT("Expert", 4, "Very complex concept requiring expert-level understanding");
    
    private final String displayName;
    private final int level;
    private final String description;
    
    DifficultyLevel(String displayName, int level, String description) {
        this.displayName = displayName;
        this.level = level;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isHarderThan(DifficultyLevel other) {
        return this.level > other.level;
    }
    
    public static DifficultyLevel fromLevel(int level) {
        for (DifficultyLevel difficulty : values()) {
            if (difficulty.level == level) {
                return difficulty;
            }
        }
        return MEDIUM; // Default fallback
    }
}

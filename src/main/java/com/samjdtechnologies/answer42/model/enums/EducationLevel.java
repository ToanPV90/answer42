package com.samjdtechnologies.answer42.model.enums;

/**
 * Education levels for concept explanations.
 * Determines the complexity and depth of explanations.
 */
public enum EducationLevel {
    HIGH_SCHOOL("high_school", "High School", 1, "Simple explanations with basic analogies"),
    UNDERGRADUATE("undergraduate", "Undergraduate", 2, "Clear explanations with some technical detail"),
    GRADUATE("graduate", "Graduate", 3, "Detailed explanations with advanced concepts"),
    EXPERT("expert", "Expert", 4, "Comprehensive explanations with full technical depth");
    
    private final String name;
    private final String displayName;
    private final int level;
    private final String description;
    
    EducationLevel(String name, String displayName, int level, String description) {
        this.name = name;
        this.displayName = displayName;
        this.level = level;
        this.description = description;
    }
    
    public String getName() { 
        return name; 
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
    
    public boolean isMoreAdvancedThan(EducationLevel other) {
        return this.level > other.level;
    }
    
    public static EducationLevel fromString(String name) {
        for (EducationLevel level : values()) {
            if (level.name.equalsIgnoreCase(name) || level.displayName.equalsIgnoreCase(name)) {
                return level;
            }
        }
        return UNDERGRADUATE; // Default fallback
    }
}

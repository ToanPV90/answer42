package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of supported citation styles.
 */
public enum CitationStyle {
    
    APA("APA", "American Psychological Association", "apa", 1.0),
    MLA("MLA", "Modern Language Association", "mla", 1.0),
    CHICAGO("Chicago", "Chicago Manual of Style", "chicago", 1.0),
    IEEE("IEEE", "Institute of Electrical and Electronics Engineers", "ieee", 1.0),
    HARVARD("Harvard", "Harvard Reference System", "harvard", 0.9),
    VANCOUVER("Vancouver", "Vancouver Reference Style", "vancouver", 0.8),
    ASA("ASA", "American Sociological Association", "asa", 0.7),
    TURABIAN("Turabian", "Turabian Style", "turabian", 0.7);
    
    private final String displayName;
    private final String fullName;
    private final String code;
    private final double supportLevel;
    
    CitationStyle(String displayName, String fullName, String code, double supportLevel) {
        this.displayName = displayName;
        this.fullName = fullName;
        this.code = code;
        this.supportLevel = supportLevel;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public String getCode() {
        return code;
    }
    
    public double getSupportLevel() {
        return supportLevel;
    }
    
    /**
     * Check if this style is fully supported.
     */
    public boolean isFullySupported() {
        return supportLevel >= 1.0;
    }
    
    /**
     * Get citation style by code.
     */
    public static CitationStyle fromCode(String code) {
        for (CitationStyle style : values()) {
            if (style.code.equalsIgnoreCase(code)) {
                return style;
            }
        }
        return APA; // Default fallback
    }
    
    /**
     * Get all fully supported styles.
     */
    public static CitationStyle[] getFullySupported() {
        return new CitationStyle[]{APA, MLA, CHICAGO, IEEE};
    }
    
    /**
     * Check if a style name is valid.
     */
    public static boolean isValidStyle(String styleName) {
        try {
            valueOf(styleName.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

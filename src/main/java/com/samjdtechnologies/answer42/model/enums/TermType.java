package com.samjdtechnologies.answer42.model.enums;

/**
 * Types of technical terms identified in academic content.
 */
public enum TermType {
    CONCEPT("Conceptual term", "General technical or scientific concept"),
    ACRONYM("Acronym", "Abbreviated term or acronym"),
    MATHEMATICAL("Mathematical term", "Mathematical or statistical concept"),
    DOMAIN_SPECIFIC("Domain-specific term", "Specialized terminology for specific field"),
    METHODOLOGY("Methodology term", "Research method or approach"),
    TOOL("Tool or technique", "Research tool, software, or technique");
    
    private final String displayName;
    private final String description;
    
    TermType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}

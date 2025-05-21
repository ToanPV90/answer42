package com.samjdtechnologies.answer42.model.enums;

/**
 * Enum representing the different chat modes available in the application.
 */
public enum ChatMode {
    /**
     * Standard chat mode for interacting with a specific paper.
     */
    CHAT("chat"),
    
    /**
     * Cross-reference mode for comparing multiple papers.
     */
    CROSS_REFERENCE("cross_reference"),
    
    /**
     * Research explorer mode for conducting research with Perplexity.
     */
    RESEARCH_EXPLORER("research_explorer");
    
    private final String value;
    
    ChatMode(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get a ChatMode enum from its string value.
     * 
     * @param value The string value to convert
     * @return The corresponding ChatMode, or CHAT if not found
     */
    public static ChatMode fromValue(String value) {
        for (ChatMode mode : ChatMode.values()) {
            if (mode.getValue().equals(value)) {
                return mode;
            }
        }
        return CHAT;  // Default to CHAT mode
    }
}

package com.samjdtechnologies.answer42.model.enums;

/**
 * Enum representing the different AI providers available in the application.
 */
public enum AIProvider {
    /**
     * OpenAI provider (ChatGPT).
     */
    OPENAI("openai"),
    
    /**
     * Anthropic provider (Claude).
     */
    ANTHROPIC("anthropic"),
    
    /**
     * Perplexity provider.
     */
    PERPLEXITY("perplexity");
    
    private final String value;
    
    AIProvider(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get an AIProvider enum from its string value.
     * 
     * @param value The string value to convert
     * @return The corresponding AIProvider, or OPENAI if not found
     */
    public static AIProvider fromValue(String value) {
        for (AIProvider provider : AIProvider.values()) {
            if (provider.getValue().equals(value)) {
                return provider;
            }
        }
        return OPENAI;  // Default to OPENAI
    }
    
    /**
     * Get the recommended AI provider for a given chat mode.
     * 
     * @param mode The chat mode
     * @return The recommended AI provider for the mode
     */
    public static AIProvider getRecommendedForMode(ChatMode mode) {
        switch (mode) {
            case CHAT:
                return ANTHROPIC;
            case CROSS_REFERENCE:
                return OPENAI;
            case RESEARCH_EXPLORER:
                return PERPLEXITY;
            default:
                return OPENAI;
        }
    }
}

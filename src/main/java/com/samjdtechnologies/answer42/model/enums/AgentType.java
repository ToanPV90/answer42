package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of different agent types in the multi-agent pipeline.
 * Each agent type corresponds to a specific processing capability.
 */
public enum AgentType {
    PAPER_PROCESSOR("paper-processor", "Paper Processor"),
    METADATA_ENHANCER("metadata-enhancer", "Metadata Enhancer"),
    CONTENT_SUMMARIZER("content-summarizer", "Content Summarizer"),
    CONCEPT_EXPLAINER("concept-explainer", "Concept Explainer"),
    QUALITY_CHECKER("quality-checker", "Quality Checker"),
    CITATION_FORMATTER("citation-formatter", "Citation Formatter"),
    CITATION_VERIFIER("citation-verifier", "Citation Verifier"),
    PERPLEXITY_RESEARCHER("perplexity-researcher", "Perplexity Researcher"),
    RELATED_PAPER_DISCOVERY("related-paper-discovery", "Related Paper Discovery");

    private final String agentId;
    private final String displayName;

    AgentType(String agentId, String displayName) {
        this.agentId = agentId;
        this.displayName = displayName;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get AgentType from agent ID string.
     */
    public static AgentType fromAgentId(String agentId) {
        for (AgentType type : values()) {
            if (type.getAgentId().equals(agentId)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown agent ID: " + agentId);
    }
}

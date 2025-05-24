package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of different sources for paper discovery.
 * Each source provides different types of discovery capabilities.
 */
public enum DiscoverySource {
    CROSSREF("crossref", "Crossref API", "Citation network and bibliographic discovery"),
    SEMANTIC_SCHOLAR("semantic-scholar", "Semantic Scholar", "Semantic similarity and influence analysis"),
    PERPLEXITY("perplexity", "Perplexity API", "Real-time trends and open access discovery"),
    ARXIV("arxiv", "arXiv", "Preprint repository discovery"),
    PUBMED("pubmed", "PubMed", "Biomedical literature discovery"),
    AI_SYNTHESIS("ai-synthesis", "AI Synthesis", "AI-powered combined results");

    private final String sourceId;
    private final String displayName;
    private final String description;

    DiscoverySource(String sourceId, String displayName, String description) {
        this.sourceId = sourceId;
        this.displayName = displayName;
        this.description = description;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get DiscoverySource from source ID string.
     */
    public static DiscoverySource fromSourceId(String sourceId) {
        for (DiscoverySource source : values()) {
            if (source.getSourceId().equals(sourceId)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown source ID: " + sourceId);
    }

    /**
     * Checks if this source supports citation network discovery.
     */
    public boolean supportsCitationNetworks() {
        return this == CROSSREF || this == SEMANTIC_SCHOLAR;
    }

    /**
     * Checks if this source supports semantic similarity discovery.
     */
    public boolean supportsSemanticSimilarity() {
        return this == SEMANTIC_SCHOLAR || this == AI_SYNTHESIS;
    }

    /**
     * Checks if this source supports real-time trend analysis.
     */
    public boolean supportsRealTimeTrends() {
        return this == PERPLEXITY;
    }

    /**
     * Gets the reliability score for this source (0.0 to 1.0).
     */
    public double getReliabilityScore() {
        return switch (this) {
            case CROSSREF -> 0.95; // Highly reliable bibliographic data
            case SEMANTIC_SCHOLAR -> 0.90; // Very reliable academic data
            case PUBMED -> 0.95; // Highly reliable biomedical data
            case ARXIV -> 0.80; // Reliable but preprints
            case PERPLEXITY -> 0.75; // Good for trends but less precise
            case AI_SYNTHESIS -> 0.85; // Depends on source combination
        };
    }
}

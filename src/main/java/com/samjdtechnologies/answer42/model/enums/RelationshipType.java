package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of different types of relationships between papers.
 * Defines how discovered papers relate to the source paper.
 */
public enum RelationshipType {
    CITES("cites", "Cites This Paper", "Papers that cite the source paper"),
    CITED_BY("cited-by", "Cited By This Paper", "Papers cited by the source paper"),
    SEMANTIC_SIMILARITY("semantic-similarity", "Semantically Similar", "Papers with similar content/methodology"),
    AUTHOR_NETWORK("author-network", "Same Author(s)", "Papers by the same authors"),
    VENUE_SIMILARITY("venue-similarity", "Same Venue", "Papers published in the same venue"),
    KEYWORD_OVERLAP("keyword-overlap", "Keyword Overlap", "Papers sharing significant keywords"),
    FIELD_RELATED("field-related", "Related Field", "Papers in related research fields"),
    METHODOLOGICAL("methodological", "Similar Methodology", "Papers using similar methods"),
    DATASET_RELATED("dataset-related", "Related Dataset", "Papers using related datasets"),
    TEMPORAL_PROXIMITY("temporal-proximity", "Temporal Proximity", "Papers published around the same time"),
    CO_CITATION("co-citation", "Co-Citation", "Papers frequently cited together"),
    BIBLIOGRAPHIC_COUPLING("bibliographic-coupling", "Bibliographic Coupling", "Papers sharing common references"),
    TRENDING("trending", "Currently Trending", "Papers currently receiving attention"),
    OPEN_ACCESS("open-access", "Open Access", "Freely available papers"),
    UNKNOWN("unknown", "Unknown Relationship", "Relationship type not determined");

    private final String relationshipId;
    private final String displayName;
    private final String description;

    RelationshipType(String relationshipId, String displayName, String description) {
        this.relationshipId = relationshipId;
        this.displayName = displayName;
        this.description = description;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get RelationshipType from relationship ID string.
     */
    public static RelationshipType fromRelationshipId(String relationshipId) {
        for (RelationshipType type : values()) {
            if (type.getRelationshipId().equals(relationshipId)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * Checks if this relationship type indicates a citation relationship.
     */
    public boolean isCitationRelationship() {
        return this == CITES || this == CITED_BY || this == CO_CITATION || this == BIBLIOGRAPHIC_COUPLING;
    }

    /**
     * Checks if this relationship type indicates semantic similarity.
     */
    public boolean isSemanticRelationship() {
        return this == SEMANTIC_SIMILARITY || this == METHODOLOGICAL || this == FIELD_RELATED;
    }

    /**
     * Checks if this relationship type indicates author/social relationships.
     */
    public boolean isAuthorRelationship() {
        return this == AUTHOR_NETWORK;
    }

    /**
     * Gets the importance score for this relationship type (0.0 to 1.0).
     */
    public double getImportanceScore() {
        return switch (this) {
            case CITES, CITED_BY -> 0.95; // Direct citation relationships are highly important
            case SEMANTIC_SIMILARITY -> 0.90; // Very important for research discovery
            case CO_CITATION, BIBLIOGRAPHIC_COUPLING -> 0.85; // Strong indirect relationships
            case METHODOLOGICAL -> 0.80; // Important for methodology comparison
            case AUTHOR_NETWORK -> 0.75; // Relevant for tracking author work
            case FIELD_RELATED -> 0.70; // Good for broader context
            case KEYWORD_OVERLAP -> 0.65; // Moderate relevance
            case VENUE_SIMILARITY -> 0.60; // Some relevance
            case DATASET_RELATED -> 0.75; // Important for reproducibility
            case TRENDING -> 0.70; // Important for current relevance
            case OPEN_ACCESS -> 0.50; // Utility rather than relevance
            case TEMPORAL_PROXIMITY -> 0.45; // Weak relationship
            case UNKNOWN -> 0.30; // Low confidence
        };
    }

    /**
     * Gets the display color for this relationship type in UI.
     */
    public String getDisplayColor() {
        return switch (this) {
            case CITES, CITED_BY -> "#1f77b4"; // Blue for citations
            case SEMANTIC_SIMILARITY -> "#ff7f0e"; // Orange for semantic
            case AUTHOR_NETWORK -> "#2ca02c"; // Green for authors
            case METHODOLOGICAL -> "#d62728"; // Red for methodology
            case TRENDING -> "#9467bd"; // Purple for trending
            case OPEN_ACCESS -> "#8c564b"; // Brown for access
            default -> "#7f7f7f"; // Gray for others
        };
    }
}

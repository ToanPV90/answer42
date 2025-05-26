package com.samjdtechnologies.answer42.model.enums;

/**
 * Types of processing stages in the multi-agent pipeline.
 */
public enum StageType {
    TEXT_EXTRACTION("Text Extraction", "Extract text content from PDF"),
    METADATA_ENHANCEMENT("Metadata Enhancement", "Enhance metadata from external sources"),
    CONTENT_ANALYSIS("Content Analysis", "Analyze document content"),
    CONCEPT_EXTRACTION("Concept Extraction", "Extract key concepts and terms"),
    CITATION_PROCESSING("Citation Processing", "Process and format citations"),
    RESEARCH_DISCOVERY("Research Discovery", "Discover related research and external knowledge"),
    PERPLEXITY_RESEARCH("Perplexity Research", "External fact verification and research using Perplexity"),
    QUALITY_CHECK("Quality Check", "Verify output quality");

    private final String displayName;
    private final String description;

    StageType(String displayName, String description) {
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

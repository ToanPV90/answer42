package com.samjdtechnologies.answer42.model.enums;

/**
 * Enumeration of pipeline execution statuses with comprehensive stage support.
 */
public enum PipelineStatus {
    // Initial states
    PENDING("Pending execution"),
    PENDING_CREDITS("Pending credits"),
    INITIALIZING("Initializing pipeline"),
    
    // Processing stages
    PROCESSING("Processing"),
    TEXT_EXTRACTION("Extracting text"),
    METADATA_ENHANCEMENT("Enhancing metadata"),
    CONTENT_SUMMARIZATION("Generating summaries"),
    CONCEPT_EXPLANATION("Explaining concepts"),
    QUALITY_CHECKING("Checking quality"),
    CITATION_FORMATTING("Formatting citations"),
    RESEARCH_DISCOVERY("Discovering research"),
    
    // Terminal states
    COMPLETED("Completed successfully"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String displayName;

    PipelineStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if the status represents a terminal state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Check if the status represents an active processing state.
     */
    public boolean isActive() {
        return this == PROCESSING || this == INITIALIZING ||
               this == TEXT_EXTRACTION || this == METADATA_ENHANCEMENT ||
               this == CONTENT_SUMMARIZATION || this == CONCEPT_EXPLANATION ||
               this == QUALITY_CHECKING || this == CITATION_FORMATTING ||
               this == RESEARCH_DISCOVERY;
    }

    /**
     * Check if the status represents a pending state.
     */
    public boolean isPending() {
        return this == PENDING || this == PENDING_CREDITS;
    }

    /**
     * Get the corresponding paper status for database storage.
     */
    public String getPaperStatus() {
        return switch (this) {
            case COMPLETED -> "COMPLETED";
            case FAILED -> "ERROR";
            case CANCELLED -> "CANCELLED";
            case PENDING, PENDING_CREDITS -> "UPLOADED";
            default -> "PROCESSING";
        };
    }

    /**
     * Parse status from string (for database compatibility).
     */
    public static PipelineStatus fromString(String status) {
        if (status == null) {
            return PENDING;
        }
        
        try {
            return PipelineStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle legacy status strings
            return switch (status.toLowerCase()) {
                case "pending execution" -> PENDING;
                case "pending credits" -> PENDING_CREDITS;
                case "initializing pipeline" -> INITIALIZING;
                case "extracting text" -> TEXT_EXTRACTION;
                case "enhancing metadata" -> METADATA_ENHANCEMENT;
                case "generating summaries" -> CONTENT_SUMMARIZATION;
                case "explaining concepts" -> CONCEPT_EXPLANATION;
                case "checking quality" -> QUALITY_CHECKING;
                case "formatting citations" -> CITATION_FORMATTING;
                case "discovering research" -> RESEARCH_DISCOVERY;
                case "completed successfully" -> COMPLETED;
                case "processing" -> PROCESSING;
                case "failed" -> FAILED;
                case "cancelled" -> CANCELLED;
                default -> PENDING;
            };
        }
    }

    /**
     * Get progress percentage for UI display.
     */
    public int getProgressPercentage() {
        return switch (this) {
            case PENDING -> 0;
            case PENDING_CREDITS -> 0;
            case INITIALIZING -> 5;
            case TEXT_EXTRACTION -> 15;
            case METADATA_ENHANCEMENT -> 30;
            case CONTENT_SUMMARIZATION -> 50;
            case CONCEPT_EXPLANATION -> 65;
            case QUALITY_CHECKING -> 80;
            case CITATION_FORMATTING -> 90;
            case RESEARCH_DISCOVERY -> 95;
            case PROCESSING -> 50; // Generic processing
            case COMPLETED -> 100;
            case FAILED, CANCELLED -> 0;
        };
    }

    /**
     * Get the next expected status in the pipeline.
     */
    public PipelineStatus getNextStatus() {
        return switch (this) {
            case PENDING -> INITIALIZING;
            case PENDING_CREDITS -> PENDING; // After credits are added
            case INITIALIZING -> TEXT_EXTRACTION;
            case TEXT_EXTRACTION -> METADATA_ENHANCEMENT;
            case METADATA_ENHANCEMENT -> CONTENT_SUMMARIZATION;
            case CONTENT_SUMMARIZATION -> CONCEPT_EXPLANATION;
            case CONCEPT_EXPLANATION -> QUALITY_CHECKING;
            case QUALITY_CHECKING -> CITATION_FORMATTING;
            case CITATION_FORMATTING -> COMPLETED;
            case RESEARCH_DISCOVERY -> COMPLETED;
            case PROCESSING -> COMPLETED; // Generic processing
            default -> this; // Terminal states stay the same
        };
    }
}

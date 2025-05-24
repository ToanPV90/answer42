package com.samjdtechnologies.answer42.model.enums;

/**
 * Status of individual stages in the pipeline.
 */
public enum StageStatus {
    PENDING("Pending", "Stage is waiting to start"),
    RUNNING("Running", "Stage is currently executing"),
    COMPLETED("Completed", "Stage completed successfully"),
    FAILED("Failed", "Stage failed with error"),
    SKIPPED("Skipped", "Stage was skipped due to configuration or dependency failure");

    private final String displayName;
    private final String description;

    StageStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this status indicates the stage is complete (either success or failure).
     */
    public boolean isComplete() {
        return this == COMPLETED || this == FAILED || this == SKIPPED;
    }

    /**
     * Check if this status indicates success.
     */
    public boolean isSuccess() {
        return this == COMPLETED;
    }
}

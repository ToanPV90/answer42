package com.samjdtechnologies.answer42.model.enums;

/**
 * Status of each file in the bulk upload.
 */
public enum FileStatus {
    PENDING("Pending"),
    PROCESSING("Processing"),
    SUCCESS("Uploaded successfully"),
    ERROR("Error during upload");
    
    private final String description;
    
    FileStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

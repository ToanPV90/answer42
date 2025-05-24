package com.samjdtechnologies.answer42.model.citation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a raw citation as extracted from a document before processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawCitation {
    
    private String text;
    private int position;
    private String context;
    private String documentSection;
    private double confidence;
    
    /**
     * Create a raw citation with basic information.
     */
    public static RawCitation of(String text, int position) {
        return RawCitation.builder()
            .text(text)
            .position(position)
            .confidence(1.0)
            .build();
    }
    
    /**
     * Create a raw citation with context information.
     */
    public static RawCitation withContext(String text, int position, String context, String section) {
        return RawCitation.builder()
            .text(text)
            .position(position)
            .context(context)
            .documentSection(section)
            .confidence(0.8)
            .build();
    }
    
    /**
     * Check if this citation appears to be valid.
     */
    public boolean isValid() {
        return text != null && !text.trim().isEmpty() && text.length() > 10;
    }
    
    /**
     * Get the cleaned text without extra whitespace.
     */
    public String getCleanedText() {
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }
}

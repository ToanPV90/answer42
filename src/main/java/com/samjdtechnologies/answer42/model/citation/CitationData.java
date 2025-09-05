package com.samjdtechnologies.answer42.model.citation;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data class for structured citation information used in citation verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationData {
    
    private String title;
    
    @Builder.Default
    private List<String> authors = new ArrayList<>();
    
    private Integer year;
    
    private String journal;
    
    private String doi;
    
    private String arxivId;
    
    private String aiAnalysis;
    
    @Builder.Default
    private double qualityScore = 0.5;
    
    /**
     * Checks if the citation data is valid for verification.
     * 
     * @return true if the citation has sufficient data for verification
     */
    public boolean isValid() {
        return title != null && !title.trim().isEmpty() && qualityScore > 0.2;
    }
}

package com.samjdtechnologies.answer42.model.concept;

import java.util.ArrayList;
import java.util.List;

import com.samjdtechnologies.answer42.model.enums.DifficultyLevel;
import com.samjdtechnologies.answer42.model.enums.EducationLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an explanation of a technical concept.
 * Contains definition, analogies, importance, and related information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptExplanation {
    private String term;
    private String definition;
    private String analogy;
    private String importance;
    @Builder.Default
    private List<String> relatedConcepts = new ArrayList<>();
    @Builder.Default
    private List<String> prerequisites = new ArrayList<>();
    @Builder.Default
    private List<String> commonMisconceptions = new ArrayList<>();
    private DifficultyLevel difficulty;
    private EducationLevel targetLevel;
    @Builder.Default
    private double confidence = 0.0;
    @Builder.Default
    private String source = "AI Generated";
    
    /**
     * Check if this explanation has high confidence.
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * Check if this explanation includes helpful analogies.
     */
    public boolean hasAnalogy() {
        return analogy != null && !analogy.trim().isEmpty();
    }
    
    /**
     * Check if this explanation identifies prerequisites.
     */
    public boolean hasPrerequisites() {
        return prerequisites != null && !prerequisites.isEmpty();
    }
    
    /**
     * Get a summary of the explanation quality.
     */
    public String getQualitySummary() {
        StringBuilder summary = new StringBuilder();
        
        if (isHighConfidence()) {
            summary.append("High confidence");
        } else {
            summary.append("Medium confidence");
        }
        
        if (hasAnalogy()) {
            summary.append(", includes analogy");
        }
        
        if (hasPrerequisites()) {
            summary.append(", identifies prerequisites");
        }
        
        return summary.toString();
    }
}

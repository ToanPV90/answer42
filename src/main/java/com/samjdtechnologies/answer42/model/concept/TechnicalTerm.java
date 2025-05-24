package com.samjdtechnologies.answer42.model.concept;

import com.samjdtechnologies.answer42.model.enums.TermType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a technical term identified in academic content.
 * Contains information about the term's frequency, type, and complexity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalTerm {
    private String term;
    private TermType type;
    private int frequency;
    private double complexity;
    @Builder.Default
    private String context = ""; // Context where the term was found
    
    public TechnicalTerm(String term, TermType type, int frequency, double complexity) {
        this.term = term;
        this.type = type;
        this.frequency = frequency;
        this.complexity = complexity;
        this.context = "";
    }
    
    public void incrementFrequency() { 
        this.frequency++; 
    }
    
    public void addFrequency(int count) {
        this.frequency += count;
    }
    
    /**
     * Calculate priority score based on frequency and complexity.
     */
    public double getPriorityScore() {
        return (frequency * 0.6) + (complexity * 0.4);
    }
    
    /**
     * Check if this term should be prioritized for explanation.
     */
    public boolean shouldPrioritize() {
        return frequency >= 2 || complexity >= 0.7;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TechnicalTerm that = (TechnicalTerm) obj;
        return term != null && term.equalsIgnoreCase(that.term);
    }
    
    @Override
    public int hashCode() {
        return term != null ? term.toLowerCase().hashCode() : 0;
    }
}

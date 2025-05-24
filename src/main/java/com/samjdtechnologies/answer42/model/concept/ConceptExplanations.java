package com.samjdtechnologies.answer42.model.concept;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.samjdtechnologies.answer42.model.enums.EducationLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Container for concept explanations at a specific education level.
 * Groups multiple concept explanations together with metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptExplanations {
    private EducationLevel level;
    @Builder.Default
    private Map<String, ConceptExplanation> explanations = new HashMap<>();
    @Builder.Default
    private int totalTermsProcessed = 0;
    @Builder.Default
    private double averageConfidence = 0.0;
    
    public ConceptExplanations(EducationLevel level, Map<String, ConceptExplanation> explanations) {
        this.level = level;
        this.explanations = explanations != null ? explanations : new HashMap<>();
        this.totalTermsProcessed = this.explanations.size();
        this.averageConfidence = calculateAverageConfidence();
    }
    
    /**
     * Add an explanation for a concept.
     */
    public void addExplanation(String term, ConceptExplanation explanation) {
        if (explanations == null) {
            explanations = new HashMap<>();
        }
        explanations.put(term.toLowerCase(), explanation);
        totalTermsProcessed = explanations.size();
        averageConfidence = calculateAverageConfidence();
    }
    
    /**
     * Get explanation for a specific term.
     */
    public ConceptExplanation getExplanation(String term) {
        return explanations != null ? explanations.get(term.toLowerCase()) : null;
    }
    
    /**
     * Check if explanations exist for a term.
     */
    public boolean hasExplanation(String term) {
        return explanations != null && explanations.containsKey(term.toLowerCase());
    }
    
    /**
     * Get all high-confidence explanations.
     */
    public List<ConceptExplanation> getHighConfidenceExplanations() {
        if (explanations == null) {
            return List.of();
        }
        
        return explanations.values().stream()
            .filter(ConceptExplanation::isHighConfidence)
            .collect(Collectors.toList());
    }
    
    /**
     * Get explanations that include analogies.
     */
    public List<ConceptExplanation> getExplanationsWithAnalogies() {
        if (explanations == null) {
            return List.of();
        }
        
        return explanations.values().stream()
            .filter(ConceptExplanation::hasAnalogy)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate average confidence across all explanations.
     */
    private double calculateAverageConfidence() {
        if (explanations == null || explanations.isEmpty()) {
            return 0.0;
        }
        
        return explanations.values().stream()
            .mapToDouble(ConceptExplanation::getConfidence)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Get summary statistics for this set of explanations.
     */
    public Map<String, Object> getSummaryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("educationLevel", level.getDisplayName());
        stats.put("totalExplanations", totalTermsProcessed);
        stats.put("averageConfidence", Math.round(averageConfidence * 100.0) / 100.0);
        stats.put("highConfidenceCount", getHighConfidenceExplanations().size());
        stats.put("analogyCount", getExplanationsWithAnalogies().size());
        
        return stats;
    }
    
    /**
     * Check if this collection has sufficient quality explanations.
     */
    public boolean hasSufficientQuality() {
        return averageConfidence >= 0.7 && totalTermsProcessed > 0;
    }
}

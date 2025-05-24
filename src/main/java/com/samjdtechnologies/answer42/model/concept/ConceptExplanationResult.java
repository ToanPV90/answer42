package com.samjdtechnologies.answer42.model.concept;

import java.time.LocalDateTime;
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
 * Complete result from concept explanation processing.
 * Contains explanations for multiple education levels and relationship mapping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptExplanationResult {
    @Builder.Default
    private Map<EducationLevel, ConceptExplanations> explanations = new HashMap<>();
    private ConceptRelationshipMap relationshipMap;
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
    @Builder.Default
    private int totalTermsProcessed = 0;
    @Builder.Default
    private double overallQualityScore = 0.0;
    @Builder.Default
    private Map<String, Object> processingMetadata = new HashMap<>();
    
    public ConceptExplanationResult(Map<EducationLevel, ConceptExplanations> explanations, 
                                  ConceptRelationshipMap relationshipMap) {
        this.explanations = explanations != null ? explanations : new HashMap<>();
        this.relationshipMap = relationshipMap;
        this.processedAt = LocalDateTime.now();
        this.totalTermsProcessed = calculateTotalTerms();
        this.overallQualityScore = calculateOverallQuality();
        this.processingMetadata = new HashMap<>();
    }
    
    /**
     * Get explanations for a specific education level.
     */
    public ConceptExplanations getExplanationsForLevel(EducationLevel level) {
        return explanations != null ? explanations.get(level) : null;
    }
    
    /**
     * Check if explanations exist for a specific level.
     */
    public boolean hasExplanationsForLevel(EducationLevel level) {
        return explanations != null && explanations.containsKey(level);
    }
    
    /**
     * Get explanation for a specific term at a specific level.
     */
    public ConceptExplanation getExplanation(String term, EducationLevel level) {
        ConceptExplanations levelExplanations = getExplanationsForLevel(level);
        return levelExplanations != null ? levelExplanations.getExplanation(term) : null;
    }
    
    /**
     * Get all available education levels with explanations.
     */
    public List<EducationLevel> getAvailableLevels() {
        return explanations != null ? 
            explanations.keySet().stream()
                .sorted((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .collect(Collectors.toList()) : 
            List.of();
    }
    
    /**
     * Get all high-confidence explanations across all levels.
     */
    public Map<EducationLevel, List<ConceptExplanation>> getHighConfidenceExplanations() {
        if (explanations == null) {
            return Map.of();
        }
        
        return explanations.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getHighConfidenceExplanations()
            ));
    }
    
    /**
     * Get explanations with analogies across all levels.
     */
    public Map<EducationLevel, List<ConceptExplanation>> getExplanationsWithAnalogies() {
        if (explanations == null) {
            return Map.of();
        }
        
        return explanations.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getExplanationsWithAnalogies()
            ));
    }
    
    /**
     * Get comprehensive summary of all explanations.
     */
    public Map<String, Object> getComprehensiveSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("processedAt", processedAt);
        summary.put("totalTermsProcessed", totalTermsProcessed);
        summary.put("overallQualityScore", Math.round(overallQualityScore * 100.0) / 100.0);
        summary.put("availableLevels", getAvailableLevels().stream()
            .map(EducationLevel::getDisplayName)
            .collect(Collectors.toList()));
        
        if (explanations != null) {
            Map<String, Object> levelSummaries = new HashMap<>();
            explanations.forEach((level, exp) -> 
                levelSummaries.put(level.getDisplayName(), exp.getSummaryStats()));
            summary.put("levelSummaries", levelSummaries);
        }
        
        if (relationshipMap != null) {
            summary.put("relationshipMap", relationshipMap.getSummaryStats());
        }
        
        return summary;
    }
    
    /**
     * Check if the result has sufficient quality across all levels.
     */
    public boolean hasSufficientQuality() {
        if (explanations == null || explanations.isEmpty()) {
            return false;
        }
        
        boolean allLevelsGoodQuality = explanations.values().stream()
            .allMatch(ConceptExplanations::hasSufficientQuality);
        
        boolean relationshipMapValid = relationshipMap == null || 
            relationshipMap.hasSufficientConnections();
        
        return allLevelsGoodQuality && relationshipMapValid && overallQualityScore >= 0.7;
    }
    
    /**
     * Calculate total number of unique terms processed across all levels.
     */
    private int calculateTotalTerms() {
        if (explanations == null || explanations.isEmpty()) {
            return 0;
        }
        
        // Get maximum terms from any single level (since same terms appear at multiple levels)
        return explanations.values().stream()
            .mapToInt(ConceptExplanations::getTotalTermsProcessed)
            .max()
            .orElse(0);
    }
    
    /**
     * Calculate overall quality score across all education levels.
     */
    private double calculateOverallQuality() {
        if (explanations == null || explanations.isEmpty()) {
            return 0.0;
        }
        
        double averageConfidence = explanations.values().stream()
            .mapToDouble(ConceptExplanations::getAverageConfidence)
            .average()
            .orElse(0.0);
        
        // Factor in relationship map quality if present
        double relationshipBonus = 0.0;
        if (relationshipMap != null && relationshipMap.hasSufficientConnections()) {
            relationshipBonus = 0.1; // 10% bonus for good relationships
        }
        
        return Math.min(1.0, averageConfidence + relationshipBonus);
    }
    
    /**
     * Add processing metadata.
     */
    public void addMetadata(String key, Object value) {
        if (processingMetadata == null) {
            processingMetadata = new HashMap<>();
        }
        processingMetadata.put(key, value);
    }
    
    /**
     * Get processing metadata value.
     */
    public Object getMetadata(String key) {
        return processingMetadata != null ? processingMetadata.get(key) : null;
    }
}

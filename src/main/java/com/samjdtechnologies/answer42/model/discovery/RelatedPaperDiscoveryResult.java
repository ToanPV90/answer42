package com.samjdtechnologies.answer42.model.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Results from the Related Paper Discovery Agent.
 * Contains all discovered papers organized by discovery method and source.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelatedPaperDiscoveryResult {

    private UUID sourcePaperId;
    private List<DiscoveredPaper> discoveredPapers;
    private Map<String, Integer> discoveryStatistics;
    private Instant discoveryStartTime;
    private Instant discoveryEndTime;
    private Long totalProcessingTimeMs;
    private DiscoveryConfiguration configuration;
    private List<String> warnings;
    private List<String> errors;
    private Double overallConfidenceScore;
    private Boolean requiresUserReview;

    /**
     * Creates an empty discovery result for a paper.
     */
    public static RelatedPaperDiscoveryResult empty(UUID sourcePaperId) {
        return RelatedPaperDiscoveryResult.builder()
            .sourcePaperId(sourcePaperId)
            .discoveredPapers(List.of())
            .discoveryStatistics(Map.of())
            .discoveryStartTime(Instant.now())
            .discoveryEndTime(Instant.now())
            .totalProcessingTimeMs(0L)
            .warnings(List.of())
            .errors(List.of())
            .overallConfidenceScore(0.0)
            .requiresUserReview(false)
            .build();
    }

    /**
     * Creates a successful discovery result.
     */
    public static RelatedPaperDiscoveryResult success(UUID sourcePaperId, 
            List<DiscoveredPaper> papers, DiscoveryConfiguration config) {
        return RelatedPaperDiscoveryResult.builder()
            .sourcePaperId(sourcePaperId)
            .discoveredPapers(papers)
            .discoveryStatistics(calculateStatistics(papers))
            .discoveryStartTime(Instant.now())
            .discoveryEndTime(Instant.now())
            .totalProcessingTimeMs(0L)
            .configuration(config)
            .warnings(List.of())
            .errors(List.of())
            .overallConfidenceScore(calculateOverallConfidence(papers))
            .requiresUserReview(determineReviewRequired(papers))
            .build();
    }

    /**
     * Creates a partial success result with some errors.
     */
    public static RelatedPaperDiscoveryResult partial(UUID sourcePaperId, 
            List<DiscoveredPaper> papers, List<String> errors, List<String> warnings) {
        return RelatedPaperDiscoveryResult.builder()
            .sourcePaperId(sourcePaperId)
            .discoveredPapers(papers)
            .discoveryStatistics(calculateStatistics(papers))
            .discoveryStartTime(Instant.now())
            .discoveryEndTime(Instant.now())
            .totalProcessingTimeMs(0L)
            .warnings(warnings)
            .errors(errors)
            .overallConfidenceScore(calculateOverallConfidence(papers))
            .requiresUserReview(true) // Always require review for partial results
            .build();
    }

    /**
     * Gets the total number of discovered papers.
     */
    public int getTotalDiscoveredPapers() {
        return discoveredPapers != null ? discoveredPapers.size() : 0;
    }

    /**
     * Gets papers discovered from a specific source.
     */
    public List<DiscoveredPaper> getPapersBySource(String sourceId) {
        if (discoveredPapers == null) {
            return List.of();
        }
        
        return discoveredPapers.stream()
            .filter(paper -> paper.getSource() != null && 
                    paper.getSource().getSourceId().equals(sourceId))
            .toList();
    }

    /**
     * Gets papers by relationship type.
     */
    public List<DiscoveredPaper> getPapersByRelationshipType(String relationshipId) {
        if (discoveredPapers == null) {
            return List.of();
        }
        
        return discoveredPapers.stream()
            .filter(paper -> paper.getRelationshipType() != null && 
                    paper.getRelationshipType().getRelationshipId().equals(relationshipId))
            .toList();
    }

    /**
     * Gets the top N papers by relevance score.
     */
    public List<DiscoveredPaper> getTopPapersByRelevance(int limit) {
        if (discoveredPapers == null) {
            return List.of();
        }
        
        return discoveredPapers.stream()
            .filter(paper -> paper.getRelevanceScore() != null)
            .sorted((a, b) -> Double.compare(
                b.getRelevanceScore(), a.getRelevanceScore()))
            .limit(limit)
            .toList();
    }

    /**
     * Calculates discovery statistics from the papers.
     */
    private static Map<String, Integer> calculateStatistics(List<DiscoveredPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return Map.of("total", 0);
        }

        Map<String, Integer> stats = new java.util.HashMap<>();
        stats.put("total", papers.size());

        // Count by source
        for (DiscoveredPaper paper : papers) {
            if (paper.getSource() != null) {
                String sourceKey = "source_" + paper.getSource().getSourceId();
                stats.put(sourceKey, stats.getOrDefault(sourceKey, 0) + 1);
            }
        }

        // Count by relationship type
        for (DiscoveredPaper paper : papers) {
            if (paper.getRelationshipType() != null) {
                String relKey = "relationship_" + paper.getRelationshipType().getRelationshipId();
                stats.put(relKey, stats.getOrDefault(relKey, 0) + 1);
            }
        }

        return stats;
    }

    /**
     * Calculates overall confidence score from individual paper scores.
     */
    private static Double calculateOverallConfidence(List<DiscoveredPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return 0.0;
        }

        double totalScore = papers.stream()
            .filter(paper -> paper.getRelevanceScore() != null)
            .mapToDouble(DiscoveredPaper::getRelevanceScore)
            .average()
            .orElse(0.0);

        return Math.min(1.0, Math.max(0.0, totalScore));
    }

    /**
     * Determines if user review is required based on paper metadata.
     */
    private static Boolean determineReviewRequired(List<DiscoveredPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return false;
        }

        return papers.stream()
            .anyMatch(paper -> paper.getMetadata() != null && 
                    paper.getMetadata().needsReview());
    }

    /**
     * Gets a summary of the discovery process.
     */
    public String getDiscoverySummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Discovered %d related papers", getTotalDiscoveredPapers()));
        
        if (overallConfidenceScore != null) {
            summary.append(String.format(" with %.1f%% confidence", 
                overallConfidenceScore * 100));
        }
        
        if (totalProcessingTimeMs != null && totalProcessingTimeMs > 0) {
            summary.append(String.format(" in %dms", totalProcessingTimeMs));
        }
        
        if (errors != null && !errors.isEmpty()) {
            summary.append(String.format(" (%d errors)", errors.size()));
        }
        
        if (warnings != null && !warnings.isEmpty()) {
            summary.append(String.format(" (%d warnings)", warnings.size()));
        }
        
        return summary.toString();
    }
}

package com.samjdtechnologies.answer42.model.discovery;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata associated with discovered papers.
 * Contains source-specific information and quality indicators.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveryMetadata implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Instant discoveredAt;
    private String discoveryQuery;
    private String apiVersion;
    private Double apiConfidenceScore;
    private Integer apiRequestDuration;
    private String searchTermsUsed;
    private Integer totalResultsFound;
    private Integer resultRankPosition;
    private Map<String, Object> sourceSpecificData;
    private String processingNotes;
    private Boolean requiresManualReview;
    private String qualityFlags;

    /**
     * Creates minimal discovery metadata.
     */
    public static DiscoveryMetadata minimal() {
        return DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .apiConfidenceScore(0.0)
            .apiRequestDuration(0)
            .totalResultsFound(0)
            .resultRankPosition(0)
            .requiresManualReview(false)
            .build();
    }

    /**
     * Creates discovery metadata for Crossref results.
     */
    public static DiscoveryMetadata forCrossref(String query, Integer duration, 
            Integer totalResults, Integer position, Double score) {
        return DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .discoveryQuery(query)
            .apiVersion("crossref-v1")
            .apiConfidenceScore(score)
            .apiRequestDuration(duration)
            .searchTermsUsed(query)
            .totalResultsFound(totalResults)
            .resultRankPosition(position)
            .requiresManualReview(false)
            .build();
    }

    /**
     * Creates discovery metadata for Semantic Scholar results.
     */
    public static DiscoveryMetadata forSemanticScholar(String query, Integer duration,
            Integer totalResults, Integer position, Double score) {
        return DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .discoveryQuery(query)
            .apiVersion("semantic-scholar-v1")
            .apiConfidenceScore(score)
            .apiRequestDuration(duration)
            .searchTermsUsed(query)
            .totalResultsFound(totalResults)
            .resultRankPosition(position)
            .requiresManualReview(false)
            .build();
    }

    /**
     * Creates discovery metadata for Perplexity results.
     */
    public static DiscoveryMetadata forPerplexity(String query, Integer duration,
            Integer totalResults, Integer position) {
        return DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .discoveryQuery(query)
            .apiVersion("perplexity-v1")
            .apiConfidenceScore(0.75) // Default confidence for Perplexity
            .apiRequestDuration(duration)
            .searchTermsUsed(query)
            .totalResultsFound(totalResults)
            .resultRankPosition(position)
            .requiresManualReview(true) // May need review for trending content
            .build();
    }

    /**
     * Creates discovery metadata for AI synthesis results.
     */
    public static DiscoveryMetadata forAISynthesis(Double confidenceScore, String notes) {
        return DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .discoveryQuery("ai-synthesis")
            .apiVersion("ai-synthesis-v1")
            .apiConfidenceScore(confidenceScore)
            .apiRequestDuration(0)
            .totalResultsFound(1)
            .resultRankPosition(1)
            .processingNotes(notes)
            .requiresManualReview(false)
            .build();
    }

    /**
     * Adds a quality flag to the metadata.
     */
    public void addQualityFlag(String flag) {
        if (qualityFlags == null || qualityFlags.isEmpty()) {
            qualityFlags = flag;
        } else {
            qualityFlags = qualityFlags + "," + flag;
        }
    }

    /**
     * Checks if this discovery requires manual review.
     */
    public boolean needsReview() {
        return Boolean.TRUE.equals(requiresManualReview) || 
               (apiConfidenceScore != null && apiConfidenceScore < 0.5) ||
               (qualityFlags != null && !qualityFlags.isEmpty());
    }

    /**
     * Gets a human-readable summary of the discovery process.
     */
    public String getDiscoverySummary() {
        StringBuilder summary = new StringBuilder();
        
        if (totalResultsFound != null && totalResultsFound > 0) {
            summary.append(String.format("Found among %d results", totalResultsFound));
            if (resultRankPosition != null) {
                summary.append(String.format(" (rank %d)", resultRankPosition));
            }
        }
        
        if (apiConfidenceScore != null) {
            summary.append(String.format(" - Confidence: %.1f%%", apiConfidenceScore * 100));
        }
        
        if (apiRequestDuration != null && apiRequestDuration > 0) {
            summary.append(String.format(" - Retrieved in %dms", apiRequestDuration));
        }
        
        return summary.toString().trim();
    }
}

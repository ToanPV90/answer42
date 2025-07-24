package com.samjdtechnologies.answer42.model.daos;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.samjdtechnologies.answer42.util.ListStringConverter;
import com.samjdtechnologies.answer42.util.MapStringIntegerConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database entity for storing discovery session results and metadata.
 */
@Entity
@Table(name = "discovery_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveryResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "source_paper_id", nullable = false)
    private UUID sourcePaperId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "configuration_name", nullable = false, length = 100)
    private String configurationName;

    @Column(name = "discovery_start_time", nullable = false)
    private Instant discoveryStartTime;

    @Column(name = "discovery_end_time")
    private Instant discoveryEndTime;

    @Column(name = "total_processing_time_ms")
    private Long totalProcessingTimeMs;

    @Column(name = "total_papers_discovered")
    private Integer totalPapersDiscovered;

    @Column(name = "total_papers_after_filtering")
    private Integer totalPapersAfterFiltering;

    @Column(name = "sources_used", columnDefinition = "TEXT")
    @Convert(converter = ListStringConverter.class)
    private List<String> sourcesUsed;

    @Column(name = "discovery_statistics", columnDefinition = "TEXT")
    @Convert(converter = MapStringIntegerConverter.class)
    private Map<String, Integer> discoveryStatistics;

    @Column(name = "overall_confidence_score")
    private Double overallConfidenceScore;

    @Column(name = "requires_user_review")
    private Boolean requiresUserReview;

    @Column(name = "crossref_papers_found")
    private Integer crossrefPapersFound;

    @Column(name = "semantic_scholar_papers_found")
    private Integer semanticScholarPapersFound;

    @Column(name = "perplexity_papers_found")
    private Integer perplexityPapersFound;

    @Column(name = "ai_synthesis_enabled")
    private Boolean aiSynthesisEnabled;

    @Column(name = "ai_synthesis_time_ms")
    private Long aiSynthesisTimeMs;

    @Column(name = "cache_hit_count")
    private Integer cacheHitCount;

    @Column(name = "cache_miss_count")
    private Integer cacheMissCount;

    @Column(name = "api_calls_made")
    private Integer apiCallsMade;

    @Column(name = "errors", columnDefinition = "TEXT")
    @Convert(converter = ListStringConverter.class)
    private List<String> errors;

    @Column(name = "warnings", columnDefinition = "TEXT")
    @Convert(converter = ListStringConverter.class)
    private List<String> warnings;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Calculate discovery success rate.
     */
    public double getSuccessRate() {
        if (totalPapersDiscovered == null || totalPapersDiscovered == 0) {
            return 0.0;
        }
        int errorCount = errors != null ? errors.size() : 0;
        return Math.max(0.0, 1.0 - ((double) errorCount / totalPapersDiscovered));
    }

    /**
     * Calculate cache efficiency.
     */
    public double getCacheEfficiency() {
        Integer hits = cacheHitCount != null ? cacheHitCount : 0;
        Integer misses = cacheMissCount != null ? cacheMissCount : 0;
        int total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        
        return ((double) hits / total) * 100.0;
    }

    /**
     * Calculate processing time per paper in milliseconds.
     */
    public double getProcessingTimePerPaper() {
        if (totalPapersDiscovered == null || totalPapersDiscovered == 0 || totalProcessingTimeMs == null) {
            return 0.0;
        }
        return (double) totalProcessingTimeMs / totalPapersDiscovered;
    }

    /**
     * Get total processing time in seconds.
     */
    public double getProcessingTimeSeconds() {
        if (totalProcessingTimeMs == null) {
            return 0.0;
        }
        return totalProcessingTimeMs / 1000.0;
    }

    /**
     * Check if discovery session had errors.
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Check if discovery session had warnings.
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Get summary string for logging.
     */
    public String getSummary() {
        return String.format(
            "Discovery %s: %d papers from %d sources in %.1fs (%.1f%% confidence)",
            id.toString().substring(0, 8),
            totalPapersDiscovered != null ? totalPapersDiscovered : 0,
            sourcesUsed != null ? sourcesUsed.size() : 0,
            getProcessingTimeSeconds(),
            overallConfidenceScore != null ? overallConfidenceScore * 100 : 0.0
        );
    }

    /**
     * Check if discovery was successful.
     */
    public boolean isSuccessful() {
        return !hasErrors() && 
               totalPapersDiscovered != null && 
               totalPapersDiscovered > 0 &&
               overallConfidenceScore != null &&
               overallConfidenceScore > 0.5;
    }

    /**
     * Get filtering efficiency (papers kept after filtering).
     */
    public double getFilteringEfficiency() {
        if (totalPapersDiscovered == null || totalPapersDiscovered == 0) {
            return 0.0;
        }
        
        Integer filtered = totalPapersAfterFiltering != null ? totalPapersAfterFiltering : totalPapersDiscovered;
        return ((double) filtered / totalPapersDiscovered) * 100.0;
    }
}

package com.samjdtechnologies.answer42.model.research;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comprehensive result from Perplexity research agent containing multiple research findings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerplexityResearchResult {
    
    private List<ResearchResult> queryResults;
    private ResearchSynthesis synthesis;
    private List<FactVerification> factVerifications;
    private TrendAnalysis trendAnalysis;
    private Map<String, Object> metadata;
    private long totalProcessingTimeMs;
    private Instant executedAt;

    public static PerplexityResearchResult successful(List<ResearchResult> results, 
                                                     ResearchSynthesis synthesis) {
        return PerplexityResearchResult.builder()
            .queryResults(results)
            .synthesis(synthesis)
            .executedAt(Instant.now())
            .build();
    }

    public boolean hasReliableResults() {
        return queryResults != null && queryResults.stream()
            .anyMatch(ResearchResult::isReliable);
    }

    public int getTotalSourceCount() {
        return queryResults != null ? queryResults.stream()
            .mapToInt(ResearchResult::getSourceCount)
            .sum() : 0;
    }

    public double getAverageConfidence() {
        return queryResults != null && !queryResults.isEmpty() ? 
            queryResults.stream()
                .mapToDouble(ResearchResult::getConfidenceScore)
                .average()
                .orElse(0.0) : 0.0;
    }

    public boolean hasFactVerifications() {
        return factVerifications != null && !factVerifications.isEmpty();
    }

    public boolean hasTrendAnalysis() {
        return trendAnalysis != null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResearchSynthesis {
        private String summary;
        private List<String> keyFindings;
        private List<String> contradictions;
        private List<String> researchGaps;
        private double overallConfidence;
        private List<ResearchSource> primarySources;

        public boolean hasContradictions() {
            return contradictions != null && !contradictions.isEmpty();
        }

        public boolean hasResearchGaps() {
            return researchGaps != null && !researchGaps.isEmpty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactVerification {
        private String fact;
        private VerificationStatus status;
        private String evidence;
        private List<ResearchSource> supportingSources;
        private double confidenceLevel;
        private String notes;

        public enum VerificationStatus {
            CONFIRMED,
            PARTIALLY_CONFIRMED,
            DISPUTED,
            UNVERIFIED,
            FALSE
        }

        public boolean isConfirmed() {
            return status == VerificationStatus.CONFIRMED;
        }

        public boolean needsAttention() {
            return status == VerificationStatus.DISPUTED || status == VerificationStatus.FALSE;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysis {
        private String topic;
        private List<String> emergingTrends;
        private List<String> decliningTrends;
        private List<String> futureDirections;
        private Map<String, Double> trendStrengths;
        private Instant analysisDate;

        public boolean hasEmergingTrends() {
            return emergingTrends != null && !emergingTrends.isEmpty();
        }

        public boolean hasDecliningTrends() {
            return decliningTrends != null && !decliningTrends.isEmpty();
        }

        public String getStrongestTrend() {
            return trendStrengths != null && !trendStrengths.isEmpty() ?
                trendStrengths.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null) : null;
        }
    }
}

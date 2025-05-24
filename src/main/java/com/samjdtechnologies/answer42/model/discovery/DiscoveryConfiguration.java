package com.samjdtechnologies.answer42.model.discovery;

import java.util.List;
import java.util.Set;

import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for paper discovery operations.
 * Defines which sources to use, what types of relationships to discover, and execution parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DiscoveryConfiguration {

    private Set<DiscoverySource> enabledSources;
    private Set<RelationshipType> targetRelationshipTypes;
    private Integer maxPapersPerSource;
    private Integer maxTotalPapers;
    private Double minimumRelevanceScore;
    private Boolean enableAISynthesis;
    private Integer timeoutSeconds;
    private Boolean parallelExecution;
    private String priority;

    /**
     * Creates a default discovery configuration.
     */
    public static DiscoveryConfiguration defaultConfig() {
        return DiscoveryConfiguration.builder()
            .enabledSources(Set.of(
                DiscoverySource.CROSSREF,
                DiscoverySource.SEMANTIC_SCHOLAR
            ))
            .targetRelationshipTypes(Set.of(
                RelationshipType.CITES,
                RelationshipType.CITED_BY,
                RelationshipType.SEMANTIC_SIMILARITY
            ))
            .maxPapersPerSource(25)
            .maxTotalPapers(100)
            .minimumRelevanceScore(0.3)
            .enableAISynthesis(true)
            .timeoutSeconds(300) // 5 minutes
            .parallelExecution(true)
            .priority("NORMAL")
            .build();
    }

    /**
     * Creates a comprehensive discovery configuration for thorough research.
     */
    public static DiscoveryConfiguration comprehensiveConfig() {
        return DiscoveryConfiguration.builder()
            .enabledSources(Set.of(
                DiscoverySource.CROSSREF,
                DiscoverySource.SEMANTIC_SCHOLAR,
                DiscoverySource.PERPLEXITY
            ))
            .targetRelationshipTypes(Set.of(
                RelationshipType.CITES,
                RelationshipType.CITED_BY,
                RelationshipType.SEMANTIC_SIMILARITY,
                RelationshipType.AUTHOR_NETWORK,
                RelationshipType.CO_CITATION,
                RelationshipType.TRENDING
            ))
            .maxPapersPerSource(50)
            .maxTotalPapers(200)
            .minimumRelevanceScore(0.2)
            .enableAISynthesis(true)
            .timeoutSeconds(600) // 10 minutes
            .parallelExecution(true)
            .priority("HIGH")
            .build();
    }

    /**
     * Creates a fast discovery configuration for quick results.
     */
    public static DiscoveryConfiguration fastConfig() {
        return DiscoveryConfiguration.builder()
            .enabledSources(Set.of(DiscoverySource.CROSSREF))
            .targetRelationshipTypes(Set.of(
                RelationshipType.CITES,
                RelationshipType.CITED_BY
            ))
            .maxPapersPerSource(10)
            .maxTotalPapers(25)
            .minimumRelevanceScore(0.5)
            .enableAISynthesis(false)
            .timeoutSeconds(60) // 1 minute
            .parallelExecution(false)
            .priority("LOW")
            .build();
    }

    /**
     * Creates a citation-focused discovery configuration.
     */
    public static DiscoveryConfiguration citationFocusedConfig() {
        return DiscoveryConfiguration.builder()
            .enabledSources(Set.of(
                DiscoverySource.CROSSREF,
                DiscoverySource.SEMANTIC_SCHOLAR
            ))
            .targetRelationshipTypes(Set.of(
                RelationshipType.CITES,
                RelationshipType.CITED_BY,
                RelationshipType.CO_CITATION,
                RelationshipType.BIBLIOGRAPHIC_COUPLING
            ))
            .maxPapersPerSource(30)
            .maxTotalPapers(120)
            .minimumRelevanceScore(0.4)
            .enableAISynthesis(true)
            .timeoutSeconds(240) // 4 minutes
            .parallelExecution(true)
            .priority("NORMAL")
            .build();
    }

    /**
     * Checks if a discovery source is enabled.
     */
    public boolean isSourceEnabled(DiscoverySource source) {
        return enabledSources != null && enabledSources.contains(source);
    }

    /**
     * Checks if a relationship type should be discovered.
     */
    public boolean shouldDiscoverRelationshipType(RelationshipType relationshipType) {
        return targetRelationshipTypes != null && 
               targetRelationshipTypes.contains(relationshipType);
    }

    /**
     * Gets the maximum papers allowed for a specific source.
     */
    public int getMaxPapersForSource(DiscoverySource source) {
        if (!isSourceEnabled(source)) {
            return 0;
        }
        return maxPapersPerSource != null ? maxPapersPerSource : 25;
    }

    /**
     * Gets the effective minimum relevance score.
     */
    public double getEffectiveMinimumRelevanceScore() {
        return minimumRelevanceScore != null ? minimumRelevanceScore : 0.3;
    }

    /**
     * Gets the effective timeout in seconds.
     */
    public int getEffectiveTimeoutSeconds() {
        return timeoutSeconds != null ? timeoutSeconds : 300;
    }

    /**
     * Checks if parallel execution is enabled.
     */
    public boolean isParallelExecutionEnabled() {
        return Boolean.TRUE.equals(parallelExecution);
    }

    /**
     * Checks if AI synthesis is enabled.
     */
    public boolean isAISynthesisEnabled() {
        return Boolean.TRUE.equals(enableAISynthesis);
    }

    /**
     * Gets a summary of the configuration.
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Sources: %s", 
            enabledSources != null ? enabledSources.size() : 0));
        
        summary.append(String.format(", Max papers: %d", 
            maxTotalPapers != null ? maxTotalPapers : 100));
        
        summary.append(String.format(", Min relevance: %.1f", 
            getEffectiveMinimumRelevanceScore()));
        
        summary.append(String.format(", Timeout: %ds", 
            getEffectiveTimeoutSeconds()));
        
        if (isParallelExecutionEnabled()) {
            summary.append(", Parallel");
        }
        
        if (isAISynthesisEnabled()) {
            summary.append(", AI Synthesis");
        }
        
        return summary.toString();
    }

    /**
     * Validates the configuration and returns any issues.
     */
    public List<String> validate() {
        List<String> issues = new java.util.ArrayList<>();
        
        if (enabledSources == null || enabledSources.isEmpty()) {
            issues.add("At least one discovery source must be enabled");
        }
        
        if (targetRelationshipTypes == null || targetRelationshipTypes.isEmpty()) {
            issues.add("At least one relationship type must be specified");
        }
        
        if (maxPapersPerSource != null && maxPapersPerSource <= 0) {
            issues.add("Maximum papers per source must be greater than 0");
        }
        
        if (maxTotalPapers != null && maxTotalPapers <= 0) {
            issues.add("Maximum total papers must be greater than 0");
        }
        
        if (minimumRelevanceScore != null && 
            (minimumRelevanceScore < 0.0 || minimumRelevanceScore > 1.0)) {
            issues.add("Minimum relevance score must be between 0.0 and 1.0");
        }
        
        if (timeoutSeconds != null && timeoutSeconds <= 0) {
            issues.add("Timeout must be greater than 0 seconds");
        }
        
        return issues;
    }

    /**
     * Checks if this configuration is valid.
     */
    public boolean isValid() {
        return validate().isEmpty();
    }
}

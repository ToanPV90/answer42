package com.samjdtechnologies.answer42.ui.helpers.components;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.db.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.repository.DiscoveredPaperRepository;
import com.samjdtechnologies.answer42.service.discovery.DiscoveryCoordinator;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Helper class for Related Papers discovery operations.
 * Provides utility methods for discovery execution, data processing, and UI operations.
 */
public class RelatedPapersComponentHelper {

    private static final Logger LOG = LoggerFactory.getLogger(RelatedPapersComponentHelper.class);


    public static final String CROSSREF = "CROSSREF";
    public static final String SEMANTIC_SCHOLAR = "SEMANTIC_SCHOLAR";
    public static final String PERPLEXITY = "PERPLEXITY";
    public static final String ARXIV = "ARXIV";
    public static final String PUBMED = "PUBMED";
    public static final String AI_SYNTHESIS = "AI_SYNTHESIS";
    public static final String UNKNOWN = "Unknown";

    private RelatedPapersComponentHelper() {
        // Utility class
    }

    /**
     * Gets display name for discovery source.
     */
    public static String getSourceDisplayName(DiscoverySource source) {
        return source.getDisplayName();
    }

    /**
     * Gets display name for discovery source string.
     */
    public static String getSourceDisplayName(String source) {
        if (source == null) {
            return UNKNOWN;
        }
        switch (source.toUpperCase()) {
            case CROSSREF: return "Crossref";
            case SEMANTIC_SCHOLAR: return "Semantic Scholar";
            case PERPLEXITY: return "Perplexity";
            case ARXIV: return "arXiv";
            case PUBMED: return "PubMed";
            case AI_SYNTHESIS: return "AI Synthesis";
            default: return source;
        }
    }

    /**
     * Gets display name for relationship type.
     */
    public static String getRelationshipDisplayName(RelationshipType type) {
        return type.getDisplayName();
    }

    /**
     * Converts string discovery source to enum.
     */
    public static DiscoverySource convertDiscoverySource(String source) {
        if (source == null) {
            return null;
        }
        try {
            return DiscoverySource.fromSourceId(source.toLowerCase().replace("_", "-"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Determines relationship type based on discovered paper metadata.
     */
    public static RelationshipType determineRelationshipType(DiscoveredPaper paper) {
        String source = paper.getDiscoverySource();
        if (CROSSREF.equals(source)) {
            if (paper.getCitationCount() != null && paper.getCitationCount() > 0) {
                return RelationshipType.CITES;
            }
            return RelationshipType.FIELD_RELATED;
        } else if (SEMANTIC_SCHOLAR.equals(source)) {
            return RelationshipType.SEMANTIC_SIMILARITY;
        } else if (PERPLEXITY.equals(source)) {
            return RelationshipType.TRENDING;
        } else {
            return RelationshipType.UNKNOWN;
        }
    }

    /**
     * Calculates discovery statistics from a list of discovered papers.
     */
    public static DiscoveryStats calculateStats(List<DiscoveredPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return new DiscoveryStats();
        }

        Map<String, Long> sourceStats = papers.stream()
            .collect(Collectors.groupingBy(DiscoveredPaper::getDiscoverySource, Collectors.counting()));

        Map<RelationshipType, Long> relationshipStats = papers.stream()
            .collect(Collectors.groupingBy(RelatedPapersComponentHelper::determineRelationshipType, Collectors.counting()));

        double avgRelevance = papers.stream()
            .mapToDouble(DiscoveredPaper::getRelevanceScore)
            .average()
            .orElse(0.0);

        String topSource = sourceStats.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> getSourceDisplayName(entry.getKey()) + " (" + entry.getValue() + ")")
            .orElse("None");

        String topRelationship = relationshipStats.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> getRelationshipDisplayName(entry.getKey()) + " (" + entry.getValue() + ")")
            .orElse("None");

        return new DiscoveryStats(
            papers.size(),
            avgRelevance,
            topSource,
            topRelationship,
            sourceStats,
            relationshipStats
        );
    }

    /**
     * Executes discovery asynchronously and handles the result.
     */
    public static CompletableFuture<RelatedPaperDiscoveryResult> executeDiscoveryAsync(
            Paper sourcePaper,
            DiscoveryCoordinator discoveryCoordinator,
            Runnable onSuccess,
            Runnable onError) {

        LoggingUtil.info(LOG, "executeDiscoveryAsync", "Starting discovery for paper: %s", sourcePaper.getId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                DiscoveryConfiguration config = DiscoveryConfiguration.defaultConfig();
                CompletableFuture<RelatedPaperDiscoveryResult> future = 
                    discoveryCoordinator.coordinateDiscovery(sourcePaper, config);
                return future.join(); // Convert CompletableFuture<CompletableFuture<T>> to T
            } catch (Exception e) {
                LoggingUtil.error(LOG, "executeDiscoveryAsync", "Discovery failed", e);
                throw new RuntimeException("Discovery failed: " + e.getMessage(), e);
            }
        }).whenComplete((result, throwable) -> {
            if (throwable != null) {
                LoggingUtil.error(LOG, "executeDiscoveryAsync", "Discovery failed", throwable);
                onError.run();
            } else {
                LoggingUtil.info(LOG, "executeDiscoveryAsync", "Discovery completed successfully");
                onSuccess.run();
            }
        });
    }

    /**
     * Formats authors list for display.
     */
    public static String formatAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return "-";
        }

        return authors.size() > 2 
            ? String.join(", ", authors.subList(0, 2)) + " et al."
            : String.join(", ", authors);
    }

    /**
     * Gets CSS theme class for discovery source badges.
     */
    public static String getSourceThemeClass(String source) {
        if (source == null) {
            return "normal";
        }
        switch (source.toUpperCase()) {
            case CROSSREF: return "success";
            case "SEMANTIC_SCHOLAR": return "primary";
            case "PERPLEXITY": return "contrast";
            case "ARXIV": return "normal";
            case "PUBMED": return "success";
            case "AI_SYNTHESIS": return "primary";
            default: return "normal";
        }
    }

    /**
     * Gets CSS theme class for relationship type badges.
     */
    public static String getRelationshipThemeClass(RelationshipType relationship) {
        if (relationship == RelationshipType.CITES || relationship == RelationshipType.CITED_BY) {
            return "primary";
        } else if (relationship == RelationshipType.SEMANTIC_SIMILARITY) {
            return "success";
        } else if (relationship == RelationshipType.AUTHOR_NETWORK) {
            return "contrast";
        } else if (relationship == RelationshipType.TRENDING) {
            return "warning";
        } else if (relationship == RelationshipType.METHODOLOGICAL) {
            return "error";
        } else if (relationship == RelationshipType.FIELD_RELATED) {
            return "normal";
        } else {
            return "normal";
        }
    }

    /**
     * Gets color class for relevance scores.
     */
    public static String getRelevanceColorClass(double score) {
        if (score >= 0.8) {
            return "var(--lumo-success-color)";
        } else if (score >= 0.6) {
            return "var(--lumo-primary-color)";
        } else {
            return "var(--lumo-secondary-text-color)";
        }
    }

    /**
     * Loads existing discoveries for a paper.
     */
    public static List<DiscoveredPaper> loadExistingDiscoveries(
            Paper sourcePaper, 
            DiscoveredPaperRepository repository) {
        try {
            List<DiscoveredPaper> discoveries = repository.findBySourcePaper_Id(sourcePaper.getId());
            LoggingUtil.debug(LOG, "loadExistingDiscoveries", "Loaded %d discovered papers", discoveries.size());
            return discoveries;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "loadExistingDiscoveries", "Failed to load discoveries", e);
            Notification.show("Failed to load discoveries: " + e.getMessage(), 
                5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return List.of();
        }
    }

    /**
     * Filters discovered papers based on search criteria.
     */
    public static List<DiscoveredPaper> filterDiscoveries(
            List<DiscoveredPaper> allPapers,
            String searchTerm,
            String sourceFilter,
            RelationshipType relationshipFilter) {

        if (allPapers == null) {
            return List.of();
        }

        return allPapers.stream()
            .filter(paper -> matchesSearchTerm(paper, searchTerm))
            .filter(paper -> sourceFilter == null || paper.getDiscoverySource().equals(sourceFilter))
            .filter(paper -> relationshipFilter == null || 
                determineRelationshipType(paper).equals(relationshipFilter))
            .collect(Collectors.toList());
    }

    private static boolean matchesSearchTerm(DiscoveredPaper paper, String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return true;
        }

        String term = searchTerm.toLowerCase().trim();
        
        if (paper.getTitle() != null && paper.getTitle().toLowerCase().contains(term)) {
            return true;
        }

        if (paper.getAuthors() != null) {
            return paper.getAuthors().stream()
                .anyMatch(author -> author.toLowerCase().contains(term));
        }

        return false;
    }

    /**
     * Gets discovery source icon name for UI.
     */
    public static String getSourceIcon(String source) {
        if (source == null) {
            return "help-circle";
        }
        switch (source.toUpperCase()) {
            case CROSSREF: return "link";
            case "SEMANTIC_SCHOLAR": return "brain";
            case "PERPLEXITY": return "trending-up";
            case "ARXIV": return "file-text";
            case "PUBMED": return "heart";
            case "AI_SYNTHESIS": return "cpu";
            default: return "help-circle";
        }
    }

    /**
     * Gets relationship type icon name for UI.
     */
    public static String getRelationshipIcon(RelationshipType relationship) {
        if (relationship == RelationshipType.CITES || relationship == RelationshipType.CITED_BY) {
            return "link";
        } else if (relationship == RelationshipType.SEMANTIC_SIMILARITY) {
            return "layers";
        } else if (relationship == RelationshipType.AUTHOR_NETWORK) {
            return "users";
        } else if (relationship == RelationshipType.TRENDING) {
            return "trending-up";
        } else if (relationship == RelationshipType.METHODOLOGICAL) {
            return "tool";
        } else if (relationship == RelationshipType.FIELD_RELATED) {
            return "book";
        } else if (relationship == RelationshipType.VENUE_SIMILARITY) {
            return "map-pin";
        } else if (relationship == RelationshipType.KEYWORD_OVERLAP) {
            return "tag";
        } else {
            return "help-circle";
        }
    }

    /**
     * Data class for discovery statistics.
     */
    public static class DiscoveryStats {
        private final int totalPapers;
        private final double avgRelevance;
        private final String topSource;
        private final String topRelationship;
        private final Map<String, Long> sourceStats;
        private final Map<RelationshipType, Long> relationshipStats;

        public DiscoveryStats() {
            this(0, 0.0, "None", "None", Map.of(), Map.of());
        }

        public DiscoveryStats(int totalPapers, double avgRelevance, String topSource, String topRelationship,
                Map<String, Long> sourceStats, Map<RelationshipType, Long> relationshipStats) {
            this.totalPapers = totalPapers;
            this.avgRelevance = avgRelevance;
            this.topSource = topSource;
            this.topRelationship = topRelationship;
            this.sourceStats = sourceStats;
            this.relationshipStats = relationshipStats;
        }

        public int getTotalPapers() { return totalPapers; }
        public double getAvgRelevance() { return avgRelevance; }
        public String getTopSource() { return topSource; }
        public String getTopRelationship() { return topRelationship; }
        public Map<String, Long> getSourceStats() { return sourceStats; }
        public Map<RelationshipType, Long> getRelationshipStats() { return relationshipStats; }
    }
}

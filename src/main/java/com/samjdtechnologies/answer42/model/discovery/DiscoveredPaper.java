package com.samjdtechnologies.answer42.model.discovery;

import java.time.LocalDateTime;
import java.util.List;

import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a discovered related paper from various sources.
 * Normalized structure across Crossref, Semantic Scholar, and Perplexity sources.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredPaper {

    private String id;
    private String title;
    private List<String> authors;
    private String journal;
    private Integer year;
    private String doi;
    private String abstractText;
    private String url;
    private DiscoverySource source;
    private Double relevanceScore;
    private Integer citationCount;
    private Double influenceScore;
    private List<String> keywords;
    private String venue;
    private LocalDateTime publishedDate;
    private RelationshipType relationshipType;
    private String relationshipDescription;
    private DiscoveryMetadata metadata;

    /**
     * Creates a DiscoveredPaper with minimal required information.
     */
    public static DiscoveredPaper minimal(String id, String title, DiscoverySource source) {
        return DiscoveredPaper.builder()
            .id(id)
            .title(title)
            .source(source)
            .relevanceScore(0.0)
            .citationCount(0)
            .influenceScore(0.0)
            .relationshipType(RelationshipType.UNKNOWN)
            .build();
    }

    /**
     * Creates a DiscoveredPaper for citation relationships.
     */
    public static DiscoveredPaper forCitation(String id, String title, List<String> authors, 
            String journal, Integer year, String doi, RelationshipType relationshipType, 
            DiscoverySource source) {
        return DiscoveredPaper.builder()
            .id(id)
            .title(title)
            .authors(authors)
            .journal(journal)
            .year(year)
            .doi(doi)
            .source(source)
            .relationshipType(relationshipType)
            .relevanceScore(0.8) // High relevance for citation relationships
            .citationCount(0)
            .influenceScore(0.0)
            .build();
    }

    /**
     * Creates a DiscoveredPaper for semantic similarity relationships.
     */
    public static DiscoveredPaper forSemantic(String id, String title, List<String> authors,
            String abstractText, Double similarityScore, DiscoverySource source) {
        return DiscoveredPaper.builder()
            .id(id)
            .title(title)
            .authors(authors)
            .abstractText(abstractText)
            .source(source)
            .relationshipType(RelationshipType.SEMANTIC_SIMILARITY)
            .relevanceScore(similarityScore)
            .citationCount(0)
            .influenceScore(0.0)
            .build();
    }

    /**
     * Gets display name for authors (max 3, then "et al.").
     */
    public String getDisplayAuthors() {
        if (authors == null || authors.isEmpty()) {
            return "Unknown Authors";
        }
        
        if (authors.size() <= 3) {
            return String.join(", ", authors);
        }
        
        return String.format("%s, %s, %s et al.", 
            authors.get(0), authors.get(1), authors.get(2));
    }

    /**
     * Gets short display title (max 100 characters).
     */
    public String getDisplayTitle() {
        if (title == null || title.length() <= 100) {
            return title;
        }
        return title.substring(0, 97) + "...";
    }

    /**
     * Gets formatted citation string.
     */
    public String getFormattedCitation() {
        StringBuilder citation = new StringBuilder();
        
        if (authors != null && !authors.isEmpty()) {
            citation.append(getDisplayAuthors()).append(". ");
        }
        
        if (title != null) {
            citation.append(title).append(". ");
        }
        
        if (journal != null) {
            citation.append(journal);
            if (year != null) {
                citation.append(" (").append(year).append(")");
            }
            citation.append(". ");
        } else if (year != null) {
            citation.append("(").append(year).append("). ");
        }
        
        if (doi != null) {
            citation.append("DOI: ").append(doi);
        }
        
        return citation.toString().trim();
    }
}

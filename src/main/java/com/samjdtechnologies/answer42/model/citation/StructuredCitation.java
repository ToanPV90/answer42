package com.samjdtechnologies.answer42.model.citation;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a citation that has been parsed into structured components.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructuredCitation {
    
    private String id;
    private List<String> authors;
    private String title;
    private String publicationVenue;
    private Integer year;
    private String volume;
    private String issue;
    private String pages;
    private String doi;
    private String url;
    private String publicationType;
    private String publisher;
    private String isbn;
    private String issn;
    private Double confidence;
    
    /**
     * Check if this citation has sufficient data for formatting.
     */
    public boolean isComplete() {
        return authors != null && !authors.isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               year != null &&
               publicationVenue != null && !publicationVenue.trim().isEmpty();
    }
    
    /**
     * Check if this citation has a valid DOI.
     */
    public boolean hasDoi() {
        return doi != null && !doi.trim().isEmpty() && doi.startsWith("10.");
    }
    
    /**
     * Get the first author's last name for sorting.
     */
    public String getFirstAuthorLastName() {
        if (authors == null || authors.isEmpty()) {
            return "";
        }
        
        String firstAuthor = authors.get(0);
        if (firstAuthor.contains(",")) {
            return firstAuthor.substring(0, firstAuthor.indexOf(",")).trim();
        }
        
        // Assume "First Last" format
        String[] parts = firstAuthor.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : firstAuthor;
    }
    
    /**
     * Get formatted author list for display.
     */
    public String getFormattedAuthors() {
        if (authors == null || authors.isEmpty()) {
            return "Unknown Author";
        }
        
        if (authors.size() == 1) {
            return authors.get(0);
        } else if (authors.size() == 2) {
            return authors.get(0) + " and " + authors.get(1);
        } else {
            return authors.get(0) + " et al.";
        }
    }
    
    /**
     * Create a basic citation with minimal required fields.
     */
    public static StructuredCitation basic(List<String> authors, String title, String venue, Integer year) {
        return StructuredCitation.builder()
            .authors(authors)
            .title(title)
            .publicationVenue(venue)
            .year(year)
            .confidence(0.7)
            .build();
    }
}

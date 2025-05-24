package com.samjdtechnologies.answer42.model.citation;

import java.util.List;
import java.util.Map;

import com.samjdtechnologies.answer42.model.enums.CitationStyle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete result of citation processing including formatted bibliographies.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitationResult {
    
    private List<StructuredCitation> citations;
    private Map<CitationStyle, FormattedBibliography> bibliographies;
    private int totalCitationsFound;
    private int citationsProcessed;
    private int citationsFormatted;
    private List<String> processingErrors;
    private long processingTimeMs;
    
    /**
     * Create a basic citation result.
     */
    public static CitationResult of(List<StructuredCitation> citations, 
                                   Map<CitationStyle, FormattedBibliography> bibliographies) {
        return CitationResult.builder()
            .citations(citations)
            .bibliographies(bibliographies)
            .totalCitationsFound(citations.size())
            .citationsProcessed(citations.size())
            .citationsFormatted(calculateFormattedCount(bibliographies))
            .processingTimeMs(0L)
            .build();
    }
    
    /**
     * Create a result with processing statistics.
     */
    public static CitationResult withStats(List<StructuredCitation> citations,
                                          Map<CitationStyle, FormattedBibliography> bibliographies,
                                          int totalFound, List<String> errors, long processingTime) {
        return CitationResult.builder()
            .citations(citations)
            .bibliographies(bibliographies)
            .totalCitationsFound(totalFound)
            .citationsProcessed(citations.size())
            .citationsFormatted(calculateFormattedCount(bibliographies))
            .processingErrors(errors)
            .processingTimeMs(processingTime)
            .build();
    }
    
    /**
     * Get processing success rate.
     */
    public double getProcessingSuccessRate() {
        if (totalCitationsFound == 0) return 1.0;
        return (double) citationsProcessed / totalCitationsFound;
    }
    
    /**
     * Get formatting success rate.
     */
    public double getFormattingSuccessRate() {
        if (citationsProcessed == 0) return 1.0;
        return (double) citationsFormatted / citationsProcessed;
    }
    
    /**
     * Check if processing was successful.
     */
    public boolean isSuccessful() {
        return processingErrors == null || processingErrors.isEmpty();
    }
    
    /**
     * Get bibliography for a specific style.
     */
    public FormattedBibliography getBibliography(CitationStyle style) {
        return bibliographies != null ? bibliographies.get(style) : null;
    }
    
    /**
     * Get all available citation styles.
     */
    public List<CitationStyle> getAvailableStyles() {
        return bibliographies != null ? List.copyOf(bibliographies.keySet()) : List.of();
    }
    
    /**
     * Get processing summary.
     */
    public String getProcessingSummary() {
        return String.format(
            "Found: %d | Processed: %d | Formatted: %d | Styles: %d | Time: %dms | Success: %.1f%%",
            totalCitationsFound,
            citationsProcessed,
            citationsFormatted,
            bibliographies != null ? bibliographies.size() : 0,
            processingTimeMs,
            getProcessingSuccessRate() * 100
        );
    }
    
    /**
     * Check if citations are complete enough for publication.
     */
    public boolean isPublicationReady() {
        return getProcessingSuccessRate() >= 0.9 && 
               getFormattingSuccessRate() >= 0.95 &&
               isSuccessful();
    }
    
    private static int calculateFormattedCount(Map<CitationStyle, FormattedBibliography> bibliographies) {
        if (bibliographies == null || bibliographies.isEmpty()) {
            return 0;
        }
        
        return bibliographies.values().stream()
            .mapToInt(FormattedBibliography::getFormattedCitations)
            .max()
            .orElse(0);
    }
}

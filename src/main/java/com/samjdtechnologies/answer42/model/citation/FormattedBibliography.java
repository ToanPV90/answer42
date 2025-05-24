package com.samjdtechnologies.answer42.model.citation;

import java.util.List;

import com.samjdtechnologies.answer42.model.enums.CitationStyle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a bibliography formatted in a specific citation style.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormattedBibliography {
    
    private CitationStyle style;
    private List<String> entries;
    private int totalCitations;
    private int formattedCitations;
    private String styleGuidelines;
    private boolean hasErrors;
    private List<String> errorMessages;
    
    /**
     * Create a bibliography with basic information.
     */
    public static FormattedBibliography of(CitationStyle style, List<String> entries) {
        return FormattedBibliography.builder()
            .style(style)
            .entries(entries)
            .totalCitations(entries.size())
            .formattedCitations(entries.size())
            .hasErrors(false)
            .build();
    }
    
    /**
     * Create a bibliography with error information.
     */
    public static FormattedBibliography withErrors(CitationStyle style, List<String> entries, 
                                                  int totalCitations, List<String> errors) {
        return FormattedBibliography.builder()
            .style(style)
            .entries(entries)
            .totalCitations(totalCitations)
            .formattedCitations(entries.size())
            .hasErrors(true)
            .errorMessages(errors)
            .build();
    }
    
    /**
     * Get the success rate of formatting.
     */
    public double getFormattingSuccessRate() {
        if (totalCitations == 0) return 1.0;
        return (double) formattedCitations / totalCitations;
    }
    
    /**
     * Check if formatting was completely successful.
     */
    public boolean isComplete() {
        return !hasErrors && formattedCitations == totalCitations;
    }
    
    /**
     * Get formatted bibliography as a single string.
     */
    public String getFormattedText() {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(style.getDisplayName()).append(" Bibliography\n\n");
        
        for (int i = 0; i < entries.size(); i++) {
            sb.append(entries.get(i));
            if (i < entries.size() - 1) {
                sb.append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Get bibliography statistics.
     */
    public String getStatistics() {
        return String.format(
            "Style: %s | Total: %d | Formatted: %d | Success Rate: %.1f%% | Errors: %s",
            style.getDisplayName(),
            totalCitations,
            formattedCitations,
            getFormattingSuccessRate() * 100,
            hasErrors ? errorMessages.size() : 0
        );
    }
}

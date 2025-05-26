package com.samjdtechnologies.answer42.model.agent;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data class for summary results from ContentSummarizerAgent.
 * Contains comprehensive summary information including content, metrics, and analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResult {
    private String content;
    private String summaryType;
    private int wordCount;
    private double compressionRatio;
    @Builder.Default
    private List<String> keyFindings = List.of();
    @Builder.Default
    private List<String> technicalTerms = List.of();
    @Builder.Default
    private List<String> methodologies = List.of();
    private double qualityScore;
    private String processingNotes;
}

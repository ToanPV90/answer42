package com.samjdtechnologies.answer42.model.semanticscholar;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for Semantic Scholar recommendations API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticScholarRecommendationsResponse {
    private List<SemanticScholarPaper> recommendedPapers;
}

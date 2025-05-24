package com.samjdtechnologies.answer42.model.semanticscholar;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base response model for Semantic Scholar API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticScholarResponse {
    private String paperId;
    private String title;
    private List<SemanticScholarPaper> data;
    private Integer total;
    private Integer offset;
}

package com.samjdtechnologies.answer42.model.semanticscholar;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a paper from Semantic Scholar API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticScholarPaper {
    private String paperId;
    private String title;
    private String abstractText;
    private String venue;
    private Integer year;
    private String publicationDate;
    private Integer citationCount;
    private Integer influentialCitationCount;
    private String url;
    private List<SemanticScholarAuthor> authors;
    private SemanticScholarExternalIds externalIds;
}

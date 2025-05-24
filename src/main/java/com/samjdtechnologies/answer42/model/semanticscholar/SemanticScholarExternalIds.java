package com.samjdtechnologies.answer42.model.semanticscholar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing external IDs from Semantic Scholar API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticScholarExternalIds {
    private String DOI;
    private String ArXiv;
    private String MAG;
    private String ACL;
    private String PubMed;
    private String CorpusId;
}

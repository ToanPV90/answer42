package com.samjdtechnologies.answer42.model.semanticscholar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing an author from Semantic Scholar API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticScholarAuthor {
    private String authorId;
    private String name;
}

package com.samjdtechnologies.answer42.model.research;

import java.io.Serializable;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a source of research information with credibility scoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchSource implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String title;
    private String url;
    private String domain;
    private String author;
    private String snippet;
    private double credibilityScore;
    private SourceType sourceType;
    private Instant publishedDate;
    private Instant accessedDate;

    public enum SourceType {
        ACADEMIC_PAPER,
        NEWS_ARTICLE,
        BLOG_POST,
        GOVERNMENT_REPORT,
        WIKIPEDIA,
        RESEARCH_INSTITUTE,
        CONFERENCE_PAPER,
        JOURNAL_ARTICLE,
        BOOK,
        WEBSITE,
        UNKNOWN
    }

    public static ResearchSource academicPaper(String title, String url, String author, String snippet) {
        return ResearchSource.builder()
            .title(title)
            .url(url)
            .author(author)
            .snippet(snippet)
            .sourceType(SourceType.ACADEMIC_PAPER)
            .credibilityScore(0.9)
            .accessedDate(Instant.now())
            .build();
    }

    public static ResearchSource newsArticle(String title, String url, String domain, String snippet) {
        return ResearchSource.builder()
            .title(title)
            .url(url)
            .domain(domain)
            .snippet(snippet)
            .sourceType(SourceType.NEWS_ARTICLE)
            .credibilityScore(0.7)
            .accessedDate(Instant.now())
            .build();
    }

    public static ResearchSource governmentReport(String title, String url, String snippet) {
        return ResearchSource.builder()
            .title(title)
            .url(url)
            .snippet(snippet)
            .sourceType(SourceType.GOVERNMENT_REPORT)
            .credibilityScore(0.95)
            .accessedDate(Instant.now())
            .build();
    }

    public boolean isHighCredibility() {
        return credibilityScore >= 0.8;
    }

    public boolean isAcademicSource() {
        return sourceType == SourceType.ACADEMIC_PAPER || 
               sourceType == SourceType.JOURNAL_ARTICLE ||
               sourceType == SourceType.CONFERENCE_PAPER;
    }

    public String getDisplayTitle() {
        if (title == null || title.length() <= 100) {
            return title;
        }
        return title.substring(0, 97) + "...";
    }

    public String getShortSnippet() {
        if (snippet == null || snippet.length() <= 150) {
            return snippet;
        }
        return snippet.substring(0, 147) + "...";
    }

    public String getFormattedCitation() {
        StringBuilder citation = new StringBuilder();
        
        if (author != null) {
            citation.append(author).append(". ");
        }
        
        if (title != null) {
            citation.append("\"").append(title).append(".\" ");
        }
        
        if (domain != null) {
            citation.append(domain).append(". ");
        }
        
        if (url != null) {
            citation.append(url);
        }
        
        return citation.toString();
    }
}

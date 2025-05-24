package com.samjdtechnologies.answer42.model.research;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the result of a research query execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchResult {
    
    private String queryId;
    private ResearchQuery.ResearchType queryType;
    private String content;
    private List<ResearchSource> sources;
    private Map<String, Object> metadata;
    private double confidenceScore;
    private Instant executedAt;
    private long processingTimeMs;

    public static ResearchResult successful(String queryId, ResearchQuery.ResearchType type, 
                                          String content, List<ResearchSource> sources) {
        return ResearchResult.builder()
            .queryId(queryId)
            .queryType(type)
            .content(content)
            .sources(sources)
            .confidenceScore(0.8)
            .executedAt(Instant.now())
            .build();
    }

    public static ResearchResult failed(String queryId, ResearchQuery.ResearchType type, String error) {
        return ResearchResult.builder()
            .queryId(queryId)
            .queryType(type)
            .content("Research failed: " + error)
            .confidenceScore(0.0)
            .executedAt(Instant.now())
            .build();
    }

    public boolean isReliable() {
        return confidenceScore >= 0.7 && sources != null && !sources.isEmpty();
    }

    public int getSourceCount() {
        return sources != null ? sources.size() : 0;
    }

    public boolean hasHighQualitySources() {
        return sources != null && sources.stream()
            .anyMatch(source -> source.getCredibilityScore() >= 0.8);
    }

    public String getSummary() {
        if (content == null || content.length() <= 200) {
            return content;
        }
        
        // Extract first two sentences
        String[] sentences = content.split("\\. ");
        if (sentences.length >= 2) {
            return sentences[0] + ". " + sentences[1] + ".";
        }
        
        return content.substring(0, 200) + "...";
    }
}

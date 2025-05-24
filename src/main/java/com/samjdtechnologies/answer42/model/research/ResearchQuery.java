package com.samjdtechnologies.answer42.model.research;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a research query for external fact verification and discovery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchQuery {
    
    private String queryId;
    private String queryText;
    private String context;
    private ResearchType type;
    private List<String> keywords;
    private String sourceDocument;
    private int priority;
    private Instant createdAt;

    public enum ResearchType {
        FACT_VERIFICATION,
        RELATED_PAPERS,
        TREND_ANALYSIS,
        EXPERT_OPINIONS,
        CURRENT_EVENTS,
        METHODOLOGY_VERIFICATION,
        STATISTICAL_VALIDATION
    }

    public static ResearchQuery factVerification(String queryText, String context, String sourceDocument) {
        return ResearchQuery.builder()
            .queryId(generateQueryId())
            .queryText(queryText)
            .context(context)
            .type(ResearchType.FACT_VERIFICATION)
            .sourceDocument(sourceDocument)
            .priority(1)
            .createdAt(Instant.now())
            .build();
    }

    public static ResearchQuery relatedPapers(String queryText, List<String> keywords) {
        return ResearchQuery.builder()
            .queryId(generateQueryId())
            .queryText(queryText)
            .type(ResearchType.RELATED_PAPERS)
            .keywords(keywords)
            .priority(2)
            .createdAt(Instant.now())
            .build();
    }

    public static ResearchQuery trendAnalysis(String queryText, String context) {
        return ResearchQuery.builder()
            .queryId(generateQueryId())
            .queryText(queryText)
            .context(context)
            .type(ResearchType.TREND_ANALYSIS)
            .priority(3)
            .createdAt(Instant.now())
            .build();
    }

    private static String generateQueryId() {
        return "query_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    public boolean isHighPriority() {
        return priority <= 2;
    }

    public String getFormattedQuery() {
        StringBuilder formatted = new StringBuilder(queryText);
        
        if (context != null && !context.trim().isEmpty()) {
            formatted.append(" Context: ").append(context);
        }
        
        if (keywords != null && !keywords.isEmpty()) {
            formatted.append(" Keywords: ").append(String.join(", ", keywords));
        }
        
        return formatted.toString();
    }
}

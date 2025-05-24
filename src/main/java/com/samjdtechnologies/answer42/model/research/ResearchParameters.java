package com.samjdtechnologies.answer42.model.research;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Research parameters extracted from agent task for Perplexity research queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchParameters {
    
    private String paperId;
    private String researchTopic;
    private String researchDomain;
    private String paperContext;
    @Builder.Default
    private List<String> keyClaims = List.of();
    @Builder.Default
    private List<String> keywords = List.of();
    private String methodologyDescription;
    @Builder.Default
    private boolean verifyFacts = true;
    @Builder.Default
    private boolean findRelatedPapers = true;
    @Builder.Default
    private boolean analyzeTrends = false;
    @Builder.Default
    private boolean verifyMethodology = false;
    @Builder.Default
    private boolean gatherExpertOpinions = false;

    public boolean shouldVerifyFacts() {
        return verifyFacts && !keyClaims.isEmpty();
    }

    public boolean shouldFindRelatedPapers() {
        return findRelatedPapers;
    }

    public boolean shouldAnalyzeTrends() {
        return analyzeTrends;
    }

    public boolean shouldVerifyMethodology() {
        return verifyMethodology && methodologyDescription != null && !methodologyDescription.isEmpty();
    }

    public boolean shouldGatherExpertOpinions() {
        return gatherExpertOpinions;
    }

    public int getActiveResearchTypeCount() {
        int count = 0;
        if (shouldVerifyFacts()) count++;
        if (shouldFindRelatedPapers()) count++;
        if (shouldAnalyzeTrends()) count++;
        if (shouldVerifyMethodology()) count++;
        if (shouldGatherExpertOpinions()) count++;
        return count;
    }

    public static ResearchParameters defaultParameters(String paperId, String topic) {
        return ResearchParameters.builder()
            .paperId(paperId)
            .researchTopic(topic)
            .researchDomain("Academic Research")
            .verifyFacts(true)
            .findRelatedPapers(true)
            .build();
    }

    public static ResearchParameters comprehensiveResearch(String paperId, String topic, 
                                                          List<String> claims, List<String> keywords) {
        return ResearchParameters.builder()
            .paperId(paperId)
            .researchTopic(topic)
            .researchDomain("Academic Research")
            .keyClaims(claims)
            .keywords(keywords)
            .verifyFacts(true)
            .findRelatedPapers(true)
            .analyzeTrends(true)
            .gatherExpertOpinions(true)
            .build();
    }
}

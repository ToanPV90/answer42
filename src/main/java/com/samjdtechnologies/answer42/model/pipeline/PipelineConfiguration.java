package com.samjdtechnologies.answer42.model.pipeline;

import java.util.HashSet;
import java.util.Set;

import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for pipeline execution.
 * Defines which stages and agents should be included in the processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfiguration {
    
    private String name;
    private String description;
    
    @Builder.Default
    private Set<StageType> requiredStages = new HashSet<>();
    
    @Builder.Default
    private Set<AgentType> enabledAgents = new HashSet<>();
    
    private boolean includeMetadataEnhancement;
    private boolean generateSummaries;
    private boolean includeCitationProcessing;
    private boolean includeQualityChecking;
    private boolean includeResearchDiscovery;
    
    private int maxConcurrentAgents;
    private long timeoutMinutes;
    
    /**
     * Create a quick analysis configuration.
     */
    public static PipelineConfiguration quickAnalysis() {
        return PipelineConfiguration.builder()
            .name("Quick Analysis")
            .description("Fast processing with essential agents only")
            .requiredStages(Set.of(
                StageType.TEXT_EXTRACTION,
                StageType.CONTENT_ANALYSIS,
                StageType.QUALITY_CHECK
            ))
            .enabledAgents(Set.of(
                AgentType.PAPER_PROCESSOR,
                AgentType.CONTENT_SUMMARIZER,
                AgentType.QUALITY_CHECKER
            ))
            .generateSummaries(true)
            .includeQualityChecking(true)
            .maxConcurrentAgents(2)
            .timeoutMinutes(5)
            .build();
    }
    
    /**
     * Create a comprehensive analysis configuration.
     */
    public static PipelineConfiguration comprehensiveAnalysis() {
        return PipelineConfiguration.builder()
            .name("Comprehensive Analysis")
            .description("Full processing with all available agents")
            .requiredStages(Set.of(
                StageType.TEXT_EXTRACTION,
                StageType.METADATA_ENHANCEMENT,
                StageType.CONTENT_ANALYSIS,
                StageType.CONCEPT_EXTRACTION,
                StageType.CITATION_PROCESSING,
                StageType.QUALITY_CHECK
            ))
            .enabledAgents(Set.of(
                AgentType.PAPER_PROCESSOR,
                AgentType.METADATA_ENHANCER,
                AgentType.CONTENT_SUMMARIZER,
                AgentType.CONCEPT_EXPLAINER,
                AgentType.CITATION_FORMATTER,
                AgentType.QUALITY_CHECKER
            ))
            .includeMetadataEnhancement(true)
            .generateSummaries(true)
            .includeCitationProcessing(true)
            .includeQualityChecking(true)
            .maxConcurrentAgents(4)
            .timeoutMinutes(15)
            .build();
    }
    
    /**
     * Create a research-grade analysis configuration.
     */
    public static PipelineConfiguration researchGradeAnalysis() {
        return PipelineConfiguration.builder()
            .name("Research Grade Analysis")
            .description("Comprehensive processing with related paper discovery")
            .requiredStages(Set.of(
                StageType.TEXT_EXTRACTION,
                StageType.METADATA_ENHANCEMENT,
                StageType.CONTENT_ANALYSIS,
                StageType.CONCEPT_EXTRACTION,
                StageType.CITATION_PROCESSING,
                StageType.RESEARCH_DISCOVERY,
                StageType.QUALITY_CHECK
            ))
            .enabledAgents(Set.of(
                AgentType.PAPER_PROCESSOR,
                AgentType.METADATA_ENHANCER,
                AgentType.CONTENT_SUMMARIZER,
                AgentType.CONCEPT_EXPLAINER,
                AgentType.CITATION_FORMATTER,
                AgentType.RELATED_PAPER_DISCOVERY,
                AgentType.QUALITY_CHECKER
            ))
            .includeMetadataEnhancement(true)
            .generateSummaries(true)
            .includeCitationProcessing(true)
            .includeQualityChecking(true)
            .includeResearchDiscovery(true)
            .maxConcurrentAgents(6)
            .timeoutMinutes(20)
            .build();
    }
}

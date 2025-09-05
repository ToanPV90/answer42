package com.samjdtechnologies.answer42.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.samjdtechnologies.answer42.batch.tasklets.CitationFormatterTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.CitationVerifierTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.ConceptExplainerTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.ContentSummarizerTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.MetadataEnhancementTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.PaperProcessorTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.PerplexityResearchTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.QualityCheckerTasklet;
import com.samjdtechnologies.answer42.batch.tasklets.RelatedPaperDiscoveryTasklet;

/**
 * Spring Batch configuration for the Answer42 multi-agent paper processing pipeline.
 * 
 * <h2>Pipeline Architecture Overview</h2>
 * This configuration orchestrates a sophisticated 9-step sequential processing pipeline that transforms
 * raw PDF academic papers into comprehensively analyzed and enhanced documents with AI-powered insights.
 * Each step is implemented as a Spring Batch tasklet that wraps specialized AI agents.
 * 
 * <h2>Pipeline Flow & Credit Consumption</h2>
 * <pre>
 * Step | Operation                | Credits (Basic/Pro/Scholar) | Database Updates
 * -----|--------------------------|----------------------------|------------------
 * 1.   | Paper Text Extraction    | 3/5/7 credits             | papers.text_content, papers.processing_status
 * 2.   | Metadata Enhancement     | 2/3/4 credits             | papers.crossref_metadata, papers.semantic_scholar_metadata
 * 3.   | Content Summarization    | 4/6/8 credits             | papers.summary_brief, papers.summary_standard, papers.summary_detailed
 * 4.   | Concept Explanation      | 3/5/7 credits             | papers.glossary, papers.main_concepts, papers.topics
 * 5.   | Perplexity Research      | 5/7/10 credits            | papers.research_questions, papers.methodology_details
 * 6.   | Related Paper Discovery  | 5/7/10 credits            | discovered_papers, discovery_results, papers.metadata
 * 7.   | Citation Formatting      | 2/3/4 credits             | papers.citations, papers.references_count
 * 7.5  | Citation Verification    | 2/3/4 credits             | citation_verifications, papers.citations_verified
 * 8.   | Quality Assessment       | 4/6/8 credits             | papers.quality_feedback, papers.quality_score
 * -----|--------------------------|----------------------------|------------------
 *      | TOTAL (Individual Steps) | 30/45/62 credits          | Complete pipeline processing
 *      | BUNDLED PIPELINE         | 18/25/35 credits          | Discounted full processing
 * </pre>
 * 
 * @author Answer42 Team
 * @since 1.0.0
 * @see BaseAgentTasklet Base class for all agent tasklets
 * @see MultiAgentJobParametersValidator Parameter validation logic
 */
@Configuration
public class MultiAgentPipelineJobConfig {

    /**
     * Main paper processing job with sequential agent steps and comprehensive parameter validation.
     * 
     * @param jobRepository Spring Batch job repository for job metadata and execution tracking
     * @param transactionManager Platform transaction manager for database transaction handling
     * @param paperProcessorTasklet Step 1: PDF text extraction and initial processing
     * @param metadataTasklet Step 2: External metadata enhancement and verification
     * @param summarizerTasklet Step 3: Multi-level content summarization
     * @param conceptTasklet Step 4: Technical concept explanation and glossary creation
     * @param researchTasklet Step 5: External research using Perplexity API
     * @param discoveryTasklet Step 6: Related paper discovery and analysis
     * @param citationTasklet Step 7: Citation extraction and formatting
     * @param citationVerifierTasklet Step 7.5: Citation verification and validation
     * @param qualityTasklet Step 8: Quality assessment and validation
     * @param parametersValidator Validates required job parameters before execution
     * @return Configured Spring Batch job ready for execution
     */
    @Bean
    public Job paperProcessingJob(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 PaperProcessorTasklet paperProcessorTasklet,
                                 MetadataEnhancementTasklet metadataTasklet,
                                 ContentSummarizerTasklet summarizerTasklet,
                                 ConceptExplainerTasklet conceptTasklet,
                                 CitationFormatterTasklet citationTasklet,
                                 CitationVerifierTasklet citationVerifierTasklet,
                                 PerplexityResearchTasklet researchTasklet,
                                 RelatedPaperDiscoveryTasklet discoveryTasklet,
                                 QualityCheckerTasklet qualityTasklet,
                                 MultiAgentJobParametersValidator parametersValidator) {
        return new JobBuilder("paperProcessingJob", jobRepository)
            .validator(parametersValidator)
            .start(paperExtractionStep(jobRepository, transactionManager, paperProcessorTasklet))
            .next(metadataEnhancementStep(jobRepository, transactionManager, metadataTasklet))
            .next(contentSummarizationStep(jobRepository, transactionManager, summarizerTasklet))
            .next(conceptExplanationStep(jobRepository, transactionManager, conceptTasklet))
            .next(perplexityResearchStep(jobRepository, transactionManager, researchTasklet))
            .next(relatedPaperDiscoveryStep(jobRepository, transactionManager, discoveryTasklet))
            .next(citationFormattingStep(jobRepository, transactionManager, citationTasklet))
            .next(citationVerificationStep(jobRepository, transactionManager, citationVerifierTasklet))
            .next(qualityCheckStep(jobRepository, transactionManager, qualityTasklet))
            .build();
    }

    /**
     * Step 1: Paper Text Extraction and Initial Processing
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param paperProcessorTasklet Tasklet wrapping PaperProcessorAgent
     * @return Configured Spring Batch step for paper text extraction
     */
    @Bean
    public Step paperExtractionStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   PaperProcessorTasklet paperProcessorTasklet) {
        return new StepBuilder("paperExtraction", jobRepository)
            .tasklet(paperProcessorTasklet, transactionManager)
            .build();
    }

    /**
     * Step 2: Metadata Enhancement from External Sources
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param metadataTasklet Tasklet wrapping MetadataEnhancementAgent
     * @return Configured Spring Batch step for metadata enhancement
     */
    @Bean
    public Step metadataEnhancementStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       MetadataEnhancementTasklet metadataTasklet) {
        return new StepBuilder("metadataEnhancement", jobRepository)
            .tasklet(metadataTasklet, transactionManager)
            .build();
    }

    /**
     * Step 3: Multi-Level Content Summarization
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param summarizerTasklet Tasklet wrapping ContentSummarizerAgent
     * @return Configured Spring Batch step for content summarization
     */
    @Bean
    public Step contentSummarizationStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        ContentSummarizerTasklet summarizerTasklet) {
        return new StepBuilder("contentSummarization", jobRepository)
            .tasklet(summarizerTasklet, transactionManager)
            .build();
    }

    /**
     * Step 4: Technical Concept Explanation and Glossary Generation
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param conceptTasklet Tasklet wrapping ConceptExplainerAgent
     * @return Configured Spring Batch step for concept explanation
     */
    @Bean
    public Step conceptExplanationStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      ConceptExplainerTasklet conceptTasklet) {
        return new StepBuilder("conceptExplanation", jobRepository)
            .tasklet(conceptTasklet, transactionManager)
            .build();
    }

    /**
     * Step 5: External Research and Fact Verification using Perplexity
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param researchTasklet Tasklet wrapping PerplexityResearchAgent
     * @return Configured Spring Batch step for external research
     */
    @Bean
    public Step perplexityResearchStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      PerplexityResearchTasklet researchTasklet) {
        return new StepBuilder("perplexityResearch", jobRepository)
            .tasklet(researchTasklet, transactionManager)
            .build();
    }

    /**
     * Step 6: Related Paper Discovery and Analysis
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param discoveryTasklet Tasklet wrapping RelatedPaperDiscoveryAgent
     * @return Configured Spring Batch step for related paper discovery
     */
    @Bean
    public Step relatedPaperDiscoveryStep(JobRepository jobRepository,
                                         PlatformTransactionManager transactionManager,
                                         RelatedPaperDiscoveryTasklet discoveryTasklet) {
        return new StepBuilder("relatedPaperDiscovery", jobRepository)
            .tasklet(discoveryTasklet, transactionManager)
            .build();
    }

    /**
     * Step 7: Citation Extraction and Multi-Format Bibliography Generation
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param citationTasklet Tasklet wrapping CitationFormatterAgent
     * @return Configured Spring Batch step for citation formatting
     */
    @Bean
    public Step citationFormattingStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      CitationFormatterTasklet citationTasklet) {
        return new StepBuilder("citationFormatting", jobRepository)
            .tasklet(citationTasklet, transactionManager)
            .build();
    }

    /**
     * Step 7.5: Citation Verification and Validation
     * 
     * <p>Verifies the accuracy and authenticity of extracted citations by cross-referencing
     * with external databases and validating DOIs, ensuring citation reliability and completeness.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>DOI validation and cross-reference against Crossref database</li>
     *   <li>Citation accuracy verification using multiple academic databases</li>
     *   <li>Author name disambiguation and affiliation verification</li>
     *   <li>Publication metadata validation and correction</li>
     *   <li>Broken citation detection and repair suggestions</li>
     *   <li>Citation completeness assessment and missing data identification</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>citation_verifications</strong> - Individual citation verification results</li>
     *   <li><strong>papers.citations_verified</strong> - Verified citation data (JSONB)</li>
     *   <li><strong>papers.citation_verification_status</strong> - Overall verification status</li>
     *   <li><strong>papers.broken_citations</strong> - List of problematic citations (JSONB)</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "CITATIONS_VERIFIED"</li>
     * </ul>
     */
    @Bean
    public Step citationVerificationStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        CitationVerifierTasklet citationVerifierTasklet) {
        return new StepBuilder("citationVerification", jobRepository)
            .tasklet(citationVerifierTasklet, transactionManager)
            .build();
    }

    /**
     * Step 8: Quality Assessment and Final Validation
     * 
     * @param jobRepository Spring Batch job repository for step execution tracking
     * @param transactionManager Database transaction manager for ACID compliance
     * @param qualityTasklet Tasklet wrapping QualityCheckerAgent
     * @return Configured Spring Batch step for quality assessment
     */
    @Bean
    public Step qualityCheckStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                QualityCheckerTasklet qualityTasklet) {
        return new StepBuilder("qualityCheck", jobRepository)
            .tasklet(qualityTasklet, transactionManager)
            .build();
    }
}

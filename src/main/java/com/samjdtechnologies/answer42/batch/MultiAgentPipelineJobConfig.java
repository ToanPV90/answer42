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
 * This configuration orchestrates a sophisticated 8-step sequential processing pipeline that transforms
 * raw PDF academic papers into comprehensively analyzed and enhanced documents with AI-powered insights.
 * Each step is implemented as a Spring Batch tasklet that wraps specialized AI agents.
 * 
 * <h2>Pipeline Flow & Database Integration</h2>
 * <pre>
 * 1. Paper Text Extraction     → papers.text_content, papers.processing_status
 * 2. Metadata Enhancement      → papers.crossref_metadata, papers.semantic_scholar_metadata
 * 3. Content Summarization     → papers.summary_brief, papers.summary_standard, papers.summary_detailed
 * 4. Concept Explanation       → papers.glossary, papers.main_concepts, papers.topics
 * 5. Perplexity Research       → papers.research_questions, papers.methodology_details
 * 6. Related Paper Discovery   → discovered_papers, discovery_results, papers.metadata
 * 7. Citation Formatting       → papers.citations, papers.references_count
 * 8. Quality Assessment        → papers.quality_feedback, papers.quality_score
 * </pre>
 * 
 * <h2>Cost Tracking & Operations</h2>
 * Each step automatically creates entries in:
 * <ul>
 *   <li><strong>user_operations</strong> - Tracks individual operations with token usage and costs</li>
 *   <li><strong>credit_transactions</strong> - Records credit deductions for each processing step</li>
 *   <li><strong>credit_balances</strong> - Updates user credit balances in real-time</li>
 * </ul>
 * 
 * <h2>AI Provider Integration</h2>
 * The pipeline leverages multiple AI providers optimized for specific tasks:
 * <ul>
 *   <li><strong>OpenAI GPT-4</strong> - Text extraction, concept explanation, citation formatting</li>
 *   <li><strong>Anthropic Claude</strong> - Content summarization, quality checking</li>
 *   <li><strong>Perplexity</strong> - External research and fact verification</li>
 * </ul>
 * 
 * <h2>Job Parameters Required</h2>
 * <ul>
 *   <li><strong>paperId</strong> (String) - UUID of the paper to process</li>
 *   <li><strong>userId</strong> (String) - UUID of the user who initiated processing</li>
 *   <li><strong>startTime</strong> (Date) - Job execution timestamp</li>
 *   <li><strong>processingMode</strong> (String) - Processing mode (FULL_ANALYSIS, QUICK_ANALYSIS, etc.)</li>
 * </ul>
 * 
 * <h2>Error Handling & Monitoring</h2>
 * Each step includes comprehensive error handling with automatic retry for transient failures.
 * Spring Batch provides built-in job execution monitoring, step status tracking, and failure recovery.
 * All agent operations are logged with detailed metrics for performance monitoring and debugging.
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
     * <p>This job orchestrates the complete multi-agent pipeline processing workflow, executing
     * eight specialized steps in sequence to transform raw PDF papers into fully analyzed documents
     * with AI-powered insights, summaries, and quality assessments.</p>
     * 
     * <h3>Job Execution Flow</h3>
     * <ol>
     *   <li><strong>paperExtractionStep</strong> - Extracts and processes raw text from PDF</li>
     *   <li><strong>metadataEnhancementStep</strong> - Enriches metadata from external APIs</li>
     *   <li><strong>contentSummarizationStep</strong> - Generates multi-level summaries</li>
     *   <li><strong>conceptExplanationStep</strong> - Creates glossaries and concept explanations</li>
     *   <li><strong>perplexityResearchStep</strong> - Performs external research and fact-checking</li>
     *   <li><strong>relatedPaperDiscoveryStep</strong> - Discovers and analyzes related academic papers</li>
     *   <li><strong>citationFormattingStep</strong> - Extracts and formats citations</li>
     *   <li><strong>qualityCheckStep</strong> - Validates output quality and accuracy</li>
     * </ol>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers</strong> - Primary table for all processing results and status updates</li>
     *   <li><strong>user_operations</strong> - Operation tracking with costs and token usage</li>
     *   <li><strong>credit_transactions</strong> - Credit deductions for each processing step</li>
     *   <li><strong>credit_balances</strong> - Real-time user credit balance updates</li>
     * </ul>
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
                                 PerplexityResearchTasklet researchTasklet,
                                 RelatedPaperDiscoveryTasklet discoveryTasklet,
                                 QualityCheckerTasklet qualityTasklet,
                                 MultiAgentJobParametersValidator parametersValidator) {
        return new JobBuilder("paperProcessingJob", jobRepository)
            .validator(parametersValidator) // Add parameter validation at job level
            .start(paperExtractionStep(jobRepository, transactionManager, paperProcessorTasklet))
            .next(metadataEnhancementStep(jobRepository, transactionManager, metadataTasklet))
            .next(contentSummarizationStep(jobRepository, transactionManager, summarizerTasklet))
            .next(conceptExplanationStep(jobRepository, transactionManager, conceptTasklet))
            .next(perplexityResearchStep(jobRepository, transactionManager, researchTasklet))
            .next(relatedPaperDiscoveryStep(jobRepository, transactionManager, discoveryTasklet))
            .next(citationFormattingStep(jobRepository, transactionManager, citationTasklet))
            .next(qualityCheckStep(jobRepository, transactionManager, qualityTasklet))
            .build();
    }

    /**
     * Step 1: Paper Text Extraction and Initial Processing
     * 
     * <p>Extracts raw text content from uploaded PDF files and performs initial document structure analysis
     * using OpenAI GPT-4. This is the foundational step that enables all subsequent processing.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>PDF text extraction with layout preservation</li>
     *   <li>Document structure identification (sections, headers, tables)</li>
     *   <li>Text quality assessment and cleanup</li>
     *   <li>Initial metadata extraction from document content</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.text_content</strong> - Complete extracted text content</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "TEXT_EXTRACTED"</li>
     *   <li><strong>papers.metadata</strong> - Document structure and extraction metadata</li>
     *   <li><strong>user_operations</strong> - Creates PAPER_TEXT_EXTRACTION operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 3/5/7 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>OpenAI GPT-4</strong> - Optimal for text structure recognition and OCR correction
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
     * <p>Enriches paper metadata by querying external academic databases and APIs to verify
     * and enhance bibliographic information, author details, and publication data.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Crossref API integration for DOI resolution and bibliographic data</li>
     *   <li>Semantic Scholar API integration for citation metrics and related papers</li>
     *   <li>Author disambiguation and affiliation resolution</li>
     *   <li>Publication venue verification and impact factor lookup</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.crossref_metadata</strong> - Crossref API response data (JSONB)</li>
     *   <li><strong>papers.semantic_scholar_metadata</strong> - Semantic Scholar data (JSONB)</li>
     *   <li><strong>papers.crossref_verified</strong> - Verification status flag</li>
     *   <li><strong>papers.semantic_scholar_verified</strong> - Verification status flag</li>
     *   <li><strong>papers.doi</strong> - Verified DOI if found</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "METADATA_ENHANCED"</li>
     *   <li><strong>user_operations</strong> - Creates METADATA_ENHANCEMENT operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 2/3/4 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>OpenAI GPT-4</strong> - Excellent for API integration and metadata synthesis
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
     * <p>Generates comprehensive summaries at multiple abstraction levels using Anthropic Claude,
     * optimized for different audiences and use cases from brief overviews to detailed analyses.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Brief summary generation (1-2 sentences for quick overview)</li>
     *   <li>Standard summary creation (1-2 paragraphs with key findings)</li>
     *   <li>Detailed summary with methodology and implications</li>
     *   <li>Key findings extraction and structuring</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.summary_brief</strong> - Concise 1-2 sentence summary</li>
     *   <li><strong>papers.summary_standard</strong> - Standard paragraph summary</li>
     *   <li><strong>papers.summary_detailed</strong> - Comprehensive detailed summary</li>
     *   <li><strong>papers.key_findings</strong> - Structured key findings (JSONB)</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "CONTENT_SUMMARIZED"</li>
     *   <li><strong>user_operations</strong> - Creates CONTENT_SUMMARIZATION operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 4/6/8 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>Anthropic Claude</strong> - Superior summarization capabilities with length control
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
     * <p>Identifies technical terms and creates comprehensive explanations and glossaries
     * tailored for different education levels, making complex research accessible.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Technical term identification and extraction</li>
     *   <li>Multi-level concept explanations (undergraduate, graduate, expert)</li>
     *   <li>Glossary creation with definitions and analogies</li>
     *   <li>Topic classification and tagging</li>
     *   <li>Concept relationship mapping</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.glossary</strong> - Technical term definitions and explanations (JSONB)</li>
     *   <li><strong>papers.main_concepts</strong> - Core concepts and relationships (JSONB)</li>
     *   <li><strong>papers.topics</strong> - Topic classifications and tags (JSONB array)</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "CONCEPTS_EXPLAINED"</li>
     *   <li><strong>user_operations</strong> - Creates CONCEPT_EXPLANATION operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 3/5/7 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>OpenAI GPT-4</strong> - Exceptional ability to simplify complex concepts
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
     * <p>Performs external research using Perplexity's real-time web search capabilities
     * to discover related work, verify claims, and provide current context.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Related research discovery and analysis</li>
     *   <li>Fact verification against current literature</li>
     *   <li>Research trend identification and contextualization</li>
     *   <li>Methodology analysis and research question extraction</li>
     *   <li>Expert opinion gathering and consensus analysis</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.research_questions</strong> - Extracted research questions (JSONB)</li>
     *   <li><strong>papers.methodology_details</strong> - Methodology analysis (JSONB)</li>
     *   <li><strong>papers.metadata.research_context</strong> - External research findings</li>
     *   <li><strong>papers.metadata.related_papers</strong> - Related work discoveries</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "RESEARCH_COMPLETED"</li>
     *   <li><strong>user_operations</strong> - Creates RESEARCH_DISCOVERY operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 5/7/10 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>Perplexity</strong> - Specialized for real-time web search and research synthesis
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
     * <p>Discovers related academic papers through multiple sources including Crossref, Semantic Scholar, 
     * and AI-powered analysis to provide comprehensive research context and citations.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Multi-source paper discovery (Crossref, Semantic Scholar, Perplexity)</li>
     *   <li>Relevance scoring and ranking of discovered papers</li>
     *   <li>AI-powered synthesis of related work insights</li>
     *   <li>Citation network analysis and relationship mapping</li>
     *   <li>Research trend identification within related papers</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>discovered_papers</strong> - Individual discovered papers with metadata</li>
     *   <li><strong>discovery_results</strong> - Discovery session results and statistics</li>
     *   <li><strong>papers.metadata.discovered_papers</strong> - Related papers list (JSONB)</li>
     *   <li><strong>papers.metadata.research_context</strong> - Research context and insights</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "RELATED_PAPERS_DISCOVERED"</li>
     *   <li><strong>user_operations</strong> - Creates RESEARCH_DISCOVERY operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 5/7/10 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>Multiple Providers</strong> - Crossref/Semantic Scholar APIs + AI synthesis
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
     * <p>Extracts, validates, and formats citations in multiple academic styles,
     * creating comprehensive bibliographies with DOI validation and metadata enhancement.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Citation extraction from reference sections</li>
     *   <li>Citation parsing and structure identification</li>
     *   <li>DOI validation and metadata lookup</li>
     *   <li>Multi-format bibliography generation (APA, MLA, Chicago, IEEE)</li>
     *   <li>Citation quality assessment and validation</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.citations</strong> - Structured citation data and bibliographies (JSONB)</li>
     *   <li><strong>papers.references_count</strong> - Total number of references found</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "CITATIONS_FORMATTED"</li>
     *   <li><strong>user_operations</strong> - Creates CITATION_FORMATTING operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 2/3/4 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>OpenAI GPT-4</strong> - Superior structured output generation and pattern recognition
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
     * Step 8: Quality Assessment and Final Validation
     * 
     * <p>Performs comprehensive quality assessment of all generated content,
     * validates accuracy, detects hallucinations, and provides quality scores and recommendations.</p>
     * 
     * <h3>Core Operations</h3>
     * <ul>
     *   <li>Content accuracy verification against source material</li>
     *   <li>Hallucination detection and validation</li>
     *   <li>Consistency checking across all generated content</li>
     *   <li>Bias detection and assessment</li>
     *   <li>Overall quality scoring and grading</li>
     *   <li>Improvement recommendations generation</li>
     * </ul>
     * 
     * <h3>Database Tables Updated</h3>
     * <ul>
     *   <li><strong>papers.quality_feedback</strong> - Detailed quality assessment (JSONB)</li>
     *   <li><strong>papers.quality_score</strong> - Overall quality score (0-10)</li>
     *   <li><strong>papers.processing_status</strong> - Updated to "COMPLETED"</li>
     *   <li><strong>papers.status</strong> - Updated to "PROCESSED"</li>
     *   <li><strong>papers.updated_at</strong> - Final processing timestamp</li>
     *   <li><strong>user_operations</strong> - Creates QUALITY_CHECKING operation record</li>
     *   <li><strong>credit_transactions</strong> - Deducts 4/6/8 credits (Basic/Pro/Scholar)</li>
     *   <li><strong>credit_balances</strong> - Updates user credit balance</li>
     * </ul>
     * 
     * <h3>AI Provider Used</h3>
     * <strong>Anthropic Claude</strong> - Reduced hallucination rates and superior fact-checking
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

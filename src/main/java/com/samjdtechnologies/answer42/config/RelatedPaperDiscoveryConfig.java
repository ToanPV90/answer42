package com.samjdtechnologies.answer42.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.repository.AgentMemoryStoreRepository;
import com.samjdtechnologies.answer42.repository.DiscoveredPaperRepository;
import com.samjdtechnologies.answer42.repository.PaperRelationshipRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.agent.RelatedPaperDiscoveryAgent;
import com.samjdtechnologies.answer42.service.discovery.DiscoveryCoordinator;
import com.samjdtechnologies.answer42.service.discovery.cache.DiscoveryCache;
import com.samjdtechnologies.answer42.service.discovery.ratelimit.APIRateLimitManager;
import com.samjdtechnologies.answer42.service.discovery.sources.CrossrefDiscoveryService;
import com.samjdtechnologies.answer42.service.discovery.sources.PerplexityDiscoveryService;
import com.samjdtechnologies.answer42.service.discovery.sources.SemanticScholarDiscoveryService;
import com.samjdtechnologies.answer42.service.discovery.synthesis.AISynthesisEngine;
import com.samjdtechnologies.answer42.service.helpers.SemanticScholarApiHelper;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;

/**
 * Configuration class for the Related Paper Discovery Agent and all its dependencies.
 * 
 * This configuration wires together all the components needed for comprehensive 
 * related paper discovery including:
 * - Multi-source discovery services (Crossref, Semantic Scholar, Perplexity)
 * - AI-powered synthesis and ranking
 * - Rate limiting and caching
 * - Discovery coordination and orchestration
 * 
 * All components are configured for production use with proper dependency injection,
 * error handling, and resource management.
 */
@Configuration
public class RelatedPaperDiscoveryConfig {

    /**
     * Creates the main Related Paper Discovery Agent with all dependencies.
     * This is the primary entry point for paper discovery functionality.
     * 
     * @param aiConfig AI configuration for model access
     * @param threadConfig Thread pool configuration
     * @param retryPolicy Agent retry policy for error handling
     * @param rateLimiter API rate limiter for resource management
     * @param discoveryCoordinator Orchestrates multi-source discovery
     * @param paperRepository Repository for paper data access
     * @return Fully configured RelatedPaperDiscoveryAgent
     */
    @Bean
    public RelatedPaperDiscoveryAgent relatedPaperDiscoveryAgent(
            AIConfig aiConfig,
            ThreadConfig threadConfig,
            AgentRetryPolicy retryPolicy,
            APIRateLimiter rateLimiter,
            DiscoveryCoordinator discoveryCoordinator,
            PaperRepository paperRepository,
            DiscoveredPaperRepository discoveredPaperRepository,
            PaperRelationshipRepository paperRelationshipRepository) {
        
        return new RelatedPaperDiscoveryAgent(
            aiConfig, 
            threadConfig, 
            retryPolicy,
            rateLimiter,
            discoveryCoordinator, 
            paperRepository,
            discoveredPaperRepository,
            paperRelationshipRepository
        );
    }

    /**
     * Creates the Discovery Coordinator that orchestrates multi-source discovery.
     * Manages parallel execution, result aggregation, and error handling.
     * 
     * @param crossrefService Crossref API discovery service
     * @param semanticScholarService Semantic Scholar API discovery service
     * @param perplexityService Perplexity API discovery service
     * @param synthesisEngine AI synthesis and ranking engine
     * @param threadConfig Thread configuration for async execution
     * @return Configured DiscoveryCoordinator
     */
    @Bean
    public DiscoveryCoordinator discoveryCoordinator(
            CrossrefDiscoveryService crossrefService,
            SemanticScholarDiscoveryService semanticScholarService,
            PerplexityDiscoveryService perplexityService,
            AISynthesisEngine synthesisEngine,
            ThreadConfig threadConfig) {
        
        return new DiscoveryCoordinator(
            crossrefService,
            semanticScholarService,
            perplexityService,
            synthesisEngine,
            threadConfig
        );
    }

    /**
     * Creates the Crossref Discovery Service for citation network analysis.
     * Handles forward/backward citations, author networks, and venue discovery.
     * 
     * @param restTemplate HTTP client for API calls
     * @param threadConfig Thread configuration for async execution
     * @return Configured CrossrefDiscoveryService
     */
    @Bean
    public CrossrefDiscoveryService crossrefDiscoveryService(
            RestTemplate discoveryRestTemplate,
            ThreadConfig threadConfig) {
        
        return new CrossrefDiscoveryService(discoveryRestTemplate, threadConfig);
    }

    /**
     * Creates the Semantic Scholar Discovery Service for semantic analysis.
     * Handles similarity analysis, influence metrics, and field classification.
     * 
     * @param semanticScholarApiHelper API helper for Semantic Scholar
     * @param threadConfig Thread configuration for async execution
     * @return Configured SemanticScholarDiscoveryService
     */
    @Bean
    public SemanticScholarDiscoveryService semanticScholarDiscoveryService(
            SemanticScholarApiHelper semanticScholarApiHelper,
            ThreadConfig threadConfig) {
        
        return new SemanticScholarDiscoveryService(semanticScholarApiHelper, threadConfig);
    }

    /**
     * Creates the Perplexity Discovery Service for real-time trend analysis.
     * Handles current discussions, open access discovery, and trend monitoring.
     * 
     * @param perplexityChatClient Perplexity ChatClient for AI queries
     * @param threadConfig Thread configuration for async execution
     * @return Configured PerplexityDiscoveryService
     */
    @Bean
    public PerplexityDiscoveryService perplexityDiscoveryService(
            @Qualifier("perplexityChatClient") ChatClient perplexityChatClient,
            ThreadConfig threadConfig) {
        
        return new PerplexityDiscoveryService(perplexityChatClient, threadConfig);
    }


    /**
     * Creates the API Rate Limit Manager for resource management.
     * Handles per-API rate limiting, circuit breaking, and usage tracking.
     * 
     * @return Configured APIRateLimitManager
     */
    @Bean
    public APIRateLimitManager apiRateLimitManager() {
        return new APIRateLimitManager();
    }

    /**
     * Creates the Discovery Cache for performance optimization.
     * Provides multi-level caching with intelligent eviction.
     * 
     * @param memoryRepository Repository for persistent cache storage
     * @param objectMapper JSON serialization mapper
     * @return Configured DiscoveryCache
     */
    @Bean
    public DiscoveryCache discoveryCache(
            AgentMemoryStoreRepository memoryRepository,
            ObjectMapper objectMapper) {
        
        return new DiscoveryCache(memoryRepository, objectMapper);
    }

    /**
     * Creates the Semantic Scholar API Helper for structured API access.
     * Handles authentication, request formatting, and response parsing.
     * 
     * @param restTemplate HTTP client for API calls
     * @return Configured SemanticScholarApiHelper
     */
    @Bean
    public SemanticScholarApiHelper semanticScholarApiHelper(RestTemplate discoveryRestTemplate) {
        return new SemanticScholarApiHelper(discoveryRestTemplate);
    }

    /**
     * Creates a REST template configured for external API calls.
     * Includes timeout settings and error handling for discovery services.
     * 
     * @return Configured RestTemplate
     */
    @Bean
    public RestTemplate discoveryRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30 seconds
        factory.setReadTimeout(60000);    // 60 seconds
        
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }

    // ============================================================================
    // Static Configuration Methods
    // ============================================================================

    /**
     * Creates the default discovery configuration for the Related Paper Discovery Agent.
     * This configuration optimizes for comprehensive discovery with balanced performance.
     * 
     * @return A DiscoveryConfiguration with production-ready settings
     */
    public static DiscoveryConfiguration getDefaultDiscoveryConfiguration() {
        return DiscoveryConfiguration.defaultConfig().toBuilder()
            .maxPapersPerSource(50)
            .minimumRelevanceScore(0.3)
            .timeoutSeconds(480) // 8 minutes
            .build();
    }

    /**
     * Creates a comprehensive discovery configuration for thorough research.
     * Use this for in-depth analysis requiring maximum paper coverage.
     * 
     * @return A DiscoveryConfiguration optimized for comprehensive discovery
     */
    public static DiscoveryConfiguration getComprehensiveDiscoveryConfiguration() {
        return DiscoveryConfiguration.comprehensiveConfig().toBuilder()
            .maxPapersPerSource(75)
            .minimumRelevanceScore(0.2)
            .timeoutSeconds(600) // 10 minutes
            .build();
    }

    /**
     * Creates a fast discovery configuration for quick results.
     * Use this for rapid citation network analysis with reduced scope.
     * 
     * @return A DiscoveryConfiguration optimized for speed
     */
    public static DiscoveryConfiguration getFastDiscoveryConfiguration() {
        return DiscoveryConfiguration.fastConfig().toBuilder()
            .maxPapersPerSource(20)
            .minimumRelevanceScore(0.5)
            .timeoutSeconds(120) // 2 minutes
            .build();
    }

    /**
     * Creates a citation-focused discovery configuration.
     * Use this for comprehensive citation network analysis.
     * 
     * @return A DiscoveryConfiguration optimized for citation analysis
     */
    public static DiscoveryConfiguration getCitationFocusedConfiguration() {
        return DiscoveryConfiguration.citationFocusedConfig().toBuilder()
            .maxPapersPerSource(60)
            .minimumRelevanceScore(0.4)
            .timeoutSeconds(360) // 6 minutes
            .build();
    }

    // ============================================================================
    // Environment-specific Configuration Helpers
    // ============================================================================

    /**
     * Creates a development-friendly discovery configuration.
     * Reduced timeouts and limits for faster development cycles.
     * 
     * @return A DiscoveryConfiguration optimized for development
     */
    public static DiscoveryConfiguration getDevelopmentConfiguration() {
        return DiscoveryConfiguration.fastConfig().toBuilder()
            .maxPapersPerSource(10)
            .maxTotalPapers(25)
            .minimumRelevanceScore(0.6)
            .timeoutSeconds(60) // 1 minute
            .parallelExecution(false) // Easier debugging
            .build();
    }

    /**
     * Creates a production-ready discovery configuration with optimal settings.
     * Balanced for performance, quality, and resource efficiency.
     * 
     * @return A DiscoveryConfiguration optimized for production use
     */
    public static DiscoveryConfiguration getProductionConfiguration() {
        return DiscoveryConfiguration.defaultConfig().toBuilder()
            .maxPapersPerSource(40)
            .maxTotalPapers(100)
            .minimumRelevanceScore(0.35)
            .timeoutSeconds(420) // 7 minutes
            .parallelExecution(true)
            .enableAISynthesis(true)
            .build();
    }

    /**
     * Creates a high-volume discovery configuration for batch processing.
     * Optimized for processing multiple papers efficiently.
     * 
     * @return A DiscoveryConfiguration optimized for batch processing
     */
    public static DiscoveryConfiguration getBatchProcessingConfiguration() {
        return DiscoveryConfiguration.fastConfig().toBuilder()
            .maxPapersPerSource(30)
            .maxTotalPapers(75)
            .minimumRelevanceScore(0.4)
            .timeoutSeconds(300) // 5 minutes
            .parallelExecution(true)
            .enableAISynthesis(false) // Skip AI synthesis for speed
            .build();
    }
}

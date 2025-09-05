package com.samjdtechnologies.answer42.service.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaperResult;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.service.discovery.sources.CrossrefDiscoveryService;
import com.samjdtechnologies.answer42.service.discovery.sources.PerplexityDiscoveryService;
import com.samjdtechnologies.answer42.service.discovery.sources.SemanticScholarDiscoveryService;
import com.samjdtechnologies.answer42.service.discovery.synthesis.AISynthesisEngine;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Discovery Coordinator - orchestrates multi-source paper discovery.
 * Coordinates parallel discovery from multiple sources and synthesizes results.
 */
@Service
public class DiscoveryCoordinator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DiscoveryCoordinator.class);

    private final CrossrefDiscoveryService crossrefService;
    private final SemanticScholarDiscoveryService semanticScholarService;
    private final PerplexityDiscoveryService perplexityService;
    private final AISynthesisEngine synthesisEngine;
    private final ThreadConfig threadConfig;

    public DiscoveryCoordinator(
            CrossrefDiscoveryService crossrefService,
            SemanticScholarDiscoveryService semanticScholarService,
            PerplexityDiscoveryService perplexityService,
            AISynthesisEngine synthesisEngine,
            ThreadConfig threadConfig) {
        this.crossrefService = crossrefService;
        this.semanticScholarService = semanticScholarService;
        this.perplexityService = perplexityService;
        this.synthesisEngine = synthesisEngine;
        this.threadConfig = threadConfig;
    }

    /**
     * Coordinates discovery across multiple sources according to configuration.
     */
    public CompletableFuture<RelatedPaperDiscoveryResult> coordinateDiscovery(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        Instant startTime = Instant.now();
        
        LoggingUtil.info(LOG, "coordinateDiscovery", 
            "Starting coordinated discovery for paper %s with config: %s", 
            sourcePaper.getId(), config.getConfigurationSummary());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Execute discovery from enabled sources with separate futures
                List<DiscoveredPaperResult> crossrefPapers = new ArrayList<>();
                List<DiscoveredPaperResult> semanticScholarPapers = new ArrayList<>();
                List<DiscoveredPaperResult> perplexityPapers = new ArrayList<>();

                // Create futures for each enabled source
                List<CompletableFuture<Void>> sourceFutures = new ArrayList<>();

                // Calculate timeout for individual services (give each service 1/3 of total timeout)
                int serviceTimeoutSeconds = Math.max(30, config.getEffectiveTimeoutSeconds() / 3);
                
                // Crossref discovery with timeout
                if (config.isSourceEnabled(DiscoverySource.CROSSREF)) {
                    CompletableFuture<Void> crossrefFuture = 
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return crossrefService.discoverRelatedPapers(sourcePaper, config);
                            } catch (Exception e) {
                                LoggingUtil.error(LOG, "coordinateDiscovery", 
                                    "Crossref discovery failed", e);
                                return List.<DiscoveredPaperResult>of();
                            }
                        }, threadConfig.taskExecutor())
                        .orTimeout(serviceTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            LoggingUtil.warn(LOG, "coordinateDiscovery", 
                                "Crossref discovery timed out or failed: %s", ex.getMessage());
                            return List.<DiscoveredPaperResult>of();
                        })
                        .thenAccept(crossrefPapers::addAll);
                    sourceFutures.add(crossrefFuture);
                }

                // Semantic Scholar discovery with timeout
                if (config.isSourceEnabled(DiscoverySource.SEMANTIC_SCHOLAR)) {
                    CompletableFuture<Void> semanticFuture = 
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return semanticScholarService.discoverRelatedPapers(sourcePaper, config);
                            } catch (Exception e) {
                                LoggingUtil.error(LOG, "coordinateDiscovery", 
                                    "Semantic Scholar discovery failed", e);
                                return List.<DiscoveredPaperResult>of();
                            }
                        }, threadConfig.taskExecutor())
                        .orTimeout(serviceTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            LoggingUtil.warn(LOG, "coordinateDiscovery", 
                                "Semantic Scholar discovery timed out or failed: %s", ex.getMessage());
                            return List.<DiscoveredPaperResult>of();
                        })
                        .thenAccept(semanticScholarPapers::addAll);
                    sourceFutures.add(semanticFuture);
                }

                // Perplexity discovery with timeout
                if (config.isSourceEnabled(DiscoverySource.PERPLEXITY)) {
                    CompletableFuture<Void> perplexityFuture = 
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return perplexityService.discoverRelatedPapers(sourcePaper, config);
                            } catch (Exception e) {
                                LoggingUtil.error(LOG, "coordinateDiscovery", 
                                    "Perplexity discovery failed", e);
                                return List.<DiscoveredPaperResult>of();
                            }
                        }, threadConfig.taskExecutor())
                        .orTimeout(serviceTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            LoggingUtil.warn(LOG, "coordinateDiscovery", 
                                "Perplexity discovery timed out or failed: %s", ex.getMessage());
                            return List.<DiscoveredPaperResult>of();
                        })
                        .thenAccept(perplexityPapers::addAll);
                    sourceFutures.add(perplexityFuture);
                }

                // Wait for all sources to complete with overall timeout
                try {
                    CompletableFuture.allOf(sourceFutures.toArray(new CompletableFuture[0]))
                        .orTimeout(config.getEffectiveTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                        .join();
                } catch (Exception e) {
                    LoggingUtil.warn(LOG, "coordinateDiscovery", 
                        "Some discovery services timed out, continuing with available results: %s", e.getMessage());
                    // Continue processing with whatever results we have
                }

                LoggingUtil.info(LOG, "coordinateDiscovery", 
                    "Discovered %d Crossref, %d Semantic Scholar, %d Perplexity papers", 
                    crossrefPapers.size(), semanticScholarPapers.size(), perplexityPapers.size());

                // Apply AI synthesis if enabled
                RelatedPaperDiscoveryResult result;
                if (config.isAISynthesisEnabled() && 
                    (!crossrefPapers.isEmpty() || !semanticScholarPapers.isEmpty() || !perplexityPapers.isEmpty())) {
                    
                    result = synthesisEngine.synthesizeResults(
                        sourcePaper, crossrefPapers, semanticScholarPapers, perplexityPapers, config);
                    
                    LoggingUtil.info(LOG, "coordinateDiscovery", 
                        "AI synthesis completed: %d final papers", result.getDiscoveredPapers().size());
                } else {
                    // Simple combination without AI synthesis
                    List<DiscoveredPaperResult> allPapers = new ArrayList<>();
                    allPapers.addAll(crossrefPapers);
                    allPapers.addAll(semanticScholarPapers);
                    allPapers.addAll(perplexityPapers);
                    
                    result = RelatedPaperDiscoveryResult.success(
                        sourcePaper.getId(), allPapers, config);
                }

                return result;

            } catch (Exception e) {
                LoggingUtil.error(LOG, "coordinateDiscovery", 
                    "Discovery coordination failed for paper %s", e, sourcePaper.getId());
                
                return RelatedPaperDiscoveryResult.partial(
                    sourcePaper.getId(), 
                    List.of(), 
                    List.of("Discovery coordination failed: " + e.getMessage()),
                    List.of());
            }
        }, threadConfig.taskExecutor());
    }
}

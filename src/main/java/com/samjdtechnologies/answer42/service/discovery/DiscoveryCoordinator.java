package com.samjdtechnologies.answer42.service.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaper;
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
                List<DiscoveredPaper> crossrefPapers = new ArrayList<>();
                List<DiscoveredPaper> semanticScholarPapers = new ArrayList<>();
                List<DiscoveredPaper> perplexityPapers = new ArrayList<>();

                // Create futures for each enabled source
                List<CompletableFuture<Void>> sourceFutures = new ArrayList<>();

                // Crossref discovery
                if (config.isSourceEnabled(DiscoverySource.CROSSREF)) {
                    CompletableFuture<Void> crossrefFuture = 
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return crossrefService.discoverRelatedPapers(sourcePaper, config);
                            } catch (Exception e) {
                                LoggingUtil.error(LOG, "coordinateDiscovery", 
                                    "Crossref discovery failed", e);
                                return List.<DiscoveredPaper>of();
                            }
                        }, threadConfig.taskExecutor())
                        .thenAccept(crossrefPapers::addAll);
                    sourceFutures.add(crossrefFuture);
                }

                // Semantic Scholar discovery
                if (config.isSourceEnabled(DiscoverySource.SEMANTIC_SCHOLAR)) {
                    CompletableFuture<Void> semanticFuture = 
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return semanticScholarService.discoverRelatedPapers(sourcePaper, config);
                            } catch (Exception e) {
                                LoggingUtil.error(LOG, "coordinateDiscovery", 
                                    "Semantic Scholar discovery failed", e);
                                return List.<DiscoveredPaper>of();
                            }
                        }, threadConfig.taskExecutor())
                        .thenAccept(semanticScholarPapers::addAll);
                    sourceFutures.add(semanticFuture);
                }

                // Perplexity discovery
                if (config.isSourceEnabled(DiscoverySource.PERPLEXITY)) {
                    CompletableFuture<Void> perplexityFuture = 
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return perplexityService.discoverRelatedPapers(sourcePaper, config);
                            } catch (Exception e) {
                                LoggingUtil.error(LOG, "coordinateDiscovery", 
                                    "Perplexity discovery failed", e);
                                return List.<DiscoveredPaper>of();
                            }
                        }, threadConfig.taskExecutor())
                        .thenAccept(perplexityPapers::addAll);
                    sourceFutures.add(perplexityFuture);
                }

                // Wait for all sources to complete
                CompletableFuture.allOf(sourceFutures.toArray(new CompletableFuture[0])).join();

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
                    List<DiscoveredPaper> allPapers = new ArrayList<>();
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

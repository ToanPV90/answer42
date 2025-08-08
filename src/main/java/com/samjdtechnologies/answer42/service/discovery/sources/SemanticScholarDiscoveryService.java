package com.samjdtechnologies.answer42.service.discovery.sources;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaperResult;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.service.helpers.SemanticScholarApiHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Semantic Scholar Discovery Service - integrates with Semantic Scholar API for semantic analysis.
 * Provides advanced discovery through semantic similarity, influence scoring, and research field classification.
 */
@Service
public class SemanticScholarDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticScholarDiscoveryService.class);

    private final SemanticScholarApiHelper apiHelper;
    private final ThreadConfig threadConfig;

    public SemanticScholarDiscoveryService(SemanticScholarApiHelper apiHelper, ThreadConfig threadConfig) {
        this.apiHelper = apiHelper;
        this.threadConfig = threadConfig;
    }

    /**
     * Discover related papers using Semantic Scholar API with comprehensive discovery strategies.
     */
    public List<DiscoveredPaperResult> discoverRelatedPapers(Paper sourcePaper, DiscoveryConfiguration config) {
        LoggingUtil.info(LOG, "discoverRelatedPapers", 
            "Starting Semantic Scholar discovery for paper %s", sourcePaper.getId());

        Instant startTime = Instant.now();
        List<DiscoveredPaperResult> allDiscovered = new ArrayList<>();

        try {
            // Execute multiple discovery strategies in parallel
            CompletableFuture<List<DiscoveredPaperResult>> semanticSimilarityFuture = 
                discoverSemanticSimilarityAsync(sourcePaper, config);
            
            CompletableFuture<List<DiscoveredPaperResult>> citationNetworkFuture = 
                discoverCitationNetworkAsync(sourcePaper, config);

            CompletableFuture<List<DiscoveredPaperResult>> authorNetworkFuture = 
                discoverAuthorNetworkAsync(sourcePaper, config);

            CompletableFuture<List<DiscoveredPaperResult>> recommendationsFuture = 
                discoverRecommendationsAsync(sourcePaper, config);

            // Wait for all discovery operations with timeout
            CompletableFuture.allOf(semanticSimilarityFuture, citationNetworkFuture, 
                                   authorNetworkFuture, recommendationsFuture)
                .orTimeout(config.getEffectiveTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .join();

            // Collect results from all strategies
            allDiscovered.addAll(semanticSimilarityFuture.join());
            allDiscovered.addAll(citationNetworkFuture.join());
            allDiscovered.addAll(authorNetworkFuture.join());
            allDiscovered.addAll(recommendationsFuture.join());

            // Calculate relevance scores for all discovered papers
            apiHelper.calculateRelevanceScores(allDiscovered, sourcePaper);

            // Remove duplicates based on DOI or title
            allDiscovered = removeDuplicates(allDiscovered);

            LoggingUtil.info(LOG, "discoverRelatedPapers", 
                "Completed Semantic Scholar discovery for paper %s: found %d related papers in %dms", 
                sourcePaper.getId(), allDiscovered.size(), 
                Duration.between(startTime, Instant.now()).toMillis());

            return allDiscovered;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverRelatedPapers", 
                "Semantic Scholar discovery failed for paper %s", e, sourcePaper.getId());
            return allDiscovered; // Return partial results
        }
    }

    /**
     * Discover papers through semantic similarity analysis.
     */
    private CompletableFuture<List<DiscoveredPaperResult>> discoverSemanticSimilarityAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaperResult> similarPapers = new ArrayList<>();

            try {
                int maxResults = config.getMaxPapersForSource(DiscoverySource.SEMANTIC_SCHOLAR) / 4;
                
                // Search by title for semantic similarity
                List<DiscoveredPaperResult> titleSearch = apiHelper.searchByTitle(
                    sourcePaper.getTitle(), config, maxResults);
                similarPapers.addAll(titleSearch);

                LoggingUtil.debug(LOG, "discoverSemanticSimilarityAsync", 
                    "Found %d semantically similar papers for %s", similarPapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverSemanticSimilarityAsync", 
                    "Semantic similarity discovery failed for paper %s", e, sourcePaper.getId());
            }

            return similarPapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Discover papers through citation network analysis.
     */
    private CompletableFuture<List<DiscoveredPaperResult>> discoverCitationNetworkAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaperResult> citationPapers = new ArrayList<>();

            try {
                // Only proceed if we have a DOI to work with
                if (sourcePaper.getDoi() != null && !sourcePaper.getDoi().trim().isEmpty()) {
                    // Try to get the paper from Semantic Scholar by DOI first
                    DiscoveredPaperResult semanticScholarPaper = apiHelper.getPaperByDoi(sourcePaper.getDoi(), config);
                    
                    if (semanticScholarPaper != null && semanticScholarPaper.getId() != null) {
                        int maxResults = config.getMaxPapersForSource(DiscoverySource.SEMANTIC_SCHOLAR) / 8;
                        
                        // Get papers that cite this one
                        List<DiscoveredPaperResult> citations = apiHelper.getPaperCitations(
                            semanticScholarPaper.getId(), config, maxResults);
                        citationPapers.addAll(citations);

                        // Get papers that this one cites
                        List<DiscoveredPaperResult> references = apiHelper.getPaperReferences(
                            semanticScholarPaper.getId(), config, maxResults);
                        citationPapers.addAll(references);
                    }
                }

                LoggingUtil.debug(LOG, "discoverCitationNetworkAsync", 
                    "Found %d citation network papers for %s", citationPapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverCitationNetworkAsync", 
                    "Citation network discovery failed for paper %s", e, sourcePaper.getId());
            }

            return citationPapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Discover papers through author network analysis.
     */
    private CompletableFuture<List<DiscoveredPaperResult>> discoverAuthorNetworkAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaperResult> authorPapers = new ArrayList<>();

            try {
                if (sourcePaper.getAuthors() != null && !sourcePaper.getAuthors().isEmpty()) {
                    int maxResults = config.getMaxPapersForSource(DiscoverySource.SEMANTIC_SCHOLAR) / 8;
                    
                    // Get papers by the first author (typically the primary researcher)
                    String firstAuthor = sourcePaper.getAuthors().get(0);
                    List<DiscoveredPaperResult> firstAuthorPapers = apiHelper.findPapersByAuthor(
                        firstAuthor, config, maxResults);
                    authorPapers.addAll(firstAuthorPapers);

                    // If there's a second author, get some of their papers too
                    if (sourcePaper.getAuthors().size() > 1) {
                        String secondAuthor = sourcePaper.getAuthors().get(1);
                        List<DiscoveredPaperResult> secondAuthorPapers = apiHelper.findPapersByAuthor(
                            secondAuthor, config, maxResults / 2);
                        authorPapers.addAll(secondAuthorPapers);
                    }
                }

                LoggingUtil.debug(LOG, "discoverAuthorNetworkAsync", 
                    "Found %d author network papers for %s", authorPapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverAuthorNetworkAsync", 
                    "Author network discovery failed for paper %s", e, sourcePaper.getId());
            }

            return authorPapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Discover papers through Semantic Scholar recommendations.
     */
    private CompletableFuture<List<DiscoveredPaperResult>> discoverRecommendationsAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaperResult> recommendations = new ArrayList<>();

            try {
                // Only proceed if we have a DOI to work with
                if (sourcePaper.getDoi() != null && !sourcePaper.getDoi().trim().isEmpty()) {
                    // Try to get the paper from Semantic Scholar by DOI first
                    DiscoveredPaperResult semanticScholarPaper = apiHelper.getPaperByDoi(sourcePaper.getDoi(), config);
                    
                    if (semanticScholarPaper != null && semanticScholarPaper.getId() != null) {
                        int maxResults = config.getMaxPapersForSource(DiscoverySource.SEMANTIC_SCHOLAR) / 4;
                        
                        // Get Semantic Scholar recommendations
                        List<DiscoveredPaperResult> recs = apiHelper.getPaperRecommendations(
                            semanticScholarPaper.getId(), config, maxResults);
                        recommendations.addAll(recs);
                    }
                }

                LoggingUtil.debug(LOG, "discoverRecommendationsAsync", 
                    "Found %d recommendation papers for %s", recommendations.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverRecommendationsAsync", 
                    "Recommendations discovery failed for paper %s", e, sourcePaper.getId());
            }

            return recommendations;
        }, threadConfig.taskExecutor());
    }

    /**
     * Remove duplicate papers based on DOI or title similarity.
     */
    private List<DiscoveredPaperResult> removeDuplicates(List<DiscoveredPaperResult> papers) {
        List<DiscoveredPaperResult> uniquePapers = new ArrayList<>();
        
        for (DiscoveredPaperResult paper : papers) {
            boolean isDuplicate = false;
            
            for (DiscoveredPaperResult existing : uniquePapers) {
                // Check for DOI match
                if (paper.getDoi() != null && existing.getDoi() != null && 
                    paper.getDoi().equalsIgnoreCase(existing.getDoi())) {
                    isDuplicate = true;
                    break;
                }
                
                // Check for title similarity (basic check)
                if (paper.getTitle() != null && existing.getTitle() != null &&
                    paper.getTitle().equalsIgnoreCase(existing.getTitle())) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                uniquePapers.add(paper);
            }
        }
        
        return uniquePapers;
    }
}

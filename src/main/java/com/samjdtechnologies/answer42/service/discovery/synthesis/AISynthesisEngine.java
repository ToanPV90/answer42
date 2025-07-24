package com.samjdtechnologies.answer42.service.discovery.synthesis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * AI-powered synthesis engine for combining and ranking discovered papers from multiple sources.
 * Uses machine learning and heuristic approaches to create cohesive discovery results.
 */
@Service
public class AISynthesisEngine {

    private static final Logger LOG = LoggerFactory.getLogger(AISynthesisEngine.class);

    private final ChatClient anthropicChatClient;
    private final ThreadConfig threadConfig;

    public AISynthesisEngine(
            @Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
            ThreadConfig threadConfig) {
        this.anthropicChatClient = anthropicChatClient;
        this.threadConfig = threadConfig;
    }

    /**
     * Synthesize discovery results from multiple sources into a unified, ranked result set.
     */
    public RelatedPaperDiscoveryResult synthesizeResults(
            Paper sourcePaper,
            List<DiscoveredPaper> crossrefPapers,
            List<DiscoveredPaper> semanticScholarPapers,
            List<DiscoveredPaper> perplexityPapers,
            DiscoveryConfiguration config) {

        LoggingUtil.info(LOG, "synthesizeResults", 
            "Starting synthesis for paper %s with %d Crossref, %d Semantic Scholar, %d Perplexity papers",
            sourcePaper.getId(), crossrefPapers.size(), semanticScholarPapers.size(), perplexityPapers.size());

        Instant startTime = Instant.now();

        try {
            // Combine all discovered papers
            List<DiscoveredPaper> allPapers = new ArrayList<>();
            allPapers.addAll(crossrefPapers);
            allPapers.addAll(semanticScholarPapers);
            allPapers.addAll(perplexityPapers);

            // Remove duplicates
            List<DiscoveredPaper> uniquePapers = removeDuplicates(allPapers);

            // Enhanced relevance scoring using AI
            List<DiscoveredPaper> scoredPapers = enhanceRelevanceScores(sourcePaper, uniquePapers, config);

            // Quality assessment
            List<DiscoveredPaper> qualityFiltered = filterByQuality(scoredPapers, config);

            // Final ranking
            List<DiscoveredPaper> rankedPapers = rankPapers(qualityFiltered, config);

            // Limit to requested size
            int maxResults = config.getMaxTotalPapers() != null ? config.getMaxTotalPapers() : 100;
            if (rankedPapers.size() > maxResults) {
                rankedPapers = rankedPapers.subList(0, maxResults);
            }

            Instant endTime = Instant.now();
            Long processingTime = java.time.Duration.between(startTime, endTime).toMillis();

            LoggingUtil.info(LOG, "synthesizeResults", 
                "Completed synthesis for paper %s: %d papers after deduplication and ranking",
                sourcePaper.getId(), rankedPapers.size());

            return RelatedPaperDiscoveryResult.builder()
                .sourcePaperId(sourcePaper.getId())
                .discoveredPapers(rankedPapers)
                .discoveryStatistics(createDiscoveryStatistics(crossrefPapers, semanticScholarPapers, perplexityPapers, rankedPapers))
                .discoveryStartTime(startTime)
                .discoveryEndTime(endTime)
                .totalProcessingTimeMs(processingTime)
                .configuration(config)
                .warnings(List.of())
                .errors(List.of())
                .overallConfidenceScore(calculateOverallConfidence(rankedPapers))
                .requiresUserReview(false)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "synthesizeResults", 
                "Synthesis failed for paper %s", e, sourcePaper.getId());

            Instant endTime = Instant.now();
            Long processingTime = java.time.Duration.between(startTime, endTime).toMillis();

            // Return basic result on failure
            return RelatedPaperDiscoveryResult.builder()
                .sourcePaperId(sourcePaper.getId())
                .discoveredPapers(Collections.emptyList())
                .discoveryStatistics(Map.of("total", 0, "errors", 1))
                .discoveryStartTime(startTime)
                .discoveryEndTime(endTime)
                .totalProcessingTimeMs(processingTime)
                .configuration(config)
                .warnings(List.of())
                .errors(List.of(e.getMessage()))
                .overallConfidenceScore(0.0)
                .requiresUserReview(true)
                .build();
        }
    }

    /**
     * Remove duplicate papers based on DOI, title similarity, and other identifying features.
     */
    private List<DiscoveredPaper> removeDuplicates(List<DiscoveredPaper> papers) {
        Map<String, DiscoveredPaper> uniquePapers = new HashMap<>();
        
        for (DiscoveredPaper paper : papers) {
            String key = generateDeduplicationKey(paper);
            
            // Keep the paper with highest relevance score if duplicates found
            if (!uniquePapers.containsKey(key) || 
                paper.getRelevanceScore() > uniquePapers.get(key).getRelevanceScore()) {
                uniquePapers.put(key, paper);
            }
        }
        
        return new ArrayList<>(uniquePapers.values());
    }

    /**
     * Generate a key for deduplication based on DOI, title, and authors.
     */
    private String generateDeduplicationKey(DiscoveredPaper paper) {
        // Primary key: DOI if available
        if (paper.getDoi() != null && !paper.getDoi().trim().isEmpty()) {
            return "doi:" + paper.getDoi().toLowerCase().trim();
        }
        
        // Secondary key: normalized title
        if (paper.getTitle() != null && !paper.getTitle().trim().isEmpty()) {
            String normalizedTitle = paper.getTitle().toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
            
            // Include first author if available for better matching
            String firstAuthor = "";
            if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
                firstAuthor = paper.getAuthors().get(0).toLowerCase()
                    .replaceAll("[^a-z\\s]", "")
                    .trim();
            }
            
            return "title:" + normalizedTitle + "|author:" + firstAuthor;
        }
        
        // Fallback: use paper ID
        return "id:" + (paper.getId() != null ? paper.getId() : paper.hashCode());
    }

    /**
     * Enhance relevance scores using AI analysis.
     */
    private List<DiscoveredPaper> enhanceRelevanceScores(
            Paper sourcePaper, List<DiscoveredPaper> papers, DiscoveryConfiguration config) {
        
        if (papers.isEmpty()) {
            return papers;
        }

        try {
            // Process papers in batches for AI analysis
            int batchSize = 10;
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < papers.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, papers.size());
                List<DiscoveredPaper> batch = papers.subList(i, endIndex);
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> 
                    enhanceRelevanceScoresBatch(sourcePaper, batch), threadConfig.taskExecutor());
                
                futures.add(future);
            }
            
            // Wait for all batches to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "enhanceRelevanceScores", 
                "AI relevance enhancement failed, using original scores", e);
        }
        
        return papers;
    }

    /**
     * Enhance relevance scores for a batch of papers using AI.
     */
    private void enhanceRelevanceScoresBatch(Paper sourcePaper, List<DiscoveredPaper> batch) {
        try {
            String prompt = buildRelevanceAnalysisPrompt(sourcePaper, batch);
            
            String response = anthropicChatClient.prompt()
                .system("You are a research paper relevance analyst. Analyze the relevance of discovered papers to a source paper and provide scores between 0.0 and 1.0.")
                .user(prompt)
                .call()
                .content();
            
            parseAndApplyRelevanceScores(batch, response);
            
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "enhanceRelevanceScoresBatch", 
                "Failed to enhance batch relevance scores", e);
        }
    }

    /**
     * Build prompt for AI relevance analysis.
     */
    private String buildRelevanceAnalysisPrompt(Paper sourcePaper, List<DiscoveredPaper> papers) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Source Paper:\n");
        prompt.append("Title: ").append(sourcePaper.getTitle()).append("\n");
        if (sourcePaper.getPaperAbstract() != null) {
            prompt.append("Abstract: ").append(sourcePaper.getPaperAbstract().substring(0, 
                Math.min(500, sourcePaper.getPaperAbstract().length()))).append("\n");
        }
        prompt.append("\nDiscovered Papers to Analyze:\n");
        
        for (int i = 0; i < papers.size(); i++) {
            DiscoveredPaper paper = papers.get(i);
            prompt.append(i + 1).append(". Title: ").append(paper.getTitle()).append("\n");
            if (paper.getAbstractText() != null) {
                prompt.append("   Abstract: ").append(paper.getAbstractText().substring(0, 
                    Math.min(300, paper.getAbstractText().length()))).append("\n");
            }
            prompt.append("   Relationship: ").append(paper.getRelationshipType().getDisplayName()).append("\n");
        }
        
        prompt.append("\nFor each discovered paper, provide a relevance score (0.0-1.0) based on:");
        prompt.append("\n- Topical similarity");
        prompt.append("\n- Methodological relevance");
        prompt.append("\n- Research field overlap");
        prompt.append("\n- Citation importance");
        prompt.append("\nRespond with: Paper X: Score Y.YY");
        
        return prompt.toString();
    }

    /**
     * Parse AI response and apply relevance scores.
     */
    private void parseAndApplyRelevanceScores(List<DiscoveredPaper> papers, String response) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.matches(".*Paper \\d+:.*\\d+\\.\\d+.*")) {
                    // Extract paper number and score
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        String paperPart = parts[0].trim();
                        String scorePart = parts[1].trim();
                        
                        // Extract paper number
                        String[] paperWords = paperPart.split("\\s+");
                        for (String word : paperWords) {
                            if (word.matches("\\d+")) {
                                int paperIndex = Integer.parseInt(word) - 1;
                                
                                // Extract score
                                String scoreStr = scorePart.replaceAll("[^0-9.]", "");
                                double score = Double.parseDouble(scoreStr);
                                
                                // Apply score if valid
                                if (paperIndex >= 0 && paperIndex < papers.size() && 
                                    score >= 0.0 && score <= 1.0) {
                                    papers.get(paperIndex).setRelevanceScore(score);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "parseAndApplyRelevanceScores", 
                "Failed to parse AI relevance scores", e);
        }
    }

    /**
     * Filter papers by quality criteria.
     */
    private List<DiscoveredPaper> filterByQuality(List<DiscoveredPaper> papers, DiscoveryConfiguration config) {
        return papers.stream()
            .filter(paper -> {
                // Basic quality filters
                if (paper.getTitle() == null || paper.getTitle().trim().length() < 10) {
                    return false;
                }
                
                // Minimum relevance threshold
                if (paper.getRelevanceScore() < config.getEffectiveMinimumRelevanceScore()) {
                    return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * Rank papers by comprehensive scoring algorithm.
     */
    private List<DiscoveredPaper> rankPapers(List<DiscoveredPaper> papers, DiscoveryConfiguration config) {
        return papers.stream()
            .sorted(Comparator.<DiscoveredPaper>comparingDouble(this::calculateFinalScore).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Calculate final ranking score considering multiple factors.
     */
    private double calculateFinalScore(DiscoveredPaper paper) {
        double score = 0.0;
        
        // Relevance score (40% weight)
        score += paper.getRelevanceScore() * 0.4;
        
        // Relationship type importance (25% weight)
        score += paper.getRelationshipType().getImportanceScore() * 0.25;
        
        // Citation impact (20% weight)
        if (paper.getCitationCount() != null && paper.getCitationCount() > 0) {
            // Logarithmic scaling for citation count
            double citationScore = Math.log(paper.getCitationCount() + 1) / Math.log(1000);
            score += Math.min(citationScore, 1.0) * 0.2;
        }
        
        // Data completeness (10% weight)
        double completeness = calculateDataCompleteness(paper);
        score += completeness * 0.1;
        
        // Source reliability (5% weight)
        score += getSourceReliabilityScore(paper.getSource()) * 0.05;
        
        return Math.min(score, 1.0);
    }

    /**
     * Calculate data completeness score.
     */
    private double calculateDataCompleteness(DiscoveredPaper paper) {
        int fields = 0;
        int filledFields = 0;
        
        fields++; if (paper.getTitle() != null && !paper.getTitle().trim().isEmpty()) filledFields++;
        fields++; if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) filledFields++;
        fields++; if (paper.getDoi() != null && !paper.getDoi().trim().isEmpty()) filledFields++;
        fields++; if (paper.getVenue() != null && !paper.getVenue().trim().isEmpty()) filledFields++;
        fields++; if (paper.getYear() != null) filledFields++;
        fields++; if (paper.getAbstractText() != null && !paper.getAbstractText().trim().isEmpty()) filledFields++;
        fields++; if (paper.getUrl() != null && !paper.getUrl().trim().isEmpty()) filledFields++;
        
        return fields > 0 ? (double) filledFields / fields : 0.0;
    }

    /**
     * Get reliability score for discovery source.
     */
    private double getSourceReliabilityScore(DiscoverySource source) {
        return switch (source) {
            case CROSSREF -> 0.95;        // Highly reliable bibliographic data
            case SEMANTIC_SCHOLAR -> 0.90; // High-quality academic data
            case PERPLEXITY -> 0.75;       // Real-time but less verified
            default -> 0.5;                // Unknown source
        };
    }

    /**
     * Create discovery statistics from the papers.
     */
    private Map<String, Integer> createDiscoveryStatistics(
            List<DiscoveredPaper> crossrefPapers,
            List<DiscoveredPaper> semanticScholarPapers,
            List<DiscoveredPaper> perplexityPapers,
            List<DiscoveredPaper> finalResults) {

        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", finalResults.size());
        stats.put("crossref", crossrefPapers.size());
        stats.put("semantic_scholar", semanticScholarPapers.size());
        stats.put("perplexity", perplexityPapers.size());
        stats.put("original_total", crossrefPapers.size() + semanticScholarPapers.size() + perplexityPapers.size());

        // Count by relationship type
        for (DiscoveredPaper paper : finalResults) {
            if (paper.getRelationshipType() != null) {
                String relKey = "relationship_" + paper.getRelationshipType().name().toLowerCase();
                stats.put(relKey, stats.getOrDefault(relKey, 0) + 1);
            }
        }

        return stats;
    }

    /**
     * Calculate overall confidence score from individual paper scores.
     */
    private Double calculateOverallConfidence(List<DiscoveredPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return 0.0;
        }

        double totalScore = papers.stream()
            .filter(paper -> paper.getRelevanceScore() != null)
            .mapToDouble(DiscoveredPaper::getRelevanceScore)
            .average()
            .orElse(0.0);

        return Math.min(1.0, Math.max(0.0, totalScore));
    }
}

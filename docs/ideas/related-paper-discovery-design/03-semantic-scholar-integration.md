# 03. Semantic Scholar Integration - Semantic Analysis & Influence

## Overview

The Semantic Scholar integration provides advanced semantic similarity analysis and influence metrics for Answer42's paper discovery system. Leveraging Semantic Scholar's AI-powered platform with over 200 million academic papers, this integration enables sophisticated relevance scoring, research impact assessment, and field-specific discovery through semantic understanding rather than just bibliographic matching.

## Semantic Scholar API Capabilities

### 🧠 **Core Semantic Discovery**

**Paper Details & Metrics:**
- `/graph/v1/paper/{paper_id}` - Comprehensive paper metadata with semantic features
- `/graph/v1/paper/search?query={query}` - Semantic search with AI-powered relevance
- `/graph/v1/paper/{paper_id}/citations` - Papers citing this work with context
- `/graph/v1/paper/{paper_id}/references` - Referenced papers with relationship strength

**Author Intelligence:**
- `/graph/v1/author/{author_id}` - Author profiles with h-index and impact metrics  
- `/graph/v1/author/search?query={name}` - Author disambiguation and identification
- `/graph/v1/author/{author_id}/papers` - Author's complete publication history

**Semantic Similarity & Influence:**
- `/recommendations/v1/papers/forpaper/{paper_id}` - AI-recommended similar papers
- `/graph/v1/paper/{paper_id}/topics` - Research topic classification
- `/graph/v1/paper/batch` - Batch processing for multiple papers

**Advanced Analytics:**
- Influence metrics (highly influential citations, citation velocity)
- Semantic similarity scores based on content analysis
- Research field classification with confidence scores
- Citation context analysis (background, method, result references)

## Service Architecture

### SemanticScholarDiscoveryService Implementation

```java
@Service
public class SemanticScholarDiscoveryService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SemanticScholarDiscoveryService.class);
    private static final String SEMANTIC_SCHOLAR_BASE_URL = "https://api.semanticscholar.org";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_RECOMMENDATIONS = 50;
    
    private final RestTemplate restTemplate;
    private final APIRateLimitManager rateLimitManager;
    private final SemanticScholarResponseParser responseParser;
    private final SemanticScholarCache semanticCache;
    private final InfluenceMetricsCalculator influenceCalculator;
    
    public SemanticScholarDiscoveryService(
            RestTemplate restTemplate,
            APIRateLimitManager rateLimitManager,
            SemanticScholarResponseParser responseParser,
            SemanticScholarCache semanticCache,
            InfluenceMetricsCalculator influenceCalculator) {
        this.restTemplate = restTemplate;
        this.rateLimitManager = rateLimitManager;
        this.responseParser = responseParser;
        this.semanticCache = semanticCache;
        this.influenceCalculator = influenceCalculator;
    }
    
    /**
     * Comprehensive semantic discovery using Semantic Scholar's AI capabilities
     */
    public SemanticScholarDiscoveryResult discoverRelatedPapers(Paper sourcePaper) {
        LoggingUtil.info(LOG, "discoverRelatedPapers", 
            "Starting Semantic Scholar discovery for paper %s", sourcePaper.getId());
        
        try {
            // Execute parallel semantic discovery strategies
            CompletableFuture<SemanticSimilarityResult> similarityFuture = 
                discoverSemanticSimilarityAsync(sourcePaper);
            
            CompletableFuture<InfluenceAnalysisResult> influenceFuture = 
                analyzeInfluenceMetricsAsync(sourcePaper);
            
            CompletableFuture<TopicClassificationResult> topicsFuture = 
                classifyResearchTopicsAsync(sourcePaper);
            
            CompletableFuture<AuthorInfluenceResult> authorInfluenceFuture = 
                analyzeAuthorInfluenceAsync(sourcePaper);
            
            CompletableFuture<CitationContextResult> contextFuture = 
                analyzeCitationContextAsync(sourcePaper);
            
            // Wait for all semantic analysis operations
            CompletableFuture.allOf(similarityFuture, influenceFuture, topicsFuture, 
                                   authorInfluenceFuture, contextFuture).join();
            
            // Combine and synthesize results
            SemanticScholarDiscoveryResult result = SemanticScholarDiscoveryResult.builder()
                .sourcePaper(sourcePaper)
                .semanticSimilarity(similarityFuture.join())
                .influenceAnalysis(influenceFuture.join())
                .topicClassification(topicsFuture.join())
                .authorInfluence(authorInfluenceFuture.join())
                .citationContext(contextFuture.join())
                .discoveryMetadata(createSemanticMetadata())
                .build();
            
            LoggingUtil.info(LOG, "discoverRelatedPapers", 
                "Completed Semantic Scholar discovery for paper %s: found %d semantically related papers", 
                sourcePaper.getId(), result.getTotalDiscoveredPapers());
            
            return result;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverRelatedPapers", 
                "Semantic Scholar discovery failed for paper %s", e, sourcePaper.getId());
            throw new SemanticScholarDiscoveryException("Semantic discovery operation failed", e);
        }
    }
    
    /**
     * Discover semantically similar papers using AI recommendations
     */
    private CompletableFuture<SemanticSimilarityResult> discoverSemanticSimilarityAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SemanticSimilarityResult.Builder builder = SemanticSimilarityResult.builder();
                
                // Get Semantic Scholar paper ID
                String semanticScholarId = resolveSemanticScholarId(sourcePaper);
                
                if (semanticScholarId != null) {
                    // AI-powered recommendations
                    List<DiscoveredPaper> aiRecommendations = getAIRecommendations(semanticScholarId);
                    builder.aiRecommendations(aiRecommendations);
                    
                    // Content-based similarity
                    List<DiscoveredPaper> contentSimilar = findContentSimilarPapers(sourcePaper);
                    builder.contentSimilar(contentSimilar);
                    
                    // Methodology similarity
                    List<DiscoveredPaper> methodSimilar = findMethodologySimilarPapers(sourcePaper);
                    builder.methodologySimilar(methodSimilar);
                } else {
                    // Fallback to text-based semantic search
                    List<DiscoveredPaper> textBasedResults = performSemanticTextSearch(sourcePaper);
                    builder.textBasedSimilar(textBasedResults);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverSemanticSimilarityAsync", 
                    "Semantic similarity discovery failed", e);
                return SemanticSimilarityResult.failed(e);
            }
        });
    }
    
    /**
     * Get AI-powered paper recommendations from Semantic Scholar
     */
    private List<DiscoveredPaper> getAIRecommendations(String semanticScholarId) {
        // Check cache first
        Optional<List<DiscoveredPaper>> cached = semanticCache.getRecommendations(semanticScholarId);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.SEMANTIC_SCHOLAR).join();
        
        try {
            String url = String.format("%s/recommendations/v1/papers/forpaper/%s?limit=%d", 
                SEMANTIC_SCHOLAR_BASE_URL, semanticScholarId, MAX_RECOMMENDATIONS);
            
            SemanticScholarRecommendationsResponse response = restTemplate.getForObject(
                url, SemanticScholarRecommendationsResponse.class);
            
            if (response != null && response.getRecommendedPapers() != null) {
                List<DiscoveredPaper> recommendations = response.getRecommendedPapers().stream()
                    .map(item -> responseParser.parseToDiscoveredPaper(item, DiscoverySource.SEMANTIC_SCHOLAR_AI_RECOMMENDATION))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                // Calculate semantic similarity scores
                recommendations.forEach(this::calculateSemanticSimilarityScore);
                
                // Cache results
                semanticCache.storeRecommendations(semanticScholarId, recommendations);
                
                return recommendations;
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getAIRecommendations", 
                "Failed to get AI recommendations for paper %s", e, semanticScholarId);
            return new ArrayList<>();
        }
    }
    
    /**
     * Analyze influence metrics and highly influential citations
     */
    private CompletableFuture<InfluenceAnalysisResult> analyzeInfluenceMetricsAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InfluenceAnalysisResult.Builder builder = InfluenceAnalysisResult.builder();
                
                String semanticScholarId = resolveSemanticScholarId(sourcePaper);
                
                if (semanticScholarId != null) {
                    // Get highly influential citations
                    List<DiscoveredPaper> influentialCitations = getHighlyInfluentialCitations(semanticScholarId);
                    builder.highlyInfluentialCitations(influentialCitations);
                    
                    // Analyze citation velocity and trends
                    CitationVelocityAnalysis velocity = analyzeCitationVelocity(semanticScholarId);
                    builder.citationVelocity(velocity);
                    
                    // Find influential papers by similar authors
                    List<DiscoveredPaper> influentialByAuthors = findInfluentialPapersByAuthors(sourcePaper);
                    builder.influentialByAuthors(influentialByAuthors);
                    
                    // Analyze impact in research field
                    FieldImpactAnalysis fieldImpact = analyzeFieldImpact(sourcePaper, semanticScholarId);
                    builder.fieldImpact(fieldImpact);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "analyzeInfluenceMetricsAsync", 
                    "Influence analysis failed", e);
                return InfluenceAnalysisResult.failed(e);
            }
        });
    }
    
    /**
     * Get highly influential citations with context
     */
    private List<DiscoveredPaper> getHighlyInfluentialCitations(String semanticScholarId) {
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.SEMANTIC_SCHOLAR).join();
        
        try {
            String url = String.format("%s/graph/v1/paper/%s/citations?fields=paperId,title,abstract,authors,venue,year,citationCount,influentialCitationCount,isInfluential&limit=%d", 
                SEMANTIC_SCHOLAR_BASE_URL, semanticScholarId, DEFAULT_LIMIT);
            
            SemanticScholarCitationsResponse response = restTemplate.getForObject(
                url, SemanticScholarCitationsResponse.class);
            
            if (response != null && response.getData() != null) {
                return response.getData().stream()
                    .filter(citation -> citation.getIsInfluential() != null && citation.getIsInfluential())
                    .map(citation -> responseParser.parseToDiscoveredPaper(
                        citation.getCitingPaper(), DiscoverySource.SEMANTIC_SCHOLAR_INFLUENTIAL_CITATION))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(DiscoveredPaper::getInfluenceScore).reversed())
                    .collect(Collectors.toList());
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getHighlyInfluentialCitations", 
                "Failed to get influential citations for paper %s", e, semanticScholarId);
            return new ArrayList<>();
        }
    }
    
    /**
     * Classify research topics and find related papers in same topics
     */
    private CompletableFuture<TopicClassificationResult> classifyResearchTopicsAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TopicClassificationResult.Builder builder = TopicClassificationResult.builder();
                
                String semanticScholarId = resolveSemanticScholarId(sourcePaper);
                
                if (semanticScholarId != null) {
                    // Get paper's research topics
                    List<ResearchTopic> topics = getResearchTopics(semanticScholarId);
                    builder.identifiedTopics(topics);
                    
                    // Find papers in same topics
                    Map<ResearchTopic, List<DiscoveredPaper>> topicPapers = new HashMap<>();
                    for (ResearchTopic topic : topics.subList(0, Math.min(3, topics.size()))) {
                        List<DiscoveredPaper> papersInTopic = findPapersInTopic(topic);
                        topicPapers.put(topic, papersInTopic);
                    }
                    builder.papersByTopic(topicPapers);
                    
                    // Analyze topic evolution and trends
                    TopicTrendAnalysis trends = analyzeTopicTrends(topics);
                    builder.topicTrends(trends);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "classifyResearchTopicsAsync", 
                    "Topic classification failed", e);
                return TopicClassificationResult.failed(e);
            }
        });
    }
    
    /**
     * Analyze citation context to understand how papers relate
     */
    private CompletableFuture<CitationContextResult> analyzeCitationContextAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CitationContextResult.Builder builder = CitationContextResult.builder();
                
                String semanticScholarId = resolveSemanticScholarId(sourcePaper);
                
                if (semanticScholarId != null) {
                    // Analyze citation contexts (background, method, result)
                    Map<CitationContext, List<DiscoveredPaper>> contextualCitations = 
                        analyzeCitationContexts(semanticScholarId);
                    builder.citationsByContext(contextualCitations);
                    
                    // Find papers with similar citation patterns
                    List<DiscoveredPaper> similarCitationPatterns = findSimilarCitationPatterns(sourcePaper);
                    builder.similarCitationPatterns(similarCitationPatterns);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "analyzeCitationContextAsync", 
                    "Citation context analysis failed", e);
                return CitationContextResult.failed(e);
            }
        });
    }
    
    /**
     * Resolve Semantic Scholar paper ID from DOI, title, or other identifiers
     */
    private String resolveSemanticScholarId(Paper sourcePaper) {
        // Check cache first
        Optional<String> cachedId = semanticCache.getSemanticScholarId(sourcePaper.getId());
        if (cachedId.isPresent()) {
            return cachedId.get();
        }
        
        // Try DOI lookup first
        if (sourcePaper.getDoi() != null) {
            String id = lookupByDOI(sourcePaper.getDoi());
            if (id != null) {
                semanticCache.storeSemanticScholarId(sourcePaper.getId(), id);
                return id;
            }
        }
        
        // Try title search
        if (sourcePaper.getTitle() != null) {
            String id = lookupByTitle(sourcePaper.getTitle(), sourcePaper.getAuthors());
            if (id != null) {
                semanticCache.storeSemanticScholarId(sourcePaper.getId(), id);
                return id;
            }
        }
        
        return null;
    }
    
    /**
     * Calculate semantic similarity score based on Semantic Scholar metrics
     */
    private void calculateSemanticSimilarityScore(DiscoveredPaper paper) {
        double score = 0.0;
        
        // Base recommendation score from Semantic Scholar AI (0-0.4)
        if (paper.getAdditionalMetadata().containsKey("recommendationScore")) {
            Double recommendationScore = (Double) paper.getAdditionalMetadata().get("recommendationScore");
            score += Math.min(recommendationScore * 0.4, 0.4);
        }
        
        // Citation influence factor (0-0.25)
        if (paper.getInfluentialCitationCount() != null && paper.getCitationCount() != null) {
            double influenceRatio = paper.getInfluentialCitationCount() / (double) Math.max(paper.getCitationCount(), 1);
            score += influenceRatio * 0.25;
        }
        
        // Research field relevance (0-0.2)
        if (paper.getResearchTopics() != null) {
            double topicRelevance = calculateTopicRelevance(paper.getResearchTopics());
            score += topicRelevance * 0.2;
        }
        
        // Citation velocity (recent impact) (0-0.15)
        if (paper.getCitationVelocity() != null) {
            score += Math.min(paper.getCitationVelocity() / 10.0, 0.15);
        }
        
        paper.setRelevanceScore(Math.min(score, 1.0));
    }
}
```

## Advanced Semantic Analysis

### 1. AI-Powered Similarity Scoring

```java
@Component
public class SemanticSimilarityAnalyzer {
    
    /**
     * Calculate semantic similarity using multiple Semantic Scholar signals
     */
    public double calculateSemanticSimilarity(Paper sourcePaper, DiscoveredPaper candidatePaper) {
        double similarity = 0.0;
        
        // Abstract semantic similarity (0-0.35)
        if (sourcePaper.getAbstract() != null && candidatePaper.getAbstractText() != null) {
            double abstractSimilarity = calculateAbstractSimilarity(
                sourcePaper.getAbstract(), candidatePaper.getAbstractText());
            similarity += abstractSimilarity * 0.35;
        }
        
        // Research topic overlap (0-0.25)
        if (sourcePaper.getTopics() != null && candidatePaper.getResearchTopics() != null) {
            double topicOverlap = calculateTopicOverlap(
                sourcePaper.getTopics(), candidatePaper.getResearchTopics());
            similarity += topicOverlap * 0.25;
        }
        
        // Methodology similarity (0-0.2)
        double methodSimilarity = analyzeMethodologySimilarity(sourcePaper, candidatePaper);
        similarity += methodSimilarity * 0.2;
        
        // Citation network similarity (0-0.2)
        double citationSimilarity = analyzeCitationNetworkSimilarity(sourcePaper, candidatePaper);
        similarity += citationSimilarity * 0.2;
        
        return Math.min(similarity, 1.0);
    }
    
    private double calculateAbstractSimilarity(String abstract1, String abstract2) {
        // Use semantic embedding similarity (simplified)
        // In practice, this would use pre-computed embeddings from Semantic Scholar
        List<String> terms1 = extractKeyTerms(abstract1);
        List<String> terms2 = extractKeyTerms(abstract2);
        
        long commonTerms = terms1.stream().filter(terms2::contains).count();
        return commonTerms / (double) Math.max(terms1.size(), terms2.size());
    }
    
    private double analyzeMethodologySimilarity(Paper sourcePaper, DiscoveredPaper candidatePaper) {
        // Extract methodology indicators from paper content
        Set<String> sourceMethods = extractMethodologyIndicators(sourcePaper);
        Set<String> candidateMethods = extractMethodologyIndicators(candidatePaper);
        
        if (sourceMethods.isEmpty() || candidateMethods.isEmpty()) {
            return 0.0;
        }
        
        long commonMethods = sourceMethods.stream().filter(candidateMethods::contains).count();
        return commonMethods / (double) Math.max(sourceMethods.size(), candidateMethods.size());
    }
}
```

### 2. Influence Metrics Calculator

```java
@Component
public class InfluenceMetricsCalculator {
    
    /**
     * Calculate comprehensive influence metrics for discovered papers
     */
    public InfluenceMetrics calculateInfluenceMetrics(DiscoveredPaper paper) {
        InfluenceMetrics.Builder builder = InfluenceMetrics.builder();
        
        // Citation influence score
        double citationInfluence = calculateCitationInfluence(paper);
        builder.citationInfluence(citationInfluence);
        
        // Author influence score
        double authorInfluence = calculateAuthorInfluence(paper);
        builder.authorInfluence(authorInfluence);
        
        // Venue influence score
        double venueInfluence = calculateVenueInfluence(paper);
        builder.venueInfluence(venueInfluence);
        
        // Temporal influence (citation velocity)
        double temporalInfluence = calculateTemporalInfluence(paper);
        builder.temporalInfluence(temporalInfluence);
        
        // Overall influence score
        double overallInfluence = (citationInfluence * 0.4) + (authorInfluence * 0.25) + 
                                 (venueInfluence * 0.2) + (temporalInfluence * 0.15);
        builder.overallInfluence(overallInfluence);
        
        return builder.build();
    }
    
    private double calculateCitationInfluence(DiscoveredPaper paper) {
        if (paper.getCitationCount() == null || paper.getInfluentialCitationCount() == null) {
            return 0.0;
        }
        
        // Combine total citations with influential citation ratio
        double citationScore = Math.min(paper.getCitationCount() / 1000.0, 0.6);
        double influenceRatio = paper.getInfluentialCitationCount() / (double) Math.max(paper.getCitationCount(), 1);
        
        return citationScore + (influenceRatio * 0.4);
    }
    
    private double calculateTemporalInfluence(DiscoveredPaper paper) {
        if (paper.getPublishedDate() == null || paper.getCitationCount() == null) {
            return 0.0;
        }
        
        // Calculate citation velocity (citations per year)
        long yearsFromPublication = ChronoUnit.YEARS.between(paper.getPublishedDate(), LocalDate.now());
        if (yearsFromPublication <= 0) {
            yearsFromPublication = 1; // Handle very recent papers
        }
        
        double citationVelocity = paper.getCitationCount() / (double) yearsFromPublication;
        
        // Normalize and cap the velocity score
        return Math.min(citationVelocity / 50.0, 1.0);
    }
}
```

## Data Models

### SemanticScholarDiscoveryResult

```java
@Data
@Builder
public class SemanticScholarDiscoveryResult {
    private Paper sourcePaper;
    private SemanticSimilarityResult semanticSimilarity;
    private InfluenceAnalysisResult influenceAnalysis;
    private TopicClassificationResult topicClassification;
    private AuthorInfluenceResult authorInfluence;
    private CitationContextResult citationContext;
    private SemanticDiscoveryMetadata discoveryMetadata;
    
    public int getTotalDiscoveredPapers() {
        return semanticSimilarity.getTotalPapers() +
               influenceAnalysis.getTotalPapers() +
               topicClassification.getTotalPapers() +
               authorInfluence.getTotalPapers() +
               citationContext.getTotalPapers();
    }
    
    public List<DiscoveredPaper> getAllDiscoveredPapers() {
        List<DiscoveredPaper> allPapers = new ArrayList<>();
        allPapers.addAll(semanticSimilarity.getAllPapers());
        allPapers.addAll(influenceAnalysis.getAllPapers());
        allPapers.addAll(topicClassification.getAllPapers());
        allPapers.addAll(authorInfluence.getAllPapers());
        allPapers.addAll(citationContext.getAllPapers());
        return allPapers;
    }
}
```

### ResearchTopic

```java
@Data
@Builder
public class ResearchTopic {
    private String topicId;
    private String topic;
    private double confidence;
    private String category;
    private List<String> keywords;
    private int paperCount;
    private double influence;
    
    public boolean isHighConfidence() {
        return confidence > 0.8;
    }
    
    public boolean isInfluentialTopic() {
        return influence > 0.7;
    }
}
```

## Performance Optimization

### Semantic Scholar Caching Strategy

```java
@Component
public class SemanticScholarCache {
    
    private final AgentMemoryStoreRepository memoryRepository;
    private final LoadingCache<String, List<DiscoveredPaper>> recommendationsCache;
    
    public SemanticScholarCache(AgentMemoryStoreRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
        this.recommendationsCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(6))
            .build(this::loadRecommendations);
    }
    
    /**
     * Cache AI recommendations with intelligent expiration
     */
    public Optional<List<DiscoveredPaper>> getRecommendations(String semanticScholarId) {
        try {
            List<DiscoveredPaper> recommendations = recommendationsCache.get(semanticScholarId);
            return Optional.ofNullable(recommendations);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    public void storeRecommendations(String semanticScholarId, List<DiscoveredPaper> recommendations) {
        recommendationsCache.put(semanticScholarId, recommendations);
        
        // Also store in persistent cache
        String cacheKey = "semantic_recommendations_" + semanticScholarId;
        AgentMemoryStore memory = AgentMemoryStore.builder()
            .key(cacheKey)
            .data(serializeRecommendations(recommendations))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        memoryRepository.save(memory);
    }
}
```

## Rate Limiting & Error Handling

### Semantic Scholar Rate Limiter

```java
@Component
public class SemanticScholarRateLimiter {
    
    // Semantic Scholar allows 100 requests per 5 minutes (partner tier)
    private final RateLimiter generalLimiter = RateLimiter.create(100.0 / 300.0); // ~0.33 req/sec
    private final RateLimiter batchLimiter = RateLimiter.create(10.0 / 300.0);    // Slower for batch operations
    
    public void acquirePermit(SemanticScholarRequestType requestType) {
        switch (requestType) {
            case SINGLE_PAPER_LOOKUP:
            case RECOMMENDATIONS:
                generalLimiter.acquire();
                break;
            case BATCH_PAPER_DETAILS:
            case AUTHOR_SEARCH:
                batchLimiter.acquire();
                break;
            default:
                generalLimiter.acquire();
        }
        
        // Add small delay to be respectful
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

This comprehensive Semantic Scholar integration provides AI-powered semantic understanding, enabling Answer42 to discover truly relevant papers based on content similarity rather than just citation overlap, significantly improving discovery quality and user satisfaction.

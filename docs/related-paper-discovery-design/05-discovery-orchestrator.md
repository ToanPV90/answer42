# 05. Discovery Orchestrator - Unified Paper Discovery

## Overview

The Discovery Orchestrator is the central coordination service that unifies the Crossref, Semantic Scholar, and Perplexity integrations into a comprehensive paper discovery system. It manages parallel execution, result synthesis, deduplication, scoring, and intelligent ranking to provide users with the most relevant and diverse set of related papers.

## Orchestrator Architecture

### 🎯 **Core Responsibilities**

**Multi-Source Coordination:**

- Parallel execution of all discovery sources
- Intelligent query planning and optimization
- Load balancing across API providers
- Fallback strategies for API failures

**Result Synthesis:**

- Advanced deduplication using multiple matching strategies
- Relevance scoring with confidence intervals
- Diversity optimization for varied perspectives
- Quality filtering and validation

**Performance Optimization:**

- Adaptive caching strategies
- Rate limit management across providers
- Resource allocation and priority queuing
- Progressive result delivery

## Service Implementation

### RelatedPaperDiscoveryOrchestrator

```java
@Service
@Transactional
public class RelatedPaperDiscoveryOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(RelatedPaperDiscoveryOrchestrator.class);
    private static final int MAX_TOTAL_RESULTS = 100;
    private static final int MAX_RESULTS_PER_SOURCE = 50;
    private static final double MIN_RELEVANCE_THRESHOLD = 0.3;

    private final CrossrefDiscoveryService crossrefService;
    private final SemanticScholarDiscoveryService semanticScholarService;
    private final PerplexityTrendService perplexityService;
    private final DiscoveryResultProcessor resultProcessor;
    private final DiscoveryCache discoveryCache;
    private final DiversityOptimizer diversityOptimizer;
    private final ThreadConfig threadConfig;

    public RelatedPaperDiscoveryOrchestrator(
            CrossrefDiscoveryService crossrefService,
            SemanticScholarDiscoveryService semanticScholarService,
            PerplexityTrendService perplexityService,
            DiscoveryResultProcessor resultProcessor,
            DiscoveryCache discoveryCache,
            DiversityOptimizer diversityOptimizer,
            ThreadConfig threadConfig) {
        this.crossrefService = crossrefService;
        this.semanticScholarService = semanticScholarService;
        this.perplexityService = perplexityService;
        this.resultProcessor = resultProcessor;
        this.discoveryCache = discoveryCache;
        this.diversityOptimizer = diversityOptimizer;
        this.threadConfig = threadConfig;
    }

    /**
     * Main orchestration method for comprehensive paper discovery
     */
    public CompletableFuture<UnifiedDiscoveryResult> discoverRelatedPapers(
            Paper sourcePaper, 
            DiscoveryConfiguration config) {

        LoggingUtil.info(LOG, "discoverRelatedPapers", 
            "Starting unified discovery for paper %s with config %s", 
            sourcePaper.getId(), config.getMode());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                Optional<UnifiedDiscoveryResult> cached = discoveryCache.getCachedResult(
                    sourcePaper.getId(), config);
                if (cached.isPresent()) {
                    LoggingUtil.info(LOG, "discoverRelatedPapers", 
                        "Returning cached result for paper %s", sourcePaper.getId());
                    return cached.get();
                }

                // Execute discovery across all sources in parallel
                DiscoveryExecution execution = executeDiscovery(sourcePaper, config);

                // Process and synthesize results
                UnifiedDiscoveryResult result = synthesizeResults(sourcePaper, execution, config);

                // Cache the result
                discoveryCache.cacheResult(sourcePaper.getId(), config, result);

                LoggingUtil.info(LOG, "discoverRelatedPapers", 
                    "Completed unified discovery for paper %s: found %d related papers", 
                    sourcePaper.getId(), result.getTotalDiscoveredPapers());

                return result;

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverRelatedPapers", 
                    "Unified discovery failed for paper %s", e, sourcePaper.getId());
                throw new DiscoveryOrchestrationException("Discovery orchestration failed", e);
            }
        }, threadConfig.taskExecutor());
    }

    /**
     * Execute discovery across all sources in parallel
     */
    private DiscoveryExecution executeDiscovery(Paper sourcePaper, DiscoveryConfiguration config) {
        DiscoveryExecution.Builder execution = DiscoveryExecution.builder()
            .sourcePaper(sourcePaper)
            .configuration(config)
            .startTime(Instant.now());

        List<CompletableFuture<SourceDiscoveryResult>> discoveryFutures = new ArrayList<>();

        // Crossref discovery (always enabled for citation networks)
        if (config.isIncludeCrossref()) {
            CompletableFuture<SourceDiscoveryResult> crossrefFuture = CompletableFuture
                .supplyAsync(() -> executeCrossrefDiscovery(sourcePaper), threadConfig.taskExecutor())
                .exceptionally(throwable -> SourceDiscoveryResult.failed(DiscoverySource.CROSSREF, throwable));
            discoveryFutures.add(crossrefFuture);
        }

        // Semantic Scholar discovery (enabled for semantic similarity)
        if (config.isIncludeSemanticScholar()) {
            CompletableFuture<SourceDiscoveryResult> semanticFuture = CompletableFuture
                .supplyAsync(() -> executeSemanticScholarDiscovery(sourcePaper), threadConfig.taskExecutor())
                .exceptionally(throwable -> SourceDiscoveryResult.failed(DiscoverySource.SEMANTIC_SCHOLAR, throwable));
            discoveryFutures.add(semanticFuture);
        }

        // Perplexity discovery (enabled for trends and open access)
        if (config.isIncludePerplexity()) {
            CompletableFuture<SourceDiscoveryResult> perplexityFuture = CompletableFuture
                .supplyAsync(() -> executePerplexityDiscovery(sourcePaper), threadConfig.taskExecutor())
                .exceptionally(throwable -> SourceDiscoveryResult.failed(DiscoverySource.PERPLEXITY, throwable));
            discoveryFutures.add(perplexityFuture);
        }

        // Wait for all discoveries to complete
        CompletableFuture<Void> allDiscoveries = CompletableFuture.allOf(
            discoveryFutures.toArray(new CompletableFuture[0]));

        try {
            // Wait with timeout
            allDiscoveries.get(config.getMaxExecutionTime().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LoggingUtil.warn(LOG, "executeDiscovery", 
                "Discovery timeout reached, proceeding with partial results");
        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeDiscovery", 
                "Discovery execution failed", e);
        }

        // Collect completed results
        List<SourceDiscoveryResult> results = discoveryFutures.stream()
            .filter(CompletableFuture::isDone)
            .map(CompletableFuture::join)
            .collect(Collectors.toList());

        execution.sourceResults(results);
        execution.endTime(Instant.now());

        return execution.build();
    }

    /**
     * Execute Crossref discovery with error handling
     */
    private SourceDiscoveryResult executeCrossrefDiscovery(Paper sourcePaper) {
        try {
            CrossrefDiscoveryResult crossrefResult = crossrefService.discoverRelatedPapers(sourcePaper);

            return SourceDiscoveryResult.builder()
                .source(DiscoverySource.CROSSREF)
                .discoveredPapers(crossrefResult.getAllDiscoveredPapers())
                .metadata(crossrefResult.getDiscoveryMetadata())
                .executionTime(Duration.between(
                    crossrefResult.getDiscoveryMetadata().getStartTime(),
                    crossrefResult.getDiscoveryMetadata().getEndTime()))
                .success(true)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeCrossrefDiscovery", 
                "Crossref discovery failed", e);
            return SourceDiscoveryResult.failed(DiscoverySource.CROSSREF, e);
        }
    }

    /**
     * Execute Semantic Scholar discovery with error handling
     */
    private SourceDiscoveryResult executeSemanticScholarDiscovery(Paper sourcePaper) {
        try {
            SemanticScholarDiscoveryResult semanticResult = semanticScholarService.discoverRelatedPapers(sourcePaper);

            return SourceDiscoveryResult.builder()
                .source(DiscoverySource.SEMANTIC_SCHOLAR)
                .discoveredPapers(semanticResult.getAllDiscoveredPapers())
                .metadata(semanticResult.getDiscoveryMetadata())
                .executionTime(Duration.between(
                    semanticResult.getDiscoveryMetadata().getStartTime(),
                    semanticResult.getDiscoveryMetadata().getEndTime()))
                .success(true)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeSemanticScholarDiscovery", 
                "Semantic Scholar discovery failed", e);
            return SourceDiscoveryResult.failed(DiscoverySource.SEMANTIC_SCHOLAR, e);
        }
    }

    /**
     * Execute Perplexity trend discovery with error handling
     */
    private SourceDiscoveryResult executePerplexityDiscovery(Paper sourcePaper) {
        try {
            PerplexityTrendResult perplexityResult = perplexityService.analyzeTrends(sourcePaper);

            return SourceDiscoveryResult.builder()
                .source(DiscoverySource.PERPLEXITY)
                .discoveredPapers(perplexityResult.getAllDiscoveredPapers())
                .metadata(perplexityResult.getTrendMetadata())
                .executionTime(Duration.between(
                    perplexityResult.getTrendMetadata().getStartTime(),
                    perplexityResult.getTrendMetadata().getEndTime()))
                .success(true)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "executePerplexityDiscovery", 
                "Perplexity discovery failed", e);
            return SourceDiscoveryResult.failed(DiscoverySource.PERPLEXITY, e);
        }
    }

    /**
     * Synthesize results from all sources into unified result
     */
    private UnifiedDiscoveryResult synthesizeResults(
            Paper sourcePaper, 
            DiscoveryExecution execution, 
            DiscoveryConfiguration config) {

        // Collect all discovered papers from successful sources
        List<DiscoveredPaper> allPapers = execution.getSourceResults().stream()
            .filter(SourceDiscoveryResult::isSuccess)
            .flatMap(result -> result.getDiscoveredPapers().stream())
            .collect(Collectors.toList());

        LoggingUtil.info(LOG, "synthesizeResults", 
            "Synthesizing %d papers from %d sources", 
            allPapers.size(), execution.getSourceResults().size());

        // Deduplicate papers using sophisticated matching
        List<DiscoveredPaper> deduplicatedPapers = resultProcessor.deduplicate(allPapers);

        // Calculate unified relevance scores
        List<DiscoveredPaper> scoredPapers = resultProcessor.calculateUnifiedScores(
            deduplicatedPapers, sourcePaper, execution);

        // Filter by relevance threshold
        List<DiscoveredPaper> relevantPapers = scoredPapers.stream()
            .filter(paper -> paper.getRelevanceScore() >= MIN_RELEVANCE_THRESHOLD)
            .collect(Collectors.toList());

        // Optimize for diversity
        List<DiscoveredPaper> diversePapers = diversityOptimizer.optimizeForDiversity(
            relevantPapers, config.getDiversityLevel());

        // Limit total results
        List<DiscoveredPaper> finalPapers = diversePapers.stream()
            .limit(Math.min(config.getMaxResults(), MAX_TOTAL_RESULTS))
            .collect(Collectors.toList());

        // Create synthesis metadata
        SynthesisMetadata synthesisMetadata = createSynthesisMetadata(execution, allPapers, finalPapers);

        return UnifiedDiscoveryResult.builder()
            .sourcePaper(sourcePaper)
            .discoveredPapers(finalPapers)
            .sourceResults(execution.getSourceResults())
            .synthesisMetadata(synthesisMetadata)
            .configuration(config)
            .build();
    }

    /**
     * Create synthesis metadata for result tracking
     */
    private SynthesisMetadata createSynthesisMetadata(
            DiscoveryExecution execution,
            List<DiscoveredPaper> allPapers,
            List<DiscoveredPaper> finalPapers) {

        return SynthesisMetadata.builder()
            .totalRawResults(allPapers.size())
            .totalProcessedResults(finalPapers.size())
            .successfulSources(execution.getSourceResults().stream()
                .filter(SourceDiscoveryResult::isSuccess)
                .map(SourceDiscoveryResult::getSource)
                .collect(Collectors.toList()))
            .failedSources(execution.getSourceResults().stream()
                .filter(result -> !result.isSuccess())
                .map(SourceDiscoveryResult::getSource)
                .collect(Collectors.toList()))
            .processingTime(Duration.between(execution.getStartTime(), execution.getEndTime()))
            .overallConfidence(calculateOverallConfidence(execution, finalPapers))
            .build();
    }

    /**
     * Calculate overall confidence based on source success and result quality
     */
    private double calculateOverallConfidence(DiscoveryExecution execution, List<DiscoveredPaper> papers) {
        long successfulSources = execution.getSourceResults().stream()
            .filter(SourceDiscoveryResult::isSuccess)
            .count();

        long totalSources = execution.getSourceResults().size();
        double sourceSuccessRate = (double) successfulSources / totalSources;

        double avgRelevanceScore = papers.stream()
            .mapToDouble(DiscoveredPaper::getRelevanceScore)
            .average()
            .orElse(0.0);

        // Combine source success rate and result quality
        return (sourceSuccessRate * 0.4) + (avgRelevanceScore * 0.6);
    }
}
```

## Advanced Result Processing

### DiscoveryResultProcessor

```java
@Component
public class DiscoveryResultProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryResultProcessor.class);
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.85;
    private static final double AUTHOR_SIMILARITY_THRESHOLD = 0.7;

    /**
     * Advanced deduplication using multiple matching strategies
     */
    public List<DiscoveredPaper> deduplicate(List<DiscoveredPaper> papers) {
        LoggingUtil.info(LOG, "deduplicate", 
            "Deduplicating %d papers using multiple strategies", papers.size());

        Map<String, List<DiscoveredPaper>> duplicateGroups = new HashMap<>();
        Set<String> processedKeys = new HashSet<>();

        for (DiscoveredPaper paper : papers) {
            String matchKey = findBestMatchKey(paper, processedKeys, duplicateGroups);

            if (matchKey != null) {
                duplicateGroups.get(matchKey).add(paper);
            } else {
                String newKey = generateUniqueKey(paper);
                duplicateGroups.put(newKey, new ArrayList<>(List.of(paper)));
                processedKeys.add(newKey);
            }
        }

        List<DiscoveredPaper> deduplicated = duplicateGroups.values().stream()
            .map(this::selectBestRepresentative)
            .collect(Collectors.toList());

        LoggingUtil.info(LOG, "deduplicate", 
            "Deduplication complete: %d papers reduced to %d unique papers", 
            papers.size(), deduplicated.size());

        return deduplicated;
    }

    /**
     * Find best matching key for a paper against existing groups
     */
    private String findBestMatchKey(
            DiscoveredPaper paper, 
            Set<String> processedKeys, 
            Map<String, List<DiscoveredPaper>> groups) {

        // Strategy 1: Exact DOI match (highest priority)
        if (paper.getDoi() != null) {
            for (String key : processedKeys) {
                List<DiscoveredPaper> group = groups.get(key);
                if (group.stream().anyMatch(p -> paper.getDoi().equals(p.getDoi()))) {
                    return key;
                }
            }
        }

        // Strategy 2: Title and author similarity
        for (String key : processedKeys) {
            List<DiscoveredPaper> group = groups.get(key);
            DiscoveredPaper representative = group.get(0);

            if (isSimilarPaper(paper, representative)) {
                return key;
            }
        }

        return null;
    }

    /**
     * Check if two papers are similar using multiple criteria
     */
    private boolean isSimilarPaper(DiscoveredPaper paper1, DiscoveredPaper paper2) {
        double titleSimilarity = calculateTitleSimilarity(paper1.getTitle(), paper2.getTitle());

        if (titleSimilarity >= TITLE_SIMILARITY_THRESHOLD) {
            double authorSimilarity = calculateAuthorSimilarity(paper1.getAuthors(), paper2.getAuthors());
            return authorSimilarity >= AUTHOR_SIMILARITY_THRESHOLD || 
                   (titleSimilarity >= 0.95 && haveSimilarPublicationYear(paper1, paper2));
        }

        return false;
    }

    /**
     * Calculate unified relevance scores combining all source scores
     */
    public List<DiscoveredPaper> calculateUnifiedScores(
            List<DiscoveredPaper> papers, 
            Paper sourcePaper, 
            DiscoveryExecution execution) {

        for (DiscoveredPaper paper : papers) {
            double unifiedScore = calculateUnifiedRelevanceScore(paper, sourcePaper, execution);
            paper.setRelevanceScore(unifiedScore);
        }

        return papers.stream()
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Calculate unified relevance score from multiple factors
     */
    private double calculateUnifiedRelevanceScore(
            DiscoveredPaper paper, 
            Paper sourcePaper, 
            DiscoveryExecution execution) {

        double score = 0.0;

        // Base relevance from source-specific scoring (0-0.4)
        score += Math.min(paper.getRelevanceScore() * 0.4, 0.4);

        // Citation influence factor (0-0.25)
        if (paper.getCitationCount() != null) {
            double citationScore = Math.min(paper.getCitationCount() / 1000.0, 0.25);
            score += citationScore;
        }

        // Publication recency factor (0-0.15)
        if (paper.getPublishedDate() != null) {
            long yearsOld = ChronoUnit.YEARS.between(paper.getPublishedDate(), LocalDate.now());
            double recencyScore = Math.max(0, (10 - yearsOld) / 10.0) * 0.15;
            score += recencyScore;
        }

        // Author overlap factor (0-0.1)
        if (sourcePaper.getAuthors() != null && paper.getAuthors() != null) {
            long authorOverlap = sourcePaper.getAuthors().stream()
                .filter(paper.getAuthors()::contains)
                .count();
            double authorScore = (authorOverlap / (double) sourcePaper.getAuthors().size()) * 0.1;
            score += authorScore;
        }

        // Open access bonus (0-0.05)
        if (paper.isOpenAccess()) {
            score += 0.05;
        }

        return Math.min(score, 1.0);
    }

    private String generateUniqueKey(DiscoveredPaper paper) {
        return UUID.randomUUID().toString();
    }

    private DiscoveredPaper selectBestRepresentative(List<DiscoveredPaper> duplicates) {
        if (duplicates.size() == 1) {
            return duplicates.get(0);
        }

        return duplicates.stream()
            .max(Comparator
                .comparing((DiscoveredPaper p) -> p.getDoi() != null ? 1.0 : 0.0)
                .thenComparing(p -> p.getCitationCount() != null ? p.getCitationCount() : 0)
                .thenComparing(this::calculateMetadataCompleteness))
            .orElse(duplicates.get(0));
    }

    private double calculateMetadataCompleteness(DiscoveredPaper paper) {
        int score = 0;
        if (paper.getDoi() != null) score++;
        if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) score++;
        if (paper.getJournal() != null) score++;
        if (paper.getPublishedDate() != null) score++;
        if (paper.getCitationCount() != null) score++;
        return score / 5.0;
    }

    private double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) return 0.0;

        String normalized1 = normalizeTitle(title1);
        String normalized2 = normalizeTitle(title2);

        return LevenshteinDistance.getDefaultInstance().apply(normalized1, normalized2) <= 3 ? 0.9 : 0.3;
    }

    private String normalizeTitle(String title) {
        return title.toLowerCase()
            .replaceAll("[^a-zA-Z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private double calculateAuthorSimilarity(List<String> authors1, List<String> authors2) {
        if (authors1 == null || authors2 == null) return 0.0;

        long overlap = authors1.stream()
            .filter(authors2::contains)
            .count();

        return overlap / (double) Math.max(authors1.size(), authors2.size());
    }

    private boolean haveSimilarPublicationYear(DiscoveredPaper paper1, DiscoveredPaper paper2) {
        if (paper1.getPublishedDate() == null || paper2.getPublishedDate() == null) return false;

        return Math.abs(paper1.getPublishedDate().getYear() - paper2.getPublishedDate().getYear()) <= 1;
    }
}
```

## Data Models

### UnifiedDiscoveryResult

```java
@Data
@Builder
public class UnifiedDiscoveryResult {
    private Paper sourcePaper;
    private List<DiscoveredPaper> discoveredPapers;
    private List<SourceDiscoveryResult> sourceResults;
    private SynthesisMetadata synthesisMetadata;
    private DiscoveryConfiguration configuration;

    public int getTotalDiscoveredPapers() {
        return discoveredPapers.size();
    }

    public List<DiscoveredPaper> getTopResults(int limit) {
        return discoveredPapers.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    public Map<DiscoverySource, Integer> getResultsBySource() {
        return sourceResults.stream()
            .collect(Collectors.toMap(
                SourceDiscoveryResult::getSource,
                result -> result.getDiscoveredPapers().size()
            ));
    }

    public double getOverallConfidence() {
        return synthesisMetadata.getOverallConfidence();
    }

    public boolean hasHighQualityResults() {
        return discoveredPapers.stream()
            .anyMatch(paper -> paper.getRelevanceScore() > 0.8);
    }
}
```

### DiscoveryConfiguration

```java
@Data
@Builder
public class DiscoveryConfiguration {
    private DiscoveryMode mode;
    private boolean includeCrossref;
    private boolean includeSemanticScholar;
    private boolean includePerplexity;
    private int maxResults;
    private DiversityLevel diversityLevel;
    private Duration maxExecutionTime;
    private double minRelevanceThreshold;
    private boolean includeOpenAccessOnly;
    private Set<String> excludeVenues;
    private LocalDate fromDate;
    private LocalDate toDate;

    public static DiscoveryConfiguration comprehensive() {
        return DiscoveryConfiguration.builder()
            .mode(DiscoveryMode.COMPREHENSIVE)
            .includeCrossref(true)
            .includeSemanticScholar(true)
            .includePerplexity(true)
            .maxResults(50)
            .diversityLevel(DiversityLevel.MEDIUM)
            .maxExecutionTime(Duration.ofMinutes(3))
            .minRelevanceThreshold(0.3)
            .includeOpenAccessOnly(false)
            .build();
    }

    public static DiscoveryConfiguration quick() {
        return DiscoveryConfiguration.builder()
            .mode(DiscoveryMode.QUICK)
            .includeCrossref(true)
            .includeSemanticScholar(true)
            .includePerplexity(false)
            .maxResults(20)
            .diversityLevel(DiversityLevel.LOW)
            .maxExecutionTime(Duration.ofMinutes(1))
            .minRelevanceThreshold(0.4)
            .includeOpenAccessOnly(false)
            .build();
    }
}
```

## Caching Strategy

### DiscoveryCache

```java
@Component
public class DiscoveryCache {

    private final AgentMemoryStoreRepository memoryRepository;
    private final LoadingCache<String, UnifiedDiscoveryResult> resultCache;

    public DiscoveryCache(AgentMemoryStoreRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
        this.resultCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(8))
            .build(this::loadDiscoveryResult);
    }

    /**
     * Get cached discovery result with configuration awareness
     */
    public Optional<UnifiedDiscoveryResult> getCachedResult(
            UUID paperId, 
            DiscoveryConfiguration config) {

        String cacheKey = buildCacheKey(paperId, config);

        try {
            UnifiedDiscoveryResult result = resultCache.get(cacheKey);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Cache discovery result with persistence
     */
    public void cacheResult(UUID paperId, DiscoveryConfiguration config, UnifiedDiscoveryResult result) {
        String cacheKey = buildCacheKey(paperId, config);

        // Store in memory cache
        resultCache.put(cacheKey, result);

        // Store in persistent cache
        AgentMemoryStore memory = AgentMemoryStore.builder()
            .key(cacheKey)
            .data(serializeDiscoveryResult(result))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        memoryRepository.save(memory);
    }

    private String buildCacheKey(UUID paperId, DiscoveryConfiguration config) {
        return String.format("discovery_%s_%s_%d_%s", 
            paperId, 
            config.getMode(), 
            config.getMaxResults(),
            config.getDiversityLevel());
    }

    private JsonNode serializeDiscoveryResult(UnifiedDiscoveryResult result) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.valueToTree(result);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "serializeDiscoveryResult", "Serialization failed", e);
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private UnifiedDiscoveryResult loadDiscoveryResult(String cacheKey) {
        return memoryRepository.findByKey(cacheKey)
            .map(this::deserializeDiscoveryResult)
            .orElse(null);
    }

    private UnifiedDiscoveryResult deserializeDiscoveryResult(AgentMemoryStore memory) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.treeToValue(memory.getData(), UnifiedDiscoveryResult.class);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "deserializeDiscoveryResult", "Deserialization failed", e);
            return null;
        }
    }
}
```

This comprehensive Discovery Orchestrator provides the central coordination for unified paper discovery, implementing sophisticated deduplication, scoring, and caching strategies to deliver the most relevant and diverse set of related papers to Answer42 users.

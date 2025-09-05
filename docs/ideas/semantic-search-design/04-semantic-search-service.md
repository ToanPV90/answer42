# 4. Semantic Search Service

## Core Search Service Implementation

### Multi-Dimensional Semantic Search Service

```java
@Service
@Transactional(readOnly = true)
public class SemanticSearchService {
    
    private final PaperRepository paperRepository;
    private final PaperSectionRepository paperSectionRepository;
    private final EmbeddingService embeddingService;
    private final SemanticSearchQueryRepository queryRepository;
    private final SemanticSearchResultRepository resultRepository;
    private static final Logger LOG = LoggerFactory.getLogger(SemanticSearchService.class);
    
    public SemanticSearchService(
            PaperRepository paperRepository,
            PaperSectionRepository paperSectionRepository,
            EmbeddingService embeddingService,
            SemanticSearchQueryRepository queryRepository,
            SemanticSearchResultRepository resultRepository) {
        this.paperRepository = paperRepository;
        this.paperSectionRepository = paperSectionRepository;
        this.embeddingService = embeddingService;
        this.queryRepository = queryRepository;
        this.resultRepository = resultRepository;
    }
    
    /**
     * Perform multi-dimensional semantic search
     */
    public SemanticSearchResponse search(SemanticSearchRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Generate query embedding
            EmbeddingResult queryEmbedding = embeddingService.generateEmbedding(request.getQuery());
            
            if (!queryEmbedding.isSuccess()) {
                throw new SemanticSearchException("Failed to generate query embedding: " + 
                    queryEmbedding.getErrorMessage());
            }
            
            // Execute search based on requested dimensions
            List<SemanticSearchMatch> matches = executeMultiDimensionalSearch(
                queryEmbedding.getEmbedding(), request);
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log and track the search query
            UUID queryId = trackSearchQuery(request, queryEmbedding, matches.size(), executionTime);
            
            // Track individual results
            trackSearchResults(queryId, matches);
            
            LoggingUtil.info(LOG, "search", 
                "Semantic search completed in %dms, found %d matches", 
                executionTime, matches.size());
            
            return SemanticSearchResponse.builder()
                .queryId(queryId)
                .query(request.getQuery())
                .matches(matches)
                .totalResults(matches.size())
                .executionTimeMs(executionTime)
                .searchTypes(request.getSearchTypes())
                .build();
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "search", 
                "Semantic search failed for query: %s", e, request.getQuery());
            throw new SemanticSearchException("Search failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute search across multiple embedding dimensions
     */
    private List<SemanticSearchMatch> executeMultiDimensionalSearch(
            float[] queryEmbedding, 
            SemanticSearchRequest request) {
        
        Map<String, List<SemanticSearchMatch>> dimensionResults = new HashMap<>();
        
        // Search each requested dimension
        for (String searchType : request.getSearchTypes()) {
            List<SemanticSearchMatch> matches = searchSingleDimension(
                queryEmbedding, searchType, request);
            
            if (!matches.isEmpty()) {
                dimensionResults.put(searchType, matches);
            }
        }
        
        // Combine and rank results from multiple dimensions
        return combineMultiDimensionalResults(dimensionResults, request);
    }
    
    /**
     * Search within a single embedding dimension
     */
    private List<SemanticSearchMatch> searchSingleDimension(
            float[] queryEmbedding,
            String searchType,
            SemanticSearchRequest request) {
        
        switch (searchType.toLowerCase()) {
            case "content":
                return searchContentEmbeddings(queryEmbedding, request);
            case "abstract":
                return searchAbstractEmbeddings(queryEmbedding, request);
            case "title":
                return searchTitleEmbeddings(queryEmbedding, request);
            case "concepts":
                return searchConceptEmbeddings(queryEmbedding, request);
            case "methodology":
                return searchMethodologyEmbeddings(queryEmbedding, request);
            case "findings":
                return searchFindingsEmbeddings(queryEmbedding, request);
            case "sections":
                return searchSectionEmbeddings(queryEmbedding, request);
            default:
                LoggingUtil.warn(LOG, "searchSingleDimension", 
                    "Unknown search type: %s", searchType);
                return new ArrayList<>();
        }
    }
    
    /**
     * Search content embeddings using native PostgreSQL vector operations
     */
    private List<SemanticSearchMatch> searchContentEmbeddings(
            float[] queryEmbedding, 
            SemanticSearchRequest request) {
        
        String sql = """
            SELECT p.id, p.title, p.paper_abstract, p.user_id, p.is_public,
                   1 - (p.content_embedding <=> ?::vector) as similarity_score
            FROM answer42.papers p
            WHERE p.content_embedding IS NOT NULL
              AND (?::uuid IS NULL OR p.user_id = ?::uuid)
              AND (? = false OR p.is_public = true)
              AND 1 - (p.content_embedding <=> ?::vector) >= ?
            ORDER BY p.content_embedding <=> ?::vector
            LIMIT ?
            """;
        
        return executeVectorQuery(sql, queryEmbedding, request, "content");
    }
    
    /**
     * Search abstract embeddings
     */
    private List<SemanticSearchMatch> searchAbstractEmbeddings(
            float[] queryEmbedding,
            SemanticSearchRequest request) {
        
        String sql = """
            SELECT p.id, p.title, p.paper_abstract, p.user_id, p.is_public,
                   1 - (p.abstract_embedding <=> ?::vector) as similarity_score
            FROM answer42.papers p
            WHERE p.abstract_embedding IS NOT NULL
              AND (?::uuid IS NULL OR p.user_id = ?::uuid)
              AND (? = false OR p.is_public = true)
              AND 1 - (p.abstract_embedding <=> ?::vector) >= ?
            ORDER BY p.abstract_embedding <=> ?::vector
            LIMIT ?
            """;
        
        return executeVectorQuery(sql, queryEmbedding, request, "abstract");
    }
    
    /**
     * Search section-level embeddings for granular matches
     */
    private List<SemanticSearchMatch> searchSectionEmbeddings(
            float[] queryEmbedding,
            SemanticSearchRequest request) {
        
        String sql = """
            SELECT DISTINCT p.id, p.title, p.paper_abstract, p.user_id, p.is_public,
                   MAX(1 - (ps.content_embedding <=> ?::vector)) as similarity_score,
                   array_agg(DISTINCT ps.section_type) FILTER (WHERE ps.section_type IS NOT NULL) as matching_sections
            FROM answer42.papers p
            JOIN answer42.paper_sections ps ON p.id = ps.paper_id
            WHERE ps.content_embedding IS NOT NULL
              AND (?::uuid IS NULL OR p.user_id = ?::uuid)
              AND (? = false OR p.is_public = true)
              AND 1 - (ps.content_embedding <=> ?::vector) >= ?
            GROUP BY p.id, p.title, p.paper_abstract, p.user_id, p.is_public
            ORDER BY similarity_score DESC
            LIMIT ?
            """;
        
        return executeVectorQueryWithSections(sql, queryEmbedding, request, "sections");
    }
    
    /**
     * Execute vector similarity query with standard parameters
     */
    private List<SemanticSearchMatch> executeVectorQuery(
            String sql, 
            float[] queryEmbedding, 
            SemanticSearchRequest request,
            String matchType) {
        
        try {
            Query query = entityManager.createNativeQuery(sql);
            
            // Set parameters for vector comparison
            query.setParameter(1, queryEmbedding);
            query.setParameter(2, request.getUserId());
            query.setParameter(3, request.getUserId());
            query.setParameter(4, request.isPublicOnly());
            query.setParameter(5, queryEmbedding);
            query.setParameter(6, request.getSimilarityThreshold());
            query.setParameter(7, queryEmbedding);
            query.setParameter(8, request.getMaxResults());
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            
            return results.stream()
                .map(row -> mapToSemanticSearchMatch(row, matchType))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeVectorQuery", 
                "Vector query failed for match type %s", e, matchType);
            return new ArrayList<>();
        }
    }
    
    /**
     * Map database result to SemanticSearchMatch
     */
    private SemanticSearchMatch mapToSemanticSearchMatch(Object[] row, String matchType) {
        return SemanticSearchMatch.builder()
            .paperId((UUID) row[0])
            .title((String) row[1])
            .abstract_((String) row[2])
            .userId((UUID) row[3])
            .isPublic((Boolean) row[4])
            .similarityScore(((Number) row[5]).doubleValue())
            .matchType(matchType)
            .matchingElements(new ArrayList<>()) // Default empty list
            .build();
    }
    
    /**
     * Combine results from multiple search dimensions with intelligent ranking
     */
    private List<SemanticSearchMatch> combineMultiDimensionalResults(
            Map<String, List<SemanticSearchMatch>> dimensionResults,
            SemanticSearchRequest request) {
        
        Map<UUID, SemanticSearchMatch> combinedResults = new HashMap<>();
        Map<String, Double> dimensionWeights = getDimensionWeights(request.getSearchTypes());
        
        // Process each dimension's results
        for (Map.Entry<String, List<SemanticSearchMatch>> entry : dimensionResults.entrySet()) {
            String dimension = entry.getKey();
            List<SemanticSearchMatch> matches = entry.getValue();
            double weight = dimensionWeights.get(dimension);
            
            for (SemanticSearchMatch match : matches) {
                UUID paperId = match.getPaperId();
                double weightedScore = match.getSimilarityScore() * weight;
                
                if (combinedResults.containsKey(paperId)) {
                    // Combine scores from multiple dimensions
                    SemanticSearchMatch existing = combinedResults.get(paperId);
                    double combinedScore = calculateCombinedScore(
                        existing.getSimilarityScore(), weightedScore);
                    
                    // Update with higher score and multiple match types
                    List<String> matchTypes = new ArrayList<>(existing.getMatchingElements());
                    matchTypes.add(dimension);
                    
                    combinedResults.put(paperId, existing.toBuilder()
                        .similarityScore(combinedScore)
                        .matchType(String.join(",", matchTypes))
                        .matchingElements(matchTypes)
                        .build());
                } else {
                    // Add new result
                    combinedResults.put(paperId, match.toBuilder()
                        .similarityScore(weightedScore)
                        .matchType(dimension)
                        .matchingElements(List.of(dimension))
                        .build());
                }
            }
        }
        
        // Sort by combined similarity score and apply final limit
        return combinedResults.values().stream()
            .sorted(Comparator.comparing(
                SemanticSearchMatch::getSimilarityScore).reversed())
            .limit(request.getMaxResults())
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate dimension weights based on search types
     */
    private Map<String, Double> getDimensionWeights(List<String> searchTypes) {
        Map<String, Double> weights = new HashMap<>();
        
        // Default weights for different dimensions
        Map<String, Double> defaultWeights = Map.of(
            "content", 1.0,      // Full content - comprehensive but general
            "abstract", 1.2,     // Abstract - concise and focused
            "title", 0.8,        // Title - specific but limited
            "concepts", 1.1,     // Concepts - domain-specific matching
            "methodology", 1.0,  // Methodology - approach matching
            "findings", 1.1,     // Findings - outcome matching
            "sections", 0.9      // Sections - granular but potentially noisy
        );
        
        // Apply weights, giving slightly higher weight to fewer dimensions
        double boost = Math.max(1.0, 3.0 / searchTypes.size());
        
        for (String searchType : searchTypes) {
            double baseWeight = defaultWeights.getOrDefault(searchType, 1.0);
            weights.put(searchType, baseWeight * boost);
        }
        
        return weights;
    }
    
    /**
     * Calculate combined score from multiple dimensions
     */
    private double calculateCombinedScore(double existingScore, double newScore) {
        // Use root mean square to combine scores (gives higher weight to better matches)
        return Math.sqrt((existingScore * existingScore + newScore * newScore) / 2.0);
    }
    
    /**
     * Find similar papers to a given paper
     */
    public List<SemanticSearchMatch> findSimilarPapers(
            UUID paperId, 
            int maxResults,
            double similarityThreshold) {
        
        try {
            Paper targetPaper = paperRepository.findById(paperId)
                .orElseThrow(() -> new EntityNotFoundException("Paper not found: " + paperId));
            
            // Use the paper's abstract embedding as the query vector
            if (targetPaper.getAbstractEmbedding() == null) {
                LoggingUtil.warn(LOG, "findSimilarPapers", 
                    "Paper %s has no abstract embedding", paperId);
                return new ArrayList<>();
            }
            
            float[] queryEmbedding = convertVectorToFloatArray(targetPaper.getAbstractEmbedding());
            
            String sql = """
                SELECT p.id, p.title, p.paper_abstract, p.user_id, p.is_public,
                       1 - (p.abstract_embedding <=> ?::vector) as similarity_score
                FROM answer42.papers p
                WHERE p.abstract_embedding IS NOT NULL
                  AND p.id != ?::uuid
                  AND 1 - (p.abstract_embedding <=> ?::vector) >= ?
                ORDER BY p.abstract_embedding <=> ?::vector
                LIMIT ?
                """;
            
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, queryEmbedding);
            query.setParameter(2, paperId);
            query.setParameter(3, queryEmbedding);
            query.setParameter(4, similarityThreshold);
            query.setParameter(5, queryEmbedding);
            query.setParameter(6, maxResults);
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            
            return results.stream()
                .map(row -> mapToSemanticSearchMatch(row, "similar_papers"))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "findSimilarPapers", 
                "Failed to find similar papers for %s", e, paperId);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get search analytics for optimization
     */
    public SemanticSearchAnalytics getSearchAnalytics(UUID userId, LocalDateTime since) {
        // Get search query statistics
        Map<String, Long> searchTypeDistribution = queryRepository
            .getSearchTypeDistribution(userId, since);
        
        // Get average similarity scores
        Map<String, Double> averageSimilarityScores = resultRepository
            .getAverageSimilarityScores(userId, since);
        
        // Get click-through rates
        Map<String, Double> clickThroughRates = resultRepository
            .getClickThroughRates(userId, since);
        
        return SemanticSearchAnalytics.builder()
            .userId(userId)
            .analysisPeriod(since)
            .searchTypeDistribution(searchTypeDistribution)
            .averageSimilarityScores(averageSimilarityScores)
            .clickThroughRates(clickThroughRates)
            .build();
    }
}
```

## Search Request/Response Models

### Search Request Model

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchRequest {
    
    private String query;
    private List<String> searchTypes; // content, abstract, title, concepts, methodology, findings, sections
    private double similarityThreshold = 0.5;
    private int maxResults = 20;
    private UUID userId; // For filtering user's papers
    private boolean publicOnly = false;
    private List<String> excludePaperIds = new ArrayList<>();
    
    // Validation
    public void validate() {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        
        if (searchTypes == null || searchTypes.isEmpty()) {
            searchTypes = List.of("abstract"); // Default to abstract search
        }
        
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("Similarity threshold must be between 0.0 and 1.0");
        }
        
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("Max results must be between 1 and 100");
        }
    }
    
    // Helper methods
    public boolean isMultiDimensional() {
        return searchTypes.size() > 1;
    }
    
    public boolean includesSearchType(String type) {
        return searchTypes.contains(type.toLowerCase());
    }
}
```

### Search Response Models

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchResponse {
    
    private UUID queryId;
    private String query;
    private List<SemanticSearchMatch> matches;
    private int totalResults;
    private long executionTimeMs;
    private List<String> searchTypes;
    private Map<String, Integer> resultsByType = new HashMap<>();
    
    // Helper methods
    public boolean hasResults() {
        return matches != null && !matches.isEmpty();
    }
    
    public List<SemanticSearchMatch> getTopMatches(int count) {
        if (!hasResults()) {
            return new ArrayList<>();
        }
        return matches.stream()
            .limit(count)
            .collect(Collectors.toList());
    }
    
    public OptionalDouble getAverageSimilarityScore() {
        if (!hasResults()) {
            return OptionalDouble.empty();
        }
        return matches.stream()
            .mapToDouble(SemanticSearchMatch::getSimilarityScore)
            .average();
    }
}

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchMatch {
    
    private UUID paperId;
    private String title;
    private String abstract_;
    private UUID userId;
    private boolean isPublic;
    private double similarityScore;
    private String matchType;
    private List<String> matchingElements;
    private List<String> matchingSections; // For section-level matches
    
    // Helper methods
    public boolean isHighSimilarity() {
        return similarityScore >= 0.8;
    }
    
    public boolean isMediumSimilarity() {
        return similarityScore >= 0.6 && similarityScore < 0.8;
    }
    
    public boolean isMultiDimensionalMatch() {
        return matchingElements != null && matchingElements.size() > 1;
    }
    
    public String getFormattedSimilarityScore() {
        return String.format("%.2f", similarityScore * 100) + "%";
    }
}
```

This semantic search service provides comprehensive multi-dimensional similarity search capabilities while integrating seamlessly with Answer42's existing architecture and maintaining high performance through optimized PostgreSQL vector operations.

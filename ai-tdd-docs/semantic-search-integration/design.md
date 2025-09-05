# Technical Design Document: Semantic Search Integration

## Architecture Overview
The semantic search integration transforms Answer42 into an AI-powered research discovery platform by leveraging PostgreSQL's pgvector extension and advanced vector embeddings. The system follows a layered architecture that seamlessly integrates with existing Answer42 components while adding sophisticated semantic capabilities.

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                    Semantic Search Architecture                  │
├─────────────────────────────────────────────────────────────────┤
│  Frontend Layer (Vaadin 24.7.3)                               │
│  ├── Enhanced Search Components                                │
│  ├── Semantic Query Interface                                  │
│  └── Relevance Visualization                                   │
├─────────────────────────────────────────────────────────────────┤
│  API Layer (Spring Boot 3.4.5)                                │
│  ├── Semantic Search Controllers                               │
│  ├── Hybrid Search Orchestration                               │
│  └── Vector Similarity APIs                                    │
├─────────────────────────────────────────────────────────────────┤
│  Service Layer                                                  │
│  ├── SemanticSearchService                                     │
│  ├── EmbeddingGenerationService                                │
│  ├── HybridRankingService                                      │
│  └── PersonalizationService                                    │
├─────────────────────────────────────────────────────────────────┤
│  Integration Layer                                              │
│  ├── Existing PaperService Integration                         │
│  ├── DiscoveryCoordinator Enhancement                          │
│  ├── AIConfig Provider Management                              │
│  └── Spring Batch Processing                                   │
├─────────────────────────────────────────────────────────────────┤
│  Data Layer (PostgreSQL + pgvector)                           │
│  ├── Vector Storage Tables                                     │
│  ├── Embedding Indexes (HNSW, IVFFlat)                        │
│  ├── Existing Paper Tables                                     │
│  └── Search Context Tables                                     │
└─────────────────────────────────────────────────────────────────┘
```

## Component Design

### Component 1: SemanticSearchService
**Responsibility:** Core semantic search functionality and orchestration  
**Interfaces:** REST API endpoints, internal service calls  
**Dependencies:** EmbeddingGenerationService, PaperService, pgvector database

```java
@Service
@Transactional(readOnly = true)
public class SemanticSearchService {
    
    private final EmbeddingGenerationService embeddingService;
    private final SemanticSearchRepository searchRepository;
    private final HybridRankingService rankingService;
    private final LoggingUtil loggingUtil;
    
    @Cacheable(value = "semantic-search", key = "#query.hashCode()")
    public SemanticSearchResult search(SemanticQuery query);
    
    public List<SimilarPaper> findSimilarPapers(UUID paperId, double threshold);
    
    public HybridSearchResult hybridSearch(HybridQuery query);
}
```

### Component 2: EmbeddingGenerationService  
**Responsibility:** Generate and manage vector embeddings for papers and queries  
**Interfaces:** Spring AI integration, batch processing APIs  
**Dependencies:** AIConfig, OpenAI/Anthropic clients, Spring Batch

```java
@Service
public class EmbeddingGenerationService {
    
    private final ChatClient chatClient;
    private final EmbeddingRepository embeddingRepository;
    private final CreditService creditService;
    
    @Async
    public CompletableFuture<float[]> generateEmbedding(String text, EmbeddingType type);
    
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public BatchEmbeddingResult generateBatchEmbeddings(List<Paper> papers);
    
    public void updatePaperEmbeddings(UUID paperId, PaperContent content);
}
```

### Component 3: HybridRankingService
**Responsibility:** Intelligent ranking combining semantic similarity and traditional factors  
**Interfaces:** Ranking algorithms, scoring APIs  
**Dependencies:** SemanticSearchService, existing ranking components

```java
@Service
public class HybridRankingService {
    
    private final SemanticScoringEngine semanticEngine;
    private final KeywordScoringEngine keywordEngine;
    private final CitationScoringEngine citationEngine;
    
    public RankedSearchResults rankResults(
        List<SemanticMatch> semanticMatches,
        List<KeywordMatch> keywordMatches,
        RankingCriteria criteria
    );
    
    public PersonalizedRanking applyUserPersonalization(
        RankedSearchResults results, 
        UserContext context
    );
}
```

### Component 4: VectorIndexManager
**Responsibility:** Manage vector indexes and optimize query performance  
**Interfaces:** Database index management, performance monitoring  
**Dependencies:** PostgreSQL, pgvector extension, monitoring services

```java
@Service
public class VectorIndexManager {
    
    private final JdbcTemplate jdbcTemplate;
    private final PerformanceMonitor monitor;
    
    @PostConstruct
    public void initializeVectorIndexes();
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void optimizeIndexes();
    
    public IndexPerformanceMetrics getIndexMetrics();
}
```

### Component 5: SemanticSearchUI
**Responsibility:** Enhanced user interface for semantic search  
**Interfaces:** Vaadin components, user interaction handlers  
**Dependencies:** Vaadin framework, semantic search services

```java
@Route(value = "search/semantic", layout = MainLayout.class)
@PageTitle("Semantic Search - Answer42")
public class SemanticSearchView extends Div implements BeforeEnterObserver {
    
    private final SemanticSearchService searchService;
    private final SemanticQueryBuilder queryBuilder;
    private final ResultsVisualizer visualizer;
    
    private void initializeSearchInterface();
    private void handleSemanticQuery(SemanticQuery query);
    private void displaySemanticResults(SemanticSearchResult results);
}
```

## Data Model

### Database Schema Extensions

#### Vector Embeddings Table
```sql
CREATE TABLE answer42.paper_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id UUID NOT NULL REFERENCES answer42.papers(id),
    embedding_type VARCHAR(50) NOT NULL, -- 'title', 'abstract', 'content'
    embedding_model VARCHAR(100) NOT NULL, -- 'openai-text-3-large', etc.
    embedding_vector vector(1536), -- OpenAI embeddings dimension
    embedding_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(paper_id, embedding_type, embedding_model)
);

-- Create HNSW index for fast similarity search
CREATE INDEX idx_paper_embeddings_vector_hnsw 
ON answer42.paper_embeddings 
USING hnsw (embedding_vector vector_cosine_ops);

-- Create IVFFlat index for larger datasets
CREATE INDEX idx_paper_embeddings_vector_ivfflat 
ON answer42.paper_embeddings 
USING ivfflat (embedding_vector vector_cosine_ops) 
WITH (lists = 1000);
```

#### Search Context and History
```sql
CREATE TABLE answer42.semantic_search_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES answer42.users(id),
    query_text TEXT NOT NULL,
    query_embedding vector(1536),
    search_mode VARCHAR(20) NOT NULL, -- 'semantic', 'keyword', 'hybrid'
    results_count INTEGER,
    clicked_papers UUID[],
    satisfaction_score INTEGER, -- 1-5 user rating
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE answer42.user_search_preferences (
    user_id UUID PRIMARY KEY REFERENCES answer42.users(id),
    preferred_search_mode VARCHAR(20) DEFAULT 'hybrid',
    semantic_threshold DECIMAL(3,2) DEFAULT 0.7,
    max_results INTEGER DEFAULT 20,
    personalization_enabled BOOLEAN DEFAULT true,
    research_domains TEXT[],
    updated_at TIMESTAMP DEFAULT NOW()
);
```

#### Research Gap Analysis
```sql
CREATE TABLE answer42.research_gaps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gap_description TEXT NOT NULL,
    semantic_keywords TEXT[],
    related_papers UUID[],
    confidence_score DECIMAL(3,2),
    discovery_date TIMESTAMP DEFAULT NOW(),
    exploitation_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'identified' -- 'identified', 'validated', 'explored'
);
```

### Entity Relationships

#### Core Entity Model
```java
@Entity
@Table(name = "paper_embeddings", schema = "answer42")
public class PaperEmbedding {
    
    @Id
    private UUID id;
    
    @Column(name = "paper_id")
    private UUID paperId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_type")
    private EmbeddingType embeddingType;
    
    @Column(name = "embedding_model")
    private String embeddingModel;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "embedding_vector", columnDefinition = "vector(1536)")
    private float[] embeddingVector;
    
    @Column(name = "embedding_version")
    private Integer embeddingVersion;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

@Entity
@Table(name = "semantic_search_history", schema = "answer42")
public class SemanticSearchHistory {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "query_text")
    private String queryText;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "query_embedding", columnDefinition = "vector(1536)")
    private float[] queryEmbedding;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "search_mode")
    private SearchMode searchMode;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "clicked_papers", columnDefinition = "UUID[]")
    private List<UUID> clickedPapers;
    
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;
}
```

## API Design

### Semantic Search Endpoints

#### Endpoint: POST /api/v1/search/semantic
**Purpose:** Execute semantic search queries with natural language
**Request:**
```json
{
  "query": "machine learning approaches for cancer detection",
  "maxResults": 20,
  "threshold": 0.7,
  "filters": {
    "dateRange": {
      "from": "2020-01-01",
      "to": "2024-12-31"
    },
    "authors": ["Smith, J.", "Doe, A."],
    "journals": ["Nature", "Science"],
    "researchDomains": ["oncology", "computer-science"]
  },
  "personalization": {
    "enabled": true,
    "userContext": {
      "researchInterests": ["cancer-research", "ai-healthcare"],
      "previousSearches": ["lung-cancer-detection", "medical-imaging"]
    }
  }
}
```
**Response:**
```json
{
  "searchId": "550e8400-e29b-41d4-a716-446655440000",
  "query": "machine learning approaches for cancer detection",
  "searchMode": "semantic",
  "results": [
    {
      "paperId": "123e4567-e89b-12d3-a456-426614174000",
      "title": "Deep Learning for Early Cancer Detection in Medical Imaging",
      "authors": ["Johnson, M.", "Wilson, K."],
      "semanticScore": 0.92,
      "relevanceExplanation": "Highly relevant due to ML cancer detection focus",
      "matchingConcepts": ["deep-learning", "cancer-detection", "medical-imaging"],
      "crossDisciplinary": false,
      "publishedDate": "2023-06-15",
      "journal": "Nature Medicine",
      "abstractSnippet": "We present a novel deep learning approach..."
    }
  ],
  "totalResults": 150,
  "processingTimeMs": 245,
  "suggestions": {
    "relatedQueries": [
      "AI-based tumor classification",
      "Medical image analysis algorithms",
      "Early cancer screening technologies"
    ],
    "researchGaps": [
      "Limited studies on pediatric cancer AI detection",
      "Underexplored: AI ethics in cancer screening"
    ]
  }
}
```

#### Endpoint: POST /api/v1/search/hybrid
**Purpose:** Combine semantic and keyword search with intelligent ranking
**Request:**
```json
{
  "semanticQuery": "neural networks for drug discovery",
  "keywordQuery": "CNN pharmaceutical compounds",
  "searchMode": "hybrid",
  "weightingStrategy": {
    "semanticWeight": 0.7,
    "keywordWeight": 0.3,
    "citationWeight": 0.1,
    "recencyWeight": 0.1
  },
  "maxResults": 15
}
```
**Response:**
```json
{
  "searchId": "660f9511-f39c-23e4-b567-537725285111",
  "hybridResults": [
    {
      "paperId": "789abc12-3def-4567-8901-234567890def",
      "combinedScore": 0.89,
      "semanticScore": 0.85,
      "keywordScore": 0.92,
      "citationScore": 0.78,
      "recencyScore": 0.91,
      "matchType": "hybrid",
      "semanticConcepts": ["neural-networks", "drug-discovery"],
      "keywordMatches": ["CNN", "pharmaceutical", "compounds"]
    }
  ]
}
```

#### Endpoint: GET /api/v1/papers/{paperId}/similar
**Purpose:** Find papers semantically similar to a specific paper
**Request Parameters:**
- `threshold`: Minimum similarity score (default: 0.7)
- `maxResults`: Maximum number of results (default: 10)
- `similarityType`: Type of similarity ('conceptual', 'methodological', 'all')

**Response:**
```json
{
  "basePaper": {
    "id": "456def78-90ab-1234-cdef-567890123456",
    "title": "Transformer Networks in Protein Folding Prediction"
  },
  "similarPapers": [
    {
      "paperId": "789ghi01-23jk-4567-lmno-890123456789",
      "title": "BERT-based Approaches for Biological Sequence Analysis",
      "similarityScore": 0.84,
      "similarityType": "methodological",
      "sharedConcepts": ["transformers", "biological-sequences", "attention-mechanisms"],
      "relationshipExplanation": "Both papers use transformer architecture for biological data analysis"
    }
  ]
}
```

### Embedding Management Endpoints

#### Endpoint: POST /api/v1/embeddings/generate
**Purpose:** Generate embeddings for papers or text content
**Request:**
```json
{
  "content": {
    "type": "paper",
    "paperId": "123e4567-e89b-12d3-a456-426614174000",
    "forceRegenerate": false
  },
  "embeddingModel": "openai-text-3-large",
  "priority": "normal"
}
```

#### Endpoint: GET /api/v1/embeddings/status
**Purpose:** Check embedding generation status and queue information
**Response:**
```json
{
  "queueStatus": {
    "pendingJobs": 45,
    "processingJobs": 3,
    "estimatedWaitTime": "2 minutes"
  },
  "modelStatus": {
    "activeModel": "openai-text-3-large",
    "fallbackModels": ["sentence-transformers-all-MiniLM-L6-v2"],
    "healthCheck": "healthy"
  }
}
```

## Integration Points

### Existing Service Integration

#### PaperService Enhancement
```java
@Service
@Transactional
public class PaperService {
    
    private final EmbeddingGenerationService embeddingService;
    private final SemanticSearchService semanticSearchService;
    
    @EventListener
    public void handlePaperCreated(PaperCreatedEvent event) {
        // Existing logic...
        
        // Generate embeddings for new paper
        embeddingService.generateEmbedding(event.getPaper().getTitle(), EmbeddingType.TITLE);
        embeddingService.generateEmbedding(event.getPaper().getAbstract(), EmbeddingType.ABSTRACT);
    }
    
    public EnhancedSearchResult searchPapersWithSemantic(SearchCriteria criteria) {
        if (criteria.isSemanticEnabled()) {
            return semanticSearchService.hybridSearch(criteria);
        }
        return traditionalSearch(criteria);
    }
}
```

#### DiscoveryCoordinator Integration
```java
@Service
public class DiscoveryCoordinator {
    
    private final SemanticSearchService semanticSearchService;
    private final List<DiscoverySource> discoveryServices;
    
    public DiscoveryResult enhancedDiscovery(DiscoveryQuery query) {
        // Enhanced with semantic similarity
        List<Paper> semanticMatches = semanticSearchService.search(query.toSemanticQuery()).getResults();
        
        // Combine with existing discovery sources
        DiscoveryResult result = coordinateTraditionalDiscovery(query);
        result.addSemanticMatches(semanticMatches);
        
        return result;
    }
}
```

### AIConfig Provider Management
```java
@Configuration
public class SemanticSearchConfig {
    
    @Bean
    @Primary
    public EmbeddingClient embeddingClient(@Value("${ai.embedding.provider:openai}") String provider) {
        return switch (provider) {
            case "openai" -> new OpenAiEmbeddingClient(openAiConfig());
            case "anthropic" -> new AnthropicEmbeddingClient(anthropicConfig());
            case "local" -> new OllamaEmbeddingClient(ollamaConfig());
            default -> throw new IllegalArgumentException("Unsupported embedding provider: " + provider);
        };
    }
    
    @Bean
    public CircuitBreaker embeddingCircuitBreaker() {
        return CircuitBreaker.ofDefaults("embedding-generation");
    }
}
```

### Spring Batch Processing Integration
```java
@Component
public class EmbeddingGenerationTasklet implements Tasklet {
    
    private final EmbeddingGenerationService embeddingService;
    private final PaperRepository paperRepository;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        
        List<Paper> unprocessedPapers = paperRepository.findPapersWithoutEmbeddings(PageRequest.of(0, 100));
        
        for (Paper paper : unprocessedPapers) {
            try {
                embeddingService.generateEmbedding(paper.getTitle(), EmbeddingType.TITLE);
                embeddingService.generateEmbedding(paper.getAbstract(), EmbeddingType.ABSTRACT);
                
                contribution.incrementWriteCount(1);
                
            } catch (Exception e) {
                LoggingUtil.error("Failed to generate embedding for paper: {}", paper.getId(), e);
                contribution.incrementProcessSkipCount(1);
            }
        }
        
        return RepeatStatus.FINISHED;
    }
}
```

## Security Considerations

### Data Privacy and Protection
```java
@Service
public class EmbeddingSecurityService {
    
    public boolean validateEmbeddingAccess(UUID userId, UUID paperId) {
        // Ensure user has permission to access paper content
        return paperAccessControlService.hasReadAccess(userId, paperId);
    }
    
    @PreAuthorize("hasRole('USER')")
    public SemanticSearchResult secureSemanticSearch(SemanticQuery query, Authentication auth) {
        // Apply user-specific access controls to search results
        SemanticSearchResult results = semanticSearchService.search(query);
        return filterResultsByAccess(results, auth.getName());
    }
    
    private void sanitizeEmbeddingStorage(float[] embedding) {
        // Ensure embeddings don't leak sensitive information
        // Apply differential privacy if required
    }
}
```

### API Security Configuration
```java
@Configuration
@EnableWebSecurity
public class SemanticSearchSecurityConfig {
    
    @Bean
    public SecurityFilterChain semanticSearchFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatchers(matchers -> matchers.requestMatchers("/api/v1/search/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/search/semantic").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/api/v1/search/**").hasRole("USER")
                .requestMatchers("/api/v1/embeddings/**").hasRole("ADMIN")
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

## Performance Considerations

### Caching Strategy
```java
@Configuration
@EnableCaching
public class SemanticSearchCacheConfig {
    
    @Bean
    public CacheManager semanticSearchCacheManager() {
        RedisCacheManager.Builder builder = RedisCacheManager
            .RedisCacheManagerBuilder
            .fromConnectionFactory(redisConnectionFactory())
            .cacheDefaults(cacheConfiguration());
            
        return builder.build();
    }
    
    private RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30)) // Cache semantic searches for 30 minutes
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
```

### Database Query Optimization
```sql
-- Optimized similarity search query
WITH semantic_matches AS (
  SELECT 
    pe.paper_id,
    pe.embedding_vector <=> $1::vector AS distance,
    1 - (pe.embedding_vector <=> $1::vector) AS similarity_score
  FROM answer42.paper_embeddings pe
  WHERE pe.embedding_type = 'abstract'
    AND pe.embedding_model = 'openai-text-3-large'
    AND pe.embedding_vector <=> $1::vector < $2  -- threshold filter
  ORDER BY pe.embedding_vector <=> $1::vector
  LIMIT $3
)
SELECT 
  p.id, p.title, p.abstract, p.published_date,
  sm.similarity_score,
  p.citation_count,
  p.impact_factor
FROM semantic_matches sm
JOIN answer42.papers p ON p.id = sm.paper_id
ORDER BY sm.similarity_score DESC;
```

### Monitoring and Observability
```java
@Component
public class SemanticSearchMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Timer searchTimer;
    private final Counter embeddingGenerationCounter;
    
    @PostConstruct
    public void initMetrics() {
        this.searchTimer = Timer.builder("semantic.search.duration")
            .description("Time taken for semantic search queries")
            .register(meterRegistry);
            
        this.embeddingGenerationCounter = Counter.builder("embedding.generation.count")
            .description("Number of embeddings generated")
            .register(meterRegistry);
    }
    
    @EventListener
    public void recordSearchMetrics(SemanticSearchEvent event) {
        searchTimer.record(event.getDuration(), TimeUnit.MILLISECONDS);
        
        Gauge.builder("semantic.search.results.count")
            .description("Number of results returned")
            .register(meterRegistry, event, SemanticSearchEvent::getResultCount);
    }
}
```

## Error Handling

### Resilience Patterns
```java
@Service
public class ResilientEmbeddingService {
    
    @CircuitBreaker(name = "embedding-generation", fallbackMethod = "fallbackEmbeddingGeneration")
    @Retry(name = "embedding-generation")
    @TimeLimiter(name = "embedding-generation")
    public CompletableFuture<float[]> generateEmbedding(String text) {
        return embeddingClient.generateEmbedding(text);
    }
    
    public CompletableFuture<float[]> fallbackEmbeddingGeneration(String text, Exception ex) {
        LoggingUtil.warn("Primary embedding service failed, using fallback: {}", ex.getMessage());
        return localEmbeddingService.generateEmbedding(text);
    }
    
    @EventListener
    public void handleEmbeddingFailure(EmbeddingGenerationFailedEvent event) {
        // Queue for retry with exponential backoff
        embeddingRetryQueue.scheduleRetry(event.getText(), calculateBackoffDelay(event.getAttemptNumber()));
    }
}
```

### Graceful Degradation
```java
@Service
public class HybridSearchFallbackService {
    
    public SearchResult searchWithFallback(SearchQuery query) {
        try {
            // Attempt semantic search first
            return semanticSearchService.search(query);
            
        } catch (SemanticSearchException e) {
            LoggingUtil.warn("Semantic search failed, falling back to keyword search: {}", e.getMessage());
            
            // Graceful degradation to keyword search
            return keywordSearchService.search(query.toKeywordQuery());
            
        } catch (Exception e) {
            LoggingUtil.error("All search methods failed: {}", e.getMessage());
            throw new SearchServiceException("Search service temporarily unavailable", e);
        }
    }
}
```

---
*Generated by AI-TDD CLI - Design Document Agent*  
*Generated on: 2025-08-06T16:47:53.000Z*  
*Feature: semantic-search-integration*  
*Agent Version: 1.0.0*  
*Architecture Style: layered*  
*Input Source: PRD.md*

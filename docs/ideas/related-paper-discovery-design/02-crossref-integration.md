# 02. Crossref Integration - Citation Network Discovery

## Overview

The Crossref integration forms the backbone of Answer42's citation network discovery system, leveraging the world's largest scholarly metadata database with over 130 million records. This integration provides comprehensive bibliographic discovery through sophisticated citation analysis, author network mapping, and publication venue exploration.

## Crossref API Capabilities

### 🔍 **Core Discovery Endpoints**

**Citation Network Analysis:**
- `/works/{doi}/references` - Papers this work cites (backward citations)
- `/works/{doi}/citations` - Papers that cite this work (forward citations)
- `/works?query.bibliographic={title}` - Find papers by bibliographic similarity

**Author Network Discovery:**
- `/works?author={author_id}` - All papers by specific author
- `/works?query.author={author_name}` - Papers by author name search
- `/works?query.affiliation={institution}` - Papers by institutional affiliation

**Publication Venue Exploration:**
- `/works?publisher={publisher_id}` - Papers from specific publisher
- `/works?container-title={journal_name}` - Papers from specific journal
- `/works?query.container-title={venue}` - Venue similarity search

**Subject & Temporal Analysis:**
- `/works?subject={field}` - Papers in specific research field
- `/works?from-pub-date={date}&until-pub-date={date}` - Time-bounded discovery
- `/works?sort=published&order=desc` - Recent publications in field

## Service Architecture

### CrossrefDiscoveryService Implementation

```java
@Service
public class CrossrefDiscoveryService {
    
    private static final Logger LOG = LoggerFactory.getLogger(CrossrefDiscoveryService.class);
    private static final String CROSSREF_BASE_URL = "https://api.crossref.org";
    private static final int DEFAULT_RESULTS_LIMIT = 20;
    private static final int MAX_BATCH_SIZE = 50;
    
    private final RestTemplate restTemplate;
    private final APIRateLimitManager rateLimitManager;
    private final CrossrefResponseParser responseParser;
    private final CrossrefCache crossrefCache;
    
    public CrossrefDiscoveryService(
            RestTemplate restTemplate,
            APIRateLimitManager rateLimitManager,
            CrossrefResponseParser responseParser,
            CrossrefCache crossrefCache) {
        this.restTemplate = restTemplate;
        this.rateLimitManager = rateLimitManager;
        this.responseParser = responseParser;
        this.crossrefCache = crossrefCache;
    }
    
    /**
     * Comprehensive related paper discovery using multiple Crossref strategies
     */
    public CrossrefDiscoveryResult discoverRelatedPapers(Paper sourcePaper) {
        LoggingUtil.info(LOG, "discoverRelatedPapers", 
            "Starting Crossref discovery for paper %s", sourcePaper.getId());
        
        try {
            // Execute parallel discovery strategies
            CompletableFuture<CitationNetworkResult> citationsFuture = 
                discoverCitationNetworkAsync(sourcePaper);
            
            CompletableFuture<AuthorNetworkResult> authorsFuture = 
                discoverAuthorNetworkAsync(sourcePaper);
            
            CompletableFuture<VenueExplorationResult> venuesFuture = 
                discoverVenueNetworkAsync(sourcePaper);
            
            CompletableFuture<SubjectExplorationResult> subjectsFuture = 
                discoverSubjectNetworkAsync(sourcePaper);
            
            // Wait for all discovery operations
            CompletableFuture.allOf(citationsFuture, authorsFuture, venuesFuture, subjectsFuture).join();
            
            // Combine and analyze results
            CrossrefDiscoveryResult result = CrossrefDiscoveryResult.builder()
                .sourcePaper(sourcePaper)
                .citationNetwork(citationsFuture.join())
                .authorNetwork(authorsFuture.join())
                .venueExploration(venuesFuture.join())
                .subjectExploration(subjectsFuture.join())
                .discoveryMetadata(createDiscoveryMetadata())
                .build();
            
            LoggingUtil.info(LOG, "discoverRelatedPapers", 
                "Completed Crossref discovery for paper %s: found %d related papers", 
                sourcePaper.getId(), result.getTotalDiscoveredPapers());
            
            return result;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverRelatedPapers", 
                "Crossref discovery failed for paper %s", e, sourcePaper.getId());
            throw new CrossrefDiscoveryException("Discovery operation failed", e);
        }
    }
    
    /**
     * Discover citation networks (forward and backward citations)
     */
    private CompletableFuture<CitationNetworkResult> discoverCitationNetworkAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CitationNetworkResult.Builder builder = CitationNetworkResult.builder();
                
                // Forward citations (papers that cite this work)
                if (sourcePaper.getDoi() != null) {
                    List<DiscoveredPaper> forwardCitations = discoverForwardCitations(sourcePaper.getDoi());
                    builder.forwardCitations(forwardCitations);
                }
                
                // Backward citations (papers this work cites)  
                List<DiscoveredPaper> backwardCitations = discoverBackwardCitations(sourcePaper);
                builder.backwardCitations(backwardCitations);
                
                // Citation overlap analysis (papers that cite similar works)
                List<DiscoveredPaper> citationOverlap = discoverCitationOverlap(sourcePaper);
                builder.citationOverlap(citationOverlap);
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverCitationNetworkAsync", 
                    "Citation network discovery failed", e);
                return CitationNetworkResult.failed(e);
            }
        });
    }
    
    /**
     * Discover forward citations using DOI
     */
    private List<DiscoveredPaper> discoverForwardCitations(String doi) {
        // Check cache first
        Optional<List<DiscoveredPaper>> cached = crossrefCache.getForwardCitations(doi);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.CROSSREF).join();
        
        String url = String.format("%s/works/%s/transform/application/vnd.citationstyles.csl+json", 
            CROSSREF_BASE_URL, doi);
        
        try {
            // Get citing papers
            String citationsUrl = String.format("%s/works?query.bibliographic=%s&rows=%d", 
                CROSSREF_BASE_URL, URLEncoder.encode(doi, StandardCharsets.UTF_8), DEFAULT_RESULTS_LIMIT);
            
            CrossrefResponse response = restTemplate.getForObject(citationsUrl, CrossrefResponse.class);
            
            if (response != null && response.getStatus().equals("ok")) {
                List<DiscoveredPaper> citations = response.getMessage().getItems().stream()
                    .map(item -> responseParser.parseToDiscoveredPaper(item, DiscoverySource.CROSSREF_FORWARD_CITATION))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                // Cache results
                crossrefCache.storeForwardCitations(doi, citations);
                
                return citations;
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverForwardCitations", 
                "Failed to discover forward citations for DOI %s", e, doi);
            return new ArrayList<>();
        }
    }
    
    /**
     * Discover backward citations from paper's reference list
     */
    private List<DiscoveredPaper> discoverBackwardCitations(Paper sourcePaper) {
        List<DiscoveredPaper> backwardCitations = new ArrayList<>();
        
        // Extract DOIs from paper's citations if available
        if (sourcePaper.getCitations() != null) {
            List<String> referenceDOIs = extractDOIsFromCitations(sourcePaper.getCitations());
            
            // Batch process reference DOIs
            List<List<String>> batches = partitionDOIs(referenceDOIs, 10);
            
            for (List<String> batch : batches) {
                List<DiscoveredPaper> batchResults = processDOIBatch(batch, DiscoverySource.CROSSREF_BACKWARD_CITATION);
                backwardCitations.addAll(batchResults);
            }
        }
        
        return backwardCitations;
    }
    
    /**
     * Discover author network (other papers by same authors)
     */
    private CompletableFuture<AuthorNetworkResult> discoverAuthorNetworkAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AuthorNetworkResult.Builder builder = AuthorNetworkResult.builder();
                
                if (sourcePaper.getAuthors() != null && !sourcePaper.getAuthors().isEmpty()) {
                    
                    // Process each author
                    for (String author : sourcePaper.getAuthors()) {
                        List<DiscoveredPaper> authorPapers = discoverPapersByAuthor(author);
                        builder.addAuthorPapers(author, authorPapers);
                    }
                    
                    // Find collaborative networks
                    List<DiscoveredPaper> collaborativePapers = discoverCollaborativeNetwork(sourcePaper.getAuthors());
                    builder.collaborativePapers(collaborativePapers);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverAuthorNetworkAsync", 
                    "Author network discovery failed", e);
                return AuthorNetworkResult.failed(e);
            }
        });
    }
    
    /**
     * Discover papers by specific author
     */
    private List<DiscoveredPaper> discoverPapersByAuthor(String authorName) {
        // Check cache
        Optional<List<DiscoveredPaper>> cached = crossrefCache.getAuthorPapers(authorName);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.CROSSREF).join();
        
        try {
            String encodedAuthor = URLEncoder.encode(authorName, StandardCharsets.UTF_8);
            String url = String.format("%s/works?query.author=%s&rows=%d&sort=published&order=desc", 
                CROSSREF_BASE_URL, encodedAuthor, DEFAULT_RESULTS_LIMIT);
            
            CrossrefResponse response = restTemplate.getForObject(url, CrossrefResponse.class);
            
            if (response != null && response.getStatus().equals("ok")) {
                List<DiscoveredPaper> papers = response.getMessage().getItems().stream()
                    .map(item -> responseParser.parseToDiscoveredPaper(item, DiscoverySource.CROSSREF_AUTHOR))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                // Cache results
                crossrefCache.storeAuthorPapers(authorName, papers);
                
                return papers;
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverPapersByAuthor", 
                "Failed to discover papers by author %s", e, authorName);
            return new ArrayList<>();
        }
    }
    
    /**
     * Discover venue network (other papers from same journal/conference)
     */
    private CompletableFuture<VenueExplorationResult> discoverVenueNetworkAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                VenueExplorationResult.Builder builder = VenueExplorationResult.builder();
                
                // Same venue papers
                if (sourcePaper.getJournal() != null) {
                    List<DiscoveredPaper> sameVenuePapers = discoverPapersByVenue(sourcePaper.getJournal());
                    builder.sameVenuePapers(sameVenuePapers);
                }
                
                // Same publisher papers
                if (sourcePaper.getPublisher() != null) {
                    List<DiscoveredPaper> samePublisherPapers = discoverPapersByPublisher(sourcePaper.getPublisher());
                    builder.samePublisherPapers(samePublisherPapers);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverVenueNetworkAsync", 
                    "Venue network discovery failed", e);
                return VenueExplorationResult.failed(e);
            }
        });
    }
    
    /**
     * Discover subject network (papers in same research field)
     */
    private CompletableFuture<SubjectExplorationResult> discoverSubjectNetworkAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SubjectExplorationResult.Builder builder = SubjectExplorationResult.builder();
                
                // Extract research subjects from paper
                List<String> subjects = extractResearchSubjects(sourcePaper);
                
                for (String subject : subjects) {
                    List<DiscoveredPaper> subjectPapers = discoverPapersBySubject(subject);
                    builder.addSubjectPapers(subject, subjectPapers);
                }
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverSubjectNetworkAsync", 
                    "Subject network discovery failed", e);
                return SubjectExplorationResult.failed(e);
            }
        });
    }
}
```

## Response Data Models

### CrossrefDiscoveryResult

```java
@Data
@Builder
public class CrossrefDiscoveryResult {
    private Paper sourcePaper;
    private CitationNetworkResult citationNetwork;
    private AuthorNetworkResult authorNetwork;
    private VenueExplorationResult venueExploration;
    private SubjectExplorationResult subjectExploration;
    private DiscoveryMetadata discoveryMetadata;
    
    public int getTotalDiscoveredPapers() {
        return citationNetwork.getTotalPapers() +
               authorNetwork.getTotalPapers() +
               venueExploration.getTotalPapers() +
               subjectExploration.getTotalPapers();
    }
    
    public List<DiscoveredPaper> getAllDiscoveredPapers() {
        List<DiscoveredPaper> allPapers = new ArrayList<>();
        allPapers.addAll(citationNetwork.getAllPapers());
        allPapers.addAll(authorNetwork.getAllPapers());
        allPapers.addAll(venueExploration.getAllPapers());
        allPapers.addAll(subjectExploration.getAllPapers());
        return allPapers;
    }
}
```

### CitationNetworkResult

```java
@Data
@Builder
public class CitationNetworkResult {
    private List<DiscoveredPaper> forwardCitations;      // Papers citing this work
    private List<DiscoveredPaper> backwardCitations;     // Papers this work cites
    private List<DiscoveredPaper> citationOverlap;       // Papers with similar citation patterns
    private CitationAnalytics analytics;
    
    public int getTotalPapers() {
        return forwardCitations.size() + backwardCitations.size() + citationOverlap.size();
    }
    
    public List<DiscoveredPaper> getAllPapers() {
        List<DiscoveredPaper> allPapers = new ArrayList<>();
        allPapers.addAll(forwardCitations);
        allPapers.addAll(backwardCitations);
        allPapers.addAll(citationOverlap);
        return allPapers;
    }
    
    public static CitationNetworkResult failed(Exception error) {
        return CitationNetworkResult.builder()
            .forwardCitations(new ArrayList<>())
            .backwardCitations(new ArrayList<>())
            .citationOverlap(new ArrayList<>())
            .analytics(CitationAnalytics.failed(error))
            .build();
    }
}
```

### DiscoveredPaper

```java
@Data
@Builder
public class DiscoveredPaper {
    private String doi;
    private String title;
    private List<String> authors;
    private String journal;
    private String publisher;
    private LocalDate publishedDate;
    private Integer citationCount;
    private DiscoverySource discoverySource;
    private double relevanceScore;
    private double confidenceScore;
    private Map<String, Object> additionalMetadata;
    
    // Crossref-specific fields
    private String crossrefType;
    private String issn;
    private String isbn;
    private String volume;
    private String issue;
    private String pages;
    private List<String> subjects;
    private String abstractText;
    private List<String> references;
    private String url;
    private boolean isOpenAccess;
    private String license;
    
    /**
     * Calculate relevance score based on multiple factors
     */
    public double calculateRelevanceScore(Paper sourcePaper) {
        double score = 0.0;
        
        // Citation count influence (0-0.3)
        if (citationCount != null) {
            score += Math.min(citationCount / 1000.0, 0.3);
        }
        
        // Author overlap (0-0.2)
        if (sourcePaper.getAuthors() != null && authors != null) {
            long authorOverlap = sourcePaper.getAuthors().stream()
                .filter(authors::contains)
                .count();
            score += (authorOverlap / (double) sourcePaper.getAuthors().size()) * 0.2;
        }
        
        // Publication recency (0-0.2)
        if (publishedDate != null) {
            long yearsAgo = ChronoUnit.YEARS.between(publishedDate, LocalDate.now());
            score += Math.max(0, (10 - yearsAgo) / 10.0) * 0.2;
        }
        
        // Journal/venue match (0-0.15)
        if (journal != null && journal.equalsIgnoreCase(sourcePaper.getJournal())) {
            score += 0.15;
        }
        
        // Subject overlap (0-0.15)
        if (subjects != null && sourcePaper.getTopics() != null) {
            long subjectOverlap = subjects.stream()
                .filter(sourcePaper.getTopics()::contains)
                .count();
            if (!subjects.isEmpty()) {
                score += (subjectOverlap / (double) subjects.size()) * 0.15;
            }
        }
        
        this.relevanceScore = Math.min(score, 1.0);
        return this.relevanceScore;
    }
}
```

## Advanced Discovery Strategies

### 1. Citation Overlap Analysis

```java
@Component
public class CitationOverlapAnalyzer {
    
    /**
     * Find papers that cite similar works to the source paper
     */
    public List<DiscoveredPaper> discoverCitationOverlap(Paper sourcePaper) {
        List<DiscoveredPaper> overlapPapers = new ArrayList<>();
        
        // Extract reference DOIs from source paper
        List<String> sourceReferences = extractReferenceDOIs(sourcePaper);
        
        if (sourceReferences.isEmpty()) {
            return overlapPapers;
        }
        
        // For each reference, find papers that also cite it
        for (String referenceDOI : sourceReferences.subList(0, Math.min(5, sourceReferences.size()))) {
            List<DiscoveredPaper> citingPapers = findPapersCitingDOI(referenceDOI);
            
            // Calculate overlap score and filter
            for (DiscoveredPaper paper : citingPapers) {
                double overlapScore = calculateCitationOverlapScore(paper, sourceReferences);
                if (overlapScore > 0.3) { // Threshold for significant overlap
                    paper.setRelevanceScore(overlapScore);
                    overlapPapers.add(paper);
                }
            }
        }
        
        // Remove duplicates and sort by relevance
        return overlapPapers.stream()
            .distinct()
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .limit(10)
            .collect(Collectors.toList());
    }
    
    private double calculateCitationOverlapScore(DiscoveredPaper paper, List<String> sourceReferences) {
        if (paper.getReferences() == null || paper.getReferences().isEmpty()) {
            return 0.0;
        }
        
        long overlapCount = paper.getReferences().stream()
            .filter(sourceReferences::contains)
            .count();
        
        return overlapCount / (double) Math.max(sourceReferences.size(), paper.getReferences().size());
    }
}
```

### 2. Temporal Citation Analysis

```java
@Component
public class TemporalCitationAnalyzer {
    
    /**
     * Analyze citation patterns over time to identify trending related work
     */
    public List<DiscoveredPaper> discoverTrendingRelatedPapers(Paper sourcePaper) {
        List<DiscoveredPaper> trendingPapers = new ArrayList<>();
        
        // Get papers from the same field published in the last 2 years
        LocalDate twoYearsAgo = LocalDate.now().minusYears(2);
        
        List<DiscoveredPaper> recentPapers = discoverPapersBySubjectAndTimeRange(
            extractPrimarySubject(sourcePaper), 
            twoYearsAgo, 
            LocalDate.now()
        );
        
        // Analyze citation velocity (citations per month since publication)
        for (DiscoveredPaper paper : recentPapers) {
            double citationVelocity = calculateCitationVelocity(paper);
            if (citationVelocity > getVelocityThreshold(paper.getPublishedDate())) {
                paper.setRelevanceScore(citationVelocity);
                trendingPapers.add(paper);
            }
        }
        
        return trendingPapers.stream()
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .limit(15)
            .collect(Collectors.toList());
    }
    
    private double calculateCitationVelocity(DiscoveredPaper paper) {
        if (paper.getCitationCount() == null || paper.getPublishedDate() == null) {
            return 0.0;
        }
        
        long monthsSincePublication = ChronoUnit.MONTHS.between(
            paper.getPublishedDate(), 
            LocalDate.now()
        );
        
        if (monthsSincePublication <= 0) {
            return 0.0;
        }
        
        return paper.getCitationCount() / (double) monthsSincePublication;
    }
}
```

## Caching Strategy

### CrossrefCache Implementation

```java
@Component
public class CrossrefCache {
    
    private final AgentMemoryStoreRepository memoryRepository;
    private final Map<String, CachedResult> inMemoryCache = new ConcurrentHashMap<>();
    
    /**
     * Multi-level cache for Crossref results
     */
    public Optional<List<DiscoveredPaper>> getForwardCitations(String doi) {
        String cacheKey = "crossref_forward_" + doi;
        
        // Level 1: In-memory cache
        CachedResult inMemory = inMemoryCache.get(cacheKey);
        if (inMemory != null && !inMemory.isExpired()) {
            return Optional.of(inMemory.getPapers());
        }
        
        // Level 2: Database cache
        return memoryRepository.findByKey(cacheKey)
            .map(this::deserializePapers)
            .filter(result -> !result.isExpired())
            .map(CachedResult::getPapers);
    }
    
    public void storeForwardCitations(String doi, List<DiscoveredPaper> papers) {
        String cacheKey = "crossref_forward_" + doi;
        CachedResult cached = new CachedResult(papers, Instant.now().plus(Duration.ofHours(12)));
        
        // Store in both levels
        inMemoryCache.put(cacheKey, cached);
        
        AgentMemoryStore memory = AgentMemoryStore.builder()
            .key(cacheKey)
            .data(serializePapers(cached))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        memoryRepository.save(memory);
    }
}
```

## Rate Limiting & Error Handling

### Crossref-Specific Rate Limiting

```java
@Component
public class CrossrefRateLimiter {
    
    private final RateLimiter generalLimiter = RateLimiter.create(50.0); // 50 requests/second
    private final RateLimiter politeLimiter = RateLimiter.create(10.0);  // 10 requests/second for large queries
    
    public void acquirePermit(CrossrefRequestType requestType) {
        switch (requestType) {
            case SINGLE_PAPER_LOOKUP:
                generalLimiter.acquire();
                break;
            case BULK_CITATION_SEARCH:
                politeLimiter.acquire();
                break;
            case AUTHOR_NETWORK_SEARCH:
                politeLimiter.acquire();
                break;
            default:
                generalLimiter.acquire();
        }
    }
}
```

This comprehensive Crossref integration provides the foundation for sophisticated citation network discovery, enabling Answer42 to automatically map the complete research landscape around any uploaded paper.

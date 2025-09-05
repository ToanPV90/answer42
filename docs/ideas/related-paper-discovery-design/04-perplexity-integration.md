# 04. Perplexity Integration - Real-time Trends & Open Access

## Overview

The Perplexity integration provides real-time research trend analysis and current academic discussion monitoring for Answer42's paper discovery system. Leveraging Perplexity's live web search capabilities and academic focus, this integration captures the most current research developments, ongoing debates, and emerging trends that traditional citation databases might miss.

## Perplexity API Capabilities

### 🌐 **Real-time Research Intelligence**

**Current Research Trends:**
- Live academic paper discovery from recent publications
- Real-time tracking of emerging research topics
- Current academic discussions and debates
- Press coverage and media attention analysis

**Open Access Discovery:**
- Recent open access publications in related fields
- Preprint server monitoring (arXiv, bioRxiv, etc.)
- Institutional repository discoveries
- Free-to-access versions of paywalled papers

**Academic Discussion Monitoring:**
- Conference presentation tracking
- Research blog and forum discussions
- Social media academic conversations
- Expert opinion and commentary tracking

**Trend Analysis:**
- Identification of rising research areas
- Citation velocity tracking for recent papers
- Research funding trend analysis
- Interdisciplinary connection discovery

## Service Architecture

### PerplexityTrendService Implementation

```java
@Service
public class PerplexityTrendService {
    
    private static final Logger LOG = LoggerFactory.getLogger(PerplexityTrendService.class);
    private static final String PERPLEXITY_MODEL = "llama-3.1-sonar-small-128k-online";
    private static final int MAX_SEARCH_RESULTS = 15;
    
    private final ChatClient perplexityClient;
    private final APIRateLimitManager rateLimitManager;
    private final PerplexityResponseParser responseParser;
    private final TrendAnalysisCache trendCache;
    private final ResearchTrendAnalyzer trendAnalyzer;
    
    public PerplexityTrendService(
            ChatClient perplexityClient,
            APIRateLimitManager rateLimitManager,
            PerplexityResponseParser responseParser,
            TrendAnalysisCache trendCache,
            ResearchTrendAnalyzer trendAnalyzer) {
        this.perplexityClient = perplexityClient;
        this.rateLimitManager = rateLimitManager;
        this.responseParser = responseParser;
        this.trendCache = trendCache;
        this.trendAnalyzer = trendAnalyzer;
    }
    
    /**
     * Comprehensive trend analysis using Perplexity's real-time capabilities
     */
    public PerplexityTrendResult analyzeTrends(Paper sourcePaper) {
        LoggingUtil.info(LOG, "analyzeTrends", 
            "Starting Perplexity trend analysis for paper %s", sourcePaper.getId());
        
        try {
            // Execute parallel trend analysis strategies
            CompletableFuture<CurrentResearchTrends> currentTrendsFuture = 
                analyzeCurrentResearchTrendsAsync(sourcePaper);
            
            CompletableFuture<OpenAccessDiscovery> openAccessFuture = 
                discoverOpenAccessPapersAsync(sourcePaper);
            
            CompletableFuture<AcademicDiscussions> discussionsFuture = 
                monitorAcademicDiscussionsAsync(sourcePaper);
            
            CompletableFuture<EmergingTopics> emergingTopicsFuture = 
                identifyEmergingTopicsAsync(sourcePaper);
            
            CompletableFuture<ExpertOpinions> expertOpinionsFuture = 
                gatherExpertOpinionsAsync(sourcePaper);
            
            // Wait for all trend analysis operations
            CompletableFuture.allOf(currentTrendsFuture, openAccessFuture, discussionsFuture, 
                                   emergingTopicsFuture, expertOpinionsFuture).join();
            
            // Synthesize and analyze results
            PerplexityTrendResult result = PerplexityTrendResult.builder()
                .sourcePaper(sourcePaper)
                .currentTrends(currentTrendsFuture.join())
                .openAccessDiscovery(openAccessFuture.join())
                .academicDiscussions(discussionsFuture.join())
                .emergingTopics(emergingTopicsFuture.join())
                .expertOpinions(expertOpinionsFuture.join())
                .trendMetadata(createTrendMetadata())
                .build();
            
            LoggingUtil.info(LOG, "analyzeTrends", 
                "Completed Perplexity trend analysis for paper %s: found %d trending papers", 
                sourcePaper.getId(), result.getTotalDiscoveredPapers());
            
            return result;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "analyzeTrends", 
                "Perplexity trend analysis failed for paper %s", e, sourcePaper.getId());
            throw new PerplexityTrendException("Trend analysis operation failed", e);
        }
    }
    
    /**
     * Analyze current research trends in the paper's field
     */
    private CompletableFuture<CurrentResearchTrends> analyzeCurrentResearchTrendsAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                Optional<CurrentResearchTrends> cached = trendCache.getCurrentTrends(
                    sourcePaper.getPrimaryField(), Duration.ofHours(6));
                if (cached.isPresent()) {
                    return cached.get();
                }
                
                CurrentResearchTrends.Builder builder = CurrentResearchTrends.builder();
                
                // Analyze current trends in the research field
                List<TrendingTopic> fieldTrends = analyzeFieldTrends(sourcePaper);
                builder.fieldTrends(fieldTrends);
                
                // Find recently published papers gaining attention
                List<DiscoveredPaper> trendingPapers = findTrendingPapers(sourcePaper);
                builder.trendingPapers(trendingPapers);
                
                // Identify hot research questions
                List<ResearchQuestion> hotQuestions = identifyHotResearchQuestions(sourcePaper);
                builder.hotResearchQuestions(hotQuestions);
                
                // Analyze citation velocity trends
                CitationVelocityTrends velocityTrends = analyzeCitationVelocityTrends(sourcePaper);
                builder.citationVelocityTrends(velocityTrends);
                
                CurrentResearchTrends result = builder.build();
                
                // Cache results
                trendCache.storeCurrentTrends(sourcePaper.getPrimaryField(), result);
                
                return result;
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "analyzeCurrentResearchTrendsAsync", 
                    "Current trends analysis failed", e);
                return CurrentResearchTrends.failed(e);
            }
        });
    }
    
    /**
     * Find trending papers in the field using real-time search
     */
    private List<DiscoveredPaper> findTrendingPapers(Paper sourcePaper) {
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.PERPLEXITY).join();
        
        try {
            String searchQuery = buildTrendingPapersQuery(sourcePaper);
            
            Prompt trendingPrompt = Prompt.from("""
                Find the most trending and discussed academic papers in {field} from the last 6 months.
                Focus on papers that are gaining significant attention, being widely cited, or generating discussion.
                
                Research Field: {field}
                Related Keywords: {keywords}
                Original Paper Context: {context}
                
                For each trending paper, provide:
                1. Full citation with DOI if available
                2. Why it's currently trending (citations, discussions, impact)
                3. How it relates to the original paper
                4. Current discussion points or controversies
                5. Recent citation count or attention metrics
                6. Open access availability
                
                Focus on papers published in 2024-2025 that are gaining momentum.
                Include both traditional publications and preprints if they're getting attention.
                """, Map.of(
                    "field", sourcePaper.getPrimaryField(),
                    "keywords", String.join(", ", sourcePaper.getKeywords()),
                    "context", sourcePaper.getTitle() + " - " + sourcePaper.getAbstract()
                ));
            
            ChatResponse response = perplexityClient.prompt(trendingPrompt).call().chatResponse();
            
            return responseParser.parseTrendingPapers(
                response.getResult().getOutput().getContent(), 
                DiscoverySource.PERPLEXITY_TRENDING);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "findTrendingPapers", 
                "Failed to find trending papers for field %s", e, sourcePaper.getPrimaryField());
            return new ArrayList<>();
        }
    }
    
    /**
     * Discover open access papers and preprints
     */
    private CompletableFuture<OpenAccessDiscovery> discoverOpenAccessPapersAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OpenAccessDiscovery.Builder builder = OpenAccessDiscovery.builder();
                
                // Find recent open access papers
                List<DiscoveredPaper> openAccessPapers = findOpenAccessPapers(sourcePaper);
                builder.openAccessPapers(openAccessPapers);
                
                // Discover relevant preprints
                List<DiscoveredPaper> preprints = findRelevantPreprints(sourcePaper);
                builder.relevantPreprints(preprints);
                
                // Find free versions of paywalled papers
                List<DiscoveredPaper> freeVersions = findFreeVersions(sourcePaper);
                builder.freeVersions(freeVersions);
                
                // Repository discoveries
                List<RepositoryPaper> repositoryPapers = discoverRepositoryPapers(sourcePaper);
                builder.repositoryPapers(repositoryPapers);
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverOpenAccessPapersAsync", 
                    "Open access discovery failed", e);
                return OpenAccessDiscovery.failed(e);
            }
        });
    }
    
    /**
     * Find recent open access papers using Perplexity search
     */
    private List<DiscoveredPaper> findOpenAccessPapers(Paper sourcePaper) {
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.PERPLEXITY).join();
        
        try {
            Prompt openAccessPrompt = Prompt.from("""
                Find recent open access academic papers related to this research topic.
                Search for papers published in the last 12 months that are freely accessible.
                
                Research Topic: {title}
                Field: {field}
                Keywords: {keywords}
                
                Look for papers in:
                1. Open access journals (PLOS, BMC, Nature Communications, etc.)
                2. Institutional repositories
                3. Preprint servers (arXiv, bioRxiv, medRxiv, etc.)
                4. Government research repositories
                5. Publisher open access collections
                
                For each paper, verify it's actually open access and provide:
                - Full citation with DOI
                - Direct link to free PDF
                - Repository or platform where it's hosted
                - Relevance to the original research
                - Quality indicators (journal impact, author credentials)
                
                Focus on high-quality, peer-reviewed papers when possible.
                """, Map.of(
                    "title", sourcePaper.getTitle(),
                    "field", sourcePaper.getPrimaryField(),
                    "keywords", String.join(", ", sourcePaper.getKeywords())
                ));
            
            ChatResponse response = perplexityClient.prompt(openAccessPrompt).call().chatResponse();
            
            return responseParser.parseOpenAccessPapers(
                response.getResult().getOutput().getContent(),
                DiscoverySource.PERPLEXITY_OPEN_ACCESS);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "findOpenAccessPapers", 
                "Failed to find open access papers", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Monitor current academic discussions and debates
     */
    private CompletableFuture<AcademicDiscussions> monitorAcademicDiscussionsAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AcademicDiscussions.Builder builder = AcademicDiscussions.builder();
                
                // Find current academic debates
                List<AcademicDebate> currentDebates = findCurrentDebates(sourcePaper);
                builder.currentDebates(currentDebates);
                
                // Monitor conference discussions
                List<ConferenceDiscussion> conferenceDiscussions = monitorConferenceDiscussions(sourcePaper);
                builder.conferenceDiscussions(conferenceDiscussions);
                
                // Track social media academic conversations
                List<SocialMediaDiscussion> socialDiscussions = trackSocialMediaDiscussions(sourcePaper);
                builder.socialMediaDiscussions(socialDiscussions);
                
                // Find expert commentary
                List<ExpertCommentary> expertCommentary = findExpertCommentary(sourcePaper);
                builder.expertCommentary(expertCommentary);
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "monitorAcademicDiscussionsAsync", 
                    "Academic discussions monitoring failed", e);
                return AcademicDiscussions.failed(e);
            }
        });
    }
    
    /**
     * Find current academic debates and controversies
     */
    private List<AcademicDebate> findCurrentDebates(Paper sourcePaper) {
        // Acquire rate limit permit
        rateLimitManager.acquirePermit(APIProvider.PERPLEXITY).join();
        
        try {
            Prompt debatesPrompt = Prompt.from("""
                Find current academic debates, controversies, or discussions related to this research area.
                Look for ongoing scientific disagreements, methodological debates, or conflicting findings.
                
                Research Area: {field}
                Paper Topic: {title}
                Context: {abstract}
                
                Search for:
                1. Recent papers with conflicting conclusions
                2. Methodological debates in the field
                3. Replication crises or failed reproductions
                4. Controversial findings or interpretations
                5. Editorial responses and commentary
                6. Conference panel discussions or debates
                
                For each debate, provide:
                - Clear description of the controversy
                - Key papers or researchers involved
                - Different viewpoints and their supporters
                - Current status of the debate
                - Potential implications for the field
                - Recent developments or resolutions
                
                Focus on debates that are active in 2024-2025.
                """, Map.of(
                    "field", sourcePaper.getPrimaryField(),
                    "title", sourcePaper.getTitle(),
                    "abstract", sourcePaper.getAbstract()
                ));
            
            ChatResponse response = perplexityClient.prompt(debatesPrompt).call().chatResponse();
            
            return responseParser.parseAcademicDebates(
                response.getResult().getOutput().getContent());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "findCurrentDebates", 
                "Failed to find current debates", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Identify emerging research topics and directions
     */
    private CompletableFuture<EmergingTopics> identifyEmergingTopicsAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EmergingTopics.Builder builder = EmergingTopics.builder();
                
                // Identify new research directions
                List<ResearchDirection> newDirections = identifyNewResearchDirections(sourcePaper);
                builder.newResearchDirections(newDirections);
                
                // Find interdisciplinary connections
                List<InterdisciplinaryConnection> interdisciplinary = findInterdisciplinaryConnections(sourcePaper);
                builder.interdisciplinaryConnections(interdisciplinary);
                
                // Analyze funding trends
                List<FundingTrend> fundingTrends = analyzeFundingTrends(sourcePaper);
                builder.fundingTrends(fundingTrends);
                
                // Technology impact analysis
                List<TechnologyImpact> techImpacts = analyzeTechnologyImpacts(sourcePaper);
                builder.technologyImpacts(techImpacts);
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "identifyEmergingTopicsAsync", 
                    "Emerging topics identification failed", e);
                return EmergingTopics.failed(e);
            }
        });
    }
    
    /**
     * Gather expert opinions and commentary
     */
    private CompletableFuture<ExpertOpinions> gatherExpertOpinionsAsync(Paper sourcePaper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ExpertOpinions.Builder builder = ExpertOpinions.builder();
                
                // Find expert commentary on the research area
                List<ExpertCommentary> commentary = findExpertCommentary(sourcePaper);
                builder.expertCommentary(commentary);
                
                // Identify thought leaders
                List<ThoughtLeader> thoughtLeaders = identifyThoughtLeaders(sourcePaper);
                builder.thoughtLeaders(thoughtLeaders);
                
                // Find policy implications
                List<PolicyImplication> policyImplications = findPolicyImplications(sourcePaper);
                builder.policyImplications(policyImplications);
                
                // Media coverage analysis
                List<MediaCoverage> mediaCoverage = analyzeMediaCoverage(sourcePaper);
                builder.mediaCoverage(mediaCoverage);
                
                return builder.build();
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "gatherExpertOpinionsAsync", 
                    "Expert opinions gathering failed", e);
                return ExpertOpinions.failed(e);
            }
        });
    }
}
```

## Advanced Trend Analysis

### Real-time Trend Detection

```java
@Component
public class ResearchTrendAnalyzer {
    
    /**
     * Analyze research trends using temporal citation and discussion patterns
     */
    public TrendAnalysisResult analyzeTrends(List<DiscoveredPaper> papers, String researchField) {
        TrendAnalysisResult.Builder builder = TrendAnalysisResult.builder();
        
        // Temporal analysis of paper publication patterns
        Map<String, Integer> monthlyPublications = analyzePublicationTrends(papers);
        builder.publicationTrends(monthlyPublications);
        
        // Keyword trend analysis
        Map<String, Double> keywordTrends = analyzeKeywordTrends(papers);
        builder.keywordTrends(keywordTrends);
        
        // Citation velocity analysis
        Map<String, Double> citationVelocity = analyzeCitationVelocityTrends(papers);
        builder.citationVelocityTrends(citationVelocity);
        
        // Collaboration network trends
        Map<String, Integer> collaborationTrends = analyzeCollaborationTrends(papers);
        builder.collaborationTrends(collaborationTrends);
        
        // Research methodology trends
        Map<String, Double> methodTrends = analyzeMethodologyTrends(papers);
        builder.methodologyTrends(methodTrends);
        
        return builder.build();
    }
    
    private Map<String, Double> analyzeKeywordTrends(List<DiscoveredPaper> papers) {
        Map<String, List<LocalDate>> keywordDates = new HashMap<>();
        
        // Collect keyword usage over time
        for (DiscoveredPaper paper : papers) {
            if (paper.getKeywords() != null && paper.getPublishedDate() != null) {
                for (String keyword : paper.getKeywords()) {
                    keywordDates.computeIfAbsent(keyword, k -> new ArrayList<>())
                        .add(paper.getPublishedDate());
                }
            }
        }
        
        // Calculate trend scores (increasing usage over time)
        Map<String, Double> trends = new HashMap<>();
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        
        for (Map.Entry<String, List<LocalDate>> entry : keywordDates.entrySet()) {
            List<LocalDate> dates = entry.getValue();
            if (dates.size() >= 3) { // Need minimum occurrences
                long recentCount = dates.stream()
                    .filter(date -> date.isAfter(sixMonthsAgo))
                    .count();
                
                double trendScore = recentCount / (double) dates.size();
                trends.put(entry.getKey(), trendScore);
            }
        }
        
        return trends.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(20)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
}
```

## Data Models

### PerplexityTrendResult

```java
@Data
@Builder
public class PerplexityTrendResult {
    private Paper sourcePaper;
    private CurrentResearchTrends currentTrends;
    private OpenAccessDiscovery openAccessDiscovery;
    private AcademicDiscussions academicDiscussions;
    private EmergingTopics emergingTopics;
    private ExpertOpinions expertOpinions;
    private TrendMetadata trendMetadata;
    
    public int getTotalDiscoveredPapers() {
        return currentTrends.getTotalPapers() +
               openAccessDiscovery.getTotalPapers() +
               academicDiscussions.getTotalPapers() +
               emergingTopics.getTotalPapers() +
               expertOpinions.getTotalPapers();
    }
    
    public List<DiscoveredPaper> getAllDiscoveredPapers() {
        List<DiscoveredPaper> allPapers = new ArrayList<>();
        allPapers.addAll(currentTrends.getAllPapers());
        allPapers.addAll(openAccessDiscovery.getAllPapers());
        allPapers.addAll(academicDiscussions.getAllPapers());
        allPapers.addAll(emergingTopics.getAllPapers());
        allPapers.addAll(expertOpinions.getAllPapers());
        return allPapers;
    }
    
    public double getTrendScore() {
        return trendMetadata.getOverallTrendScore();
    }
}
```

### TrendingTopic

```java
@Data
@Builder
public class TrendingTopic {
    private String topic;
    private double trendScore;
    private int paperCount;
    private LocalDate firstSeen;
    private LocalDate lastUpdated;
    private List<String> keywords;
    private List<String> relatedFields;
    private TrendDirection direction;
    private double velocity;
    private String description;
    
    public boolean isHotTrend() {
        return trendScore > 0.8 && velocity > 0.5;
    }
    
    public boolean isEmergingTrend() {
        return ChronoUnit.DAYS.between(firstSeen, LocalDate.now()) < 90 && trendScore > 0.6;
    }
}
```

## Caching and Performance

### Trend Analysis Caching

```java
@Component
public class TrendAnalysisCache {
    
    private final AgentMemoryStoreRepository memoryRepository;
    private final LoadingCache<String, CurrentResearchTrends> trendsCache;
    
    public TrendAnalysisCache(AgentMemoryStoreRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
        this.trendsCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(4)) // Trends change frequently
            .build(this::loadTrends);
    }
    
    /**
     * Cache current trends with time-aware expiration
     */
    public Optional<CurrentResearchTrends> getCurrentTrends(String field, Duration maxAge) {
        try {
            String cacheKey = "trends_" + field;
            
            // Check in-memory cache first
            CurrentResearchTrends trends = trendsCache.get(cacheKey);
            if (trends != null && !isStale(trends, maxAge)) {
                return Optional.of(trends);
            }
            
            // Check persistent cache
            return memoryRepository.findByKey(cacheKey)
                .map(this::deserializeTrends)
                .filter(result -> !isStale(result, maxAge));
                
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "getCurrentTrends", "Cache access failed", e);
            return Optional.empty();
        }
    }
    
    public void storeCurrentTrends(String field, CurrentResearchTrends trends) {
        String cacheKey = "trends_" + field;
        
        // Store in memory cache
        trendsCache.put(cacheKey, trends);
        
        // Store in persistent cache
        AgentMemoryStore memory = AgentMemoryStore.builder()
            .key(cacheKey)
            .data(serializeTrends(trends))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        memoryRepository.save(memory);
    }
    
    private boolean isStale(CurrentResearchTrends trends, Duration maxAge) {
        return Duration.between(trends.getAnalysisTime(), Instant.now()).compareTo(maxAge) > 0;
    }
}
```

This Perplexity integration provides real-time research intelligence, enabling Answer42 to capture the most current developments in any research field and identify emerging trends that users might otherwise miss.

# 05b. Discovery Components - Supporting Services

## Overview

This document details the supporting components for the Discovery Orchestrator: diversity optimization, performance monitoring, error handling, and multi-agent pipeline integration.

## Diversity Optimization

### DiversityOptimizer Implementation

```java
@Component
public class DiversityOptimizer {

    private static final Logger LOG = LoggerFactory.getLogger(DiversityOptimizer.class);

    /**
     * Optimize paper selection for maximum diversity across multiple dimensions
     */
    public List<DiscoveredPaper> optimizeForDiversity(
            List<DiscoveredPaper> papers, 
            DiversityLevel diversityLevel) {

        if (papers.size() <= 20) {
            return papers; // No need to optimize small sets
        }

        LoggingUtil.info(LOG, "optimizeForDiversity", 
            "Optimizing %d papers for diversity level %s", papers.size(), diversityLevel);

        switch (diversityLevel) {
            case HIGH:
                return maximizeDiversity(papers, 0.7); // 70% diversity, 30% relevance
            case MEDIUM:
                return balanceDiversityAndRelevance(papers, 0.5); // 50/50 balance
            case LOW:
                return prioritizeRelevance(papers, 0.3); // 30% diversity, 70% relevance
            default:
                return papers;
        }
    }

    /**
     * Maximize diversity using multi-dimensional clustering
     */
    private List<DiscoveredPaper> maximizeDiversity(List<DiscoveredPaper> papers, double diversityWeight) {
        // Group papers by multiple diversity dimensions
        Map<String, List<DiscoveredPaper>> venueGroups = groupByVenue(papers);
        Map<String, List<DiscoveredPaper>> timeGroups = groupByTimeperiod(papers);
        Map<String, List<DiscoveredPaper>> topicGroups = groupByTopics(papers);
        Map<String, List<DiscoveredPaper>> authorGroups = groupByAuthors(papers);

        List<DiscoveredPaper> diverseSelection = new ArrayList<>();
        Set<String> usedPapers = new HashSet<>();

        // Round-robin selection across all dimensions
        int maxIterations = Math.min(papers.size(), 50);

        for (int i = 0; i < maxIterations && diverseSelection.size() < papers.size(); i++) {
            // Select from different venue groups
            selectFromGroup(venueGroups, diverseSelection, usedPapers, 1);
            // Select from different time periods
            selectFromGroup(timeGroups, diverseSelection, usedPapers, 1);
            // Select from different topic areas
            selectFromGroup(topicGroups, diverseSelection, usedPapers, 1);
            // Select from different author networks
            selectFromGroup(authorGroups, diverseSelection, usedPapers, 1);
        }

        // Fill remaining slots with highest-scored papers not yet selected
        papers.stream()
            .filter(paper -> !usedPapers.contains(paper.getId()))
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .limit(papers.size() - diverseSelection.size())
            .forEach(diverseSelection::add);

        return diverseSelection;
    }

    /**
     * Balance diversity and relevance
     */
    private List<DiscoveredPaper> balanceDiversityAndRelevance(List<DiscoveredPaper> papers, double balance) {
        // Sort by relevance first
        List<DiscoveredPaper> relevanceSorted = papers.stream()
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .collect(Collectors.toList());

        // Take top 60% by relevance, then diversify within that set
        int topRelevantCount = (int) (papers.size() * 0.6);
        List<DiscoveredPaper> topRelevant = relevanceSorted.stream()
            .limit(topRelevantCount)
            .collect(Collectors.toList());

        // Apply diversity optimization to top relevant papers
        return maximizeDiversity(topRelevant, balance);
    }

    /**
     * Prioritize relevance with minimal diversity
     */
    private List<DiscoveredPaper> prioritizeRelevance(List<DiscoveredPaper> papers, double diversityWeight) {
        // Mostly relevance-based with light diversity touch
        List<DiscoveredPaper> relevanceSorted = papers.stream()
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .collect(Collectors.toList());

        // Take top 80% by relevance, apply light diversity
        int topCount = (int) (papers.size() * 0.8);
        List<DiscoveredPaper> topPapers = relevanceSorted.stream()
            .limit(topCount)
            .collect(Collectors.toList());

        return applyLightDiversification(topPapers);
    }

    /**
     * Group papers by publication venue for diversity
     */
    private Map<String, List<DiscoveredPaper>> groupByVenue(List<DiscoveredPaper> papers) {
        return papers.stream()
            .filter(paper -> paper.getJournal() != null)
            .collect(Collectors.groupingBy(DiscoveredPaper::getJournal));
    }

    /**
     * Group papers by time period for temporal diversity
     */
    private Map<String, List<DiscoveredPaper>> groupByTimeperiod(List<DiscoveredPaper> papers) {
        return papers.stream()
            .filter(paper -> paper.getPublishedDate() != null)
            .collect(Collectors.groupingBy(paper -> {
                int year = paper.getPublishedDate().getYear();
                if (year >= 2020) return "recent";
                else if (year >= 2015) return "modern";
                else if (year >= 2010) return "established";
                else return "classic";
            }));
    }

    /**
     * Group papers by topic areas (simplified)
     */
    private Map<String, List<DiscoveredPaper>> groupByTopics(List<DiscoveredPaper> papers) {
        return papers.stream()
            .collect(Collectors.groupingBy(paper -> {
                // Simplified topic extraction from title and journal
                String title = paper.getTitle().toLowerCase();
                String journal = paper.getJournal() != null ? paper.getJournal().toLowerCase() : "";

                if (title.contains("machine learning") || title.contains("neural") || journal.contains("ai")) {
                    return "ai_ml";
                } else if (title.contains("medicine") || title.contains("clinical") || journal.contains("medical")) {
                    return "medical";
                } else if (title.contains("biology") || title.contains("genetic") || journal.contains("bio")) {
                    return "biology";
                } else if (title.contains("physics") || title.contains("quantum") || journal.contains("physics")) {
                    return "physics";
                } else {
                    return "general";
                }
            }));
    }

    /**
     * Group papers by author networks (simplified)
     */
    private Map<String, List<DiscoveredPaper>> groupByAuthors(List<DiscoveredPaper> papers) {
        return papers.stream()
            .filter(paper -> paper.getAuthors() != null && !paper.getAuthors().isEmpty())
            .collect(Collectors.groupingBy(paper -> {
                // Group by first author's last name initial
                String firstAuthor = paper.getAuthors().get(0);
                return firstAuthor.substring(0, 1).toUpperCase();
            }));
    }

    /**
     * Select papers from groups ensuring diversity
     */
    private void selectFromGroup(
            Map<String, List<DiscoveredPaper>> groups,
            List<DiscoveredPaper> selection,
            Set<String> usedPapers,
            int count) {

        for (Map.Entry<String, List<DiscoveredPaper>> entry : groups.entrySet()) {
            List<DiscoveredPaper> candidates = entry.getValue().stream()
                .filter(paper -> !usedPapers.contains(paper.getId()))
                .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
                .limit(count)
                .collect(Collectors.toList());

            for (DiscoveredPaper candidate : candidates) {
                if (!usedPapers.contains(candidate.getId())) {
                    selection.add(candidate);
                    usedPapers.add(candidate.getId());
                }
            }
        }
    }

    /**
     * Apply light diversification to maintain relevance priority
     */
    private List<DiscoveredPaper> applyLightDiversification(List<DiscoveredPaper> papers) {
        List<DiscoveredPaper> result = new ArrayList<>();
        Set<String> usedVenues = new HashSet<>();
        Set<String> usedTopics = new HashSet<>();

        for (DiscoveredPaper paper : papers) {
            String venue = paper.getJournal();
            String topic = extractSimpleTopic(paper);

            // Prefer papers from new venues and topics, but don't exclude high-relevance papers
            boolean newVenue = venue == null || !usedVenues.contains(venue);
            boolean newTopic = !usedTopics.contains(topic);

            if (newVenue || newTopic || paper.getRelevanceScore() > 0.8) {
                result.add(paper);
                if (venue != null) usedVenues.add(venue);
                usedTopics.add(topic);
            }
        }

        return result;
    }

    private String extractSimpleTopic(DiscoveredPaper paper) {
        String title = paper.getTitle().toLowerCase();
        if (title.contains("neural") || title.contains("learning")) return "ml";
        if (title.contains("clinical") || title.contains("patient")) return "clinical";
        if (title.contains("quantum") || title.contains("physics")) return "physics";
        return "general";
    }
}
```

## Performance Monitoring

### DiscoveryPerformanceMonitor

```java
@Component
public class DiscoveryPerformanceMonitor {

    private final MeterRegistry meterRegistry;

    public DiscoveryPerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record discovery execution metrics
     */
    public void recordDiscoveryExecution(DiscoveryExecution execution, UnifiedDiscoveryResult result) {
        Duration totalTime = Duration.between(execution.getStartTime(), execution.getEndTime());

        // Record overall execution time
        Timer.builder("discovery.execution.time")
            .tag("mode", execution.getConfiguration().getMode().name())
            .tag("sources", String.valueOf(execution.getSourceResults().size()))
            .register(meterRegistry)
            .record(totalTime);

        // Record results count
        Counter.builder("discovery.results.count")
            .tag("mode", execution.getConfiguration().getMode().name())
            .register(meterRegistry)
            .increment(result.getTotalDiscoveredPapers());

        // Record source success rates
        for (SourceDiscoveryResult sourceResult : execution.getSourceResults()) {
            Counter.builder("discovery.source.requests")
                .tag("source", sourceResult.getSource().name())
                .tag("success", String.valueOf(sourceResult.isSuccess()))
                .register(meterRegistry)
                .increment();
        }

        // Record cache performance
        boolean cacheHit = result.getSynthesisMetadata().isCacheHit();
        Counter.builder("discovery.cache.requests")
            .tag("hit", String.valueOf(cacheHit))
            .register(meterRegistry)
            .increment();
    }

    /**
     * Get current discovery performance summary
     */
    public DiscoveryPerformanceSummary getPerformanceSummary() {
        return DiscoveryPerformanceSummary.builder()
            .averageExecutionTime(getAverageExecutionTime())
            .successRate(getOverallSuccessRate())
            .cacheHitRate(getCacheHitRate())
            .sourcePerformance(getSourcePerformance())
            .build();
    }

    private Duration getAverageExecutionTime() {
        // Implementation would query metrics for average execution time
        return Duration.ofSeconds(30); // Placeholder
    }

    private double getOverallSuccessRate() {
        // Implementation would calculate from success/failure counters
        return 0.95; // Placeholder
    }

    private double getCacheHitRate() {
        // Implementation would calculate from cache hit/miss counters
        return 0.75; // Placeholder
    }

    private Map<String, Double> getSourcePerformance() {
        // Implementation would query per-source metrics
        return Map.of(
            "CROSSREF", 0.98,
            "SEMANTIC_SCHOLAR", 0.92,
            "PERPLEXITY", 0.88
        );
    }
}
```

## Error Handling Strategy

### DiscoveryErrorHandler

```java
@Component
public class DiscoveryErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryErrorHandler.class);

    /**
     * Handle discovery failures with graceful degradation
     */
    public UnifiedDiscoveryResult handleDiscoveryFailure(
            Paper sourcePaper,
            DiscoveryConfiguration config,
            List<SourceDiscoveryResult> partialResults,
            Exception primaryError) {

        LoggingUtil.warn(LOG, "handleDiscoveryFailure", 
            "Discovery partially failed, attempting graceful degradation for paper %s", 
            sourcePaper.getId());

        try {
            // Filter successful results
            List<SourceDiscoveryResult> successfulResults = partialResults.stream()
                .filter(SourceDiscoveryResult::isSuccess)
                .collect(Collectors.toList());

            if (successfulResults.isEmpty()) {
                // Complete failure - return minimal result with error info
                return createMinimalErrorResult(sourcePaper, config, primaryError);
            }

            // Partial success - process available results
            List<DiscoveredPaper> availablePapers = successfulResults.stream()
                .flatMap(result -> result.getDiscoveredPapers().stream())
                .collect(Collectors.toList());

            // Apply basic processing
            List<DiscoveredPaper> processedPapers = applyBasicProcessing(availablePapers, sourcePaper);

            // Create partial result with warning
            SynthesisMetadata metadata = SynthesisMetadata.builder()
                .partialResult(true)
                .failedSources(getFailedSources(partialResults))
                .processingWarnings(List.of("Some discovery sources failed"))
                .overallConfidence(0.7) // Reduced confidence for partial results
                .build();

            return UnifiedDiscoveryResult.builder()
                .sourcePaper(sourcePaper)
                .discoveredPapers(processedPapers)
                .sourceResults(successfulResults)
                .synthesisMetadata(metadata)
                .configuration(config)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "handleDiscoveryFailure", 
                "Graceful degradation failed", e);
            return createMinimalErrorResult(sourcePaper, config, e);
        }
    }

    private UnifiedDiscoveryResult createMinimalErrorResult(
            Paper sourcePaper, 
            DiscoveryConfiguration config, 
            Exception error) {

        SynthesisMetadata errorMetadata = SynthesisMetadata.builder()
            .partialResult(false)
            .processingErrors(List.of(error.getMessage()))
            .overallConfidence(0.0)
            .build();

        return UnifiedDiscoveryResult.builder()
            .sourcePaper(sourcePaper)
            .discoveredPapers(new ArrayList<>())
            .sourceResults(new ArrayList<>())
            .synthesisMetadata(errorMetadata)
            .configuration(config)
            .build();
    }

    private List<DiscoveredPaper> applyBasicProcessing(List<DiscoveredPaper> papers, Paper sourcePaper) {
        // Basic deduplication and scoring
        return papers.stream()
            .distinct()
            .sorted(Comparator.comparing(DiscoveredPaper::getRelevanceScore).reversed())
            .limit(20) // Limit for error scenarios
            .collect(Collectors.toList());
    }

    private List<String> getFailedSources(List<SourceDiscoveryResult> results) {
        return results.stream()
            .filter(result -> !result.isSuccess())
            .map(result -> result.getSource().name())
            .collect(Collectors.toList());
    }
}
```

## Integration with Multi-Agent Pipeline

### DiscoveryPipelineIntegration

```java
@Service
public class DiscoveryPipelineIntegration {

    private final RelatedPaperDiscoveryOrchestrator discoveryOrchestrator;
    private final AgentTaskService agentTaskService;
    private final PaperRepository paperRepository;

    public DiscoveryPipelineIntegration(
            RelatedPaperDiscoveryOrchestrator discoveryOrchestrator,
            AgentTaskService agentTaskService,
            PaperRepository paperRepository) {
        this.discoveryOrchestrator = discoveryOrchestrator;
        this.agentTaskService = agentTaskService;
        this.paperRepository = paperRepository;
    }

    /**
     * Execute discovery as part of the multi-agent pipeline
     */
    @Async
    public CompletableFuture<AgentResult> executeDiscoveryAgent(AgentTask task) {
        try {
            String paperId = task.getInput().get("paperId").asText();
            Paper sourcePaper = paperRepository.findById(UUID.fromString(paperId))
                .orElseThrow(() -> new EntityNotFoundException("Paper not found: " + paperId));

            // Configure discovery based on task parameters
            DiscoveryConfiguration config = buildDiscoveryConfiguration(task);

            // Execute discovery
            UnifiedDiscoveryResult result = discoveryOrchestrator
                .discoverRelatedPapers(sourcePaper, config).get();

            // Convert to agent result
            return CompletableFuture.completedFuture(
                AgentResult.success(task.getId(), convertToAgentData(result)));

        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeDiscoveryAgent", 
                "Discovery agent execution failed", e);
            return CompletableFuture.completedFuture(
                AgentResult.failure(task.getId(), e.getMessage()));
        }
    }

    private DiscoveryConfiguration buildDiscoveryConfiguration(AgentTask task) {
        JsonNode params = task.getInput();

        return DiscoveryConfiguration.builder()
            .mode(DiscoveryMode.valueOf(
                params.path("mode").asText("COMPREHENSIVE")))
            .includeCrossref(params.path("includeCrossref").asBoolean(true))
            .includeSemanticScholar(params.path("includeSemanticScholar").asBoolean(true))
            .includePerplexity(params.path("includePerplexity").asBoolean(true))
            .maxResults(params.path("maxResults").asInt(50))
            .diversityLevel(DiversityLevel.valueOf(
                params.path("diversityLevel").asText("MEDIUM")))
            .maxExecutionTime(Duration.ofMinutes(
                params.path("maxExecutionMinutes").asInt(3)))
            .minRelevanceThreshold(params.path("minRelevanceThreshold").asDouble(0.3))
            .build();
    }

    private Map<String, Object> convertToAgentData(UnifiedDiscoveryResult result) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalDiscovered", result.getTotalDiscoveredPapers());
        data.put("discoveredPapers", result.getDiscoveredPapers());
        data.put("confidence", result.getOverallConfidence());
        data.put("sourceResults", result.getResultsBySource());
        data.put("processingTime", result.getSynthesisMetadata().getProcessingTime().toMillis());
        return data;
    }
}
```

## Supporting Data Models

### SynthesisMetadata

```java
@Data
@Builder
public class SynthesisMetadata {
    private int totalRawResults;
    private int totalProcessedResults;
    private List<DiscoverySource> successfulSources;
    private List<DiscoverySource> failedSources;
    private Duration processingTime;
    private double overallConfidence;
    private boolean partialResult;
    private boolean cacheHit;
    private List<String> processingWarnings;
    private List<String> processingErrors;

    public boolean hasErrors() {
        return processingErrors != null && !processingErrors.isEmpty();
    }

    public boolean hasWarnings() {
        return processingWarnings != null && !processingWarnings.isEmpty();
    }

    public int getSuccessfulSourceCount() {
        return successfulSources != null ? successfulSources.size() : 0;
    }

    public int getFailedSourceCount() {
        return failedSources != null ? failedSources.size() : 0;
    }
}
```

### DiscoveryExecution

```java
@Data
@Builder
public class DiscoveryExecution {
    private Paper sourcePaper;
    private DiscoveryConfiguration configuration;
    private List<SourceDiscoveryResult> sourceResults;
    private Instant startTime;
    private Instant endTime;

    public Duration getTotalExecutionTime() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.ZERO;
    }

    public boolean isComplete() {
        return endTime != null;
    }

    public int getSuccessfulSourceCount() {
        return (int) sourceResults.stream()
            .filter(SourceDiscoveryResult::isSuccess)
            .count();
    }

    public List<SourceDiscoveryResult> getFailedResults() {
        return sourceResults.stream()
            .filter(result -> !result.isSuccess())
            .collect(Collectors.toList());
    }
}
```

### SourceDiscoveryResult

```java
@Data
@Builder
public class SourceDiscoveryResult {
    private DiscoverySource source;
    private List<DiscoveredPaper> discoveredPapers;
    private Object metadata; // Source-specific metadata
    private Duration executionTime;
    private boolean success;
    private String errorMessage;
    private Exception exception;

    public static SourceDiscoveryResult failed(DiscoverySource source, Throwable throwable) {
        return SourceDiscoveryResult.builder()
            .source(source)
            .discoveredPapers(new ArrayList<>())
            .success(false)
            .errorMessage(throwable.getMessage())
            .exception(throwable instanceof Exception ? (Exception) throwable : 
                      new RuntimeException(throwable))
            .build();
    }

    public int getResultCount() {
        return discoveredPapers != null ? discoveredPapers.size() : 0;
    }

    public boolean hasResults() {
        return getResultCount() > 0;
    }
}
```

## Configuration Enums

### DiscoveryMode

```java
public enum DiscoveryMode {
    QUICK("Fast discovery with limited sources"),
    COMPREHENSIVE("Full discovery across all sources"),
    TARGETED("Focused discovery for specific use cases"),
    EXPERIMENTAL("Experimental discovery with new methods");

    private final String description;

    DiscoveryMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

### DiversityLevel

```java
public enum DiversityLevel {
    LOW("Prioritize relevance, minimal diversity"),
    MEDIUM("Balance relevance and diversity"),
    HIGH("Maximize diversity across multiple dimensions");

    private final String description;

    DiversityLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

### DiscoverySource

```java
public enum DiscoverySource {
    CROSSREF("Crossref API"),
    SEMANTIC_SCHOLAR("Semantic Scholar API"),
    PERPLEXITY("Perplexity Research API"),
    INTERNAL_CACHE("Internal Answer42 cache");

    private final String displayName;

    DiscoverySource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

This comprehensive set of supporting components provides the Discovery Orchestrator with sophisticated diversity optimization, robust error handling, comprehensive performance monitoring, and seamless integration with Answer42's multi-agent pipeline system.

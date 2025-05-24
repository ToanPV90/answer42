# 5. Batch Processing

## Overview

The batch processing system handles automated embedding generation for papers in the background, optimizing costs through scheduled processing while ensuring all papers eventually receive semantic search capabilities.

## Batch Processing Service

### Core Batch Processing Implementation

```java
@Service
@Transactional
public class EmbeddingBatchProcessingService {
    
    private final PaperRepository paperRepository;
    private final EmbeddingProcessingStatusRepository statusRepository;
    private final EmbeddingGenerationAgent embeddingAgent;
    private final AgentTaskService agentTaskService;
    private final CreditService creditService;
    private final ThreadPoolTaskScheduler scheduler;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingBatchProcessingService.class);
    
    @Value("${answer42.embedding.batch.enabled:true}")
    private boolean batchProcessingEnabled;
    
    @Value("${answer42.embedding.batch.size:10}")
    private int batchSize;
    
    @Value("${answer42.embedding.batch.max-daily-papers:100}")
    private int maxDailyPapers;
    
    public EmbeddingBatchProcessingService(
            PaperRepository paperRepository,
            EmbeddingProcessingStatusRepository statusRepository,
            EmbeddingGenerationAgent embeddingAgent,
            AgentTaskService agentTaskService,
            CreditService creditService,
            ThreadPoolTaskScheduler scheduler) {
        this.paperRepository = paperRepository;
        this.statusRepository = statusRepository;
        this.embeddingAgent = embeddingAgent;
        this.agentTaskService = agentTaskService;
        this.creditService = creditService;
        this.scheduler = scheduler;
    }
    
    /**
     * Scheduled batch processing every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void processPendingEmbeddings() {
        if (!batchProcessingEnabled) {
            LoggingUtil.debug(LOG, "processPendingEmbeddings", "Batch processing disabled");
            return;
        }
        
        LoggingUtil.info(LOG, "processPendingEmbeddings", "Starting scheduled embedding batch processing");
        
        try {
            // Check daily processing limit
            if (hasReachedDailyLimit()) {
                LoggingUtil.info(LOG, "processPendingEmbeddings", 
                    "Daily processing limit reached, skipping batch");
                return;
            }
            
            // Find papers needing embedding generation
            List<Paper> pendingPapers = findPapersNeedingEmbeddings();
            
            if (pendingPapers.isEmpty()) {
                LoggingUtil.debug(LOG, "processPendingEmbeddings", "No papers pending embedding generation");
                return;
            }
            
            // Process in batches
            List<List<Paper>> batches = partitionPapers(pendingPapers, batchSize);
            
            for (List<Paper> batch : batches) {
                processBatch(batch);
                
                // Rate limiting between batches
                if (batches.size() > 1) {
                    try {
                        Thread.sleep(5000); // 5 second delay between batches
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            LoggingUtil.info(LOG, "processPendingEmbeddings", 
                "Completed batch processing for %d papers", pendingPapers.size());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processPendingEmbeddings", 
                "Batch processing failed: %s", e, e.getMessage());
        }
    }
    
    /**
     * Process a single batch of papers
     */
    private void processBatch(List<Paper> papers) {
        LoggingUtil.info(LOG, "processBatch", "Processing batch of %d papers", papers.size());
        
        List<CompletableFuture<Void>> futures = papers.stream()
            .map(this::processPaperAsync)
            .collect(Collectors.toList());
        
        // Wait for all papers in batch to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    LoggingUtil.error(LOG, "processBatch", 
                        "Batch processing completed with errors", throwable);
                } else {
                    LoggingUtil.info(LOG, "processBatch", 
                        "Batch processing completed successfully");
                }
            });
    }
    
    /**
     * Process individual paper asynchronously
     */
    private CompletableFuture<Void> processPaperAsync(Paper paper) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Check if user has sufficient credits for system processing
                if (!creditService.hasSystemCreditsForOperation("EMBEDDING_GENERATION")) {
                    LoggingUtil.warn(LOG, "processPaperAsync", 
                        "Insufficient system credits for embedding generation");
                    return;
                }
                
                // Create agent task for embedding generation
                String taskId = UUID.randomUUID().toString();
                JsonNode input = JsonNodeFactory.instance.objectNode()
                    .put("paperId", paper.getId().toString())
                    .put("batchProcessing", true);
                
                AgentTask task = agentTaskService.createTask(
                    taskId, "embedding-generator", paper.getUserId(), input);
                
                // Process with embedding agent
                AgentResult result = embeddingAgent.processTask(task).join();
                
                if (result.isSuccess()) {
                    LoggingUtil.info(LOG, "processPaperAsync", 
                        "Successfully generated embeddings for paper %s", paper.getId());
                    
                    // Track system credit usage
                    creditService.deductSystemCredits("EMBEDDING_GENERATION", taskId);
                } else {
                    LoggingUtil.error(LOG, "processPaperAsync", 
                        "Failed to generate embeddings for paper %s: %s", 
                        paper.getId(), result.getErrorMessage());
                }
                
            } catch (Exception e) {
                LoggingUtil.error(LOG, "processPaperAsync", 
                    "Error processing paper %s: %s", e, paper.getId(), e.getMessage());
            }
        }, scheduler.getScheduledExecutor());
    }
    
    /**
     * Find papers that need embedding generation
     */
    private List<Paper> findPapersNeedingEmbeddings() {
        String sql = """
            SELECT p.* FROM answer42.papers p
            LEFT JOIN answer42.embedding_processing_status eps ON p.id = eps.paper_id
            WHERE (
                p.content_embedding IS NULL 
                OR p.abstract_embedding IS NULL 
                OR p.embeddings_generated_at IS NULL
                OR p.embeddings_generated_at < p.updated_at
            )
            AND p.text_content IS NOT NULL
            AND p.status = 'PROCESSED'
            AND (
                eps.processing_status IS NULL 
                OR eps.processing_status IN ('failed', 'skipped')
                OR (eps.processing_status = 'failed' AND eps.retry_after < NOW())
            )
            ORDER BY p.created_at ASC
            LIMIT ?
            """;
        
        Query query = entityManager.createNativeQuery(sql, Paper.class);
        query.setParameter(1, maxDailyPapers);
        
        @SuppressWarnings("unchecked")
        List<Paper> papers = query.getResultList();
        
        LoggingUtil.info(LOG, "findPapersNeedingEmbeddings", 
            "Found %d papers needing embedding generation", papers.size());
        
        return papers;
    }
    
    /**
     * Check if daily processing limit has been reached
     */
    private boolean hasReachedDailyLimit() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        long todayProcessedCount = statusRepository.countByCompletedAtAfter(
            startOfDay.atZone(ZoneId.systemDefault()).toInstant());
        
        return todayProcessedCount >= maxDailyPapers;
    }
    
    /**
     * Partition papers into processing batches
     */
    private List<List<Paper>> partitionPapers(List<Paper> papers, int batchSize) {
        List<List<Paper>> batches = new ArrayList<>();
        for (int i = 0; i < papers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, papers.size());
            batches.add(papers.subList(i, end));
        }
        return batches;
    }
}
```

## Priority-Based Processing

### Processing Priority Service

```java
@Service
public class EmbeddingPriorityService {
    
    private final UserRepository userRepository;
    private final PaperRepository paperRepository;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingPriorityService.class);
    
    public EmbeddingPriorityService(
            UserRepository userRepository,
            PaperRepository paperRepository) {
        this.userRepository = userRepository;
        this.paperRepository = paperRepository;
    }
    
    /**
     * Calculate processing priority for a paper
     */
    public int calculatePriority(Paper paper) {
        int priority = 0;
        
        // Base priority factors
        priority += getUserTierPriority(paper.getUserId());
        priority += getPaperAgePriority(paper.getCreatedAt());
        priority += getPaperSizePriority(paper);
        priority += getPaperPopularityPriority(paper);
        
        return Math.max(1, Math.min(priority, 100)); // Clamp between 1-100
    }
    
    /**
     * Get priority based on user subscription tier
     */
    private int getUserTierPriority(UUID userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return 10; // Default priority
            }
            
            // Higher priority for paid subscribers
            String subscriptionTier = getUserSubscriptionTier(user);
            
            return switch (subscriptionTier.toLowerCase()) {
                case "scholar" -> 50;      // Highest priority
                case "pro" -> 30;          // Medium-high priority
                case "basic" -> 20;        // Medium priority
                default -> 10;             // Default priority
            };
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "getUserTierPriority", 
                "Failed to get user tier for %s", userId);
            return 10;
        }
    }
    
    /**
     * Get priority based on paper age (newer papers get higher priority)
     */
    private int getPaperAgePriority(LocalDateTime createdAt) {
        Duration age = Duration.between(createdAt, LocalDateTime.now());
        
        if (age.toDays() < 1) {
            return 30; // Very recent papers
        } else if (age.toDays() < 7) {
            return 20; // Recent papers
        } else if (age.toDays() < 30) {
            return 10; // Moderately old papers
        } else {
            return 5;  // Old papers
        }
    }
    
    /**
     * Get priority based on paper size and complexity
     */
    private int getPaperSizePriority(Paper paper) {
        int contentLength = 0;
        
        if (paper.getTextContent() != null) {
            contentLength += paper.getTextContent().length();
        }
        
        if (paper.getPaperAbstract() != null) {
            contentLength += paper.getPaperAbstract().length();
        }
        
        // Smaller papers get slightly higher priority (faster to process)
        if (contentLength < 10000) {
            return 15; // Small papers
        } else if (contentLength < 50000) {
            return 10; // Medium papers
        } else {
            return 5;  // Large papers
        }
    }
    
    /**
     * Get priority based on paper popularity and usage
     */
    private int getPaperPopularityPriority(Paper paper) {
        // Check if paper is public (potentially higher usage)
        if (paper.getIsPublic() != null && paper.getIsPublic()) {
            return 15;
        }
        
        // Could also factor in view counts, download counts, etc.
        return 5; // Default for private papers
    }
    
    /**
     * Sort papers by processing priority
     */
    public List<Paper> sortByPriority(List<Paper> papers) {
        return papers.stream()
            .map(paper -> new PaperWithPriority(paper, calculatePriority(paper)))
            .sorted(Comparator.comparing(PaperWithPriority::getPriority).reversed())
            .map(PaperWithPriority::getPaper)
            .collect(Collectors.toList());
    }
    
    @Data
    @AllArgsConstructor
    private static class PaperWithPriority {
        private Paper paper;
        private int priority;
    }
}
```

## Status Tracking and Monitoring

### Batch Processing Status Service

```java
@Service
public class BatchProcessingStatusService {
    
    private final EmbeddingProcessingStatusRepository statusRepository;
    private final PaperRepository paperRepository;
    private static final Logger LOG = LoggerFactory.getLogger(BatchProcessingStatusService.class);
    
    public BatchProcessingStatusService(
            EmbeddingProcessingStatusRepository statusRepository,
            PaperRepository paperRepository) {
        this.statusRepository = statusRepository;
        this.paperRepository = paperRepository;
    }
    
    /**
     * Get comprehensive batch processing statistics
     */
    public BatchProcessingStatistics getProcessingStatistics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOfWeek = now.minusDays(7);
        LocalDateTime startOfMonth = now.minusDays(30);
        
        return BatchProcessingStatistics.builder()
            .totalPapersProcessed(getTotalProcessedCount())
            .papersProcessedToday(getProcessedCountSince(startOfDay))
            .papersProcessedThisWeek(getProcessedCountSince(startOfWeek))
            .papersProcessedThisMonth(getProcessedCountSince(startOfMonth))
            .papersAwaitingProcessing(getAwaitingProcessingCount())
            .papersWithErrors(getErrorCount())
            .averageProcessingTime(getAverageProcessingTime())
            .processingSuccessRate(getProcessingSuccessRate())
            .lastProcessingRun(getLastProcessingRun())
            .estimatedCompletionTime(estimateCompletionTime())
            .build();
    }
    
    /**
     * Get processing queue status
     */
    public ProcessingQueueStatus getQueueStatus() {
        List<EmbeddingProcessingStatus> pendingItems = statusRepository
            .findByProcessingStatusOrderByCreatedAtAsc("pending");
        
        List<EmbeddingProcessingStatus> processingItems = statusRepository
            .findByProcessingStatus("processing");
        
        List<EmbeddingProcessingStatus> failedItems = statusRepository
            .findByProcessingStatusAndRetryAfterLessThan("failed", Instant.now());
        
        return ProcessingQueueStatus.builder()
            .pendingCount(pendingItems.size())
            .processingCount(processingItems.size())
            .failedRetryCount(failedItems.size())
            .totalQueueSize(pendingItems.size() + processingItems.size() + failedItems.size())
            .estimatedProcessingTime(calculateEstimatedTime(pendingItems.size()))
            .nextScheduledRun(getNextScheduledRun())
            .build();
    }
    
    /**
     * Get detailed error analysis
     */
    public List<ErrorAnalysis> getErrorAnalysis(LocalDateTime since) {
        List<EmbeddingProcessingStatus> failedStatuses = statusRepository
            .findByProcessingStatusAndUpdatedAtAfter("failed", 
                since.atZone(ZoneId.systemDefault()).toInstant());
        
        Map<String, Long> errorCounts = failedStatuses.stream()
            .collect(Collectors.groupingBy(
                status -> extractErrorType(status.getErrorMessage()),
                Collectors.counting()
            ));
        
        return errorCounts.entrySet().stream()
            .map(entry -> ErrorAnalysis.builder()
                .errorType(entry.getKey())
                .occurrenceCount(entry.getValue())
                .lastOccurrence(getLastOccurrenceForError(entry.getKey(), failedStatuses))
                .affectedPapers(getPapersForError(entry.getKey(), failedStatuses))
                .build())
            .sorted(Comparator.comparing(ErrorAnalysis::getOccurrenceCount).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Monitor processing health and performance
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void monitorProcessingHealth() {
        try {
            // Check for stuck processing tasks
            List<EmbeddingProcessingStatus> stuckTasks = findStuckProcessingTasks();
            if (!stuckTasks.isEmpty()) {
                LoggingUtil.warn(LOG, "monitorProcessingHealth", 
                    "Found %d stuck processing tasks", stuckTasks.size());
                
                // Reset stuck tasks
                resetStuckTasks(stuckTasks);
            }
            
            // Check error rates
            double errorRate = calculateRecentErrorRate();
            if (errorRate > 0.25) { // More than 25% error rate
                LoggingUtil.error(LOG, "monitorProcessingHealth", 
                    "High error rate detected: %.2f%%", errorRate * 100);
                
                // Could trigger alerts or notifications here
            }
            
            // Check processing backlog
            long backlogSize = getAwaitingProcessingCount();
            if (backlogSize > 1000) {
                LoggingUtil.warn(LOG, "monitorProcessingHealth", 
                    "Large processing backlog: %d papers", backlogSize);
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "monitorProcessingHealth", 
                "Error during health monitoring", e);
        }
    }
    
    /**
     * Find processing tasks that appear to be stuck
     */
    private List<EmbeddingProcessingStatus> findStuckProcessingTasks() {
        Instant stuckThreshold = Instant.now().minus(2, ChronoUnit.HOURS);
        
        return statusRepository.findByProcessingStatusAndStartedAtBefore(
            "processing", stuckThreshold);
    }
    
    /**
     * Reset stuck tasks to pending status
     */
    private void resetStuckTasks(List<EmbeddingProcessingStatus> stuckTasks) {
        for (EmbeddingProcessingStatus status : stuckTasks) {
            status.setProcessingStatus("pending");
            status.setStartedAt(null);
            status.setErrorMessage("Task was reset due to timeout");
            status.setErrorCount(status.getErrorCount() + 1);
            status.setUpdatedAt(Instant.now());
            
            statusRepository.save(status);
        }
        
        LoggingUtil.info(LOG, "resetStuckTasks", "Reset %d stuck tasks", stuckTasks.size());
    }
    
    /**
     * Calculate recent error rate for health monitoring
     */
    private double calculateRecentErrorRate() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        
        long totalRecent = statusRepository.countByUpdatedAtAfter(since);
        long failedRecent = statusRepository.countByProcessingStatusAndUpdatedAtAfter("failed", since);
        
        if (totalRecent == 0) {
            return 0.0;
        }
        
        return (double) failedRecent / totalRecent;
    }
}
```

This batch processing system provides automated, prioritized embedding generation with comprehensive monitoring and error handling, ensuring all papers eventually receive semantic search capabilities while optimizing costs and system resources.

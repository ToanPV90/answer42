package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.agent.AgentRetryStatistics;
import com.samjdtechnologies.answer42.model.agent.RetryMetrics;
import com.samjdtechnologies.answer42.model.agent.RetryStatistics;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.interfaces.AIAgent;
import com.samjdtechnologies.answer42.service.agent.ContentSummarizerFallbackAgent;
import com.samjdtechnologies.answer42.service.agent.ConceptExplainerFallbackAgent;
import com.samjdtechnologies.answer42.service.agent.MetadataEnhancementFallbackAgent;
import com.samjdtechnologies.answer42.service.agent.PaperProcessorFallbackAgent;
import com.samjdtechnologies.answer42.service.agent.QualityCheckerFallbackAgent;
import com.samjdtechnologies.answer42.service.agent.CitationFormatterFallbackAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Retry mechanism for transient failures with exponential backoff and jitter.
 * Integrates with AgentCircuitBreaker for comprehensive failure protection.
 * Provides configurable retry policies for different agent types with comprehensive statistics tracking.
 */
@Component
public class AgentRetryPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(AgentRetryPolicy.class);
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final double JITTER_FACTOR = 0.1; // 10% jitter
    
    private final Executor taskExecutor;
    private final AgentCircuitBreaker circuitBreaker;
    
    // Fallback agents injected directly
    private final ContentSummarizerFallbackAgent contentSummarizerFallback;
    private final ConceptExplainerFallbackAgent conceptExplainerFallback;
    private final MetadataEnhancementFallbackAgent metadataEnhancementFallback;
    private final PaperProcessorFallbackAgent paperProcessorFallback;
    private final QualityCheckerFallbackAgent qualityCheckerFallback;
    private final CitationFormatterFallbackAgent citationFormatterFallback;
    
    // Statistics tracking
    private final AtomicLong totalAttempts = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong successfulRetries = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong circuitBreakerTrips = new AtomicLong(0);
    
    // Fallback tracking statistics (Phase 3)
    private final AtomicLong fallbackAttempts = new AtomicLong(0);
    private final AtomicLong fallbackSuccesses = new AtomicLong(0);
    private final AtomicLong fallbackFailures = new AtomicLong(0);
    
    private final Map<AgentType, RetryMetrics> agentMetrics = new ConcurrentHashMap<>();
    private final ZonedDateTime startTime = ZonedDateTime.now();
    
    // Retry configurations for different agent types
    private final Map<AgentType, RetryConfiguration> retryConfigs = new ConcurrentHashMap<>();
    private static final RetryConfiguration DEFAULT_CONFIG = new RetryConfiguration(DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY);
    
    public AgentRetryPolicy(ThreadConfig threadConfig, AgentCircuitBreaker circuitBreaker,
                           ContentSummarizerFallbackAgent contentSummarizerFallback,
                           ConceptExplainerFallbackAgent conceptExplainerFallback,
                           MetadataEnhancementFallbackAgent metadataEnhancementFallback,
                           PaperProcessorFallbackAgent paperProcessorFallback,
                           QualityCheckerFallbackAgent qualityCheckerFallback,
                           CitationFormatterFallbackAgent citationFormatterFallback) {
        this.taskExecutor = threadConfig.taskExecutor();
        this.circuitBreaker = circuitBreaker;
        this.contentSummarizerFallback = contentSummarizerFallback;
        this.conceptExplainerFallback = conceptExplainerFallback;
        this.metadataEnhancementFallback = metadataEnhancementFallback;
        this.paperProcessorFallback = paperProcessorFallback;
        this.qualityCheckerFallback = qualityCheckerFallback;
        this.citationFormatterFallback = citationFormatterFallback;
        
        LoggingUtil.info(LOG, "constructor", 
            "AgentRetryPolicy initialized with direct fallback agent injection");
    }
    
    /**
     * Execute operation with agent-specific retry policy, circuit breaker protection, and fallback support.
     * All calls MUST include the original task for proper fallback functionality.
     */
    public <T> CompletableFuture<T> executeWithRetry(
            AgentType agentType,
            Supplier<CompletableFuture<T>> operation,
            AgentTask originalTask) {
        
        if (agentType == null) {
            throw new IllegalArgumentException("AgentType cannot be null - all retry operations must specify the agent type");
        }
        
        if (originalTask == null) {
            throw new IllegalArgumentException("AgentTask cannot be null - all retry operations must provide the original task for proper fallback functionality");
        }
        
        RetryConfiguration config = getRetryConfigForAgent(agentType);
        LoggingUtil.debug(LOG, "executeWithRetry", 
            "Executing retry operation for agent %s with original task %s", 
            agentType, originalTask.getId());
        return executeWithRetry(agentType, operation, originalTask, 0, config.maxRetries, config.initialDelay);
    }
    
    /**
     * Internal retry implementation with exponential backoff, circuit breaker integration, and statistics tracking.
     */
    private <T> CompletableFuture<T> executeWithRetry(
            AgentType agentType,
            Supplier<CompletableFuture<T>> operation,
            AgentTask originalTask,
            int attemptNumber,
            int maxRetries,
            Duration initialDelay) {
        
        // Record attempt
        recordAttempt(agentType, attemptNumber > 0);
        
        LoggingUtil.debug(LOG, "executeWithRetry", 
            "Executing operation attempt %d/%d for agent %s", 
            attemptNumber + 1, maxRetries + 1, agentType);
        
        // Wrap operation with circuit breaker if agent type is specified
        Supplier<CompletableFuture<T>> protectedOperation = agentType != null ?
            () -> circuitBreaker.executeWithCircuitBreaker(agentType, operation) :
            operation;
        
        return protectedOperation.get()
            .whenComplete((result, throwable) -> {
                if (throwable == null) {
                    // Success - record it
                    recordSuccess(agentType, attemptNumber > 0);
                }
            })
            .exceptionallyCompose(throwable -> {
                // Check if this is a circuit breaker exception
                if (throwable instanceof AgentCircuitBreaker.CircuitBreakerOpenException) {
                    circuitBreakerTrips.incrementAndGet();
                    LoggingUtil.warn(LOG, "executeWithRetry", 
                        "Circuit breaker opened for agent %s, stopping retries", agentType);
                    recordFailure(agentType);
                    return CompletableFuture.failedFuture(throwable);
                }
                
                if (attemptNumber >= maxRetries || !isRetryableException(throwable)) {
                    LoggingUtil.warn(LOG, "executeWithRetry", 
                        "Operation failed after %d attempts, checking fallback availability: %s", 
                        attemptNumber + 1, throwable.getMessage());
                    
                    // If all retries exhausted and fallback available, try Ollama
                    if (agentType != null && isFallbackAvailable(agentType)) {
                        LoggingUtil.info(LOG, "executeWithRetry", 
                            "Attempting Ollama fallback for agent %s after %d failed attempts", 
                            agentType, attemptNumber + 1);
                        
                        try {
                            T fallbackResult = attemptOllamaFallback(agentType, throwable, originalTask);
                            recordFallbackSuccess(agentType);
                            return CompletableFuture.completedFuture(fallbackResult);
                        } catch (Exception fallbackException) {
                            recordFallbackFailure(agentType, fallbackException);
                            LoggingUtil.error(LOG, "executeWithRetry", 
                                "Ollama fallback also failed for agent %s: %s", agentType, fallbackException.getMessage());
                            // Continue to record final failure and return error
                        }
                    }
                    
                    // Record final failure
                    recordFailure(agentType);
                    return CompletableFuture.failedFuture(throwable);
                }
                
                // Calculate delay with exponential backoff and jitter
                Duration delay = calculateDelay(initialDelay, attemptNumber);
                
                LoggingUtil.warn(LOG, "executeWithRetry", 
                    "Operation failed (attempt %d/%d), retrying in %dms: %s", 
                    attemptNumber + 1, maxRetries + 1, delay.toMillis(), throwable.getMessage());
                
                // Schedule retry with delay
                return delayedExecution(delay)
                    .thenCompose(v -> executeWithRetry(agentType, operation, originalTask, attemptNumber + 1, maxRetries, initialDelay));
            });
    }
    
    /**
     * Record statistics for retry operations.
     */
    private void recordAttempt(AgentType agentType, boolean isRetry) {
        totalAttempts.incrementAndGet();
        if (isRetry) {
            totalRetries.incrementAndGet();
        }
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.computeIfAbsent(agentType, k -> RetryMetrics.builder().build());
            metrics.getTotalAttempts().incrementAndGet();
            if (isRetry) {
                metrics.getTotalRetries().incrementAndGet();
            }
        }
    }
    
    /**
     * Create a fallback task with context about the primary failure.
     */
    private AgentTask createFallbackTask(AgentType agentType, Throwable primaryFailure) {
        String fallbackTaskId = "fallback-" + System.currentTimeMillis();
        
        // Create input JSON with fallback context
        ObjectNode inputJson = JsonNodeFactory.instance.objectNode();
        inputJson.put("content", "Fallback processing due to primary agent failure: " + primaryFailure.getMessage());
        inputJson.put("isFallback", true);
        inputJson.put("primaryFailure", primaryFailure.getMessage());
        inputJson.put("fallbackReason", "All cloud providers failed");
        inputJson.put("agentType", agentType.toString());
        
        return AgentTask.builder()
            .id(fallbackTaskId)
            .agentId(agentType.toString().toLowerCase().replace("_", "-"))
            .userId(java.util.UUID.randomUUID()) // Fallback user ID - in real implementation would use actual user
            .input(inputJson)
            .status("pending")
            .createdAt(java.time.Instant.now())
            .build();
    }
    
    /**
     * Record successful operation outcome.
     */
    private void recordSuccess(AgentType agentType, boolean isRetry) {
        successfulOperations.incrementAndGet();
        if (isRetry) {
            successfulRetries.incrementAndGet();
        }
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.get(agentType);
            if (metrics != null) {
                metrics.getSuccessfulOperations().incrementAndGet();
                if (isRetry) {
                    metrics.getSuccessfulRetries().incrementAndGet();
                }
            }
        }
    }
    
    /**
     * Record failed operation outcome.
     */
    private void recordFailure(AgentType agentType) {
        failedOperations.incrementAndGet();
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.get(agentType);
            if (metrics != null) {
                metrics.getFailedOperations().incrementAndGet();
            }
        }
    }
    
    
    /**
     * Get retry statistics for a specific agent type (FIXED VERSION).
     */
    public AgentRetryStatistics getAgentRetryStatistics(AgentType agentType) {
        RetryMetrics metrics = agentMetrics.get(agentType);
        if (metrics == null) {
            return AgentRetryStatistics.builder()
                .agentType(agentType)
                .totalAttempts(0)
                .totalRetries(0)
                .successfulOperations(0)
                .successfulRetries(0)
                .overallSuccessRate(0.0)
                .retrySuccessRate(0.0)
                .build();
        }
        
        long totalOps = metrics.getTotalAttempts().get();
        long successfulOps = metrics.getSuccessfulOperations().get();
        long totalRetries = metrics.getTotalRetries().get();
        long successfulRetries = metrics.getSuccessfulRetries().get();
        
        double overallSuccessRate = totalOps > 0 ? (double) successfulOps / totalOps : 0.0;
        double retrySuccessRate = totalRetries > 0 ? (double) successfulRetries / totalRetries : 0.0;
        
        return AgentRetryStatistics.builder()
            .agentType(agentType)
            .totalAttempts(totalOps)
            .totalRetries(totalRetries)
            .successfulOperations(successfulOps)
            .successfulRetries(successfulRetries)
            .overallSuccessRate(overallSuccessRate)
            .retrySuccessRate(retrySuccessRate)
            .build();
    }
    
    /**
     * Get circuit breaker status for an agent.
     */
    public AgentCircuitBreaker.CircuitBreakerStatus getCircuitBreakerStatus(AgentType agentType) {
        return circuitBreaker.getCircuitBreakerStatus(agentType);
    }
    
    /**
     * Reset circuit breaker for an agent (for manual recovery).
     */
    public void resetCircuitBreaker(AgentType agentType) {
        circuitBreaker.resetCircuitBreaker(agentType);
        LoggingUtil.info(LOG, "resetCircuitBreaker", 
            "Reset circuit breaker for agent %s via retry policy", agentType);
    }
    
    /**
     * Get circuit breaker trip count.
     */
    public long getCircuitBreakerTrips() {
        return circuitBreakerTrips.get();
    }
    
    /**
     * Get the fallback agent for a specific agent type.
     */
    private AIAgent getFallbackAgent(AgentType agentType) {
        switch (agentType) {
            case CONTENT_SUMMARIZER:
                return contentSummarizerFallback;
            case CONCEPT_EXPLAINER:
                return conceptExplainerFallback;
            case METADATA_ENHANCER:
                return metadataEnhancementFallback;
            case PAPER_PROCESSOR:
                return paperProcessorFallback;
            case QUALITY_CHECKER:
                return qualityCheckerFallback;
            case CITATION_FORMATTER:
                return citationFormatterFallback;
            default:
                return null;
        }
    }

    /**
     * Check if fallback is available for the specified agent type.
     * Phase 3: Fallback integration logic.
     */
    private boolean isFallbackAvailable(AgentType agentType) {
        // Special agents that don't have fallbacks (require external APIs)
        if (agentType == AgentType.RELATED_PAPER_DISCOVERY) {
            LoggingUtil.debug(LOG, "isFallbackAvailable", 
                "No fallback available for %s - requires external APIs", agentType);
            return false;
        }
        
        AIAgent fallbackAgent = getFallbackAgent(agentType);
        boolean available = fallbackAgent != null && 
            (!(fallbackAgent instanceof com.samjdtechnologies.answer42.service.agent.OllamaBasedAgent) || 
             ((com.samjdtechnologies.answer42.service.agent.OllamaBasedAgent) fallbackAgent).canProcess());
        
        LoggingUtil.debug(LOG, "isFallbackAvailable", 
            "Fallback availability for %s: %s", agentType, available);
        
        return available;
    }
    
    /**
     * Attempts to use Ollama fallback agent for the operation.
     * Phase 3: Core fallback execution logic.
     */
    @SuppressWarnings("unchecked")
    private <T> T attemptOllamaFallback(AgentType agentType, Throwable primaryFailure, AgentTask originalTask) {
        fallbackAttempts.incrementAndGet();
        
        try {
            AIAgent fallbackAgent = getFallbackAgent(agentType);
            if (fallbackAgent == null) {
                throw new RuntimeException("No fallback agent available for " + agentType);
            }
            
            LoggingUtil.info(LOG, "attemptOllamaFallback", 
                "Executing fallback agent %s for %s", 
                fallbackAgent.getClass().getSimpleName(), agentType);
            
            // Use original task if available, otherwise cannot create meaningful fallback
            if (originalTask == null) {
                LoggingUtil.warn(LOG, "attemptOllamaFallback", 
                    "No original task available, cannot create proper fallback task");
                throw new RuntimeException("Ollama fallback failed for " + agentType + ": No original task available");
            }
            
            // Use the original task that contains the actual data to process
            LoggingUtil.debug(LOG, "attemptOllamaFallback", 
                "Using original task %s for fallback processing", originalTask.getId());
            AgentTask taskToProcess = originalTask;
            
            // Execute the fallback agent directly without retry policy
            // Note: Fallback agents are designed to work without retry policies to avoid circular dependencies
            AgentResult fallbackResult;
            try {
                fallbackResult = fallbackAgent.process(taskToProcess).get();
            } catch (Exception e) {
                // If process() fails due to null retry policy, this is expected for fallback agents
                // Create a fallback response indicating the issue
                fallbackResult = AgentResult.builder()
                    .taskId(taskToProcess.getId())
                    .success(false)
                    .errorMessage("Ollama fallback unavailable: " + e.getMessage())
                    .build();
                
                LoggingUtil.warn(LOG, "attemptOllamaFallback", 
                    "Fallback agent execution failed, likely due to retry policy dependency: %s", e.getMessage());
                throw new RuntimeException("Fallback agent execution failed", e);
            }
            
            if (fallbackResult.isSuccess()) {
                LoggingUtil.info(LOG, "attemptOllamaFallback", 
                    "Ollama fallback successful for %s", agentType);
                return (T) fallbackResult;
            } else {
                throw new RuntimeException("Fallback agent processing failed: " + fallbackResult.getErrorMessage());
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "attemptOllamaFallback", 
                "Failed to execute Ollama fallback for %s: %s", agentType, e.getMessage());
            throw new RuntimeException("Ollama fallback failed for " + agentType, e);
        }
    }
    
    
    /**
     * Record successful fallback operation.
     */
    private void recordFallbackSuccess(AgentType agentType) {
        fallbackSuccesses.incrementAndGet();
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.get(agentType);
            if (metrics != null) {
                metrics.getFallbackSuccesses().incrementAndGet();
            }
        }
        
        LoggingUtil.info(LOG, "recordFallbackSuccess", 
            "Recorded fallback success for %s", agentType);
    }
    
    /**
     * Record failed fallback operation.
     */
    private void recordFallbackFailure(AgentType agentType, Exception fallbackException) {
        fallbackFailures.incrementAndGet();
        
        if (agentType != null) {
            RetryMetrics metrics = agentMetrics.get(agentType);
            if (metrics != null) {
                metrics.getFallbackFailures().incrementAndGet();
            }
        }
        
        LoggingUtil.warn(LOG, "recordFallbackFailure", 
            "Recorded fallback failure for %s: %s", agentType, fallbackException.getMessage());
    }
    
    /**
     * Get fallback statistics for monitoring.
     */
    public java.util.Map<String, Object> getFallbackStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        long attempts = fallbackAttempts.get();
        long successes = fallbackSuccesses.get();
        long failures = fallbackFailures.get();
        
        stats.put("fallbackAttempts", attempts);
        stats.put("fallbackSuccesses", successes);
        stats.put("fallbackFailures", failures);
        stats.put("fallbackSuccessRate", attempts > 0 ? (double) successes / attempts : 0.0);
        
        // Count available fallback agents directly
        int availableAgents = 0;
        java.util.List<String> availableTypes = new java.util.ArrayList<>();
        
        if (contentSummarizerFallback != null) { availableAgents++; availableTypes.add("CONTENT_SUMMARIZER"); }
        if (conceptExplainerFallback != null) { availableAgents++; availableTypes.add("CONCEPT_EXPLAINER"); }
        if (metadataEnhancementFallback != null) { availableAgents++; availableTypes.add("METADATA_ENHANCER"); }
        if (paperProcessorFallback != null) { availableAgents++; availableTypes.add("PAPER_PROCESSOR"); }
        if (qualityCheckerFallback != null) { availableAgents++; availableTypes.add("QUALITY_CHECKER"); }
        if (citationFormatterFallback != null) { availableAgents++; availableTypes.add("CITATION_FORMATTER"); }
        
        stats.put("fallbackEnabled", availableAgents > 0);
        stats.put("availableFallbackAgents", availableAgents);
        stats.put("fallbackSystemInfo", String.format("Direct injection fallback system: %d agents available %s", 
            availableAgents, availableTypes));
        
        return stats;
    }
    
    /**
     * Get overall retry statistics.
     */
    public RetryStatistics getRetryStatistics() {
        long totalOps = totalAttempts.get();
        long successfulOps = successfulOperations.get();
        long totalRetryCount = totalRetries.get();
        long successfulRetryCount = successfulRetries.get();
        long failedOps = failedOperations.get();
        
        double overallSuccessRate = totalOps > 0 ? (double) successfulOps / totalOps : 0.0;
        double retrySuccessRate = totalRetryCount > 0 ? (double) successfulRetryCount / totalRetryCount : 0.0;
        
        Duration uptime = Duration.between(startTime.toInstant(), java.time.Instant.now());
        
        return RetryStatistics.builder()
            .totalAttempts(totalOps)
            .totalRetries(totalRetryCount)
            .successfulOperations(successfulOps)
            .successfulRetries(successfulRetryCount)
            .failedOperations(failedOps)
            .overallSuccessRate(overallSuccessRate)
            .retrySuccessRate(retrySuccessRate)
            .uptime(uptime)
            .build();
    }
    
    /**
     * Reset statistics (for testing or periodic cleanup).
     */
    public void resetStatistics() {
        totalAttempts.set(0);
        totalRetries.set(0);
        successfulOperations.set(0);
        successfulRetries.set(0);
        failedOperations.set(0);
        circuitBreakerTrips.set(0);
        
        // Reset fallback statistics
        fallbackAttempts.set(0);
        fallbackSuccesses.set(0);
        fallbackFailures.set(0);
        
        agentMetrics.values().forEach(RetryMetrics::reset);
        
        LoggingUtil.info(LOG, "resetStatistics", "Reset all retry and fallback statistics");
    }
    
    /**
     * Scheduled task to log statistics periodically (ENHANCED WITH FALLBACK METRICS).
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logStatistics() {
        RetryStatistics stats = getRetryStatistics();
        
        // Log overall statistics
        LoggingUtil.info(LOG, "logStatistics", 
            "Statistics - Total Attempts: %d, Successful Operations: %d, Overall Success Rate: %.2f%%, Retry-Only Success Rate: %.2f%%, Failed Operations: %d, Circuit Breaker Trips: %d",
            stats.getTotalAttempts(), 
            stats.getSuccessfulOperations(),
            stats.getOverallSuccessRate() * 100,
            stats.getRetrySuccessRate() * 100,
            stats.getFailedOperations(), 
            circuitBreakerTrips.get());
        
        // Log overall fallback statistics
        java.util.Map<String, Object> fallbackStats = getFallbackStatistics();
        long totalFallbackAttempts = (Long) fallbackStats.get("fallbackAttempts");
        long totalFallbackSuccesses = (Long) fallbackStats.get("fallbackSuccesses"); 
        long totalFallbackFailures = (Long) fallbackStats.get("fallbackFailures");
        double fallbackSuccessRate = (Double) fallbackStats.get("fallbackSuccessRate");
        
        if (totalFallbackAttempts > 0) {
            LoggingUtil.info(LOG, "logStatistics", 
                "Ollama Fallback Summary - Attempts: %d, Successes: %d, Failures: %d, Success Rate: %.2f%%",
                totalFallbackAttempts, totalFallbackSuccesses, totalFallbackFailures, fallbackSuccessRate * 100);
        }
        
        // Log per-agent statistics with fallback details
        if (!agentMetrics.isEmpty()) {
            LoggingUtil.info(LOG, "logStatistics", "Per-agent statistics (including Ollama fallback metrics):");
            for (AgentType agentType : agentMetrics.keySet()) {
                AgentRetryStatistics agentStats = getAgentRetryStatistics(agentType);
                AgentCircuitBreaker.CircuitBreakerStatus cbStatus = getCircuitBreakerStatus(agentType);
                RetryMetrics metrics = agentMetrics.get(agentType);
                
                // Get per-agent fallback statistics
                long agentFallbackSuccesses = metrics.getFallbackSuccesses().get();
                long agentFallbackFailures = metrics.getFallbackFailures().get();
                long agentTotalFallbacks = agentFallbackSuccesses + agentFallbackFailures;
                
                String fallbackInfo = "";
                if (agentTotalFallbacks > 0) {
                    double agentFallbackSuccessRate = agentTotalFallbacks > 0 ? 
                        (double) agentFallbackSuccesses / agentTotalFallbacks : 0.0;
                    fallbackInfo = String.format(", Ollama Fallbacks: %d (%.1f%% success)", 
                        agentTotalFallbacks, agentFallbackSuccessRate * 100);
                } else if (isFallbackAvailable(agentType)) {
                    fallbackInfo = ", Ollama Fallback: Available";
                } else {
                    fallbackInfo = ", Ollama Fallback: N/A";
                }
                
                LoggingUtil.info(LOG, "logStatistics", 
                    "  %s - Attempts: %d, Successful Ops: %d, Overall Success: %.2f%%, Retry Success: %.2f%%, Circuit Breaker: %s%s",
                    agentType, 
                    agentStats.getTotalAttempts(),
                    agentStats.getSuccessfulOperations(),
                    agentStats.getOverallSuccessRate() * 100,
                    agentStats.getRetrySuccessRate() * 100,
                    cbStatus,
                    fallbackInfo);
            }
        }
        
        // Log detailed fallback agent availability
        LoggingUtil.info(LOG, "logStatistics", 
            "Ollama Fallback System: %s", fallbackStats.get("fallbackSystemInfo"));
    }
    
    /**
     * Calculate delay with exponential backoff and jitter.
     */
    private Duration calculateDelay(Duration initialDelay, int attemptNumber) {
        // Exponential backoff: delay = initialDelay * 2^attemptNumber
        long delayMs = initialDelay.toMillis() * (long) Math.pow(2, attemptNumber);
        
        // Add jitter to prevent thundering herd
        double jitter = 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * JITTER_FACTOR;
        delayMs = (long) (delayMs * jitter);
        
        // Cap maximum delay at 30 seconds
        delayMs = Math.min(delayMs, 30000);
        
        return Duration.ofMillis(delayMs);
    }
    
    
    /**
     * Creates a delayed execution CompletableFuture.
     */
    private CompletableFuture<Void> delayedExecution(Duration delay) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        // Use a scheduled executor for the delay
        java.util.concurrent.ScheduledExecutorService scheduler = 
            java.util.concurrent.Executors.newScheduledThreadPool(1);
        
        scheduler.schedule(() -> {
            future.complete(null);
            scheduler.shutdown();
        }, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        
        return future;
    }
    
    /**
     * Determine if an exception is retryable by checking the entire exception chain.
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        // Check the entire exception chain for retryable conditions
        Throwable current = throwable;
        while (current != null) {
            if (isRetryableExceptionType(current)) {
                LoggingUtil.debug(LOG, "isRetryableException", 
                    "Found retryable exception in chain: %s", current.getClass().getSimpleName());
                return true;
            }
            
            String message = current.getMessage();
            if (message != null && isRetryableMessage(message)) {
                LoggingUtil.debug(LOG, "isRetryableException", 
                    "Found retryable message in chain: %s", message);
                return true;
            }
            
            current = current.getCause();
        }
        
        // Check for non-retryable conditions in the entire chain
        current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && isNonRetryableMessage(message)) {
                LoggingUtil.debug(LOG, "isRetryableException", 
                    "Found non-retryable message in chain: %s", message);
                return false;
            }
            
            if (current instanceof AgentCircuitBreaker.CircuitBreakerOpenException) {
                return false;
            }
            
            current = current.getCause();
        }
        
        // Default: don't retry unknown exceptions
        LoggingUtil.debug(LOG, "isRetryableException", 
            "Exception not identified as retryable: %s", throwable.getClass().getSimpleName());
        return false;
    }
    
    /**
     * Check if exception type is retryable.
     */
    private boolean isRetryableExceptionType(Throwable throwable) {
        // Network-related errors that are typically transient
        if (throwable instanceof java.net.SocketTimeoutException ||
            throwable instanceof java.net.ConnectException ||
            throwable instanceof java.io.IOException ||
            throwable instanceof io.netty.handler.timeout.ReadTimeoutException ||
            throwable instanceof io.netty.handler.timeout.WriteTimeoutException ||
            throwable instanceof java.net.SocketException ||
            throwable instanceof java.net.UnknownHostException) {
            return true;
        }
        
        // Spring web client exceptions
        if (throwable instanceof org.springframework.web.client.ResourceAccessException ||
            throwable instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if exception message indicates a retryable condition.
     */
    private boolean isRetryableMessage(String message) {
        String lowerMessage = message.toLowerCase();
        
        // HTTP client errors that might be transient
        if (lowerMessage.contains("timeout") ||
            lowerMessage.contains("connection") ||
            lowerMessage.contains("503") ||
            lowerMessage.contains("502") ||
            lowerMessage.contains("504") ||
            lowerMessage.contains("rate limit") ||
            lowerMessage.contains("throttle") ||
            lowerMessage.contains("i/o error")) {
            return true;
        }
        
        // AI provider specific errors
        if (lowerMessage.contains("quota") ||
            lowerMessage.contains("overloaded") ||
            lowerMessage.contains("capacity") ||
            lowerMessage.contains("temporarily unavailable") ||
            lowerMessage.contains("service unavailable") ||
            lowerMessage.contains("internal server error")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if exception message indicates a non-retryable condition.
     */
    private boolean isNonRetryableMessage(String message) {
        String lowerMessage = message.toLowerCase();
        
        // Authentication/authorization errors are not retryable
        if (lowerMessage.contains("401") ||
            lowerMessage.contains("403") ||
            lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("forbidden") ||
            lowerMessage.contains("invalid_api_key") ||
            lowerMessage.contains("authentication failed") ||
            lowerMessage.contains("access denied")) {
            return true;
        }
        
        // Client errors that won't be fixed by retrying
        if (lowerMessage.contains("400") ||
            lowerMessage.contains("bad request") ||
            lowerMessage.contains("malformed") ||
            lowerMessage.contains("invalid request")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get retry configuration for specific agent types.
     * Phase 1 reliability improvements: Tuned for AI service characteristics.
     */
    private RetryConfiguration getRetryConfigForAgent(AgentType agentType) {
        switch (agentType) {
            case PAPER_PROCESSOR:
                // Paper processing can be resource-intensive, increased delay for AI services
                return new RetryConfiguration(3, Duration.ofSeconds(10)); // Was 5s
                
            case CONTENT_SUMMARIZER:
                // Content summarization needs more time for AI processing
                return new RetryConfiguration(4, Duration.ofSeconds(8)); // Was 3 retries, 3s
                
            case CONCEPT_EXPLAINER:
                // Concept explanation is complex, more retries with longer delays (Phase 1 fix)
                return new RetryConfiguration(4, Duration.ofSeconds(5)); // Was 3 retries, 2s
                
            case METADATA_ENHANCER:
                // Metadata enhancement with AI needs more time
                return new RetryConfiguration(4, Duration.ofSeconds(5)); // Was 2s
                
            case QUALITY_CHECKER:
                // Quality checking with AI models needs patience
                return new RetryConfiguration(3, Duration.ofSeconds(6)); // Was 3s
                
            case CITATION_FORMATTER:
                // Citation formatting with AI assistance needs more time
                return new RetryConfiguration(3, Duration.ofSeconds(4)); // Was 2 retries, 2s
                
            case PERPLEXITY_RESEARCHER:
                // Research operations are most tolerant (Phase 1 improvement)
                return new RetryConfiguration(5, Duration.ofSeconds(15)); // Was 4 retries, 10s
                
            case RELATED_PAPER_DISCOVERY:
                // Discovery operations may face external API limits, need patience
                return new RetryConfiguration(4, Duration.ofSeconds(12)); // Was 8s
                
            default:
                // Default configuration tuned for AI services
                return new RetryConfiguration(3, Duration.ofSeconds(8)); // Was 5s
        }
    }
    
    /**
     * Configuration for retry behavior.
     */
    private static class RetryConfiguration {
        final int maxRetries;
        final Duration initialDelay;
        
        RetryConfiguration(int maxRetries, Duration initialDelay) {
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
        }
    }
}

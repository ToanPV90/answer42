package com.samjdtechnologies.answer42.service.discovery.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.cache.CacheStats;
import com.samjdtechnologies.answer42.model.cache.CachedDiscoveryResult;
import com.samjdtechnologies.answer42.model.daos.AgentMemoryStore;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.repository.AgentMemoryStoreRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import jakarta.annotation.PostConstruct;

/**
 * Production-grade multi-level discovery cache with comprehensive monitoring and optimization.
 * Provides in-memory caching with database persistence for discovery result optimization.
 */
@Service
public class DiscoveryCache {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryCache.class);

    // Cache configuration constants
    private static final Duration DEFAULT_TTL = Duration.ofHours(6);
    private static final int MAX_CACHE_SIZE = 1000;
    private static final String CACHE_KEY_PREFIX = "discovery_cache_";

    // In-memory cache
    private final Map<String, CachedDiscoveryResult> inMemoryCache = new ConcurrentHashMap<>();
    
    // Database persistence
    private final AgentMemoryStoreRepository memoryRepository;
    
    // JSON serialization
    private final ObjectMapper objectMapper;
    
    // Statistics tracking
    private final CacheStats.StatsUpdater statsUpdater = new CacheStats.StatsUpdater();

    public DiscoveryCache(AgentMemoryStoreRepository memoryRepository, ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        LoggingUtil.info(LOG, "initialize", 
            "Discovery cache initialized with TTL: %s, Max size: %d", 
            DEFAULT_TTL, MAX_CACHE_SIZE);
    }

    /**
     * Get cached discovery result if available and not stale.
     */
    public Optional<RelatedPaperDiscoveryResult> get(UUID paperId, String configHash) {
        String cacheKey = buildCacheKey(paperId, configHash);
        long startTime = System.currentTimeMillis();

        try {
            // Level 1: Check in-memory cache
            CachedDiscoveryResult cached = inMemoryCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                cached.recordAccess();
                statsUpdater.recordHit();
                
                LoggingUtil.debug(LOG, "get", 
                    "Cache HIT (memory) for paper %s in %dms", 
                    paperId, System.currentTimeMillis() - startTime);
                return Optional.of(cached.getResult());
            }

            // Level 2: Check database cache
            Optional<RelatedPaperDiscoveryResult> dbResult = getFromDatabase(cacheKey);
            if (dbResult.isPresent()) {
                // Store back in memory for faster access
                store(paperId, configHash, dbResult.get(), DEFAULT_TTL);
                statsUpdater.recordHit();
                
                LoggingUtil.debug(LOG, "get", 
                    "Cache HIT (database) for paper %s in %dms", 
                    paperId, System.currentTimeMillis() - startTime);
                return dbResult;
            }

            // Cache miss
            statsUpdater.recordMiss();
            LoggingUtil.debug(LOG, "get", 
                "Cache MISS for paper %s in %dms", 
                paperId, System.currentTimeMillis() - startTime);
            return Optional.empty();
            
        } catch (Exception e) {
            statsUpdater.recordError();
            LoggingUtil.error(LOG, "get", 
                "Cache error for paper %s: %s", e, paperId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store discovery result in cache with default TTL.
     */
    public void store(UUID paperId, String configHash, RelatedPaperDiscoveryResult result) {
        store(paperId, configHash, result, DEFAULT_TTL);
    }

    /**
     * Store discovery result in cache with custom TTL.
     */
    public void store(UUID paperId, String configHash, RelatedPaperDiscoveryResult result, Duration ttl) {
        if (result == null) {
            return;
        }

        String cacheKey = buildCacheKey(paperId, configHash);
        Instant expiresAt = Instant.now().plus(ttl);
        long sizeBytes = estimateResultSize(result);

        CachedDiscoveryResult cached = CachedDiscoveryResult.builder()
            .result(result)
            .cachedAt(Instant.now())
            .expiresAt(expiresAt)
            .accessCount(1)
            .lastAccessAt(Instant.now())
            .sizeBytes(sizeBytes)
            .sourceHash(configHash)
            .build();

        // Store in memory cache
        inMemoryCache.put(cacheKey, cached);

        // Enforce cache size limits
        if (inMemoryCache.size() > MAX_CACHE_SIZE) {
            evictOldestEntries();
        }

        // Store in database cache (async, best effort)
        try {
            storeInDatabase(cacheKey, result, expiresAt);
        } catch (Exception e) {
            statsUpdater.recordError();
            LoggingUtil.warn(LOG, "store", 
                "Failed to store in database cache for paper %s: %s", 
                paperId, e.getMessage());
        }

        LoggingUtil.debug(LOG, "store", 
            "Cached discovery result for paper %s (size: %d bytes, expires: %s)", 
            paperId, sizeBytes, expiresAt);
    }

    /**
     * Invalidate cached result for a specific paper and configuration.
     */
    public void invalidate(UUID paperId, String configHash) {
        String cacheKey = buildCacheKey(paperId, configHash);
        
        // Remove from memory cache
        CachedDiscoveryResult removed = inMemoryCache.remove(cacheKey);
        
        // Remove from database cache
        try {
            removeFromDatabase(cacheKey);
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "invalidate", 
                "Failed to remove from database cache: %s", e.getMessage());
        }

        if (removed != null) {
            LoggingUtil.debug(LOG, "invalidate", 
                "Invalidated cache entry for paper %s", paperId);
        }
    }

    /**
     * Invalidate all cached results for a specific paper.
     */
    public void invalidateAllForPaper(UUID paperId) {
        String paperPrefix = buildCacheKey(paperId, "");
        
        int removed = 0;
        for (String key : inMemoryCache.keySet()) {
            if (key.startsWith(paperPrefix)) {
                inMemoryCache.remove(key);
                removed++;
            }
        }

        LoggingUtil.debug(LOG, "invalidateAllForPaper", 
            "Invalidated %d cache entries for paper %s", removed, paperId);
    }

    /**
     * Clear all cache entries.
     */
    public void clear() {
        int size = inMemoryCache.size();
        inMemoryCache.clear();
        statsUpdater.reset();
        
        LoggingUtil.info(LOG, "clear", 
            "Cleared %d entries from discovery cache", size);
    }

    /**
     * Get comprehensive cache statistics.
     */
    public CacheStats getStats() {
        int totalEntries = inMemoryCache.size();
        int expiredEntries = 0;
        long totalAccessCount = 0;
        Instant oldestEntry = Instant.now();
        Instant newestEntry = Instant.MIN;
        double totalSizeBytes = 0;
        double totalEfficiency = 0;

        for (CachedDiscoveryResult cached : inMemoryCache.values()) {
            if (cached.isExpired()) {
                expiredEntries++;
            }
            totalAccessCount += cached.getAccessCount();
            totalSizeBytes += cached.getSizeBytes();
            totalEfficiency += cached.getEffectivenessScore();
            
            if (cached.getCachedAt().isBefore(oldestEntry)) {
                oldestEntry = cached.getCachedAt();
            }
            if (cached.getCachedAt().isAfter(newestEntry)) {
                newestEntry = cached.getCachedAt();
            }
        }

        // Calculate derived metrics
        double averageAccessCount = totalEntries > 0 ? (double) totalAccessCount / totalEntries : 0.0;
        double memoryUsageMB = totalSizeBytes / (1024.0 * 1024.0);
        double cacheEfficiency = totalEntries > 0 ? totalEfficiency / totalEntries : 0.0;
        double memoryEfficiency = totalSizeBytes > 0 ? (double) totalAccessCount / totalSizeBytes * 1000000 : 0.0;
        
        // Calculate operations per second based on stats updater data
        long totalOps = statsUpdater.contributeToStats(CacheStats.builder()).build().getTotalOperations();
        Instant lastResetTime = statsUpdater.contributeToStats(CacheStats.builder()).build().getLastResetTime();
        double operationsPerSecond = 0.0;
        
        if (lastResetTime != null) {
            long secondsElapsed = Duration.between(lastResetTime, Instant.now()).getSeconds();
            if (secondsElapsed > 0) {
                operationsPerSecond = (double) totalOps / secondsElapsed;
            }
        }

        return statsUpdater.contributeToStats(CacheStats.builder())
            .totalEntries(totalEntries)
            .validEntries(totalEntries - expiredEntries)
            .expiredEntries(expiredEntries)
            .totalAccessCount(totalAccessCount)
            .oldestEntry(totalEntries > 0 ? oldestEntry : null)
            .newestEntry(totalEntries > 0 ? newestEntry : null)
            .memoryUsageMB(memoryUsageMB)
            .averageAccessCount(averageAccessCount)
            .cacheEfficiency(cacheEfficiency)
            .memoryEfficiency(memoryEfficiency)
            .operationsPerSecond(operationsPerSecond)
            .build();
    }

    /**
     * Build cache key from paper ID and configuration hash.
     */
    private String buildCacheKey(UUID paperId, String configHash) {
        return String.format("%s%s_%s", CACHE_KEY_PREFIX, paperId, configHash);
    }

    /**
     * Calculate cache key hash from discovery configuration.
     */
    public String calculateConfigHash(UUID paperId, 
                                     int maxResults, 
                                     boolean includeCitations,
                                     boolean includeSemanticSimilar,
                                     boolean includeTrends) {
        // Enhanced hash calculation for better cache key distribution
        String configString = String.format("%s_%d_%s_%s_%s", 
            paperId, maxResults, includeCitations, includeSemanticSimilar, includeTrends);
        
        return String.valueOf(Math.abs(configString.hashCode()));
    }

    /**
     * Scheduled cleanup of expired entries.
     * Uses existing ThreadConfig scheduler - runs every 30 minutes.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    public void cleanupExpiredEntries() {
        Instant now = Instant.now();
        
        // Count and remove expired entries
        long removed = inMemoryCache.entrySet().stream()
            .filter(entry -> entry.getValue().isExpired(now))
            .map(Map.Entry::getKey)
            .peek(key -> inMemoryCache.remove(key))
            .count();

        if (removed > 0) {
            LoggingUtil.debug(LOG, "cleanupExpiredEntries", 
                "Removed %d expired cache entries", removed);
        }
    }

    /**
     * Scheduled cache optimization - runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void optimizeCache() {
        try {
            CacheStats stats = getStats();
            
            // Log performance summary
            LoggingUtil.info(LOG, "optimizeCache", stats.getSummary());
            
            // Perform optimization if needed
            if (stats.getHitRate() < 50.0) {
                LoggingUtil.warn(LOG, "optimizeCache", 
                    "Low hit rate detected (%.1f%%), consider reviewing cache strategy", 
                    stats.getHitRate());
            }
            
            if (stats.getMemoryUsageMB() > 100.0) {
                LoggingUtil.warn(LOG, "optimizeCache", 
                    "High memory usage detected (%.1f MB), performing cleanup", 
                    stats.getMemoryUsageMB());
                evictLeastEffectiveEntries();
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "optimizeCache", 
                "Error during cache optimization", e);
        }
    }

    /**
     * Evict oldest entries when cache size limit is exceeded.
     */
    private void evictOldestEntries() {
        int targetSize = (int) (MAX_CACHE_SIZE * 0.8); // Remove 20% when limit hit
        int toRemove = inMemoryCache.size() - targetSize;
        
        if (toRemove <= 0) {
            return;
        }

        // Find oldest entries and remove them
        inMemoryCache.entrySet().stream()
            .sorted((e1, e2) -> e1.getValue().getCachedAt().compareTo(e2.getValue().getCachedAt()))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .forEach(key -> {
                inMemoryCache.remove(key);
                statsUpdater.recordEviction();
            });

        LoggingUtil.debug(LOG, "evictOldestEntries", 
            "Evicted %d oldest cache entries", toRemove);
    }

    /**
     * Evict least effective entries based on effectiveness score.
     */
    private void evictLeastEffectiveEntries() {
        int targetSize = (int) (inMemoryCache.size() * 0.9); // Remove 10%
        int toRemove = inMemoryCache.size() - targetSize;
        
        if (toRemove <= 0) {
            return;
        }

        // Find least effective entries and remove them
        inMemoryCache.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(
                e1.getValue().getEffectivenessScore(), 
                e2.getValue().getEffectivenessScore()))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .forEach(key -> {
                inMemoryCache.remove(key);
                statsUpdater.recordEviction();
            });

        LoggingUtil.debug(LOG, "evictLeastEffectiveEntries", 
            "Evicted %d least effective cache entries", toRemove);
    }

    /**
     * Retrieve from database cache.
     */
    private Optional<RelatedPaperDiscoveryResult> getFromDatabase(String cacheKey) {
        try {
            Optional<AgentMemoryStore> memoryOpt = memoryRepository.findByKey(cacheKey);
            if (memoryOpt.isPresent()) {
                AgentMemoryStore memory = memoryOpt.get();
                JsonNode data = memory.getData();
                
                // Check if cache entry has expired
                JsonNode expiresAtNode = data.get("expiresAt");
                if (expiresAtNode != null) {
                    Instant expiresAt = Instant.parse(expiresAtNode.asText());
                    if (Instant.now().isAfter(expiresAt)) {
                        // Entry expired, remove it
                        memoryRepository.delete(memory);
                        return Optional.empty();
                    }
                }
                
                // Deserialize the result
                JsonNode resultNode = data.get("result");
                if (resultNode != null) {
                    RelatedPaperDiscoveryResult result = objectMapper.treeToValue(
                        resultNode, RelatedPaperDiscoveryResult.class);
                    return Optional.of(result);
                }
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getFromDatabase", 
                "Failed to deserialize cached result for key %s", e, cacheKey);
        }
        
        return Optional.empty();
    }

    /**
     * Store in database cache.
     */
    private void storeInDatabase(String cacheKey, RelatedPaperDiscoveryResult result, Instant expiresAt) {
        try {
            // Serialize the result to JSON
            JsonNode resultNode = objectMapper.valueToTree(result);
            
            // Create cache data structure
            ObjectNode cacheData = JsonNodeFactory.instance.objectNode();
            cacheData.set("result", resultNode);
            cacheData.put("expiresAt", expiresAt.toString());
            cacheData.put("cachedAt", Instant.now().toString());
            cacheData.put("sizeBytes", estimateResultSize(result));
            
            // Create or update AgentMemoryStore entry
            Optional<AgentMemoryStore> existingOpt = memoryRepository.findByKey(cacheKey);
            AgentMemoryStore memory;
            
            if (existingOpt.isPresent()) {
                memory = existingOpt.get();
                memory.setData(cacheData);
                memory.setUpdatedAt(Instant.now());
            } else {
                memory = AgentMemoryStore.builder()
                    .key(cacheKey)
                    .data(cacheData)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            }
            
            memoryRepository.save(memory);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "storeInDatabase", 
                "Failed to serialize and store result for key %s", e, cacheKey);
            throw new RuntimeException("Database cache storage failed", e);
        }
    }

    /**
     * Remove from database cache.
     */
    private void removeFromDatabase(String cacheKey) {
        try {
            Optional<AgentMemoryStore> existing = memoryRepository.findByKey(cacheKey);
            if (existing.isPresent()) {
                memoryRepository.delete(existing.get());
            }
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "removeFromDatabase", 
                "Failed to remove database cache entry for key %s: %s", 
                cacheKey, e.getMessage());
        }
    }

    /**
     * Estimate size of result in bytes for memory management.
     */
    private long estimateResultSize(RelatedPaperDiscoveryResult result) {
        try {
            // Rough estimation based on JSON serialization
            String json = objectMapper.writeValueAsString(result);
            return json.getBytes().length;
        } catch (Exception e) {
            // Fallback estimation
            int paperCount = result.getDiscoveredPapers() != null ? result.getDiscoveredPapers().size() : 0;
            return paperCount * 2048; // Estimate 2KB per paper
        }
    }
}

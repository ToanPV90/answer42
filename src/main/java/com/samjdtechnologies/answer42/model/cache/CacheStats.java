package com.samjdtechnologies.answer42.model.cache;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comprehensive cache statistics for monitoring and optimization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheStats {
    
    // Basic metrics
    private int totalEntries;
    private int validEntries;
    private int expiredEntries;
    private long totalAccessCount;
    private Instant oldestEntry;
    private Instant newestEntry;
    private double memoryUsageMB;
    
    // Performance metrics
    private long hitCount;
    private long missCount;
    private long evictionCount;
    private long errorCount;
    
    // Efficiency metrics
    private double averageResponseTimeMs;
    private double averageAccessCount;
    private double cacheEfficiency;
    private double memoryEfficiency;
    
    // Time-based metrics
    private Instant lastResetTime;
    private long totalOperations;
    private double operationsPerSecond;

    /**
     * Calculate hit rate as percentage.
     */
    public double getHitRate() {
        long totalRequests = hitCount + missCount;
        if (totalRequests == 0) {
            return 100.0;
        }
        return ((double) hitCount / totalRequests) * 100.0;
    }

    /**
     * Calculate miss rate as percentage.
     */
    public double getMissRate() {
        return 100.0 - getHitRate();
    }

    /**
     * Calculate expired ratio as percentage.
     */
    public double getExpiredRatio() {
        if (totalEntries == 0) return 0.0;
        return ((double) expiredEntries / totalEntries) * 100.0;
    }

    /**
     * Calculate cache utilization (valid entries / total capacity).
     */
    public double getCacheUtilization(int maxCapacity) {
        if (maxCapacity == 0) return 0.0;
        return ((double) validEntries / maxCapacity) * 100.0;
    }

    /**
     * Calculate eviction rate (evictions per hour).
     */
    public double getEvictionRate() {
        if (lastResetTime == null) return 0.0;
        
        long hoursElapsed = java.time.Duration.between(lastResetTime, Instant.now()).toHours();
        if (hoursElapsed == 0) hoursElapsed = 1; // Avoid division by zero
        
        return (double) evictionCount / hoursElapsed;
    }

    /**
     * Calculate error rate as percentage of total operations.
     */
    public double getErrorRate() {
        if (totalOperations == 0) return 0.0;
        return ((double) errorCount / totalOperations) * 100.0;
    }

    /**
     * Get cache health score (0.0 to 1.0).
     */
    public double getHealthScore() {
        // Composite score based on multiple factors
        double hitRateScore = getHitRate() / 100.0;
        double errorRateScore = Math.max(0.0, 1.0 - (getErrorRate() / 100.0));
        double utilizationScore = Math.min(1.0, getCacheUtilization(1000) / 80.0); // Target 80% utilization
        double efficiencyScore = Math.min(1.0, cacheEfficiency);
        
        // Weighted average
        return (hitRateScore * 0.4) + 
               (errorRateScore * 0.3) + 
               (utilizationScore * 0.2) + 
               (efficiencyScore * 0.1);
    }

    /**
     * Get performance grade based on health score.
     */
    public String getPerformanceGrade() {
        double score = getHealthScore();
        if (score >= 0.9) return "A";
        if (score >= 0.8) return "B";
        if (score >= 0.7) return "C";
        if (score >= 0.6) return "D";
        return "F";
    }

    /**
     * Check if cache is performing optimally.
     */
    public boolean isOptimal() {
        return getHealthScore() >= 0.85 && 
               getHitRate() >= 80.0 && 
               getErrorRate() <= 1.0;
    }

    /**
     * Get summary string for logging.
     */
    public String getSummary() {
        return String.format(
            "Cache: %d entries (%.1f%% valid), %.1f%% hit rate, %.1f MB memory, Grade: %s",
            totalEntries,
            (double) validEntries / Math.max(1, totalEntries) * 100,
            getHitRate(),
            memoryUsageMB,
            getPerformanceGrade()
        );
    }

    /**
     * Get detailed performance report.
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Cache Performance Report ===\n");
        
        // Basic metrics
        report.append(String.format("Total Entries: %,d\n", totalEntries));
        report.append(String.format("Valid Entries: %,d (%.1f%%)\n", validEntries, 
            (double) validEntries / Math.max(1, totalEntries) * 100));
        report.append(String.format("Expired Entries: %,d (%.1f%%)\n", expiredEntries, getExpiredRatio()));
        report.append(String.format("Memory Usage: %.2f MB\n", memoryUsageMB));
        
        // Performance metrics
        report.append(String.format("\nPerformance:\n"));
        report.append(String.format("Hit Rate: %.2f%% (%,d hits)\n", getHitRate(), hitCount));
        report.append(String.format("Miss Rate: %.2f%% (%,d misses)\n", getMissRate(), missCount));
        report.append(String.format("Eviction Count: %,d (%.1f/hour)\n", evictionCount, getEvictionRate()));
        report.append(String.format("Error Rate: %.2f%% (%,d errors)\n", getErrorRate(), errorCount));
        
        // Efficiency metrics
        report.append(String.format("\nEfficiency:\n"));
        report.append(String.format("Average Access Count: %.1f\n", averageAccessCount));
        report.append(String.format("Cache Efficiency: %.2f\n", cacheEfficiency));
        report.append(String.format("Memory Efficiency: %.2f\n", memoryEfficiency));
        report.append(String.format("Operations/Second: %.1f\n", operationsPerSecond));
        
        // Overall assessment
        report.append(String.format("\nOverall Assessment:\n"));
        report.append(String.format("Health Score: %.2f/1.0\n", getHealthScore()));
        report.append(String.format("Performance Grade: %s\n", getPerformanceGrade()));
        report.append(String.format("Status: %s\n", isOptimal() ? "Optimal" : "Needs Attention"));
        
        return report.toString();
    }

    /**
     * Get recommendations for cache optimization.
     */
    public String getOptimizationRecommendations() {
        StringBuilder recommendations = new StringBuilder();
        
        if (getHitRate() < 70.0) {
            recommendations.append("- Low hit rate detected. Consider increasing cache TTL or size.\n");
        }
        
        if (getExpiredRatio() > 30.0) {
            recommendations.append("- High expiration rate. Consider adjusting TTL based on access patterns.\n");
        }
        
        if (getEvictionRate() > 100.0) {
            recommendations.append("- High eviction rate. Consider increasing cache size.\n");
        }
        
        if (getErrorRate() > 5.0) {
            recommendations.append("- High error rate detected. Investigate serialization/deserialization issues.\n");
        }
        
        if (memoryUsageMB > 500.0) {
            recommendations.append("- High memory usage. Consider implementing size-based eviction.\n");
        }
        
        if (averageAccessCount < 2.0) {
            recommendations.append("- Low access frequency. Consider reducing cache size or TTL.\n");
        }
        
        if (recommendations.length() == 0) {
            recommendations.append("- Cache is performing well. No immediate optimizations needed.\n");
        }
        
        return recommendations.toString();
    }

    /**
     * Thread-safe statistics updater for concurrent access.
     */
    public static class StatsUpdater {
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong missCount = new AtomicLong(0);
        private final AtomicLong evictionCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalOperations = new AtomicLong(0);
        private volatile Instant lastResetTime = Instant.now();

        public void recordHit() {
            hitCount.incrementAndGet();
            totalOperations.incrementAndGet();
        }

        public void recordMiss() {
            missCount.incrementAndGet();
            totalOperations.incrementAndGet();
        }

        public void recordEviction() {
            evictionCount.incrementAndGet();
        }

        public void recordError() {
            errorCount.incrementAndGet();
        }

        public void reset() {
            hitCount.set(0);
            missCount.set(0);
            evictionCount.set(0);
            errorCount.set(0);
            totalOperations.set(0);
            lastResetTime = Instant.now();
        }

        public CacheStats.CacheStatsBuilder contributeToStats(CacheStats.CacheStatsBuilder builder) {
            return builder
                .hitCount(hitCount.get())
                .missCount(missCount.get())
                .evictionCount(evictionCount.get())
                .errorCount(errorCount.get())
                .totalOperations(totalOperations.get())
                .lastResetTime(lastResetTime);
        }
    }
}

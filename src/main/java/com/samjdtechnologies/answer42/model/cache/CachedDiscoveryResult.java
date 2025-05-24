package com.samjdtechnologies.answer42.model.cache;

import java.time.Instant;

import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cached discovery result with metadata for cache management.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CachedDiscoveryResult {
    
    private RelatedPaperDiscoveryResult result;
    private Instant cachedAt;
    private Instant expiresAt;
    private int accessCount;
    private Instant lastAccessAt;
    private long sizeBytes;
    private String sourceHash;

    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void recordAccess() {
        accessCount++;
        lastAccessAt = Instant.now();
    }

    /**
     * Calculate age of cached entry in seconds.
     */
    public long getAgeSeconds() {
        return java.time.Duration.between(cachedAt, Instant.now()).getSeconds();
    }

    /**
     * Calculate remaining time to live in seconds.
     */
    public long getTtlSeconds() {
        return java.time.Duration.between(Instant.now(), expiresAt).getSeconds();
    }

    /**
     * Check if cache entry is stale (> 80% of TTL consumed).
     */
    public boolean isStale() {
        long totalTtl = java.time.Duration.between(cachedAt, expiresAt).getSeconds();
        long consumed = getAgeSeconds();
        return consumed > (totalTtl * 0.8);
    }

    /**
     * Calculate access frequency (accesses per hour).
     */
    public double getAccessFrequency() {
        long ageHours = Math.max(1, getAgeSeconds() / 3600);
        return (double) accessCount / ageHours;
    }

    /**
     * Calculate cache effectiveness score (0.0 to 1.0).
     */
    public double getEffectivenessScore() {
        if (isExpired()) {
            return 0.0;
        }
        
        // Score based on access frequency and remaining TTL
        double frequencyScore = Math.min(1.0, getAccessFrequency() / 10.0); // Normalize to 10 accesses/hour
        double freshnessScore = (double) getTtlSeconds() / java.time.Duration.between(cachedAt, expiresAt).getSeconds();
        
        return (frequencyScore * 0.7) + (freshnessScore * 0.3);
    }
}

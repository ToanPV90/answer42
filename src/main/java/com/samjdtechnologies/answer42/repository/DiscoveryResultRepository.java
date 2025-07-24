package com.samjdtechnologies.answer42.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.DiscoveryResult;

/**
 * Repository for managing discovery result persistence and retrieval.
 */
@Repository
public interface DiscoveryResultRepository extends JpaRepository<DiscoveryResult, UUID> {

    /**
     * Find discovery results by source paper ID.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.sourcePaperId = :sourcePaperId ORDER BY dr.discoveryStartTime DESC")
    List<DiscoveryResult> findBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    /**
     * Find the most recent discovery result for a source paper.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.sourcePaperId = :sourcePaperId ORDER BY dr.discoveryStartTime DESC LIMIT 1")
    Optional<DiscoveryResult> findLatestBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    /**
     * Find discovery results by user ID.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.userId = :userId ORDER BY dr.discoveryStartTime DESC")
    List<DiscoveryResult> findByUserId(@Param("userId") UUID userId);

    /**
     * Find discovery results within a time range.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.discoveryStartTime BETWEEN :startTime AND :endTime ORDER BY dr.discoveryStartTime DESC")
    List<DiscoveryResult> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find discovery results by configuration type.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.configurationName = :configName ORDER BY dr.discoveryStartTime DESC")
    List<DiscoveryResult> findByConfigurationName(@Param("configName") String configName);

    /**
     * Count discovery results by source paper.
     */
    @Query("SELECT COUNT(dr) FROM DiscoveryResult dr WHERE dr.sourcePaperId = :sourcePaperId")
    long countBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    /**
     * Find discovery results with high confidence scores.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.overallConfidenceScore >= :minConfidence ORDER BY dr.overallConfidenceScore DESC")
    List<DiscoveryResult> findHighConfidenceResults(@Param("minConfidence") Double minConfidence);

    /**
     * Find recent discovery results requiring user review.
     */
    @Query("SELECT dr FROM DiscoveryResult dr WHERE dr.requiresUserReview = true AND dr.discoveryStartTime >= :since ORDER BY dr.discoveryStartTime DESC")
    List<DiscoveryResult> findRecentRequiringReview(@Param("since") Instant since);

    /**
     * Delete old discovery results before the given time.
     */
    @Query("DELETE FROM DiscoveryResult dr WHERE dr.discoveryStartTime < :before")
    void deleteOldResults(@Param("before") Instant before);
}

package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.PaperRelationship;

/**
 * Repository for PaperRelationship entity operations.
 */
@Repository
public interface PaperRelationshipRepository extends JpaRepository<PaperRelationship, UUID> {

    /**
     * Find all relationships for a source paper.
     *
     * @param sourcePaperId the source paper ID
     * @return list of paper relationships
     */
    List<PaperRelationship> findBySourcePaperId(UUID sourcePaperId);

    /**
     * Find all relationships to a discovered paper.
     *
     * @param discoveredPaperId the discovered paper ID
     * @return list of paper relationships
     */
    List<PaperRelationship> findByDiscoveredPaperId(UUID discoveredPaperId);

    /**
     * Find relationships by type.
     *
     * @param relationshipType the relationship type
     * @return list of paper relationships
     */
    List<PaperRelationship> findByRelationshipType(String relationshipType);

    /**
     * Find relationships by discovery source.
     *
     * @param discoverySource the discovery source
     * @return list of paper relationships
     */
    List<PaperRelationship> findByDiscoverySource(String discoverySource);

    /**
     * Find relationships with confidence score above threshold.
     *
     * @param threshold the minimum confidence score
     * @return list of paper relationships
     */
    @Query("SELECT pr FROM PaperRelationship pr WHERE pr.confidenceScore >= :threshold ORDER BY pr.confidenceScore DESC")
    List<PaperRelationship> findByConfidenceScoreGreaterThanEqual(@Param("threshold") Double threshold);

    /**
     * Find relationships with relationship strength above threshold.
     *
     * @param threshold the minimum relationship strength
     * @return list of paper relationships
     */
    @Query("SELECT pr FROM PaperRelationship pr WHERE pr.relationshipStrength >= :threshold ORDER BY pr.relationshipStrength DESC")
    List<PaperRelationship> findByRelationshipStrengthGreaterThanEqual(@Param("threshold") Double threshold);

    /**
     * Delete all relationships for a source paper.
     *
     * @param sourcePaperId the source paper ID
     */
    void deleteBySourcePaperId(UUID sourcePaperId);

    /**
     * Delete all relationships to a discovered paper.
     *
     * @param discoveredPaperId the discovered paper ID
     */
    void deleteByDiscoveredPaperId(UUID discoveredPaperId);

    /**
     * Count relationships for a source paper.
     *
     * @param sourcePaperId the source paper ID
     * @return relationship count
     */
    long countBySourcePaperId(UUID sourcePaperId);

    /**
     * Get relationship statistics by discovery source.
     *
     * @return list of discovery source statistics
     */
    @Query("SELECT pr.discoverySource, COUNT(pr), AVG(pr.confidenceScore) " +
           "FROM PaperRelationship pr " +
           "GROUP BY pr.discoverySource " +
           "ORDER BY COUNT(pr) DESC")
    List<Object[]> getDiscoverySourceStats();
}

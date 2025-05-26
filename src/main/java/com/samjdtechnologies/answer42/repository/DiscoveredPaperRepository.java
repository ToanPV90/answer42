package com.samjdtechnologies.answer42.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;

/**
 * Repository interface for DiscoveredPaper entities.
 * Provides database access methods for discovered paper management and relationship tracking.
 */
@Repository
public interface DiscoveredPaperRepository extends JpaRepository<DiscoveredPaper, UUID> {

    // Basic discovery queries
    List<DiscoveredPaper> findBySourcePaper_Id(UUID sourcePaperId);
    List<DiscoveredPaper> findByUser_Id(UUID userId);
    List<DiscoveredPaper> findByDiscoverySource(String discoverySource);

    // Relationship-based queries
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.relationshipType = :relationshipType ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findBySourcePaperIdAndRelationshipType(@Param("sourcePaperId") UUID sourcePaperId, @Param("relationshipType") String relationshipType);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.relevanceScore >= :minScore ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findBySourcePaperIdAndRelevanceScoreGreaterThanEqual(@Param("sourcePaperId") UUID sourcePaperId, @Param("minScore") Double minScore);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.discoverySource IN :sources ORDER BY dp.relevanceScore DESC, dp.discoveredAt DESC")
    List<DiscoveredPaper> findBySourcePaperIdAndDiscoverySourceIn(@Param("sourcePaperId") UUID sourcePaperId, @Param("sources") List<String> sources);

    // Duplicate detection and deduplication
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.externalId = :externalId AND dp.sourcePaper.id = :sourcePaperId")
    List<DiscoveredPaper> findDuplicatesByExternalId(@Param("externalId") String externalId, @Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.title = :title AND dp.sourcePaper.id = :sourcePaperId")
    List<DiscoveredPaper> findDuplicatesByTitle(@Param("title") String title, @Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.doi = :doi AND dp.sourcePaper.id = :sourcePaperId")
    Optional<DiscoveredPaper> findByDoiAndSourcePaperId(@Param("doi") String doi, @Param("sourcePaperId") UUID sourcePaperId);

    // Performance and analytics queries
    @Query("SELECT COUNT(dp) FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId")
    long countBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT dp.discoverySource, COUNT(dp) FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId GROUP BY dp.discoverySource")
    List<Object[]> countBySourcePaperIdGroupByDiscoverySource(@Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT dp.relationshipType, COUNT(dp) FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId GROUP BY dp.relationshipType")
    List<Object[]> countBySourcePaperIdGroupByRelationshipType(@Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT AVG(dp.relevanceScore) FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.discoverySource = :source")
    Double getAverageRelevanceScoreBySourceAndDiscoverySource(@Param("sourcePaperId") UUID sourcePaperId, @Param("source") String source);

    // Discovery session management
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.discoverySessionId = :sessionId ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findByDiscoverySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.user.id = :userId AND dp.discoverySessionId = :sessionId ORDER BY dp.discoveredAt DESC")
    List<DiscoveredPaper> findByUserIdAndDiscoverySessionId(@Param("userId") UUID userId, @Param("sessionId") String sessionId);

    // Quality and verification queries
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.isVerified = true ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findVerifiedBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.citationCount >= :minCitations ORDER BY dp.citationCount DESC")
    List<DiscoveredPaper> findHighlyCitedBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId, @Param("minCitations") Integer minCitations);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.openAccess = true ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findOpenAccessBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    // User interaction queries
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.user.id = :userId AND dp.userRating IS NOT NULL ORDER BY dp.userRating DESC, dp.discoveredAt DESC")
    List<DiscoveredPaper> findRatedByUserId(@Param("userId") UUID userId);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.user.id = :userId AND dp.userNotes IS NOT NULL ORDER BY dp.discoveredAt DESC")
    List<DiscoveredPaper> findWithNotesByUserId(@Param("userId") UUID userId);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.user.id = :userId AND dp.isArchived = false ORDER BY dp.discoveredAt DESC")
    List<DiscoveredPaper> findActiveByUserId(@Param("userId") UUID userId);

    // Recent discoveries
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.user.id = :userId AND dp.discoveredAt >= :since ORDER BY dp.discoveredAt DESC")
    List<DiscoveredPaper> findRecentByUserId(@Param("userId") UUID userId, @Param("since") ZonedDateTime since);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.discoveredAt >= :since ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findRecentBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId, @Param("since") ZonedDateTime since);

    // Advanced filtering queries
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.year >= :minYear AND dp.year <= :maxYear ORDER BY dp.year DESC, dp.relevanceScore DESC")
    List<DiscoveredPaper> findBySourcePaperIdAndYearRange(@Param("sourcePaperId") UUID sourcePaperId, @Param("minYear") Integer minYear, @Param("maxYear") Integer maxYear);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.venueType = :venueType ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> findBySourcePaperIdAndVenueType(@Param("sourcePaperId") UUID sourcePaperId, @Param("venueType") String venueType);

    // Topic and field filtering
    @Query(value = "SELECT * FROM answer42.discovered_papers dp WHERE dp.source_paper_id = :sourcePaperId AND dp.topics @> :topic ORDER BY dp.relevance_score DESC", nativeQuery = true)
    List<DiscoveredPaper> findBySourcePaperIdAndTopic(@Param("sourcePaperId") UUID sourcePaperId, @Param("topic") String topic);

    @Query(value = "SELECT * FROM answer42.discovered_papers dp WHERE dp.source_paper_id = :sourcePaperId AND dp.fields_of_study @> :field ORDER BY dp.relevance_score DESC", nativeQuery = true)
    List<DiscoveredPaper> findBySourcePaperIdAndFieldOfStudy(@Param("sourcePaperId") UUID sourcePaperId, @Param("field") String field);

    // Cleanup and maintenance queries
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.isDuplicate = true")
    List<DiscoveredPaper> findAllDuplicates();

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.discoveredAt < :cutoff AND dp.lastAccessedAt IS NULL")
    List<DiscoveredPaper> findNeverAccessedOlderThan(@Param("cutoff") ZonedDateTime cutoff);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.lastAccessedAt < :cutoff AND dp.userRating IS NULL")
    List<DiscoveredPaper> findUnratedNotAccessedSince(@Param("cutoff") ZonedDateTime cutoff);

    // Batch operations
    @Modifying
    @Query("UPDATE DiscoveredPaper dp SET dp.isArchived = true WHERE dp.user.id = :userId AND dp.relevanceScore < :minScore")
    int archiveLowRelevanceByUserId(@Param("userId") UUID userId, @Param("minScore") Double minScore);

    @Modifying
    @Query("UPDATE DiscoveredPaper dp SET dp.lastAccessedAt = :accessTime WHERE dp.id = :paperId")
    int updateLastAccessedAt(@Param("paperId") UUID paperId, @Param("accessTime") ZonedDateTime accessTime);

    @Modifying
    @Query("DELETE FROM DiscoveredPaper dp WHERE dp.discoveredAt < :cutoff AND dp.isDuplicate = true")
    int deleteDuplicatesOlderThan(@Param("cutoff") ZonedDateTime cutoff);

    // Statistics and insights
    @Query("SELECT AVG(dp.relevanceScore) FROM DiscoveredPaper dp WHERE dp.user.id = :userId")
    Double getAverageRelevanceScoreByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(dp), dp.discoverySource FROM DiscoveredPaper dp WHERE dp.user.id = :userId GROUP BY dp.discoverySource")
    List<Object[]> getDiscoverySourceStatsForUser(@Param("userId") UUID userId);

    @Query("SELECT COUNT(dp), dp.relationshipType FROM DiscoveredPaper dp WHERE dp.user.id = :userId GROUP BY dp.relationshipType")
    List<Object[]> getRelationshipTypeStatsForUser(@Param("userId") UUID userId);

    // Text search capabilities
    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND (LOWER(dp.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(dp.paperAbstract) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) ORDER BY dp.relevanceScore DESC")
    List<DiscoveredPaper> searchBySourcePaperIdAndText(@Param("sourcePaperId") UUID sourcePaperId, @Param("searchTerm") String searchTerm);

    @Query("SELECT dp FROM DiscoveredPaper dp WHERE dp.user.id = :userId AND (LOWER(dp.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(dp.paperAbstract) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) ORDER BY dp.discoveredAt DESC")
    List<DiscoveredPaper> searchByUserIdAndText(@Param("userId") UUID userId, @Param("searchTerm") String searchTerm);

    // Author-based queries
    @Query(value = "SELECT * FROM answer42.discovered_papers dp WHERE dp.source_paper_id = :sourcePaperId AND dp.authors @> :authorName ORDER BY dp.relevance_score DESC", nativeQuery = true)
    List<DiscoveredPaper> findBySourcePaperIdAndAuthor(@Param("sourcePaperId") UUID sourcePaperId, @Param("authorName") String authorName);

    // Complex aggregation queries
    @Query("SELECT dp.year, COUNT(dp), AVG(dp.relevanceScore) FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.year IS NOT NULL GROUP BY dp.year ORDER BY dp.year DESC")
    List<Object[]> getYearlyDiscoveryStatsForSourcePaper(@Param("sourcePaperId") UUID sourcePaperId);

    @Query("SELECT dp.venue, COUNT(dp), AVG(dp.relevanceScore) FROM DiscoveredPaper dp WHERE dp.sourcePaper.id = :sourcePaperId AND dp.venue IS NOT NULL GROUP BY dp.venue ORDER BY COUNT(dp) DESC")
    List<Object[]> getVenueDiscoveryStatsForSourcePaper(@Param("sourcePaperId") UUID sourcePaperId);
}

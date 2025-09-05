package com.samjdtechnologies.answer42.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.CitationVerification;

/**
 * Repository for CitationVerification entities providing database access methods
 * for citation verification results against external sources.
 */
@Repository
public interface CitationVerificationRepository extends JpaRepository<CitationVerification, UUID> {

    /**
     * Find verification for a specific citation.
     */
    Optional<CitationVerification> findByCitationId(UUID citationId);

    /**
     * Find all verifications for citations of a specific paper.
     */
    List<CitationVerification> findByPaperIdOrderByVerificationDateDesc(UUID paperId);

    /**
     * Find all verifications for a specific paper.
     */
    List<CitationVerification> findByPaperId(UUID paperId);

    /**
     * Find verified citations for a paper.
     */
    List<CitationVerification> findByPaperIdAndVerifiedTrueOrderByConfidenceDesc(UUID paperId);

    /**
     * Find unverified citations for a paper.
     */
    List<CitationVerification> findByPaperIdAndVerifiedFalseOrderByVerificationDateDesc(UUID paperId);

    /**
     * Find verifications by verification source.
     */
    List<CitationVerification> findByVerificationSourceContainingIgnoreCase(String source);

    /**
     * Find verifications with confidence above threshold.
     */
    List<CitationVerification> findByConfidenceGreaterThanEqualOrderByConfidenceDesc(Double threshold);

    /**
     * Find verifications for a paper with confidence above threshold.
     */
    List<CitationVerification> findByPaperIdAndConfidenceGreaterThanEqualOrderByConfidenceDesc(
        UUID paperId, Double threshold);

    /**
     * Find verifications by DOI.
     */
    List<CitationVerification> findByDoiIgnoreCase(String doi);

    /**
     * Find verifications by Semantic Scholar ID.
     */
    List<CitationVerification> findBySemanticScholarId(String semanticScholarId);

    /**
     * Find verifications by arXiv ID.
     */
    List<CitationVerification> findByArxivId(String arxivId);

    /**
     * Find recent verifications within time range.
     */
    List<CitationVerification> findByVerificationDateAfterOrderByVerificationDateDesc(Instant after);

    /**
     * Find verifications between dates.
     */
    List<CitationVerification> findByVerificationDateBetweenOrderByVerificationDateDesc(
        Instant startDate, Instant endDate);

    /**
     * Check if citation has been verified.
     */
    boolean existsByCitationId(UUID citationId);

    /**
     * Count verified citations for a paper.
     */
    long countByPaperIdAndVerifiedTrue(UUID paperId);

    /**
     * Count total verifications for a paper.
     */
    long countByPaperId(UUID paperId);

    /**
     * Count verifications by verification source.
     */
    @Query("SELECT COUNT(cv) FROM CitationVerification cv WHERE cv.verificationSource LIKE %:source%")
    long countByVerificationSourceContaining(@Param("source") String source);

    /**
     * Get verification statistics for a paper.
     */
    @Query("SELECT " +
           "COUNT(*) as total, " +
           "COUNT(CASE WHEN cv.verified = true THEN 1 END) as verified, " +
           "AVG(cv.confidence) as avgConfidence, " +
           "MAX(cv.confidence) as maxConfidence, " +
           "MIN(cv.confidence) as minConfidence " +
           "FROM CitationVerification cv WHERE cv.paperId = :paperId")
    Optional<Object[]> getVerificationStatistics(@Param("paperId") UUID paperId);

    /**
     * Get verification statistics by source for a paper.
     */
    @Query("SELECT " +
           "cv.verificationSource as source, " +
           "COUNT(*) as total, " +
           "COUNT(CASE WHEN cv.verified = true THEN 1 END) as verified, " +
           "AVG(cv.confidence) as avgConfidence " +
           "FROM CitationVerification cv " +
           "WHERE cv.paperId = :paperId " +
           "GROUP BY cv.verificationSource " +
           "ORDER BY total DESC")
    List<Object[]> getVerificationStatisticsBySource(@Param("paperId") UUID paperId);

    /**
     * Find verifications that have external IDs (DOI, Semantic Scholar, or arXiv).
     */
    @Query("SELECT cv FROM CitationVerification cv " +
           "WHERE cv.paperId = :paperId " +
           "AND (cv.doi IS NOT NULL OR cv.semanticScholarId IS NOT NULL OR cv.arxivId IS NOT NULL) " +
           "ORDER BY cv.confidence DESC")
    List<CitationVerification> findByPaperIdWithExternalIds(@Param("paperId") UUID paperId);

    /**
     * Find verifications without external IDs.
     */
    @Query("SELECT cv FROM CitationVerification cv " +
           "WHERE cv.paperId = :paperId " +
           "AND cv.doi IS NULL AND cv.semanticScholarId IS NULL AND cv.arxivId IS NULL " +
           "ORDER BY cv.confidence DESC")
    List<CitationVerification> findByPaperIdWithoutExternalIds(@Param("paperId") UUID paperId);

    /**
     * Find duplicate verifications (same DOI, different citation).
     */
    @Query("SELECT cv FROM CitationVerification cv " +
           "WHERE cv.doi IS NOT NULL " +
           "AND cv.doi IN (" +
           "  SELECT cv2.doi FROM CitationVerification cv2 " +
           "  WHERE cv2.doi IS NOT NULL " +
           "  GROUP BY cv2.doi " +
           "  HAVING COUNT(cv2.citationId) > 1" +
           ") " +
           "ORDER BY cv.doi, cv.confidence DESC")
    List<CitationVerification> findDuplicateVerificationsByDoi();

    /**
     * Find high-confidence verifications for a paper.
     */
    @Query("SELECT cv FROM CitationVerification cv " +
           "WHERE cv.paperId = :paperId " +
           "AND cv.verified = true " +
           "AND cv.confidence >= :threshold " +
           "ORDER BY cv.confidence DESC")
    List<CitationVerification> findHighConfidenceVerifications(
        @Param("paperId") UUID paperId, @Param("threshold") Double threshold);

    /**
     * Find verifications needing review (low confidence but claimed verified).
     */
    @Query("SELECT cv FROM CitationVerification cv " +
           "WHERE cv.verified = true " +
           "AND cv.confidence < :threshold " +
           "ORDER BY cv.confidence ASC")
    List<CitationVerification> findVerificationsNeedingReview(@Param("threshold") Double threshold);

    /**
     * Get verification coverage for a paper (percentage of citations verified).
     */
    @Query("SELECT " +
           "COUNT(cv) as totalVerifications, " +
           "COUNT(CASE WHEN cv.verified = true THEN 1 END) as verifiedCount, " +
           "(COUNT(CASE WHEN cv.verified = true THEN 1 END) * 100.0 / COUNT(cv)) as coveragePercentage " +
           "FROM CitationVerification cv WHERE cv.paperId = :paperId")
    Optional<Object[]> getVerificationCoverage(@Param("paperId") UUID paperId);

    /**
     * Delete verifications for a specific paper.
     */
    void deleteByPaperId(UUID paperId);

    /**
     * Delete verifications for a specific citation.
     */
    void deleteByCitationId(UUID citationId);

    /**
     * Delete old verifications before a specific date.
     */
    @Query("DELETE FROM CitationVerification cv WHERE cv.verificationDate < :beforeDate")
    int deleteByVerificationDateBefore(@Param("beforeDate") Instant beforeDate);
}

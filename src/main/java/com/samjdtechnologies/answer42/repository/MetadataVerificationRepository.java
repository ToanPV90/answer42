package com.samjdtechnologies.answer42.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.MetadataVerification;

/**
 * Repository interface for MetadataVerification entities.
 * Provides data access operations for metadata verification tracking.
 */
@Repository
@Transactional(readOnly = true)
public interface MetadataVerificationRepository extends JpaRepository<MetadataVerification, UUID> {

    /**
     * Find all metadata verifications for a specific paper
     * @param paperId The paper UUID
     * @return List of metadata verifications for the paper
     */
    List<MetadataVerification> findByPaperId(UUID paperId);

    /**
     * Find all metadata verifications for a specific paper ordered by confidence descending
     * @param paperId The paper UUID
     * @return List of metadata verifications ordered by confidence
     */
    List<MetadataVerification> findByPaperIdOrderByConfidenceDesc(UUID paperId);

    /**
     * Find all metadata verifications from a specific source
     * @param source The verification source (e.g., "crossref", "semantic_scholar", "arxiv")
     * @return List of metadata verifications from the source
     */
    List<MetadataVerification> findBySource(String source);

    /**
     * Find all metadata verifications for a paper from a specific source
     * @param paperId The paper UUID
     * @param source The verification source
     * @return List of metadata verifications for the paper from the source
     */
    List<MetadataVerification> findByPaperIdAndSource(UUID paperId, String source);

    /**
     * Find the most recent metadata verification for a paper from a specific source
     * @param paperId The paper UUID
     * @param source The verification source
     * @return Optional containing the most recent verification if found
     */
    Optional<MetadataVerification> findFirstByPaperIdAndSourceOrderByVerifiedAtDesc(UUID paperId, String source);

    /**
     * Find all high confidence metadata verifications (confidence >= threshold)
     * @param paperId The paper UUID
     * @param confidenceThreshold The minimum confidence threshold
     * @return List of high confidence metadata verifications
     */
    @Query("SELECT mv FROM MetadataVerification mv WHERE mv.paperId = :paperId AND mv.confidence >= :threshold ORDER BY mv.confidence DESC")
    List<MetadataVerification> findHighConfidenceVerifications(@Param("paperId") UUID paperId, @Param("threshold") Double confidenceThreshold);

    /**
     * Find all metadata verifications with metadata present
     * @param paperId The paper UUID
     * @return List of metadata verifications that have metadata content
     */
    @Query("SELECT mv FROM MetadataVerification mv WHERE mv.paperId = :paperId AND mv.metadata IS NOT NULL ORDER BY mv.confidence DESC")
    List<MetadataVerification> findVerificationsWithMetadata(@Param("paperId") UUID paperId);

    /**
     * Find all metadata verifications created after a specific date
     * @param paperId The paper UUID
     * @param afterDate The date threshold
     * @return List of metadata verifications created after the date
     */
    List<MetadataVerification> findByPaperIdAndCreatedAtAfter(UUID paperId, Instant afterDate);

    /**
     * Find all metadata verifications verified after a specific date
     * @param paperId The paper UUID
     * @param afterDate The date threshold
     * @return List of metadata verifications verified after the date
     */
    List<MetadataVerification> findByPaperIdAndVerifiedAtAfter(UUID paperId, Instant afterDate);

    /**
     * Find all metadata verifications that used a specific identifier
     * @param identifierUsed The identifier that was used for matching
     * @return List of metadata verifications that used this identifier
     */
    List<MetadataVerification> findByIdentifierUsed(String identifierUsed);

    /**
     * Find all metadata verifications matched by a specific method
     * @param matchedBy The matching method (e.g., "doi", "title", "author_title")
     * @return List of metadata verifications matched by this method
     */
    List<MetadataVerification> findByMatchedBy(String matchedBy);

    /**
     * Find all metadata verifications for a paper matched by a specific method
     * @param paperId The paper UUID
     * @param matchedBy The matching method
     * @return List of metadata verifications for the paper matched by this method
     */
    List<MetadataVerification> findByPaperIdAndMatchedBy(UUID paperId, String matchedBy);

    /**
     * Count metadata verifications by source
     * @param source The verification source
     * @return Count of metadata verifications from this source
     */
    long countBySource(String source);

    /**
     * Count metadata verifications for a specific paper
     * @param paperId The paper UUID
     * @return Count of metadata verifications for the paper
     */
    long countByPaperId(UUID paperId);

    /**
     * Count high confidence metadata verifications for a paper
     * @param paperId The paper UUID
     * @param confidenceThreshold The minimum confidence threshold
     * @return Count of high confidence metadata verifications
     */
    @Query("SELECT COUNT(mv) FROM MetadataVerification mv WHERE mv.paperId = :paperId AND mv.confidence >= :threshold")
    long countHighConfidenceVerifications(@Param("paperId") UUID paperId, @Param("threshold") Double confidenceThreshold);

    /**
     * Find all metadata verifications within a confidence range
     * @param paperId The paper UUID
     * @param minConfidence The minimum confidence
     * @param maxConfidence The maximum confidence
     * @return List of metadata verifications within the confidence range
     */
    @Query("SELECT mv FROM MetadataVerification mv WHERE mv.paperId = :paperId AND mv.confidence BETWEEN :minConfidence AND :maxConfidence ORDER BY mv.confidence DESC")
    List<MetadataVerification> findByConfidenceRange(@Param("paperId") UUID paperId, @Param("minConfidence") Double minConfidence, @Param("maxConfidence") Double maxConfidence);

    /**
     * Find the best metadata verification for a paper (highest confidence)
     * @param paperId The paper UUID
     * @return Optional containing the best metadata verification if found
     */
    Optional<MetadataVerification> findFirstByPaperIdOrderByConfidenceDesc(UUID paperId);

    /**
     * Find all metadata verifications for multiple papers
     * @param paperIds The list of paper UUIDs
     * @return List of metadata verifications for the papers
     */
    @Query("SELECT mv FROM MetadataVerification mv WHERE mv.paperId IN :paperIds ORDER BY mv.paperId, mv.confidence DESC")
    List<MetadataVerification> findByPaperIdIn(@Param("paperIds") List<UUID> paperIds);

    /**
     * Find recent metadata verifications (within last 24 hours)
     * @param paperId The paper UUID
     * @return List of recent metadata verifications
     */
    @Query("SELECT mv FROM MetadataVerification mv WHERE mv.paperId = :paperId AND mv.verifiedAt >= :since ORDER BY mv.verifiedAt DESC")
    List<MetadataVerification> findRecentVerifications(@Param("paperId") UUID paperId, @Param("since") Instant since);

    /**
     * Check if a paper has any metadata verifications
     * @param paperId The paper UUID
     * @return true if the paper has metadata verifications
     */
    boolean existsByPaperId(UUID paperId);

    /**
     * Check if a paper has high confidence metadata verifications
     * @param paperId The paper UUID
     * @param confidenceThreshold The minimum confidence threshold
     * @return true if the paper has high confidence metadata verifications
     */
    @Query("SELECT COUNT(mv) > 0 FROM MetadataVerification mv WHERE mv.paperId = :paperId AND mv.confidence >= :threshold")
    boolean hasHighConfidenceVerifications(@Param("paperId") UUID paperId, @Param("threshold") Double confidenceThreshold);

    /**
     * Delete all metadata verifications for a specific paper
     * @param paperId The paper UUID
     * @return Number of deleted records
     */
    @Transactional
    long deleteByPaperId(UUID paperId);

    /**
     * Delete all metadata verifications from a specific source
     * @param source The verification source
     * @return Number of deleted records
     */
    @Transactional
    long deleteBySource(String source);

    /**
     * Delete all metadata verifications for a paper from a specific source
     * @param paperId The paper UUID
     * @param source The verification source
     * @return Number of deleted records
     */
    @Transactional
    long deleteByPaperIdAndSource(UUID paperId, String source);
}

package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.DiscoveryFeedback;

/**
 * Repository for managing discovery feedback data.
 * Provides methods for storing and retrieving user feedback on discovered papers.
 */
@Repository
public interface DiscoveryFeedbackRepository extends JpaRepository<DiscoveryFeedback, UUID> {

    /**
     * Find all feedback for a specific discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return List of feedback entries
     */
    List<DiscoveryFeedback> findByDiscoveredPaperId(UUID discoveredPaperId);

    /**
     * Find all feedback by a specific user.
     * 
     * @param userId ID of the user
     * @return List of feedback entries
     */
    List<DiscoveryFeedback> findByUserId(UUID userId);

    /**
     * Find all feedback for a specific source paper.
     * 
     * @param sourcePaperId ID of the source paper
     * @return List of feedback entries
     */
    List<DiscoveryFeedback> findBySourcePaperId(UUID sourcePaperId);

    /**
     * Find feedback by user and discovered paper combination.
     * 
     * @param userId ID of the user
     * @param discoveredPaperId ID of the discovered paper
     * @return List of feedback entries
     */
    List<DiscoveryFeedback> findByUserIdAndDiscoveredPaperId(UUID userId, UUID discoveredPaperId);

    /**
     * Find feedback by type.
     * 
     * @param feedbackType Type of feedback (EXPLICIT_RATING, IMPLICIT_BEHAVIOR, TIME_SPENT)
     * @return List of feedback entries
     */
    List<DiscoveryFeedback> findByFeedbackType(String feedbackType);

    /**
     * Count total feedback entries for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Count of feedback entries
     */
    long countByDiscoveredPaperId(UUID discoveredPaperId);

    /**
     * Count positive interactions for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Count of positive interactions
     */
    @Query("SELECT COUNT(f) FROM DiscoveryFeedback f WHERE f.discoveredPaperId = :discoveredPaperId " +
           "AND (f.clickedPaper = true OR f.downloadedPaper = true OR f.bookmarkedPaper = true OR " +
           "f.sharedPaper = true OR f.timeSpentViewingSeconds > 30)")
    long countPositiveInteractionsByDiscoveredPaperId(@Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Get average relevance rating for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Average relevance rating or null if no ratings
     */
    @Query("SELECT AVG(f.relevanceRating) FROM DiscoveryFeedback f WHERE f.discoveredPaperId = :discoveredPaperId " +
           "AND f.relevanceRating IS NOT NULL")
    Double getAverageRelevanceRatingByDiscoveredPaperId(@Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Get average quality rating for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Average quality rating or null if no ratings
     */
    @Query("SELECT AVG(f.qualityRating) FROM DiscoveryFeedback f WHERE f.discoveredPaperId = :discoveredPaperId " +
           "AND f.qualityRating IS NOT NULL")
    Double getAverageQualityRatingByDiscoveredPaperId(@Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Get average usefulness rating for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Average usefulness rating or null if no ratings
     */
    @Query("SELECT AVG(f.usefulnessRating) FROM DiscoveryFeedback f WHERE f.discoveredPaperId = :discoveredPaperId " +
           "AND f.usefulnessRating IS NOT NULL")
    Double getAverageUsefulnessRatingByDiscoveredPaperId(@Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Find recent feedback for a user (last 30 days).
     * 
     * @param userId ID of the user
     * @return List of recent feedback entries
     */
    @Query(value = "SELECT * FROM answer42.discovery_feedback WHERE user_id = :userId " +
           "AND created_at >= CURRENT_TIMESTAMP - INTERVAL '30 days' " +
           "ORDER BY created_at DESC", nativeQuery = true)
    List<DiscoveryFeedback> findRecentFeedbackByUserId(@Param("userId") UUID userId);

    /**
     * Find feedback with explicit ratings only.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return List of feedback with ratings
     */
    @Query("SELECT f FROM DiscoveryFeedback f WHERE f.discoveredPaperId = :discoveredPaperId " +
           "AND (f.relevanceRating IS NOT NULL OR f.qualityRating IS NOT NULL OR f.usefulnessRating IS NOT NULL)")
    List<DiscoveryFeedback> findExplicitRatingsByDiscoveredPaperId(@Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Check if user has already provided feedback for a specific discovered paper.
     * 
     * @param userId ID of the user
     * @param discoveredPaperId ID of the discovered paper
     * @param feedbackType Type of feedback
     * @return true if feedback exists
     */
    boolean existsByUserIdAndDiscoveredPaperIdAndFeedbackType(UUID userId, UUID discoveredPaperId, String feedbackType);

    /**
     * Get feedback statistics for a source paper.
     * 
     * @param sourcePaperId ID of the source paper
     * @return List of feedback entries with statistics
     */
    @Query("SELECT f FROM DiscoveryFeedback f WHERE f.sourcePaperId = :sourcePaperId " +
           "ORDER BY f.createdAt DESC")
    List<DiscoveryFeedback> findFeedbackStatsBySourcePaperId(@Param("sourcePaperId") UUID sourcePaperId);

    /**
     * Delete feedback older than specified days.
     * 
     * @param days Number of days to keep
     * @return Number of deleted records
     */
    @Query(value = "DELETE FROM answer42.discovery_feedback WHERE created_at < CURRENT_TIMESTAMP - INTERVAL ':days days'", nativeQuery = true)
    int deleteOldFeedback(@Param("days") int days);
}

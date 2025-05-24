package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.daos.DiscoveryFeedback;
import com.samjdtechnologies.answer42.repository.DiscoveryFeedbackRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing discovery feedback operations.
 * Handles saving, retrieving, and analyzing user feedback on discovered papers.
 */
@Service
@Transactional
public class DiscoveryFeedbackService {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryFeedbackService.class);

    private final DiscoveryFeedbackRepository discoveryFeedbackRepository;

    public DiscoveryFeedbackService(DiscoveryFeedbackRepository discoveryFeedbackRepository) {
        this.discoveryFeedbackRepository = discoveryFeedbackRepository;
    }

    /**
     * Saves explicit user rating feedback.
     * 
     * @param userId User providing feedback
     * @param discoveredPaperId Paper being rated
     * @param sourcePaperId Source paper that led to discovery
     * @param relevanceRating Relevance rating (1-5)
     * @param qualityRating Quality rating (1-5)
     * @param usefulnessRating Usefulness rating (1-5)
     * @param feedbackText Optional text feedback
     * @param sessionId User session ID
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @return Saved feedback instance
     */
    public DiscoveryFeedback saveExplicitRating(UUID userId, UUID discoveredPaperId, UUID sourcePaperId,
            Integer relevanceRating, Integer qualityRating, Integer usefulnessRating, String feedbackText,
            String sessionId, String ipAddress, String userAgent) {
        
        try {
            // Check if user already provided explicit rating for this paper
            boolean alreadyRated = discoveryFeedbackRepository.existsByUserIdAndDiscoveredPaperIdAndFeedbackType(
                userId, discoveredPaperId, "EXPLICIT_RATING");
            
            if (alreadyRated) {
                LoggingUtil.warn(LOG, "saveExplicitRating", 
                    "User %s already provided explicit rating for paper %s", userId, discoveredPaperId);
                // Still save it as users might want to update their rating
            }

            DiscoveryFeedback feedback = DiscoveryFeedback.createExplicitRating(
                userId, discoveredPaperId, sourcePaperId, relevanceRating, qualityRating, usefulnessRating, feedbackText);
            
            feedback.setSessionMetadata(sessionId, ipAddress, userAgent);
            
            DiscoveryFeedback saved = discoveryFeedbackRepository.save(feedback);
            
            LoggingUtil.info(LOG, "saveExplicitRating", 
                "Saved explicit rating feedback: user=%s, paper=%s, relevance=%s, quality=%s, usefulness=%s", 
                userId, discoveredPaperId, relevanceRating, qualityRating, usefulnessRating);
            
            return saved;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveExplicitRating", "Error saving explicit rating feedback", e);
            throw new RuntimeException("Failed to save feedback: " + e.getMessage(), e);
        }
    }

    /**
     * Saves implicit user behavior feedback.
     * 
     * @param userId User performing action
     * @param discoveredPaperId Paper being interacted with
     * @param sourcePaperId Source paper that led to discovery
     * @param actionType Type of action (clicked, bookmarked, downloaded, shared)
     * @param sessionId User session ID
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @return Saved feedback instance
     */
    public DiscoveryFeedback saveImplicitBehavior(UUID userId, UUID discoveredPaperId, UUID sourcePaperId,
            String actionType, String sessionId, String ipAddress, String userAgent) {
        
        try {
            DiscoveryFeedback feedback = DiscoveryFeedback.createImplicitFeedback(
                userId, discoveredPaperId, sourcePaperId, actionType);
            
            feedback.setSessionMetadata(sessionId, ipAddress, userAgent);
            
            DiscoveryFeedback saved = discoveryFeedbackRepository.save(feedback);
            
            LoggingUtil.info(LOG, "saveImplicitBehavior", 
                "Saved implicit behavior feedback: user=%s, paper=%s, action=%s", 
                userId, discoveredPaperId, actionType);
            
            return saved;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveImplicitBehavior", "Error saving implicit behavior feedback", e);
            throw new RuntimeException("Failed to save implicit feedback: " + e.getMessage(), e);
        }
    }

    /**
     * Saves time spent viewing feedback.
     * 
     * @param userId User viewing paper
     * @param discoveredPaperId Paper being viewed
     * @param sourcePaperId Source paper that led to discovery
     * @param timeSpentSeconds Time spent viewing in seconds
     * @param sessionId User session ID
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @return Saved feedback instance
     */
    public DiscoveryFeedback saveTimeSpentViewing(UUID userId, UUID discoveredPaperId, UUID sourcePaperId,
            Integer timeSpentSeconds, String sessionId, String ipAddress, String userAgent) {
        
        try {
            DiscoveryFeedback feedback = DiscoveryFeedback.createTimeSpentFeedback(
                userId, discoveredPaperId, sourcePaperId, timeSpentSeconds);
            
            feedback.setSessionMetadata(sessionId, ipAddress, userAgent);
            
            DiscoveryFeedback saved = discoveryFeedbackRepository.save(feedback);
            
            LoggingUtil.info(LOG, "saveTimeSpentViewing", 
                "Saved time spent feedback: user=%s, paper=%s, time=%s seconds", 
                userId, discoveredPaperId, timeSpentSeconds);
            
            return saved;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveTimeSpentViewing", "Error saving time spent feedback", e);
            throw new RuntimeException("Failed to save time spent feedback: " + e.getMessage(), e);
        }
    }

    /**
     * Gets all feedback for a specific discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return List of feedback entries
     */
    @Transactional(readOnly = true)
    public List<DiscoveryFeedback> getFeedbackForPaper(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.findByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Gets all feedback by a specific user.
     * 
     * @param userId ID of the user
     * @return List of feedback entries
     */
    @Transactional(readOnly = true)
    public List<DiscoveryFeedback> getFeedbackByUser(UUID userId) {
        return discoveryFeedbackRepository.findByUserId(userId);
    }

    /**
     * Gets recent feedback for a user (last 30 days).
     * 
     * @param userId ID of the user
     * @return List of recent feedback entries
     */
    @Transactional(readOnly = true)
    public List<DiscoveryFeedback> getRecentFeedbackByUser(UUID userId) {
        return discoveryFeedbackRepository.findRecentFeedbackByUserId(userId);
    }

    /**
     * Gets average relevance rating for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Average relevance rating or null if no ratings
     */
    @Transactional(readOnly = true)
    public Double getAverageRelevanceRating(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.getAverageRelevanceRatingByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Gets average quality rating for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Average quality rating or null if no ratings
     */
    @Transactional(readOnly = true)
    public Double getAverageQualityRating(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.getAverageQualityRatingByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Gets average usefulness rating for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Average usefulness rating or null if no ratings
     */
    @Transactional(readOnly = true)
    public Double getAverageUsefulnessRating(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.getAverageUsefulnessRatingByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Counts total feedback entries for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Count of feedback entries
     */
    @Transactional(readOnly = true)
    public long getFeedbackCount(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.countByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Counts positive interactions for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return Count of positive interactions
     */
    @Transactional(readOnly = true)
    public long getPositiveInteractionCount(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.countPositiveInteractionsByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Checks if user has already provided explicit rating for a paper.
     * 
     * @param userId ID of the user
     * @param discoveredPaperId ID of the discovered paper
     * @return true if explicit rating exists
     */
    @Transactional(readOnly = true)
    public boolean hasUserProvidedExplicitRating(UUID userId, UUID discoveredPaperId) {
        return discoveryFeedbackRepository.existsByUserIdAndDiscoveredPaperIdAndFeedbackType(
            userId, discoveredPaperId, "EXPLICIT_RATING");
    }

    /**
     * Gets feedback statistics for a source paper.
     * 
     * @param sourcePaperId ID of the source paper
     * @return List of feedback entries
     */
    @Transactional(readOnly = true)
    public List<DiscoveryFeedback> getFeedbackStatsForSourcePaper(UUID sourcePaperId) {
        return discoveryFeedbackRepository.findFeedbackStatsBySourcePaperId(sourcePaperId);
    }

    /**
     * Gets explicit ratings only for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return List of feedback with explicit ratings
     */
    @Transactional(readOnly = true)
    public List<DiscoveryFeedback> getExplicitRatings(UUID discoveredPaperId) {
        return discoveryFeedbackRepository.findExplicitRatingsByDiscoveredPaperId(discoveredPaperId);
    }

    /**
     * Calculates feedback statistics for a discovered paper.
     * 
     * @param discoveredPaperId ID of the discovered paper
     * @return FeedbackStats object with aggregated data
     */
    @Transactional(readOnly = true)
    public FeedbackStats calculateFeedbackStats(UUID discoveredPaperId) {
        FeedbackStats stats = new FeedbackStats();
        
        stats.totalFeedbackCount = getFeedbackCount(discoveredPaperId);
        stats.positiveInteractionCount = getPositiveInteractionCount(discoveredPaperId);
        stats.averageRelevanceRating = getAverageRelevanceRating(discoveredPaperId);
        stats.averageQualityRating = getAverageQualityRating(discoveredPaperId);
        stats.averageUsefulnessRating = getAverageUsefulnessRating(discoveredPaperId);
        
        // Calculate overall average rating
        if (stats.averageRelevanceRating != null || stats.averageQualityRating != null || stats.averageUsefulnessRating != null) {
            double sum = 0;
            int count = 0;
            
            if (stats.averageRelevanceRating != null) {
                sum += stats.averageRelevanceRating;
                count++;
            }
            if (stats.averageQualityRating != null) {
                sum += stats.averageQualityRating;
                count++;
            }
            if (stats.averageUsefulnessRating != null) {
                sum += stats.averageUsefulnessRating;
                count++;
            }
            
            stats.overallAverageRating = count > 0 ? sum / count : null;
        }
        
        return stats;
    }

    /**
     * Data class for feedback statistics.
     */
    public static class FeedbackStats {
        public long totalFeedbackCount;
        public long positiveInteractionCount;
        public Double averageRelevanceRating;
        public Double averageQualityRating;
        public Double averageUsefulnessRating;
        public Double overallAverageRating;
        
        public boolean hasRatings() {
            return averageRelevanceRating != null || averageQualityRating != null || averageUsefulnessRating != null;
        }
        
        public boolean hasPositiveInteractions() {
            return positiveInteractionCount > 0;
        }
    }
}

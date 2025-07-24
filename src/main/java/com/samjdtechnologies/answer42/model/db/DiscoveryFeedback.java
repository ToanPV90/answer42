package com.samjdtechnologies.answer42.model.daos;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * Entity representing user feedback on discovered papers.
 * Tracks user interactions, ratings, and qualitative feedback to improve discovery algorithms.
 */
@Entity
@Table(name = "discovery_feedback", schema = "answer42")
@NoArgsConstructor
public class DiscoveryFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "discovery_result_id")
    private UUID discoveryResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovery_result_id", insertable = false, updatable = false)
    private DiscoveryResult discoveryResult;

    @Column(name = "discovered_paper_id")
    private UUID discoveredPaperId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovered_paper_id", insertable = false, updatable = false)
    private DiscoveredPaper discoveredPaper;

    @Column(name = "source_paper_id")
    private UUID sourcePaperId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_paper_id", insertable = false, updatable = false)
    private Paper sourcePaper;

    @Column(name = "feedback_type", nullable = false)
    private String feedbackType;

    @Column(name = "relevance_rating")
    private Integer relevanceRating;

    @Column(name = "quality_rating")
    private Integer qualityRating;

    @Column(name = "usefulness_rating")
    private Integer usefulnessRating;

    @Column(name = "feedback_text")
    private String feedbackText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_context", columnDefinition = "jsonb")
    private JsonNode feedbackContext;

    @Column(name = "clicked_paper")
    private Boolean clickedPaper = false;

    @Column(name = "downloaded_paper")
    private Boolean downloadedPaper = false;

    @Column(name = "bookmarked_paper")
    private Boolean bookmarkedPaper = false;

    @Column(name = "shared_paper")
    private Boolean sharedPaper = false;

    @Column(name = "time_spent_viewing_seconds")
    private Integer timeSpentViewingSeconds = 0;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "session_id")
    private String sessionId;

    @UpdateTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    // Constructors
    public DiscoveryFeedback(UUID userId, UUID discoveredPaperId, UUID sourcePaperId, String feedbackType) {
        this.userId = userId;
        this.discoveredPaperId = discoveredPaperId;
        this.sourcePaperId = sourcePaperId;
        this.feedbackType = feedbackType;
    }

    // Getters and Setters for basic fields
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDiscoveryResultId() {
        return discoveryResultId;
    }

    public void setDiscoveryResultId(UUID discoveryResultId) {
        this.discoveryResultId = discoveryResultId;
    }

    public UUID getDiscoveredPaperId() {
        return discoveredPaperId;
    }

    public void setDiscoveredPaperId(UUID discoveredPaperId) {
        this.discoveredPaperId = discoveredPaperId;
    }

    public UUID getSourcePaperId() {
        return sourcePaperId;
    }

    public void setSourcePaperId(UUID sourcePaperId) {
        this.sourcePaperId = sourcePaperId;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public Integer getRelevanceRating() {
        return relevanceRating;
    }

    public void setRelevanceRating(Integer relevanceRating) {
        this.relevanceRating = relevanceRating;
    }

    public Integer getQualityRating() {
        return qualityRating;
    }

    public void setQualityRating(Integer qualityRating) {
        this.qualityRating = qualityRating;
    }

    public Integer getUsefulnessRating() {
        return usefulnessRating;
    }

    public void setUsefulnessRating(Integer usefulnessRating) {
        this.usefulnessRating = usefulnessRating;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public void setFeedbackText(String feedbackText) {
        this.feedbackText = feedbackText;
    }

    public JsonNode getFeedbackContext() {
        return feedbackContext;
    }

    public void setFeedbackContext(JsonNode feedbackContext) {
        this.feedbackContext = feedbackContext;
    }

    public Boolean getClickedPaper() {
        return clickedPaper;
    }

    public void setClickedPaper(Boolean clickedPaper) {
        this.clickedPaper = clickedPaper;
    }

    public Boolean getDownloadedPaper() {
        return downloadedPaper;
    }

    public void setDownloadedPaper(Boolean downloadedPaper) {
        this.downloadedPaper = downloadedPaper;
    }

    public Boolean getBookmarkedPaper() {
        return bookmarkedPaper;
    }

    public void setBookmarkedPaper(Boolean bookmarkedPaper) {
        this.bookmarkedPaper = bookmarkedPaper;
    }

    public Boolean getSharedPaper() {
        return sharedPaper;
    }

    public void setSharedPaper(Boolean sharedPaper) {
        this.sharedPaper = sharedPaper;
    }

    public Integer getTimeSpentViewingSeconds() {
        return timeSpentViewingSeconds;
    }

    public void setTimeSpentViewingSeconds(Integer timeSpentViewingSeconds) {
        this.timeSpentViewingSeconds = timeSpentViewingSeconds;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Getters and Setters for lazy relationships (careful with these)
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public DiscoveryResult getDiscoveryResult() {
        return discoveryResult;
    }

    public void setDiscoveryResult(DiscoveryResult discoveryResult) {
        this.discoveryResult = discoveryResult;
    }

    public DiscoveredPaper getDiscoveredPaper() {
        return discoveredPaper;
    }

    public void setDiscoveredPaper(DiscoveredPaper discoveredPaper) {
        this.discoveredPaper = discoveredPaper;
    }

    public Paper getSourcePaper() {
        return sourcePaper;
    }

    public void setSourcePaper(Paper sourcePaper) {
        this.sourcePaper = sourcePaper;
    }

    // Factory methods
    /**
     * Creates a new feedback instance for explicit user rating.
     * 
     * @param userId User providing feedback
     * @param discoveredPaperId Paper being rated
     * @param sourcePaperId Source paper that led to discovery
     * @param relevanceRating Relevance rating (1-5)
     * @param qualityRating Quality rating (1-5)
     * @param usefulnessRating Usefulness rating (1-5)
     * @param feedbackText Optional text feedback
     * @return DiscoveryFeedback instance
     */
    public static DiscoveryFeedback createExplicitRating(UUID userId, UUID discoveredPaperId, UUID sourcePaperId,
            Integer relevanceRating, Integer qualityRating, Integer usefulnessRating, String feedbackText) {
        DiscoveryFeedback feedback = new DiscoveryFeedback(userId, discoveredPaperId, sourcePaperId, "EXPLICIT_RATING");
        feedback.setRelevanceRating(relevanceRating);
        feedback.setQualityRating(qualityRating);
        feedback.setUsefulnessRating(usefulnessRating);
        feedback.setFeedbackText(feedbackText);
        return feedback;
    }

    /**
     * Creates a new feedback instance for implicit user behavior.
     * 
     * @param userId User performing action
     * @param discoveredPaperId Paper being interacted with
     * @param sourcePaperId Source paper that led to discovery
     * @param actionType Type of action (clicked, bookmarked, etc.)
     * @return DiscoveryFeedback instance
     */
    public static DiscoveryFeedback createImplicitFeedback(UUID userId, UUID discoveredPaperId, UUID sourcePaperId,
            String actionType) {
        DiscoveryFeedback feedback = new DiscoveryFeedback(userId, discoveredPaperId, sourcePaperId, "IMPLICIT_BEHAVIOR");
        
        // Set appropriate boolean flags based on action type
        switch (actionType.toLowerCase()) {
            case "clicked":
                feedback.setClickedPaper(true);
                break;
            case "downloaded":
                feedback.setDownloadedPaper(true);
                break;
            case "bookmarked":
                feedback.setBookmarkedPaper(true);
                break;
            case "shared":
                feedback.setSharedPaper(true);
                break;
        }
        
        return feedback;
    }

    /**
     * Creates feedback for time spent viewing a paper.
     * 
     * @param userId User viewing paper
     * @param discoveredPaperId Paper being viewed
     * @param sourcePaperId Source paper that led to discovery
     * @param timeSpentSeconds Time spent viewing in seconds
     * @return DiscoveryFeedback instance
     */
    public static DiscoveryFeedback createTimeSpentFeedback(UUID userId, UUID discoveredPaperId, UUID sourcePaperId,
            Integer timeSpentSeconds) {
        DiscoveryFeedback feedback = new DiscoveryFeedback(userId, discoveredPaperId, sourcePaperId, "TIME_SPENT");
        feedback.setTimeSpentViewingSeconds(timeSpentSeconds);
        return feedback;
    }

    /**
     * Sets session and request metadata.
     * 
     * @param sessionId User session ID
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     */
    public void setSessionMetadata(String sessionId, String ipAddress, String userAgent) {
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    /**
     * Checks if this feedback has explicit ratings.
     * 
     * @return true if any rating is provided
     */
    public boolean hasExplicitRatings() {
        return relevanceRating != null || qualityRating != null || usefulnessRating != null;
    }

    /**
     * Checks if this feedback represents positive interaction.
     * 
     * @return true if user performed positive actions
     */
    public boolean isPositiveInteraction() {
        return Boolean.TRUE.equals(clickedPaper) || 
               Boolean.TRUE.equals(downloadedPaper) || 
               Boolean.TRUE.equals(bookmarkedPaper) || 
               Boolean.TRUE.equals(sharedPaper) ||
               (timeSpentViewingSeconds != null && timeSpentViewingSeconds > 30);
    }

    /**
     * Gets average rating across all provided ratings.
     * 
     * @return average rating or null if no ratings provided
     */
    public Double getAverageRating() {
        int count = 0;
        int sum = 0;
        
        if (relevanceRating != null) {
            sum += relevanceRating;
            count++;
        }
        if (qualityRating != null) {
            sum += qualityRating;
            count++;
        }
        if (usefulnessRating != null) {
            sum += usefulnessRating;
            count++;
        }
        
        return count > 0 ? (double) sum / count : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveryFeedback)) return false;
        DiscoveryFeedback that = (DiscoveryFeedback) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DiscoveryFeedback{" +
                "id=" + id +
                ", userId=" + userId +
                ", discoveredPaperId=" + discoveredPaperId +
                ", sourcePaperId=" + sourcePaperId +
                ", feedbackType='" + feedbackType + '\'' +
                ", relevanceRating=" + relevanceRating +
                ", qualityRating=" + qualityRating +
                ", usefulnessRating=" + usefulnessRating +
                ", createdAt=" + createdAt +
                '}';
    }
}

package com.samjdtechnologies.answer42.model.daos;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
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
 * Entity representing a discovered related paper in the system.
 * Maps to the 'discovered_papers' table in the answer42 schema.
 */
@Entity
@Table(name = "discovered_papers", schema = "answer42")
@NoArgsConstructor
public class DiscoveredPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_paper_id")
    private Paper sourcePaper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authors", columnDefinition = "jsonb")
    private List<String> authors;

    @Column(name = "journal")
    private String journal;

    @Column(name = "year")
    private Integer year;

    @Column(name = "doi")
    private String doi;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "paper_abstract", columnDefinition = "text")
    private String paperAbstract;

    @Column(name = "discovery_source", nullable = false)
    private String discoverySource; // CROSSREF, SEMANTIC_SCHOLAR, PERPLEXITY

    @Column(name = "relationship_type", nullable = false)
    private String relationshipType; // CITES, CITED_BY, SEMANTIC_SIMILARITY, etc.

    @Column(name = "relevance_score", nullable = false)
    private Double relevanceScore;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "citation_count")
    private Integer citationCount;

    @Column(name = "influential_citation_count")
    private Integer influentialCitationCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovery_metadata", columnDefinition = "jsonb")
    private JsonNode discoveryMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_specific_data", columnDefinition = "jsonb")
    private JsonNode sourceSpecificData;

    @Column(name = "discovery_session_id")
    private String discoverySessionId;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "verification_score")
    private Double verificationScore;

    @Column(name = "is_duplicate")
    private Boolean isDuplicate = false;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Column(name = "access_url")
    private String accessUrl;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "open_access")
    private Boolean openAccess;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics", columnDefinition = "jsonb")
    private List<String> topics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_of_study", columnDefinition = "jsonb")
    private List<String> fieldsOfStudy;

    @Column(name = "publication_date")
    private ZonedDateTime publicationDate;

    @Column(name = "venue")
    private String venue;

    @Column(name = "venue_type")
    private String venueType; // journal, conference, preprint, etc.

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "user_rating")
    private Integer userRating; // 1-5 star rating by user

    @Column(name = "user_notes", columnDefinition = "text")
    private String userNotes;

    @Column(name = "last_accessed_at")
    private ZonedDateTime lastAccessedAt;

    @CreationTimestamp
    @Column(name = "discovered_at")
    private ZonedDateTime discoveredAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /**
     * Constructor with required fields for creating a new discovered paper.
     * 
     * @param sourcePaper The paper that triggered this discovery
     * @param user The user who owns this discovery
     * @param title The title of the discovered paper
     * @param discoverySource The source of the discovery (CROSSREF, SEMANTIC_SCHOLAR, etc.)
     * @param relationshipType The type of relationship to the source paper
     * @param relevanceScore The relevance score (0.0 to 1.0)
     */
    public DiscoveredPaper(Paper sourcePaper, User user, String title, 
                          String discoverySource, String relationshipType, 
                          Double relevanceScore) {
        this();
        this.sourcePaper = sourcePaper;
        this.user = user;
        this.title = title;
        this.discoverySource = discoverySource;
        this.relationshipType = relationshipType;
        this.relevanceScore = relevanceScore;
        this.isVerified = false;
        this.isDuplicate = false;
        this.isArchived = false;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Paper getSourcePaper() {
        return sourcePaper;
    }

    public void setSourcePaper(Paper sourcePaper) {
        this.sourcePaper = sourcePaper;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public String getJournal() {
        return journal;
    }

    public void setJournal(String journal) {
        this.journal = journal;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getPaperAbstract() {
        return paperAbstract;
    }

    public void setPaperAbstract(String paperAbstract) {
        this.paperAbstract = paperAbstract;
    }

    public String getDiscoverySource() {
        return discoverySource;
    }

    public void setDiscoverySource(String discoverySource) {
        this.discoverySource = discoverySource;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Integer getCitationCount() {
        return citationCount;
    }

    public void setCitationCount(Integer citationCount) {
        this.citationCount = citationCount;
    }

    public Integer getInfluentialCitationCount() {
        return influentialCitationCount;
    }

    public void setInfluentialCitationCount(Integer influentialCitationCount) {
        this.influentialCitationCount = influentialCitationCount;
    }

    public JsonNode getDiscoveryMetadata() {
        return discoveryMetadata;
    }

    public void setDiscoveryMetadata(JsonNode discoveryMetadata) {
        this.discoveryMetadata = discoveryMetadata;
    }

    public JsonNode getSourceSpecificData() {
        return sourceSpecificData;
    }

    public void setSourceSpecificData(JsonNode sourceSpecificData) {
        this.sourceSpecificData = sourceSpecificData;
    }

    public String getDiscoverySessionId() {
        return discoverySessionId;
    }

    public void setDiscoverySessionId(String discoverySessionId) {
        this.discoverySessionId = discoverySessionId;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public Double getVerificationScore() {
        return verificationScore;
    }

    public void setVerificationScore(Double verificationScore) {
        this.verificationScore = verificationScore;
    }

    public Boolean getIsDuplicate() {
        return isDuplicate;
    }

    public void setIsDuplicate(Boolean isDuplicate) {
        this.isDuplicate = isDuplicate;
    }

    public UUID getDuplicateOfId() {
        return duplicateOfId;
    }

    public void setDuplicateOfId(UUID duplicateOfId) {
        this.duplicateOfId = duplicateOfId;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl) {
        this.accessUrl = accessUrl;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public Boolean getOpenAccess() {
        return openAccess;
    }

    public void setOpenAccess(Boolean openAccess) {
        this.openAccess = openAccess;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public List<String> getFieldsOfStudy() {
        return fieldsOfStudy;
    }

    public void setFieldsOfStudy(List<String> fieldsOfStudy) {
        this.fieldsOfStudy = fieldsOfStudy;
    }

    public ZonedDateTime getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(ZonedDateTime publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getVenueType() {
        return venueType;
    }

    public void setVenueType(String venueType) {
        this.venueType = venueType;
    }

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean isArchived) {
        this.isArchived = isArchived;
    }

    public Integer getUserRating() {
        return userRating;
    }

    public void setUserRating(Integer userRating) {
        this.userRating = userRating;
    }

    public String getUserNotes() {
        return userNotes;
    }

    public void setUserNotes(String userNotes) {
        this.userNotes = userNotes;
    }

    public ZonedDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(ZonedDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public ZonedDateTime getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(ZonedDateTime discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper Methods

    /**
     * Marks this discovered paper as a duplicate of another paper.
     * 
     * @param originalPaperId The ID of the original paper this is a duplicate of
     */
    public void markAsDuplicate(UUID originalPaperId) {
        this.isDuplicate = true;
        this.duplicateOfId = originalPaperId;
    }

    /**
     * Updates the last accessed timestamp to now.
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = ZonedDateTime.now();
    }

    /**
     * Checks if this discovered paper has a high relevance score.
     * 
     * @return true if relevance score is >= 0.7, false otherwise
     */
    public boolean isHighlyRelevant() {
        return relevanceScore != null && relevanceScore >= 0.7;
    }

    /**
     * Checks if this discovered paper is from a verified source.
     * 
     * @return true if the paper is verified, false otherwise
     */
    public boolean isFromVerifiedSource() {
        return Boolean.TRUE.equals(isVerified);
    }

    /**
     * Gets a display name for the discovery source.
     * 
     * @return A human-readable name for the discovery source
     */
    public String getDiscoverySourceDisplayName() {
        if (discoverySource == null) {
            return "Unknown";
        }
        switch (discoverySource.toUpperCase()) {
            case "CROSSREF":
                return "Crossref";
            case "SEMANTIC_SCHOLAR":
                return "Semantic Scholar";
            case "PERPLEXITY":
                return "Perplexity";
            default:
                return discoverySource;
        }
    }

    /**
     * Gets a display name for the relationship type.
     * 
     * @return A human-readable name for the relationship type
     */
    public String getRelationshipTypeDisplayName() {
        if (relationshipType == null) {
            return "Unknown";
        }
        switch (relationshipType.toUpperCase()) {
            case "CITES":
                return "Cites this paper";
            case "CITED_BY":
                return "Cited by this paper";
            case "SEMANTIC_SIMILARITY":
                return "Similar content";
            case "AUTHOR_NETWORK":
                return "Same author(s)";
            case "CO_CITATION":
                return "Co-cited";
            case "BIBLIOGRAPHIC_COUPLING":
                return "Bibliographic coupling";
            case "TRENDING":
                return "Currently trending";
            default:
                return relationshipType;
        }
    }

    /**
     * Gets the full author list as a formatted string.
     * 
     * @return A comma-separated string of authors, or "Unknown" if no authors
     */
    public String getAuthorsAsString() {
        if (authors == null || authors.isEmpty()) {
            return "Unknown";
        }
        if (authors.size() == 1) {
            return authors.get(0);
        }
        if (authors.size() <= 3) {
            return String.join(", ", authors);
        }
        return String.join(", ", authors.subList(0, 2)) + ", et al.";
    }

    /**
     * Checks if this paper has open access availability.
     * 
     * @return true if open access is available, false otherwise
     */
    public boolean hasOpenAccess() {
        return Boolean.TRUE.equals(openAccess);
    }

    /**
     * Gets a quality indicator based on citation count and verification.
     * 
     * @return A quality indicator: HIGH, MEDIUM, LOW, or UNKNOWN
     */
    public String getQualityIndicator() {
        if (citationCount == null) {
            return "UNKNOWN";
        }
        
        boolean highCitations = citationCount >= 50;
        boolean mediumCitations = citationCount >= 10;
        boolean verified = Boolean.TRUE.equals(isVerified);
        
        if (highCitations && verified) {
            return "HIGH";
        } else if (mediumCitations || verified) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    // Static factory methods

    /**
     * Static factory method for creating a discovered paper from Crossref data.
     * 
     * @param sourcePaper The source paper
     * @param user The user
     * @param title The discovered paper title
     * @param relationshipType The relationship type
     * @param relevanceScore The relevance score
     * @return A new DiscoveredPaper instance configured for Crossref
     */
    public static DiscoveredPaper fromCrossref(Paper sourcePaper, User user, String title,
                                              String relationshipType, Double relevanceScore) {
        return new DiscoveredPaper(sourcePaper, user, title, "CROSSREF", 
                                  relationshipType, relevanceScore);
    }

    /**
     * Static factory method for creating a discovered paper from Semantic Scholar data.
     * 
     * @param sourcePaper The source paper
     * @param user The user
     * @param title The discovered paper title
     * @param relationshipType The relationship type
     * @param relevanceScore The relevance score
     * @return A new DiscoveredPaper instance configured for Semantic Scholar
     */
    public static DiscoveredPaper fromSemanticScholar(Paper sourcePaper, User user, String title,
                                                     String relationshipType, Double relevanceScore) {
        return new DiscoveredPaper(sourcePaper, user, title, "SEMANTIC_SCHOLAR", 
                                  relationshipType, relevanceScore);
    }

    /**
     * Static factory method for creating a discovered paper from Perplexity data.
     * 
     * @param sourcePaper The source paper
     * @param user The user
     * @param title The discovered paper title
     * @param relationshipType The relationship type
     * @param relevanceScore The relevance score
     * @return A new DiscoveredPaper instance configured for Perplexity
     */
    public static DiscoveredPaper fromPerplexity(Paper sourcePaper, User user, String title,
                                                String relationshipType, Double relevanceScore) {
        return new DiscoveredPaper(sourcePaper, user, title, "PERPLEXITY", 
                                  relationshipType, relevanceScore);
    }

    @Override
    public String toString() {
        return "DiscoveredPaper{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", authors=" + authors +
                ", journal='" + journal + '\'' +
                ", year=" + year +
                ", discoverySource='" + discoverySource + '\'' +
                ", relationshipType='" + relationshipType + '\'' +
                ", relevanceScore=" + relevanceScore +
                ", discoveredAt=" + discoveredAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredPaper that = (DiscoveredPaper) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

package com.samjdtechnologies.answer42.model.db;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata Verification entity for tracking verification of paper metadata against external sources.
 * Maps to answer42.metadata_verifications table in Supabase.
 * 
 * This entity stores results from verifying paper metadata against authoritative sources like:
 * - Crossref API for DOI validation and bibliographic data
 * - Semantic Scholar API for academic paper metadata
 * - arXiv API for preprint metadata verification
 * - PubMed API for medical literature verification
 */
@Entity
@Table(name = "metadata_verifications", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @ManyToOne
    @JoinColumn(name = "paper_id", insertable = false, updatable = false)
    private Paper paper;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "confidence")
    private Double confidence = 0.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "matched_by")
    private String matchedBy;

    @Column(name = "identifier_used")
    private String identifierUsed;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors for common use cases
    public MetadataVerification(UUID paperId, String source) {
        this.paperId = paperId;
        this.source = source;
        this.verifiedAt = Instant.now();
    }

    public MetadataVerification(UUID paperId, String source, Double confidence, String matchedBy) {
        this(paperId, source);
        this.confidence = confidence;
        this.matchedBy = matchedBy;
    }

    public MetadataVerification(UUID paperId, String source, Double confidence, String matchedBy, String identifierUsed) {
        this(paperId, source, confidence, matchedBy);
        this.identifierUsed = identifierUsed;
    }

    // Lifecycle methods
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.verifiedAt == null) {
            this.verifiedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Utility methods
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }

    public boolean isMediumConfidence() {
        return confidence != null && confidence >= 0.6 && confidence < 0.8;
    }

    public boolean isLowConfidence() {
        return confidence != null && confidence < 0.6;
    }

    public void updateVerification(Double newConfidence, String newMatchedBy, String newIdentifierUsed) {
        this.confidence = newConfidence;
        this.matchedBy = newMatchedBy;
        this.identifierUsed = newIdentifierUsed;
        this.verifiedAt = Instant.now();
    }

    public void updateMetadata(JsonNode newMetadata) {
        this.metadata = newMetadata;
        this.verifiedAt = Instant.now();
    }

    public boolean hasMetadata() {
        return metadata != null && !metadata.isNull() && metadata.size() > 0;
    }

    public boolean hasIdentifier() {
        return identifierUsed != null && !identifierUsed.trim().isEmpty();
    }

    /**
     * Get the confidence level as a descriptive string
     * @return String representation of confidence level
     */
    public String getConfidenceLevel() {
        if (confidence == null) {
            return "Unknown";
        }
        if (confidence >= 0.9) {
            return "Very High";
        }
        if (confidence >= 0.8) {
            return "High";
        }
        if (confidence >= 0.6) {
            return "Medium";
        }
        if (confidence >= 0.4) {
            return "Low";
        }
        return "Very Low";
    }

    /**
     * Check if this verification is recent (within last 24 hours)
     * @return true if verification is recent
     */
    public boolean isRecentVerification() {
        if (verifiedAt == null) {
            return false;
        }
        Instant twentyFourHoursAgo = Instant.now().minusSeconds(24 * 60 * 60);
        return verifiedAt.isAfter(twentyFourHoursAgo);
    }

    /**
     * Get a summary string for this verification
     * @return Summary of the verification
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Source: ").append(source);
        summary.append(", Confidence: ").append(getConfidenceLevel());
        if (matchedBy != null) {
            summary.append(", Matched by: ").append(matchedBy);
        }
        if (identifierUsed != null) {
            summary.append(", ID: ").append(identifierUsed);
        }
        return summary.toString();
    }
}

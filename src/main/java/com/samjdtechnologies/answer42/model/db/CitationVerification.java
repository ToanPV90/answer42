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
 * Citation Verification entity for tracking verification of citations against external sources.
 * Maps to answer42.citation_verifications table in Supabase.
 * 
 * This entity stores results from verifying citations against authoritative sources like:
 * - Crossref API for DOI validation and metadata
 * - Semantic Scholar API for academic paper verification  
 * - arXiv API for preprint verification
 */
@Entity
@Table(name = "citation_verifications", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitationVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "citation_id", nullable = false)
    private UUID citationId;

    @ManyToOne
    @JoinColumn(name = "citation_id", insertable = false, updatable = false)
    private Citation citation;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @Column(name = "doi")
    private String doi;

    @Column(name = "verified")
    private Boolean verified = false;

    @Column(name = "verification_source")
    private String verificationSource;

    @Column(name = "confidence")
    private Double confidence = 0.0;

    @Column(name = "verification_date")
    private Instant verificationDate;

    @Column(name = "semantic_scholar_id")
    private String semanticScholarId;

    @Column(name = "arxiv_id")
    private String arxivId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "merged_metadata", columnDefinition = "jsonb")
    private JsonNode mergedMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors for common use cases
    public CitationVerification(UUID citationId, UUID paperId) {
        this.citationId = citationId;
        this.paperId = paperId;
        this.verificationDate = Instant.now();
    }

    public CitationVerification(UUID citationId, UUID paperId, String verificationSource, Double confidence) {
        this(citationId, paperId);
        this.verificationSource = verificationSource;
        this.confidence = confidence;
    }

    // Lifecycle methods
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.verificationDate == null) {
            this.verificationDate = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Utility methods
    public boolean isVerified() {
        return Boolean.TRUE.equals(verified);
    }

    public void markAsVerified(String source, Double confidenceScore) {
        this.verified = true;
        this.verificationSource = source;
        this.confidence = confidenceScore;
        this.verificationDate = Instant.now();
    }

    public void markAsUnverified(String source, String reason) {
        this.verified = false;
        this.verificationSource = source;
        this.confidence = 0.0;
        this.verificationDate = Instant.now();
    }

    public boolean hasExternalId() {
        return semanticScholarId != null || arxivId != null || doi != null;
    }

    public String getPrimaryExternalId() {
        if (doi != null && !doi.trim().isEmpty()) {
            return "DOI:" + doi;
        }
        if (semanticScholarId != null && !semanticScholarId.trim().isEmpty()) {
            return "S2:" + semanticScholarId;
        }
        if (arxivId != null && !arxivId.trim().isEmpty()) {
            return "arXiv:" + arxivId;
        }
        return null;
    }
}

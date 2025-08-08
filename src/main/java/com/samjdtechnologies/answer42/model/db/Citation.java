package com.samjdtechnologies.answer42.model.db;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Citation entity representing extracted and structured citations from academic papers.
 * Maps to the answer42.citations table in Supabase.
 */
@Entity
@Table(name = "citations", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "paper_id", nullable = false)
    private UUID paperId;
    
    /**
     * Structured citation data as JSONB containing:
     * - authors: List of author names
     * - title: Publication title
     * - publicationVenue: Journal/conference name
     * - year: Publication year
     * - volume, issue, pages: Publication details
     * - doi: Digital Object Identifier
     * - publicationType: Type of publication
     * - confidence: AI parsing confidence score
     * - formattedCitations: Map of citation style to formatted text
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citation_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode citationData;
    
    /**
     * Original raw citation text as found in the document.
     */
    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Constructor for creating a new citation with paper ID and data.
     */
    public Citation(UUID paperId, JsonNode citationData, String rawText) {
        this.paperId = paperId;
        this.citationData = citationData;
        this.rawText = rawText;
    }
    
    /**
     * Automatically set timestamps on creation and updates.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if this citation has structured data (not just raw text).
     */
    public boolean isStructured() {
        return citationData != null && citationData.isObject() && 
               citationData.has("title") && citationData.has("authors");
    }
    
    /**
     * Get the citation title from structured data.
     */
    public String getTitle() {
        if (citationData != null && citationData.has("title")) {
            return citationData.get("title").asText();
        }
        return null;
    }
    
    /**
     * Get the first author from structured data.
     */
    public String getFirstAuthor() {
        if (citationData != null && citationData.has("authors") && 
            citationData.get("authors").isArray() && citationData.get("authors").size() > 0) {
            return citationData.get("authors").get(0).asText();
        }
        return null;
    }
    
    /**
     * Get the publication year from structured data.
     */
    public Integer getYear() {
        if (citationData != null && citationData.has("year")) {
            return citationData.get("year").asInt();
        }
        return null;
    }
    
    /**
     * Get the DOI from structured data.
     */
    public String getDoi() {
        if (citationData != null && citationData.has("doi")) {
            return citationData.get("doi").asText();
        }
        return null;
    }
    
    /**
     * Get the confidence score of AI parsing.
     */
    public Double getConfidence() {
        if (citationData != null && citationData.has("confidence")) {
            return citationData.get("confidence").asDouble();
        }
        return null;
    }
    
}

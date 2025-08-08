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
 * Entity representing relationships between papers and discovered papers.
 */
@Entity
@Table(name = "paper_relationships", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "source_paper_id", nullable = false)
    private UUID sourcePaperId;

    @ManyToOne
    @JoinColumn(name = "source_paper_id", insertable = false, updatable = false)
    private Paper sourcePaper;

    @Column(name = "discovered_paper_id", nullable = false)
    private UUID discoveredPaperId;

    @ManyToOne
    @JoinColumn(name = "discovered_paper_id", insertable = false, updatable = false)
    private DiscoveredPaper discoveredPaper;

    @Column(name = "relationship_type", nullable = false)
    private String relationshipType;

    @Column(name = "relationship_strength")
    private Double relationshipStrength;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "discovery_source", nullable = false)
    private String discoverySource;

    @Column(name = "discovery_method")
    private String discoveryMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovery_context", columnDefinition = "jsonb")
    private JsonNode discoveryContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private JsonNode evidence;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructor for creating new relationship
    public PaperRelationship(UUID sourcePaperId, UUID discoveredPaperId, String relationshipType, 
                            Double relationshipStrength, Double confidenceScore, String discoverySource) {
        this.sourcePaperId = sourcePaperId;
        this.discoveredPaperId = discoveredPaperId;
        this.relationshipType = relationshipType;
        this.relationshipStrength = relationshipStrength;
        this.confidenceScore = confidenceScore;
        this.discoverySource = discoverySource;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

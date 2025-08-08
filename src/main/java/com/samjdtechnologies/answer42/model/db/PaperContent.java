package com.samjdtechnologies.answer42.model.db;

import java.time.Instant;
import java.util.UUID;

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
 * Entity representing paper content storage.
 * Maps to the actual paper_content table in database.
 */
@Entity
@Table(name = "paper_content", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperContent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @ManyToOne
    @JoinColumn(name = "paper_id", insertable = false, updatable = false)
    private Paper paper;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructor for creating new content
    public PaperContent(UUID paperId, String content) {
        this.paperId = paperId;
        this.content = content;
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

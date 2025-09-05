package com.samjdtechnologies.answer42.model.db;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Junction entity representing paper-tag relationships.
 */
@Entity
@Table(name = "paper_tags", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperTag {

    @EmbeddedId
    private PaperTagId id;

    @ManyToOne
    @JoinColumn(name = "paper_id", insertable = false, updatable = false)
    private Paper paper;

    @ManyToOne
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private Tag tag;

    @Column(name = "created_at")
    private Instant createdAt;

    // Constructor for creating new paper-tag relationship
    public PaperTag(UUID paperId, UUID tagId) {
        this.id = new PaperTagId(paperId, tagId);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Composite primary key for PaperTag entity.
     */
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaperTagId implements Serializable {

        @Column(name = "paper_id")
        private UUID paperId;

        @Column(name = "tag_id")
        private UUID tagId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PaperTagId that = (PaperTagId) o;
            return Objects.equals(paperId, that.paperId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paperId, tagId);
        }
    }
}

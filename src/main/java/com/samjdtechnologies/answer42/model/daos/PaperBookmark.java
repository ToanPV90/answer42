package com.samjdtechnologies.answer42.model.daos;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

/**
 * Entity representing a user's bookmark of a discovered paper.
 * Maps to the 'paper_bookmarks' table in the answer42 schema.
 */
@Entity
@Table(name = "paper_bookmarks", schema = "answer42", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "discovered_paper_id"})
})
@NoArgsConstructor
public class PaperBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovered_paper_id", nullable = false)
    private DiscoveredPaper discoveredPaper;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "tags", length = 500)
    private String tags;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // Constructors
    public PaperBookmark(User user, DiscoveredPaper discoveredPaper) {
        this.user = user;
        this.discoveredPaper = discoveredPaper;
    }

    public PaperBookmark(User user, DiscoveredPaper discoveredPaper, String notes) {
        this(user, discoveredPaper);
        this.notes = notes;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public DiscoveredPaper getDiscoveredPaper() {
        return discoveredPaper;
    }

    public void setDiscoveredPaper(DiscoveredPaper discoveredPaper) {
        this.discoveredPaper = discoveredPaper;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaperBookmark that = (PaperBookmark) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PaperBookmark{" +
                "id=" + id +
                ", user=" + (user != null ? user.getUsername() : "null") +
                ", discoveredPaper=" + (discoveredPaper != null ? discoveredPaper.getTitle() : "null") +
                ", createdAt=" + createdAt +
                '}';
    }
}

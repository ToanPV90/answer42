package com.samjdtechnologies.answer42.model.db;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing tags.
 * Maps to the actual tags table in database.
 */
@Entity
@Table(name = "tags", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "color", columnDefinition = "TEXT")
    private String color;

    @Column(name = "created_at")
    private Instant createdAt;

    // Constructor for creating new tag
    public Tag(UUID userId, String name, String color) {
        this.userId = userId;
        this.name = name;
        this.color = color;
    }

    // Constructor for system-generated tags (no user)
    public Tag(String name, String color) {
        this.name = name;
        this.color = color;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (color == null) {
            color = "#6B7280"; // Default color
        }
    }
}

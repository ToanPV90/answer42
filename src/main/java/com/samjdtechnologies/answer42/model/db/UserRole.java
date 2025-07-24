package com.samjdtechnologies.answer42.model.db;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a user role assignment.
 * Maps to the 'user_roles' table in the answer42 schema.
 */
@Entity
@Table(name = "user_roles", schema = "answer42")
@Data
@NoArgsConstructor
public class UserRole {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "role")
    private String role;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    
    /**
     * Constructor for creating a new user role assignment.
     */
    public UserRole(UUID userId, String role) {
        this.userId = userId;
        this.role = role;
    }
}

package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * UserPreferences stores user-specific preference settings.
 */
@Entity
@Table(name = "user_settings", schema = "answer42")
public class UserPreferences {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    @Column(name = "academic_field")
    private String academicField;
    
    @Column(name = "study_material_generation_enabled", nullable = false)
    private boolean studyMaterialGenerationEnabled = false;
    
    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;
    
    @Column(name = "system_notifications_enabled", nullable = false)
    private boolean systemNotificationsEnabled = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    /**
     * Default constructor for UserPreferences.
     */
    public UserPreferences() {
    }
    
    /**
     * Creates user preferences for a specific user.
     * 
     * @param userId the UUID of the user these preferences belong to
     */
    public UserPreferences(UUID userId) {
        this.userId = userId;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getAcademicField() {
        return academicField;
    }
    
    public void setAcademicField(String academicField) {
        this.academicField = academicField;
    }
    
    public boolean isStudyMaterialGenerationEnabled() {
        return studyMaterialGenerationEnabled;
    }
    
    public void setStudyMaterialGenerationEnabled(boolean studyMaterialGenerationEnabled) {
        this.studyMaterialGenerationEnabled = studyMaterialGenerationEnabled;
    }
    
    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }
    
    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }
    
    public boolean isSystemNotificationsEnabled() {
        return systemNotificationsEnabled;
    }
    
    public void setSystemNotificationsEnabled(boolean systemNotificationsEnabled) {
        this.systemNotificationsEnabled = systemNotificationsEnabled;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Returns a string representation of the user preferences.
     * 
     * @return a string containing the user preferences details
     */
    @Override
    public String toString() {
        return "UserPreferences{" +
                "id=" + id +
                ", userId=" + userId +
                ", academicField='" + academicField + '\'' +
                ", studyMaterialGenerationEnabled=" + studyMaterialGenerationEnabled +
                ", emailNotificationsEnabled=" + emailNotificationsEnabled +
                ", systemNotificationsEnabled=" + systemNotificationsEnabled +
                '}';
    }
}

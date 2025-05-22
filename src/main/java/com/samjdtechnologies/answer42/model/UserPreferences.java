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
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserPreferences stores user-specific preference settings.
 */
@Entity
@Table(name = "user_settings", schema = "answer42")
@Data
@NoArgsConstructor
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
    
    @Column(name = "openai_api_key")
    private String openaiApiKey;
    
    @Column(name = "perplexity_api_key")
    private String perplexityApiKey;
    
    @Column(name = "anthropic_api_key")
    private String anthropicApiKey;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    
    /**
     * Creates user preferences for a specific user.
     * 
     * @param userId the UUID of the user these preferences belong to
     */
    public UserPreferences(UUID userId) {
        this.userId = userId;
    }
}

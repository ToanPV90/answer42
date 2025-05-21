package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity class representing a chat session.
 * Follows the normalized message storage strategy.
 */
@Entity
@Table(name = "chat_sessions", schema = "answer42")
public class ChatSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    private User user;
    
    @Column(nullable = false)
    private String mode;
    
    @Column(nullable = false)
    private String provider;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
    
    @Column(nullable = true)
    private String title;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;
    
    /**
     * Default constructor initializing the created timestamp.
     */
    public ChatSession() {
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Convenience constructor for creating a new chat session.
     * 
     * @param user The user who owns this session
     * @param mode The chat mode (chat, cross_reference, research_explorer)
     * @param provider The AI provider for this session
     */
    public ChatSession(User user, String mode, String provider) {
        this();
        this.user = user;
        this.mode = mode;
        this.provider = provider;
    }
    
    /**
     * Updates the last message timestamp to now.
     */
    public void updateLastMessageTimestamp() {
        this.lastMessageAt = LocalDateTime.now();
    }

    // Getters and setters
    
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}

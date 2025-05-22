package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing a chat session.
 * Follows the normalized message storage strategy.
 */
@Entity
@Table(name = "chat_sessions", schema = "answer42")
@Data
@NoArgsConstructor
public class ChatSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "mode")
    private String mode;
    
    @Column(name = "provider")
    private String provider;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
    
    @Column(name = "title")
    private String title;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "jsonb")
    private Map<String, Object> context;
    
    
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

}

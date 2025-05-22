package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing a single message in a chat session.
 * Follows the normalized message storage strategy.
 */
@Entity
@Table(name = "chat_messages", schema = "answer42")
@Data
@NoArgsConstructor
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "session_id")
    private UUID sessionId;
    
    @Column(name = "role")
    private String role;
    
    @Column(name = "content", columnDefinition = "text")
    private String content;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", columnDefinition = "jsonb")
    private List<Map<String, Object>> citations;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "sequence_number")
    private Integer sequenceNumber = 0;
    
    @Column(name = "message_type")
    private String messageType = "message";
    
    @Column(name = "is_edited")
    private Boolean isEdited = false;
    
    @Column(name = "token_count")
    private Integer tokenCount;
    
    @UpdateTimestamp
    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;
    
    /**
     * Convenience constructor for creating a new message.
     * 
     * @param sessionId The ID of the chat session this message belongs to
     * @param role The role of the sender (user/assistant)
     * @param content The actual message content
     * @param sequenceNumber The sequence number of this message in the conversation
     */
    public ChatMessage(UUID sessionId, String role, String content, Integer sequenceNumber) {
        this();
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.sequenceNumber = sequenceNumber;
    }
    
    /**
     * Marks this message as edited.
     * 
     * @param newContent The new content for this message
     */
    public void edit(String newContent) {
        this.content = newContent;
        this.isEdited = true;
        this.lastEditedAt = LocalDateTime.now();
    }
}

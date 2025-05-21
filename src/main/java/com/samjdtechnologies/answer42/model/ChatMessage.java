package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity class representing a single message in a chat session.
 * Follows the normalized message storage strategy.
 */
@Entity
@Table(name = "chat_messages", schema = "answer42")
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    
    @Column(nullable = false)
    private String role;
    
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> citations;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;
    
    @Column(name = "message_type", nullable = false)
    private String messageType;
    
    @Column(name = "is_edited", nullable = false)
    private Boolean isEdited;
    
    @Column(name = "token_count")
    private Integer tokenCount;
    
    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;
    
    /**
     * Default constructor initializing the created timestamp and default values.
     */
    public ChatMessage() {
        this.createdAt = LocalDateTime.now();
        this.isEdited = false;
        this.messageType = "message";
        this.sequenceNumber = 0;
    }
    
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
    
    // Getters and setters
    
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Map<String, Object>> getCitations() {
        return citations;
    }

    public void setCitations(List<Map<String, Object>> citations) {
        this.citations = citations;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Boolean getIsEdited() {
        return isEdited;
    }

    public void setIsEdited(Boolean isEdited) {
        this.isEdited = isEdited;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public LocalDateTime getLastEditedAt() {
        return lastEditedAt;
    }

    public void setLastEditedAt(LocalDateTime lastEditedAt) {
        this.lastEditedAt = lastEditedAt;
    }
}

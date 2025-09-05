package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.ChatMessage;

/**
 * Repository interface for ChatMessage entities.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    
    /**
     * Find all messages for a specific chat session.
     * 
     * @param sessionId The ID of the session whose messages to find
     * @return List of chat messages belonging to the session
     */
    List<ChatMessage> findBySessionId(UUID sessionId);
    
    /**
     * Find all messages for a specific chat session, ordered by sequence number.
     * 
     * @param sessionId The ID of the session whose messages to find
     * @return List of chat messages belonging to the session, ordered by sequence number
     */
    List<ChatMessage> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);
    
    /**
     * Find the message with the highest sequence number in a session.
     * 
     * @param sessionId The ID of the session to examine
     * @return Optional containing the message with the highest sequence number, if any
     */
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.sessionId = :sessionId ORDER BY cm.sequenceNumber DESC")
    List<ChatMessage> findTopBySessionIdOrderBySequenceNumberDesc(@Param("sessionId") UUID sessionId);
    
    /**
     * Get the next sequence number for a chat session.
     * 
     * @param sessionId The ID of the session to examine
     * @return The next sequence number (highest current + 1, or 0 if no messages)
     */
    @Query("SELECT COALESCE(MAX(cm.sequenceNumber) + 1, 0) FROM ChatMessage cm WHERE cm.sessionId = :sessionId")
    Integer getNextSequenceNumber(@Param("sessionId") UUID sessionId);
    
    /**
     * Count the number of messages in a chat session.
     * 
     * @param sessionId The ID of the session whose messages to count
     * @return The number of messages in the session
     */
    long countBySessionId(UUID sessionId);
    
    /**
     * Find messages by their type.
     * 
     * @param sessionId The ID of the session whose messages to find
     * @param messageType The type of messages to find
     * @return List of messages matching the criteria
     */
    List<ChatMessage> findBySessionIdAndMessageType(UUID sessionId, String messageType);
    
    /**
     * Delete all messages belonging to a chat session.
     * 
     * @param sessionId The ID of the session whose messages to delete
     */
    void deleteBySessionId(UUID sessionId);
}

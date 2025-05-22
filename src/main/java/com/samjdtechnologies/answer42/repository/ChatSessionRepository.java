package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.daos.ChatSession;
import com.samjdtechnologies.answer42.model.daos.User;

/**
 * Repository interface for ChatSession entities.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    
    /**
     * Find all chat sessions for a specific user.
     * 
     * @param user The user whose sessions to find
     * @return List of chat sessions owned by the user
     */
    List<ChatSession> findByUser(User user);
    
    /**
     * Find all chat sessions for a specific user, ordered by last message time (newest first).
     * 
     * @param user The user whose sessions to find
     * @return List of chat sessions owned by the user, ordered by last message time
     */
    @Query("SELECT cs FROM ChatSession cs WHERE cs.user = :user ORDER BY cs.lastMessageAt DESC NULLS LAST")
    List<ChatSession> findByUserOrderByLastMessageAtDesc(@Param("user") User user);
    
    /**
     * Find all chat sessions for a specific user and mode.
     * 
     * @param user The user whose sessions to find
     * @param mode The chat mode to filter by
     * @return List of chat sessions matching the criteria
     */
    List<ChatSession> findByUserAndMode(User user, String mode);
    
    /**
     * Find all chat sessions for a specific user and provider.
     * 
     * @param user The user whose sessions to find
     * @param provider The AI provider to filter by
     * @return List of chat sessions matching the criteria
     */
    List<ChatSession> findByUserAndProvider(User user, String provider);
    
    /**
     * Count the number of chat sessions for a specific user.
     * 
     * @param user The user whose sessions to count
     * @return The number of chat sessions for the user
     */
    long countByUser(User user);
}

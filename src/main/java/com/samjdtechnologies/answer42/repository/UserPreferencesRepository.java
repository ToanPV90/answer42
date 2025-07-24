package com.samjdtechnologies.answer42.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.UserPreferences;

/**
 * Repository for managing UserPreferences entities.
 */
@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
    
    /**
     * Find user preferences by user ID.
     * 
     * @param userId The ID of the user
     * @return Optional containing the user preferences if found
     */
    Optional<UserPreferences> findByUserId(UUID userId);
    
    /**
     * Delete user preferences by user ID.
     * 
     * @param userId The ID of the user
     */
    void deleteByUserId(UUID userId);
}

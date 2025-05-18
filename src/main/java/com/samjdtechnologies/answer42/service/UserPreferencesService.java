package com.samjdtechnologies.answer42.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.UserPreferences;
import com.samjdtechnologies.answer42.repository.UserPreferencesRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing user preferences.
 */
@Service
public class UserPreferencesService {
    
    private static final Logger LOG = LoggerFactory.getLogger(UserPreferencesService.class);
    
    private final UserPreferencesRepository userPreferencesRepository;
    
    public UserPreferencesService(UserPreferencesRepository userPreferencesRepository) {
        this.userPreferencesRepository = userPreferencesRepository;
    }
    
    /**
     * Get user preferences by user ID, creating default preferences if none exist.
     * 
     * @param userId The ID of the user
     * @return The user preferences
     */
    @Transactional
    public UserPreferences getByUserId(UUID userId) {
        LoggingUtil.debug(LOG, "getByUserId", "Getting user preferences for user ID: %s", userId);
        
        return userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    LoggingUtil.info(LOG, "getByUserId", "User preferences not found, creating default for user ID: %s", userId);
                    return initializeUserPreferences(userId);
                });
    }
    
    /**
     * Save user preferences.
     * 
     * @param preferences The user preferences to save
     * @return The saved user preferences
     */
    @Transactional
    public UserPreferences save(UserPreferences preferences) {
        LoggingUtil.debug(LOG, "save", "Saving user preferences for user ID: %s", preferences.getUserId());
        return userPreferencesRepository.save(preferences);
    }
    
    /**
     * Update a user's academic field.
     * 
     * @param userId The ID of the user
     * @param academicField The academic field to set
     * @return The updated user preferences
     */
    @Transactional
    public UserPreferences updateAcademicField(UUID userId, String academicField) {
        LoggingUtil.debug(LOG, "updateAcademicField", "Updating academic field to '%s' for user ID: %s", 
                academicField, userId);
        
        UserPreferences preferences = getByUserId(userId);
        preferences.setAcademicField(academicField);
        return userPreferencesRepository.save(preferences);
    }
    
    /**
     * Update user's study material generation preference.
     * 
     * @param userId The ID of the user
     * @param enabled Whether study material generation is enabled
     * @return The updated user preferences
     */
    @Transactional
    public UserPreferences updateStudyMaterialGeneration(UUID userId, boolean enabled) {
        LoggingUtil.debug(LOG, "updateStudyMaterialGeneration", "Setting study material generation to %s for user ID: %s", 
                enabled, userId);
        
        UserPreferences preferences = getByUserId(userId);
        preferences.setStudyMaterialGenerationEnabled(enabled);
        return userPreferencesRepository.save(preferences);
    }
    
    /**
     * Update user's email notifications preference.
     * 
     * @param userId The ID of the user
     * @param enabled Whether email notifications are enabled
     * @return The updated user preferences
     */
    @Transactional
    public UserPreferences updateEmailNotifications(UUID userId, boolean enabled) {
        LoggingUtil.debug(LOG, "updateEmailNotifications", "Setting email notifications to %s for user ID: %s", 
                enabled, userId);
        
        UserPreferences preferences = getByUserId(userId);
        preferences.setEmailNotificationsEnabled(enabled);
        return userPreferencesRepository.save(preferences);
    }
    
    /**
     * Update user's system notifications preference.
     * 
     * @param userId The ID of the user
     * @param enabled Whether system notifications are enabled
     * @return The updated user preferences
     */
    @Transactional
    public UserPreferences updateSystemNotifications(UUID userId, boolean enabled) {
        LoggingUtil.debug(LOG, "updateSystemNotifications", "Setting system notifications to %s for user ID: %s", 
                enabled, userId);
        
        UserPreferences preferences = getByUserId(userId);
        preferences.setSystemNotificationsEnabled(enabled);
        return userPreferencesRepository.save(preferences);
    }
    
    /**
     * Delete user preferences.
     * 
     * @param userId The ID of the user
     */
    @Transactional
    public void deleteByUserId(UUID userId) {
        LoggingUtil.info(LOG, "deleteByUserId", "Deleting user preferences for user ID: %s", userId);
        userPreferencesRepository.deleteByUserId(userId);
    }
    
    /**
     * Initialize default user preferences.
     * 
     * @param userId The ID of the user
     * @return The initialized user preferences
     */
    private UserPreferences initializeUserPreferences(UUID userId) {
        UserPreferences preferences = new UserPreferences(userId);
        // Default settings are set in the model
        return userPreferencesRepository.save(preferences);
    }
}

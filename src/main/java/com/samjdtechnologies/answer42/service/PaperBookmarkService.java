package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.daos.PaperBookmark;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.repository.DiscoveredPaperRepository;
import com.samjdtechnologies.answer42.repository.PaperBookmarkRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing paper bookmarks.
 * Handles bookmark creation, deletion, and retrieval operations.
 */
@Service
@Transactional
public class PaperBookmarkService {

    private static final Logger LOG = LoggerFactory.getLogger(PaperBookmarkService.class);

    private final PaperBookmarkRepository paperBookmarkRepository;
    private final DiscoveredPaperRepository discoveredPaperRepository;

    public PaperBookmarkService(PaperBookmarkRepository paperBookmarkRepository,
                               DiscoveredPaperRepository discoveredPaperRepository) {
        this.paperBookmarkRepository = paperBookmarkRepository;
        this.discoveredPaperRepository = discoveredPaperRepository;
    }

    /**
     * Toggles the bookmark status of a discovered paper for a user.
     * If the paper is bookmarked, removes the bookmark. If not bookmarked, creates a bookmark.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @return true if paper is now bookmarked, false if bookmark was removed
     * @throws IllegalArgumentException if user or paper doesn't exist
     */
    public boolean toggleBookmark(UUID userId, UUID discoveredPaperId) {
        LoggingUtil.debug(LOG, "toggleBookmark", "Toggling bookmark for user %s, paper %s", 
            userId, discoveredPaperId);

        // Check if bookmark already exists
        Optional<PaperBookmark> existingBookmark = paperBookmarkRepository
            .findByUserIdAndDiscoveredPaperId(userId, discoveredPaperId);

        if (existingBookmark.isPresent()) {
            // Remove existing bookmark
            paperBookmarkRepository.delete(existingBookmark.get());
            LoggingUtil.info(LOG, "toggleBookmark", "Removed bookmark for user %s, paper %s", 
                userId, discoveredPaperId);
            return false;
        } else {
            // Create new bookmark
            return createBookmark(userId, discoveredPaperId) != null;
        }
    }

    /**
     * Creates a new bookmark for a discovered paper.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @return The created bookmark or null if creation failed
     * @throws IllegalArgumentException if user or paper doesn't exist
     */
    public PaperBookmark createBookmark(UUID userId, UUID discoveredPaperId) {
        return createBookmark(userId, discoveredPaperId, null);
    }

    /**
     * Creates a new bookmark for a discovered paper with notes.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @param notes Optional notes for the bookmark
     * @return The created bookmark or null if creation failed
     * @throws IllegalArgumentException if user or paper doesn't exist
     */
    public PaperBookmark createBookmark(UUID userId, UUID discoveredPaperId, String notes) {
        LoggingUtil.debug(LOG, "createBookmark", "Creating bookmark for user %s, paper %s", 
            userId, discoveredPaperId);

        // Verify the discovered paper exists
        Optional<DiscoveredPaper> discoveredPaper = discoveredPaperRepository.findById(discoveredPaperId);
        if (discoveredPaper.isEmpty()) {
            throw new IllegalArgumentException("Discovered paper not found: " + discoveredPaperId);
        }

        // Check if bookmark already exists
        if (paperBookmarkRepository.existsByUserIdAndDiscoveredPaperId(userId, discoveredPaperId)) {
            LoggingUtil.warn(LOG, "createBookmark", "Bookmark already exists for user %s, paper %s", 
                userId, discoveredPaperId);
            return paperBookmarkRepository.findByUserIdAndDiscoveredPaperId(userId, discoveredPaperId)
                .orElse(null);
        }

        try {
            // Create the bookmark with lazy-loaded relationships
            PaperBookmark bookmark = new PaperBookmark();
            
            // Set user by creating a reference (avoiding full load)
            User userRef = new User();
            userRef.setId(userId);
            bookmark.setUser(userRef);
            
            // Set discovered paper
            bookmark.setDiscoveredPaper(discoveredPaper.get());
            bookmark.setNotes(notes);

            PaperBookmark savedBookmark = paperBookmarkRepository.save(bookmark);
            
            LoggingUtil.info(LOG, "createBookmark", "Created bookmark %s for user %s, paper %s", 
                savedBookmark.getId(), userId, discoveredPaperId);
            
            return savedBookmark;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createBookmark", "Failed to create bookmark", e);
            return null;
        }
    }

    /**
     * Removes a bookmark for a discovered paper.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @return true if bookmark was removed, false if it didn't exist
     */
    public boolean removeBookmark(UUID userId, UUID discoveredPaperId) {
        LoggingUtil.debug(LOG, "removeBookmark", "Removing bookmark for user %s, paper %s", 
            userId, discoveredPaperId);

        Optional<PaperBookmark> bookmark = paperBookmarkRepository
            .findByUserIdAndDiscoveredPaperId(userId, discoveredPaperId);

        if (bookmark.isPresent()) {
            paperBookmarkRepository.delete(bookmark.get());
            LoggingUtil.info(LOG, "removeBookmark", "Removed bookmark for user %s, paper %s", 
                userId, discoveredPaperId);
            return true;
        }

        LoggingUtil.debug(LOG, "removeBookmark", "No bookmark found for user %s, paper %s", 
            userId, discoveredPaperId);
        return false;
    }

    /**
     * Checks if a user has bookmarked a specific discovered paper.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @return true if the paper is bookmarked, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isBookmarked(UUID userId, UUID discoveredPaperId) {
        return paperBookmarkRepository.existsByUserIdAndDiscoveredPaperId(userId, discoveredPaperId);
    }

    /**
     * Gets all bookmarks for a user.
     * 
     * @param userId The user's ID
     * @return List of user's bookmarks
     */
    @Transactional(readOnly = true)
    public List<PaperBookmark> getUserBookmarks(UUID userId) {
        LoggingUtil.debug(LOG, "getUserBookmarks", "Getting bookmarks for user %s", userId);
        return paperBookmarkRepository.findByUserId(userId);
    }

    /**
     * Gets the total number of bookmarks for a user.
     * 
     * @param userId The user's ID
     * @return Number of bookmarks
     */
    @Transactional(readOnly = true)
    public long getUserBookmarkCount(UUID userId) {
        return paperBookmarkRepository.countByUserId(userId);
    }

    /**
     * Finds bookmarks by tag for a user.
     * 
     * @param userId The user's ID
     * @param tag The tag to search for
     * @return List of bookmarks containing the tag
     */
    @Transactional(readOnly = true)
    public List<PaperBookmark> getUserBookmarksByTag(UUID userId, String tag) {
        LoggingUtil.debug(LOG, "getUserBookmarksByTag", "Getting bookmarks for user %s with tag %s", 
            userId, tag);
        return paperBookmarkRepository.findByUserIdAndTagsContaining(userId, tag);
    }

    /**
     * Updates the notes for an existing bookmark.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @param notes The new notes
     * @return true if bookmark was updated, false if bookmark doesn't exist
     */
    public boolean updateBookmarkNotes(UUID userId, UUID discoveredPaperId, String notes) {
        LoggingUtil.debug(LOG, "updateBookmarkNotes", "Updating notes for user %s, paper %s", 
            userId, discoveredPaperId);

        Optional<PaperBookmark> bookmark = paperBookmarkRepository
            .findByUserIdAndDiscoveredPaperId(userId, discoveredPaperId);

        if (bookmark.isPresent()) {
            bookmark.get().setNotes(notes);
            paperBookmarkRepository.save(bookmark.get());
            LoggingUtil.info(LOG, "updateBookmarkNotes", "Updated notes for user %s, paper %s", 
                userId, discoveredPaperId);
            return true;
        }

        LoggingUtil.warn(LOG, "updateBookmarkNotes", "No bookmark found for user %s, paper %s", 
            userId, discoveredPaperId);
        return false;
    }

    /**
     * Updates the tags for an existing bookmark.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @param tags The new tags (comma-separated)
     * @return true if bookmark was updated, false if bookmark doesn't exist
     */
    public boolean updateBookmarkTags(UUID userId, UUID discoveredPaperId, String tags) {
        LoggingUtil.debug(LOG, "updateBookmarkTags", "Updating tags for user %s, paper %s", 
            userId, discoveredPaperId);

        Optional<PaperBookmark> bookmark = paperBookmarkRepository
            .findByUserIdAndDiscoveredPaperId(userId, discoveredPaperId);

        if (bookmark.isPresent()) {
            bookmark.get().setTags(tags);
            paperBookmarkRepository.save(bookmark.get());
            LoggingUtil.info(LOG, "updateBookmarkTags", "Updated tags for user %s, paper %s", 
                userId, discoveredPaperId);
            return true;
        }

        LoggingUtil.warn(LOG, "updateBookmarkTags", "No bookmark found for user %s, paper %s", 
            userId, discoveredPaperId);
        return false;
    }
}

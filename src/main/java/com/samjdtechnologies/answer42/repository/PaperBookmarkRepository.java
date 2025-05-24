package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.daos.PaperBookmark;

/**
 * Repository interface for PaperBookmark entities.
 * Provides data access operations for paper bookmarks.
 */
@Repository
public interface PaperBookmarkRepository extends JpaRepository<PaperBookmark, UUID> {

    /**
     * Finds all bookmarks for a specific user.
     * 
     * @param userId The user's ID
     * @return List of bookmarks for the user
     */
    @Query("SELECT pb FROM PaperBookmark pb WHERE pb.user.id = :userId ORDER BY pb.createdAt DESC")
    List<PaperBookmark> findByUserId(@Param("userId") UUID userId);

    /**
     * Finds a specific bookmark by user and discovered paper.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @return Optional containing the bookmark if it exists
     */
    @Query("SELECT pb FROM PaperBookmark pb WHERE pb.user.id = :userId AND pb.discoveredPaper.id = :discoveredPaperId")
    Optional<PaperBookmark> findByUserIdAndDiscoveredPaperId(@Param("userId") UUID userId, 
                                                            @Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Checks if a user has bookmarked a specific discovered paper.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     * @return True if the bookmark exists, false otherwise
     */
    @Query("SELECT COUNT(pb) > 0 FROM PaperBookmark pb WHERE pb.user.id = :userId AND pb.discoveredPaper.id = :discoveredPaperId")
    boolean existsByUserIdAndDiscoveredPaperId(@Param("userId") UUID userId, 
                                              @Param("discoveredPaperId") UUID discoveredPaperId);

    /**
     * Counts the total number of bookmarks for a user.
     * 
     * @param userId The user's ID
     * @return Number of bookmarks for the user
     */
    @Query("SELECT COUNT(pb) FROM PaperBookmark pb WHERE pb.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Finds bookmarks with specific tags for a user.
     * 
     * @param userId The user's ID
     * @param tag The tag to search for
     * @return List of bookmarks containing the tag
     */
    @Query("SELECT pb FROM PaperBookmark pb WHERE pb.user.id = :userId AND pb.tags LIKE %:tag% ORDER BY pb.createdAt DESC")
    List<PaperBookmark> findByUserIdAndTagsContaining(@Param("userId") UUID userId, 
                                                     @Param("tag") String tag);

    /**
     * Deletes a bookmark by user and discovered paper.
     * 
     * @param userId The user's ID
     * @param discoveredPaperId The discovered paper's ID
     */
    @Query("DELETE FROM PaperBookmark pb WHERE pb.user.id = :userId AND pb.discoveredPaper.id = :discoveredPaperId")
    void deleteByUserIdAndDiscoveredPaperId(@Param("userId") UUID userId, 
                                           @Param("discoveredPaperId") UUID discoveredPaperId);
}

package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;

/**
 * Repository interface for the Paper entity.
 * Provides data access methods for papers.
 */
@Repository
public interface PaperRepository extends JpaRepository<Paper, UUID> {

    /**
     * Find all papers by user.
     *
     * @param user The user whose papers to find
     * @return List of papers belonging to the user
     */
    List<Paper> findByUser(User user);

    /**
     * Find all papers by user with pagination.
     *
     * @param user The user whose papers to find
     * @param pageable Pagination information
     * @return Page of papers belonging to the user
     */
    Page<Paper> findByUser(User user, Pageable pageable);

    /**
     * Find papers by title containing the search term (case-insensitive).
     *
     * @param title The search term for paper titles
     * @param pageable Pagination information
     * @return Page of matching papers
     */
    Page<Paper> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    /**
     * Find papers by user and title containing the search term (case-insensitive).
     *
     * @param user The user whose papers to search
     * @param title The search term for paper titles
     * @param pageable Pagination information
     * @return Page of matching papers
     */
    Page<Paper> findByUserAndTitleContainingIgnoreCase(User user, String title, Pageable pageable);

    /**
     * Find papers by status.
     *
     * @param status The paper status
     * @param pageable Pagination information
     * @return Page of papers with the specified status
     */
    Page<Paper> findByStatus(String status, Pageable pageable);

    /**
     * Find papers by user and status.
     *
     * @param user The user whose papers to find
     * @param status The paper status
     * @param pageable Pagination information
     * @return Page of papers belonging to the user with the specified status
     */
    Page<Paper> findByUserAndStatus(User user, String status, Pageable pageable);

    /**
     * Count papers by user.
     *
     * @param user The user whose papers to count
     * @return The number of papers belonging to the user
     */
    long countByUser(User user);

    /**
     * Find recent papers by user, ordered by creation date.
     *
     * @param user The user whose papers to find
     * @param pageable Pagination information including size limit
     * @return List of recent papers belonging to the user
     */
    @Query("SELECT p FROM Paper p WHERE p.user = :user ORDER BY p.createdAt DESC")
    List<Paper> findRecentPapersByUser(@Param("user") User user, Pageable pageable);

    /**
     * Search papers by title, abstract, or content.
     *
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching papers
     */
    @Query("SELECT p FROM Paper p WHERE " +
           "p.title LIKE %:searchTerm% OR " +
           "p.paperAbstract LIKE %:searchTerm% OR " +
           "p.textContent LIKE %:searchTerm%")
    Page<Paper> searchPapers(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Search papers by user, title, abstract, or content.
     *
     * @param user The user whose papers to search
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching papers
     */
    @Query("SELECT p FROM Paper p WHERE p.user = :user AND (" +
           "p.title LIKE %:searchTerm% OR " +
           "p.paperAbstract LIKE %:searchTerm% OR " +
           "p.textContent LIKE %:searchTerm%)")
    Page<Paper> searchPapersByUser(@Param("user") User user, 
                                  @Param("searchTerm") String searchTerm, 
                                  Pageable pageable);

    /**
     * Find a paper by its DOI.
     *
     * @param doi The DOI of the paper
     * @return Optional containing the paper if found
     */
    Optional<Paper> findByDoi(String doi);

    /**
     * Find papers by publication year.
     *
     * @param year The publication year
     * @param pageable Pagination information
     * @return Page of papers published in the specified year
     */
    Page<Paper> findByYear(Integer year, Pageable pageable);

    /**
     * Find papers by user and publication year.
     *
     * @param user The user whose papers to find
     * @param year The publication year
     * @param pageable Pagination information
     * @return Page of papers belonging to the user and published in the specified year
     */
    Page<Paper> findByUserAndYear(User user, Integer year, Pageable pageable);
}

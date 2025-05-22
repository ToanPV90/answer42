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

import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;

/**
 * Repository interface for Project entity.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    
    /**
     * Find projects by user.
     * 
     * @param user The user whose projects to find
     * @param pageable Pagination information
     * @return Page of projects
     */
    Page<Project> findByUser(User user, Pageable pageable);
    
    /**
     * Find a project by ID and user.
     * 
     * @param id The project ID
     * @param user The user
     * @return Optional containing the project if found
     */
    Optional<Project> findByIdAndUser(UUID id, User user);
    
    /**
     * Count projects for a user.
     * 
     * @param user The user whose projects to count
     * @return The number of projects
     */
    long countByUser(User user);
    
    /**
     * Find recent projects for a user.
     * 
     * @param user The user whose projects to retrieve
     * @param pageable Pagination information
     * @return List of recent projects
     */
    @Query("SELECT p FROM Project p WHERE p.user = :user ORDER BY p.updatedAt DESC")
    List<Project> findRecentProjectsByUser(@Param("user") User user, Pageable pageable);
    
    /**
     * Search projects by name or description.
     * 
     * @param user The user whose projects to search
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching projects
     */
    @Query("SELECT p FROM Project p WHERE p.user = :user " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Project> searchProjectsByUser(
        @Param("user") User user, 
        @Param("searchTerm") String searchTerm,
        Pageable pageable
    );
    
    /**
     * Find public projects.
     * 
     * @param pageable Pagination information
     * @return Page of public projects
     */
    Page<Project> findByIsPublicTrue(Pageable pageable);
}

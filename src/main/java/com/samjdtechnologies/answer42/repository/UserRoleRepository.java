package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.UserRole;

/**
 * Repository interface for managing UserRole entities in the database.
 * Provides methods for managing user role assignments.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    
    /**
     * Finds all roles assigned to a specific user.
     * 
     * @param userId the ID of the user
     * @return a list of UserRole entities for the user
     */
    List<UserRole> findByUserId(UUID userId);
    
    /**
     * Finds a specific role assignment for a user.
     * 
     * @param userId the ID of the user
     * @param role the role name
     * @return an Optional containing the UserRole if found
     */
    Optional<UserRole> findByUserIdAndRole(UUID userId, String role);
    
    /**
     * Checks if a user has a specific role.
     * 
     * @param userId the ID of the user
     * @param role the role name
     * @return true if the user has the role, false otherwise
     */
    boolean existsByUserIdAndRole(UUID userId, String role);
    
    /**
     * Removes a specific role from a user.
     * 
     * @param userId the ID of the user
     * @param role the role name to remove
     * @return the number of records deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserRole ur WHERE ur.userId = :userId AND ur.role = :role")
    int deleteByUserIdAndRole(@Param("userId") UUID userId, @Param("role") String role);
    
    /**
     * Removes all roles from a user.
     * 
     * @param userId the ID of the user
     * @return the number of records deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserRole ur WHERE ur.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
    
    /**
     * Finds all users with a specific role.
     * 
     * @param role the role name
     * @return a list of UserRole entities with the specified role
     */
    List<UserRole> findByRole(String role);
    
    /**
     * Counts how many users have a specific role.
     * 
     * @param role the role name
     * @return the count of users with the role
     */
    long countByRole(String role);
    
    /**
     * Finds all distinct roles in the system.
     * 
     * @return a list of distinct role names
     */
    @Query("SELECT DISTINCT ur.role FROM UserRole ur ORDER BY ur.role")
    List<String> findAllDistinctRoles();
    
    /**
     * Finds all role names assigned to a specific user.
     * Returns just the role strings rather than full UserRole entities.
     * 
     * @param userId the ID of the user
     * @return a list of role names assigned to the user
     */
    @Query("SELECT ur.role FROM UserRole ur WHERE ur.userId = :userId ORDER BY ur.role")
    List<String> findRolesByUserId(@Param("userId") UUID userId);
    
    /**
     * Gets user role statistics - count of users per role.
     * Useful for administrative dashboards and reporting.
     * 
     * @return a list of Object arrays where each array contains [role_name, user_count]
     */
    @Query("SELECT ur.role, COUNT(DISTINCT ur.userId) FROM UserRole ur GROUP BY ur.role ORDER BY ur.role")
    List<Object[]> countUsersByRole();
}

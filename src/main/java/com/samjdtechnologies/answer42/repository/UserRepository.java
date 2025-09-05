package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.User;

/**
 * Repository interface for managing User entities in the database.
 * Provides methods for finding and validating user credentials.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Finds a user by their username.
     * 
     * @param username the username to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Finds a user by their email address.
     * 
     * @param email the email address to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Checks if a username already exists in the database.
     * 
     * @param username the username to check
     * @return true if the username exists, false otherwise
     */
    boolean existsByUsername(String username);
    
    /**
     * Checks if an email address already exists in the database.
     * 
     * @param email the email address to check
     * @return true if the email exists, false otherwise
     */
    boolean existsByEmail(String email);
    
    /**
     * Finds all users with a specific role.
     * Uses JOIN to query through the new UserRole entity relationship.
     * 
     * @param role the role name to search for
     * @return a list of users with the specified role
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.userRoles ur WHERE ur.role = :role")
    List<User> findByRole(@Param("role") String role);
    
    /**
     * Finds users with any of the specified roles.
     * 
     * @param roles the list of role names to search for
     * @return a list of users with any of the specified roles
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.userRoles ur WHERE ur.role IN :roles")
    List<User> findByRoleIn(@Param("roles") List<String> roles);
    
    /**
     * Checks if a user has a specific role.
     * 
     * @param userId the user ID
     * @param role the role name
     * @return true if the user has the role, false otherwise
     */
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.userRoles ur WHERE u.id = :userId AND ur.role = :role")
    boolean userHasRole(@Param("userId") UUID userId, @Param("role") String role);
}

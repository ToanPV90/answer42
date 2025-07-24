package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.model.db.UserRole;
import com.samjdtechnologies.answer42.repository.UserRepository;
import com.samjdtechnologies.answer42.repository.UserRoleRepository;

/**
 * Service for managing user operations including registration, authentication, and profile management.
 * Provides methods for CRUD operations on User entities and user verification.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs a new UserService with required dependencies.
     * 
     * @param userRepository the repository for User entity operations
     * @param userRoleRepository the repository for UserRole entity operations
     * @param passwordEncoder the encoder for securely hashing passwords
     */
    public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Retrieves all users from the database.
     * 
     * @return a list of all users
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Finds a user by their unique identifier.
     * 
     * @param id the UUID of the user to find
     * @return an Optional containing the user if found, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    /**
     * Finds a user by their username.
     * 
     * @param username the username to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Finds a user by their email address.
     * 
     * @param email the email address to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Saves a user to the database. If the user has a password that isn't already
     * encoded (doesn't start with the bcrypt prefix), it will be encoded before saving.
     * 
     * @param user the user entity to save
     * @return the saved user entity with any generated fields populated
     */
    @Transactional
    public User save(User user) {
        // Encode password before saving
        if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }

    /**
     * Registers a new user with the system. Checks for existing username or email,
     * and throws an exception if either already exists. Encodes the password and
     * assigns the default user role.
     * 
     * @param username the username for the new user
     * @param email the email address for the new user
     * @param password the password for the new user (will be encoded)
     * @return the newly created and saved user entity
     * @throws IllegalArgumentException if the username or email already exists
     */
    @Transactional
    public User register(String username, String email, String password) {
        // Check if username or email already exists
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User(username, passwordEncoder.encode(password), email);
        user.addRole("ROLE_USER"); // Default role
        return userRepository.save(user);
    }

    /**
     * Deletes a user by their unique identifier.
     * 
     * @param id the UUID of the user to delete
     */
    @Transactional
    public void deleteById(UUID id) {
        userRepository.deleteById(id);
    }

    /**
     * Checks if a user with the given username exists.
     * 
     * @param username the username to check
     * @return true if a user with the username exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Checks if a user with the given email address exists.
     * 
     * @param email the email address to check
     * @return true if a user with the email exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    // ========== ENHANCED ROLE MANAGEMENT METHODS ==========

    /**
     * Adds a role to a user with database-level audit trail.
     * Uses the new UserRole entity for improved performance and consistency.
     * 
     * @param userId the ID of the user to add the role to
     * @param role the role to add (e.g., "ROLE_ADMIN", "ROLE_USER")
     * @throws IllegalArgumentException if the user does not exist
     */
    @Transactional
    public void addRoleToUser(UUID userId, String role) {
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }
        
        // Check if role already exists to avoid duplicates
        if (!userRoleRepository.existsByUserIdAndRole(userId, role)) {
            UserRole userRole = new UserRole(userId, role);
            userRoleRepository.save(userRole);
        }
    }

    /**
     * Removes a role from a user.
     * 
     * @param userId the ID of the user to remove the role from
     * @param role the role to remove
     */
    @Transactional
    public void removeRoleFromUser(UUID userId, String role) {
        userRoleRepository.deleteByUserIdAndRole(userId, role);
    }

    /**
     * Gets all users with a specific role using optimized database queries.
     * Leverages new composite indexes for improved performance.
     * 
     * @param role the role to search for
     * @return list of users with the specified role
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByRole(String role) {
        return userRepository.findByRole(role);
    }

    /**
     * Checks if a user has a specific role using efficient database-level check.
     * This method uses optimized EXISTS queries for better performance.
     * 
     * @param userId the ID of the user to check
     * @param role the role to check for
     * @return true if the user has the role, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean userHasRole(UUID userId, String role) {
        return userRepository.userHasRole(userId, role);
    }

    /**
     * Gets all distinct roles available in the system.
     * Useful for role management interfaces and validation.
     * 
     * @return list of all distinct roles in the system
     */
    @Transactional(readOnly = true)
    public List<String> getAllAvailableRoles() {
        return userRoleRepository.findAllDistinctRoles();
    }

    /**
     * Gets all roles for a specific user.
     * 
     * @param userId the ID of the user
     * @return list of roles assigned to the user
     */
    @Transactional(readOnly = true)
    public List<String> getUserRoles(UUID userId) {
        return userRoleRepository.findRolesByUserId(userId);
    }

    /**
     * Enhanced registration method that uses the new role management system.
     * This method creates the user and adds the default role using the
     * optimized UserRole entity instead of the legacy collection approach.
     * 
     * @param username the username for the new user
     * @param email the email address for the new user
     * @param password the password for the new user (will be encoded)
     * @return the newly created and saved user entity
     * @throws IllegalArgumentException if the username or email already exists
     */
    @Transactional
    public User registerWithEnhancedRoles(String username, String email, String password) {
        // Check if username or email already exists
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create user without roles first
        User user = new User(username, passwordEncoder.encode(password), email);
        user = userRepository.save(user);
        
        // Add default role using enhanced role management
        addRoleToUser(user.getId(), "ROLE_USER");
        
        return user;
    }

    /**
     * Bulk role assignment method for administrative operations.
     * 
     * @param userIds list of user IDs to assign roles to
     * @param role the role to assign to all users
     * @return number of role assignments made (excludes duplicates)
     */
    @Transactional
    public int bulkAssignRole(List<UUID> userIds, String role) {
        int assignmentCount = 0;
        for (UUID userId : userIds) {
            if (userRepository.existsById(userId) && !userRoleRepository.existsByUserIdAndRole(userId, role)) {
                UserRole userRole = new UserRole(userId, role);
                userRoleRepository.save(userRole);
                assignmentCount++;
            }
        }
        return assignmentCount;
    }

    /**
     * Gets user role statistics for administrative dashboards.
     * 
     * @return list of role counts
     */
    @Transactional(readOnly = true)
    public List<Object[]> getRoleStatistics() {
        return userRoleRepository.countUsersByRole();
    }
}

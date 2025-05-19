package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.repository.UserRepository;

/**
 * Service for managing user operations including registration, authentication, and profile management.
 * Provides methods for CRUD operations on User entities and user verification.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs a new UserService with required dependencies.
     * 
     * @param userRepository the repository for User entity operations
     * @param passwordEncoder the encoder for securely hashing passwords
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
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
}

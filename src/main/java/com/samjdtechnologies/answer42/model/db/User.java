package com.samjdtechnologies.answer42.model.db;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a user in the system.
 * Maps to the 'users' table in the answer42 schema.
 */
@Entity
@Table(name = "users", schema = "answer42")
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "username", unique = true, nullable = false)
    private String username;
    
    @Column(name = "password")
    private String password;
    
    @Email
    @Column(name = "email", unique = true)
    private String email;
    
    private boolean enabled = true;
    
    @Column(name = "last_login")
    private ZonedDateTime lastLogin;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    
    // UPDATED: Use OneToMany relationship instead of ElementCollection
    @OneToMany(mappedBy = "userId", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> userRoles = new HashSet<>();

    /**
     * Constructor with required fields for creating a new user.
     *
     * @param username The username for the user account
     * @param password The password for the user account (should be encrypted before storage)
     * @param email The email address for the user account
     */
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.createdAt = ZonedDateTime.now();
    }

    /**
     * Gets the roles as a Set of strings for backward compatibility.
     */
    public Set<String> getRoles() {
        return userRoles.stream()
            .map(UserRole::getRole)
            .collect(Collectors.toSet());
    }

    /**
     * Adds a role to this user's set of roles.
     */
    public void addRole(String role) {
        // Check if role already exists
        boolean exists = userRoles.stream()
            .anyMatch(ur -> ur.getRole().equals(role));
        
        if (!exists) {
            UserRole userRole = new UserRole(this.id, role);
            this.userRoles.add(userRole);
        }
    }
    
    /**
     * Removes a role from this user's set of roles.
     */
    public void removeRole(String role) {
        userRoles.removeIf(ur -> ur.getRole().equals(role));
    }
    
    /**
     * Checks if user has a specific role.
     */
    public boolean hasRole(String role) {
        return userRoles.stream()
            .anyMatch(ur -> ur.getRole().equals(role));
    }
    

}

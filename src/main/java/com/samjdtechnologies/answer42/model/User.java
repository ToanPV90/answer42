package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
    private LocalDateTime lastLogin;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_roles", 
        schema = "answer42", 
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();


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
    }


    /**
     * Adds a role to this user's set of roles.
     *
     * @param role The role to add to the user
     */
    public void addRole(String role) {
        this.roles.add(role);
    }
    

}

package com.samjdtechnologies.answer42.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.security.JwtTokenUtil;
import com.samjdtechnologies.answer42.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;

    /**
     * Constructs a new AuthController with required dependencies.
     *
     * @param authenticationManager The authentication manager for authenticating users
     * @param jwtTokenUtil The JWT utility for generating and validating tokens
     * @param userService The user service for user management operations
     */
    public AuthController(AuthenticationManager authenticationManager, JwtTokenUtil jwtTokenUtil, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userService = userService;
    }

    /**
     * Authenticates a user with provided credentials and returns a JWT token.
     *
     * @param loginRequest The login request containing username and password
     * @return A ResponseEntity containing the JWT token and other authentication details
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Get the authenticated user details
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        String jwt = jwtTokenUtil.generateToken(userDetails);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("type", "Bearer");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Registers a new user with the provided details.
     *
     * @param registerRequest The registration request containing username, email, and password
     * @return A ResponseEntity containing the created user information or an error message
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        if (userService.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body("Username is already taken");
        }

        if (userService.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body("Email is already in use");
        }

        // Create new user
        try {
            User user = userService.register(
                    registerRequest.getUsername(),
                    registerRequest.getEmail(),
                    registerRequest.getPassword()
            );
            
            // Create response DTO with user information (excluding sensitive data)
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("roles", user.getRoles());
            response.put("enabled", user.isEnabled());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // Inner classes for request/response objects
    
    public static class LoginRequest {
        private String username;
        private String password;

        /**
         * Default constructor for LoginRequest.
         */
        public LoginRequest() {
        }

        /**
         * Gets the username for authentication.
         *
         * @return The username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Sets the username for authentication.
         *
         * @param username The username to set
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Gets the password for authentication.
         *
         * @return The password
         */
        public String getPassword() {
            return password;
        }

        /**
         * Sets the password for authentication.
         *
         * @param password The password to set
         */
        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;

        /**
         * Default constructor for RegisterRequest.
         */
        public RegisterRequest() {
        }

        /**
         * Gets the username for registration.
         *
         * @return The username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Sets the username for registration.
         *
         * @param username The username to set
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Gets the email for registration.
         *
         * @return The email
         */
        public String getEmail() {
            return email;
        }

        /**
         * Sets the email for registration.
         *
         * @param email The email to set
         */
        public void setEmail(String email) {
            this.email = email;
        }

        /**
         * Gets the password for registration.
         *
         * @return The password
         */
        public String getPassword() {
            return password;
        }

        /**
         * Sets the password for registration.
         *
         * @param password The password to set
         */
        public void setPassword(String password) {
            this.password = password;
        }
    }
}

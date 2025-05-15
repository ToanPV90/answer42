package com.samjdtechnologies.answer42.ui.service;

import com.samjdtechnologies.answer42.controller.AuthController;
import com.samjdtechnologies.answer42.service.UserService;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service for handling authentication operations in the Vaadin UI.
 */
@Service
public class AuthenticationService {

    private final AuthController authController;
    private final UserService userService;

    public AuthenticationService(AuthController authController, UserService userService) {
        this.authController = authController;
        this.userService = userService;
    }

    /**
     * Attempts to log in a user with the provided credentials.
     *
     * @param username the username
     * @param password the password
     * @return an optional containing the JWT token if login was successful, empty otherwise
     */
    public Optional<String> login(String username, String password) {
        try {
            AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
            loginRequest.setUsername(username);
            loginRequest.setPassword(password);
            
            ResponseEntity<?> response = authController.authenticateUser(loginRequest);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response.getBody();
                String token = (String) responseMap.get("token");
                
                // Store the token in the Vaadin session
                VaadinSession.getCurrent().setAttribute("jwt_token", token);
                
                return Optional.of(token);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Registers a new user.
     *
     * @param username the username
     * @param email the email
     * @param password the password
     * @return true if registration was successful, false otherwise
     */
    public boolean register(String username, String email, String password) {
        try {
            AuthController.RegisterRequest registerRequest = new AuthController.RegisterRequest();
            registerRequest.setUsername(username);
            registerRequest.setEmail(email);
            registerRequest.setPassword(password);
            
            ResponseEntity<?> response = authController.registerUser(registerRequest);
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a username already exists.
     *
     * @param username the username to check
     * @return true if the username exists, false otherwise
     */
    public boolean usernameExists(String username) {
        return userService.existsByUsername(username);
    }

    /**
     * Checks if an email already exists.
     *
     * @param email the email to check
     * @return true if the email exists, false otherwise
     */
    public boolean emailExists(String email) {
        return userService.existsByEmail(email);
    }

    /**
     * Logs out the current user.
     */
    public void logout() {
        // Clear the Vaadin session
        VaadinSession.getCurrent().getSession().invalidate();
        // Clear Spring Security context
        SecurityContextHolder.clearContext();
    }

    /**
     * Checks if a user is currently authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && 
               !"anonymousUser".equals(authentication.getPrincipal());
    }
}

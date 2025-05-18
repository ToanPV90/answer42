package com.samjdtechnologies.answer42.ui.service;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.controller.AuthController;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;

/**
 * Service for handling authentication operations in the Vaadin UI.
 */
@Service
public class AuthenticationService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);

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
        LoggingUtil.debug(LOG, "login", "Attempting login for user: %s", username);
        try {
            AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
            loginRequest.setUsername(username);
            loginRequest.setPassword(password);
            
            ResponseEntity<?> response = authController.authenticateUser(loginRequest);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response.getBody();
                String token = (String) responseMap.get("token");
                
                // Store the token in the Vaadin session and HTTP session
                VaadinSession.getCurrent().setAttribute("jwt_token", token);
                
                // Ensure token is also in the HTTP session for filter access
                if (VaadinSession.getCurrent() != null && 
                    VaadinSession.getCurrent().getSession() != null) {
                    VaadinSession.getCurrent().getSession().setAttribute("jwt_token", token);
                }
                
                // Store token in localStorage and sessionStorage for client-side requests
                UI.getCurrent().getPage().executeJs(
                    "window.storeJwtToken($0)", token);
                
                LoggingUtil.debug(LOG, "login", "JWT token stored in session and localStorage for user: %s", username);
                
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
        // Clear the JWT token from localStorage
        UI.getCurrent().getPage().executeJs("window.clearJwtToken()");
    }

    /**
     * Checks if a user is currently authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuth = authentication != null && 
                        authentication.isAuthenticated() && 
                        !"anonymousUser".equals(authentication.getPrincipal());
        
        LoggingUtil.debug(LOG, "isAuthenticated", 
                "Authentication check: %s, Result: %s", 
                (authentication != null ? 
                    authentication.getName() + " (authenticated: " + authentication.isAuthenticated() + 
                    ", principal: " + authentication.getPrincipal().getClass().getName() + ")" 
                    : "null"), 
                isAuth);
        
        return isAuth;
    }
    
    /**
     * Gets the current user ID.
     *
     * @return the current user ID or null if not authenticated
     */
    public java.util.UUID getCurrentUserId() {
        if (!isAuthenticated()) {
            return null;
        }
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        return userService.findByUsername(username)
                .map(user -> user.getId())
                .orElse(null);
    }
}

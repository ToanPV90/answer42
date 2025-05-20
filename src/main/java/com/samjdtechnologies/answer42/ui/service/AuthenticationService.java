package com.samjdtechnologies.answer42.ui.service;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.config.AIConfig;
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
    private AIConfig aiConfig;

    /**
     * Constructs a new AuthenticationService with the necessary dependencies.
     * 
     * @param authController the controller that handles authentication API requests
     * @param userService the service that provides user operations and data access
     */
    public AuthenticationService(AuthController authController, UserService userService) {
        this.authController = authController;
        this.userService = userService;
    }
    
    /**
     * Sets the AIConfig dependency. This is done via setter injection to avoid
     * circular dependency issues between AIConfig and AuthenticationService.
     * 
     * @param aiConfig the configuration for AI services
     */
    @Autowired
    public void setAiConfig(AIConfig aiConfig) {
        this.aiConfig = aiConfig;
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
                
                // Update the user's last login timestamp
                userService.findByUsername(username).ifPresent(user -> {
                    // Update the last login timestamp
                    user.setLastLogin(java.time.LocalDateTime.now());
                    userService.save(user);
                    LoggingUtil.debug(LOG, "login", "Updated last login timestamp for user: %s", username);
                    
                    // Update AI API keys based on user preferences
                    aiConfig.updateKeysForUser(user);
                    LoggingUtil.debug(LOG, "login", "Updated API keys for user: %s", username);
                });
                
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
     * 
     * Important: This method does NOT invalidate the session as that would make
     * UI.getCurrent() return null, preventing navigation. The session should be
     * invalidated after any UI operations are complete.
     */
    public void logout() {
        // First, clear the JWT token from localStorage if UI is available
        UI ui = UI.getCurrent();
        if (ui != null && ui.getPage() != null) {
            ui.getPage().executeJs("window.clearJwtToken()");
        }
        
        // Reset AI API keys to system defaults
        aiConfig.resetToSystemDefaults();
        LoggingUtil.debug(LOG, "logout", "Reset API keys to system defaults");
        
        // Clear Spring Security context
        SecurityContextHolder.clearContext();
    }
    
    /**
     * Completes the logout process by invalidating the session.
     * This should only be called after all UI operations are complete.
     */
    public void invalidateSession() {
        VaadinSession currentSession = VaadinSession.getCurrent();
        if (currentSession != null && currentSession.getSession() != null) {
            currentSession.getSession().invalidate();
        }
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
                    authentication.getName() + " authenticated: " + authentication.isAuthenticated()
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

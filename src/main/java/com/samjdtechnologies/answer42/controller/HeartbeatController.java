package com.samjdtechnologies.answer42.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samjdtechnologies.answer42.security.CustomUserDetailsService;
import com.samjdtechnologies.answer42.security.JwtTokenUtil;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Controller to handle heartbeat requests to keep the session alive.
 */
@RestController
public class HeartbeatController {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatController.class);

    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private CustomUserDetailsService userDetailsService;

    /**
     * Endpoint to keep the session alive and validate authentication status.
     * This is called by the frontend heartbeat mechanism.
     *
     * @param request The HTTP request
     * @return A simple success response if authenticated
     */
    @GetMapping("/heartbeat")
    public ResponseEntity<String> heartbeat(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        
        // Try to get JWT token from session if it exists
        String jwtToken = null;
        if (session != null) {
            Object sessionToken = session.getAttribute("jwt_token");
            if (sessionToken != null) {
                jwtToken = sessionToken.toString();
                LoggingUtil.debug(LOG, "heartbeat", "Found JWT token in session");
                
                try {
                    // Extract username from token
                    String username = jwtTokenUtil.extractUsername(jwtToken);
                    if (username != null) {
                        // Load user details for authentication
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        
                        // Validate token
                        if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                            // Create authentication object
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            
                            LoggingUtil.debug(LOG, "heartbeat", "Authenticated user from session token: %s", username);
                            return ResponseEntity.ok().body("Authenticated");
                        }
                    }
                } catch (Exception e) {
                    LoggingUtil.warn(LOG, "heartbeat", "Error processing session token: %s", e.getMessage());
                }
            }
        }
        
        // Try with existing authentication 
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            LoggingUtil.debug(LOG, "heartbeat", "Received heartbeat from authenticated user: %s", auth.getName());
            return ResponseEntity.ok().body("Authenticated");
        } else {
            LoggingUtil.debug(LOG, "heartbeat", "Received heartbeat from unauthenticated user");
            return ResponseEntity.status(401).body("Not authenticated");
        }
    }
}

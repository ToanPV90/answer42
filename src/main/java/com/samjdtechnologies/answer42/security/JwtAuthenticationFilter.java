package com.samjdtechnologies.answer42.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.samjdtechnologies.answer42.config.JwtConfig;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtTokenUtil jwtTokenUtil;
    private UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }
    
    // Add additional constructor for JwtConfig support
    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil, JwtConfig jwtConfig) {
        this.jwtTokenUtil = jwtTokenUtil;
        // You can use jwtConfig properties here if needed
    }

    public void setUserDetailsService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();

        // Skip JWT validation for specific requests
        boolean skipAuth = false;
        
        // Vaadin static resources and endpoints
        if (requestURI.contains("/VAADIN/") || 
            requestURI.contains("/frontend/") ||
            requestURI.contains("/sw.js") ||
            requestURI.contains("/offline") ||
            requestURI.contains("/manifest.webmanifest") ||
            requestURI.contains("/sw-runtime-resources-precache.js") ||
            requestURI.contains("/icons/") ||
            requestURI.contains("/images/") ||
            requestURI.contains("/favicon.") ||
            requestURI.contains("/robots.txt") ||
            requestURI.contains("/PUSH") || 
            requestURI.contains("/connect/")) {
            skipAuth = true;
        }
        
        // Vaadin URL parameters (v-r parameter) - only skip for specific internal operations
        if (queryString != null && 
            (queryString.contains("v-r=heartbeat") || 
             queryString.contains("v-r=init") || 
             queryString.contains("v-r=push") ||
             queryString.contains("v-r=")&& requestURI.contains("VAADIN"))) {
            skipAuth = true;
        }

        // Do not skip auth for main application URLs even with v-r parameter
        if (requestURI.equals("/") || 
            requestURI.contains("/" + UIConstants.ROUTE_DASHBOARD) ||
            requestURI.contains("/" + UIConstants.ROUTE_PAPERS) ||
            requestURI.contains("/" + UIConstants.ROUTE_PROJECTS) ||
            requestURI.contains("/" + UIConstants.ROUTE_AI_CHAT) ||
            requestURI.contains("/" + UIConstants.ROUTE_PROFILE) ||
            requestURI.contains("/" + UIConstants.ROUTE_SETTINGS)) {
            // Override skipAuth for application views - they must be authenticated
            if (queryString == null || 
                !(queryString.contains("v-r=heartbeat") || 
                  queryString.contains("v-r=init") || 
                  queryString.contains("v-r=push"))) {
                skipAuth = false;
            }
        }
        
        // Login and registration pages
        if (requestURI.contains("/login") || requestURI.contains("/register")) {
            skipAuth = true;
        }

        if (skipAuth) {
            chain.doFilter(request, response);
            return;
        }
     
        final String authorizationHeader = request.getHeader(jwtTokenUtil.getHeader());

        String username = null;
        String jwtToken = null;

        // Extract JWT token if authorization header exists and has correct format
        if (authorizationHeader != null && authorizationHeader.startsWith(jwtTokenUtil.getPrefix())) {
            jwtToken = authorizationHeader.substring(jwtTokenUtil.getPrefix().length()).trim();
            try {
                username = jwtTokenUtil.extractUsername(jwtToken);
            } catch (Exception e) {
                LoggingUtil.warn(LOG, "doFilterInternal", "Error extracting username from token: %s", e.getMessage());
            }
        } else {
            LoggingUtil.debug(LOG, "doFilterInternal", "No valid authentication header found");
        }

        // Validate token and set up authentication if needed
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (userDetailsService != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    LoggingUtil.debug(LOG, "doFilterInternal", "Authenticated user: %s", username);
                } else {
                    LoggingUtil.warn(LOG, "doFilterInternal", "Token validation failed for user: %s", username);
                }
            } else {
                LoggingUtil.warn(LOG, "doFilterInternal", "UserDetailsService not set, skipping authentication");
            }
        }

        chain.doFilter(request, response);
    }
}

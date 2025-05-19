package com.samjdtechnologies.answer42.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * Public endpoint that doesn't require authentication.
     * 
     * @return A ResponseEntity containing a message about the public nature of the endpoint
     */
    @GetMapping("/public")
    public ResponseEntity<?> publicEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint that doesn't require authentication");
        return ResponseEntity.ok(response);
    }

    /**
     * Secured endpoint that requires authentication.
     * Returns information about the authenticated user.
     * 
     * @return A ResponseEntity containing user authentication details and a message
     */
    @GetMapping("/secured")
    public ResponseEntity<?> securedEndpoint() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a secured endpoint that requires authentication");
        response.put("username", authentication.getName());
        response.put("authorities", authentication.getAuthorities());
        return ResponseEntity.ok(response);
    }
}

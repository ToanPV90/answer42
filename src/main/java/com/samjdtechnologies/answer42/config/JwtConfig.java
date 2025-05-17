package com.samjdtechnologies.answer42.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT authentication.
 * Provides centralized access to JWT-related settings.
 */
@Configuration
public class JwtConfig {

    @Value("${app.auth.jwt.header}")
    private String header;

    @Value("${app.auth.jwt.prefix}")
    private String prefix;

    @Value("${app.auth.jwt.expiration}")
    private long expiration;

    @Value("${app.auth.jwt.secret}")
    private String secret;

    // Getters
    public String getHeader() {
        return header;
    }

    public String getPrefix() {
        return prefix;
    }

    public long getExpiration() {
        return expiration;
    }

    public String getSecret() {
        return secret;
    }
}

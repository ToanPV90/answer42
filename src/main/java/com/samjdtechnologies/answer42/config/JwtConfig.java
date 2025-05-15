package com.samjdtechnologies.answer42.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${app.auth.jwt.secret}")
    private String secret;

    @Value("${app.auth.jwt.expiration:86400000}")
    private long expiration;

    @Value("${app.auth.jwt.header:Authorization}")
    private String header;

    @Value("${app.auth.jwt.prefix:Bearer }")
    private String prefix;

    public String getSecret() {
        return secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public String getHeader() {
        return header;
    }

    public String getPrefix() {
        return prefix;
    }
}

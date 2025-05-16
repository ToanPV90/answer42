package com.samjdtechnologies.answer42.config;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.samjdtechnologies.answer42.security.CustomUserDetailsService;
import com.samjdtechnologies.answer42.security.JwtAuthenticationFilter;
import com.samjdtechnologies.answer42.security.JwtTokenUtil;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtConfig jwtConfig;
    
    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Vaadin specific request paths that should be accessible without authentication
        String[] allowedPaths = {
            // Vaadin Flow static resources
            "/VAADIN/**",
            // Web Component UI static resources
            "/frontend/**",
            // Vaadin dev tools
            "/vaadin-dev-tools/**",
            // Push endpoint
            "/PUSH/**",
            // Vaadin endpoint
            "/connect/**",
            // WebJars
            "/webjars/**",
            // Client-side routing paths
            "/sw.js",
            "/sw-runtime-resources-precache.js",
            "/favicon.ico",
            "/favicon.svg",
            "/robots.txt",
            "/manifest.webmanifest",
            "/offline.html",
            "/offline-stub.html",
            "/*.js",
            "/*.css",
            "/*.svg",
            "/icons/**",
            "/images/**",
            "/static/**",
            // Our public API endpoints
            "/api/auth/**",
            "/api/test/public",
            // Login and registration pages
            "/" + UIConstants.ROUTE_LOGIN + "/**",
            "/" + UIConstants.ROUTE_REGISTER + "/**"
        };
        
        http
            .csrf(csrf -> csrf.disable()) // For API use
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(Stream.concat(
                    Stream.of(allowedPaths),
                    Stream.of("/public/**", "/login", "/")
                ).toArray(String[]::new)).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            ) 
            .sessionManagement(session -> session // VAADIN requires: IF_REQUIRED || ALWAYS
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenUtil(), jwtConfig);
    }
    
    @Bean
    public JwtTokenUtil jwtTokenUtil() {
        return new JwtTokenUtil(jwtConfig);
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}

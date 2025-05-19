package com.samjdtechnologies.answer42.config;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.samjdtechnologies.answer42.security.CustomUserDetailsService;
import com.samjdtechnologies.answer42.security.JwtAuthenticationFilter;
import com.samjdtechnologies.answer42.security.JwtTokenUtil;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.expiration}")
    private long jwtExpiration;

    @Value("${app.auth.jwt.header}")
    private String jwtHeader;

    @Value("${app.auth.jwt.prefix}")
    private String jwtPrefix;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    /**
     * Creates and configures the JWT token utility bean.
     * 
     * @return a configured JwtTokenUtil instance for JWT token generation and validation
     */
    @Bean
    public JwtTokenUtil jwtTokenUtil() {
        return new JwtTokenUtil(jwtSecret, jwtExpiration, jwtHeader, jwtPrefix);
    }

    /**
     * Creates a password encoder for securely hashing and verifying passwords.
     * 
     * @return a BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Creates and configures the DaoAuthenticationProvider with our CustomUserDetailsService.
     * This explicitly connects our UserDetailsService with the authentication process.
     * 
     * @return the configured DaoAuthenticationProvider instance
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        LoggingUtil.debug(LOG, "authenticationProvider", "Configured DaoAuthenticationProvider with CustomUserDetailsService");
        return provider;
    }

    /**
     * Creates an authentication manager that explicitly uses our DaoAuthenticationProvider.
     * This ensures our CustomUserDetailsService is properly utilized for authentication.
     * 
     * @return the configured AuthenticationManager instance
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider());
    }

    /**
     * Creates and configures the JWT authentication filter which processes 
     * incoming requests for JWT tokens.
     * 
     * @return a configured JwtAuthenticationFilter instance
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        LoggingUtil.debug(LOG, "jwtAuthenticationFilter", "Creating JwtAuthenticationFilter bean");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenUtil(), jwtConfig);
        filter.setUserDetailsService(customUserDetailsService);
        LoggingUtil.debug(LOG, "jwtAuthenticationFilter", "UserDetailsService set on JwtAuthenticationFilter");
        return filter;
    }

    /**
     * Configures the application's security filter chain with authentication rules,
     * JWT token processing, and defining public/protected endpoints.
     * 
     * @param http the HttpSecurity instance to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during security configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        LoggingUtil.debug(LOG, "securityFilterChain", "Configuring security filter chain");
        // Vaadin specific request paths that should be accessible without authentication
        String[] allowedPaths = {
            // Vaadin Flow static resources
            "/VAADIN/**",
            // Web Component UI static resources
            "/frontend/**",
            // Vaadin dev tools
            "/vaadin-dev-tools/**",
            // Push endpoints
            "/PUSH/**",
            "/HILLA/push/**",
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
            // Our public WEB endpoints
            "/" + UIConstants.ROUTE_PUBLIC + "/**",
            // Our public API endpoints
            "/" + UIConstants.ROUTE_API_AUTH + "/**",
            "/" + UIConstants.ROUTE_API_TEST_PUBLIC,
            // Login and registration pages
            "/" + UIConstants.ROUTE_LOGIN + "/**",
            "/" + UIConstants.ROUTE_REGISTER + "/**",
            // Heartbeat endpoint for session maintenance
            "/" + UIConstants.ROUTE_HEARTBEAT
        };
        // Disable CSRF as we're using JWT
        http.csrf(csrf -> csrf.disable())
            // Use stateless session management
            .sessionManagement(session -> session // VAADIN requires: IF_REQUIRED || ALWAYS
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS) 
                .invalidSessionUrl("/" + UIConstants.ROUTE_LOGIN))
            // Set our custom authentication manager
            .authenticationManager(authenticationManager())
            .formLogin(form -> form
                .loginPage("/" + UIConstants.ROUTE_LOGIN)
                .permitAll())
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/" + UIConstants.ROUTE_LOGIN))    
            // Allow CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()))
            // Configure authorization rules
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints
                .requestMatchers(allowedPaths).permitAll()
                // Allow Vaadin UI views that handle navigation
                .requestMatchers("/").permitAll()
                .requestMatchers("/?**").permitAll() // Support query parameters on root path
                .requestMatchers("/HILLA/**").permitAll() // All Hilla endpoints (includes push)
                .requestMatchers("/sw-runtime-resources-precache.js").permitAll()
                .requestMatchers("/manifest.webmanifest").permitAll()
                .requestMatchers("/icons/**").permitAll()
                .requestMatchers("/error").permitAll() // Error page
                // Require authentication for other endpoints
                .anyRequest().authenticated()
            )
            // Add JWT filter before processing requests
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures Cross-Origin Resource Sharing (CORS) settings for the application.
     * 
     * @return a CorsConfigurationSource with allowed origins, methods, and headers
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*")); // Adjust for production
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(jwtHeader));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

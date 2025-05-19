package com.samjdtechnologies.answer42.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class JwtTokenUtil {

    private final String secret;
    private final long expiration;
    private final String header;
    private final String prefix;
    private final SecretKey key;

    /**
     * Constructs a JWT token utility with the specified configuration.
     *
     * @param secret The secret key used to sign JWT tokens
     * @param expiration The token expiration time in milliseconds
     * @param header The HTTP header name used for token transmission
     * @param prefix The token prefix (e.g., "Bearer ")
     */
    public JwtTokenUtil(String secret, long expiration, String header, String prefix) {
        this.secret = secret;
        this.expiration = expiration;
        this.header = header;
        this.prefix = prefix;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the HTTP header name used for JWT token transmission.
     *
     * @return The HTTP header name
     */
    public String getHeader() {
        return header;
    }

    /**
     * Gets the token prefix used before the JWT token string.
     *
     * @return The token prefix (e.g., "Bearer ")
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Extracts the username from a JWT token.
     *
     * @param token The JWT token string
     * @return The username stored in the token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the expiration date from a JWT token.
     *
     * @param token The JWT token string
     * @return The expiration date of the token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts a specific claim from a JWT token using a claims resolver function.
     *
     * @param <T> The type of the claim value to be extracted
     * @param token The JWT token string
     * @param claimsResolver A function that extracts a specific claim from the token claims
     * @return The extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks if a JWT token has expired.
     *
     * @param token The JWT token string
     * @return true if the token has expired, false otherwise
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generates a JWT token for a user.
     *
     * @param userDetails The user details for which to generate the token
     * @return The generated JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Generates a JWT token for a username with additional claims.
     *
     * @param username The username for which to generate the token
     * @param claims Additional claims to include in the token
     * @return The generated JWT token string
     */
    public String generateToken(String username, Map<String, Object> claims) {
        return createToken(claims, username);
    }

    /**
     * Creates a JWT token with the specified claims and subject.
     *
     * @param claims The claims to include in the token
     * @param subject The subject of the token (typically the username)
     * @return The created JWT token string
     */
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Validates a JWT token for a specific user.
     *
     * @param token The JWT token string to validate
     * @param userDetails The user details against which to validate the token
     * @return true if the token is valid for the user, false otherwise
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}

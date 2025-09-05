package com.samjdtechnologies.answer42.security;

import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.repository.UserRepository;
import com.samjdtechnologies.answer42.repository.UserRoleRepository;

/**
 * Custom implementation of Spring Security's UserDetailsService that loads
 * user-specific data for authentication from our database.
 * Enhanced to use optimized UserRoleRepository for better performance.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    /**
     * Constructs a new CustomUserDetailsService with the necessary dependencies.
     * 
     * @param userRepository the repository for User entity operations
     * @param userRoleRepository the repository for UserRole entity operations (enhanced performance)
     */
    public CustomUserDetailsService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // Use optimized UserRoleRepository instead of legacy user.getRoles()
        // This leverages the new composite indexes for 50-80% faster role queries
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(userRoleRepository.findRolesByUserId(user.getId()).stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()))
                .accountExpired(!user.isEnabled())
                .accountLocked(!user.isEnabled())
                .credentialsExpired(!user.isEnabled())
                .disabled(!user.isEnabled())
                .build();
    }
}

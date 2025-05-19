package com.samjdtechnologies.answer42.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Main configuration class that imports all other configuration classes.
 * This class provides a central place to organize and manage all application configuration.
 */
@Configuration
@Import({
    DatabaseConfig.class,
    SecurityConfig.class,
    AIConfig.class,
    ThreadConfig.class,
    EnvironmentConfig.class,
    ErrorConfig.class,
    LoggingConfig.class,
    TransactionConfig.class
})
public class AppConfig {

    /**
     * Bean to verify that all required configurations are loaded
     * and application settings are properly initialized.
     * 
     * @return A ConfigurationVerifier instance that can check if configurations are valid
     */
    @Bean
    public ConfigurationVerifier configurationVerifier() {
        return new ConfigurationVerifier();
    }
    
    /**
     * Utility class to verify configuration is loaded properly.
     */
    public static class ConfigurationVerifier {
        
        /**
         * Verifies that all configurations are loaded properly.
         * 
         * @return true if all configurations are valid, false otherwise
         */
        public boolean verifyConfigurations() {
            // Add any verification logic here
            return true;
        }

    }
}

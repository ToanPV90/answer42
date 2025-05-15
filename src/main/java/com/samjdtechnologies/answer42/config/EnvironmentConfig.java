package com.samjdtechnologies.answer42.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

/**
 * Configuration class to load environment variables from .env file.
 * This ensures API keys are loaded from the .env file if not already
 * set in the environment.
 */
@Configuration
public class EnvironmentConfig {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfig.class);
    private final ConfigurableEnvironment environment;

    public EnvironmentConfig(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        File envFile = new File(".env");
        if (envFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(envFile)) {
                props.load(fis);
                logger.info("Loaded environment variables from .env file");
                
                // Only set properties that aren't already in the environment
                props.forEach((key, value) -> {
                    if (environment.getProperty(key.toString()) == null) {
                        System.setProperty(key.toString(), value.toString());
                    }
                });
                
                // Add as a property source with lower precedence than system environment
                environment.getPropertySources().addLast(new PropertiesPropertySource(".env", props));
                
            } catch (IOException e) {
                logger.error("Error loading .env file", e);
            }
        } else {
            logger.warn(".env file not found. Make sure environment variables are set through other means.");
        }
        
        // Log important environment variables (without revealing full values)
        checkAndLogEnvVar("OPENAI_API_KEY");
        checkAndLogEnvVar("ANTHROPIC_API_KEY");
    }
    
    private void checkAndLogEnvVar(String varName) {
        String value = environment.getProperty(varName);
        if (value != null && !value.isEmpty()) {
            String maskedValue = maskApiKey(value);
            logger.info("{} is set {}", varName, maskedValue);
        } else {
            logger.warn("{} is not set", varName);
        }
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "[INVALID_KEY]";
        }
        
        // Show only first 4 and last 4 characters
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}

package com.samjdtechnologies.answer42.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PostConstruct;

@Configuration
@PropertySource(value = "file:.env", ignoreResourceNotFound = true)
public class EnvironmentConfig {

    private static final Logger LOG = Logger.getLogger(EnvironmentConfig.class.getName());

    @Value("${ANTHROPIC_API_KEY:#{null}}")
    private String anthropicApiKey;

    @Value("${OPENAI_API_KEY:#{null}}")
    private String perplexityApiKey;

    @EventListener(ContextRefreshedEvent.class)
    public void logEnvironmentSetup() {
        LOG.info("Loaded environment variables from .env file");
        
        if (anthropicApiKey != null && !anthropicApiKey.isEmpty()) {
            String maskedKey = maskApiKey(anthropicApiKey);
            LOG.info("ANTHROPIC_API_KEY is set " + maskedKey);
        } else {
            LOG.warning("ANTHROPIC_API_KEY is not set");
        }
        
        if (perplexityApiKey != null && !perplexityApiKey.isEmpty()) {
            String maskedKey = maskApiKey(perplexityApiKey);
            LOG.info("OPENAI_API_KEY is set " + maskedKey);
        } else {
            LOG.warning("OPENAI_API_KEY is not set");
        }
    }

    @PostConstruct
    public void init() {
        // Load .env file manually if PropertySource wasn't able to
        if (anthropicApiKey == null && perplexityApiKey == null) {
            loadEnvironmentVariables();
        }
    }

    private void loadEnvironmentVariables() {
        try {
            Path dotEnvPath = Paths.get(".env");
            if (Files.exists(dotEnvPath)) {
                Map<String, String> env = new HashMap<>();
                try (Stream<String> lines = Files.lines(dotEnvPath)) {
                    lines.filter(line -> !line.startsWith("#") && line.contains("="))
                         .forEach(line -> {
                             String[] keyValue = line.split("=", 2);
                             if (keyValue.length == 2) {
                                 String key = keyValue[0].trim();
                                 String value = keyValue[1].trim();
                                 // Remove quotes if present
                                 if (value.startsWith("\"") && value.endsWith("\"") || 
                                     value.startsWith("'") && value.endsWith("'")) {
                                     value = value.substring(1, value.length() - 1);
                                 }
                                 env.put(key, value);
                             }
                         });
                }
                
                // Set as system properties if they don't exist already
                env.forEach((key, value) -> {
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                });
                
                LOG.info("Manually loaded " + env.size() + " environment variables from .env file");
            } else {
                LOG.warning(".env file not found at path: " + dotEnvPath.toAbsolutePath());
            }
        } catch (IOException e) {
            LOG.severe("Error loading .env file: " + e.getMessage());
        }
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "********";
        }
        
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
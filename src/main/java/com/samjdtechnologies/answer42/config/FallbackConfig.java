package com.samjdtechnologies.answer42.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Configuration class for fallback system settings.
 * Manages Ollama fallback integration properties and retry policies.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.fallback.enabled", havingValue = "true", matchIfMissing = true)
public class FallbackConfig {
    
    private static final Logger LOG = LoggerFactory.getLogger(FallbackConfig.class);
    
    @Value("${spring.ai.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    @Value("${spring.ai.fallback.retry-after-failures:3}")
    private int retryAfterFailures;
    
    @Value("${spring.ai.fallback.timeout-seconds:60}")
    private int timeoutSeconds;
    
    @Value("${spring.ai.fallback.connection-check-timeout:5000}")
    private int connectionCheckTimeout;
    
    @Value("${spring.ai.fallback.health-check-interval:30000}")
    private int healthCheckInterval;
    
    @Value("${spring.ai.ollama.enabled:true}")
    private boolean ollamaEnabled;
    
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.ollama.chat.options.model:llama3.1:8b}")
    private String ollamaModel;
    
    @Value("${spring.ai.ollama.timeout:30000}")
    private int ollamaTimeout;
    
    /**
     * Creates a retry template specifically configured for fallback operations.
     * This template defines how many times to retry operations before falling back to Ollama.
     * 
     * @return A configured RetryTemplate for fallback operations
     */
    @Bean("fallbackRetryTemplate")
    public RetryTemplate fallbackRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy
        RetryPolicy retryPolicy = new SimpleRetryPolicy(retryAfterFailures);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        LoggingUtil.info(LOG, "fallbackRetryTemplate", 
            "Configured fallback retry template with %d max attempts", retryAfterFailures);
        
        return retryTemplate;
    }
    
    /**
     * Checks if the fallback system is enabled.
     * 
     * @return true if fallback is enabled, false otherwise
     */
    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }
    
    /**
     * Gets the number of failures after which fallback should be triggered.
     * 
     * @return The retry threshold before fallback
     */
    public int getRetryAfterFailures() {
        return retryAfterFailures;
    }
    
    /**
     * Gets the timeout in seconds for fallback operations.
     * 
     * @return The timeout in seconds
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    /**
     * Gets the connection check timeout in milliseconds.
     * 
     * @return The connection check timeout in milliseconds
     */
    public int getConnectionCheckTimeout() {
        return connectionCheckTimeout;
    }
    
    /**
     * Gets the health check interval in milliseconds.
     * 
     * @return The health check interval in milliseconds
     */
    public int getHealthCheckInterval() {
        return healthCheckInterval;
    }
    
    /**
     * Checks if Ollama is enabled for fallback operations.
     * 
     * @return true if Ollama is enabled, false otherwise
     */
    public boolean isOllamaEnabled() {
        return ollamaEnabled;
    }
    
    /**
     * Gets the Ollama base URL.
     * 
     * @return The Ollama base URL
     */
    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }
    
    /**
     * Gets the Ollama model name.
     * 
     * @return The Ollama model name
     */
    public String getOllamaModel() {
        return ollamaModel;
    }
    
    /**
     * Gets the Ollama timeout in milliseconds.
     * 
     * @return The Ollama timeout in milliseconds
     */
    public int getOllamaTimeout() {
        return ollamaTimeout;
    }
    
    /**
     * Logs the current fallback configuration settings.
     * This is useful for debugging and monitoring.
     */
    public void logConfiguration() {
        LoggingUtil.info(LOG, "logConfiguration", 
            "Fallback Configuration - Enabled: %b, Retry After Failures: %d, " +
            "Timeout: %ds, Connection Check Timeout: %dms, Health Check Interval: %dms",
            fallbackEnabled, retryAfterFailures, timeoutSeconds, connectionCheckTimeout, healthCheckInterval);
        
        LoggingUtil.info(LOG, "logConfiguration", 
            "Ollama Configuration - Enabled: %b, Base URL: %s, Model: %s, Timeout: %dms",
            ollamaEnabled, ollamaBaseUrl, ollamaModel, ollamaTimeout);
    }
}

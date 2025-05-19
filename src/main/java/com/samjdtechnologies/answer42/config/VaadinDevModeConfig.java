package com.samjdtechnologies.answer42.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Vaadin development mode to prevent thread leaks.
 * This class configures a shutdown hook to clean up Vaadin resources properly.
 */
@Configuration
public class VaadinDevModeConfig {

    private static final Logger logger = LoggerFactory.getLogger(VaadinDevModeConfig.class);
    
    @Value("${vaadin.productionMode:false}")
    private boolean productionMode;
    
    /**
     * Creates an executor service for Vaadin development mode tasks
     * with a proper shutdown hook to clean up resources.
     * 
     * @return the executor service
     */
    @Bean(name = "vaadinDevModeExecutor", destroyMethod = "shutdownNow")
    public ExecutorService vaadinDevModeExecutor() {
        logger.info("Initializing Vaadin development mode executor");
        
        if (productionMode) {
            logger.info("Running in production mode, using minimal executor");
            return Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "vaadin-production-executor");
                thread.setDaemon(true);
                return thread;
            });
        }
        
        logger.info("Running in development mode, using development executor");
        ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "vaadin-dev-thread");
            thread.setDaemon(true);
            return thread;
        });
        
        // Register a shutdown hook to ensure the executor is properly shut down
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Vaadin development mode executor");
            executor.shutdownNow();
        }, "vaadin-shutdown-hook"));
        
        return executor;
    }
}

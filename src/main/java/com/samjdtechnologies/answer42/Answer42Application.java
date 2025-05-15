package com.samjdtechnologies.answer42;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main application class with additional configuration for proper thread management
 * and shutdown operations to prevent memory leaks.
 */
@SpringBootApplication
public class Answer42Application implements DisposableBean {
    
    private static final Logger logger = LoggerFactory.getLogger(Answer42Application.class);
    
    // Executor for application shutdown tasks
    private final ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();
    
    /**
     * Main method to start the application
     */
    public static void main(String[] args) {
        SpringApplication.run(Answer42Application.class, args);
    }
    
    /**
     * Cleanup and shutdown operations when the application is stopping.
     * This handles proper thread shutdown to prevent memory leaks.
     */
    @Override
    public void destroy() throws Exception {
        logger.info("Application shutting down, cleaning up resources...");
        
        // Interrupt any JNA Cleaner threads
        Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getName().contains("JNA") || 
                             thread.getName().contains("vaadin"))
            .forEach(thread -> {
                logger.info("Interrupting thread: {}", thread.getName());
                thread.interrupt();
            });
        
        // Use the System.gc() to help with cleaning up JNA resources
        System.gc();
        
        // Shutdown our executor
        shutdownExecutor.shutdown();
        if (!shutdownExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
            logger.warn("Executor did not terminate in the specified time.");
            shutdownExecutor.shutdownNow();
        }
        
        logger.info("Application shutdown complete");
    }
}

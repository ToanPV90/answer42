package com.samjdtechnologies.answer42.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class to manage thread lifecycle, particularly for graceful shutdown.
 * This helps prevent thread leaks like JNA Cleaner and Vaadin dev-server threads.
 */
@Configuration
public class VaadinThreadManagerConfig implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(VaadinThreadManagerConfig.class);
    
    // A dedicated executor for thread cleanup
    private final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "thread-cleanup-executor");
        thread.setDaemon(true);
        return thread;
    });
    
    // Scheduler to periodically check for leaked threads
    private final ScheduledExecutorService threadMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "thread-monitor-executor");
        thread.setDaemon(true);
        return thread;
    });
    
    @Autowired
    private Environment env;
    
    /**
     * Register a shutdown hook and start thread monitoring at application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Initializing thread management");
        
        // Register a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Executing application shutdown hook");
            cleanupLeakedThreads();
        }, "app-shutdown-hook"));
        
        // Start a periodic task to monitor for leaked threads
        boolean enableMonitoring = env.getProperty("vaadin.devmode.threads-debug", Boolean.class, false);
        if (enableMonitoring) {
            threadMonitor.scheduleWithFixedDelay(() -> {
                logPotentialLeaks();
            }, 60, 300, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Log any threads that might be potential memory leaks.
     */
    private void logPotentialLeaks() {
        Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> (thread.getName().contains("vaadin") || 
                              thread.getName().contains("JNA")) &&
                             !thread.isDaemon())
            .forEach(thread -> {
                logger.debug("Potential leak: Non-daemon thread still running: {}", thread.getName());
            });
    }
    
    /**
     * Clean up any leaked threads.
     */
    private void cleanupLeakedThreads() {
        Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> 
                thread.getName().contains("vaadin") || 
                thread.getName().contains("JNA"))
            .forEach(thread -> {
                logger.info("Interrupting thread during cleanup: {}", thread.getName());
                thread.interrupt();
            });
    }

    /**
     * Clean up resources when the Spring context is destroyed.
     */
    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down thread manager");
        
        // Clean up leaked threads
        cleanupLeakedThreads();
        
        // Shutdown our executors
        threadMonitor.shutdownNow();
        cleanupExecutor.shutdown();
        if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            cleanupExecutor.shutdownNow();
        }
        
        logger.info("Thread manager shutdown complete");
    }
}

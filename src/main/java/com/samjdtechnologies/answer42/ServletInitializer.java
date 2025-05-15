package com.samjdtechnologies.answer42;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * ServletInitializer that extends SpringBootServletInitializer and provides
 * additional configuration for graceful application shutdown to prevent memory leaks.
 */
@Configuration
public class ServletInitializer extends SpringBootServletInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ServletInitializer.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Answer42Application.class);
    }
    
    /**
     * Provides a custom ServletContextListener to handle application lifecycle events
     * like context initialization and destruction, ensuring proper cleanup of threads.
     */
    @Bean
    public ServletContextInitializer servletContextListenerInitializer() {
        return servletContext -> {
            servletContext.addListener(new ServletContextListener() {
                
                @Override
                public void contextInitialized(ServletContextEvent sce) {
                    logger.info("ServletContext initialized");
                }
                
                @Override
                public void contextDestroyed(ServletContextEvent sce) {
                    logger.info("ServletContext destroyed, cleaning up threads...");
                    
                    // Interrupt any lingering threads to help prevent memory leaks
                    Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> thread.getName().contains("JNA") || 
                                         thread.getName().contains("vaadin"))
                        .forEach(thread -> {
                            try {
                                logger.info("Interrupting thread from ServletContextListener: {}", thread.getName());
                                thread.interrupt();
                            } catch (Exception e) {
                                logger.warn("Error interrupting thread: {}", thread.getName(), e);
                            }
                        });
                    
                    // Run garbage collection to help clean up resources
                    System.gc();
                    
                    logger.info("Thread cleanup complete");
                }
            });
        };
    }
}

package com.samjdtechnologies.answer42.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

@Configuration
public class LoggingConfig {

    private static final Logger logger = LoggerFactory.getLogger(LoggingConfig.class);

    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    @Value("${spring.jpa.properties.hibernate.format_sql:false}")
    private boolean formatSql;

    /**
     * Configures the logging levels for different packages in the application.
     * This method sets up appropriate logging levels for application components
     * and third-party libraries based on the application's needs.
     * 
     * @return The configured LoggerContext with all logging levels applied
     */
    @Bean(name = "loggerContext")
    public LoggerContext configureLoggers() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Set up logging levels based on your application.properties
        Map<String, Level> loggingLevels = new HashMap<>();
        loggingLevels.put("com.samjdtechnologies.answer42", Level.DEBUG);
        loggingLevels.put("org.springframework.security", Level.INFO);
        
        // Respect the JPA_SHOW_SQL setting from application.properties/environment
        if (showSql) {
            loggingLevels.put("org.hibernate.SQL", Level.DEBUG);
            loggingLevels.put("org.hibernate.type.descriptor.sql.BasicBinder", Level.TRACE);
            logger.info("Hibernate SQL logging ENABLED (JPA_SHOW_SQL=true)");
        } else {
            // Comprehensive Hibernate SQL logging suppression
            loggingLevels.put("org.hibernate.SQL", Level.OFF);
            loggingLevels.put("org.hibernate.type.descriptor.sql.BasicBinder", Level.OFF);
            loggingLevels.put("org.hibernate.type.descriptor.sql", Level.OFF);
            loggingLevels.put("org.hibernate.engine.jdbc", Level.OFF);
            loggingLevels.put("org.hibernate.engine.jdbc.spi.SqlExceptionHelper", Level.OFF);
            loggingLevels.put("org.hibernate.engine.jdbc.batch.internal.BatchingBatch", Level.OFF);
            loggingLevels.put("org.hibernate.resource.jdbc", Level.OFF);
            logger.info("Hibernate SQL logging DISABLED (JPA_SHOW_SQL=false)");
        }
        
        loggingLevels.put("org.springframework.transaction", Level.INFO);
        loggingLevels.put("org.springframework.orm.jpa", Level.INFO);
        loggingLevels.put("com.zaxxer.hikari", Level.INFO);
        
        // Apply logging levels
        for (Map.Entry<String, Level> entry : loggingLevels.entrySet()) {
            ch.qos.logback.classic.Logger logbackLogger = loggerContext.getLogger(entry.getKey());
            logbackLogger.setLevel(entry.getValue());
            logger.info("Set logging level for {} to {}", entry.getKey(), entry.getValue());
        }

        // Return the configured LoggerContext
        return loggerContext;
    }
}

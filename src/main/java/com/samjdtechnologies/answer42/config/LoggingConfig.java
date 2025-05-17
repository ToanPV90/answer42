package com.samjdtechnologies.answer42.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

@Configuration
public class LoggingConfig {

    private static final Logger logger = LoggerFactory.getLogger(LoggingConfig.class);

    @Bean(name = "loggerContext")
    public LoggerContext configureLoggers() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Set up logging levels based on your application.properties
        Map<String, Level> loggingLevels = new HashMap<>();
        loggingLevels.put("com.samjdtechnologies.answer42", Level.DEBUG);
        loggingLevels.put("org.springframework.security", Level.INFO);
        loggingLevels.put("org.hibernate.SQL", Level.DEBUG);
        loggingLevels.put("org.hibernate.type.descriptor.sql.BasicBinder", Level.INFO);
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

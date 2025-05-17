package com.samjdtechnologies.answer42.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for logging throughout the application.
 * This centralizes logging configuration and provides consistent methods.
 */
public class LoggingUtil {

    /**
     * Get a logger for the specified class.
     * 
     * @param clazz The class to get a logger for
     * @return A configured logger
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Log a debug message with context information.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void debug(Logger logger, String method, String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] {}", method, message);
        }
    }
    
    /**
     * Log a debug message with context information and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object param) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] {}", method, String.format(message, param));
        }
    }
    
    /**
     * Log a debug message with context information and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object param1, Object param2) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] {}", method, String.format(message, param1, param2));
        }
    }
    
    /**
     * Log a debug message with context information and three parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     * @param param3 The third parameter to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object param1, Object param2, Object param3) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] {}", method, String.format(message, param1, param2, param3));
        }
    }
    
    /**
     * Log a debug message with context information and variable parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param params The parameters to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object... params) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] {}", method, String.format(message, params));
        }
    }
    
    /**
     * Log an info message with context information.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void info(Logger logger, String method, String message) {
        logger.info("[{}] {}", method, message);
    }
    
    /**
     * Log an info message with context information and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void info(Logger logger, String method, String message, Object param) {
        logger.info("[{}] {}", method, String.format(message, param));
    }
    
    /**
     * Log an info message with context information and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void info(Logger logger, String method, String message, Object param1, Object param2) {
        logger.info("[{}] {}", method, String.format(message, param1, param2));
    }
    
    /**
     * Log a warning message with context information.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void warn(Logger logger, String method, String message) {
        logger.warn("[{}] {}", method, message);
    }
    
    /**
     * Log a warning message with context information and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void warn(Logger logger, String method, String message, Object param) {
        logger.warn("[{}] {}", method, String.format(message, param));
    }
    
    /**
     * Log an error message with context information.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void error(Logger logger, String method, String message) {
        logger.error("[{}] {}", method, message);
    }
    
    /**
     * Log an error message with an exception and context information.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void error(Logger logger, String method, String message, Throwable throwable) {
        logger.error("[{}] {}", method, message, throwable);
    }
}

package com.samjdtechnologies.answer42.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced utility class for colorized logging throughout the application.
 * This centralizes logging configuration and provides consistent colored methods for better readability.
 */
public class LoggingUtil {

    // ANSI Color Codes for terminal output - only the ones actually used
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String DIM_WHITE = "\u001B[90m";
    
    // Bright colors - only the ones actually used
    private static final String BRIGHT_RED = "\u001B[91m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_BLUE = "\u001B[94m";
    private static final String BRIGHT_PURPLE = "\u001B[95m";
    private static final String BRIGHT_CYAN = "\u001B[96m";
    
    // Check if colors should be enabled (disable in tests or when ANSWER42_NO_COLORS is set)
    private static final boolean COLORS_ENABLED = !Boolean.getBoolean("ANSWER42_NO_COLORS") && 
                                                  !System.getProperty("java.awt.headless", "false").equals("true") &&
                                                  System.console() != null;
    
    /**
     * Apply color formatting if colors are enabled.
     */
    private static String colorize(String text, String color) {
        return COLORS_ENABLED ? color + text + RESET : text;
    }
    
    /**
     * Format method name with consistent styling.
     */
    private static String formatMethod(String method) {
        return colorize("[" + method + "]", BRIGHT_CYAN);
    }
    
    
    /**
     * Enhanced message formatting that detects and colorizes common patterns.
     */
    private static String enhanceMessage(String message) {
        if (!COLORS_ENABLED) {
            return message;
        }
        
        // Color agent types
        message = message.replaceAll("\\b(PAPER_PROCESSOR|CONTENT_SUMMARIZER|CONCEPT_EXPLAINER|METADATA_ENHANCER|QUALITY_CHECKER|CITATION_FORMATTER|PERPLEXITY_RESEARCHER|RELATED_PAPER_DISCOVERY)\\b", 
                                   BRIGHT_PURPLE + "$1" + RESET);
        
        // Color providers
        message = message.replaceAll("\\b(OPENAI)\\b", BRIGHT_GREEN + "$1" + RESET);
        message = message.replaceAll("\\b(ANTHROPIC)\\b", BRIGHT_YELLOW + "$1" + RESET);
        message = message.replaceAll("\\b(PERPLEXITY)\\b", BRIGHT_BLUE + "$1" + RESET);
        message = message.replaceAll("\\b(OLLAMA)\\b", CYAN + "$1" + RESET);
        
        // Color costs and tokens
        message = message.replaceAll("\\$([0-9]+\\.?[0-9]*)", BRIGHT_GREEN + "\\$$1" + RESET);
        message = message.replaceAll("\\b([0-9,]+)\\s+(tokens?|ms|attempts?)\\b", YELLOW + "$1 $2" + RESET);
        
        // Color success/failure indicators
        message = message.replaceAll("\\b(successful|completed|available|active)\\b", BRIGHT_GREEN + "$1" + RESET);
        message = message.replaceAll("\\b(failed|error|unavailable|timeout)\\b", BRIGHT_RED + "$1" + RESET);
        message = message.replaceAll("\\b(fallback|retry|circuit breaker)\\b", BRIGHT_YELLOW + "$1" + RESET);
        
        return message;
    }

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
     * Log a trace message with context information and color formatting.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void trace(Logger logger, String method, String message) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(message), DIM_WHITE));
        }
    }
    
    /**
     * Log a trace message with context information, color formatting, and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void trace(Logger logger, String method, String message, Object param) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param)), DIM_WHITE));
        }
    }
    
    /**
     * Log a trace message with context information, color formatting, and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void trace(Logger logger, String method, String message, Object param1, Object param2) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param1, param2)), DIM_WHITE));
        }
    }
    
    /**
     * Log a trace message with context information, color formatting, and three parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     * @param param3 The third parameter to include in the message
     */
    public static void trace(Logger logger, String method, String message, Object param1, Object param2, Object param3) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param1, param2, param3)), DIM_WHITE));
        }
    }
    
    /**
     * Log a trace message with context information, color formatting, and variable parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param params The parameters to include in the message
     */
    public static void trace(Logger logger, String method, String message, Object... params) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, params)), DIM_WHITE));
        }
    }
    
    /**
     * Log a trace message with context information, color formatting, and one String parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The string parameter to include in the message
     */
    public static void trace(Logger logger, String method, String message, String param) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param)), DIM_WHITE));
        }
    }
    
    /**
     * Log a trace message with context information, color formatting, and two String parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first string parameter to include in the message
     * @param param2 The second string parameter to include in the message
     */
    public static void trace(Logger logger, String method, String message, String param1, String param2) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param1, param2)), DIM_WHITE));
        }
    }
    
    /**
     * Log a debug message with context information and color formatting.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void debug(Logger logger, String method, String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(message), BLUE));
        }
    }
    
    /**
     * Log a debug message with context information, color formatting, and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object param) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param)), BLUE));
        }
    }
    
    /**
     * Log a debug message with context information, color formatting, and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object param1, Object param2) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param1, param2)), BLUE));
        }
    }
    
    /**
     * Log a debug message with context information, color formatting, and three parameters.
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
            logger.debug("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, param1, param2, param3)), BLUE));
        }
    }
    
    /**
     * Log a debug message with context information, color formatting, and variable parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param params The parameters to include in the message
     */
    public static void debug(Logger logger, String method, String message, Object... params) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", formatMethod(method), 
                        colorize(enhanceMessage(String.format(message, params)), BLUE));
        }
    }
    
    /**
     * Log an info message with context information and color formatting.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void info(Logger logger, String method, String message) {
        logger.info("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(message), GREEN));
    }
    
    /**
     * Log an info message with context information, color formatting, and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void info(Logger logger, String method, String message, Object param) {
        logger.info("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, param)), GREEN));
    }
    
    /**
     * Log an info message with context information, color formatting, and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void info(Logger logger, String method, String message, Object param1, Object param2) {
        logger.info("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, param1, param2)), GREEN));
    }
    
    /**
     * Log an info message with context information, color formatting, and variable parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param params The parameters to include in the message
     */
    public static void info(Logger logger, String method, String message, Object... params) {
        logger.info("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, params)), GREEN));
    }
    
    /**
     * Log a warning message with context information and color formatting.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void warn(Logger logger, String method, String message) {
        logger.warn("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(message), BRIGHT_YELLOW));
    }
    
    /**
     * Log a warning message with context information, color formatting, and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void warn(Logger logger, String method, String message, Object param) {
        logger.warn("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, param)), BRIGHT_YELLOW));
    }
    
    /**
     * Log a warning message with context information, color formatting, and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void warn(Logger logger, String method, String message, Object param1, Object param2) {
        logger.warn("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, param1, param2)), BRIGHT_YELLOW));
    }
    
    /**
     * Log a warning message with context information, color formatting, and three parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     * @param param3 The third parameter to include in the message
     */
    public static void warn(Logger logger, String method, String message, Object param1, Object param2, Object param3) {
        logger.warn("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, param1, param2, param3)), BRIGHT_YELLOW));
    }
    
    /**
     * Log a warning message with context information, color formatting, and variable parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param params The parameters to include in the message
     */
    public static void warn(Logger logger, String method, String message, Object... params) {
        logger.warn("{} {}", formatMethod(method), 
                    colorize(enhanceMessage(String.format(message, params)), BRIGHT_YELLOW));
    }
    
    /**
     * Log an error message with context information and color formatting.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     */
    public static void error(Logger logger, String method, String message) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(message), BRIGHT_RED));
    }
    
    /**
     * Log an error message with an exception, context information, and color formatting.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void error(Logger logger, String method, String message, Throwable throwable) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(message), BRIGHT_RED), throwable);
    }
    
    /**
     * Log an error message with context information, color formatting, and one parameter.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param The parameter to include in the message
     */
    public static void error(Logger logger, String method, String message, Object param) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(String.format(message, param)), BRIGHT_RED));
    }
    
    /**
     * Log an error message with context information, color formatting, and two parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     */
    public static void error(Logger logger, String method, String message, Object param1, Object param2) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(String.format(message, param1, param2)), BRIGHT_RED));
    }
    
    /**
     * Log an error message with context information, color formatting, and three parameters.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     * @param param3 The third parameter to include in the message
     */
    public static void error(Logger logger, String method, String message, Object param1, Object param2, Object param3) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(String.format(message, param1, param2, param3)), BRIGHT_RED));
    }
    
    /**
     * Log an error message with context information, color formatting, two parameters, and an exception.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     * @param throwable The exception to log
     */
    public static void error(Logger logger, String method, String message, Object param1, Object param2, Throwable throwable) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(String.format(message, param1, param2)), BRIGHT_RED), throwable);
    }
    
    /**
     * Log an error message with context information, color formatting, three parameters, and an exception.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param param1 The first parameter to include in the message
     * @param param2 The second parameter to include in the message
     * @param param3 The third parameter to include in the message
     * @param throwable The exception to log
     */
    public static void error(Logger logger, String method, String message, Object param1, Object param2, Object param3, Throwable throwable) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(String.format(message, param1, param2, param3)), BRIGHT_RED), throwable);
    }
    
    /**
     * Log an error message with context information, color formatting, variable parameters, and an exception.
     * 
     * @param logger The logger to use
     * @param method The method name for context
     * @param message The message to log
     * @param params The parameters to include in the message
     * @param throwable The exception to log
     */
    public static void error(Logger logger, String method, String message, Throwable throwable, Object... params) {
        logger.error("{} {}", formatMethod(method), 
                     colorize(enhanceMessage(String.format(message, params)), BRIGHT_RED), throwable);
    }
}

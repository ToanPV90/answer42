package com.samjdtechnologies.answer42.service.pipeline;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Circuit breaker implementation for agent failure protection.
 * Prevents cascade failures when agents become unavailable.
 */
@Component
public class AgentCircuitBreaker {
    
    private static final Logger LOG = LoggerFactory.getLogger(AgentCircuitBreaker.class);
    
    private static final int FAILURE_THRESHOLD = 5;
    private static final int SUCCESS_THRESHOLD = 3; // Require 3 consecutive successes to close circuit
    private static final Duration TIMEOUT_DURATION = Duration.ofMinutes(2);
    private static final Duration HALF_OPEN_TIMEOUT = Duration.ofSeconds(30);
    
    private final Map<AgentType, CircuitBreakerState> circuitStates = new ConcurrentHashMap<>();
    
    /**
     * Execute an operation with circuit breaker protection.
     */
    public <T> CompletableFuture<T> executeWithCircuitBreaker(
            AgentType agentType, 
            Supplier<CompletableFuture<T>> operation) {
        
        CircuitBreakerState state = circuitStates.computeIfAbsent(agentType, 
            k -> new CircuitBreakerState());
        
        if (state.isOpen()) {
            LoggingUtil.warn(LOG, "executeWithCircuitBreaker", 
                "Circuit breaker is OPEN for agent %s", agentType);
            return CompletableFuture.failedFuture(
                new CircuitBreakerOpenException("Agent " + agentType + " is unavailable"));
        }
        
        if (state.isHalfOpen() && !state.canAttemptCall()) {
            LoggingUtil.warn(LOG, "executeWithCircuitBreaker", 
                "Circuit breaker is HALF-OPEN but not ready for agent %s", agentType);
            return CompletableFuture.failedFuture(
                new CircuitBreakerOpenException("Agent " + agentType + " is in recovery"));
        }
        
        return operation.get()
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    state.recordFailure();
                    LoggingUtil.warn(LOG, "executeWithCircuitBreaker", 
                        "Recorded failure for agent %s: %s", agentType, throwable.getMessage());
                } else {
                    state.recordSuccess();
                    LoggingUtil.debug(LOG, "executeWithCircuitBreaker", 
                        "Recorded success for agent %s", agentType);
                }
            });
    }
    
    /**
     * Get the current state of an agent's circuit breaker.
     */
    public CircuitBreakerStatus getCircuitBreakerStatus(AgentType agentType) {
        CircuitBreakerState state = circuitStates.get(agentType);
        if (state == null) {
            return CircuitBreakerStatus.CLOSED;
        }
        
        if (state.isOpen()) {
            return CircuitBreakerStatus.OPEN;
        } else if (state.isHalfOpen()) {
            return CircuitBreakerStatus.HALF_OPEN;
        } else {
            return CircuitBreakerStatus.CLOSED;
        }
    }
    
    /**
     * Reset the circuit breaker for an agent (for manual recovery).
     */
    public void resetCircuitBreaker(AgentType agentType) {
        circuitStates.remove(agentType);
        LoggingUtil.info(LOG, "resetCircuitBreaker", 
            "Reset circuit breaker for agent %s", agentType);
    }
    
    /**
     * Internal state tracking for circuit breaker.
     */
    private static class CircuitBreakerState {
        private int failureCount = 0;
        private int successCount = 0;
        private ZonedDateTime lastFailureTime;
        private ZonedDateTime lastAttemptTime;
        private CircuitBreakerStatus status = CircuitBreakerStatus.CLOSED;
        
        public synchronized void recordFailure() {
            failureCount++;
            successCount = 0; // Reset success count on any failure
            lastFailureTime = ZonedDateTime.now();
            
            if (failureCount >= FAILURE_THRESHOLD) {
                status = CircuitBreakerStatus.OPEN;
            }
        }
        
        public synchronized void recordSuccess() {
            successCount++;
            
            if (status == CircuitBreakerStatus.HALF_OPEN) {
                // Require multiple consecutive successes to close circuit from half-open
                if (successCount >= SUCCESS_THRESHOLD) {
                    status = CircuitBreakerStatus.CLOSED;
                    failureCount = 0;
                    successCount = 0; // Reset success count
                }
            } else if (status == CircuitBreakerStatus.CLOSED) {
                // Reset failure count on successful operations
                failureCount = Math.max(0, failureCount - 1);
                // Reset success count in closed state since we're tracking consecutive failures
                successCount = 0;
            }
        }
        
        public synchronized boolean isOpen() {
            if (status == CircuitBreakerStatus.OPEN && shouldTransitionToHalfOpen()) {
                status = CircuitBreakerStatus.HALF_OPEN;
                return false;
            }
            return status == CircuitBreakerStatus.OPEN;
        }
        
        public synchronized boolean isHalfOpen() {
            return status == CircuitBreakerStatus.HALF_OPEN;
        }
        
        public synchronized boolean canAttemptCall() {
            if (status != CircuitBreakerStatus.HALF_OPEN) {
                return true;
            }
            
            ZonedDateTime now = ZonedDateTime.now();
            if (lastAttemptTime == null || 
                Duration.between(lastAttemptTime, now).compareTo(HALF_OPEN_TIMEOUT) > 0) {
                lastAttemptTime = now;
                return true;
            }
            
            return false;
        }
        
        private boolean shouldTransitionToHalfOpen() {
            if (lastFailureTime == null) {
                return false;
            }
            
            ZonedDateTime now = ZonedDateTime.now();
            return Duration.between(lastFailureTime, now).compareTo(TIMEOUT_DURATION) > 0;
        }
    }
    
    /**
     * Circuit breaker status enumeration.
     */
    public enum CircuitBreakerStatus {
        CLOSED,    // Normal operation
        OPEN,      // Blocking calls due to failures
        HALF_OPEN  // Testing if service has recovered
    }
    
    /**
     * Exception thrown when circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}

package com.samjdtechnologies.answer42.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samjdtechnologies.answer42.transaction.TransactionMonitor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Controller for testing and monitoring database and application status.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private TransactionMonitor transactionMonitor;
    
    /**
     * Simple health check endpoint.
     * 
     * @return ResponseEntity containing system health status information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }
    
    /**
     * Tests database connection.
     * 
     * @return ResponseEntity containing database connection status and timestamp information
     */
    @GetMapping("/db-status")
    public ResponseEntity<Map<String, Object>> dbStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Simple query to check database connectivity
            Object result = entityManager.createNativeQuery("SELECT current_timestamp").getSingleResult();
            status.put("status", "UP");
            status.put("timestamp", result.toString());
            status.put("message", "Database connection successful");
        } catch (Exception e) {
            logger.error("Database connection error", e);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Tests transaction management.
     * 
     * @return ResponseEntity containing transaction isolation level and status information
     */
    @GetMapping("/transaction-info")
    public ResponseEntity<Map<String, Object>> transactionInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            String isolationLevel = transactionMonitor.checkIsolationLevel();
            info.put("isolationLevel", isolationLevel);
            info.put("status", "OK");
        } catch (Exception e) {
            logger.error("Error checking transaction info", e);
            info.put("status", "ERROR");
            info.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * Runs a test transaction.
     * 
     * @return ResponseEntity containing the status and result of the test transaction execution
     */
    @PostMapping("/test-transaction")
    public ResponseEntity<Map<String, Object>> testTransaction() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            transactionMonitor.executeTestTransaction();
            result.put("status", "SUCCESS");
            result.put("message", "Transaction test completed successfully. Check server logs for details.");
        } catch (Exception e) {
            logger.error("Transaction test error", e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}

package com.samjdtechnologies.answer42.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Utility class to monitor and demonstrate transaction management in Spring.
 * This class provides methods to check transaction status and isolation level.
 */
@Component
public class TransactionMonitor {

    private static final Logger logger = LoggerFactory.getLogger(TransactionMonitor.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private TransactionTemplate readOnlyTransactionTemplate;
    
    @Autowired
    private TransactionTemplate writeTransactionTemplate;
    
    /**
     * Checks the current transaction isolation level.
     * Must be called within a transaction context.
     */
    @Transactional(readOnly = true)
    public String checkIsolationLevel() {
        logger.debug("Checking transaction isolation level");
        Query query = entityManager.createNativeQuery("SHOW TRANSACTION ISOLATION LEVEL");
        String isolationLevel = (String) query.getSingleResult();
        logger.info("Current transaction isolation level: {}", isolationLevel);
        return isolationLevel;
    }
    
    /**
     * Demonstrates transaction synchronization capabilities.
     * Shows how to hook into transaction lifecycle events.
     */
    @Transactional
    public void demonstrateTransactionSynchronization() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            logger.info("Transaction is active with name: {}", 
                    TransactionSynchronizationManager.getCurrentTransactionName());
            
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            logger.info("Before commit, readOnly={}", readOnly);
                        }
                        
                        @Override
                        public void afterCommit() {
                            logger.info("After commit");
                        }
                        
                        @Override
                        public void afterCompletion(int status) {
                            logger.info("After completion with status: {}", 
                                    statusToString(status));
                        }
                    });
        } else {
            logger.warn("No active transaction found");
        }
    }
    
    /**
     * Executes a test transaction using the programmatic transaction template.
     */
    public void executeTestTransaction() {
        writeTransactionTemplate.execute(status -> {
            logger.info("Starting programmatic transaction");
            
            // Check if we're actually in a transaction
            boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            logger.info("In transaction: {}", inTransaction);
            
            // Check isolation level
            String isolationLevel = checkIsolationLevel();
            logger.info("Transaction isolation level: {}", isolationLevel);
            
            // Register synchronization to log transaction completion
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCompletion(int status) {
                            logger.info("Transaction completed with status: {}", 
                                    statusToString(status));
                        }
                    });
            
            return null;
        });
    }
    
    private String statusToString(int status) {
        switch (status) {
            case TransactionSynchronizationAdapter.STATUS_COMMITTED:
                return "COMMITTED";
            case TransactionSynchronizationAdapter.STATUS_ROLLED_BACK:
                return "ROLLED_BACK";
            case TransactionSynchronizationAdapter.STATUS_UNKNOWN:
                return "UNKNOWN";
            default:
                return "INVALID(" + status + ")";
        }
    }
}
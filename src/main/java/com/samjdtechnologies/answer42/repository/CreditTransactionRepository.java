package com.samjdtechnologies.answer42.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.CreditTransaction;

/**
 * Repository for accessing credit transactions data.
 */
@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {
    
    /**
     * Find all transactions for a user ordered by creation date descending.
     * 
     * @param userId the user ID
     * @return list of credit transactions
     */
    List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Find transactions for a user within a specified date range.
     * 
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return list of credit transactions
     */
    @Query("SELECT ct FROM CreditTransaction ct WHERE ct.userId = :userId AND ct.createdAt BETWEEN :startDate AND :endDate ORDER BY ct.createdAt DESC")
    List<CreditTransaction> findByUserIdAndDateRange(
        @Param("userId") UUID userId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Count the number of transactions for a user.
     * 
     * @param userId the user ID
     * @return the count of transactions
     */
    long countByUserId(UUID userId);
    
    /**
     * Find transactions by transaction type for a user.
     * 
     * @param userId the user ID
     * @param transactionType the transaction type
     * @return list of credit transactions
     */
    List<CreditTransaction> findByUserIdAndTransactionTypeOrderByCreatedAtDesc(
        UUID userId, CreditTransaction.TransactionType transactionType
    );
    
    /**
     * Find the most recent transaction for a user.
     * 
     * @param userId the user ID
     * @return the most recent credit transaction, if any
     */
    @Query("SELECT ct FROM CreditTransaction ct WHERE ct.userId = :userId ORDER BY ct.createdAt DESC LIMIT 1")
    CreditTransaction findMostRecentByUserId(@Param("userId") UUID userId);
}

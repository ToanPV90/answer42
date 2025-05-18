package com.samjdtechnologies.answer42.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.CreditBalance;

/**
 * Repository for accessing credit balance data.
 */
@Repository
public interface CreditBalanceRepository extends JpaRepository<CreditBalance, UUID> {
    
    /**
     * Find the credit balance for a user.
     * 
     * @param userId the user ID
     * @return the credit balance, if any
     */
    Optional<CreditBalance> findByUserId(UUID userId);
    
    /**
     * Check if a user has a credit balance.
     * 
     * @param userId the user ID
     * @return true if the user has a credit balance, false otherwise
     */
    boolean existsByUserId(UUID userId);
    
    /**
     * Get the current credit balance amount for a user.
     * 
     * @param userId the user ID
     * @return the balance amount, or 0 if no balance record exists
     */
    @Query("SELECT COALESCE(cb.balance, 0) FROM CreditBalance cb WHERE cb.userId = :userId")
    Integer getCurrentBalanceAmount(@Param("userId") UUID userId);
    
    /**
     * Get the number of credits used in the current period for a user.
     * 
     * @param userId the user ID
     * @return the number of credits used, or 0 if no balance record exists
     */
    @Query("SELECT COALESCE(cb.usedThisPeriod, 0) FROM CreditBalance cb WHERE cb.userId = :userId")
    Integer getUsedThisPeriod(@Param("userId") UUID userId);
}

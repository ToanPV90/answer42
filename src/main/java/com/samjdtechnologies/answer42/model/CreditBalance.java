package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreditBalance represents a user's current credit balance and usage statistics.
 */
@Entity
@Table(name = "credit_balances", schema = "answer42")
@Data
@NoArgsConstructor
public class CreditBalance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "balance")
    private Integer balance;
    
    @Column(name = "used_this_period")
    private Integer usedThisPeriod;
    
    @Column(name = "next_reset_date")
    private LocalDateTime nextResetDate;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    
    /**
     * Constructor with required fields for creating a new credit balance.
     *
     * @param userId The ID of the user this balance belongs to
     * @param balance The initial credit balance
     * @param usedThisPeriod The number of credits used in the current period
     * @param nextResetDate The date when the usage counter will be reset
     */
    public CreditBalance(UUID userId, Integer balance, Integer usedThisPeriod, LocalDateTime nextResetDate) {
        this.userId = userId;
        this.balance = balance;
        this.usedThisPeriod = usedThisPeriod;
        this.nextResetDate = nextResetDate;
    }
    
    /**
     * Adds credits to the user's balance.
     * 
     * @param amount The number of credits to add
     */
    public void addCredits(int amount) {
        this.balance += amount;
    }
    
    /**
     * Deducts credits from the user's balance and updates the usage counter.
     * 
     * @param amount The number of credits to use
     */
    public void useCredits(int amount) {
        this.balance -= amount;
        this.usedThisPeriod += amount;
    }
    
    /**
     * Checks if the user has enough credits for an operation.
     * 
     * @param amount The number of credits needed
     * @return true if the user has enough credits, false otherwise
     */
    public boolean hasEnoughCredits(int amount) {
        return this.balance >= amount;
    }
    
    /**
     * Checks if the current billing/usage period has expired.
     * 
     * @return true if the current time is after the next reset date, false otherwise
     */
    public boolean isPeriodExpired() {
        return LocalDateTime.now().isAfter(nextResetDate);
    }

}

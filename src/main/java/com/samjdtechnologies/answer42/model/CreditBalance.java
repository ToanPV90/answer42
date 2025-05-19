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

/**
 * CreditBalance represents a user's current credit balance and usage statistics.
 */
@Entity
@Table(name = "credit_balances", schema = "answer42")
public class CreditBalance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private Integer balance;
    
    @Column(name = "used_this_period", nullable = false)
    private Integer usedThisPeriod;
    
    @Column(name = "next_reset_date", nullable = false)
    private LocalDateTime nextResetDate;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Default constructor for CreditBalance.
     * Required by JPA.
     */
    public CreditBalance() {
    }
    
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
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public Integer getBalance() {
        return balance;
    }
    
    public void setBalance(Integer balance) {
        this.balance = balance;
    }
    
    public Integer getUsedThisPeriod() {
        return usedThisPeriod;
    }
    
    public void setUsedThisPeriod(Integer usedThisPeriod) {
        this.usedThisPeriod = usedThisPeriod;
    }
    
    public LocalDateTime getNextResetDate() {
        return nextResetDate;
    }
    
    public void setNextResetDate(LocalDateTime nextResetDate) {
        this.nextResetDate = nextResetDate;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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

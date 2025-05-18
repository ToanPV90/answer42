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
    
    // Constructors
    public CreditBalance() {
    }
    
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
    
    // Utility methods
    public void addCredits(int amount) {
        this.balance += amount;
    }
    
    public void useCredits(int amount) {
        this.balance -= amount;
        this.usedThisPeriod += amount;
    }
    
    public boolean hasEnoughCredits(int amount) {
        return this.balance >= amount;
    }
    
    public boolean isPeriodExpired() {
        return LocalDateTime.now().isAfter(nextResetDate);
    }
}

package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Subscription represents a user's subscription to a specific plan.
 */
@Entity
@Table(name = "subscriptions", schema = "answer42")
public class Subscription {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @Column(name = "plan_id", nullable = false)
    private String planId;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    private SubscriptionPlan plan;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;
    
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;
    
    @Column(name = "payment_provider", nullable = false)
    private String paymentProvider;
    
    @Column(name = "payment_provider_id")
    private String paymentProviderId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors
    public Subscription() {
    }
    
    public Subscription(UUID userId, String planId, String status, 
                      LocalDateTime currentPeriodStart, LocalDateTime currentPeriodEnd,
                      String paymentProvider) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.planId = planId;
        this.status = status;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.paymentProvider = paymentProvider;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }
    
    public String getPlanId() {
        return planId;
    }
    
    public void setPlanId(String planId) {
        this.planId = planId;
    }
    
    public SubscriptionPlan getPlan() {
        return plan;
    }
    
    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
        if (plan != null) {
            this.planId = plan.getId();
        }
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getCurrentPeriodStart() {
        return currentPeriodStart;
    }
    
    public void setCurrentPeriodStart(LocalDateTime currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }
    
    public LocalDateTime getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
    
    public void setCurrentPeriodEnd(LocalDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }
    
    public String getPaymentProvider() {
        return paymentProvider;
    }
    
    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }
    
    public String getPaymentProviderId() {
        return paymentProviderId;
    }
    
    public void setPaymentProviderId(String paymentProviderId) {
        this.paymentProviderId = paymentProviderId;
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
    
    // Helper methods
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }
    
    public boolean isTrialing() {
        return "trialing".equalsIgnoreCase(status);
    }
    
    public boolean isCanceled() {
        return "canceled".equalsIgnoreCase(status);
    }
    
    public boolean isExpired() {
        return currentPeriodEnd != null && currentPeriodEnd.isBefore(LocalDateTime.now());
    }
    
    public boolean isRenewing() {
        return isActive() && !isExpired();
    }
}

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
    /**
     * Default constructor for Subscription.
     */
    public Subscription() {
    }
    
    /**
     * Creates a subscription with the specified details.
     * 
     * @param userId The unique identifier of the user who owns this subscription
     * @param planId The identifier of the subscription plan
     * @param status The status of the subscription (e.g., "active", "canceled")
     * @param currentPeriodStart The start date of the current subscription period
     * @param currentPeriodEnd The end date of the current subscription period
     * @param paymentProvider The payment provider used for this subscription
     */
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
    /**
     * Checks if this subscription is in an active state.
     * 
     * @return true if the status equals "active" (case-insensitive), false otherwise
     */
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if this subscription is in a trial period.
     * 
     * @return true if the status equals "trialing" (case-insensitive), false otherwise
     */
    public boolean isTrialing() {
        return "trialing".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if this subscription has been canceled.
     * 
     * @return true if the status equals "canceled" (case-insensitive), false otherwise
     */
    public boolean isCanceled() {
        return "canceled".equalsIgnoreCase(status);
    }
    
    /**
     * Checks if this subscription period has ended.
     * 
     * @return true if the current period end date is in the past, false otherwise
     */
    public boolean isExpired() {
        return currentPeriodEnd != null && currentPeriodEnd.isBefore(LocalDateTime.now());
    }
    
    /**
     * Checks if this subscription is active and will renew automatically.
     * 
     * @return true if the subscription is active and not expired, false otherwise
     */
    public boolean isRenewing() {
        return isActive() && !isExpired();
    }
}

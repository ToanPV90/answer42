package com.samjdtechnologies.answer42.model.db;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subscription represents a user's subscription to a specific plan.
 */
@Entity
@Table(name = "subscriptions", schema = "answer42")
@Data
@NoArgsConstructor 
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
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "current_period_start")
    private ZonedDateTime currentPeriodStart;
    
    @Column(name = "current_period_end")
    private ZonedDateTime currentPeriodEnd;
    
    @Column(name = "payment_provider", nullable = false)
    private String paymentProvider;
    
    @Column(name = "payment_provider_id")
    private String paymentProviderId;
    
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
    
    // Constructors
    
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
                      ZonedDateTime currentPeriodStart, ZonedDateTime currentPeriodEnd,
                      String paymentProvider) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.planId = planId;
        this.status = status;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.paymentProvider = paymentProvider;
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }
    
    /**
     * Updates the user for this subscription and synchronizes the userId.
     * 
     * @param user the user who owns this subscription
     */
    public void updateUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }
    
    /**
     * Updates the plan for this subscription and synchronizes the planId.
     * 
     * @param plan the subscription plan
     */
    public void updatePlan(SubscriptionPlan plan) {
        this.plan = plan;
        if (plan != null) {
            this.planId = plan.getId();
        }
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
        return currentPeriodEnd != null && currentPeriodEnd.isBefore(ZonedDateTime.now());
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

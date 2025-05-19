package com.samjdtechnologies.answer42.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SubscriptionPlan represents a subscription tier that users can subscribe to.
 */
@Entity
@Table(name = "subscription_plans", schema = "answer42")
public class SubscriptionPlan {
    
    public enum AITier {
        FREE,
        BASIC, 
        PRO,
        SCHOLAR
    }
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly;
    
    @Column(name = "price_annually", nullable = false)
    private BigDecimal priceAnnually;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> features;
    
    @Column(name = "base_credits", nullable = false)
    private Integer baseCredits;
    
    @Column(name = "rollover_limit", nullable = false)
    private Integer rolloverLimit;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_free")
    private Boolean isFree;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "default_ai_tier")
    private AITier defaultAITier;
    
    // Constructors
    /**
     * Default constructor for SubscriptionPlan.
     */
    public SubscriptionPlan() {
    }
    
    /**
     * Creates a subscription plan with the specified details.
     *
     * @param id The unique identifier for the subscription plan
     * @param name The name of the subscription plan
     * @param priceMonthly The monthly price of the subscription
     * @param priceAnnually The annual price of the subscription
     * @param baseCredits The base number of credits provided with the plan
     * @param rolloverLimit The maximum number of credits that can roll over
     * @param isFree Whether this is a free plan
     * @param defaultAITier The default AI tier associated with this plan
     */
    public SubscriptionPlan(String id, String name, BigDecimal priceMonthly, BigDecimal priceAnnually, 
                           Integer baseCredits, Integer rolloverLimit, Boolean isFree, AITier defaultAITier) {
        this.id = id;
        this.name = name;
        this.priceMonthly = priceMonthly;
        this.priceAnnually = priceAnnually;
        this.baseCredits = baseCredits;
        this.rolloverLimit = rolloverLimit;
        this.isFree = isFree;
        this.defaultAITier = defaultAITier;
        this.isActive = true;
    }
    
    // Getters and Setters
    /**
     * Gets the unique identifier for this subscription plan.
     * 
     * @return the subscription plan ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Sets the unique identifier for this subscription plan.
     * 
     * @param id the subscription plan ID to set
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Gets the name of this subscription plan.
     * 
     * @return the subscription plan name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the name of this subscription plan.
     * 
     * @param name the subscription plan name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Gets the description of this subscription plan.
     * 
     * @return the subscription plan description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Sets the description of this subscription plan.
     * 
     * @param description the subscription plan description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Gets the monthly price of this subscription plan.
     * 
     * @return the monthly price
     */
    public BigDecimal getPriceMonthly() {
        return priceMonthly;
    }
    
    /**
     * Sets the monthly price of this subscription plan.
     * 
     * @param priceMonthly the monthly price to set
     */
    public void setPriceMonthly(BigDecimal priceMonthly) {
        this.priceMonthly = priceMonthly;
    }
    
    /**
     * Gets the annual price of this subscription plan.
     * 
     * @return the annual price
     */
    public BigDecimal getPriceAnnually() {
        return priceAnnually;
    }
    
    /**
     * Sets the annual price of this subscription plan.
     * 
     * @param priceAnnually the annual price to set
     */
    public void setPriceAnnually(BigDecimal priceAnnually) {
        this.priceAnnually = priceAnnually;
    }
    
    /**
     * Gets the features included in this subscription plan.
     * 
     * @return map of feature names to their values/configurations
     */
    public Map<String, Object> getFeatures() {
        return features;
    }
    
    /**
     * Sets the features included in this subscription plan.
     * 
     * @param features map of feature names to their values/configurations
     */
    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }
    
    /**
     * Gets the base number of credits provided with this subscription plan.
     * 
     * @return the base number of credits
     */
    public Integer getBaseCredits() {
        return baseCredits;
    }
    
    /**
     * Sets the base number of credits provided with this subscription plan.
     * 
     * @param baseCredits the base number of credits to set
     */
    public void setBaseCredits(Integer baseCredits) {
        this.baseCredits = baseCredits;
    }
    
    /**
     * Gets the maximum number of credits that can roll over to the next billing period.
     * 
     * @return the rollover limit
     */
    public Integer getRolloverLimit() {
        return rolloverLimit;
    }
    
    /**
     * Sets the maximum number of credits that can roll over to the next billing period.
     * 
     * @param rolloverLimit the rollover limit to set
     */
    public void setRolloverLimit(Integer rolloverLimit) {
        this.rolloverLimit = rolloverLimit;
    }
    
    /**
     * Gets whether this subscription plan is currently active.
     * 
     * @return true if this plan is active, false otherwise
     */
    public Boolean getIsActive() {
        return isActive;
    }
    
    /**
     * Sets whether this subscription plan is currently active.
     * 
     * @param isActive true to make this plan active, false to deactivate it
     */
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    /**
     * Gets the creation timestamp of this subscription plan.
     * 
     * @return the timestamp when this plan was created
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Sets the creation timestamp of this subscription plan.
     * 
     * @param createdAt the timestamp to set as creation time
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Gets the last update timestamp of this subscription plan.
     * 
     * @return the timestamp when this plan was last updated
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Sets the last update timestamp of this subscription plan.
     * 
     * @param updatedAt the timestamp to set as the last update time
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Gets whether this subscription plan is free.
     * 
     * @return true if this is a free plan, false otherwise
     */
    public Boolean getIsFree() {
        return isFree;
    }
    
    /**
     * Sets whether this subscription plan is free.
     * 
     * @param isFree true to mark this as a free plan, false otherwise
     */
    public void setIsFree(Boolean isFree) {
        this.isFree = isFree;
    }
    
    /**
     * Gets the default AI tier for this subscription plan.
     * 
     * @return the default AI tier associated with this plan
     */
    public AITier getDefaultAITier() {
        return defaultAITier;
    }
    
    /**
     * Sets the default AI tier for this subscription plan.
     * 
     * @param defaultAITier the AI tier to set as default for this plan
     */
    public void setDefaultAITier(AITier defaultAITier) {
        this.defaultAITier = defaultAITier;
    }
    
    // Helper methods
    
    /**
     * Checks if this subscription plan is a free plan.
     * 
     * @return true if this plan is marked as free, false otherwise
     */
    public boolean isFree() {
        return Boolean.TRUE.equals(this.isFree);
    }
    
    /**
     * Checks if this subscription plan is currently active.
     * 
     * @return true if this plan is marked as active, false otherwise
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }
    
    /**
     * Calculates the discount percentage when paying annually instead of monthly.
     * 
     * @return the discount percentage as a decimal (e.g., 0.20 for 20% discount)
     */
    public BigDecimal getYearlyDiscount() {
        if (priceMonthly.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Calculate the yearly price if paying monthly
        BigDecimal yearlyPriceIfMonthly = priceMonthly.multiply(new BigDecimal("12"));
        // Calculate the discount amount
        BigDecimal discountAmount = yearlyPriceIfMonthly.subtract(priceAnnually);
        // Calculate the discount percentage
        return discountAmount.divide(yearlyPriceIfMonthly, 2, BigDecimal.ROUND_HALF_UP);
    }
}

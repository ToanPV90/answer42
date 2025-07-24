package com.samjdtechnologies.answer42.model.db;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SubscriptionPlan represents a subscription tier that users can subscribe to.
 */
@Entity
@Table(name = "subscription_plans", schema = "answer42")
@Data
@NoArgsConstructor
public class SubscriptionPlan {
    
    public enum AITier {
        FREE,
        BASIC, 
        PRO,
        SCHOLAR
    }
    
    @Id
    private String id;
    
    @Column(name = "name")
    private String name;
    
    private String description;
    
    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly;
    
    @Column(name = "price_annually", nullable = false)
    private BigDecimal priceAnnually;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private Map<String, Object> features;
    
    @Column(name = "base_credits", nullable = false)
    private Integer baseCredits;
    
    @Column(name = "rollover_limit", nullable = false)
    private Integer rolloverLimit;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
    
    @Column(name = "is_free")
    private Boolean isFree;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "default_ai_tier")
    private AITier defaultAITier;
    
    // Constructors
    
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
    
    // Helper methods
    
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
    public BigDecimal calculateYearlyDiscount() {
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

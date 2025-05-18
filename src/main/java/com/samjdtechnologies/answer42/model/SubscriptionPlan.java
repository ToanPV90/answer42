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
    public SubscriptionPlan() {
    }
    
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
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public BigDecimal getPriceMonthly() {
        return priceMonthly;
    }
    
    public void setPriceMonthly(BigDecimal priceMonthly) {
        this.priceMonthly = priceMonthly;
    }
    
    public BigDecimal getPriceAnnually() {
        return priceAnnually;
    }
    
    public void setPriceAnnually(BigDecimal priceAnnually) {
        this.priceAnnually = priceAnnually;
    }
    
    public Map<String, Object> getFeatures() {
        return features;
    }
    
    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }
    
    public Integer getBaseCredits() {
        return baseCredits;
    }
    
    public void setBaseCredits(Integer baseCredits) {
        this.baseCredits = baseCredits;
    }
    
    public Integer getRolloverLimit() {
        return rolloverLimit;
    }
    
    public void setRolloverLimit(Integer rolloverLimit) {
        this.rolloverLimit = rolloverLimit;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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
    
    public Boolean getIsFree() {
        return isFree;
    }
    
    public void setIsFree(Boolean isFree) {
        this.isFree = isFree;
    }
    
    public AITier getDefaultAITier() {
        return defaultAITier;
    }
    
    public void setDefaultAITier(AITier defaultAITier) {
        this.defaultAITier = defaultAITier;
    }
    
    // Helper methods
    
    public boolean isFree() {
        return Boolean.TRUE.equals(this.isFree);
    }
    
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }
    
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

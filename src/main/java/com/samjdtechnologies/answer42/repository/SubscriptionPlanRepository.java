package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.SubscriptionPlan;

/**
 * Repository for SubscriptionPlan entities.
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
    
    /**
     * Find a subscription plan by its id.
     * 
     * @param id the subscription plan id
     * @return an Optional containing the subscription plan, or empty if not found
     */
    Optional<SubscriptionPlan> findById(String id);
    
    /**
     * Find a subscription plan by its name.
     * 
     * @param name the subscription plan name
     * @return an Optional containing the subscription plan, or empty if not found
     */
    Optional<SubscriptionPlan> findByName(String name);
    
    /**
     * Find all active subscription plans.
     * 
     * @return a list of active subscription plans
     */
    List<SubscriptionPlan> findByIsActiveTrueOrderByPriceMonthlyAsc();
    
    /**
     * Find the free subscription plan.
     * 
     * @return an Optional containing the free subscription plan, or empty if not found
     */
    Optional<SubscriptionPlan> findByIsFreeTrue();
    
    /**
     * Find plans by AI tier.
     * 
     * @param tier the AI tier
     * @return a list of subscription plans with the specified AI tier
     */
    List<SubscriptionPlan> findByDefaultAITierAndIsActiveTrue(SubscriptionPlan.AITier tier);
    
    /**
     * Find all active paid subscription plans.
     * 
     * @return a list of active paid subscription plans
     */
    @Query("SELECT p FROM SubscriptionPlan p WHERE p.isActive = true AND p.isFree = false ORDER BY p.priceMonthly ASC")
    List<SubscriptionPlan> findAllActivePaidPlans();
}

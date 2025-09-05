package com.samjdtechnologies.answer42.service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.Subscription;
import com.samjdtechnologies.answer42.model.db.SubscriptionPlan;
import com.samjdtechnologies.answer42.repository.SubscriptionPlanRepository;
import com.samjdtechnologies.answer42.repository.SubscriptionRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing subscription plans and user subscriptions.
 */
@Service
public class SubscriptionService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionService.class);
    
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    
    /**
     * Constructs a new SubscriptionService with the necessary dependencies.
     * 
     * @param planRepository the repository for SubscriptionPlan entity operations
     * @param subscriptionRepository the repository for Subscription entity operations
     */
    public SubscriptionService(SubscriptionPlanRepository planRepository, 
                              SubscriptionRepository subscriptionRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
    }
    
    /**
     * Get all active subscription plans ordered by price.
     * 
     * @return a list of active subscription plans
     */
    public List<SubscriptionPlan> getAllPlans() {
        LoggingUtil.debug(LOG, "getAllPlans", "Fetching all active subscription plans");
        return planRepository.findByIsActiveTrueOrderByPriceMonthlyAsc();
    }
    
    /**
     * Get all active paid subscription plans.
     * 
     * @return a list of active paid subscription plans
     */
    public List<SubscriptionPlan> getPaidPlans() {
        LoggingUtil.debug(LOG, "getPaidPlans", "Fetching all active paid subscription plans");
        return planRepository.findAllActivePaidPlans();
    }
    
    /**
     * Get a subscription plan by ID.
     * 
     * @param planId the subscription plan ID
     * @return an Optional containing the subscription plan, or empty if not found
     */
    public Optional<SubscriptionPlan> getPlanById(String planId) {
        LoggingUtil.debug(LOG, "getPlanById", "Fetching subscription plan with ID: %s", planId);
        return planRepository.findById(planId);
    }
    
    /**
     * Get a subscription plan by name.
     * 
     * @param name the subscription plan name
     * @return an Optional containing the subscription plan, or empty if not found
     */
    public Optional<SubscriptionPlan> getPlanByName(String name) {
        LoggingUtil.debug(LOG, "getPlanByName", "Fetching subscription plan with name: %s", name);
        return planRepository.findByName(name);
    }
    
    /**
     * Get the free subscription plan.
     * 
     * @return an Optional containing the free subscription plan, or empty if not found
     */
    public Optional<SubscriptionPlan> getFreePlan() {
        LoggingUtil.debug(LOG, "getFreePlan", "Fetching free subscription plan");
        return planRepository.findByIsFreeTrue();
    }
    
    /**
     * Get a user's active subscription.
     * 
     * @param userId the user ID
     * @return an Optional containing the user's active subscription, or empty if none exists
     */
    public Optional<Subscription> getUserActiveSubscription(UUID userId) {
        LoggingUtil.debug(LOG, "getUserActiveSubscription", "Fetching active subscription for user ID: %s", userId);
        return subscriptionRepository.findActiveSubscriptionByUserId(userId);
    }
    
    /**
     * Get all subscriptions for a user.
     * 
     * @param userId the user ID
     * @return a list of the user's subscriptions
     */
    public List<Subscription> getUserSubscriptions(UUID userId) {
        LoggingUtil.debug(LOG, "getUserSubscriptions", "Fetching all subscriptions for user ID: %s", userId);
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Check if a user has an active subscription.
     * 
     * @param userId the user ID
     * @return true if the user has an active subscription, false otherwise
     */
    public boolean hasActiveSubscription(UUID userId) {
        LoggingUtil.debug(LOG, "hasActiveSubscription", "Checking if user ID: %s has active subscription", userId);
        return subscriptionRepository.hasActiveSubscription(userId);
    }
    
    /**
     * Get the current subscription plan for a user.
     * 
     * @param userId the user ID
     * @return an Optional containing the user's current subscription plan, or empty if none exists
     */
    public Optional<SubscriptionPlan> getUserCurrentPlan(UUID userId) {
        LoggingUtil.debug(LOG, "getUserCurrentPlan", "Getting current plan for user ID: %s", userId);
        
        // Get the user's active subscription
        Optional<Subscription> activeSubscription = getUserActiveSubscription(userId);
        
        if (activeSubscription.isPresent()) {
            // If the user has an active subscription, get the plan
            return planRepository.findById(activeSubscription.get().getPlanId());
        } else {
            // If the user doesn't have an active subscription, return the free plan
            return getFreePlan();
        }
    }
    
    /**
     * Create a new subscription for a user.
     * 
     * @param userId the user ID
     * @param planId the subscription plan ID
     * @param paymentProvider the payment provider
     * @param paymentProviderId the payment provider's subscription ID
     * @param isAnnual whether the subscription is annual
     * @return the created subscription
     */
    @Transactional
    public Subscription createSubscription(UUID userId, String planId, String paymentProvider, 
                                        String paymentProviderId, boolean isAnnual) {
        LoggingUtil.info(LOG, "createSubscription", 
            "Creating subscription for user ID: %s, plan ID: %s, payment provider: %s, annual: %s", 
            userId, planId, paymentProvider, isAnnual);
        
        // Set subscription period based on whether it's annual or monthly
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime periodEnd = isAnnual ? now.plusYears(1) : now.plusMonths(1);
        
        // Create the subscription
        Subscription subscription = new Subscription(
            userId, 
            planId, 
            "active", 
            now, 
            periodEnd, 
            paymentProvider
        );
        
        subscription.setPaymentProviderId(paymentProviderId);
        
        // Save and return the subscription
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Upgrade or downgrade a user's subscription.
     * 
     * @param subscriptionId the subscription ID
     * @param newPlanId the new subscription plan ID
     * @return the updated subscription
     */
    @Transactional
    public Subscription changePlan(UUID subscriptionId, String newPlanId) {
        LoggingUtil.info(LOG, "changePlan", 
            "Changing subscription ID: %s to plan ID: %s", subscriptionId, newPlanId);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        
        // Update the subscription
        subscription.setPlanId(newPlanId);
        subscription.setUpdatedAt(ZonedDateTime.now());
        
        // Save and return the updated subscription
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Cancel a user's subscription.
     * 
     * @param subscriptionId the subscription ID
     * @return the updated subscription
     */
    @Transactional
    public Subscription cancelSubscription(UUID subscriptionId) {
        LoggingUtil.info(LOG, "cancelSubscription", 
            "Canceling subscription ID: %s", subscriptionId);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        
        // Update the subscription
        subscription.setStatus("canceled");
        subscription.setUpdatedAt(ZonedDateTime.now());
        
        // Save and return the updated subscription
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Get the yearly discount percentage for a plan.
     * 
     * @param planId the subscription plan ID
     * @return the yearly discount percentage, or 0 if the plan is not found
     */
    public BigDecimal getYearlyDiscountPercentage(String planId) {
        LoggingUtil.debug(LOG, "getYearlyDiscountPercentage", 
            "Getting yearly discount percentage for plan ID: %s", planId);
        
        return getPlanById(planId)
            .map(SubscriptionPlan::calculateYearlyDiscount)
            .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Generate the default subscription plan features.
     * This is used to generate the default plan features when creating new plans.
     * 
     * @param planName the plan name
     * @return a map of features
     */
    public Map<String, Object> generateDefaultFeatures(String planName) {
        Map<String, Object> features = new HashMap<>();
        
        switch (planName.toLowerCase()) {
            case "free":
                features.put("monthlyCredits", 30);
                features.put("maxPaperUploads", 3);
                features.put("aiAnalysis", "basic");
                features.put("maxProjects", 1);
                features.put("support", "community");
                break;
                
            case "basic":
                features.put("monthlyCredits", 120);
                features.put("maxPaperUploads", 50);
                features.put("maxProjects", 5);
                features.put("creditsRollover", 20);
                features.put("aiTier", "standard");
                features.put("support", "email");
                features.put("offlineAccess", true);
                break;
                
            case "pro":
                features.put("monthlyCredits", 300);
                features.put("maxPaperUploads", 500);
                features.put("maxProjects", 20);
                features.put("creditsRollover", 50);
                features.put("aiTier", "premium");
                features.put("support", "priority");
                features.put("teamMembers", 3);
                features.put("advancedPdfTools", true);
                break;
                
            case "scholar":
                features.put("monthlyCredits", 300);
                features.put("maxPaperUploads", -1); // Unlimited
                features.put("maxProjects", 100);
                features.put("creditsRollover", 100);
                features.put("aiTier", "premium");
                features.put("support", "priority");
                features.put("teamMembers", 10);
                features.put("customIntegrations", true);
                features.put("dedicatedSupport", true);
                break;
                
            default:
                // Default features
                features.put("monthlyCredits", 50);
                features.put("maxPaperUploads", 10);
                features.put("aiAnalysis", "basic");
                features.put("maxProjects", 2);
                features.put("support", "email");
                break;
        }
        
        return features;
    }
    
    /**
     * Update an existing plan's features.
     * 
     * @param plan the subscription plan to update
     * @param features the new features map
     * @return the updated subscription plan
     */
    @Transactional
    public SubscriptionPlan updatePlanFeatures(SubscriptionPlan plan, Map<String, Object> features) {
        LoggingUtil.info(LOG, "updatePlanFeatures", 
            "Updating features for plan ID: %s", plan.getId());
        
        plan.setFeatures(features);
        plan.setUpdatedAt(ZonedDateTime.now());
        
        return planRepository.save(plan);
    }
}

package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.Subscription;

/**
 * Repository for Subscription entities.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    /**
     * Find the active subscription for a user.
     * 
     * @param userId the user ID
     * @return an Optional containing the active subscription, or empty if not found
     */
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId AND (s.status = 'active' OR s.status = 'trialing')")
    Optional<Subscription> findActiveSubscriptionByUserId(@Param("userId") UUID userId);
    
    /**
     * Find all subscriptions for a user.
     * 
     * @param userId the user ID
     * @return a list of subscriptions
     */
    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Find subscriptions by status.
     * 
     * @param status the subscription status
     * @return a list of subscriptions with the specified status
     */
    List<Subscription> findByStatus(String status);
    
    /**
     * Find subscriptions by plan ID.
     * 
     * @param planId the plan ID
     * @return a list of subscriptions for the specified plan
     */
    List<Subscription> findByPlanId(String planId);
    
    /**
     * Find subscriptions by payment provider.
     * 
     * @param paymentProvider the payment provider name
     * @return a list of subscriptions processed by the specified payment provider
     */
    List<Subscription> findByPaymentProvider(String paymentProvider);
    
    /**
     * Find subscription by payment provider's subscription ID.
     * 
     * @param paymentProviderId the payment provider's subscription ID
     * @return an Optional containing the subscription, or empty if not found
     */
    Optional<Subscription> findByPaymentProviderId(String paymentProviderId);
    
    /**
     * Count active subscriptions by plan ID.
     * 
     * @param planId the plan ID
     * @return the number of active subscriptions for the specified plan
     */
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.planId = :planId AND (s.status = 'active' OR s.status = 'trialing')")
    long countActiveSubscriptionsByPlanId(@Param("planId") String planId);
    
    /**
     * Check if a user has an active subscription.
     * 
     * @param userId the user ID
     * @return true if the user has an active subscription, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Subscription s WHERE s.userId = :userId AND (s.status = 'active' OR s.status = 'trialing')")
    boolean hasActiveSubscription(@Param("userId") UUID userId);
}

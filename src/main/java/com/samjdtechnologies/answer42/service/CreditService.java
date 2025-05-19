package com.samjdtechnologies.answer42.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.CreditBalance;
import com.samjdtechnologies.answer42.model.CreditTransaction;
import com.samjdtechnologies.answer42.model.CreditTransaction.OperationType;
import com.samjdtechnologies.answer42.model.CreditTransaction.TransactionType;
import com.samjdtechnologies.answer42.model.Subscription;
import com.samjdtechnologies.answer42.model.SubscriptionPlan;
import com.samjdtechnologies.answer42.repository.CreditBalanceRepository;
import com.samjdtechnologies.answer42.repository.CreditTransactionRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing credit operations.
 */
@Service
public class CreditService {
    
    private static final Logger LOG = LoggerFactory.getLogger(CreditService.class);
    
    private final CreditBalanceRepository balanceRepository;
    private final CreditTransactionRepository transactionRepository;
    private final SubscriptionService subscriptionService;
    
    /**
     * Constructs a new CreditService with the necessary dependencies.
     * 
     * @param balanceRepository the repository for CreditBalance entity operations
     * @param transactionRepository the repository for CreditTransaction entity operations
     * @param subscriptionService the service for subscription-related operations
     */
    public CreditService(
            CreditBalanceRepository balanceRepository,
            CreditTransactionRepository transactionRepository,
            SubscriptionService subscriptionService) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.subscriptionService = subscriptionService;
    }
    
    /**
     * Get the credit balance for a user.
     * 
     * @param userId the user ID
     * @return the credit balance
     */
    public CreditBalance getCreditBalance(UUID userId) {
        LoggingUtil.debug(LOG, "getCreditBalance", "Getting credit balance for user ID: %s", userId);
        
        return balanceRepository.findByUserId(userId)
                .orElseGet(() -> initializeUserBalance(userId));
    }
    
    /**
     * Initialize a new credit balance for a user.
     * 
     * @param userId the user ID
     * @return the new credit balance
     */
    @Transactional
    public CreditBalance initializeUserBalance(UUID userId) {
        LoggingUtil.info(LOG, "initializeUserBalance", "Initializing credit balance for user ID: %s", userId);
        
        // Check if user already has a balance
        if (balanceRepository.existsByUserId(userId)) {
            return balanceRepository.findByUserId(userId).get();
        }
        
        // Get the user's subscription plan to determine initial credits
        Optional<SubscriptionPlan> plan = subscriptionService.getUserCurrentPlan(userId);
        int initialCredits = plan.map(SubscriptionPlan::getBaseCredits).orElse(0);
        
        // Calculate next reset date (first day of next month)
        LocalDateTime nextResetDate = LocalDateTime.now()
                .plusMonths(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        
        // Create and save the balance
        CreditBalance balance = new CreditBalance(userId, initialCredits, 0, nextResetDate);
        CreditBalance savedBalance = balanceRepository.save(balance);
        
        // Record the initial credit transaction if credits > 0
        if (initialCredits > 0) {
            recordTransaction(
                userId,
                TransactionType.SUBSCRIPTION_RENEWAL,
                initialCredits,
                initialCredits,
                null,
                "Initial subscription credits",
                null
            );
        }
        
        return savedBalance;
    }
    
    /**
     * Record a credit transaction.
     * 
     * @param userId the user ID
     * @param type the transaction type
     * @param amount the transaction amount
     * @param balanceAfter the balance after the transaction
     * @param operationType the operation type (can be null)
     * @param description the transaction description
     * @param referenceId the reference ID (can be null)
     * @return the recorded transaction
     */
    @Transactional
    public CreditTransaction recordTransaction(
            UUID userId,
            TransactionType type,
            int amount,
            int balanceAfter,
            OperationType operationType,
            String description,
            String referenceId) {
        
        LoggingUtil.info(LOG, "recordTransaction", 
                "Recording transaction for user ID: %s, type: %s, amount: %d", 
                userId, type, amount);
        
        CreditTransaction transaction = new CreditTransaction(
            userId, type, amount, balanceAfter, operationType, description, referenceId
        );
        
        return transactionRepository.save(transaction);
    }
    
    /**
     * Add credits to a user's balance.
     * 
     * @param userId the user ID
     * @param amount the amount to add
     * @param type the transaction type
     * @param description the transaction description
     * @param referenceId the reference ID (can be null)
     * @return the updated credit balance
     * @throws IllegalArgumentException if amount is not positive
     */
    @Transactional
    public CreditBalance addCredits(
            UUID userId, 
            int amount, 
            TransactionType type, 
            String description,
            String referenceId) {
        
        LoggingUtil.info(LOG, "addCredits", 
                "Adding %d credits to user ID: %s, type: %s", 
                amount, userId, type);
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        
        // Get or create the user's credit balance
        CreditBalance balance = getCreditBalance(userId);
        
        // Add the credits
        balance.addCredits(amount);
        CreditBalance updatedBalance = balanceRepository.save(balance);
        
        // Record the transaction
        recordTransaction(
            userId, type, amount, balance.getBalance(), null, description, referenceId
        );
        
        return updatedBalance;
    }
    
    /**
     * Use credits for an operation.
     * 
     * @param userId the user ID
     * @param amount the amount to use
     * @param operationType the operation type
     * @param description the transaction description
     * @param referenceId the reference ID (can be null)
     * @return true if credits were successfully used, false if insufficient credits
     * @throws IllegalArgumentException if amount is not positive
     */
    @Transactional
    public boolean useCredits(
            UUID userId, 
            int amount, 
            OperationType operationType, 
            String description,
            String referenceId) {
        
        LoggingUtil.info(LOG, "useCredits", 
                "Using %d credits for user ID: %s, operation: %s", 
                amount, userId, operationType);
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        
        // Get the user's credit balance
        CreditBalance balance = getCreditBalance(userId);
        
        // Check if the user has enough credits
        if (!balance.hasEnoughCredits(amount)) {
            LoggingUtil.warn(LOG, "useCredits", 
                    "Insufficient credits for user ID: %s, required: %d, available: %d", 
                    userId, amount, balance.getBalance());
            return false;
        }
        
        // Use the credits
        balance.useCredits(amount);
        CreditBalance updatedBalance = balanceRepository.save(balance);
        
        // Record the transaction
        recordTransaction(
            userId, 
            TransactionType.valueOf(operationType.name()), 
            -amount, 
            updatedBalance.getBalance(), 
            operationType, 
            description, 
            referenceId
        );
        
        return true;
    }
    
    /**
     * Check if a user has enough credits for an operation.
     * 
     * @param userId the user ID
     * @param amount the amount to check
     * @return true if the user has enough credits, false otherwise
     */
    public boolean hasEnoughCredits(UUID userId, int amount) {
        LoggingUtil.debug(LOG, "hasEnoughCredits", 
                "Checking if user ID: %s has enough credits: %d", userId, amount);
        
        CreditBalance balance = getCreditBalance(userId);
        return balance.hasEnoughCredits(amount);
    }
    
    /**
     * Get the current credit balance amount for a user.
     * 
     * @param userId the user ID
     * @return the balance amount
     */
    public int getCurrentBalanceAmount(UUID userId) {
        LoggingUtil.debug(LOG, "getCurrentBalanceAmount", 
                "Getting current balance amount for user ID: %s", userId);
        
        return balanceRepository.getCurrentBalanceAmount(userId);
    }
    
    /**
     * Get the credit transactions for a user.
     * 
     * @param userId the user ID
     * @return the list of credit transactions
     */
    public List<CreditTransaction> getUserTransactions(UUID userId) {
        LoggingUtil.debug(LOG, "getUserTransactions", 
                "Getting transactions for user ID: %s", userId);
        
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get the credit transactions for a user within a date range.
     * 
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return the list of credit transactions
     */
    public List<CreditTransaction> getUserTransactionsInDateRange(
            UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
        
        LoggingUtil.debug(LOG, "getUserTransactionsInDateRange", 
                "Getting transactions for user ID: %s between %s and %s", 
                userId, startDate, endDate);
        
        return transactionRepository.findByUserIdAndDateRange(userId, startDate, endDate);
    }
    
    /**
     * Process monthly credit renewal for a user.
     * This should be called by a scheduled job at the beginning of each month.
     * 
     * @param userId the user ID
     * @return the updated credit balance
     */
    @Transactional
    public CreditBalance processMonthlyRenewal(UUID userId) {
        LoggingUtil.info(LOG, "processMonthlyRenewal", 
                "Processing monthly credit renewal for user ID: %s", userId);
        
        // Get the user's credit balance
        CreditBalance balance = getCreditBalance(userId);
        
        // Check if renewal is due
        if (!balance.isPeriodExpired()) {
            LoggingUtil.debug(LOG, "processMonthlyRenewal", 
                    "Renewal not yet due for user ID: %s, next reset: %s", 
                    userId, balance.getNextResetDate());
            return balance;
        }
        
        // Get the user's active subscription
        Optional<Subscription> subscription = subscriptionService.getUserActiveSubscription(userId);
        
        if (subscription.isEmpty()) {
            LoggingUtil.warn(LOG, "processMonthlyRenewal", 
                    "No active subscription for user ID: %s", userId);
            return balance;
        }
        
        // Get the subscription plan
        Optional<SubscriptionPlan> plan = subscriptionService.getPlanById(subscription.get().getPlanId());
        
        if (plan.isEmpty()) {
            LoggingUtil.warn(LOG, "processMonthlyRenewal", 
                    "No plan found for subscription ID: %s", subscription.get().getId());
            return balance;
        }
        
        // Calculate rollover amount based on plan limits
        int rolloverLimit = plan.get().getRolloverLimit() != null ? plan.get().getRolloverLimit() : 0;
        int rolloverAmount = Math.min(balance.getBalance(), rolloverLimit);
        
        // Calculate new balance: rollover + monthly credits
        int monthlyCredits = plan.get().getBaseCredits();
        int newBalance = rolloverAmount + monthlyCredits;
        
        // Update the balance
        balance.setBalance(newBalance);
        balance.setUsedThisPeriod(0);
        
        // Calculate next reset date (first day of next month)
        LocalDateTime nextResetDate = LocalDateTime.now()
                .plusMonths(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        
        balance.setNextResetDate(nextResetDate);
        CreditBalance updatedBalance = balanceRepository.save(balance);
        
        // Record the transaction for monthly credits
        recordTransaction(
            userId,
            TransactionType.SUBSCRIPTION_RENEWAL,
            monthlyCredits,
            updatedBalance.getBalance(),
            null,
            String.format("Monthly subscription credits (%s plan)", plan.get().getName()),
            subscription.get().getId().toString()
        );
        
        // Record the transaction for rollover if applicable
        if (rolloverAmount > 0) {
            recordTransaction(
                userId,
                TransactionType.ROLLOVER,
                rolloverAmount,
                updatedBalance.getBalance(),
                null,
                String.format("Credits rolled over from previous month (%d/%d)", 
                        rolloverAmount, rolloverLimit),
                subscription.get().getId().toString()
            );
        }
        
        return updatedBalance;
    }
}

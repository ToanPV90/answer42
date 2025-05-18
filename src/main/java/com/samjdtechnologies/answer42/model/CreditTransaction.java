package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * CreditTransaction represents a transaction involving user credits.
 */
@Entity
@Table(name = "credit_transactions", schema = "answer42")
public class CreditTransaction {
    
    public enum TransactionType {
        SUBSCRIPTION_RENEWAL, // Credits added from monthly subscription
        PURCHASE,            // Credits purchased
        PAPER_UPLOAD,        // Credits used for uploading/processing papers
        SUMMARY_GENERATION,  // Credits used for generating summaries
        STUDY_GUIDE,         // Credits used for creating study guides
        RESEARCH,            // Credits used for research with Perplexity
        MANUAL_ADJUSTMENT,   // Manual adjustment by admin
        ROLLOVER,            // Credits rolled over from previous month
        REFERRAL_BONUS,      // Credits earned from referrals
        PROMOTION            // Credits from promotions
    }
    
    public enum OperationType {
        PAPER_ANALYSIS,      // Operation for paper analysis
        RESEARCH_QUERY,      // Operation for research query
        AI_CHAT,             // Operation for AI chat
        SUMMARY_GENERATION,  // Operation for summary generation
        STUDY_GUIDE_CREATION, // Operation for study guide creation
        Q_AND_A_SESSION,     // Operation for Q&A session
        API_INTEGRATION      // Operation for API integration
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;
    
    @Column(nullable = false)
    private Integer amount;
    
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type")
    private OperationType operationType;
    
    @Column(nullable = false)
    private String description;
    
    @Column(name = "reference_id")
    private String referenceId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public CreditTransaction() {
    }
    
    public CreditTransaction(UUID userId, TransactionType transactionType, 
                          Integer amount, Integer balanceAfter, 
                          OperationType operationType, String description, 
                          String referenceId) {
        this.userId = userId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.operationType = operationType;
        this.description = description;
        this.referenceId = referenceId;
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
    
    public TransactionType getTransactionType() {
        return transactionType;
    }
    
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }
    
    public Integer getAmount() {
        return amount;
    }
    
    public void setAmount(Integer amount) {
        this.amount = amount;
    }
    
    public Integer getBalanceAfter() {
        return balanceAfter;
    }
    
    public void setBalanceAfter(Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }
    
    public OperationType getOperationType() {
        return operationType;
    }
    
    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getReferenceId() {
        return referenceId;
    }
    
    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

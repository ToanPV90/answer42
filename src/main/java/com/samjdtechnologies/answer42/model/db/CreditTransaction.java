package com.samjdtechnologies.answer42.model.db;

import java.time.ZonedDateTime;
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
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreditTransaction represents a transaction involving user credits.
 */
@Entity
@Table(name = "credit_transactions", schema = "answer42")
@Data
@NoArgsConstructor
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
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private TransactionType transactionType;
    
    @Column(name = "amount")
    private Integer amount;
    
    @Column(name = "balance_after")
    private Integer balanceAfter;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type")
    private OperationType operationType;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "reference_id")
    private String referenceId;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    
    
    /**
     * Constructor with all fields for creating a new credit transaction.
     * 
     * @param userId The ID of the user this transaction belongs to
     * @param transactionType The type of transaction (e.g., PURCHASE, SUBSCRIPTION_RENEWAL)
     * @param amount The amount of credits involved in the transaction
     * @param balanceAfter The user's credit balance after the transaction
     * @param operationType The type of operation this transaction is for
     * @param description A human-readable description of the transaction
     * @param referenceId An optional reference ID to link to other entities
     */
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
}

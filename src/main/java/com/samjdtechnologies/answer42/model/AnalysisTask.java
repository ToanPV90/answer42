package com.samjdtechnologies.answer42.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.samjdtechnologies.answer42.model.enums.AnalysisType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Entity representing an analysis task for a paper.
 * This tracks the status of analysis generation requests.
 */
@Entity
@Table(name = "analysis_tasks", schema = "answer42")
@Data
public class AnalysisTask {
    
    /**
     * Enum representing the status of an analysis task.
     */
    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "paper_id")
    private Paper paper;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type")
    private AnalysisType analysisType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    
    @OneToOne
    @JoinColumn(name = "task_id")
    private AnalysisResult result;
    
    /**
     * Constructor for creating a new pending analysis task.
     * 
     */
    public AnalysisTask(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new pending analysis task.
     * 
     * @param paper the paper to analyze
     * @param user the user requesting the analysis
     * @param analysisType the type of analysis to perform
     */
    public AnalysisTask(Paper paper, User user, AnalysisType analysisType) {
        this.paper = paper;
        this.user = user;
        this.analysisType = analysisType;
        this.status = Status.PENDING;
    }
    
    /**
     * Mark this task as processing.
     */
    public void markAsProcessing() {
        this.status = Status.PROCESSING;
    }
    
    /**
     * Mark this task as completed with the given result.
     * 
     * @param result the analysis result
     */
    public void markAsCompleted(AnalysisResult result) {
        this.status = Status.COMPLETED;
        this.result = result;
        this.completedAt = LocalDateTime.now();
    }
    
    /**
     * Mark this task as failed with the given error message.
     * 
     * @param errorMessage the error message
     */
    public void markAsFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
}

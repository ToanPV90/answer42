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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Entity representing an analysis task for a paper.
 * This tracks the status of analysis generation requests.
 */
@Entity
@Table(name = "analysis_tasks", schema = "answer42")
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
    private UUID id;
    
    @ManyToOne
    private Paper paper;
    
    @ManyToOne
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type")
    private AnalysisType analysisType;
    
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;
    
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text")
    private String errorMessage;
    
    @OneToOne
    private AnalysisResult result;
    
    /**
     * Default constructor for JPA.
     */
    public AnalysisTask() {
        // Required by JPA
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
        this.createdAt = LocalDateTime.now();
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
    
    // Getters and setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public Paper getPaper() {
        return paper;
    }
    
    public void setPaper(Paper paper) {
        this.paper = paper;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public AnalysisType getAnalysisType() {
        return analysisType;
    }
    
    public void setAnalysisType(AnalysisType analysisType) {
        this.analysisType = analysisType;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public AnalysisResult getResult() {
        return result;
    }
    
    public void setResult(AnalysisResult result) {
        this.result = result;
    }
    
    @Override
    public String toString() {
        return "AnalysisTask [id=" + id + ", paper=" + (paper != null ? paper.getId() : "null") + 
               ", user=" + (user != null ? user.getId() : "null") + 
               ", analysisType=" + analysisType + ", status=" + status + 
               ", createdAt=" + createdAt + ", completedAt=" + completedAt + "]";
    }
}

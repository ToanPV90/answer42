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
import jakarta.persistence.Table;

/**
 * Entity representing an AI analysis result for a paper.
 * This stores the results of analyses like Deep Summary, Methodology Analysis, etc.
 */
@Entity
@Table(name = "chat_analysis_results", schema = "answer42")
public class ChatAnalysisResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    private Paper paper;
    
    @Enumerated(EnumType.STRING)
    private AnalysisType analysisType;
    
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text")
    private String content;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime lastAccessedAt;
    
    @Column(nullable = false)
    private boolean isArchived = false;
    
    /**
     * Default constructor for JPA.
     */
    public ChatAnalysisResult() {
        // Required by JPA
    }
    
    /**
     * Constructor for creating a new analysis result.
     * 
     * @param paper the paper being analyzed
     * @param analysisType the type of analysis
     * @param content the content of the analysis
     */
    public ChatAnalysisResult(Paper paper, AnalysisType analysisType, String content) {
        this.paper = paper;
        this.analysisType = analysisType;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    /**
     * @return the id
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * @param id the id to set
     */
    public void setId(UUID id) {
        this.id = id;
    }
    
    /**
     * @return the paper
     */
    public Paper getPaper() {
        return paper;
    }
    
    /**
     * @param paper the paper to set
     */
    public void setPaper(Paper paper) {
        this.paper = paper;
    }
    
    /**
     * @return the analysisType
     */
    public AnalysisType getAnalysisType() {
        return analysisType;
    }
    
    /**
     * @param analysisType the analysisType to set
     */
    public void setAnalysisType(AnalysisType analysisType) {
        this.analysisType = analysisType;
    }
    
    /**
     * @return the content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * @param content the content to set
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * @return the createdAt
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * @param createdAt the createdAt to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * @return the lastAccessedAt
     */
    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }
    
    /**
     * @param lastAccessedAt the lastAccessedAt to set
     */
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
    
    /**
     * @return the isArchived
     */
    public boolean isArchived() {
        return isArchived;
    }
    
    /**
     * @param isArchived the isArchived to set
     */
    public void setArchived(boolean isArchived) {
        this.isArchived = isArchived;
    }
    
    /**
     * Update the last accessed time to now.
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "ChatAnalysisResult [id=" + id + ", paper=" + (paper != null ? paper.getId() : "null") + 
               ", analysisType=" + analysisType + ", createdAt=" + createdAt + 
               ", lastAccessedAt=" + lastAccessedAt + ", isArchived=" + isArchived + "]";
    }
}

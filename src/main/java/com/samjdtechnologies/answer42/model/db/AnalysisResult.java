package com.samjdtechnologies.answer42.model.db;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
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
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing an AI analysis result for a paper.
 * This stores the results of analyses like Deep Summary, Methodology Analysis, etc.
 */
@Entity
@Table(name = "analysis_results", schema = "answer42")
@Data
@NoArgsConstructor
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "paper_id")
    private Paper paper;
    
    @ManyToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type")
    private AnalysisType analysisType;
    
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content", columnDefinition = "text")
    private String content;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    
    @Column(name = "last_accessed_at")
    private ZonedDateTime lastAccessedAt = ZonedDateTime.now();
    
    @Column(name = "is_archived")
    private boolean isArchived = false;
    
    /**
     * Constructor for creating a new analysis result.
     * 
     * @param paper the paper being analyzed
     * @param task the analysis task that generated this result
     * @param user the user who requested the analysis
     * @param analysisType the type of analysis
     * @param content the content of the analysis
     */
    public AnalysisResult(Paper paper, AnalysisTask task, User user, AnalysisType analysisType, String content) {
        this.paper = paper;
        this.task = task;
        this.user = user;
        this.analysisType = analysisType;
        this.content = content;
    }
    
    /**
     * Update the last accessed time to now.
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = ZonedDateTime.now();
    }
}

package com.samjdtechnologies.answer42.model.db;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary entity representing different levels of paper summaries.
 * Stores brief, standard, and detailed summaries for papers.
 */
@Entity
@Table(name = "summaries", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Summary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @Column(name = "paper_id", insertable = false, updatable = false)
    private UUID paperId;

    @Column(name = "brief", columnDefinition = "TEXT")
    private String brief;

    @Column(name = "standard", columnDefinition = "TEXT")
    private String standard;

    @Column(name = "detailed", columnDefinition = "TEXT")
    private String detailed;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructor with paper ID
    public Summary(UUID paperId) {
        this.paperId = paperId;
    }

    /**
     * Get summary by type.
     */
    public String getSummaryByType(String summaryType) {
        return switch (summaryType.toLowerCase()) {
            case "brief" -> brief;
            case "standard" -> standard;
            case "detailed" -> detailed;
            case "content" -> content;
            default -> null;
        };
    }

    /**
     * Set summary by type.
     */
    public void setSummaryByType(String summaryType, String summaryContent) {
        switch (summaryType.toLowerCase()) {
            case "brief" -> setBrief(summaryContent);
            case "standard" -> setStandard(summaryContent);
            case "detailed" -> setDetailed(summaryContent);
            case "content" -> setContent(summaryContent);
        }
    }
}

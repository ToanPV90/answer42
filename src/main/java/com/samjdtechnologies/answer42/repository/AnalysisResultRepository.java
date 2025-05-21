package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.AnalysisResult;
import com.samjdtechnologies.answer42.model.enums.AnalysisType;

/**
 * Repository interface for {@link AnalysisResult} entities.
 * Provides methods to interact with the analysis_results table.
 */
@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {
    
    /**
     * Find analysis result by paper ID and analysis type.
     * 
     * @param paperId the ID of the paper
     * @param analysisType the type of analysis
     * @return an Optional containing the analysis result if found
     */
    Optional<AnalysisResult> findByPaperIdAndAnalysisType(UUID paperId, AnalysisType analysisType);
    
    /**
     * Find all analysis results for a specific paper.
     * 
     * @param paperId the ID of the paper
     * @return a list of analysis results
     */
    List<AnalysisResult> findByPaperId(UUID paperId);
    
    /**
     * Find all analysis results for a specific paper, ordered by last accessed time.
     * 
     * @param paperId the ID of the paper
     * @return a list of analysis results
     */
    @Query("SELECT a FROM AnalysisResult a WHERE a.paper.id = :paperId ORDER BY a.lastAccessedAt DESC")
    List<AnalysisResult> findByPaperIdOrderByLastAccessedAtDesc(@Param("paperId") UUID paperId);
    
    /**
     * Find all analysis results of a specific type.
     * 
     * @param analysisType the type of analysis
     * @return a list of analysis results
     */
    List<AnalysisResult> findByAnalysisType(AnalysisType analysisType);
    
    /**
     * Find all non-archived analysis results for a paper.
     * 
     * @param paperId the ID of the paper
     * @param isArchived whether the analysis is archived
     * @return a list of analysis results
     */
    List<AnalysisResult> findByPaperIdAndIsArchived(UUID paperId, boolean isArchived);
    
    /**
     * Count the number of analyses for a specific paper.
     * 
     * @param paperId the ID of the paper
     * @return the count of analyses
     */
    long countByPaperId(UUID paperId);
}

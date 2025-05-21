package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.AnalysisTask;
import com.samjdtechnologies.answer42.model.enums.AnalysisType;

/**
 * Repository interface for {@link AnalysisTask} entities.
 * Provides methods to interact with the analysis_tasks table.
 */
@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, UUID> {
    
    /**
     * Find a task by paper ID and analysis type.
     * 
     * @param paperId the ID of the paper
     * @param analysisType the type of analysis
     * @return an Optional containing the task if found
     */
    Optional<AnalysisTask> findByPaperIdAndAnalysisType(UUID paperId, AnalysisType analysisType);
    
    /**
     * Find all tasks for a specific paper.
     * 
     * @param paperId the ID of the paper
     * @return a list of tasks
     */
    List<AnalysisTask> findByPaperId(UUID paperId);
    
    /**
     * Find tasks by paper ID and status.
     * 
     * @param paperId the ID of the paper
     * @param status the status of the task
     * @return a list of tasks
     */
    List<AnalysisTask> findByPaperIdAndStatus(UUID paperId, AnalysisTask.Status status);
    
    /**
     * Find tasks by user ID.
     * 
     * @param userId the ID of the user
     * @return a list of tasks
     */
    List<AnalysisTask> findByUserId(UUID userId);
    
    /**
     * Find tasks by status.
     * 
     * @param status the status of the task
     * @return a list of tasks
     */
    List<AnalysisTask> findByStatus(AnalysisTask.Status status);
    
    /**
     * Count the number of tasks for a specific paper.
     * 
     * @param paperId the ID of the paper
     * @return the count of tasks
     */
    long countByPaperId(UUID paperId);
}

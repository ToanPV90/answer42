package com.samjdtechnologies.answer42.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.daos.AgentTask;

/**
 * Repository interface for AgentTask entities.
 * Provides database access methods for agent task management.
 */
@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, String> {

    // Basic queries
    List<AgentTask> findByUserId(UUID userId);
    List<AgentTask> findByAgentId(String agentId);
    List<AgentTask> findByStatus(String status);

    // Task coordination queries
    @Query("SELECT t FROM AgentTask t WHERE t.userId = :userId AND t.status = :status ORDER BY t.createdAt DESC")
    List<AgentTask> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);

    @Query("SELECT t FROM AgentTask t WHERE t.agentId = :agentId AND t.status IN :statuses ORDER BY t.createdAt ASC")
    List<AgentTask> findByAgentIdAndStatusIn(@Param("agentId") String agentId, @Param("statuses") List<String> statuses);

    @Query("SELECT t FROM AgentTask t WHERE t.createdAt < :cutoff AND t.status IN ('completed', 'failed')")
    List<AgentTask> findCompletedTasksOlderThan(@Param("cutoff") Instant cutoff);

    // Performance monitoring queries
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - started_at))) FROM answer42.tasks WHERE agent_id = :agentId AND status = 'completed' AND started_at IS NOT NULL AND completed_at IS NOT NULL", nativeQuery = true)
    Double getAverageProcessingTimeByAgent(@Param("agentId") String agentId);

    @Query("SELECT t.agentId, COUNT(t) FROM AgentTask t WHERE t.status = 'failed' AND t.createdAt > :since GROUP BY t.agentId")
    List<Object[]> getFailureCountsByAgent(@Param("since") Instant since);

    // Active task monitoring
    @Query("SELECT COUNT(t) FROM AgentTask t WHERE t.status IN ('pending', 'processing')")
    long countActiveTasks();

    @Query("SELECT t.agentId, COUNT(t) FROM AgentTask t WHERE t.status IN ('pending', 'processing') GROUP BY t.agentId")
    List<Object[]> getActiveTaskCountsByAgent();

    // Task correlation (useful for workflows)
    @Query("SELECT t FROM AgentTask t WHERE JSON_EXTRACT(t.input, '$.workflowId') = :workflowId ORDER BY t.createdAt ASC")
    List<AgentTask> findByWorkflowId(@Param("workflowId") String workflowId);

    @Query("SELECT t FROM AgentTask t WHERE JSON_EXTRACT(t.input, '$.paperId') = :paperId AND t.status = 'completed' ORDER BY t.createdAt DESC")
    List<AgentTask> findCompletedTasksForPaper(@Param("paperId") String paperId);

    // Cleanup operations
    @Modifying
    @Query("DELETE FROM AgentTask t WHERE t.createdAt < :cutoff AND t.status IN ('completed', 'failed')")
    int deleteCompletedTasksOlderThan(@Param("cutoff") Instant cutoff);

    // Task timeout detection
    @Query("SELECT t FROM AgentTask t WHERE t.status = 'processing' AND t.startedAt < :timeoutThreshold")
    List<AgentTask> findTimedOutTasks(@Param("timeoutThreshold") Instant timeoutThreshold);
}

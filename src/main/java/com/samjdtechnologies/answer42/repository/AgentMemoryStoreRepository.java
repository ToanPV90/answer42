package com.samjdtechnologies.answer42.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.daos.AgentMemoryStore;

/**
 * Repository interface for AgentMemoryStore entities.
 * Provides database access methods for agent memory management.
 */
@Repository
public interface AgentMemoryStoreRepository extends JpaRepository<AgentMemoryStore, String> {

    // Basic queries
    Optional<AgentMemoryStore> findByKey(String key);

    // Pattern-based queries for agent memory organization
    @Query("SELECT a FROM AgentMemoryStore a WHERE a.key LIKE :pattern ORDER BY a.updatedAt DESC")
    List<AgentMemoryStore> findByKeyPattern(@Param("pattern") String pattern);

    @Query("SELECT a FROM AgentMemoryStore a WHERE a.key LIKE :prefix% ORDER BY a.updatedAt DESC")
    List<AgentMemoryStore> findByKeyPrefix(@Param("prefix") String prefix);

    // Time-based queries for memory management
    @Query("SELECT a FROM AgentMemoryStore a WHERE a.updatedAt < :cutoff")
    List<AgentMemoryStore> findStaleEntries(@Param("cutoff") Instant cutoff);

    // Agent-specific memory queries
    default List<AgentMemoryStore> findUserAgentConfigs(UUID userId) {
        return findByKeyPattern("user_" + userId + "_agent_%");
    }

    default List<AgentMemoryStore> findAgentCaches(String agentType) {
        return findByKeyPrefix("agent_" + agentType + "_");
    }

    default List<AgentMemoryStore> findWorkflowStates() {
        return findByKeyPrefix("workflow_state_");
    }

    // Memory usage monitoring
    @Query("SELECT COUNT(a), SUM(LENGTH(CAST(a.data AS string))) FROM AgentMemoryStore a")
    Object[] getMemoryUsageStats();

    @Query("SELECT LEFT(a.key, LOCATE('_', a.key) - 1) as prefix, COUNT(a) FROM AgentMemoryStore a WHERE a.key LIKE '%_%' GROUP BY LEFT(a.key, LOCATE('_', a.key) - 1)")
    List<Object[]> getMemoryUsageByPrefix();

    // Bulk operations for memory cleanup
    @Modifying
    @Query("DELETE FROM AgentMemoryStore a WHERE a.updatedAt < :cutoff")
    int deleteStaleEntries(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM AgentMemoryStore a WHERE a.key LIKE :pattern")
    int deleteByKeyPattern(@Param("pattern") String pattern);

    // Processed papers specific queries
    default Optional<AgentMemoryStore> findProcessedPapersMemory() {
        return findByKey(AgentMemoryStore.buildProcessedPapersKey());
    }

    default Optional<AgentMemoryStore> findUserAgentConfig(UUID userId, String agentType) {
        return findByKey(AgentMemoryStore.buildUserAgentConfigKey(userId, agentType));
    }
}

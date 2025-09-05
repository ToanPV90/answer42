package com.samjdtechnologies.answer42.model.db;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for agent tasks in the multi-agent pipeline.
 * Maps to the tasks table in the answer42 schema.
 */
@Entity
@Table(name = "tasks", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTask {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", columnDefinition = "jsonb", nullable = false)
    private JsonNode input;

    @Column(name = "status", nullable = false)
    private String status; // pending, processing, completed, failed

    @Column(name = "error")
    private String error;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private JsonNode result;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Helper methods for task lifecycle management
    public void markStarted() {
        this.startedAt = Instant.now();
        this.status = "processing";
    }

    public void markCompleted(JsonNode result) {
        this.completedAt = Instant.now();
        this.result = result;
        this.status = "completed";
        this.error = null;
    }

    public void markFailed(String errorMessage) {
        this.completedAt = Instant.now();
        this.error = errorMessage;
        this.status = "failed";
    }

    public Duration getProcessingDuration() {
        if (startedAt != null && completedAt != null) {
            return Duration.between(startedAt, completedAt);
        }
        return Duration.ZERO;
    }

    public boolean isCompleted() {
        return "completed".equals(status) || "failed".equals(status);
    }

    public boolean isActive() {
        return "pending".equals(status) || "processing".equals(status);
    }

    // Static factory methods for common task types
    public static AgentTask createPaperProcessingTask(String taskId, UUID userId, String paperId) {
        return AgentTask.builder()
            .id(taskId)
            .agentId("paper-processor")
            .userId(userId)
            .input(JsonNodeFactory.instance.objectNode().put("paperId", paperId))
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    public static AgentTask createSummaryTask(String taskId, UUID userId, String paperId, String summaryType) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId);
        input.put("summaryType", summaryType);

        return AgentTask.builder()
            .id(taskId)
            .agentId("content-summarizer")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    public static AgentTask createCitationFormattingTask(String taskId, UUID userId, String paperId, List<String> styles) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId);
        input.set("citationStyles", JsonNodeFactory.instance.arrayNode().addAll(
            styles.stream().map(JsonNodeFactory.instance::textNode).collect(Collectors.toList())
        ));

        return AgentTask.builder()
            .id(taskId)
            .agentId("citation-formatter")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    public static AgentTask createRelatedPaperDiscoveryTask(String taskId, UUID userId, String paperId, String discoveryType) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId);
        input.put("discoveryType", discoveryType);

        return AgentTask.builder()
            .id(taskId)
            .agentId("related-paper-discovery")
            .userId(userId)
            .input(input)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }
}

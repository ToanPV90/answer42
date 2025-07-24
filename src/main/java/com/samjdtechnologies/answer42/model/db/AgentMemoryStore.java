package com.samjdtechnologies.answer42.model.daos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * JPA Entity for agent memory store in the multi-agent pipeline.
 * Maps to the agent_memory_store table in the answer42 schema.
 */
@Entity
@Table(name = "agent_memory_store", schema = "answer42")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMemoryStore {

    @Id
    @Column(name = "key", length = 255)
    private String key;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private JsonNode data;

    // Static factory methods for common memory keys
    public static String buildProcessedPapersKey() {
        return "processed_papers";
    }

    public static String buildUserAgentConfigKey(UUID userId, String agentType) {
        return String.format("user_%s_agent_%s_config", userId, agentType);
    }

    public static String buildAgentCacheKey(String agentType, String operation, String identifier) {
        return String.format("agent_%s_%s_%s", agentType, operation, identifier);
    }

    public static String buildWorkflowStateKey(String workflowId) {
        return String.format("workflow_state_%s", workflowId);
    }

    // Helper methods for common data operations
    public static AgentMemoryStore createProcessedPapersMemory(List<String> processedPaperIds) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        ArrayNode processed = JsonNodeFactory.instance.arrayNode();
        processedPaperIds.forEach(processed::add);
        data.set("processed", processed);

        return AgentMemoryStore.builder()
            .key(buildProcessedPapersKey())
            .data(data)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    public static AgentMemoryStore createUserAgentConfig(UUID userId, String agentType, Map<String, Object> config) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        config.forEach((key, value) -> {
            if (value instanceof String) {
                data.put(key, (String) value);
            } else if (value instanceof Integer) {
                data.put(key, (Integer) value);
            } else if (value instanceof Boolean) {
                data.put(key, (Boolean) value);
            } else if (value instanceof Double) {
                data.put(key, (Double) value);
            }
        });

        return AgentMemoryStore.builder()
            .key(buildUserAgentConfigKey(userId, agentType))
            .data(data)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    // Data access helpers
    public List<String> getProcessedPaperIds() {
        JsonNode processed = data.get("processed");
        if (processed != null && processed.isArray()) {
            return StreamSupport.stream(processed.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public void addProcessedPaperId(String paperId) {
        List<String> processed = getProcessedPaperIds();
        if (!processed.contains(paperId)) {
            processed.add(paperId);
            ObjectNode dataNode = (ObjectNode) data;
            ArrayNode processedArray = JsonNodeFactory.instance.arrayNode();
            processed.forEach(processedArray::add);
            dataNode.set("processed", processedArray);
            this.updatedAt = Instant.now();
        }
    }

    public boolean hasPaperBeenProcessed(String paperId) {
        return getProcessedPaperIds().contains(paperId);
    }

    public <T> T getConfigValue(String key, Class<T> type) {
        JsonNode value = data.get(key);
        if (value != null) {
            if (type == String.class) {
                return type.cast(value.asText());
            } else if (type == Integer.class) {
                return type.cast(value.asInt());
            } else if (type == Boolean.class) {
                return type.cast(value.asBoolean());
            } else if (type == Double.class) {
                return type.cast(value.asDouble());
            }
        }
        return null;
    }
}

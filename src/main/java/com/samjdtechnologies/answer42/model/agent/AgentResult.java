package com.samjdtechnologies.answer42.model.agent;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Builder;
import lombok.Data;

/**
 * Result wrapper for agent processing operations.
 * Contains both success/failure status and structured data.
 * Implements Serializable for Spring Batch compatibility.
 */
@Data
@Builder
public class AgentResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String taskId;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, Object> resultData;
    private final ProcessingMetrics metrics;
    private final Instant timestamp;
    private final Duration processingTime;
    
    // Fields for result metadata
    private boolean usedFallback;
    private String primaryFailureReason;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Create a successful result with data.
     */
    public static AgentResult success(String taskId, Object data) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("result", data);
        
        return AgentResult.builder()
            .taskId(taskId)
            .success(true)
            .resultData(resultMap)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a successful result with structured data map.
     */
    public static AgentResult success(String taskId, Map<String, Object> data) {
        return AgentResult.builder()
            .taskId(taskId)
            .success(true)
            .resultData(data)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a successful result with metrics.
     */
    public static AgentResult success(String taskId, Object data, ProcessingMetrics metrics) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("result", data);
        
        return AgentResult.builder()
            .taskId(taskId)
            .success(true)
            .resultData(resultMap)
            .metrics(metrics)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a failure result.
     */
    public static AgentResult failure(String taskId, String errorMessage) {
        return AgentResult.builder()
            .taskId(taskId)
            .success(false)
            .errorMessage(errorMessage)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a failure result from exception.
     */
    public static AgentResult failure(String taskId, Exception exception) {
        return failure(taskId, exception.getMessage());
    }
    
    /**
     * Create a skipped result (when processing is not needed).
     */
    public static AgentResult skipped(String taskId, String reason) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("skipped", true);
        resultMap.put("reason", reason);
        
        return AgentResult.builder()
            .taskId(taskId)
            .success(true)
            .resultData(resultMap)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create result from cached data.
     */
    public static AgentResult fromCachedResult(String taskId, JsonNode cachedData) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("result", cachedData);
        resultMap.put("fromCache", true);
        
        return AgentResult.builder()
            .taskId(taskId)
            .success(true)
            .resultData(resultMap)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a copy of this result with metrics added.
     */
    public AgentResult withMetrics(ProcessingMetrics metrics) {
        return AgentResult.builder()
            .taskId(this.taskId)
            .success(this.success)
            .errorMessage(this.errorMessage)
            .resultData(this.resultData)
            .metrics(metrics)
            .timestamp(this.timestamp)
            .processingTime(this.processingTime)
            .usedFallback(this.usedFallback)
            .primaryFailureReason(this.primaryFailureReason)
            .build();
    }
    
    /**
     * Check if result contains specific data key.
     */
    public boolean hasData(String key) {
        return resultData != null && resultData.containsKey(key);
    }
    
    /**
     * Get data value as string.
     */
    public String getDataAsString(String key) {
        Object value = resultData != null ? resultData.get(key) : null;
        return value != null ? value.toString() : null;
    }
    
    /**
     * Get data value as integer.
     */
    public Integer getDataAsInteger(String key) {
        Object value = resultData != null ? resultData.get(key) : null;
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
    
    /**
     * Get data value as double.
     */
    public Double getDataAsDouble(String key) {
        Object value = resultData != null ? resultData.get(key) : null;
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
    
    /**
     * Get data value as JsonNode.
     */
    public JsonNode getDataAsJsonNode(String key) {
        Object value = resultData != null ? resultData.get(key) : null;
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
    }
    
    /**
     * Convert entire result to JsonNode for storage.
     */
    public JsonNode toJsonNode() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("taskId", taskId);
        node.put("success", success);
        node.put("timestamp", timestamp.toString());
        
        if (errorMessage != null) {
            node.put("errorMessage", errorMessage);
        }
        
        if (resultData != null) {
            node.set("data", objectMapper.valueToTree(resultData));
        }
        
        if (metrics != null) {
            node.set("metrics", objectMapper.valueToTree(metrics));
        }
        
        return node;
    }
}

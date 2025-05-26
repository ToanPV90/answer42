package com.samjdtechnologies.answer42.model.pipeline;

import java.time.ZonedDateTime;
import java.util.Map;

import com.samjdtechnologies.answer42.model.enums.StageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a pipeline stage execution.
 * Contains the output data and metadata from stage processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageResult {
    
    private StageType stageType;
    private boolean success;
    private String errorMessage;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private Map<String, Object> resultData;
    private Map<String, Object> metrics;
    
    /**
     * Create a successful stage result.
     */
    public static StageResult success(StageType stageType, Map<String, Object> resultData) {
        return StageResult.builder()
            .stageType(stageType)
            .success(true)
            .resultData(resultData)
            .endTime(ZonedDateTime.now())
            .build();
    }
    
    /**
     * Create a failed stage result.
     */
    public static StageResult failure(StageType stageType, String errorMessage) {
        return StageResult.builder()
            .stageType(stageType)
            .success(false)
            .errorMessage(errorMessage)
            .endTime(ZonedDateTime.now())
            .build();
    }
    
    /**
     * Create a skipped stage result.
     */
    public static StageResult skipped(StageType stageType, String reason) {
        return StageResult.builder()
            .stageType(stageType)
            .success(true)
            .errorMessage("Skipped: " + reason)
            .endTime(ZonedDateTime.now())
            .build();
    }
    
    /**
     * Get a specific result value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getResultValue(String key, Class<T> type) {
        if (resultData == null) {
            return null;
        }
        Object value = resultData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Add result data.
     */
    public void addResultData(String key, Object value) {
        if (resultData == null) {
            resultData = new java.util.HashMap<>();
        }
        resultData.put(key, value);
    }
    
    /**
     * Add metric data.
     */
    public void addMetric(String key, Object value) {
        if (metrics == null) {
            metrics = new java.util.HashMap<>();
        }
        metrics.put(key, value);
    }
}

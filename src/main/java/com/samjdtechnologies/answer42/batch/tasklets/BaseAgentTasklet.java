package com.samjdtechnologies.answer42.batch.tasklets;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;

import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Base class for all agent tasklets providing common parameter extraction and utilities.
 * Eliminates boilerplate code and ensures consistent parameter handling across all tasklets.
 */
public abstract class BaseAgentTasklet implements Tasklet {
    
    protected static final Logger LOG = LoggerFactory.getLogger(BaseAgentTasklet.class);
    
    /**
     * Extracts user ID from job parameters (required for AIConfig integration and cost tracking).
     */
    protected UUID getUserId(ChunkContext chunkContext) {
        try {
            // Strategy 1: Job parameters
            Object userIdParam = chunkContext.getStepContext().getJobParameters().get("userId");
            if (userIdParam != null) {
                String userIdStr = userIdParam.toString().trim();
                if (!userIdStr.isEmpty()) {
                    return UUID.fromString(userIdStr);
                }
            }

            // Strategy 2: Alternative parameter names
            String[] userParamNames = {"user", "userGuid", "user_id"};
            for (String paramName : userParamNames) {
                Object param = chunkContext.getStepContext().getJobParameters().get(paramName);
                if (param != null) {
                    String paramStr = param.toString().trim();
                    if (!paramStr.isEmpty()) {
                        return UUID.fromString(paramStr);
                    }
                }
            }

            // Strategy 3: Execution context
            Object userIdFromContext = chunkContext.getStepContext()
                .getStepExecution().getJobExecution().getExecutionContext()
                .get("userId");
            
            if (userIdFromContext instanceof String) {
                return UUID.fromString((String) userIdFromContext);
            }
            if (userIdFromContext instanceof UUID) {
                return (UUID) userIdFromContext;
            }

            throw new IllegalArgumentException(
                "Required parameter 'userId' is missing. " +
                "userId is mandatory for AIConfig integration, cost tracking, and audit trail.");

        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            throw new IllegalArgumentException("Error extracting user ID: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts paper ID from job parameters with validation.
     */
    protected UUID getPaperId(ChunkContext chunkContext) {
        try {
            // Strategy 1: Direct job parameter
            Object paperIdParam = chunkContext.getStepContext().getJobParameters().get("paperId");
            if (paperIdParam != null) {
                String paperIdStr = paperIdParam.toString().trim();
                if (!paperIdStr.isEmpty()) {
                    return UUID.fromString(paperIdStr);
                }
            }

            // Strategy 2: Alternative parameter names
            String[] paperParamNames = {"paper", "paperGuid", "paper_id", "documentId"};
            for (String paramName : paperParamNames) {
                Object param = chunkContext.getStepContext().getJobParameters().get(paramName);
                if (param != null) {
                    String paramStr = param.toString().trim();
                    if (!paramStr.isEmpty()) {
                        return UUID.fromString(paramStr);
                    }
                }
            }

            // Strategy 3: Execution context
            Object paperIdFromContext = chunkContext.getStepContext()
                .getStepExecution().getJobExecution().getExecutionContext()
                .get("paperId");
            
            if (paperIdFromContext instanceof String) {
                return UUID.fromString((String) paperIdFromContext);
            }
            if (paperIdFromContext instanceof UUID) {
                return (UUID) paperIdFromContext;
            }

            throw new IllegalArgumentException("Paper ID is required but not found in job parameters");

        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            throw new IllegalArgumentException("Error extracting paper ID: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets previous step result from execution context.
     */
    protected AgentResult getPreviousStepResult(ChunkContext chunkContext, String resultKey) {
        try {
            Object result = chunkContext.getStepContext()
                .getStepExecution().getJobExecution().getExecutionContext()
                .get(resultKey);
                
            if (result instanceof AgentResult) {
                return (AgentResult) result;
            }
            
            LoggingUtil.warn(LOG, "getPreviousStepResult", 
                "No result found for key: %s", resultKey);
            return null;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getPreviousStepResult", 
                "Error getting previous step result for key %s", e, resultKey);
            return null;
        }
    }
    
    /**
     * Stores result in execution context for next steps.
     */
    protected void storeStepResult(ChunkContext chunkContext, String resultKey, AgentResult result) {
        try {
            chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext()
                .put(resultKey, result);
                
            LoggingUtil.debug(LOG, "storeStepResult", 
                "Stored result with key: %s", resultKey);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "storeStepResult", 
                "Error storing step result for key %s", e, resultKey);
        }
    }
    
    /**
     * Creates standard failure result and stores it in context.
     */
    protected void handleTaskletFailure(ChunkContext chunkContext, String taskletName, 
                                      String resultKey, Exception error) {
        LoggingUtil.error(LOG, "handleTaskletFailure", 
            "%s failed: %s", error, taskletName, error.getMessage());
        
        AgentResult failureResult = AgentResult.failure(taskletName + "_batch", error.getMessage());
        storeStepResult(chunkContext, resultKey, failureResult);
    }
    
    /**
     * Logs processing completion with timing.
     */
    protected void logProcessingComplete(String taskletName, UUID paperId, Instant startTime) {
        Duration processingTime = Duration.between(startTime, Instant.now());
        LoggingUtil.info(LOG, "logProcessingComplete", 
            "%s completed for paper %s in %d ms", 
            taskletName, paperId, processingTime.toMillis());
    }
    
    /**
     * Validates that a result exists and is successful.
     */
    protected void validatePreviousStepResult(AgentResult result, String stepName) {
        if (result == null) {
            throw new RuntimeException(stepName + " result not available");
        }
        if (!result.isSuccess()) {
            throw new RuntimeException(stepName + " failed: " + result.getErrorMessage());
        }
    }
    
    /**
     * Extracts string value from result data with fallback keys.
     */
    protected String extractStringFromResult(AgentResult result, String... keys) {
        if (result == null || result.getResultData() == null) {
            return null;
        }
        
        for (String key : keys) {
            Object value = result.getResultData().get(key);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return (String) value;
            }
        }
        
        return null;
    }
}

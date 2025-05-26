package com.samjdtechnologies.answer42.batch;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Custom job parameter validator for the multi-agent pipeline.
 * Validates required parameters and their formats before job execution.
 */
@Component
public class MultiAgentJobParametersValidator implements JobParametersValidator {
    
    private static final Logger LOG = 
        LoggerFactory.getLogger(MultiAgentJobParametersValidator.class);

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        LoggingUtil.info(LOG, "validate", "Validating job parameters for multi-agent pipeline");

        // Validate paperId (required)
        validatePaperId(parameters);
        
        // Validate userId (required) 
        validateUserId(parameters);
        
        // Validate optional parameters if present
        validateOptionalParameters(parameters);

        LoggingUtil.info(LOG, "validate", "Job parameter validation completed successfully");
    }

    /**
     * Validates the paperId parameter.
     */
    private void validatePaperId(JobParameters parameters) throws JobParametersInvalidException {
        String paperId = parameters.getString("paperId");
        
        if (paperId == null || paperId.trim().isEmpty()) {
            throw new JobParametersInvalidException("Required parameter 'paperId' is missing or empty");
        }

        // Validate UUID format
        try {
            UUID.fromString(paperId.trim());
            LoggingUtil.debug(LOG, "validatePaperId", "Valid paperId: %s", paperId);
        } catch (IllegalArgumentException e) {
            throw new JobParametersInvalidException(
                String.format("Parameter 'paperId' must be a valid UUID format. Got: %s", paperId));
        }
    }

    /**
     * Validates the userId parameter (required for AIConfig integration and cost tracking).
     */
    private void validateUserId(JobParameters parameters) throws JobParametersInvalidException {
        String userId = parameters.getString("userId");
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new JobParametersInvalidException(
                "Required parameter 'userId' is missing or empty. " +
                "userId is mandatory for AIConfig integration, cost tracking, and audit trail.");
        }

        // Validate UUID format
        try {
            UUID.fromString(userId.trim());
            LoggingUtil.debug(LOG, "validateUserId", "Valid userId: %s", userId);
        } catch (IllegalArgumentException e) {
            throw new JobParametersInvalidException(
                String.format("Parameter 'userId' must be a valid UUID format. Got: %s", userId));
        }
    }

    /**
     * Validates optional parameters if they are present.
     */
    private void validateOptionalParameters(JobParameters parameters) throws JobParametersInvalidException {
        // Validate processingMode if present
        validateProcessingMode(parameters);
        
        // Validate enabledAgents if present
        validateEnabledAgents(parameters);
        
        // Validate timeout parameters if present
        validateTimeoutParameters(parameters);
        
        // Validate priority if present
        validatePriority(parameters);
    }

    /**
     * Validates the processingMode parameter.
     */
    private void validateProcessingMode(JobParameters parameters) throws JobParametersInvalidException {
        String mode = parameters.getString("processingMode");
        
        if (mode != null && !mode.trim().isEmpty()) {
            String modeUpper = mode.trim().toUpperCase();
            
            String[] validModes = {"FAST", "STANDARD", "COMPREHENSIVE", "RESEARCH_GRADE"};
            boolean isValid = false;
            
            for (String validMode : validModes) {
                if (validMode.equals(modeUpper)) {
                    isValid = true;
                    break;
                }
            }
            
            if (!isValid) {
                throw new JobParametersInvalidException(
                    String.format("Parameter 'processingMode' must be one of: %s. Got: %s", 
                        String.join(", ", validModes), mode));
            }
            
            LoggingUtil.debug(LOG, "validateProcessingMode", "Valid processingMode: %s", modeUpper);
        }
    }

    /**
     * Validates the enabledAgents parameter.
     */
    private void validateEnabledAgents(JobParameters parameters) throws JobParametersInvalidException {
        String agentsStr = parameters.getString("enabledAgents");
        
        if (agentsStr != null && !agentsStr.trim().isEmpty()) {
            String[] agents = agentsStr.split(",");
            String[] validAgents = {
                "paper-processor", "metadata-enhancer", "content-summarizer", 
                "concept-explainer", "citation-formatter", "quality-checker",
                "research-discovery", "perplexity-researcher"
            };
            
            for (String agent : agents) {
                String agentTrimmed = agent.trim();
                boolean isValid = false;
                
                for (String validAgent : validAgents) {
                    if (validAgent.equals(agentTrimmed)) {
                        isValid = true;
                        break;
                    }
                }
                
                if (!isValid) {
                    throw new JobParametersInvalidException(
                        String.format("Invalid agent '%s'. Valid agents: %s", 
                            agentTrimmed, String.join(", ", validAgents)));
                }
            }
            
            LoggingUtil.debug(LOG, "validateEnabledAgents", "Valid enabledAgents: %s", agentsStr);
        }
    }

    /**
     * Validates timeout parameters.
     */
    private void validateTimeoutParameters(JobParameters parameters) throws JobParametersInvalidException {
        validatePositiveLongParameter(parameters, "timeoutMinutes", 1L, 480L); // 1 min to 8 hours
        validatePositiveLongParameter(parameters, "stepTimeoutMinutes", 1L, 60L); // 1 min to 1 hour
    }

    /**
     * Validates the priority parameter.
     */
    private void validatePriority(JobParameters parameters) throws JobParametersInvalidException {
        Long priority = parameters.getLong("priority");
        
        if (priority != null) {
            if (priority < 1L || priority > 10L) {
                throw new JobParametersInvalidException(
                    String.format("Parameter 'priority' must be between 1 and 10. Got: %d", priority));
            }
            
            LoggingUtil.debug(LOG, "validatePriority", "Valid priority: %d", priority);
        }
    }

    /**
     * Helper method to validate positive long parameters with min/max bounds.
     */
    private void validatePositiveLongParameter(JobParameters parameters, String paramName, 
                                             Long minValue, Long maxValue) throws JobParametersInvalidException {
        Long value = parameters.getLong(paramName);
        
        if (value != null) {
            if (minValue != null && value < minValue) {
                throw new JobParametersInvalidException(
                    String.format("Parameter '%s' must be at least %d. Got: %d", 
                        paramName, minValue, value));
            }
            
            if (maxValue != null && value > maxValue) {
                throw new JobParametersInvalidException(
                    String.format("Parameter '%s' must be at most %d. Got: %d", 
                        paramName, maxValue, value));
            }
            
            LoggingUtil.debug(LOG, "validatePositiveLongParameter", "Valid %s: %d", paramName, value);
        }
    }

    /**
     * Helper method to get parameter summary for logging.
     */
    public String getParameterSummary(JobParameters parameters) {
        StringBuilder summary = new StringBuilder();
        summary.append("Job Parameters Summary: ");
        
        String paperId = parameters.getString("paperId");
        if (paperId != null) {
            summary.append("paperId=").append(paperId).append(", ");
        }
        
        String userId = parameters.getString("userId");
        if (userId != null) {
            summary.append("userId=").append(userId).append(", ");
        }
        
        String mode = parameters.getString("processingMode");
        if (mode != null) {
            summary.append("processingMode=").append(mode).append(", ");
        }
        
        String agents = parameters.getString("enabledAgents");
        if (agents != null) {
            summary.append("enabledAgents=").append(agents).append(", ");
        }
        
        Long priority = parameters.getLong("priority");
        if (priority != null) {
            summary.append("priority=").append(priority).append(", ");
        }
        
        // Remove trailing comma and space
        String result = summary.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        
        return result;
    }

    /**
     * Validates that all required parameters are present and valid.
     */
    public boolean hasValidParameters(JobParameters parameters) {
        try {
            validate(parameters);
            return true;
        } catch (JobParametersInvalidException e) {
            LoggingUtil.warn(LOG, "hasValidParameters", 
                "Invalid parameters: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Gets parameter validation summary for monitoring and debugging.
     */
    public String getValidationSummary(JobParameters parameters) {
        StringBuilder summary = new StringBuilder();
        summary.append("Parameter Validation Summary:\n");
        
        try {
            validate(parameters);
            summary.append("✓ All parameters are valid\n");
        } catch (JobParametersInvalidException e) {
            summary.append("✗ Validation failed: ").append(e.getMessage()).append("\n");
        }
        
        summary.append(getParameterSummary(parameters));
        
        return summary.toString();
    }
}

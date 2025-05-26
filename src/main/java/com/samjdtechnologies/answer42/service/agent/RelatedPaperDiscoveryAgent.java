package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.discovery.DiscoveryCoordinator;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Related Paper Discovery Agent - discovers related academic papers through multiple sources.
 * Integrates Crossref, Semantic Scholar, and Perplexity APIs to provide comprehensive paper discovery.
 */
@Component
public class RelatedPaperDiscoveryAgent extends AbstractConfigurableAgent {

    private final DiscoveryCoordinator discoveryCoordinator;
    private final PaperRepository paperRepository;
    private final ObjectMapper objectMapper;

    public RelatedPaperDiscoveryAgent(AIConfig aiConfig, ThreadConfig threadConfig,
            AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter,
            DiscoveryCoordinator discoveryCoordinator, PaperRepository paperRepository) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        this.discoveryCoordinator = discoveryCoordinator;
        this.paperRepository = paperRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.RELATED_PAPER_DISCOVERY;
    }

    @Override
    public AIProvider getProvider() {
        return AIProvider.ANTHROPIC; // Uses Anthropic for AI synthesis
    }

    @Override
    protected ChatClient getConfiguredChatClient() {
        // This agent primarily uses external APIs, but uses Anthropic for synthesis
        return aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
    }

    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        try {
            DiscoveryRequest request = extractDiscoveryRequest(task);
            if (request != null && request.getConfiguration() != null) {
                // Base time: 2 minutes + variable time based on configuration
                int baseMinutes = 2;
                int additionalMinutes = calculateAdditionalTime(request.getConfiguration());
                return Duration.ofMinutes(baseMinutes + additionalMinutes);
            }
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "estimateProcessingTime", 
                "Could not estimate time for task %s: %s", task.getId(), e.getMessage());
        }
        return Duration.ofMinutes(5); // Default fallback
    }

    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        Instant startTime = Instant.now();
        
        try {
            LoggingUtil.info(LOG, "processWithConfig", 
                "Starting related paper discovery for task %s", task.getId());

            // Extract and validate discovery request
            DiscoveryRequest request = extractDiscoveryRequest(task);
            if (request == null) {
                String errorMessage = "Invalid task input: could not extract discovery request";
                LoggingUtil.error(LOG, "processWithConfig", errorMessage);
                return AgentResult.failure(task.getId(), errorMessage);
            }

            Paper sourcePaper = request.getSourcePaper();
            DiscoveryConfiguration config = request.getConfiguration();

            LoggingUtil.info(LOG, "processWithConfig", 
                "Starting discovery for paper %s (%s) with config: %s", 
                sourcePaper.getId(), sourcePaper.getTitle(), config.getConfigurationSummary());

            // Validate configuration
            if (!config.isValid()) {
                String errorMessage = "Invalid discovery configuration: " + 
                    String.join(", ", config.validate());
                LoggingUtil.error(LOG, "processWithConfig", errorMessage);
                return AgentResult.failure(task.getId(), errorMessage);
            }

            // Execute discovery using coordinator
            CompletableFuture<RelatedPaperDiscoveryResult> discoveryFuture = 
                discoveryCoordinator.coordinateDiscovery(sourcePaper, config);

            // Wait for discovery completion with timeout
            RelatedPaperDiscoveryResult result = discoveryFuture
                .orTimeout(config.getEffectiveTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .join();

            // Update timing information
            result.setDiscoveryStartTime(startTime);
            result.setDiscoveryEndTime(Instant.now());
            result.setTotalProcessingTimeMs(Duration.between(startTime, Instant.now()).toMillis());

            LoggingUtil.info(LOG, "processWithConfig", 
                "Discovery completed for paper %s: %s", 
                sourcePaper.getId(), result.getDiscoverySummary());

            return AgentResult.success(task.getId(), result, createProcessingMetrics(startTime));

        } catch (CompletionException e) {
            // Check if the cause is a TimeoutException
            if (e.getCause() instanceof TimeoutException) {
                LoggingUtil.error(LOG, "processWithConfig", 
                    "Discovery timed out for task %s", e, task.getId());
                return AgentResult.failure(task.getId(), "Discovery operation timed out");
            } else {
                LoggingUtil.error(LOG, "processWithConfig", 
                    "Discovery failed for task %s", e, task.getId());
                return AgentResult.failure(task.getId(), "Discovery operation failed: " + e.getMessage());
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Discovery failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), "Discovery operation failed: " + e.getMessage());
        }
    }

    @Override
    public boolean canHandle(AgentTask task) {
        try {
            DiscoveryRequest request = extractDiscoveryRequest(task);
            return request != null && 
                   request.getSourcePaper() != null && 
                   request.getConfiguration() != null &&
                   request.getConfiguration().isValid();
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "canHandle", "Cannot handle task %s: %s", task.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Extracts discovery request from agent task input using proper JSON parsing.
     */
    private DiscoveryRequest extractDiscoveryRequest(AgentTask task) {
        try {
            JsonNode input = task.getInput();
            
            // Extract paper ID (required)
            UUID paperId = extractPaperIdFromInput(input);
            if (paperId == null) {
                LoggingUtil.error(LOG, "extractDiscoveryRequest", "No valid paper ID found in task input");
                return null;
            }

            // Load paper from repository
            Paper sourcePaper = loadPaperFromRepository(paperId);
            if (sourcePaper == null) {
                LoggingUtil.error(LOG, "extractDiscoveryRequest", 
                    "Paper with ID %s not found in repository", paperId);
                return null;
            }

            // Extract discovery configuration
            DiscoveryConfiguration config = extractConfigurationFromInput(input);

            return new DiscoveryRequest(sourcePaper, config);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "extractDiscoveryRequest", 
                "Failed to parse discovery request from task input", e);
            return null;
        }
    }

    /**
     * Extracts paper ID from task input with robust parsing.
     */
    private UUID extractPaperIdFromInput(JsonNode input) {
        // Try multiple field names for paper ID
        String[] paperIdFields = {"paperId", "paper_id", "sourcepaperId", "sourcePaperId", "id"};
        
        for (String field : paperIdFields) {
            if (input.has(field)) {
                JsonNode paperIdNode = input.get(field);
                try {
                    if (paperIdNode.isTextual()) {
                        return UUID.fromString(paperIdNode.asText());
                    }
                } catch (IllegalArgumentException e) {
                    LoggingUtil.warn(LOG, "extractPaperIdFromInput", 
                        "Invalid UUID format for field %s: %s", field, paperIdNode.asText());
                }
            }
        }

        // Try extracting from nested objects
        if (input.has("paper") && input.get("paper").has("id")) {
            try {
                return UUID.fromString(input.get("paper").get("id").asText());
            } catch (IllegalArgumentException e) {
                LoggingUtil.warn(LOG, "extractPaperIdFromInput", 
                    "Invalid UUID format in nested paper.id");
            }
        }

        LoggingUtil.error(LOG, "extractPaperIdFromInput", 
            "No valid paper ID found in input. Available fields: %s", 
            input.fieldNames().next());
        return null;
    }

    /**
     * Loads paper from repository with proper error handling.
     */
    private Paper loadPaperFromRepository(UUID paperId) {
        try {
            Optional<Paper> paperOpt = paperRepository.findById(paperId);
            if (paperOpt.isPresent()) {
                Paper paper = paperOpt.get();
                LoggingUtil.debug(LOG, "loadPaperFromRepository", 
                    "Loaded paper: %s (%s)", paper.getId(), paper.getTitle());
                return paper;
            } else {
                LoggingUtil.warn(LOG, "loadPaperFromRepository", 
                    "Paper with ID %s not found in repository", paperId);
                return null;
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "loadPaperFromRepository", 
                "Error loading paper %s from repository", e, paperId);
            return null;
        }
    }

    /**
     * Extracts discovery configuration from task input with intelligent defaults.
     */
    private DiscoveryConfiguration extractConfigurationFromInput(JsonNode input) {
        try {
            // Check if explicit configuration is provided
            if (input.has("configuration") || input.has("discoveryConfig")) {
                JsonNode configNode = input.has("configuration") ? 
                    input.get("configuration") : input.get("discoveryConfig");
                return parseExplicitConfiguration(configNode);
            }

            // Check for configuration type indicators
            String configType = extractStringValue(input, "configurationType", "configType", "type");
            if (configType != null) {
                return createConfigurationByType(configType);
            }

            // Check for individual configuration parameters
            if (hasCustomConfigurationParameters(input)) {
                return buildCustomConfiguration(input);
            }

            // Default configuration
            LoggingUtil.debug(LOG, "extractConfigurationFromInput", 
                "No specific configuration found, using default");
            return DiscoveryConfiguration.defaultConfig();

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractConfigurationFromInput", 
                "Error parsing configuration, using default: %s", e.getMessage());
            return DiscoveryConfiguration.defaultConfig();
        }
    }

    private DiscoveryConfiguration parseExplicitConfiguration(JsonNode configNode) {
        // Parse explicit configuration JSON
        try {
            return objectMapper.treeToValue(configNode, DiscoveryConfiguration.class);
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "parseExplicitConfiguration", 
                "Failed to parse explicit configuration: %s", e.getMessage());
            return DiscoveryConfiguration.defaultConfig();
        }
    }

    private DiscoveryConfiguration createConfigurationByType(String configType) {
        return switch (configType.toLowerCase()) {
            case "comprehensive", "complete", "thorough" -> DiscoveryConfiguration.comprehensiveConfig();
            case "fast", "quick", "simple" -> DiscoveryConfiguration.fastConfig();
            case "citation", "citations", "citation-focused" -> DiscoveryConfiguration.citationFocusedConfig();
            default -> {
                LoggingUtil.debug(LOG, "createConfigurationByType", 
                    "Unknown configuration type '%s', using default", configType);
                yield DiscoveryConfiguration.defaultConfig();
            }
        };
    }

    private boolean hasCustomConfigurationParameters(JsonNode input) {
        String[] configParams = {
            "sources", "enabledSources", "maxPapers", "maxPapersPerSource", 
            "maxTotalPapers", "minimumRelevanceScore", "timeoutSeconds", 
            "parallelExecution", "enableAISynthesis"
        };
        
        for (String param : configParams) {
            if (input.has(param)) {
                return true;
            }
        }
        return false;
    }

    private DiscoveryConfiguration buildCustomConfiguration(JsonNode input) {
        DiscoveryConfiguration.DiscoveryConfigurationBuilder builder = 
            DiscoveryConfiguration.defaultConfig().toBuilder();

        // Override with provided parameters
        if (input.has("maxPapers") || input.has("maxTotalPapers")) {
            int maxTotal = extractIntValue(input, 100, "maxPapers", "maxTotalPapers");
            builder.maxTotalPapers(maxTotal);
        }

        if (input.has("maxPapersPerSource")) {
            int maxPerSource = extractIntValue(input, 25, "maxPapersPerSource");
            builder.maxPapersPerSource(maxPerSource);
        }

        if (input.has("minimumRelevanceScore")) {
            double minRelevance = extractDoubleValue(input, 0.3, "minimumRelevanceScore");
            builder.minimumRelevanceScore(minRelevance);
        }

        if (input.has("timeoutSeconds")) {
            int timeout = extractIntValue(input, 300, "timeoutSeconds");
            builder.timeoutSeconds(timeout);
        }

        if (input.has("parallelExecution")) {
            boolean parallel = extractBooleanValue(input, true, "parallelExecution");
            builder.parallelExecution(parallel);
        }

        if (input.has("enableAISynthesis")) {
            boolean synthesis = extractBooleanValue(input, true, "enableAISynthesis");
            builder.enableAISynthesis(synthesis);
        }

        return builder.build();
    }

    private String extractStringValue(JsonNode input, String... fieldNames) {
        for (String field : fieldNames) {
            if (input.has(field) && input.get(field).isTextual()) {
                return input.get(field).asText();
            }
        }
        return null;
    }

    private int extractIntValue(JsonNode input, int defaultValue, String... fieldNames) {
        for (String field : fieldNames) {
            if (input.has(field) && input.get(field).isNumber()) {
                return input.get(field).asInt();
            }
        }
        return defaultValue;
    }

    private double extractDoubleValue(JsonNode input, double defaultValue, String... fieldNames) {
        for (String field : fieldNames) {
            if (input.has(field) && input.get(field).isNumber()) {
                return input.get(field).asDouble();
            }
        }
        return defaultValue;
    }

    private boolean extractBooleanValue(JsonNode input, boolean defaultValue, String... fieldNames) {
        for (String field : fieldNames) {
            if (input.has(field) && input.get(field).isBoolean()) {
                return input.get(field).asBoolean();
            }
        }
        return defaultValue;
    }

    /**
     * Calculates additional processing time based on configuration complexity.
     */
    private int calculateAdditionalTime(DiscoveryConfiguration config) {
        int additionalMinutes = 0;

        // Time based on enabled sources
        if (config.getEnabledSources() != null) {
            additionalMinutes += config.getEnabledSources().size() * 1; // 1 minute per source
        }

        // Time based on target papers
        if (config.getMaxTotalPapers() != null && config.getMaxTotalPapers() > 100) {
            additionalMinutes += 2; // Extra time for larger result sets
        }

        // Time for AI synthesis
        if (config.isAISynthesisEnabled()) {
            additionalMinutes += 1;
        }

        return Math.min(additionalMinutes, 6); // Cap at 6 additional minutes
    }

    /**
     * Inner class for discovery request data.
     */
    private static class DiscoveryRequest {
        private final Paper sourcePaper;
        private final DiscoveryConfiguration configuration;

        public DiscoveryRequest(Paper sourcePaper, DiscoveryConfiguration configuration) {
            this.sourcePaper = sourcePaper;
            this.configuration = configuration;
        }

        public Paper getSourcePaper() {
            return sourcePaper;
        }

        public DiscoveryConfiguration getConfiguration() {
            return configuration;
        }
    }
}

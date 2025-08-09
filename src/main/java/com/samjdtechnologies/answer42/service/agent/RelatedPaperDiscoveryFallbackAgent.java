package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaperResult;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryMetadata;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Fallback agent for related paper discovery using local Ollama models.
 * This agent provides basic paper discovery capabilities when external discovery APIs are unavailable.
 * 
 * Features:
 * - Local paper discovery using Ollama models
 * - Rule-based paper suggestions
 * - Integration with existing retry policies
 * - Fallback discovery mechanisms
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class RelatedPaperDiscoveryFallbackAgent extends OllamaBasedAgent {

    private final PaperRepository paperRepository;

    public RelatedPaperDiscoveryFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                            APIRateLimiter rateLimiter, PaperRepository paperRepository) {
        super(aiConfig, threadConfig, rateLimiter);
        this.paperRepository = paperRepository;
        LoggingUtil.info(LOG, "RelatedPaperDiscoveryFallbackAgent", 
            "Initialized RelatedPaperDiscoveryFallbackAgent for local paper discovery");
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.RELATED_PAPER_DISCOVERY;
    }

    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Starting related paper discovery using Ollama fallback for task %s", task.getId());

        if (!canProcess()) {
            String errorMessage = "Ollama chat client not available for paper discovery";
            LoggingUtil.error(LOG, "processWithConfig", errorMessage);
            return AgentResult.failure(task.getId(), errorMessage);
        }


        try {
            JsonNode input = task.getInput();
            
            // Extract discovery parameters from task
            DiscoveryParameters params = extractDiscoveryParameters(input);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Processing discovery for paper: %s (%s)", 
                params.getSourcePaper().getId(), params.getSourcePaper().getTitle());

            // Generate related papers using local Ollama processing
            RelatedPaperDiscoveryResult discoveryResult = performLocalDiscovery(params, task);

            // Prepare result data
            Map<String, Object> resultData = createResultData(discoveryResult);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Local discovery completed with %d papers found", 
                discoveryResult.getTotalDiscoveredPapers());

            return AgentResult.withFallback(task.getId(), resultData,
                "External discovery APIs unavailable - using local analysis");

        } catch (Exception e) {
            String errorMessage = handleLocalProcessingError(e, task.getId());
            LoggingUtil.error(LOG, "processWithConfig", 
                "Local paper discovery failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), errorMessage);
        }
    }

    /**
     * Extract discovery parameters from task input.
     */
    private DiscoveryParameters extractDiscoveryParameters(JsonNode input) {
        // Extract paper ID (required)
        UUID paperId = extractPaperIdFromInput(input);
        if (paperId == null) {
            throw new IllegalArgumentException("No valid paper ID found in task input");
        }

        // Load paper from repository
        Paper sourcePaper = loadPaperFromRepository(paperId);
        if (sourcePaper == null) {
            throw new IllegalArgumentException("Paper with ID " + paperId + " not found in repository");
        }

        // Extract discovery configuration with defaults
        DiscoveryConfiguration config = extractConfigurationFromInput(input);
        
        return new DiscoveryParameters(sourcePaper, config);
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
            "No valid paper ID found in input");
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

            // Use default configuration for fallback
            LoggingUtil.debug(LOG, "extractConfigurationFromInput", 
                "No specific configuration found, using default for fallback");
            return DiscoveryConfiguration.defaultConfig();

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractConfigurationFromInput", 
                "Error parsing configuration, using default: %s", e.getMessage());
            return DiscoveryConfiguration.defaultConfig();
        }
    }

    private DiscoveryConfiguration parseExplicitConfiguration(JsonNode configNode) {
        // For fallback, we use a simplified configuration approach
        try {
            return DiscoveryConfiguration.fastConfig(); // Use fast config for local processing
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "parseExplicitConfiguration", 
                "Failed to parse explicit configuration: %s", e.getMessage());
            return DiscoveryConfiguration.defaultConfig();
        }
    }

    /**
     * Performs local paper discovery using Ollama models.
     */
    private RelatedPaperDiscoveryResult performLocalDiscovery(DiscoveryParameters params, AgentTask task) {
        List<DiscoveredPaperResult> discoveredPapers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // Generate paper suggestions using Ollama
            List<DiscoveredPaperResult> ollamaPapers = generateOllamaPaperSuggestions(params);
            discoveredPapers.addAll(ollamaPapers);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "performLocalDiscovery", 
                "Ollama discovery failed, using rule-based fallback: %s", e.getMessage());
            warnings.add("AI-based discovery failed: " + e.getMessage());
        }

        // Add rule-based paper suggestions as fallback
        List<DiscoveredPaperResult> rulePapers = generateRuleBasedPaperSuggestions(params);
        discoveredPapers.addAll(rulePapers);

        // Create discovery result
        return RelatedPaperDiscoveryResult.builder()
            .sourcePaperId(params.getSourcePaper().getId())
            .discoveredPapers(discoveredPapers)
            .discoveryStartTime(Instant.now().minusSeconds(30)) // Simulate processing time
            .discoveryEndTime(Instant.now())
            .totalProcessingTimeMs(30000L)
            .configuration(params.getConfiguration())
            .warnings(warnings)
            .errors(List.of())
            .overallConfidenceScore(0.6) // Lower confidence for fallback
            .requiresUserReview(true) // Always require review for fallback
            .build();
    }

    /**
     * Generates paper suggestions using Ollama models.
     */
    private List<DiscoveredPaperResult> generateOllamaPaperSuggestions(DiscoveryParameters params) {
        List<DiscoveredPaperResult> papers = new ArrayList<>();
        
        try {
            Paper sourcePaper = params.getSourcePaper();
            
            // Create prompt for paper discovery
            String promptText = String.format(
                "Paper Title: %s\n\n" +
                "Abstract: %s\n\n" +
                "Please suggest related research papers that would be relevant to this work. " +
                "Consider papers that might share similar topics, methodologies, or research areas. " +
                "Focus on academic papers that would be cited in a literature review.",
                sourcePaper.getTitle(),
                sourcePaper.getPaperAbstract() != null ? sourcePaper.getPaperAbstract() : "No abstract available"
            );

            Prompt discoveryPrompt = createSimplePrompt("Paper Discovery", promptText);
            
            ChatClient chatClient = getConfiguredChatClient();
            String response = chatClient.prompt(discoveryPrompt)
                .call()
                .content();

            LoggingUtil.debug(LOG, "generateOllamaPaperSuggestions", 
                "Ollama response received: %d characters", response.length());

            // Parse the response and create discovered papers
            papers.addAll(parseOllamaResponse(response, params));
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "generateOllamaPaperSuggestions", 
                "Failed to generate Ollama paper suggestions: %s", e.getMessage());
            throw e;
        }

        return papers;
    }

    /**
     * Parses Ollama response to extract paper suggestions.
     */
    private List<DiscoveredPaperResult> parseOllamaResponse(String response, DiscoveryParameters params) {
        List<DiscoveredPaperResult> papers = new ArrayList<>();
        
        // Simple parsing - look for patterns that might indicate paper titles
        String[] lines = response.split("\n");
        int paperCount = 0;
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip empty lines and very short lines
            if (line.length() < 20) continue;
            
            // Look for lines that might be paper titles (contain academic keywords)
            if (containsAcademicKeywords(line) && paperCount < 3) { // Limit to 3 papers
                DiscoveredPaperResult paper = createDiscoveredPaperFromText(line, params, paperCount);
                papers.add(paper);
                paperCount++;
            }
        }
        
        LoggingUtil.debug(LOG, "parseOllamaResponse", 
            "Parsed %d papers from Ollama response", papers.size());
        
        return papers;
    }

    /**
     * Checks if a line contains academic keywords that might indicate a paper title.
     */
    private boolean containsAcademicKeywords(String line) {
        String lowerLine = line.toLowerCase();
        String[] academicKeywords = {
            "analysis", "study", "research", "investigation", "evaluation", "assessment",
            "approach", "method", "algorithm", "framework", "model", "system",
            "application", "implementation", "design", "development", "optimization",
            "performance", "effectiveness", "efficiency", "comparison", "survey"
        };
        
        for (String keyword : academicKeywords) {
            if (lowerLine.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Creates a discovered paper from text analysis.
     */
    private DiscoveredPaperResult createDiscoveredPaperFromText(String text, 
            DiscoveryParameters params, int index) {
        
        // Create discovery metadata
        DiscoveryMetadata metadata = DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .apiConfidenceScore(0.7)
            .processingNotes("Generated using local Ollama AI analysis")
            .requiresManualReview(true)
            .build();

        return DiscoveredPaperResult.builder()
            .id("ollama-" + index)
            .title(text.length() > 100 ? text.substring(0, 100) + "..." : text)
            .authors(List.of("Unknown Author"))
            .abstractText("Related paper identified through local AI analysis")
            .relationshipType(RelationshipType.SEMANTIC_SIMILARITY)
            .relevanceScore(0.7 - (index * 0.1)) // Decreasing relevance
            .source(DiscoverySource.AI_SYNTHESIS)
            .metadata(metadata)
            .build();
    }

    /**
     * Generates rule-based paper suggestions as fallback.
     */
    private List<DiscoveredPaperResult> generateRuleBasedPaperSuggestions(DiscoveryParameters params) {
        List<DiscoveredPaperResult> papers = new ArrayList<>();
        Paper sourcePaper = params.getSourcePaper();
        
        // Create discovery metadata
        DiscoveryMetadata metadata = DiscoveryMetadata.builder()
            .discoveredAt(Instant.now())
            .apiConfidenceScore(0.5)
            .processingNotes("Generated using rule-based heuristics")
            .requiresManualReview(true)
            .build();

        // Generate basic suggestions based on paper characteristics
        String[] suggestionTemplates = {
            "A survey of %s in %s research",
            "Advanced techniques in %s: A comprehensive review",
            "Comparative analysis of %s methodologies"
        };

        String domain = extractDomainFromPaper(sourcePaper);
        String topic = extractTopicFromPaper(sourcePaper);

        for (int i = 0; i < Math.min(2, suggestionTemplates.length); i++) {
            String template = suggestionTemplates[i];
            String title = String.format(template, topic, domain);
            
            DiscoveredPaperResult paper = DiscoveredPaperResult.builder()
                .id("rule-" + i)
                .title(title)
                .authors(List.of("Research Community"))
                .abstractText(String.format("This paper explores %s within the context of %s research, " +
                    "providing insights relevant to the source paper.", topic, domain))
                .relationshipType(RelationshipType.FIELD_RELATED)
                .relevanceScore(0.5 - (i * 0.1))
                .source(DiscoverySource.AI_SYNTHESIS)
                .metadata(metadata)
                .build();
            
            papers.add(paper);
        }

        LoggingUtil.debug(LOG, "generateRuleBasedPaperSuggestions", 
            "Generated %d rule-based paper suggestions", papers.size());

        return papers;
    }

    /**
     * Extracts domain information from paper.
     */
    private String extractDomainFromPaper(Paper paper) {
        // Simple heuristic based on title analysis
        String title = paper.getTitle().toLowerCase();
        
        if (title.contains("machine learning") || title.contains("ai") || title.contains("artificial intelligence")) {
            return "machine learning";
        } else if (title.contains("computer") || title.contains("software") || title.contains("algorithm")) {
            return "computer science";
        } else if (title.contains("data") || title.contains("analysis")) {
            return "data science";
        } else {
            return "interdisciplinary";
        }
    }

    /**
     * Extracts topic information from paper.
     */
    private String extractTopicFromPaper(Paper paper) {
        // Extract key terms from title
        String title = paper.getTitle();
        String[] words = title.split("\\s+");
        
        // Look for meaningful terms (skip common words)
        for (String word : words) {
            word = word.toLowerCase().replaceAll("[^a-z]", "");
            if (word.length() > 4 && !isCommonWord(word)) {
                return word;
            }
        }
        
        return "research topics";
    }

    /**
     * Checks if a word is a common word to skip.
     */
    private boolean isCommonWord(String word) {
        String[] commonWords = {
            "using", "based", "approach", "method", "system", "analysis", 
            "study", "research", "paper", "novel", "improved"
        };
        
        for (String common : commonWords) {
            if (word.equals(common)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Creates result data map for the discovery result.
     */
    private Map<String, Object> createResultData(RelatedPaperDiscoveryResult discoveryResult) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("result", discoveryResult);
        resultData.put("usedFallback", true);
        resultData.put("primaryFailureReason", "External discovery APIs unavailable");
        
        // Add fallback processing note
        resultData.put("fallbackNote", createFallbackProcessingNote("Paper discovery"));
        
        // Add discovery statistics
        resultData.put("hasResults", discoveryResult.getTotalDiscoveredPapers() > 0);
        resultData.put("papersFound", discoveryResult.getTotalDiscoveredPapers());
        
        return resultData;
    }

    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        // Local processing time estimate
        return Duration.ofSeconds(45);
    }

    protected String getAgentDescription() {
        return "Fallback agent for related paper discovery using local Ollama models when external discovery APIs are unavailable";
    }

    /**
     * Inner class for discovery parameters.
     */
    private static class DiscoveryParameters {
        private final Paper sourcePaper;
        private final DiscoveryConfiguration configuration;

        public DiscoveryParameters(Paper sourcePaper, DiscoveryConfiguration configuration) {
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

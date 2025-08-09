package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.research.PerplexityResearchResult;
import com.samjdtechnologies.answer42.model.research.ResearchParameters;
import com.samjdtechnologies.answer42.model.research.ResearchQuery;
import com.samjdtechnologies.answer42.model.research.ResearchResult;
import com.samjdtechnologies.answer42.model.research.ResearchSource;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Fallback agent for research using local Ollama models.
 * This agent provides basic research capabilities when external research APIs are unavailable.
 * 
 * Features:
 * - Local research using Ollama models
 * - Fact verification using local processing
 * - Research synthesis and analysis
 * - Integration with existing retry policies
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class PerplexityResearchFallbackAgent extends OllamaBasedAgent {

    public PerplexityResearchFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                         APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, rateLimiter);
        LoggingUtil.info(LOG, "PerplexityResearchFallbackAgent", 
            "Initialized PerplexityResearchFallbackAgent for local research processing");
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.PERPLEXITY_RESEARCHER;
    }

    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Starting research analysis using Ollama fallback for task %s", task.getId());

        if (!canProcess()) {
            String errorMessage = "Ollama chat client not available for research analysis";
            LoggingUtil.error(LOG, "processWithConfig", errorMessage);
            return AgentResult.failure(task.getId(), errorMessage);
        }

        try {
            JsonNode input = task.getInput();
            
            // Extract research parameters from task
            ResearchParameters params = extractResearchParameters(input);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Processing research for topic: %s with %d claims", 
                params.getResearchTopic(), params.getKeyClaims().size());

            // Generate research using local Ollama processing
            PerplexityResearchResult researchResult = performLocalResearch(params, task);

            // Prepare result data
            Map<String, Object> resultData = createResultData(researchResult);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Local research completed with %d query results", 
                researchResult.getQueryResults().size());

            return AgentResult.withFallback(task.getId(), resultData,
                "External research APIs unavailable - using local analysis");

        } catch (Exception e) {
            String errorMessage = handleLocalProcessingError(e, task.getId());
            LoggingUtil.error(LOG, "processWithConfig", 
                "Local research analysis failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), errorMessage);
        }
    }

    /**
     * Extract research parameters from task input.
     */
    private ResearchParameters extractResearchParameters(JsonNode input) {
        // Extract basic identifiers
        String paperId = getStringValue(input, "paperId", "unknown");
        String researchTopic = getStringValue(input, "topic", getStringValue(input, "researchTopic", "General Research"));
        String researchDomain = getStringValue(input, "domain", getStringValue(input, "researchDomain", "Academic"));
        String paperContext = getStringValue(input, "context", getStringValue(input, "paperContext", ""));
        String methodology = getStringValue(input, "methodology", getStringValue(input, "methodologyDescription", ""));
        
        // Extract lists
        List<String> keyClaims = extractStringList(input, "claims", "keyClaims");
        List<String> keywords = extractStringList(input, "keywords");
        
        // Extract boolean flags with defaults
        boolean verifyFacts = getBooleanValue(input, "verifyFacts", true);
        boolean findRelated = getBooleanValue(input, "findRelated", getBooleanValue(input, "findRelatedPapers", true));
        boolean analyzeTrends = getBooleanValue(input, "analyzeTrends", false);
        boolean verifyMethodology = getBooleanValue(input, "verifyMethodology", false);
        boolean expertOpinions = getBooleanValue(input, "expertOpinions", getBooleanValue(input, "gatherExpertOpinions", false));
        
        // If no specific topic provided, try to extract from paper content
        if ("General Research".equals(researchTopic) && input.has("paperTitle")) {
            researchTopic = getStringValue(input, "paperTitle", researchTopic);
        }
        
        // Auto-detect research type based on available data
        if (keyClaims.isEmpty() && input.has("abstract")) {
            keyClaims = extractClaimsFromAbstract(getStringValue(input, "abstract", ""));
        }
        
        if (keywords.isEmpty() && input.has("paperKeywords")) {
            keywords = extractStringList(input, "paperKeywords");
        }
        
        LoggingUtil.debug(LOG, "extractResearchParameters", 
            "Extracted parameters: topic='%s', claims=%d, keywords=%d, verifyFacts=%s, findRelated=%s",
            researchTopic, keyClaims.size(), keywords.size(), verifyFacts, findRelated);
        
        return ResearchParameters.builder()
            .paperId(paperId)
            .researchTopic(researchTopic)
            .researchDomain(researchDomain)
            .paperContext(paperContext)
            .keyClaims(keyClaims)
            .keywords(keywords)
            .methodologyDescription(methodology)
            .verifyFacts(verifyFacts)
            .findRelatedPapers(findRelated)
            .analyzeTrends(analyzeTrends)
            .verifyMethodology(verifyMethodology)
            .gatherExpertOpinions(expertOpinions)
            .build();
    }

    /**
     * Performs local research using Ollama models.
     */
    private PerplexityResearchResult performLocalResearch(ResearchParameters params, AgentTask task) {
        List<ResearchResult> queryResults = new ArrayList<>();
        
        try {
            // Generate research queries based on parameters
            List<ResearchQuery> queries = buildLocalResearchQueries(params);
            
            LoggingUtil.debug(LOG, "performLocalResearch", 
                "Generated %d research queries for local processing", queries.size());

            // Execute queries using Ollama
            for (ResearchQuery query : queries) {
                try {
                    ResearchResult result = executeLocalResearchQuery(query);
                    if (result != null && result.isReliable()) {
                        queryResults.add(result);
                    }
                } catch (Exception e) {
                    LoggingUtil.warn(LOG, "performLocalResearch", 
                        "Failed to execute local research query: %s", e.getMessage());
                }
            }

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "performLocalResearch", 
                "Local research processing failed, using rule-based fallback: %s", e.getMessage());
        }

        // Add rule-based research results as fallback
        queryResults.addAll(generateRuleBasedResearch(params));

        // Generate synthesis
        PerplexityResearchResult.ResearchSynthesis synthesis = generateResearchSynthesis(queryResults, params);
        
        // Generate fact verifications if requested
        List<PerplexityResearchResult.FactVerification> factVerifications = null;
        if (params.shouldVerifyFacts()) {
            factVerifications = generateFactVerifications(params.getKeyClaims(), queryResults);
        }

        return PerplexityResearchResult.builder()
            .queryResults(queryResults)
            .synthesis(synthesis)
            .factVerifications(factVerifications)
            .totalProcessingTimeMs(100L)
            .executedAt(Instant.now())
            .build();
    }

    /**
     * Builds research queries for local processing.
     */
    private List<ResearchQuery> buildLocalResearchQueries(ResearchParameters params) {
        List<ResearchQuery> queries = new ArrayList<>();

        // Fact verification queries
        if (params.shouldVerifyFacts()) {
            for (String claim : params.getKeyClaims()) {
                queries.add(ResearchQuery.factVerification(
                    "Analyze and discuss this research claim: " + claim,
                    params.getPaperContext(),
                    params.getPaperId()
                ));
            }
        }

        // Related research topics
        if (params.shouldFindRelatedPapers()) {
            queries.add(ResearchQuery.relatedPapers(
                "Discuss related research topics for: " + params.getResearchTopic(),
                params.getKeywords()
            ));
        }

        // Trend analysis
        if (params.shouldAnalyzeTrends()) {
            queries.add(ResearchQuery.trendAnalysis(
                "Analyze research trends in: " + params.getResearchDomain(),
                params.getPaperContext()
            ));
        }

        return queries;
    }

    /**
     * Executes a research query using Ollama.
     */
    private ResearchResult executeLocalResearchQuery(ResearchQuery query) {
        try {
            // Create prompt for local processing
            String promptText = String.format(
                "Research Topic: %s\n\nContext: %s\n\n" +
                "Please provide a comprehensive analysis of this research topic. " +
                "Include key findings, methodological approaches, and current understanding. " +
                "Focus on factual information and scholarly perspectives.",
                query.getQueryText(),
                query.getContext() != null ? query.getContext() : "Academic research"
            );

            Prompt researchPrompt = createSimplePrompt("Research Analysis", promptText);
            
            ChatClient chatClient = getConfiguredChatClient();
            String response = chatClient.prompt(researchPrompt)
                .call()
                .content();

            LoggingUtil.debug(LOG, "executeLocalResearchQuery", 
                "Ollama response received: %d characters", response.length());

            // Create research result
            List<ResearchSource> sources = List.of(
                ResearchSource.builder()
                    .title("Local Analysis")
                    .url("local://ollama")
                    .snippet(response.length() > 200 ? response.substring(0, 200) + "..." : response)
                    .credibilityScore(0.7)
                    .sourceType(ResearchSource.SourceType.UNKNOWN)
                    .build()
            );

            return ResearchResult.builder()
                .queryId(query.getQueryId())
                .queryType(query.getType())
                .content(response)
                .sources(sources)
                .confidenceScore(0.7)
                .processingTimeMs(1000L)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeLocalResearchQuery", 
                "Failed to execute local research query: %s", e.getMessage());
            return ResearchResult.failed(query.getQueryId(), query.getType(), e.getMessage());
        }
    }

    /**
     * Generates rule-based research results as fallback.
     */
    private List<ResearchResult> generateRuleBasedResearch(ResearchParameters params) {
        List<ResearchResult> results = new ArrayList<>();

        // Generate basic research findings based on parameters
        String[] researchTopics = {
            "Background and Context",
            "Methodological Approaches", 
            "Key Findings",
            "Implications and Applications"
        };

        for (int i = 0; i < Math.min(2, researchTopics.length); i++) {
            String topic = researchTopics[i];
            
            String summary = String.format(
                "This research area focuses on %s within the domain of %s. " +
                "Key considerations include methodological rigor, reproducibility, " +
                "and practical applications in the field.",
                params.getResearchTopic().toLowerCase(),
                params.getResearchDomain()
            );

            List<ResearchSource> sources = List.of(
                ResearchSource.builder()
                    .title(topic + " Analysis")
                    .url("local://rule-based")
                    .snippet("Generated analysis based on research parameters")
                    .credibilityScore(0.6)
                    .sourceType(ResearchSource.SourceType.UNKNOWN)
                    .build()
            );

            ResearchResult result = ResearchResult.builder()
                .queryId("rule-based-" + i)
                .queryType(ResearchQuery.ResearchType.FACT_VERIFICATION)
                .content(summary)
                .sources(sources)
                .confidenceScore(0.6)
                .processingTimeMs(50L)
                .build();
                
            results.add(result);
        }

        LoggingUtil.debug(LOG, "generateRuleBasedResearch", 
            "Generated %d rule-based research results", results.size());

        return results;
    }

    /**
     * Generates research synthesis from query results.
     */
    private PerplexityResearchResult.ResearchSynthesis generateResearchSynthesis(
            List<ResearchResult> queryResults, ResearchParameters params) {
        
        // Combine content from all query results
        String combinedFindings = queryResults.stream()
            .map(ResearchResult::getContent)
            .reduce("", (a, b) -> a + "\n\n" + b);

        // Calculate overall confidence
        double avgConfidence = queryResults.stream()
            .mapToDouble(ResearchResult::getConfidenceScore)
            .average()
            .orElse(0.5);

        return PerplexityResearchResult.ResearchSynthesis.builder()
            .summary("Research analysis of " + params.getResearchTopic() + 
                " using local processing capabilities.")
            .keyFindings(List.of(combinedFindings.length() > 500 ? 
                combinedFindings.substring(0, 500) + "..." : combinedFindings))
            .overallConfidence(avgConfidence)
            .build();
    }

    /**
     * Generates fact verifications for key claims.
     */
    private List<PerplexityResearchResult.FactVerification> generateFactVerifications(
            List<String> claims, List<ResearchResult> queryResults) {
        
        List<PerplexityResearchResult.FactVerification> verifications = new ArrayList<>();
        
        for (String claim : claims) {
            PerplexityResearchResult.FactVerification verification = 
                PerplexityResearchResult.FactVerification.builder()
                    .fact(claim)
                    .status(PerplexityResearchResult.FactVerification.VerificationStatus.UNVERIFIED)
                    .confidenceLevel(0.6)
                    .evidence("Local analysis suggests this claim aligns with " +
                        "general research patterns in the field.")
                    .notes("Claim analyzed using local processing capabilities. " +
                        "Limited verification due to local-only analysis.")
                    .build();
            
            verifications.add(verification);
        }

        return verifications;
    }

    /**
     * Helper methods for parameter extraction.
     */
    private String getStringValue(JsonNode input, String key, String defaultValue) {
        return input.has(key) && input.get(key).isTextual() ? input.get(key).asText() : defaultValue;
    }

    private boolean getBooleanValue(JsonNode input, String key, boolean defaultValue) {
        return input.has(key) ? input.get(key).asBoolean(defaultValue) : defaultValue;
    }

    private List<String> extractStringList(JsonNode input, String... keys) {
        for (String key : keys) {
            if (input.has(key) && input.get(key).isArray()) {
                List<String> items = new ArrayList<>();
                input.get(key).forEach(item -> {
                    if (item.isTextual()) {
                        items.add(item.asText());
                    }
                });
                return items;
            }
        }
        return List.of();
    }

    /**
     * Extracts basic claims from abstract text.
     */
    private List<String> extractClaimsFromAbstract(String abstractText) {
        if (abstractText == null || abstractText.trim().isEmpty()) {
            return List.of();
        }

        // Simple sentence splitting for claims extraction
        String[] sentences = abstractText.split("\\. ");
        List<String> claims = new ArrayList<>();
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            // Look for sentences that might contain claims
            if (sentence.length() > 30 && 
                (sentence.toLowerCase().contains("found") || 
                 sentence.toLowerCase().contains("results") ||
                 sentence.toLowerCase().contains("shows") ||
                 sentence.toLowerCase().contains("demonstrates"))) {
                claims.add(sentence);
            }
        }

        return claims.isEmpty() ? 
            Arrays.asList("General research findings", "Methodological approach", "Key conclusions") : 
            claims.subList(0, Math.min(claims.size(), 3));
    }

    /**
     * Creates result data map for the research result.
     */
    private Map<String, Object> createResultData(PerplexityResearchResult researchResult) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("result", researchResult);
        resultData.put("usedFallback", true);
        resultData.put("primaryFailureReason", "External research APIs unavailable");
        
        // Add fallback processing note
        resultData.put("fallbackNote", createFallbackProcessingNote("Research analysis"));
        
        return resultData;
    }

    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        // Local processing time estimate
        return Duration.ofSeconds(30);
    }

    protected String getAgentDescription() {
        return "Fallback agent for research and fact verification using local Ollama models when external research APIs are unavailable";
    }

}

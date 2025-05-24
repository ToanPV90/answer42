package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.research.PerplexityResearchResult;
import com.samjdtechnologies.answer42.model.research.ResearchParameters;
import com.samjdtechnologies.answer42.model.research.ResearchQuery;
import com.samjdtechnologies.answer42.model.research.ResearchResult;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.samjdtechnologies.answer42.util.ResearchResponseParser;

/**
 * Perplexity Research Agent for external research and fact verification.
 * Uses Perplexity API for real-time web search and research synthesis.
 */
@Component
public class PerplexityResearchAgent extends PerplexityBasedAgent {

    // Enhanced patterns for claim extraction
    private static final Pattern QUANTITATIVE_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s*%|\\b\\d+(?:\\.\\d+)?\\s*fold|\\b\\d+(?:\\.\\d+)?\\s*times|\\b\\d+(?:\\.\\d+)?\\s*(?:mg|kg|ml|cm|mm|μm|nm)");
    private static final Pattern STATISTICAL_PATTERN = Pattern.compile("(?i)\\b(?:p\\s*[<>=]\\s*0\\.\\d+|significant|correlation|regression|odds\\s+ratio|confidence\\s+interval)");
    private static final Pattern CAUSAL_PATTERN = Pattern.compile("(?i)\\b(?:caused?\\s+by|leads?\\s+to|results?\\s+in|due\\s+to|because\\s+of|associated\\s+with)");

    public PerplexityResearchAgent(AIConfig aiConfig, ThreadConfig threadConfig) {
        super(aiConfig, threadConfig);
        LoggingUtil.info(LOG, "PerplexityResearchAgent", 
            "Initialized Perplexity Research Agent with enhanced claim extraction");
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.PERPLEXITY_RESEARCHER;
    }

    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Starting research analysis for task %s", task.getId());

        Instant startTime = Instant.now();
        
        try {
            // Extract research parameters from task
            ResearchParameters params = extractResearchParameters(task);
            
            // Build comprehensive research queries
            List<ResearchQuery> queries = buildResearchQueries(params);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Generated %d research queries for analysis", queries.size());

            // Execute research queries in parallel using ThreadConfig executor
            List<CompletableFuture<ResearchResult>> queryFutures = queries.stream()
                .map(query -> CompletableFuture.supplyAsync(
                    () -> executeResearchQuery(query), taskExecutor))
                .collect(Collectors.toList());

            // Wait for all queries to complete
            CompletableFuture.allOf(queryFutures.toArray(new CompletableFuture[0])).join();

            // Collect results
            List<ResearchResult> queryResults = queryFutures.stream()
                .map(CompletableFuture::join)
                .filter(result -> result != null && result.isReliable())
                .collect(Collectors.toList());

            LoggingUtil.info(LOG, "processWithConfig", 
                "Completed %d reliable research queries", queryResults.size());

            // Synthesize comprehensive research findings
            PerplexityResearchResult researchResult = synthesizeResearchFindings(queryResults, params);

            return AgentResult.success(task.getId(), researchResult, createProcessingMetrics(startTime));

        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Research analysis failed for task %s: %s", task.getId(), e.getMessage(), e);
            return AgentResult.failure(task.getId(), e.getMessage());
        }
    }

    /**
     * Extract research parameters from agent task with full JSON parsing.
     */
    private ResearchParameters extractResearchParameters(AgentTask task) {
        JsonNode input = task.getInput();
        
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
     * Enhanced extraction of verifiable claims from academic abstracts.
     * Uses sophisticated pattern matching and scoring to identify high-quality claims.
     */
    private List<String> extractClaimsFromAbstract(String abstractText) {
        if (abstractText == null || abstractText.trim().isEmpty()) {
            return List.of();
        }

        List<ScoredClaim> potentialClaims = new ArrayList<>();
        
        // Normalize and split into sentences
        String normalizedAbstract = abstractText.replaceAll("\\s+", " ").trim();
        List<String> sentences = splitIntoSentences(normalizedAbstract);
        
        for (String sentence : sentences) {
            ScoredClaim claim = analyzeSentenceForClaims(sentence);
            if (claim != null && claim.score >= 3) { // Minimum threshold for claims
                potentialClaims.add(claim);
            }
        }
        
        // Sort by score and return top claims
        List<String> topClaims = potentialClaims.stream()
            .sorted((a, b) -> Integer.compare(b.score, a.score))
            .limit(5) // Maximum 5 claims
            .map(claim -> claim.text)
            .collect(Collectors.toList());
        
        LoggingUtil.debug(LOG, "extractClaimsFromAbstract", 
            "Extracted %d claims from %d sentences (analyzed %d potential claims)",
            topClaims.size(), sentences.size(), potentialClaims.size());
        
        return topClaims;
    }

    private List<String> splitIntoSentences(String text) {
        // Enhanced sentence splitting that handles academic writing patterns
        String[] rawSentences = text.split("(?<=[.!?])\\s+(?=[A-Z])");
        List<String> sentences = new ArrayList<>();
        
        for (String sentence : rawSentences) {
            sentence = sentence.trim();
            // Filter out very short sentences and incomplete fragments
            if (sentence.length() >= 20 && !isIncompleteFragment(sentence)) {
                sentences.add(sentence);
            }
        }
        
        return sentences;
    }

    private boolean isIncompleteFragment(String sentence) {
        // Check for incomplete sentence patterns
        String lower = sentence.toLowerCase();
        return lower.startsWith("however,") ||
               lower.startsWith("therefore,") ||
               lower.startsWith("in addition,") ||
               lower.startsWith("furthermore,") ||
               lower.startsWith("for example,") ||
               lower.startsWith("such as") ||
               !sentence.contains(" "); // Single words
    }

    private ScoredClaim analyzeSentenceForClaims(String sentence) {
        String lower = sentence.toLowerCase();
        int score = 0;
        ClaimType type = ClaimType.GENERAL;
        
        // Score based on claim indicators (findings, results, conclusions)
        score += scoreClaimIndicators(lower);
        
        // Score based on quantitative data presence
        if (QUANTITATIVE_PATTERN.matcher(sentence).find()) {
            score += 3;
            type = ClaimType.QUANTITATIVE;
        }
        
        // Score based on statistical language
        if (STATISTICAL_PATTERN.matcher(lower).find()) {
            score += 4;
            type = ClaimType.STATISTICAL;
        }
        
        // Score based on causal language
        if (CAUSAL_PATTERN.matcher(lower).find()) {
            score += 2;
            type = ClaimType.CAUSAL;
        }
        
        // Score based on comparative language
        score += scoreComparativeLanguage(lower);
        
        // Score based on certainty level
        score += scoreCertaintyLevel(lower);
        
        // Penalize background/methodology statements
        score -= penalizeNonClaims(lower);
        
        // Penalize overly complex or unclear sentences
        if (sentence.length() > 200 || countClauses(sentence) > 3) {
            score -= 1;
        }
        
        return score >= 1 ? new ScoredClaim(sentence, score, type) : null;
    }

    private int scoreClaimIndicators(String lower) {
        int score = 0;
        
        // High-value finding indicators
        String[] strongIndicators = {
            "we found", "results show", "findings indicate", "data demonstrate", 
            "analysis reveals", "study shows", "research demonstrates", "evidence suggests",
            "our findings", "results demonstrate", "we demonstrate", "we report",
            "we observed", "investigation revealed", "experiments show"
        };
        
        // Medium-value indicators
        String[] mediumIndicators = {
            "suggests that", "indicates that", "shows that", "reveals that",
            "confirmed that", "established that", "proved that", "validates that"
        };
        
        // Conclusion indicators
        String[] conclusionIndicators = {
            "we conclude", "in conclusion", "these findings", "overall",
            "taken together", "collectively", "our data suggest"
        };
        
        for (String indicator : strongIndicators) {
            if (lower.contains(indicator)) {
                score += 4;
                break; // Only count once per sentence
            }
        }
        
        for (String indicator : mediumIndicators) {
            if (lower.contains(indicator)) {
                score += 2;
                break;
            }
        }
        
        for (String indicator : conclusionIndicators) {
            if (lower.contains(indicator)) {
                score += 3;
                break;
            }
        }
        
        return score;
    }

    private int scoreComparativeLanguage(String lower) {
        String[] comparativeTerms = {
            "higher than", "lower than", "greater than", "less than", "compared to",
            "versus", "relative to", "in comparison", "significantly higher", 
            "significantly lower", "more effective", "less effective", "better than",
            "worse than", "superior to", "inferior to", "outperformed", "exceeded"
        };
        
        for (String term : comparativeTerms) {
            if (lower.contains(term)) {
                return 2;
            }
        }
        return 0;
    }

    private int scoreCertaintyLevel(String lower) {
        // High certainty language
        if (lower.contains("significantly") || lower.contains("substantial") || 
            lower.contains("marked") || lower.contains("pronounced") ||
            lower.contains("clear") || lower.contains("evident")) {
            return 2;
        }
        
        // Uncertain language (lower score)
        if (lower.contains("might") || lower.contains("could") || 
            lower.contains("possibly") || lower.contains("potentially") ||
            lower.contains("appear to") || lower.contains("seem to")) {
            return -1;
        }
        
        return 0;
    }

    private int penalizeNonClaims(String lower) {
        int penalty = 0;
        
        // Background/context statements
        String[] backgroundIndicators = {
            "background", "context", "it is known", "previous studies", 
            "has been shown", "it has been reported", "literature suggests",
            "traditionally", "historically", "commonly used", "widely accepted"
        };
        
        // Methodology statements
        String[] methodologyIndicators = {
            "we used", "we employed", "methodology", "we applied", "approach was",
            "we analyzed", "we performed", "we conducted", "participants were",
            "data were collected", "analysis was performed", "we measured"
        };
        
        // Future work statements
        String[] futureWorkIndicators = {
            "future studies", "further research", "additional work", "future work",
            "needs to be", "should be investigated", "requires further", "warrants investigation"
        };
        
        for (String indicator : backgroundIndicators) {
            if (lower.contains(indicator)) {
                penalty += 2;
                break;
            }
        }
        
        for (String indicator : methodologyIndicators) {
            if (lower.contains(indicator)) {
                penalty += 3;
                break;
            }
        }
        
        for (String indicator : futureWorkIndicators) {
            if (lower.contains(indicator)) {
                penalty += 2;
                break;
            }
        }
        
        return penalty;
    }

    private int countClauses(String sentence) {
        // Enhanced clause counting with sophisticated academic sentence analysis
        if (sentence == null || sentence.trim().isEmpty()) {
            return 0;
        }
        
        String lower = sentence.toLowerCase().trim();
        int count = 1; // Start with 1 for the main clause
        
        // Primary conjunctions that typically introduce new clauses
        String[] primaryConjunctions = {
            " and ", " but ", " or ", " yet ", " so ", " for ", " nor "
        };
        
        // Subordinating conjunctions that introduce dependent clauses
        String[] subordinatingConjunctions = {
            " while ", " although ", " because ", " since ", " if ", " when ", 
            " where ", " unless ", " whereas ", " however ", " moreover ", " furthermore "
        };
        
        // Complex punctuation markers
        String[] punctuationMarkers = {
            "; ", ": ", " - ", " – ", " — "
        };
        
        // Count primary conjunctions
        for (String conjunction : primaryConjunctions) {
            count += countOccurrences(lower, conjunction);
        }
        
        // Count subordinating conjunctions
        for (String conjunction : subordinatingConjunctions) {
            count += countOccurrences(lower, conjunction);
        }
        
        // Count punctuation-based clauses
        for (String punctuation : punctuationMarkers) {
            count += countOccurrences(lower, punctuation);
        }
        
        // Simple comma-based clauses (but be conservative to avoid over-counting)
        int commaCount = countOccurrences(lower, ", ");
        count += Math.min(commaCount, 2); // Max 2 additional clauses from commas
        
        return Math.max(1, count); // Ensure at least 1 clause
    }
    
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    // Helper classes for claim analysis
    private static class ScoredClaim {
        final String text;
        final int score;
        final ClaimType type;
        
        ScoredClaim(String text, int score, ClaimType type) {
            this.text = text;
            this.score = score;
            this.type = type;
        }
    }

    private enum ClaimType {
        GENERAL, QUANTITATIVE, STATISTICAL, CAUSAL, COMPARATIVE
    }

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
     * Build comprehensive research queries based on parameters.
     */
    private List<ResearchQuery> buildResearchQueries(ResearchParameters params) {
        List<ResearchQuery> queries = new ArrayList<>();

        // Fact verification queries
        if (params.shouldVerifyFacts()) {
            for (String claim : params.getKeyClaims()) {
                queries.add(ResearchQuery.factVerification(
                    "Verify the accuracy of this claim: " + claim,
                    params.getPaperContext(),
                    params.getPaperId()
                ));
            }
        }

        // Related papers discovery
        if (params.shouldFindRelatedPapers()) {
            queries.add(ResearchQuery.relatedPapers(
                "Find recent academic papers related to: " + params.getResearchTopic(),
                params.getKeywords()
            ));
        }

        // Trend analysis
        if (params.shouldAnalyzeTrends()) {
            queries.add(ResearchQuery.trendAnalysis(
                "Analyze current research trends in: " + params.getResearchDomain(),
                params.getPaperContext()
            ));
        }

        // Methodology verification
        if (params.shouldVerifyMethodology()) {
            queries.add(ResearchQuery.builder()
                .queryText("Evaluate research methodology: " + params.getMethodologyDescription())
                .context(params.getPaperContext())
                .type(ResearchQuery.ResearchType.METHODOLOGY_VERIFICATION)
                .priority(2)
                .build());
        }

        // Expert opinions
        if (params.shouldGatherExpertOpinions()) {
            queries.add(ResearchQuery.builder()
                .queryText("Find expert opinions on: " + params.getResearchTopic())
                .context(params.getPaperContext())
                .type(ResearchQuery.ResearchType.EXPERT_OPINIONS)
                .keywords(params.getKeywords())
                .priority(3)
                .build());
        }

        return queries;
    }

    /**
     * Execute individual research query using Perplexity API.
     */
    private ResearchResult executeResearchQuery(ResearchQuery query) {
        try {
            LoggingUtil.debug(LOG, "executeResearchQuery", 
                "Executing research query: %s (type: %s)", query.getQueryText(), query.getType());

            // Build optimized prompt for Perplexity
            Prompt researchPrompt = buildPerplexityPrompt(query);

            // Execute query using Perplexity chat client
            ChatResponse response = executePrompt(researchPrompt);

            // Parse response based on query type
            ResearchResult result = parseQueryResponse(response, query);

            LoggingUtil.info(LOG, "executeResearchQuery", 
                "Research query completed: %s sources, confidence: %.2f", 
                result.getSourceCount(), result.getConfidenceScore());

            return result;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "executeResearchQuery", 
                "Failed to execute research query: %s", e, query.getQueryText());
            return ResearchResult.failed(query.getQueryId(), query.getType(), e.getMessage());
        }
    }

    private Prompt buildPerplexityPrompt(ResearchQuery query) {
        String promptText = switch (query.getType()) {
            case FACT_VERIFICATION -> String.format(
                "Verify this claim using authoritative sources: %s\nContext: %s", 
                query.getQueryText(), query.getContext());
            case RELATED_PAPERS -> String.format(
                "Find recent academic papers on: %s\nKeywords: %s", 
                query.getQueryText(), String.join(", ", query.getKeywords()));
            case TREND_ANALYSIS -> String.format(
                "Analyze research trends: %s\nContext: %s", 
                query.getQueryText(), query.getContext());
            default -> query.getQueryText();
        };
        
        return new Prompt(promptText);
    }

    private ResearchResult parseQueryResponse(ChatResponse response, ResearchQuery query) {
        String content = response.getResult().getOutput().getText();
        
        return switch (query.getType()) {
            case FACT_VERIFICATION -> ResearchResponseParser.parseFactVerificationResponse(content, query);
            case RELATED_PAPERS -> ResearchResponseParser.parseRelatedPapersResponse(content, query);
            default -> ResearchResult.successful(query.getQueryId(), query.getType(), content, List.of());
        };
    }

    /**
     * Synthesize research findings into comprehensive result.
     */
    private PerplexityResearchResult synthesizeResearchFindings(
            List<ResearchResult> queryResults, ResearchParameters params) {
        
        try {
            // Generate synthesis using AI
            String synthesisContent = queryResults.stream()
                .map(result -> String.format("Query: %s\nFindings: %s\n", 
                    result.getQueryType(), result.getSummary()))
                .collect(Collectors.joining("\n---\n"));

            Prompt synthesisPrompt = new Prompt(String.format(
                "Synthesize these research findings:\n%s\n\nProvide overall summary and key insights.", 
                synthesisContent));

            ChatResponse synthesisResponse = executePrompt(synthesisPrompt);
            
            // Parse synthesis
            PerplexityResearchResult.ResearchSynthesis synthesis = 
                ResearchResponseParser.parseSynthesis(queryResults, synthesisResponse.getResult().getOutput().getText());

            // Generate fact verifications if requested
            List<PerplexityResearchResult.FactVerification> factVerifications = null;
            if (params.shouldVerifyFacts()) {
                factVerifications = ResearchResponseParser.parseFactVerifications(
                    synthesisResponse.getResult().getOutput().getText(), params.getKeyClaims());
            }

            return PerplexityResearchResult.builder()
                .queryResults(queryResults)
                .synthesis(synthesis)
                .factVerifications(factVerifications)
                .totalProcessingTimeMs(System.currentTimeMillis())
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "synthesizeResearchFindings", 
                "Failed to synthesize research findings", e);
            
            return PerplexityResearchResult.builder()
                .queryResults(queryResults)
                .totalProcessingTimeMs(System.currentTimeMillis())
                .build();
        }
    }

    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        ResearchParameters params = extractResearchParameters(task);
        
        // Base time: 2 minutes + 90 seconds per research type
        long baseSeconds = 120;
        long researchSeconds = params.getActiveResearchTypeCount() * 90;
        long synthesisSeconds = 60;
        
        return Duration.ofSeconds(baseSeconds + researchSeconds + synthesisSeconds);
    }
}

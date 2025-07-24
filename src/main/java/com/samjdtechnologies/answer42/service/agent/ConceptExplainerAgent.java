package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.concept.ConceptExplanation;
import com.samjdtechnologies.answer42.model.concept.ConceptExplanationResult;
import com.samjdtechnologies.answer42.model.concept.ConceptExplanations;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap;
import com.samjdtechnologies.answer42.model.concept.TechnicalTerm;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.EducationLevel;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.ConceptResponseParser;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Concept Explainer Agent using OpenAI GPT-4 for technical term explanation.
 * Generates explanations at different education levels with analogies and relationships.
 */
@Component
public class ConceptExplainerAgent extends OpenAIBasedAgent {
    
    private final ConceptResponseParser responseParser;
    
    public ConceptExplainerAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter,
                                ConceptResponseParser responseParser) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        this.responseParser = responseParser;
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.CONCEPT_EXPLAINER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing concept explanation for task %s", task.getId());
        
        try {
            String paperId = extractPaperIdFromTask(task);
            String content = extractContentFromTask(task);
            
            if (content == null || content.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), "No content provided for concept explanation");
            }
            
            // Step 1: Extract and prioritize technical terms
            List<TechnicalTerm> prioritizedTerms = extractAndPrioritizeTerms(content);
            LoggingUtil.info(LOG, "processWithConfig", 
                "Prioritized %d technical terms for explanation", prioritizedTerms.size());
            
            // Step 2: Generate explanations for multiple education levels in parallel
            Set<EducationLevel> targetLevels = Set.of(
                EducationLevel.HIGH_SCHOOL, EducationLevel.UNDERGRADUATE, 
                EducationLevel.GRADUATE, EducationLevel.EXPERT
            );
            
            Map<EducationLevel, CompletableFuture<ConceptExplanations>> explanationFutures = 
                targetLevels.stream()
                    .collect(Collectors.toMap(
                        level -> level,
                        level -> CompletableFuture.supplyAsync(
                            () -> generateExplanationsForLevel(prioritizedTerms, content, level),
                            taskExecutor)
                    ));
            
            // Step 3: Generate concept relationship map in parallel
            CompletableFuture<ConceptRelationshipMap> relationshipFuture = 
                CompletableFuture.supplyAsync(
                    () -> generateRelationshipMap(prioritizedTerms, content), 
                    taskExecutor);
            
            // Wait for all tasks to complete
            CompletableFuture.allOf(
                Stream.concat(
                    explanationFutures.values().stream(),
                    Stream.of(relationshipFuture)
                ).toArray(CompletableFuture[]::new)
            ).join();
            
            // Collect results
            Map<EducationLevel, ConceptExplanations> explanations = explanationFutures.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().join()
                ));
            
            ConceptRelationshipMap relationshipMap = relationshipFuture.join();
            
            // Create final result
            ConceptExplanationResult result = new ConceptExplanationResult(explanations, relationshipMap);
            result.addMetadata("paperId", paperId);
            result.addMetadata("termCount", prioritizedTerms.size());
            result.addMetadata("processingTimeMs", System.currentTimeMillis());
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Generated concept explanations with quality score: %.2f", 
                result.getOverallQualityScore());
            
            return AgentResult.success(task.getId(), result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Failed to process concept explanation", e);
            return AgentResult.failure(task.getId(), e.getMessage());
        }
    }
    
    private List<TechnicalTerm> extractAndPrioritizeTerms(String content) {
        String templateString = """
            Extract technical terms from this academic content that would benefit from explanation.
            
            Content: {content}
            
            For each term, provide:
            1. The exact term as it appears
            2. Term type (concept, acronym, mathematical, domain_specific, methodology, tool)
            3. Estimated complexity (0.0 to 1.0, where 1.0 is most complex)
            4. Context where it appears
            
            Focus on technical/scientific concepts, acronyms, mathematical terms, methodologies, and tools.
            Exclude common words and basic terms.
            
            Return as JSON array where each object has fields: term, type, complexity, context
            """;
        
        // Clean the content to avoid template parsing issues
        String cleanContent = cleanContentForTemplate(content);
        Prompt extractionPrompt = optimizePromptForOpenAI(templateString, 
            Map.of("content", truncateContent(cleanContent, 4000)));
        
        try {
            ChatResponse response = executePromptWithRetry(extractionPrompt).join();
            String responseContent = response.getResult().getOutput().getText();
            
            List<TechnicalTerm> terms = responseParser.parseTermsFromResponse(responseContent);
            
            // Prioritize terms based on complexity and importance
            return terms.stream()
                .sorted((a, b) -> Double.compare(b.getPriorityScore(), a.getPriorityScore()))
                .limit(20) // Top 20 terms
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "extractAndPrioritizeTerms", 
                "Failed to extract technical terms", e);
            return List.of();
        }
    }
    
    private ConceptExplanations generateExplanationsForLevel(
            List<TechnicalTerm> terms, String content, EducationLevel level) {
        
        LoggingUtil.info(LOG, "generateExplanationsForLevel", 
            "Generating explanations for %s level with %d terms", 
            level.getDisplayName(), terms.size());
        
        // Process terms in batches to avoid token limits
        List<List<TechnicalTerm>> termBatches = partitionList(terms, 5);
        Map<String, ConceptExplanation> allExplanations = new HashMap<>();
        
        for (List<TechnicalTerm> batch : termBatches) {
            Map<String, ConceptExplanation> batchExplanations = 
                generateBatchExplanations(batch, content, level);
            allExplanations.putAll(batchExplanations);
        }
        
        return new ConceptExplanations(level, allExplanations);
    }
    
    private Map<String, ConceptExplanation> generateBatchExplanations(
            List<TechnicalTerm> terms, String content, EducationLevel level) {
        
        String termsList = terms.stream()
            .map(TechnicalTerm::getTerm)
            .collect(Collectors.joining(", "));
        
        String templateString = """
            Explain these technical concepts for a {level} education level audience:
            
            Paper Context: {context}
            Terms to Explain: {terms}
            
            For each term, provide:
            1. Clear definition appropriate for {level} level
            2. Real-world analogy (if helpful for understanding)
            3. Why it's important in this research context
            4. Related concepts the reader should know
            5. Common misconceptions to avoid
            6. Prerequisites needed to understand this concept
            
            Guidelines for {level} level: {guidelines}
            
            Return as JSON object with each term as a key. Each term should have fields:
            definition, analogy, importance, relatedConcepts (array), commonMisconceptions (array), prerequisites (array), confidence (number)
            """;
        
        // Clean inputs to avoid template parsing issues
        String cleanContext = cleanContentForTemplate(content);
        String cleanTerms = cleanContentForTemplate(termsList);
        String cleanGuidelines = cleanContentForTemplate(getGuidelinesForLevel(level));
        
        Prompt explanationPrompt = optimizePromptForOpenAI(templateString, Map.of(
                "level", level.getDisplayName(),
                "context", truncateContent(cleanContext, 2000),
                "terms", cleanTerms,
                "guidelines", cleanGuidelines
            ));
        
        try {
            ChatResponse response = executePromptWithRetry(explanationPrompt).join();
            String responseContent = response.getResult().getOutput().getText();
            
            return responseParser.parseExplanationsFromResponse(responseContent, level);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "generateBatchExplanations", 
                "Failed to generate explanations for batch", e);
            return Map.of();
        }
    }
    
    private ConceptRelationshipMap generateRelationshipMap(
            List<TechnicalTerm> terms, String content) {
        
        String termsList = terms.stream()
            .map(TechnicalTerm::getTerm)
            .collect(Collectors.joining(", "));
        
        String templateString = """
            Analyze the relationships between these technical concepts in this research context:
            
            Paper Context: {context}
            Concepts: {terms}
            
            Identify relationships: HIERARCHICAL, CAUSAL, DEPENDENCY, SIMILARITY, OPPOSITION, TEMPORAL, COMPONENT
            
            Return as JSON with nodes array (concept, description, importance) and edges array (fromConcept, toConcept, type, description, strength)
            """;
        
        // Clean inputs to avoid template parsing issues
        String cleanContext = cleanContentForTemplate(content);
        String cleanTerms = cleanContentForTemplate(termsList);
        
        Prompt relationshipPrompt = optimizePromptForOpenAI(templateString, Map.of(
                "context", truncateContent(cleanContext, 2000),
                "terms", cleanTerms
            ));
        
        try {
            ChatResponse response = executePromptWithRetry(relationshipPrompt).join();
            String responseContent = response.getResult().getOutput().getText();
            
            return responseParser.parseRelationshipMapFromResponse(responseContent);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "generateRelationshipMap", 
                "Failed to generate relationship map", e);
            return ConceptRelationshipMap.builder().build();
        }
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        String content = extractContentFromTask(task);
        if (content == null) {
            return Duration.ofMinutes(2);
        }
        
        // Estimate based on content length and complexity
        int contentLength = content.length();
        int estimatedTerms = Math.min(contentLength / 200, 20);
        int educationLevels = 4;
        
        // Base: 60 seconds + 15 seconds per term per level + relationship mapping
        long baseSeconds = 60;
        long termSeconds = estimatedTerms * educationLevels * 15;
        long relationshipSeconds = 30;
        
        return Duration.ofSeconds(baseSeconds + termSeconds + relationshipSeconds);
    }
    
    // Helper methods
    private String extractPaperIdFromTask(AgentTask task) {
        JsonNode input = task.getInput();
        return input.has("paperId") ? input.get("paperId").asText() : null;
    }
    
    private String extractContentFromTask(AgentTask task) {
        JsonNode input = task.getInput();
        return input.has("content") ? input.get("content").asText() : null;
    }
    
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
    
    private String getGuidelinesForLevel(EducationLevel level) {
        return switch (level) {
            case HIGH_SCHOOL -> "Use simple language, avoid jargon, include basic analogies";
            case UNDERGRADUATE -> "Clear explanations with some technical detail, helpful examples";
            case GRADUATE -> "Detailed explanations, technical accuracy, advanced concepts";
            case EXPERT -> "Comprehensive explanations, full technical depth, nuanced understanding";
        };
    }
    
    /**
     * Clean content to avoid template parsing issues with special characters.
     */
    private String cleanContentForTemplate(String content) {
        if (content == null) {
            return "";
        }
        
        // Remove or escape characters that might cause template parsing issues
        return content
            .replace("\\", "\\\\")  // Escape backslashes
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", " ")     // Replace newlines with spaces
            .replace("\r", " ")     // Replace carriage returns with spaces
            .replace("\t", " ")     // Replace tabs with spaces
            .replaceAll("\\s+", " ") // Normalize whitespace
            .trim();
    }
    
    private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}

package com.samjdtechnologies.answer42.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.model.concept.ConceptExplanation;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap.ConceptEdge;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap.ConceptNode;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap.RelationshipType;
import com.samjdtechnologies.answer42.model.concept.TechnicalTerm;
import com.samjdtechnologies.answer42.model.enums.DifficultyLevel;
import com.samjdtechnologies.answer42.model.enums.EducationLevel;
import com.samjdtechnologies.answer42.model.enums.TermType;

/**
 * Utility class for parsing AI responses into concept explanation objects.
 * Handles JSON parsing with robust error handling and fallbacks.
 */
@Component
public class ConceptResponseParser {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConceptResponseParser.class);

    private final ObjectMapper objectMapper;
    
    public ConceptResponseParser() {
        this.objectMapper = new ObjectMapper();
        LoggingUtil.debug(LOG, "ConceptResponseParser", "ConceptResponseParser initialized");
    }
    
    /**
     * Parse technical terms from AI response.
     */
    public List<TechnicalTerm> parseTermsFromResponse(String response) {
        try {
            String jsonPart = extractJsonFromResponse(response, true);
            JsonNode jsonArray = objectMapper.readTree(jsonPart);
            
            List<TechnicalTerm> terms = new ArrayList<>();
            
            if (jsonArray.isArray()) {
                for (JsonNode termNode : jsonArray) {
                    try {
                        TechnicalTerm term = parseSingleTerm(termNode);
                        if (term != null) {
                            terms.add(term);
                        }
                    } catch (Exception e) {
                        LoggingUtil.warn(null, "parseTermsFromResponse", 
                            "Failed to parse individual term: %s", e.getMessage());
                    }
                }
            }
            
            return terms;
            
        } catch (Exception e) {
            LoggingUtil.error(null, "parseTermsFromResponse", 
                "Failed to parse terms JSON response", e);
            return List.of();
        }
    }
    
    /**
     * Parse concept explanations from AI response.
     */
    public Map<String, ConceptExplanation> parseExplanationsFromResponse(
            String response, EducationLevel level) {
        
        Map<String, ConceptExplanation> explanations = new HashMap<>();
        
        try {
            String jsonPart = extractJsonFromResponse(response, false);
            JsonNode explanationNode = objectMapper.readTree(jsonPart);
            
            if (explanationNode.isObject()) {
                explanationNode.fields().forEachRemaining(entry -> {
                    try {
                        String term = entry.getKey();
                        JsonNode termData = entry.getValue();
                        
                        ConceptExplanation explanation = parseSingleExplanation(term, termData, level);
                        if (explanation != null) {
                            explanations.put(term.toLowerCase(), explanation);
                        }
                        
                    } catch (Exception e) {
                        LoggingUtil.warn(null, "parseExplanationsFromResponse", 
                            "Failed to parse explanation for term: %s", e.getMessage());
                    }
                });
            }
            
        } catch (Exception e) {
            LoggingUtil.error(null, "parseExplanationsFromResponse", 
                "Failed to parse explanations JSON response", e);
        }
        
        return explanations;
    }
    
    /**
     * Parse concept relationship map from AI response.
     */
    public ConceptRelationshipMap parseRelationshipMapFromResponse(String response) {
        try {
            String jsonPart = extractJsonFromResponse(response, false);
            JsonNode mapNode = objectMapper.readTree(jsonPart);
            
            List<ConceptNode> nodes = parseNodes(mapNode.get("nodes"));
            List<ConceptEdge> edges = parseEdges(mapNode.get("edges"));
            
            ConceptRelationshipMap map = ConceptRelationshipMap.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("parsedAt", System.currentTimeMillis());
            metadata.put("nodeCount", nodes.size());
            metadata.put("edgeCount", edges.size());
            map.setMetadata(metadata);
            
            return map;
            
        } catch (Exception e) {
            LoggingUtil.error(null, "parseRelationshipMapFromResponse", 
                "Failed to parse relationship map", e);
            return ConceptRelationshipMap.builder().build();
        }
    }
    
    // Private helper methods
    
    private TechnicalTerm parseSingleTerm(JsonNode termNode) {
        if (!termNode.has("term")) {
            return null;
        }
        
        String term = termNode.get("term").asText();
        String typeStr = termNode.has("type") ? termNode.get("type").asText() : "concept";
        double complexity = termNode.has("complexity") ? termNode.get("complexity").asDouble() : 0.5;
        String context = termNode.has("context") ? termNode.get("context").asText() : "";
        
        TermType type = parseTermType(typeStr);
        
        return TechnicalTerm.builder()
            .term(term)
            .type(type)
            .frequency(1)
            .complexity(complexity)
            .context(context)
            .build();
    }
    
    private ConceptExplanation parseSingleExplanation(String term, JsonNode termData, EducationLevel level) {
        if (!termData.has("definition")) {
            return null;
        }
        
        String definition = termData.get("definition").asText();
        String analogy = termData.has("analogy") ? termData.get("analogy").asText() : "";
        String importance = termData.has("importance") ? termData.get("importance").asText() : "";
        double confidence = termData.has("confidence") ? termData.get("confidence").asDouble() : 0.8;
        
        List<String> relatedConcepts = parseStringArray(termData.get("relatedConcepts"));
        List<String> prerequisites = parseStringArray(termData.get("prerequisites"));
        List<String> misconceptions = parseStringArray(termData.get("commonMisconceptions"));
        
        // Determine difficulty based on education level and complexity indicators
        DifficultyLevel difficulty = determineDifficulty(definition, level);
        
        return ConceptExplanation.builder()
            .term(term)
            .definition(definition)
            .analogy(analogy.isEmpty() ? null : analogy)
            .importance(importance)
            .relatedConcepts(relatedConcepts)
            .prerequisites(prerequisites)
            .commonMisconceptions(misconceptions)
            .difficulty(difficulty)
            .targetLevel(level)
            .confidence(confidence)
            .source("AI Generated - OpenAI GPT-4")
            .build();
    }
    
    private List<ConceptNode> parseNodes(JsonNode nodesArray) {
        List<ConceptNode> nodes = new ArrayList<>();
        
        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode nodeJson : nodesArray) {
                try {
                    String concept = nodeJson.get("concept").asText();
                    String description = nodeJson.has("description") ? 
                        nodeJson.get("description").asText() : "";
                    double importance = nodeJson.has("importance") ? 
                        nodeJson.get("importance").asDouble() : 0.5;
                    
                    Map<String, Object> properties = new HashMap<>();
                    if (nodeJson.has("properties") && nodeJson.get("properties").isObject()) {
                        nodeJson.get("properties").fields().forEachRemaining(entry -> 
                            properties.put(entry.getKey(), entry.getValue().asText()));
                    }
                    
                    ConceptNode node = ConceptNode.builder()
                        .concept(concept)
                        .description(description)
                        .importance(importance)
                        .properties(properties)
                        .build();
                    
                    nodes.add(node);
                    
                } catch (Exception e) {
                    LoggingUtil.warn(null, "parseNodes", 
                        "Failed to parse individual node: %s", e.getMessage());
                }
            }
        }
        
        return nodes;
    }
    
    private List<ConceptEdge> parseEdges(JsonNode edgesArray) {
        List<ConceptEdge> edges = new ArrayList<>();
        
        if (edgesArray != null && edgesArray.isArray()) {
            for (JsonNode edgeJson : edgesArray) {
                try {
                    String fromConcept = edgeJson.get("fromConcept").asText();
                    String toConcept = edgeJson.get("toConcept").asText();
                    String typeStr = edgeJson.get("type").asText();
                    String description = edgeJson.has("description") ? 
                        edgeJson.get("description").asText() : "";
                    double strength = edgeJson.has("strength") ? 
                        edgeJson.get("strength").asDouble() : 0.5;
                    
                    RelationshipType type = parseRelationshipType(typeStr);
                    
                    Map<String, Object> properties = new HashMap<>();
                    if (edgeJson.has("properties") && edgeJson.get("properties").isObject()) {
                        edgeJson.get("properties").fields().forEachRemaining(entry -> 
                            properties.put(entry.getKey(), entry.getValue().asText()));
                    }
                    
                    ConceptEdge edge = ConceptEdge.builder()
                        .fromConcept(fromConcept)
                        .toConcept(toConcept)
                        .type(type)
                        .description(description)
                        .strength(strength)
                        .properties(properties)
                        .build();
                    
                    edges.add(edge);
                    
                } catch (Exception e) {
                    LoggingUtil.warn(null, "parseEdges", 
                        "Failed to parse individual edge: %s", e.getMessage());
                }
            }
        }
        
        return edges;
    }
    
    private List<String> parseStringArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new ArrayList<>();
        }
        
        return StreamSupport.stream(arrayNode.spliterator(), false)
            .map(JsonNode::asText)
            .filter(s -> !s.trim().isEmpty())
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    private String extractJsonFromResponse(String response, boolean isArray) {
        if (response == null || response.trim().isEmpty()) {
            return isArray ? "[]" : "{}";
        }
        
        // Clean up response - remove markdown code blocks
        String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
        
        // Find JSON in response
        char startChar = isArray ? '[' : '{';
        char endChar = isArray ? ']' : '}';
        
        int start = cleaned.indexOf(startChar);
        int end = cleaned.lastIndexOf(endChar);
        
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        
        // If no proper JSON markers found, try to find any JSON-like structure
        if (cleaned.contains("\"") && (cleaned.contains("{") || cleaned.contains("["))) {
            return cleaned;
        }
        
        // Return empty structure as fallback
        return isArray ? "[]" : "{}";
    }
    
    private TermType parseTermType(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            return TermType.CONCEPT;
        }
        
        try {
            return TermType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to map common variations
            String normalized = typeStr.toLowerCase().trim();
            return switch (normalized) {
                case "mathematical", "math", "statistical" -> TermType.MATHEMATICAL;
                case "domain_specific", "domain", "field" -> TermType.DOMAIN_SPECIFIC;
                case "method", "methodology" -> TermType.METHODOLOGY;
                case "acronym", "abbreviation" -> TermType.ACRONYM;
                case "tool", "technique", "software" -> TermType.TOOL;
                default -> TermType.CONCEPT;
            };
        }
    }
    
    private RelationshipType parseRelationshipType(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            return RelationshipType.SIMILARITY;
        }
        
        try {
            return RelationshipType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to map common variations
            String normalized = typeStr.toLowerCase().trim();
            return switch (normalized) {
                case "hierarchical", "hierarchy", "parent-child" -> RelationshipType.HIERARCHICAL;
                case "causal", "cause-effect", "causation" -> RelationshipType.CAUSAL;
                case "dependency", "depends", "requires" -> RelationshipType.DEPENDENCY;
                case "similarity", "similar", "alike" -> RelationshipType.SIMILARITY;
                case "opposition", "opposite", "contrast" -> RelationshipType.OPPOSITION;
                case "temporal", "sequence", "time" -> RelationshipType.TEMPORAL;
                case "component", "part", "whole" -> RelationshipType.COMPONENT;
                default -> RelationshipType.SIMILARITY;
            };
        }
    }
    
    private DifficultyLevel determineDifficulty(String definition, EducationLevel level) {
        // Simple heuristic based on definition complexity and education level
        int complexityScore = 0;
        
        // Count technical indicators
        String lowerDef = definition.toLowerCase();
        if (lowerDef.contains("algorithm") || lowerDef.contains("mathematical")) complexityScore++;
        if (lowerDef.contains("statistical") || lowerDef.contains("probability")) complexityScore++;
        if (lowerDef.contains("theoretical") || lowerDef.contains("abstract")) complexityScore++;
        if (definition.length() > 200) complexityScore++;
        
        // Adjust based on target education level
        return switch (level) {
            case HIGH_SCHOOL -> complexityScore >= 2 ? DifficultyLevel.HARD : DifficultyLevel.EASY;
            case UNDERGRADUATE -> complexityScore >= 3 ? DifficultyLevel.HARD : 
                                 complexityScore >= 1 ? DifficultyLevel.MEDIUM : DifficultyLevel.EASY;
            case GRADUATE -> complexityScore >= 3 ? DifficultyLevel.EXPERT : 
                           complexityScore >= 1 ? DifficultyLevel.HARD : DifficultyLevel.MEDIUM;
            case EXPERT -> complexityScore >= 2 ? DifficultyLevel.EXPERT : DifficultyLevel.HARD;
        };
    }
}

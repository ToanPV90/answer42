package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Metadata Enhancement Agent - Enriches paper metadata from external sources.
 * Uses OpenAI GPT-4 for intelligent metadata synthesis and conflict resolution.
 */
@Component
public class MetadataEnhancementAgent extends OpenAIBasedAgent {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Regex patterns for metadata extraction
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,}/[\\w\\-\\.;/]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern JOURNAL_PATTERN = Pattern.compile("(?i)journal\\s*[:\"]\\s*([^\\n\\r,\"]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUBLISHER_PATTERN = Pattern.compile("(?i)publisher\\s*[:\"]\\s*([^\\n\\r,\"]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("(?i)confidence\\s*[:\"]?\\s*(\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHOR_COUNT_PATTERN = Pattern.compile("(?i)author[s]?\\s*count\\s*[:\"]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITATION_COUNT_PATTERN = Pattern.compile("(?i)citation[s]?\\s*count\\s*[:\"]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    
    public MetadataEnhancementAgent(AIConfig aiConfig, ThreadConfig threadConfig) {
        super(aiConfig, threadConfig);
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.METADATA_ENHANCER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", "Processing metadata enhancement for task %s", task.getId());
        
        try {
            // Extract task input
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            String title = input.has("title") ? input.get("title").asText() : null;
            String doi = input.has("doi") ? input.get("doi").asText() : null;
            JsonNode authors = input.has("authors") ? input.get("authors") : null;
            
            if (title == null || title.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), "No title provided for metadata enhancement");
            }
            
            // Execute parallel metadata enhancement from multiple sources
            EnhancementResult enhancementResult = enhanceMetadataFromSources(paperId, title, doi, authors);
            
            // Create result data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("enhancedMetadata", enhancementResult.getMetadata());
            resultData.put("sources", enhancementResult.getSources());
            resultData.put("confidence", enhancementResult.getConfidence());
            resultData.put("conflicts", enhancementResult.getConflicts());
            resultData.put("processingNotes", enhancementResult.getProcessingNotes());
            
            return AgentResult.success(task.getId(), resultData);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", "Failed to enhance metadata", e);
            return AgentResult.failure(task.getId(), "Metadata enhancement failed: " + e.getMessage());
        }
    }
    
    /**
     * Enhances metadata using multiple external sources in parallel.
     */
    private EnhancementResult enhanceMetadataFromSources(String paperId, String title, String doi, JsonNode authors) {
        LoggingUtil.info(LOG, "enhanceMetadataFromSources", "Enhancing metadata for paper %s", paperId);
        
        // Execute multiple enhancement sources in parallel using ThreadConfig executor
        List<CompletableFuture<MetadataSource>> enhancementFutures = List.of(
            CompletableFuture.supplyAsync(() -> enhanceWithCrossref(title, doi), taskExecutor),
            CompletableFuture.supplyAsync(() -> enhanceWithSemanticScholar(title, doi), taskExecutor),
            CompletableFuture.supplyAsync(() -> enhanceWithDOIResolution(doi), taskExecutor),
            CompletableFuture.supplyAsync(() -> enhanceWithAuthorDisambiguation(authors), taskExecutor)
        );
        
        // Wait for all enhancements to complete
        CompletableFuture.allOf(enhancementFutures.toArray(new CompletableFuture[0])).join();
        
        // Collect results
        List<MetadataSource> sources = enhancementFutures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // Synthesize results using AI
        return synthesizeMetadataWithAI(title, doi, sources);
    }
    
    /**
     * Enhanced metadata using Crossref API.
     */
    private MetadataSource enhanceWithCrossref(String title, String doi) {
        try {
            LoggingUtil.debug(LOG, "enhanceWithCrossref", "Querying Crossref for title: %s", title);
            
            // Simulate Crossref API call (in real implementation would use actual API)
            Map<String, Object> crossrefData = new HashMap<>();
            crossrefData.put("source", "crossref");
            crossrefData.put("title", title);
            crossrefData.put("publisher", "Academic Publisher");
            crossrefData.put("publicationDate", "2023");
            crossrefData.put("journal", "Journal of Academic Research");
            crossrefData.put("volume", "42");
            crossrefData.put("issue", "3");
            crossrefData.put("pages", "123-145");
            
            if (doi != null) {
                crossrefData.put("doi", doi);
                crossrefData.put("confidence", 0.95);
            } else {
                crossrefData.put("confidence", 0.75);
            }
            
            return new MetadataSource("crossref", crossrefData, 
                (Double) crossrefData.get("confidence"));
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "enhanceWithCrossref", "Crossref enhancement failed", e);
            return null;
        }
    }
    
    /**
     * Enhanced metadata using Semantic Scholar API.
     */
    private MetadataSource enhanceWithSemanticScholar(String title, String doi) {
        try {
            LoggingUtil.debug(LOG, "enhanceWithSemanticScholar", "Querying Semantic Scholar for title: %s", title);
            
            // Simulate Semantic Scholar API call
            Map<String, Object> semanticData = new HashMap<>();
            semanticData.put("source", "semantic_scholar");
            semanticData.put("title", title);
            semanticData.put("citationCount", 42);
            semanticData.put("influentialCitationCount", 15);
            semanticData.put("venue", "Conference on Research");
            semanticData.put("year", 2023);
            semanticData.put("authors", List.of("Dr. Jane Smith", "Prof. John Doe"));
            semanticData.put("confidence", 0.85);
            
            return new MetadataSource("semantic_scholar", semanticData, 0.85);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "enhanceWithSemanticScholar", "Semantic Scholar enhancement failed", e);
            return null;
        }
    }
    
    /**
     * Enhanced metadata using DOI resolution.
     */
    private MetadataSource enhanceWithDOIResolution(String doi) {
        if (doi == null || doi.trim().isEmpty()) {
            return null;
        }
        
        try {
            LoggingUtil.debug(LOG, "enhanceWithDOIResolution", "Resolving DOI: %s", doi);
            
            // Simulate DOI resolution
            Map<String, Object> doiData = new HashMap<>();
            doiData.put("source", "doi_resolution");
            doiData.put("doi", doi);
            doiData.put("type", "journal-article");
            doiData.put("isValid", true);
            doiData.put("registrant", "CrossRef");
            doiData.put("confidence", 0.98);
            
            return new MetadataSource("doi_resolution", doiData, 0.98);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "enhanceWithDOIResolution", "DOI resolution failed", e);
            return null;
        }
    }
    
    /**
     * Enhanced author information with disambiguation.
     */
    private MetadataSource enhanceWithAuthorDisambiguation(JsonNode authors) {
        if (authors == null || !authors.isArray()) {
            return null;
        }
        
        try {
            LoggingUtil.debug(LOG, "enhanceWithAuthorDisambiguation", "Processing %d authors", authors.size());
            
            // Simulate author disambiguation
            Map<String, Object> authorData = new HashMap<>();
            authorData.put("source", "author_disambiguation");
            authorData.put("authorCount", authors.size());
            authorData.put("disambiguated", true);
            authorData.put("orcidFound", 2);
            authorData.put("affiliationResolved", 3);
            authorData.put("confidence", 0.80);
            
            return new MetadataSource("author_disambiguation", authorData, 0.80);
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "enhanceWithAuthorDisambiguation", "Author disambiguation failed", e);
            return null;
        }
    }
    
    /**
     * Synthesizes metadata from multiple sources using OpenAI GPT-4.
     */
    private EnhancementResult synthesizeMetadataWithAI(String title, String doi, List<MetadataSource> sources) {
        LoggingUtil.info(LOG, "synthesizeMetadataWithAI", "Synthesizing metadata from %d sources", sources.size());
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", title);
        variables.put("doi", doi != null ? doi : "Not available");
        variables.put("sources", sources.stream()
            .map(MetadataSource::getDetailedDescription)
            .collect(Collectors.joining("\n\n")));
        
        String synthesisPrompt = """
            Synthesize the following metadata from multiple sources into a coherent, authoritative record.
            Resolve conflicts by prioritizing sources based on their confidence scores and reliability.
            
            Paper Title: {title}
            DOI: {doi}
            
            Metadata Sources:
            {sources}
            
            Return a JSON object with the following structure:
            {
              "title": "synthesized title",
              "doi": "synthesized doi",
              "year": "publication year",
              "journal": "journal name",
              "publisher": "publisher name",
              "volume": "volume number",
              "issue": "issue number",
              "pages": "page range",
              "authors": ["author1", "author2"],
              "citationCount": number,
              "publicationType": "journal-article",
              "confidence": {
                "title": 0.95,
                "doi": 0.98,
                "year": 0.90,
                "journal": 0.85,
                "overall": 0.92
              },
              "conflicts": ["list of any conflicts found"],
              "sources": ["list of sources used"]
            }
            
            Ensure all numeric values are properly formatted and all text fields are cleaned.
            """;
        
        Prompt prompt = optimizePromptForOpenAI(synthesisPrompt, variables);
        ChatResponse response = executePrompt(prompt);
        
        String aiResponse = response.getResult().getOutput().toString();
        
        return parseEnhancementResponse(aiResponse, sources);
    }
    
    /**
     * Parses the AI response into a structured enhancement result.
     */
    private EnhancementResult parseEnhancementResponse(String aiResponse, List<MetadataSource> sources) {
        try {
            // Parse AI response to extract structured metadata
            Map<String, Object> enhancedMetadata = parseMetadataFromResponse(aiResponse);
            
            // Calculate overall confidence
            double confidence = calculateOverallConfidence(sources, enhancedMetadata);
            
            // Identify conflicts
            List<String> conflicts = identifyConflicts(sources, enhancedMetadata);
            
            return EnhancementResult.builder()
                .metadata(enhancedMetadata)
                .sources(sources)
                .confidence(confidence)
                .conflicts(conflicts)
                .processingNotes("Enhanced with OpenAI GPT-4 synthesis from " + sources.size() + " sources")
                .build();
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseEnhancementResponse", "Failed to parse enhancement response", e);
            
            // Create fallback result
            return createFallbackResult(sources);
        }
    }
    
    /**
     * Enhanced metadata parsing from AI response with multiple strategies.
     */
    private Map<String, Object> parseMetadataFromResponse(String response) {
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            // Strategy 1: Try to parse as JSON first
            Map<String, Object> jsonMetadata = parseJsonFromResponse(response);
            if (!jsonMetadata.isEmpty()) {
                LoggingUtil.debug(LOG, "parseMetadataFromResponse", "Successfully parsed JSON metadata");
                return jsonMetadata;
            }
            
            // Strategy 2: Extract using regex patterns
            Map<String, Object> regexMetadata = parseUsingRegexPatterns(response);
            if (!regexMetadata.isEmpty()) {
                LoggingUtil.debug(LOG, "parseMetadataFromResponse", "Successfully parsed metadata using regex");
                return regexMetadata;
            }
            
            // Strategy 3: Structured text parsing
            Map<String, Object> structuredMetadata = parseStructuredText(response);
            if (!structuredMetadata.isEmpty()) {
                LoggingUtil.debug(LOG, "parseMetadataFromResponse", "Successfully parsed structured text");
                return structuredMetadata;
            }
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "parseMetadataFromResponse", "Error in metadata parsing", e);
        }
        
        // Fallback: basic metadata
        metadata.put("enhanced", true);
        metadata.put("parseStrategy", "fallback");
        metadata.put("processingTimestamp", System.currentTimeMillis());
        
        return metadata;
    }
    
    /**
     * Attempts to parse JSON from the AI response.
     */
    private Map<String, Object> parseJsonFromResponse(String response) {
        try {
            // Find JSON object in response
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');
            
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                JsonNode jsonNode = objectMapper.readTree(jsonStr);
                
                Map<String, Object> metadata = new HashMap<>();
                
                // Extract standard fields
                extractStringField(jsonNode, "title", metadata);
                extractStringField(jsonNode, "doi", metadata);
                extractStringField(jsonNode, "journal", metadata);
                extractStringField(jsonNode, "publisher", metadata);
                extractStringField(jsonNode, "volume", metadata);
                extractStringField(jsonNode, "issue", metadata);
                extractStringField(jsonNode, "pages", metadata);
                extractStringField(jsonNode, "publicationType", metadata);
                
                // Extract numeric fields
                extractNumericField(jsonNode, "year", metadata);
                extractNumericField(jsonNode, "citationCount", metadata);
                
                // Extract arrays
                extractArrayField(jsonNode, "authors", metadata);
                extractArrayField(jsonNode, "conflicts", metadata);
                extractArrayField(jsonNode, "sources", metadata);
                
                // Extract confidence object
                if (jsonNode.has("confidence") && jsonNode.get("confidence").isObject()) {
                    JsonNode confidenceNode = jsonNode.get("confidence");
                    Map<String, Double> confidenceMap = new HashMap<>();
                    confidenceNode.fieldNames().forEachRemaining(fieldName -> {
                        JsonNode fieldValue = confidenceNode.get(fieldName);
                        if (fieldValue.isNumber()) {
                            confidenceMap.put(fieldName, fieldValue.asDouble());
                        }
                    });
                    metadata.put("confidence", confidenceMap);
                }
                
                metadata.put("parseStrategy", "json");
                return metadata;
            }
            
        } catch (JsonProcessingException e) {
            LoggingUtil.debug(LOG, "parseJsonFromResponse", "Failed to parse JSON: %s", e.getMessage());
        }
        
        return new HashMap<>();
    }
    
    /**
     * Extracts metadata using regex patterns.
     */
    private Map<String, Object> parseUsingRegexPatterns(String response) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract DOI
        Matcher doiMatcher = DOI_PATTERN.matcher(response);
        if (doiMatcher.find()) {
            metadata.put("doi", doiMatcher.group().trim());
        }
        
        // Extract year
        Matcher yearMatcher = YEAR_PATTERN.matcher(response);
        if (yearMatcher.find()) {
            try {
                metadata.put("year", Integer.parseInt(yearMatcher.group()));
            } catch (NumberFormatException e) {
                LoggingUtil.debug(LOG, "parseUsingRegexPatterns", "Failed to parse year: %s", yearMatcher.group());
            }
        }
        
        // Extract journal
        Matcher journalMatcher = JOURNAL_PATTERN.matcher(response);
        if (journalMatcher.find()) {
            metadata.put("journal", journalMatcher.group(1).trim());
        }
        
        // Extract publisher
        Matcher publisherMatcher = PUBLISHER_PATTERN.matcher(response);
        if (publisherMatcher.find()) {
            metadata.put("publisher", publisherMatcher.group(1).trim());
        }
        
        // Extract confidence
        Matcher confidenceMatcher = CONFIDENCE_PATTERN.matcher(response);
        if (confidenceMatcher.find()) {
            try {
                metadata.put("overallConfidence", Double.parseDouble(confidenceMatcher.group(1)));
            } catch (NumberFormatException e) {
                LoggingUtil.debug(LOG, "parseUsingRegexPatterns", "Failed to parse confidence: %s", confidenceMatcher.group(1));
            }
        }
        
        // Extract author count
        Matcher authorCountMatcher = AUTHOR_COUNT_PATTERN.matcher(response);
        if (authorCountMatcher.find()) {
            try {
                metadata.put("authorCount", Integer.parseInt(authorCountMatcher.group(1)));
            } catch (NumberFormatException e) {
                LoggingUtil.debug(LOG, "parseUsingRegexPatterns", "Failed to parse author count: %s", authorCountMatcher.group(1));
            }
        }
        
        // Extract citation count
        Matcher citationCountMatcher = CITATION_COUNT_PATTERN.matcher(response);
        if (citationCountMatcher.find()) {
            try {
                metadata.put("citationCount", Integer.parseInt(citationCountMatcher.group(1)));
            } catch (NumberFormatException e) {
                LoggingUtil.debug(LOG, "parseUsingRegexPatterns", "Failed to parse citation count: %s", citationCountMatcher.group(1));
            }
        }
        
        if (!metadata.isEmpty()) {
            metadata.put("parseStrategy", "regex");
        }
        
        return metadata;
    }
    
    /**
     * Parses structured text format (key: value pairs).
     */
    private Map<String, Object> parseStructuredText(String response) {
        Map<String, Object> metadata = new HashMap<>();
        
        String[] lines = response.split("\\r?\\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim().toLowerCase().replaceAll("\\s+", "");
                    String value = parts[1].trim().replaceAll("^[\"']|[\"']$", ""); // Remove quotes
                    
                    if (!value.isEmpty()) {
                        // Try to parse numeric values
                        if (key.contains("year") || key.contains("count")) {
                            try {
                                metadata.put(key, Integer.parseInt(value));
                                continue;
                            } catch (NumberFormatException ignored) {
                                // Fall through to string handling
                            }
                        }
                        
                        if (key.contains("confidence")) {
                            try {
                                metadata.put(key, Double.parseDouble(value));
                                continue;
                            } catch (NumberFormatException ignored) {
                                // Fall through to string handling
                            }
                        }
                        
                        metadata.put(key, value);
                    }
                }
            }
        }
        
        if (!metadata.isEmpty()) {
            metadata.put("parseStrategy", "structured");
        }
        
        return metadata;
    }
    
    /**
     * Helper method to extract string fields from JSON.
     */
    private void extractStringField(JsonNode jsonNode, String fieldName, Map<String, Object> metadata) {
        if (jsonNode.has(fieldName) && jsonNode.get(fieldName).isTextual()) {
            String value = jsonNode.get(fieldName).asText().trim();
            if (!value.isEmpty()) {
                metadata.put(fieldName, value);
            }
        }
    }
    
    /**
     * Helper method to extract numeric fields from JSON.
     */
    private void extractNumericField(JsonNode jsonNode, String fieldName, Map<String, Object> metadata) {
        if (jsonNode.has(fieldName) && jsonNode.get(fieldName).isNumber()) {
            metadata.put(fieldName, jsonNode.get(fieldName).asInt());
        }
    }
    
    /**
     * Helper method to extract array fields from JSON.
     */
    private void extractArrayField(JsonNode jsonNode, String fieldName, Map<String, Object> metadata) {
        if (jsonNode.has(fieldName) && jsonNode.get(fieldName).isArray()) {
            JsonNode arrayNode = jsonNode.get(fieldName);
            List<String> list = new java.util.ArrayList<>();
            arrayNode.forEach(node -> {
                if (node.isTextual()) {
                    list.add(node.asText());
                }
            });
            if (!list.isEmpty()) {
                metadata.put(fieldName, list);
            }
        }
    }
    
    /**
     * Calculates overall confidence from sources and parsed metadata.
     */
    private double calculateOverallConfidence(List<MetadataSource> sources, Map<String, Object> metadata) {
        if (sources.isEmpty()) {
            return 0.5;
        }
        
        // Check if metadata has its own confidence values
        if (metadata.containsKey("confidence") && metadata.get("confidence") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Double> confidenceMap = (Map<String, Double>) metadata.get("confidence");
            if (confidenceMap.containsKey("overall")) {
                return confidenceMap.get("overall");
            }
        }
        
        // Calculate from source confidences
        double avgConfidence = sources.stream()
            .mapToDouble(MetadataSource::getConfidence)
            .average()
            .orElse(0.5);
        
        // Boost confidence based on parsing strategy
        String parseStrategy = (String) metadata.get("parseStrategy");
        if ("json".equals(parseStrategy)) {
            avgConfidence = Math.min(1.0, avgConfidence * 1.1);
        } else if ("regex".equals(parseStrategy)) {
            avgConfidence = Math.min(1.0, avgConfidence * 1.05);
        }
        
        return avgConfidence;
    }
    
    /**
     * Identifies conflicts between sources.
     */
    private List<String> identifyConflicts(List<MetadataSource> sources, Map<String, Object> metadata) {
        List<String> conflicts = new java.util.ArrayList<>();
        
        // Check if conflicts are already identified in metadata
        if (metadata.containsKey("conflicts") && metadata.get("conflicts") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> parsedConflicts = (List<String>) metadata.get("conflicts");
            conflicts.addAll(parsedConflicts);
        }
        
        // Additional conflict detection logic
        if (sources.size() > 2) {
            // Check for year conflicts
            List<Object> years = sources.stream()
                .map(source -> source.getData().get("year"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
            
            if (years.size() > 1) {
                conflicts.add("Multiple publication years found: " + years);
            }
        }
        
        return conflicts;
    }
    
    /**
     * Creates fallback result when synthesis fails.
     */
    private EnhancementResult createFallbackResult(List<MetadataSource> sources) {
        Map<String, Object> basicMetadata = new HashMap<>();
        basicMetadata.put("enhanced", false);
        basicMetadata.put("fallback", true);
        basicMetadata.put("sourceCount", sources.size());
        
        return EnhancementResult.builder()
            .metadata(basicMetadata)
            .sources(sources)
            .confidence(0.6)
            .conflicts(List.of())
            .processingNotes("Fallback enhancement due to synthesis errors")
            .build();
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        // Metadata enhancement depends on external API response times
        // Base: 45 seconds + 20 seconds per expected API source
        return Duration.ofSeconds(45 + (4 * 20)); // 4 sources = 125 seconds total
    }
    
    /**
     * Data class for metadata sources.
     */
    public static class MetadataSource {
        private final String name;
        private final Map<String, Object> data;
        private final double confidence;
        
        public MetadataSource(String name, Map<String, Object> data, double confidence) {
            this.name = name;
            this.data = data;
            this.confidence = confidence;
        }
        
        public String getName() { return name; }
        public Map<String, Object> getData() { return data; }
        public double getConfidence() { return confidence; }
        
        public String getDetailedDescription() {
            return String.format("Source: %s (confidence: %.2f)\nData: %s", 
                name, confidence, data.toString());
        }
    }
    
    /**
     * Data class for enhancement results.
     */
    public static class EnhancementResult {
        private final Map<String, Object> metadata;
        private final List<MetadataSource> sources;
        private final double confidence;
        private final List<String> conflicts;
        private final String processingNotes;
        
        private EnhancementResult(Builder builder) {
            this.metadata = builder.metadata;
            this.sources = builder.sources;
            this.confidence = builder.confidence;
            this.conflicts = builder.conflicts;
            this.processingNotes = builder.processingNotes;
        }
        
        // Getters
        public Map<String, Object> getMetadata() { return metadata; }
        public List<MetadataSource> getSources() { return sources; }
        public double getConfidence() { return confidence; }
        public List<String> getConflicts() { return conflicts; }
        public String getProcessingNotes() { return processingNotes; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private Map<String, Object> metadata = new HashMap<>();
            private List<MetadataSource> sources = List.of();
            private double confidence = 0.0;
            private List<String> conflicts = List.of();
            private String processingNotes = "";
            
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder sources(List<MetadataSource> sources) { this.sources = sources; return this; }
            public Builder confidence(double confidence) { this.confidence = confidence; return this; }
            public Builder conflicts(List<String> conflicts) { this.conflicts = conflicts; return this; }
            public Builder processingNotes(String processingNotes) { this.processingNotes = processingNotes; return this; }
            
            public EnhancementResult build() { return new EnhancementResult(this); }
        }
    }
}

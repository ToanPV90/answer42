package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.citation.CitationResult;
import com.samjdtechnologies.answer42.model.citation.FormattedBibliography;
import com.samjdtechnologies.answer42.model.citation.RawCitation;
import com.samjdtechnologies.answer42.model.citation.StructuredCitation;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.CitationStyle;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.samjdtechnologies.answer42.model.db.Citation;
import com.samjdtechnologies.answer42.repository.CitationRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.Optional;
import com.samjdtechnologies.answer42.repository.PaperRepository;

/**
 * Citation formatter agent using OpenAI for citation processing and formatting.
 */
@Component
public class CitationFormatterAgent extends OpenAIBasedAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CITATION_PATTERN = Pattern.compile(
        "(?:(?:\\[\\d+\\])|(?:\\(\\w+(?:,\\s*\\w+)*,?\\s*\\d{4}\\))|(?:\\w+\\s+et\\s+al\\.?,?\\s*\\d{4}))"
    );

    private final CitationRepository citationRepository;

    public CitationFormatterAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                 AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter,
                                 CitationRepository citationRepository) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        this.citationRepository = citationRepository;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.CITATION_FORMATTER;
    }

    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Processing citation formatting for task %s", task.getId());

        try {
            long startTime = System.currentTimeMillis();
            
            // Extract document content from task
            String documentContent = extractDocumentContent(task);
            Set<CitationStyle> requestedStyles = extractRequestedStyles(task);
            
            // Step 1: Extract raw citations from document
            List<RawCitation> rawCitations = extractRawCitations(documentContent);
            LoggingUtil.info(LOG, "processWithConfig", 
                "Extracted %d raw citations", rawCitations.size());
            
            // Step 2: Process citations in parallel batches
            List<StructuredCitation> structuredCitations = processRawCitationsInBatches(rawCitations);
            LoggingUtil.info(LOG, "processWithConfig", 
                "Processed %d structured citations", structuredCitations.size());
            
            // Step 3: Format citations in requested styles
            Map<CitationStyle, FormattedBibliography> bibliographies = 
                formatCitationsInStyles(structuredCitations, requestedStyles);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Save citations to database
            saveCitationsToDatabase(task, structuredCitations, rawCitations);
            
            // Create comprehensive result
            CitationResult result = CitationResult.withStats(
                structuredCitations, 
                bibliographies,
                rawCitations.size(),
                new ArrayList<>(),
                processingTime
            );
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Citation formatting completed in %dms: %s", 
                processingTime, result.getProcessingSummary());
            
            return AgentResult.success(task.getId(), result);
            
        } catch (RuntimeException e) {
            // Let retryable exceptions (like rate limits) bubble up to retry policy
            if (isRetryableException(e)) {
                LoggingUtil.warn(LOG, "processWithConfig", 
                    "Retryable exception occurred, letting retry policy handle: %s", e.getMessage());
                throw e; // Let retry policy handle this
            }
            
            // Only catch non-retryable exceptions
            LoggingUtil.error(LOG, "processWithConfig", 
                "Citation formatting failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), e.getMessage());
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Citation formatting failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), e.getMessage());
        }
    }

    /**
     * Extract raw citations from document text using pattern matching.
     */
    private List<RawCitation> extractRawCitations(String documentContent) {
        List<RawCitation> citations = new ArrayList<>();
        
        // Split document into sections to provide context
        String[] sections = documentContent.split("\\n\\n");
        String currentSection = "main";
        
        for (String section : sections) {
            // Determine section type
            if (section.toLowerCase().contains("reference") || 
                section.toLowerCase().contains("bibliograph")) {
                currentSection = "references";
            } else if (section.toLowerCase().contains("introduction")) {
                currentSection = "introduction";
            } else if (section.toLowerCase().contains("method")) {
                currentSection = "methods";
            }
            
            // Extract citations from this section
            Matcher matcher = CITATION_PATTERN.matcher(section);
            while (matcher.find()) {
                String citationText = matcher.group();
                int position = matcher.start();
                
                // Get surrounding context
                int contextStart = Math.max(0, position - 100);
                int contextEnd = Math.min(section.length(), position + citationText.length() + 100);
                String context = section.substring(contextStart, contextEnd);
                
                RawCitation citation = RawCitation.withContext(
                    citationText, position, context, currentSection);
                citations.add(citation);
            }
        }
        
        return citations;
    }

    /**
     * Process raw citations in parallel batches for efficiency.
     */
    private List<StructuredCitation> processRawCitationsInBatches(List<RawCitation> rawCitations) {
        final int BATCH_SIZE = 5;
        List<List<RawCitation>> batches = partitionList(rawCitations, BATCH_SIZE);
        
        List<CompletableFuture<List<StructuredCitation>>> futures = batches.stream()
            .map(batch -> CompletableFuture.supplyAsync(
                () -> processCitationBatch(batch), taskExecutor))
            .collect(Collectors.toList());
        
        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return futures.stream()
            .flatMap(future -> future.join().stream())
            .collect(Collectors.toList());
    }

    /**
     * Process a batch of citations using AI parsing.
     */
    private List<StructuredCitation> processCitationBatch(List<RawCitation> batch) {
        String citationsText = batch.stream()
            .map(RawCitation::getText)
            .collect(Collectors.joining("\n"));
        
        String promptText = String.format("""
            Parse these academic citations into structured components with high accuracy.
            
            Citations:
            %s
            
            For each citation, extract:
            1. Authors (separate multiple authors with semicolons)
            2. Title of the work
            3. Publication venue (journal, conference, book)
            4. Publication year
            5. Volume and issue numbers if available
            6. Page numbers if available
            7. DOI if present
            8. Publication type (journal article, conference paper, book, etc.)
            
            Return as a JSON array with objects containing these fields:
            [
              {
                "authors": ["Last, First", "Last, First"],
                "title": "Article Title",
                "publicationVenue": "Journal Name",
                "year": 2023,
                "volume": "15",
                "issue": "3",
                "pages": "123-145",
                "doi": "10.1000/example",
                "publicationType": "journal article",
                "confidence": 0.9
              }
            ]
            
            If any field is unclear or missing, omit it from the JSON.
            """, citationsText);
        
        try {
            Prompt structurePrompt = new Prompt(promptText);
            
            // Do NOT catch exceptions here - let them propagate to trigger circuit breaker
            ChatResponse response;
            try {
                response = executePrompt(structurePrompt);
            } catch (Exception e) {
                // Log the error but re-throw to ensure circuit breaker sees the failure
                LoggingUtil.error(LOG, "processCitationBatch", 
                    "AI provider failed for citation parsing: %s", e.getMessage());
                throw new RuntimeException("AI provider communication failed: " + e.getMessage(), e);
            }
            
            String jsonResponse = response.getResult().getOutput().getText();
            
            return parseCitationStructures(jsonResponse);
            
        } catch (RuntimeException e) {
            // Re-throw circuit breaker exceptions
            throw e;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processCitationBatch", 
                "Failed to process citation batch", e);
            // Return empty citations with basic info as fallback
            return batch.stream()
                .map(raw -> StructuredCitation.basic(
                    List.of("Unknown Author"), 
                    raw.getText(), 
                    "Unknown Venue", 
                    null))
                .collect(Collectors.toList());
        }
    }

    /**
     * Format citations in all requested styles.
     */
    private Map<CitationStyle, FormattedBibliography> formatCitationsInStyles(
            List<StructuredCitation> citations, Set<CitationStyle> styles) {
        
        Map<CitationStyle, FormattedBibliography> bibliographies = new HashMap<>();
        
        for (CitationStyle style : styles) {
            try {
                FormattedBibliography bibliography = formatBibliography(citations, style);
                bibliographies.put(style, bibliography);
            } catch (Exception e) {
                LoggingUtil.error(LOG, "formatCitationsInStyles", 
                    "Failed to format bibliography for style %s", e, style);
                
                // Create error bibliography
                FormattedBibliography errorBib = FormattedBibliography.withErrors(
                    style, List.of(), citations.size(), 
                    List.of("Formatting failed: " + e.getMessage()));
                bibliographies.put(style, errorBib);
            }
        }
        
        return bibliographies;
    }

    /**
     * Format a bibliography in a specific citation style.
     */
    private FormattedBibliography formatBibliography(List<StructuredCitation> citations, CitationStyle style) {
        String citationData = citations.stream()
            .filter(StructuredCitation::isComplete)
            .map(this::citationToText)
            .collect(Collectors.joining("\n\n"));
        
        String promptText = String.format("""
            Format these academic citations according to %s citation style.
            
            Citations to format:
            %s
            
            Requirements:
            1. Follow %s formatting rules precisely
            2. Sort alphabetically by first author's last name
            3. Handle multiple authors correctly
            4. Include DOIs as active links where available
            5. Use proper punctuation and italicization
            6. Ensure consistent formatting throughout
            
            Return each formatted citation on a separate line, properly formatted for %s style.
            Do not include explanatory text, just the formatted citations.
            """, style.getDisplayName(), citationData, style.getDisplayName(), style.getDisplayName());
        
        Prompt formatPrompt = new Prompt(promptText);
        
        // Do NOT catch exceptions here - let them propagate to trigger circuit breaker
        ChatResponse response;
        try {
            response = executePrompt(formatPrompt);
        } catch (Exception e) {
            // Log the error but re-throw to ensure circuit breaker sees the failure
            LoggingUtil.error(LOG, "formatBibliography", 
                "AI provider failed for bibliography formatting: %s", e.getMessage());
            throw new RuntimeException("AI provider communication failed: " + e.getMessage(), e);
        }
        
        String formattedText = response.getResult().getOutput().getText();
        
        List<String> formattedEntries = Arrays.stream(formattedText.split("\n"))
            .filter(line -> !line.trim().isEmpty())
            .collect(Collectors.toList());
        
        return FormattedBibliography.of(style, formattedEntries);
    }

    /**
     * Helper methods for data extraction and processing.
     */
    private String extractDocumentContent(AgentTask task) {
        JsonNode input = task.getInput();
        if (input.has("documentContent")) {
            return input.get("documentContent").asText();
        } else if (input.has("paperId")) {
            // Would load from paper service in real implementation
            return "Sample document content for citation extraction";
        }
        throw new IllegalArgumentException("No document content provided in task");
    }

    private Set<CitationStyle> extractRequestedStyles(AgentTask task) {
        JsonNode input = task.getInput();
        if (input.has("citationStyles")) {
            return input.get("citationStyles").asText().split(",").length > 0 ?
                Arrays.stream(input.get("citationStyles").asText().split(","))
                    .map(String::trim)
                    .map(CitationStyle::valueOf)
                    .collect(Collectors.toSet()) :
                Set.of(CitationStyle.APA);
        }
        return Set.of(CitationStyle.APA); // Default style
    }

    private List<StructuredCitation> parseCitationStructures(String jsonResponse) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(jsonResponse);
            List<StructuredCitation> citations = new ArrayList<>();
            
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    StructuredCitation citation = parseSingleCitation(node);
                    if (citation != null) {
                        citations.add(citation);
                    }
                }
            }
            
            return citations;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseCitationStructures", 
                "Failed to parse citation JSON", e);
            return new ArrayList<>();
        }
    }

    private StructuredCitation parseSingleCitation(JsonNode node) {
        try {
            StructuredCitation.StructuredCitationBuilder builder = StructuredCitation.builder();
            
            if (node.has("authors") && node.get("authors").isArray()) {
                List<String> authors = new ArrayList<>();
                for (JsonNode authorNode : node.get("authors")) {
                    authors.add(authorNode.asText());
                }
                builder.authors(authors);
            }
            
            if (node.has("title")) builder.title(node.get("title").asText());
            if (node.has("publicationVenue")) builder.publicationVenue(node.get("publicationVenue").asText());
            if (node.has("year")) builder.year(node.get("year").asInt());
            if (node.has("volume")) builder.volume(node.get("volume").asText());
            if (node.has("issue")) builder.issue(node.get("issue").asText());
            if (node.has("pages")) builder.pages(node.get("pages").asText());
            if (node.has("doi")) builder.doi(node.get("doi").asText());
            if (node.has("publicationType")) builder.publicationType(node.get("publicationType").asText());
            if (node.has("confidence")) builder.confidence(node.get("confidence").asDouble());
            
            return builder.build();
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseSingleCitation", 
                "Failed to parse single citation", e);
            return null;
        }
    }

    private String citationToText(StructuredCitation citation) {
        return String.format("Authors: %s | Title: %s | Venue: %s | Year: %s | DOI: %s",
            citation.getFormattedAuthors(),
            citation.getTitle(),
            citation.getPublicationVenue(),
            citation.getYear(),
            citation.getDoi() != null ? citation.getDoi() : "N/A");
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        // Estimate based on expected number of citations
        JsonNode input = task.getInput();
        int estimatedCitations = 20; // Default estimate
        
        if (input.has("documentContent")) {
            String content = input.get("documentContent").asText();
            // Rough estimate: 1 citation per 1000 characters
            estimatedCitations = Math.max(5, content.length() / 1000);
        }
        
        // Base: 60 seconds + 10 seconds per citation + 30 seconds per style
        Set<CitationStyle> styles = extractRequestedStyles(task);
        long baseSeconds = 60;
        long citationSeconds = estimatedCitations * 10L;
        long styleSeconds = styles.size() * 30L;
        
        return Duration.ofSeconds(baseSeconds + citationSeconds + styleSeconds);
    }

    /**
     * Save citations to the database.
     */
    @Transactional
    private void saveCitationsToDatabase(AgentTask task, 
                                        List<StructuredCitation> structuredCitations, 
                                        List<RawCitation> rawCitations) {
        try {
            // Extract paper ID from task
            UUID paperId = extractPaperIdFromTask(task);
            if (paperId == null) {
                LoggingUtil.warn(LOG, "saveCitationsToDatabase", 
                    "No paper ID found in task, skipping citation save");
                return;
            }

            LoggingUtil.info(LOG, "saveCitationsToDatabase", 
                "Saving %d citations for paper %s", structuredCitations.size(), paperId);

            // Delete existing citations for this paper to avoid duplicates
            citationRepository.deleteByPaperId(paperId);

            // Save structured citations
            for (int i = 0; i < structuredCitations.size(); i++) {
                StructuredCitation structured = structuredCitations.get(i);
                String rawText = i < rawCitations.size() ? rawCitations.get(i).getText() : null;
                
                // Convert structured citation to JSON
                JsonNode citationData = structuredCitationToJson(structured);
                
                // Create and save citation entity
                Citation citation = new Citation(paperId, citationData, rawText);
                citationRepository.save(citation);
            }

            LoggingUtil.info(LOG, "saveCitationsToDatabase", 
                "Successfully saved %d citations for paper %s", structuredCitations.size(), paperId);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveCitationsToDatabase", 
                "Failed to save citations to database", e);
            // Don't throw exception - citation saving is not critical to the main process
        }
    }

    /**
     * Extract paper ID from the agent task.
     */
    private UUID extractPaperIdFromTask(AgentTask task) {
        JsonNode input = task.getInput();
        
        if (input.has("paperId")) {
            String paperIdStr = input.get("paperId").asText();
            try {
                return UUID.fromString(paperIdStr);
            } catch (IllegalArgumentException e) {
                LoggingUtil.warn(LOG, "extractPaperIdFromTask", 
                    "Invalid paper ID format: %s", paperIdStr);
                return null;
            }
        }
        
        return null;
    }

    /**
     * Convert a StructuredCitation to JSON for database storage.
     */
    private JsonNode structuredCitationToJson(StructuredCitation citation) {
        try {
            ObjectNode jsonNode = OBJECT_MAPPER.createObjectNode();
            
            // Basic citation fields
            if (citation.getTitle() != null) {
                jsonNode.put("title", citation.getTitle());
            }
            
            if (citation.getAuthors() != null && !citation.getAuthors().isEmpty()) {
                var authorsArray = jsonNode.putArray("authors");
                citation.getAuthors().forEach(authorsArray::add);
            }
            
            if (citation.getPublicationVenue() != null) {
                jsonNode.put("publicationVenue", citation.getPublicationVenue());
            }
            
            if (citation.getYear() != null) {
                jsonNode.put("year", citation.getYear());
            }
            
            if (citation.getVolume() != null) {
                jsonNode.put("volume", citation.getVolume());
            }
            
            if (citation.getIssue() != null) {
                jsonNode.put("issue", citation.getIssue());
            }
            
            if (citation.getPages() != null) {
                jsonNode.put("pages", citation.getPages());
            }
            
            if (citation.getDoi() != null) {
                jsonNode.put("doi", citation.getDoi());
            }
            
            if (citation.getPublicationType() != null) {
                jsonNode.put("publicationType", citation.getPublicationType());
            }
            
            if (citation.getConfidence() != null) {
                jsonNode.put("confidence", citation.getConfidence());
            }
            
            // Add metadata
            jsonNode.put("extractedAt", System.currentTimeMillis());
            jsonNode.put("isStructured", citation.isComplete());
            
            return jsonNode;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "structuredCitationToJson", 
                "Failed to convert citation to JSON", e);
            
            // Return minimal JSON as fallback
            ObjectNode fallbackNode = OBJECT_MAPPER.createObjectNode();
            fallbackNode.put("title", citation.getTitle() != null ? citation.getTitle() : "Unknown");
            fallbackNode.put("error", "Failed to serialize citation: " + e.getMessage());
            return fallbackNode;
        }
    }
}

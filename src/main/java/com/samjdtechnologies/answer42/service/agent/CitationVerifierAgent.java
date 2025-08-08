package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.citation.CitationData;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.db.Citation;
import com.samjdtechnologies.answer42.model.db.CitationVerification;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaperResult;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.CitationRepository;
import com.samjdtechnologies.answer42.repository.CitationVerificationRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.helpers.SemanticScholarApiHelper;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Citation Verifier Agent - Verifies citations against external sources using OpenAI for fuzzy matching and analysis.
 * Uses OpenAI GPT-4 for intelligent citation parsing, fuzzy matching, and confidence scoring.
 * 
 * <p>This agent implements comprehensive citation verification by:</p>
 * <ul>
 *   <li>Extracting structured citation data using AI-enhanced parsing</li>
 *   <li>Verifying citations against external databases (Crossref, Semantic Scholar)</li>
 *   <li>Using intelligent fuzzy matching for title and author verification</li>
 *   <li>Providing confidence scores and verification details</li>
 * </ul>
 * 
 * <h3>Fallback Strategy</h3>
 * <p>When this agent fails due to API issues or rate limits, the system automatically
 * falls back to {@link CitationVerifierFallbackAgent} which uses local Ollama models
 * for simplified but reliable citation verification.</p>
 */
@Component
public class CitationVerifierAgent extends OpenAIBasedAgent {
    
    private final CitationRepository citationRepository;
    private final CitationVerificationRepository citationVerificationRepository;
    private final PaperRepository paperRepository;
    private final SemanticScholarApiHelper semanticScholarApiHelper;
    private final ObjectMapper objectMapper;
    
    public CitationVerifierAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                               AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter,
                               CitationRepository citationRepository,
                               CitationVerificationRepository citationVerificationRepository,
                               PaperRepository paperRepository,
                               SemanticScholarApiHelper semanticScholarApiHelper,
                               ObjectMapper objectMapper) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        this.citationRepository = citationRepository;
        this.citationVerificationRepository = citationVerificationRepository;
        this.paperRepository = paperRepository;
        this.semanticScholarApiHelper = semanticScholarApiHelper;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.CITATION_VERIFIER;
    }
    
    @Override
    @Transactional
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", "Starting citation verification for task %s", task.getId());
        
        try {
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            
            UUID paperUuid = UUID.fromString(paperId);
            Paper paper = paperRepository.findById(paperUuid)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found: " + paperId));

            List<Citation> citations = citationRepository.findByPaperIdOrderByCreatedAt(paperUuid);
            if (citations.isEmpty()) {
                LoggingUtil.info(LOG, "processWithConfig", "No citations found for paper: %s", paperId);
                return AgentResult.success(task.getId(), Map.of(
                    "paperId", paperId,
                    "verificationsCount", 0,
                    "verifiedCount", 0,
                    "message", "No citations to verify"
                ));
            }

            LoggingUtil.info(LOG, "processWithConfig", 
                "Processing %d citations for paper: %s", citations.size(), paperId);

            List<CitationVerification> verifications = new ArrayList<>();
            int verifiedCount = 0;

            for (Citation citation : citations) {
                try {
                    CitationVerification verification = verifyCitation(paper, citation, task);
                    if (verification != null) {
                        verifications.add(verification);
                        if (verification.isVerified()) {
                            verifiedCount++;
                        }
                    }
                } catch (Exception e) {
                    LoggingUtil.error(LOG, "processWithConfig", 
                        "Error verifying citation %s", e, citation.getId());
                }
            }

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("verificationsCount", verifications.size());
            resultData.put("verifiedCount", verifiedCount);
            resultData.put("verificationRate", verifications.isEmpty() ? 0.0 : 
                           (double) verifiedCount / verifications.size());
            resultData.put("verifications", verifications.stream()
                .map(this::verificationToMap)
                .toList());

            String message = String.format(
                "Citation verification completed: %d total, %d verified (%.1f%%)",
                verifications.size(), verifiedCount, 
                verifications.isEmpty() ? 0.0 : (verifiedCount * 100.0 / verifications.size())
            );

            LoggingUtil.info(LOG, "processWithConfig", message);
            return AgentResult.success(task.getId(), resultData);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Citation verification failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), "Citation verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Verifies a single citation using AI-enhanced analysis and external sources.
     */
    private CitationVerification verifyCitation(Paper paper, Citation citation, AgentTask task) {
        try {
            // Check if verification already exists
            Optional<CitationVerification> existingVerification = 
                citationVerificationRepository.findByCitationId(citation.getId());
            if (existingVerification.isPresent()) {
                LoggingUtil.debug(LOG, "verifyCitation", 
                    "Citation verification already exists: %s", citation.getId());
                return existingVerification.get();
            }

            // Extract citation data using OpenAI
            CitationData citationData = extractCitationData(citation, task);
            
            if (citationData == null || !citationData.isValid()) {
                return createVerificationRecord(paper, citation, false, 0.1, 
                    "Unable to extract valid citation data", null, null, null, "EXTRACTION_FAILED");
            }

            // Verify against external sources
            CitationVerification verification = verifyAgainstExternalSources(
                paper, citation, citationData, task);
            
            // Save verification record
            citationVerificationRepository.save(verification);
            
            LoggingUtil.info(LOG, "verifyCitation", 
                "Citation %s verified with confidence %.2f", 
                citation.getId(), verification.getConfidence());
            
            return verification;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyCitation", 
                "Error verifying citation %s", e, citation.getId());
            return createVerificationRecord(paper, citation, false, 0.0, 
                "Verification error: " + e.getMessage(), null, null, null, "ERROR");
        }
    }
    
    /**
     * Extracts structured citation data using OpenAI for intelligent parsing.
     */
    private CitationData extractCitationData(Citation citation, AgentTask task) {
        try {
            JsonNode citationJson = citation.getCitationData();
            if (citationJson == null) {
                LoggingUtil.warn(LOG, "extractCitationData", 
                    "Citation has no structured data: %s", citation.getId());
                return null;
            }

            // Use OpenAI to analyze and normalize the citation data
            Map<String, Object> variables = new HashMap<>();
            variables.put("citationData", citationJson.toString());
            variables.put("citationId", citation.getId().toString());

            String extractionPromptTemplate = """
                Analyze the following citation data and extract key bibliographic information:
                
                Citation Data: {citationData}
                Citation ID: {citationId}
                
                Extract and normalize the following fields:
                1. Title (cleaned and normalized)
                2. Authors (list of names)
                3. Publication year
                4. Journal/venue
                5. DOI (if present)
                6. arXiv ID (if present)
                7. Volume/issue/pages (if applicable)
                
                Also assess:
                - Data quality (high/medium/low)
                - Completeness score (0-1)
                - Potential issues or ambiguities
                
                Return a structured analysis focusing on the most reliable identifiers
                for verification (title, DOI, arXiv ID, key authors).
                """;

            Prompt prompt = optimizePromptForOpenAI(extractionPromptTemplate, variables);
            ChatResponse response = executePrompt(prompt);
            String aiResponse = response.getResult().getOutput().getText();

            return parseCitationDataFromAI(aiResponse, citationJson);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "extractCitationData", 
                "Failed to extract citation data for %s", e, citation.getId());
            return null;
        }
    }
    
    /**
     * Parses AI response into structured citation data.
     */
    private CitationData parseCitationDataFromAI(String aiResponse, JsonNode originalData) {
        try {
            // Extract from original JSON data with AI enhancement
            String title = extractTextValue(originalData, "title");
            String doi = extractTextValue(originalData, "doi");
            String arxivId = extractTextValue(originalData, "arxiv_id");
            
            // Use AI response to enhance/validate extracted data
            String lowerResponse = aiResponse.toLowerCase();
            double qualityScore = 0.5; // Base score
            
            if (title != null && !title.trim().isEmpty()) qualityScore += 0.2;
            if (doi != null && !doi.trim().isEmpty()) qualityScore += 0.2;
            if (lowerResponse.contains("high")) qualityScore += 0.1;
            if (lowerResponse.contains("complete")) qualityScore += 0.1;
            
            return CitationData.builder()
                .title(title)
                .doi(doi)
                .arxivId(arxivId)
                .authors(extractAuthors(originalData))
                .year(extractYear(originalData))
                .journal(extractTextValue(originalData, "journal"))
                .aiAnalysis(aiResponse)
                .qualityScore(Math.min(1.0, qualityScore))
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseCitationDataFromAI", 
                "Failed to parse AI citation analysis", e);
            return null;
        }
    }
    
    /**
     * Verifies citation against external sources (Semantic Scholar, etc.).
     */
    private CitationVerification verifyAgainstExternalSources(Paper paper, Citation citation, 
                                                            CitationData citationData, AgentTask task) {
        try {
            // First try exact DOI match if available
            if (citationData.getDoi() != null && !citationData.getDoi().trim().isEmpty()) {
                CitationVerification doiVerification = verifyByDoi(paper, citation, citationData);
                if (doiVerification != null && doiVerification.getConfidence() >= 0.8) {
                    return doiVerification;
                }
            }
            
            // Try arXiv ID match if available
            if (citationData.getArxivId() != null && !citationData.getArxivId().trim().isEmpty()) {
                CitationVerification arxivVerification = verifyByArxivId(paper, citation, citationData);
                if (arxivVerification != null && arxivVerification.getConfidence() >= 0.8) {
                    return arxivVerification;
                }
            }
            
            // Fall back to fuzzy title/author matching
            return verifyByTitleAndAuthors(paper, citation, citationData, task);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyAgainstExternalSources", 
                "Failed to verify against external sources", e);
            return createVerificationRecord(paper, citation, false, 0.0, 
                "External verification failed: " + e.getMessage(), 
                citationData.getDoi(), null, citationData.getArxivId(), "EXTERNAL_ERROR");
        }
    }
    
    /**
     * Verifies citation by DOI using Semantic Scholar.
     */
    private CitationVerification verifyByDoi(Paper paper, Citation citation, CitationData citationData) {
        try {
            // Search Semantic Scholar by DOI would go here
            // For now, create a basic verification record
            return createVerificationRecord(paper, citation, false, 0.3, 
                "DOI verification not yet implemented", 
                citationData.getDoi(), null, citationData.getArxivId(), "SEMANTIC_SCHOLAR");
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyByDoi", "DOI verification failed", e);
            return null;
        }
    }
    
    /**
     * Verifies citation by arXiv ID.
     */
    private CitationVerification verifyByArxivId(Paper paper, Citation citation, CitationData citationData) {
        try {
            // arXiv API verification would go here
            return createVerificationRecord(paper, citation, false, 0.3, 
                "arXiv verification not yet implemented", 
                citationData.getDoi(), null, citationData.getArxivId(), "ARXIV");
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyByArxivId", "arXiv verification failed", e);
            return null;
        }
    }
    
    /**
     * Verifies citation by title and authors using AI-enhanced fuzzy matching.
     */
    private CitationVerification verifyByTitleAndAuthors(Paper paper, Citation citation, 
                                                       CitationData citationData, AgentTask task) {
        try {
            if (citationData.getTitle() == null || citationData.getTitle().trim().isEmpty()) {
                return createVerificationRecord(paper, citation, false, 0.1, 
                    "No title available for verification", 
                    citationData.getDoi(), null, citationData.getArxivId(), "NO_TITLE");
            }

            // Search Semantic Scholar by title - use correct method
            DiscoveryConfiguration config = DiscoveryConfiguration.builder().build();
            List<DiscoveredPaperResult> searchResults = 
                semanticScholarApiHelper.searchByTitle(citationData.getTitle(), config, 5);
            
            if (searchResults.isEmpty()) {
                return createVerificationRecord(paper, citation, false, 0.2, 
                    "No matching papers found in Semantic Scholar", 
                    citationData.getDoi(), null, citationData.getArxivId(), "SEMANTIC_SCHOLAR");
            }
            
            // Convert to Map format for AI processing
            List<Map<String, Object>> searchResultMaps = searchResults.stream()
                .map(this::discoveredPaperToMap)
                .toList();

            // Use AI to find the best match
            return findBestMatchWithAI(paper, citation, citationData, searchResultMaps, task);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyByTitleAndAuthors", 
                "Title/author verification failed", e);
            return createVerificationRecord(paper, citation, false, 0.0, 
                "Title/author verification error: " + e.getMessage(), 
                citationData.getDoi(), null, citationData.getArxivId(), "MATCH_ERROR");
        }
    }
    
    /**
     * Uses AI to find the best match among search results.
     */
    private CitationVerification findBestMatchWithAI(Paper paper, Citation citation, 
                                                   CitationData citationData,
                                                   List<Map<String, Object>> searchResults, 
                                                   AgentTask task) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("originalTitle", citationData.getTitle());
            variables.put("originalAuthors", citationData.getAuthors());
            variables.put("originalYear", citationData.getYear());
            variables.put("searchResults", objectMapper.writeValueAsString(searchResults));

            String matchingPromptTemplate = """
                Compare the original citation with search results to find the best match:
                
                Original Citation:
                - Title: {originalTitle}
                - Authors: {originalAuthors}
                - Year: {originalYear}
                
                Search Results: {searchResults}
                
                Analyze each result and determine:
                1. Title similarity (exact, very similar, somewhat similar, different)
                2. Author overlap (how many authors match?)
                3. Year match (exact, close, different)
                4. Overall confidence score (0.0 to 1.0)
                5. Best match index (or -1 if no good match)
                
                Consider variations in:
                - Formatting (punctuation, capitalization)
                - Author name formats (first name vs initials)
                - Title abbreviations or expansions
                - Publication year discrepancies
                
                Return analysis with confidence score and reasoning.
                """;

            Prompt prompt = optimizePromptForOpenAI(matchingPromptTemplate, variables);
            ChatResponse response = executePrompt(prompt);
            String aiAnalysis = response.getResult().getOutput().getText();

            return parseMatchingResult(paper, citation, citationData, searchResults, aiAnalysis);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findBestMatchWithAI", 
                "AI matching failed", e);
            return createVerificationRecord(paper, citation, false, 0.2, 
                "AI matching failed: " + e.getMessage(), 
                citationData.getDoi(), null, citationData.getArxivId(), "AI_MATCH_ERROR");
        }
    }
    
    /**
     * Parses AI matching result and creates verification record.
     */
    private CitationVerification parseMatchingResult(Paper paper, Citation citation, 
                                                   CitationData citationData,
                                                   List<Map<String, Object>> searchResults, 
                                                   String aiAnalysis) {
        try {
            // Extract confidence score from AI analysis
            double confidence = extractConfidenceFromAnalysis(aiAnalysis);
            boolean verified = confidence >= 0.7; // 70% threshold
            
            String verificationNotes = String.format(
                "AI-enhanced matching analysis (confidence: %.2f): %s", 
                confidence, aiAnalysis.length() > 500 ? 
                aiAnalysis.substring(0, 500) + "..." : aiAnalysis);

            // Try to extract Semantic Scholar ID from best match
            String semanticScholarId = extractSemanticScholarIdFromAnalysis(aiAnalysis, searchResults);

            return createVerificationRecord(paper, citation, verified, confidence, 
                verificationNotes, citationData.getDoi(), semanticScholarId, 
                citationData.getArxivId(), "AI_ENHANCED_MATCH");

        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseMatchingResult", 
                "Failed to parse matching result", e);
            return createVerificationRecord(paper, citation, false, 0.1, 
                "Failed to parse matching result: " + e.getMessage(), 
                citationData.getDoi(), null, citationData.getArxivId(), "PARSE_ERROR");
        }
    }
    
    /**
     * Extracts confidence score from AI analysis text.
     */
    private double extractConfidenceFromAnalysis(String analysis) {
        try {
            String lowerAnalysis = analysis.toLowerCase();
            
            // Look for explicit confidence scores
            if (lowerAnalysis.contains("confidence") && lowerAnalysis.contains("0.")) {
                // Extract numeric confidence score
                String[] parts = analysis.split("confidence[^0-9]*([0-9]*\\.?[0-9]+)");
                if (parts.length > 1) {
                    try {
                        return Double.parseDouble(parts[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            // Fallback to keyword analysis
            if (lowerAnalysis.contains("exact") || lowerAnalysis.contains("identical")) {
                return 0.95;
            } else if (lowerAnalysis.contains("very similar") || lowerAnalysis.contains("high")) {
                return 0.85;
            } else if (lowerAnalysis.contains("similar") || lowerAnalysis.contains("good")) {
                return 0.7;
            } else if (lowerAnalysis.contains("somewhat") || lowerAnalysis.contains("partial")) {
                return 0.5;
            } else if (lowerAnalysis.contains("different") || lowerAnalysis.contains("no match")) {
                return 0.2;
            }
            
            return 0.5; // Default moderate confidence
            
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractConfidenceFromAnalysis", 
                "Failed to extract confidence score, using default", e);
            return 0.5;
        }
    }
    
    /**
     * Extracts Semantic Scholar ID from AI analysis and search results.
     */
    private String extractSemanticScholarIdFromAnalysis(String analysis, 
                                                       List<Map<String, Object>> searchResults) {
        try {
            // This would be more sophisticated in practice
            if (!searchResults.isEmpty() && analysis.toLowerCase().contains("best match")) {
                Map<String, Object> firstResult = searchResults.get(0);
                return (String) firstResult.get("paperId");
            }
            return null;
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractSemanticScholarIdFromAnalysis", 
                "Failed to extract Semantic Scholar ID", e);
            return null;
        }
    }
    
    /**
     * Creates a citation verification record.
     */
    private CitationVerification createVerificationRecord(Paper paper, Citation citation, 
                                                        boolean verified, double confidence, 
                                                        String verificationNotes, String doi, 
                                                        String semanticScholarId, String arxivId, 
                                                        String verificationSource) {
        CitationVerification verification = new CitationVerification();
        verification.setId(UUID.randomUUID());
        verification.setPaperId(paper.getId());
        verification.setCitationId(citation.getId());
        verification.setVerified(verified);
        verification.setConfidence(Math.max(0.0, Math.min(1.0, confidence))); // Clamp to [0,1]
        verification.setVerificationDate(Instant.now());
        verification.setVerificationSource(verificationSource);
        verification.setDoi(doi);
        verification.setSemanticScholarId(semanticScholarId);
        verification.setArxivId(arxivId);
        return verification;
    }
    
    /**
     * Converts CitationVerification to map for result data.
     */
    private Map<String, Object> verificationToMap(CitationVerification verification) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", verification.getId().toString());
        map.put("citationId", verification.getCitationId().toString());
        map.put("verified", verification.isVerified());
        map.put("confidence", verification.getConfidence());
        map.put("verificationDate", verification.getVerificationDate().toString());
        map.put("verificationSource", verification.getVerificationSource());
        map.put("doi", verification.getDoi());
        map.put("semanticScholarId", verification.getSemanticScholarId());
        map.put("arxivId", verification.getArxivId());
        return map;
    }
    
    // Helper methods for extracting data from JSON
    private String extractTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText().trim() : null;
    }
    
    private List<String> extractAuthors(JsonNode citationData) {
        List<String> authors = new ArrayList<>();
        JsonNode authorsNode = citationData.get("authors");
        
        if (authorsNode != null && authorsNode.isArray()) {
            for (JsonNode authorNode : authorsNode) {
                String author = authorNode.asText();
                if (author != null && !author.trim().isEmpty()) {
                    authors.add(author.trim());
                }
            }
        }
        
        return authors;
    }
    
    private Integer extractYear(JsonNode citationData) {
        JsonNode yearNode = citationData.get("year");
        if (yearNode != null && !yearNode.isNull()) {
            try {
                return yearNode.asInt();
            } catch (Exception ignored) {}
        }
        return null;
    }
    
    /**
     * Converts DiscoveredPaperResult to Map for AI processing.
     */
    private Map<String, Object> discoveredPaperToMap(DiscoveredPaperResult paper) {
        Map<String, Object> map = new HashMap<>();
        map.put("paperId", paper.getId());
        map.put("title", paper.getTitle());
        map.put("authors", paper.getAuthors());
        map.put("year", paper.getYear());
        map.put("journal", paper.getJournal());
        map.put("venue", paper.getVenue());
        map.put("citationCount", paper.getCitationCount());
        map.put("doi", paper.getDoi());
        map.put("abstractText", paper.getAbstractText());
        map.put("url", paper.getUrl());
        return map;
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        if (input != null && input.has("paperId")) {
            // Estimate based on typical citation count (assume 20-50 citations per paper)
            // Base: 2 minutes + 10 seconds per citation
            return Duration.ofMinutes(5); // Conservative estimate
        }
        return Duration.ofMinutes(3);
    }
    
    
}

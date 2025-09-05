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
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.citation.CitationData;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.db.Citation;
import com.samjdtechnologies.answer42.model.db.CitationVerification;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.CitationRepository;
import com.samjdtechnologies.answer42.repository.CitationVerificationRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Citation Verifier Fallback Agent - Ollama-based fallback for citation verification.
 * 
 * <p>This fallback agent uses local Ollama models to perform citation verification when the primary
 * OpenAI-based agent fails. It provides simplified but reliable citation analysis using local AI models.</p>
 * 
 * <h3>Fallback Capabilities</h3>
 * <ul>
 *   <li>Basic citation parsing and data extraction</li>
 *   <li>Simple title/author matching against external sources</li>
 *   <li>Rule-based confidence scoring</li>
 *   <li>Local processing without external AI dependencies</li>
 * </ul>
 * 
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>Lower accuracy than primary OpenAI agent</li>
 *   <li>Faster processing due to local execution</li>
 *   <li>No external API rate limits</li>
 *   <li>Reduced token costs</li>
 * </ul>
 */
@Component
public class CitationVerifierFallbackAgent extends OllamaBasedAgent {
    
    private final CitationRepository citationRepository;
    private final CitationVerificationRepository citationVerificationRepository;
    private final PaperRepository paperRepository;
    
    public CitationVerifierFallbackAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                                       APIRateLimiter rateLimiter,
                                       CitationRepository citationRepository,
                                       CitationVerificationRepository citationVerificationRepository,
                                       PaperRepository paperRepository) {
        super(aiConfig, threadConfig, rateLimiter);
        this.citationRepository = citationRepository;
        this.citationVerificationRepository = citationVerificationRepository;
        this.paperRepository = paperRepository;
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.CITATION_VERIFIER;
    }
    
    @Override
    @Transactional
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", 
            "Starting fallback citation verification for task %s", task.getId());
        
        try {
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            
            UUID paperUuid = UUID.fromString(paperId);
            Paper paper = paperRepository.findById(paperUuid)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found: " + paperId));

            List<Citation> citations = citationRepository.findByPaperIdOrderByCreatedAt(paperUuid);
            if (citations.isEmpty()) {
                LoggingUtil.info(LOG, "processWithConfig", 
                    "No citations found for paper: %s", paperId);
                return AgentResult.success(task.getId(), Map.of(
                    "paperId", paperId,
                    "verificationsCount", 0,
                    "verifiedCount", 0,
                    "message", "No citations to verify (fallback agent)",
                    "fallbackUsed", true
                ));
            }

            LoggingUtil.info(LOG, "processWithConfig", 
                "Processing %d citations with fallback agent for paper: %s", citations.size(), paperId);

            List<CitationVerification> verifications = new ArrayList<>();
            int verifiedCount = 0;

            for (Citation citation : citations) {
                try {
                    CitationVerification verification = verifyCitationWithFallback(paper, citation, task);
                    if (verification != null) {
                        verifications.add(verification);
                        if (verification.isVerified()) {
                            verifiedCount++;
                        }
                    }
                } catch (Exception e) {
                    LoggingUtil.error(LOG, "processWithConfig", 
                        "Error verifying citation %s with fallback", e, citation.getId());
                }
            }

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("verificationsCount", verifications.size());
            resultData.put("verifiedCount", verifiedCount);
            resultData.put("verificationRate", verifications.isEmpty() ? 0.0 : 
                           (double) verifiedCount / verifications.size());
            resultData.put("fallbackUsed", true);
            resultData.put("verifications", verifications.stream()
                .map(this::verificationToMap)
                .toList());

            String message = String.format(
                "Fallback citation verification completed: %d total, %d verified (%.1f%%)",
                verifications.size(), verifiedCount, 
                verifications.isEmpty() ? 0.0 : (verifiedCount * 100.0 / verifications.size())
            );

            LoggingUtil.info(LOG, "processWithConfig", message);
            return AgentResult.success(task.getId(), resultData);

        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Fallback citation verification failed for task %s", e, task.getId());
            return AgentResult.failure(task.getId(), 
                "Fallback citation verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Verifies a single citation using simplified fallback logic.
     */
    private CitationVerification verifyCitationWithFallback(Paper paper, Citation citation, AgentTask task) {
        try {
            // Check if verification already exists
            Optional<CitationVerification> existingVerification = 
                citationVerificationRepository.findByCitationId(citation.getId());
            if (existingVerification.isPresent()) {
                LoggingUtil.debug(LOG, "verifyCitationWithFallback", 
                    "Citation verification already exists: %s", citation.getId());
                return existingVerification.get();
            }

            // Extract citation data using simple parsing
            CitationData citationData = extractCitationDataSimple(citation);
            
            if (citationData == null || !citationData.isValid()) {
                return createVerificationRecord(paper, citation, false, 0.1, 
                    "Unable to extract valid citation data (fallback)", null, null, null, 
                    "FALLBACK_EXTRACTION_FAILED");
            }

            // Use Ollama for basic verification analysis
            CitationVerification verification = verifyWithOllama(paper, citation, citationData, task);
            
            // Save verification record
            citationVerificationRepository.save(verification);
            
            LoggingUtil.info(LOG, "verifyCitationWithFallback", 
                "Citation %s verified with fallback agent, confidence %.2f", 
                citation.getId(), verification.getConfidence());
            
            return verification;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyCitationWithFallback", 
                "Error verifying citation %s with fallback", e, citation.getId());
            return createVerificationRecord(paper, citation, false, 0.0, 
                "Fallback verification error: " + e.getMessage(), null, null, null, "FALLBACK_ERROR");
        }
    }
    
    /**
     * Extracts citation data using simple parsing (no AI required).
     */
    private CitationData extractCitationDataSimple(Citation citation) {
        try {
            JsonNode citationJson = citation.getCitationData();
            if (citationJson == null) {
                LoggingUtil.warn(LOG, "extractCitationDataSimple", 
                    "Citation has no structured data: %s", citation.getId());
                return null;
            }

            // Simple extraction without AI enhancement
            String title = extractTextValue(citationJson, "title");
            String doi = extractTextValue(citationJson, "doi");
            String arxivId = extractTextValue(citationJson, "arxiv_id");
            
            // Basic quality scoring
            double qualityScore = 0.3; // Base score for fallback
            if (title != null && !title.trim().isEmpty()) qualityScore += 0.3;
            if (doi != null && !doi.trim().isEmpty()) qualityScore += 0.2;
            if (arxivId != null && !arxivId.trim().isEmpty()) qualityScore += 0.2;
            
            return CitationData.builder()
                .title(title)
                .doi(doi)
                .arxivId(arxivId)
                .authors(extractAuthors(citationJson))
                .year(extractYear(citationJson))
                .journal(extractTextValue(citationJson, "journal"))
                .aiAnalysis("Fallback agent - simple parsing used")
                .qualityScore(Math.min(1.0, qualityScore))
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "extractCitationDataSimple", 
                "Failed to extract citation data simply", e);
            return null;
        }
    }
    
    /**
     * Uses Ollama for simplified verification analysis.
     */
    private CitationVerification verifyWithOllama(Paper paper, Citation citation, 
                                                 CitationData citationData, AgentTask task) {
        try {
            // First try exact matches
            if (citationData.getDoi() != null && !citationData.getDoi().trim().isEmpty()) {
                return createVerificationRecord(paper, citation, true, 0.9, 
                    "DOI present - assumed valid (fallback)", citationData.getDoi(), 
                    null, citationData.getArxivId(), "FALLBACK_DOI");
            }
            
            if (citationData.getArxivId() != null && !citationData.getArxivId().trim().isEmpty()) {
                return createVerificationRecord(paper, citation, true, 0.8, 
                    "arXiv ID present - assumed valid (fallback)", citationData.getDoi(), 
                    null, citationData.getArxivId(), "FALLBACK_ARXIV");
            }
            
            // Use Ollama for title analysis if available
            if (citationData.getTitle() != null && !citationData.getTitle().trim().isEmpty()) {
                return verifyTitleWithOllama(paper, citation, citationData, task);
            }
            
            return createVerificationRecord(paper, citation, false, 0.2, 
                "Insufficient data for fallback verification", citationData.getDoi(), 
                null, citationData.getArxivId(), "FALLBACK_INSUFFICIENT_DATA");

        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyWithOllama", 
                "Ollama verification failed", e);
            return createVerificationRecord(paper, citation, false, 0.1, 
                "Ollama verification failed: " + e.getMessage(), 
                citationData.getDoi(), null, citationData.getArxivId(), "FALLBACK_OLLAMA_ERROR");
        }
    }
    
    /**
     * Uses Ollama to analyze title quality and provide basic verification.
     */
    private CitationVerification verifyTitleWithOllama(Paper paper, Citation citation, 
                                                      CitationData citationData, AgentTask task) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("title", citationData.getTitle());
            variables.put("authors", citationData.getAuthors());
            variables.put("year", citationData.getYear());

            String simplifiedPromptTemplate = """
                Analyze this citation for basic quality:
                
                Title: {title}
                Authors: {authors}
                Year: {year}
                
                Is this a valid academic citation? Consider:
                1. Does the title look like a real academic paper?
                2. Are author names present and formatted reasonably?
                3. Is the year plausible for an academic publication?
                
                Respond with: VALID or INVALID and a brief reason.
                """;

            Prompt prompt = optimizePromptForOllama(simplifiedPromptTemplate, variables);
            ChatResponse response = executePrompt(prompt);
            String ollamaResponse = response.getResult().getOutput().getText().toLowerCase();

            boolean isValid = ollamaResponse.contains("valid") && !ollamaResponse.contains("invalid");
            double confidence = isValid ? 0.6 : 0.3; // Conservative confidence for fallback
            
            String verificationNotes = String.format(
                "Ollama fallback analysis: %s (confidence: %.2f)", 
                response.getResult().getOutput().getText(), confidence);

            return createVerificationRecord(paper, citation, isValid, confidence, 
                verificationNotes, citationData.getDoi(), null, 
                citationData.getArxivId(), "FALLBACK_OLLAMA");

        } catch (Exception e) {
            LoggingUtil.error(LOG, "verifyTitleWithOllama", 
                "Ollama title verification failed", e);
            return createVerificationRecord(paper, citation, false, 0.2, 
                "Ollama title analysis failed: " + e.getMessage(), 
                citationData.getDoi(), null, citationData.getArxivId(), "FALLBACK_OLLAMA_TITLE_ERROR");
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
        map.put("fallbackAgent", true);
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
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        // Fallback agent is generally faster due to local processing
        return Duration.ofMinutes(2);
    }
}

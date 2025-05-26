package com.samjdtechnologies.answer42.batch.tasklets;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.agent.MetadataEnhancementAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for metadata enhancement using external APIs.
 * Enhances paper metadata through Crossref, Semantic Scholar, and DOI resolution.
 */
@Component
public class MetadataEnhancementTasklet extends BaseAgentTasklet {

    private final MetadataEnhancementAgent metadataAgent;
    private final PaperService paperService;

    public MetadataEnhancementTasklet(MetadataEnhancementAgent metadataAgent, PaperService paperService) {
        this.metadataAgent = metadataAgent;
        this.paperService = paperService;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods - MUCH CLEANER!
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Enhancing metadata for paper %s", paperId);

            // Load paper from database
            Optional<Paper> paperOpt = paperService.getPaperById(paperId);
            if (paperOpt.isEmpty()) {
                throw new RuntimeException("Paper not found: " + paperId);
            }

            Paper paper = paperOpt.get();

            // Get previous step result (paper processing results) using base class method
            AgentResult paperResult = getPreviousStepResult(chunkContext, "paperProcessorResult");

            // Create agent task for metadata enhancement
            AgentTask task = createMetadataEnhancementTask(paperId, paper, paperResult, userId);

            // Execute with MetadataEnhancementAgent
            CompletableFuture<AgentResult> resultFuture = metadataAgent.process(task);
            AgentResult result = resultFuture.get();

            if (!result.isSuccess()) {
                LoggingUtil.warn(LOG, "execute", 
                    "Metadata enhancement failed for paper %s, continuing with existing metadata: %s", 
                    paperId, result.getErrorMessage());
                
                // Create fallback result with existing metadata
                result = createFallbackResult(task, paper);
            }

            // Update paper with enhanced metadata results
            updatePaperWithEnhancedMetadata(paper, result);

            // Store result using base class method
            storeStepResult(chunkContext, "metadataEnhancementResult", result);

            logProcessingComplete("MetadataEnhancement", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "MetadataEnhancement", "metadataEnhancementResult", e);
            throw new RuntimeException("Metadata enhancement failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates agent task for metadata enhancement with paper information.
     */
    private AgentTask createMetadataEnhancementTask(UUID paperId, Paper paper, AgentResult paperResult, UUID userId) {
        ObjectNode inputNode = JsonNodeFactory.instance.objectNode();
        inputNode.put("paperId", paperId.toString());
        inputNode.put("operation", "metadata_enhancement");

        // Add existing paper metadata
        if (paper.getTitle() != null) {
            inputNode.put("title", paper.getTitle());
        }
        if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
            inputNode.put("authors", String.join("; ", paper.getAuthors()));
        }
        if (paper.getDoi() != null) {
            inputNode.put("doi", paper.getDoi());
        }
        if (paper.getPublicationDate() != null) {
            inputNode.put("publicationDate", paper.getPublicationDate().toString());
        }
        if (paper.getJournal() != null) {
            inputNode.put("journal", paper.getJournal());
        }

        // Add enhanced features to search for
        ObjectNode enhancementConfig = JsonNodeFactory.instance.objectNode();
        enhancementConfig.put("includeCrossref", true);
        enhancementConfig.put("includeSemanticScholar", true);
        enhancementConfig.put("includeDOIResolution", true);
        enhancementConfig.put("includeAuthorDisambiguation", true);
        enhancementConfig.put("includeCitationMetrics", true);
        inputNode.set("enhancementConfig", enhancementConfig);

        // Add text content if available from previous step using base class utility
        if (paperResult != null) {
            String textContent = extractStringFromResult(paperResult, "extractedText", "textContent", "content");
            if (textContent != null) {
                // Use first 1000 characters for metadata enhancement context
                String contextText = textContent.length() > 1000 ? 
                    textContent.substring(0, 1000) + "..." : textContent;
                inputNode.put("textContext", contextText);
            }
        }

        return AgentTask.builder()
            .id("metadata_enhancement_" + System.currentTimeMillis())
            .agentId("metadata-enhancer")
            .userId(userId)
            .input(inputNode)
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Updates the paper entity with enhanced metadata results.
     */
    private void updatePaperWithEnhancedMetadata(Paper paper, AgentResult result) {
        boolean updated = false;

        // Update Crossref metadata if available
        if (result.hasData("crossrefMetadata")) {
            JsonNode crossrefData = result.getDataAsJsonNode("crossrefMetadata");
            paper.setCrossrefMetadata(crossrefData);
            paper.setCrossrefVerified(true);
            paper.setCrossrefLastVerified(ZonedDateTime.now());
            updated = true;
            
            LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                "Updated Crossref metadata for paper %s", paper.getId());
        }

        // Update Semantic Scholar metadata if available
        if (result.hasData("semanticScholarMetadata")) {
            JsonNode semanticData = result.getDataAsJsonNode("semanticScholarMetadata");
            paper.setSemanticScholarMetadata(semanticData);
            paper.setSemanticScholarVerified(true);
            paper.setSemanticScholarLastVerified(ZonedDateTime.now());
            updated = true;
            
            LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                "Updated Semantic Scholar metadata for paper %s", paper.getId());
        }

        // Update DOI if enhanced/resolved
        if (result.hasData("resolvedDOI")) {
            String resolvedDOI = result.getDataAsString("resolvedDOI");
            if (resolvedDOI != null && !resolvedDOI.equals(paper.getDoi())) {
                paper.setDoi(resolvedDOI);
                updated = true;
                
                LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                    "Updated DOI for paper %s: %s", paper.getId(), resolvedDOI);
            }
        }

        // Update publication date if enhanced
        if (result.hasData("enhancedPublicationDate")) {
            String pubDateStr = result.getDataAsString("enhancedPublicationDate");
            try {
                LocalDate enhancedDate = LocalDate.parse(pubDateStr);
                if (paper.getPublicationDate() == null || !enhancedDate.equals(paper.getPublicationDate())) {
                    paper.setPublicationDate(enhancedDate);
                    updated = true;
                    
                    LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                        "Updated publication date for paper %s", paper.getId());
                }
            } catch (Exception e) {
                LoggingUtil.warn(LOG, "updatePaperWithEnhancedMetadata", 
                    "Failed to parse enhanced publication date: %s", pubDateStr);
            }
        }

        // Update journal information if enhanced
        if (result.hasData("enhancedJournal")) {
            String enhancedJournal = result.getDataAsString("enhancedJournal");
            if (enhancedJournal != null && !enhancedJournal.equals(paper.getJournal())) {
                paper.setJournal(enhancedJournal);
                updated = true;
                
                LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                    "Updated journal for paper %s: %s", paper.getId(), enhancedJournal);
            }
        }

        // Update Crossref DOI if available
        if (result.hasData("crossrefDOI")) {
            String crossrefDOI = result.getDataAsString("crossrefDOI");
            if (crossrefDOI != null && !crossrefDOI.equals(paper.getCrossrefDoi())) {
                paper.setCrossrefDoi(crossrefDOI);
                updated = true;
                
                LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                    "Updated Crossref DOI for paper %s: %s", paper.getId(), crossrefDOI);
            }
        }

        // Update Semantic Scholar ID if available
        if (result.hasData("semanticScholarId")) {
            String semanticId = result.getDataAsString("semanticScholarId");
            if (semanticId != null && !semanticId.equals(paper.getSemanticScholarId())) {
                paper.setSemanticScholarId(semanticId);
                updated = true;
                
                LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                    "Updated Semantic Scholar ID for paper %s: %s", paper.getId(), semanticId);
            }
        }

        // Save paper if any updates were made
        if (updated) {
            paper.setUpdatedAt(ZonedDateTime.now());
            paperService.updatePaperStatus(paper.getId(), "METADATA_ENHANCED", "METADATA_VERIFIED");
            
            LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                "Successfully updated metadata for paper %s", paper.getId());
        } else {
            LoggingUtil.info(LOG, "updatePaperWithEnhancedMetadata", 
                "No metadata updates needed for paper %s", paper.getId());
        }
    }

    /**
     * Creates a fallback result when metadata enhancement fails.
     */
    private AgentResult createFallbackResult(AgentTask task, Paper paper) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("status", "fallback");
        resultData.put("message", "Using existing metadata - enhancement failed");
        resultData.put("enhancedFields", 0);
        
        // Include existing metadata as "enhanced" data
        if (paper.getDoi() != null) {
            resultData.put("existingDOI", paper.getDoi());
        }
        if (paper.getJournal() != null) {
            resultData.put("existingJournal", paper.getJournal());
        }
        if (paper.getCrossrefDoi() != null) {
            resultData.put("existingCrossrefDOI", paper.getCrossrefDoi());
        }

        return AgentResult.builder()
            .taskId(task.getId())
            .success(true) // Mark as success to continue pipeline
            .resultData(resultData)
            .processingTime(Duration.ofMillis(100))
            .build();
    }

    /**
     * Estimates processing time based on metadata complexity and external API calls.
     */
    public Duration estimateProcessingTime(Paper paper) {
        // Base: 60 seconds for API calls + processing time based on existing metadata
        long baseSeconds = 60;
        
        // Add time for each missing metadata field that needs enhancement
        if (paper.getDoi() == null) baseSeconds += 15; // DOI resolution
        if (paper.getCrossrefMetadata() == null) baseSeconds += 25; // Crossref API
        if (paper.getSemanticScholarMetadata() == null) baseSeconds += 30; // Semantic Scholar API
        if (paper.getCrossrefDoi() == null) baseSeconds += 10; // Crossref DOI lookup
        if (paper.getSemanticScholarId() == null) baseSeconds += 10; // Semantic Scholar ID lookup
        
        return Duration.ofSeconds(Math.min(baseSeconds, 180)); // Cap at 3 minutes
    }

    /**
     * Checks if metadata enhancement is needed for this paper.
     */
    public boolean isEnhancementNeeded(Paper paper) {
        // Check if key metadata fields are missing or outdated
        boolean needsEnhancement = false;
        
        if (paper.getDoi() == null) needsEnhancement = true;
        if (paper.getCrossrefMetadata() == null) needsEnhancement = true;
        if (paper.getSemanticScholarMetadata() == null) needsEnhancement = true;
        if (paper.getCrossrefDoi() == null) needsEnhancement = true;
        if (paper.getSemanticScholarId() == null) needsEnhancement = true;
        
        // Check if metadata is outdated (older than 30 days)
        if (paper.getCrossrefLastVerified() != null) {
            ZonedDateTime thirtyDaysAgo = ZonedDateTime.now().minusDays(30);
            if (paper.getCrossrefLastVerified().isBefore(thirtyDaysAgo)) {
                needsEnhancement = true;
            }
        }
        
        if (paper.getSemanticScholarLastVerified() != null) {
            ZonedDateTime thirtyDaysAgo = ZonedDateTime.now().minusDays(30);
            if (paper.getSemanticScholarLastVerified().isBefore(thirtyDaysAgo)) {
                needsEnhancement = true;
            }
        }
        
        LoggingUtil.debug(LOG, "isEnhancementNeeded", 
            "Enhancement needed for paper %s: %b", paper.getId(), needsEnhancement);
        
        return needsEnhancement;
    }
}

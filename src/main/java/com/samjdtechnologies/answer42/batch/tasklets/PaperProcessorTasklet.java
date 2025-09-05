package com.samjdtechnologies.answer42.batch.tasklets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.agent.PaperProcessorAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Spring Batch tasklet for paper processing using the real PaperProcessorAgent.
 * Processes papers with AI-powered text analysis and structure recognition.
 */
@Component
public class PaperProcessorTasklet extends BaseAgentTasklet {

    private final PaperProcessorAgent paperProcessorAgent;
    private final PaperService paperService;

    public PaperProcessorTasklet(PaperProcessorAgent paperProcessorAgent, PaperService paperService) {
        this.paperProcessorAgent = paperProcessorAgent;
        this.paperService = paperService;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Instant startTime = Instant.now();
        
        try {
            // Get IDs using base class methods - MUCH CLEANER!
            UUID paperId = getPaperId(chunkContext);
            UUID userId = getUserId(chunkContext);

            LoggingUtil.info(LOG, "execute", "Processing paper %s", paperId);

            // Load paper from database
            Optional<Paper> paperOpt = paperService.getPaperById(paperId);
            if (paperOpt.isEmpty()) {
                throw new RuntimeException("Paper not found: " + paperId);
            }

            Paper paper = paperOpt.get();

            // Get existing text content from paper
            String textContent = getTextContentFromPaper(paper);

            // Create agent task with text content
            AgentTask task = createPaperProcessingTask(paperId, textContent, userId);

            // Execute with PaperProcessorAgent
            CompletableFuture<AgentResult> resultFuture = paperProcessorAgent.process(task);
            AgentResult result = resultFuture.get();

            if (!result.isSuccess()) {
                throw new RuntimeException("Paper processing failed: " + result.getErrorMessage());
            }

            // Update paper with processed results
            updatePaperWithResults(paper, result);

            // Store result using base class method
            storeStepResult(chunkContext, "paperProcessorResult", result);

            logProcessingComplete("PaperProcessor", paperId, startTime);
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            handleTaskletFailure(chunkContext, "PaperProcessor", "paperProcessorResult", e);
            throw new RuntimeException("Paper processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets text content from the paper entity.
     * Uses existing text_content field or extracts from file if needed.
     */
    private String getTextContentFromPaper(Paper paper) {
        // First try to get existing text content
        if (paper.getTextContent() != null && !paper.getTextContent().trim().isEmpty()) {
            LoggingUtil.info(LOG, "getTextContentFromPaper", 
                "Using existing text content for paper %s (%d characters)", 
                paper.getId(), paper.getTextContent().length());
            return paper.getTextContent();
        }

        // If no text content, extract it from the PDF file
        if (paper.getFilePath() != null && !paper.getFilePath().trim().isEmpty()) {
            try {
                String extractedText = extractTextFromPDF(paper);
                LoggingUtil.info(LOG, "getTextContentFromPaper", 
                    "Extracted text from PDF for paper %s (%d characters)", 
                    paper.getId(), extractedText.length());
                return extractedText;
            } catch (IOException e) {
                LoggingUtil.error(LOG, "getTextContentFromPaper", 
                    "Failed to extract text from PDF for paper %s", e, paper.getId());
                return "TEXT_EXTRACTION_FAILED: " + e.getMessage();
            }
        }

        // Fallback if no file path
        LoggingUtil.warn(LOG, "getTextContentFromPaper", 
            "No text content or file path found for paper %s, using title", paper.getId());
        return "PAPER_TITLE: " + (paper.getTitle() != null ? paper.getTitle() : "Unknown Paper");
    }

    /**
     * Extracts text content from PDF file using PDFBox.
     */
    private String extractTextFromPDF(Paper paper) throws IOException {
        if (paper.getFilePath() == null) {
            throw new IOException("Paper file path is null for paper: " + paper.getId());
        }

        Path filePath = Paths.get(paper.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("Paper file not found: " + filePath);
        }

        LoggingUtil.info(LOG, "extractTextFromPDF", "Extracting text from file: %s", filePath);

        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Configure text extraction settings for better quality
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            stripper.setWordSeparator(" ");
            stripper.setAddMoreFormatting(true);
            stripper.setSuppressDuplicateOverlappingText(true);
            
            String extractedText = stripper.getText(document);
            
            // Clean up the extracted text
            String cleanedText = cleanExtractedText(extractedText);
            
            LoggingUtil.info(LOG, "extractTextFromPDF", 
                "Extracted %d characters from %d pages (cleaned to %d characters)", 
                extractedText.length(), document.getNumberOfPages(), cleanedText.length());
            
            return cleanedText;
            
        } catch (IOException e) {
            LoggingUtil.error(LOG, "extractTextFromPDF", "Failed to extract text from PDF", e);
            throw new IOException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Cleans extracted text to improve quality.
     */
    private String cleanExtractedText(String rawText) {
        if (rawText == null) {
            return "";
        }

        return rawText
            // Normalize line endings
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\r", "\n")
            // Remove excessive whitespace
            .replaceAll("[ \\t]+", " ")
            // Normalize multiple newlines
            .replaceAll("\\n{3,}", "\n\n")
            // Remove trailing whitespace from lines
            .replaceAll("[ \\t]+\\n", "\n")
            // Trim the entire text
            .trim();
    }

    /**
     * Creates agent task with text content for processing.
     */
    private AgentTask createPaperProcessingTask(UUID paperId, String textContent, UUID userId) {
        return AgentTask.builder()
            .id("paper_processing_" + System.currentTimeMillis())
            .agentId("paper-processor")
            .userId(userId)
            .input(JsonNodeFactory.instance.objectNode()
                .put("paperId", paperId.toString())
                .put("operation", "text_extraction")
                .put("textContent", textContent))
            .status("pending")
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Updates the paper entity with processing results from the agent.
     */
    private void updatePaperWithResults(Paper paper, AgentResult result) {
        if (result.hasData("extractedText")) {
            String extractedText = result.getDataAsString("extractedText");
            paperService.updateTextContent(paper.getId(), extractedText);
            LoggingUtil.info(LOG, "updatePaperWithResults", 
                "Updated paper %s with extracted text (%d characters)", 
                paper.getId(), extractedText.length());
        }

        // Store structure information if available
        if (result.hasData("structure")) {
            LoggingUtil.info(LOG, "updatePaperWithResults", 
                "Structure analysis completed for paper %s", paper.getId());
        }

        // Update paper status to indicate text processing is complete
        paperService.updatePaperStatus(paper.getId(), "TEXT_PROCESSED", "STRUCTURE_ANALYZED");
    }

    /**
     * Estimates processing time based on content length and complexity.
     */
    public Duration estimateProcessingTime(Paper paper) {
        if (paper.getTextContent() != null) {
            int contentLength = paper.getTextContent().length();
            // Base: 30 seconds + 1 second per 1000 characters
            long estimatedSeconds = 30 + (contentLength / 1000);
            return Duration.ofSeconds(Math.min(estimatedSeconds, 300)); // Cap at 5 minutes
        }
        
        if (paper.getFileSize() != null) {
            // Base: 30 seconds + 1 second per MB
            long fileSizeMB = paper.getFileSize() / (1024 * 1024);
            long estimatedSeconds = 30 + fileSizeMB;
            return Duration.ofSeconds(Math.min(estimatedSeconds, 300)); // Cap at 5 minutes
        }
        
        return Duration.ofMinutes(2); // Default estimate
    }
}

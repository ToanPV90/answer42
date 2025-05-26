package com.samjdtechnologies.answer42.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.model.enums.PipelineStatus;
import com.samjdtechnologies.answer42.model.pipeline.PipelineConfiguration;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.pipeline.PipelineOrchestrator;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import jakarta.transaction.Transactional;

/**
 * Enhanced PaperService with multi-agent pipeline integration.
 * Automatically triggers pipeline processing on paper upload and provides
 * comprehensive status tracking using PipelineStatus constants.
 */
@Service
public class PaperService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaperService.class);
    
    private final PaperRepository paperRepository;
    private final ObjectMapper objectMapper;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final CreditService creditService;
    
    // Configure base upload directory for papers
    private final Path uploadDir = Paths.get("uploads/papers");
    
    /**
     * Constructs a new PaperService with the necessary dependencies and initializes
     * the upload directory for storing paper files.
     * 
     * @param paperRepository the repository for Paper entity operations
     * @param objectMapper the mapper for JSON and Java object conversion
     * @param pipelineOrchestrator the orchestrator for multi-agent pipeline processing
     * @param creditService the service for credit management and validation
     */
    public PaperService(PaperRepository paperRepository, ObjectMapper objectMapper,
                       @Autowired(required = false) PipelineOrchestrator pipelineOrchestrator,
                       @Autowired(required = false) CreditService creditService) {
        this.paperRepository = paperRepository;
        this.objectMapper = objectMapper;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.creditService = creditService;
        
        // Create upload directories if they don't exist
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                logger.info("Created upload directory: {}", uploadDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create upload directory: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get all papers with pagination.
     *
     * @param pageable Pagination information
     * @return Page of papers
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<Paper> getAllPapers(Pageable pageable) {
        return paperRepository.findAll(pageable);
    }
    
    /**
     * Get papers for a specific user with pagination.
     *
     * @param user The user whose papers to retrieve
     * @param pageable Pagination information
     * @return Page of papers
     */
    public Page<Paper> getPapersByUser(User user, Pageable pageable) {
        return paperRepository.findByUser(user, pageable);
    }
    
    /**
     * Get paper by ID.
     *
     * @param id The paper ID
     * @return Optional containing the paper if found
     */
    public Optional<Paper> getPaperById(UUID id) {
        return paperRepository.findById(id);
    }
    
    /**
     * Save a paper.
     *
     * @param paper The paper to save
     * @return The saved paper
     */
    @Transactional
    public Paper savePaper(Paper paper) {
        if (paper.getCreatedAt() == null) {
            paper.setCreatedAt(ZonedDateTime.now());
        }
        paper.setUpdatedAt(ZonedDateTime.now());
        return paperRepository.save(paper);
    }
    
    /**
     * Upload a paper file and create a paper record with automatic pipeline processing.
     *
     * @param file The paper file
     * @param title The paper title
     * @param authors The paper authors
     * @param currentUser The current user
     * @return The created paper
     * @throws IOException if there's an error reading from or writing to the file system
     */
    @Transactional
    public Paper uploadPaper(MultipartFile file, String title, String[] authors, User currentUser) throws IOException {
        // Create user directory if it doesn't exist
        Path userDir = uploadDir.resolve(currentUser.getId().toString());
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir);
        }
        
        // Generate a unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID() + fileExtension;
        
        // Save the file
        Path targetPath = userDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Create paper record with initial pipeline status
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setAuthors(Arrays.asList(authors));
        paper.setUser(currentUser);
        paper.setFilePath(targetPath.toString());
        paper.setFileSize(file.getSize());
        paper.setFileType(file.getContentType());
        paper.setStatus("UPLOADED");
        paper.setProcessingStatus(PipelineStatus.PENDING.getDisplayName());
        
        // Create initial metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("originalFilename", originalFilename);
        metadata.put("contentType", file.getContentType());
        metadata.put("size", file.getSize());
        metadata.put("uploadTimestamp", ZonedDateTime.now().toString());
        paper.setMetadata(metadata);
        
        // Save paper first
        Paper savedPaper = savePaper(paper);
        
        // Trigger multi-agent pipeline processing if available
        if (pipelineOrchestrator != null) {
            initiateMultiAgentProcessing(savedPaper, currentUser);
        } else {
            LoggingUtil.warn(logger, "uploadPaper", "PipelineOrchestrator not available, skipping pipeline processing");
        }
        
        return savedPaper;
    }
    
    /**
     * Initiate multi-agent pipeline processing for a newly uploaded paper.
     * 
     * @param paper The paper to process
     * @param user The user who uploaded the paper
     */
    private void initiateMultiAgentProcessing(Paper paper, User user) {
        try {
            // Check user credits if credit service is available (assuming 30 credits for full pipeline)
            if (creditService != null && !creditService.hasEnoughCredits(user.getId(), 30)) {
                LoggingUtil.warn(logger, "initiateMultiAgentProcessing", 
                    "User %s has insufficient credits for pipeline processing", user.getId());
                
                // Update paper status to indicate credit shortage
                updatePaperPipelineStatus(paper.getId(), PipelineStatus.PENDING_CREDITS);
                return;
            }
            
            // Create pipeline configuration
            PipelineConfiguration config = PipelineConfiguration.builder()
                .name("Auto Upload Processing")
                .description("Automatic processing for uploaded paper")
                .includeMetadataEnhancement(true)
                .generateSummaries(true)
                .includeCitationProcessing(true)
                .includeQualityChecking(true)
                .maxConcurrentAgents(4)
                .timeoutMinutes(15)
                .build();
            
            // Start pipeline processing with progress callback
            pipelineOrchestrator.processPaper(
                paper.getId(), 
                user.getId(),
                config, 
                progressUpdate -> {
                    LoggingUtil.debug(logger, "pipelineProgress", 
                        "Pipeline progress for paper %s: %s", 
                        paper.getId(), progressUpdate.toString());
                }
            );
            
            // Update paper status using enum
            updatePaperPipelineStatus(paper.getId(), PipelineStatus.INITIALIZING);
            
            LoggingUtil.info(logger, "initiateMultiAgentProcessing", 
                "Initiated pipeline processing for paper %s", paper.getId());
                
        } catch (Exception e) {
            LoggingUtil.error(logger, "initiateMultiAgentProcessing", 
                "Failed to initiate pipeline processing: " + e.getMessage(), e);
            
            // Update paper status to indicate processing failure using enum
            updatePaperPipelineStatus(paper.getId(), PipelineStatus.FAILED);
        }
    }
    
    /**
     * Get processing status for pipeline display.
     * 
     * @param paperId The paper ID
     * @return Processing status information
     */
    public PipelineStatus getPipelineStatus(UUID paperId) {
        Optional<Paper> paperOpt = paperRepository.findById(paperId);
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            String processingStatus = paper.getProcessingStatus();
            
            // Parse from string using the enum's fromString method
            return PipelineStatus.fromString(processingStatus);
        }
        return PipelineStatus.PENDING;
    }
    
    /**
     * Update paper status using pipeline status enum.
     *
     * @param id The paper ID
     * @param pipelineStatus The new pipeline status
     * @return Optional containing the updated paper if found
     */
    @Transactional
    public Optional<Paper> updatePaperPipelineStatus(UUID id, PipelineStatus pipelineStatus) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            
            // Use the enum's getPaperStatus method for consistency
            paper.setStatus(pipelineStatus.getPaperStatus());
            paper.setProcessingStatus(pipelineStatus.getDisplayName());
            paper.setUpdatedAt(ZonedDateTime.now());
            
            return Optional.of(paperRepository.save(paper));
        }
        
        return Optional.empty();
    }
    
    /**
     * Get processing progress percentage for UI display.
     * 
     * @param paperId The paper ID
     * @return Progress percentage (0-100)
     */
    public int getProcessingProgress(UUID paperId) {
        PipelineStatus status = getPipelineStatus(paperId);
        return status.getProgressPercentage();
    }
    
    /**
     * Check if paper processing is complete.
     * 
     * @param paperId The paper ID
     * @return True if processing is complete (success or failure)
     */
    public boolean isProcessingComplete(UUID paperId) {
        PipelineStatus status = getPipelineStatus(paperId);
        return status.isTerminal();
    }
    
    /**
     * Delete a paper by ID.
     *
     * @param id The paper ID
     */
    @Transactional
    public void deletePaper(UUID id) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            
            // Delete the physical file if it exists
            if (paper.getFilePath() != null) {
                try {
                    Path filePath = Paths.get(paper.getFilePath());
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                        logger.info("Deleted file: {}", filePath);
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete file: {}", e.getMessage(), e);
                }
            }
            
            // Delete the database record
            paperRepository.deleteById(id);
        }
    }
    
    /**
     * Count papers for a user.
     *
     * @param user The user whose papers to count
     * @return The number of papers
     */
    public long countPapersByUser(User user) {
        return paperRepository.countByUser(user);
    }
    
    /**
     * Get recent papers for a user.
     *
     * @param user The user whose papers to retrieve
     * @param limit The maximum number of papers to return
     * @return List of recent papers
     */
    public List<Paper> getRecentPapersByUser(User user, int limit) {
        return paperRepository.findRecentPapersByUser(user, Pageable.ofSize(limit));
    }
    
    /**
     * Search papers by title, abstract, or content.
     *
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching papers
     */
    public Page<Paper> searchPapers(String searchTerm, Pageable pageable) {
        return paperRepository.searchPapers(searchTerm, pageable);
    }
    
    /**
     * Search papers by user, title, abstract, or content.
     *
     * @param user The user whose papers to search
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching papers
     */
    public Page<Paper> searchPapersByUser(User user, String searchTerm, Pageable pageable) {
        return paperRepository.searchPapersByUser(user, searchTerm, pageable);
    }
    
    /**
     * Update paper metadata.
     *
     * @param id The paper ID
     * @param title The new title
     * @param authors The new authors
     * @param paperAbstract The new abstract
     * @param journal The new journal
     * @param year The new year
     * @param doi The new DOI
     * @return Optional containing the updated paper if found
     */
    @Transactional
    public Optional<Paper> updatePaperMetadata(UUID id, String title, String[] authors, 
                                             String paperAbstract, String journal, Integer year, String doi) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            
            if (title != null) paper.setTitle(title);
            if (authors != null) paper.setAuthors(Arrays.asList(authors));
            if (paperAbstract != null) paper.setPaperAbstract(paperAbstract);
            if (journal != null) paper.setJournal(journal);
            if (year != null) paper.setYear(year);
            if (doi != null) paper.setDoi(doi);
            
            paper.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(paperRepository.save(paper));
        }
        
        return Optional.empty();
    }
    
    /**
     * Update paper status (legacy method - use updatePaperPipelineStatus for pipeline integration).
     *
     * @param id The paper ID
     * @param status The new status
     * @param processingStatus The new processing status
     * @return Optional containing the updated paper if found
     */
    @Transactional
    public Optional<Paper> updatePaperStatus(UUID id, String status, String processingStatus) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            
            if (status != null) paper.setStatus(status);
            if (processingStatus != null) paper.setProcessingStatus(processingStatus);
            
            paper.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(paperRepository.save(paper));
        }
        
        return Optional.empty();
    }
    
    /**
     * Update paper text content.
     *
     * @param id The paper ID
     * @param textContent The new text content
     * @return Optional containing the updated paper if found
     */
    @Transactional
    public Optional<Paper> updateTextContent(UUID id, String textContent) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            paper.setTextContent(textContent);
            paper.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(paperRepository.save(paper));
        }
        
        return Optional.empty();
    }
    
    /**
     * Update paper visibility.
     *
     * @param id The paper ID
     * @param isPublic Whether the paper should be public
     * @return Optional containing the updated paper if found
     */
    @Transactional
    public Optional<Paper> updatePaperVisibility(UUID id, boolean isPublic) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            paper.setIsPublic(isPublic);
            paper.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(paperRepository.save(paper));
        }
        
        return Optional.empty();
    }
    
    /**
     * Get papers by status with pagination.
     *
     * @param status The paper status
     * @param pageable Pagination information
     * @return Page of papers with the specified status
     */
    public Page<Paper> getPapersByStatus(String status, Pageable pageable) {
        return paperRepository.findByStatus(status, pageable);
    }
    
    /**
     * Get papers by user and status with pagination.
     *
     * @param user The user whose papers to retrieve
     * @param status The paper status
     * @param pageable Pagination information
     * @return Page of papers with the specified status
     */
    public Page<Paper> getPapersByUserAndStatus(User user, String status, Pageable pageable) {
        return paperRepository.findByUserAndStatus(user, status, pageable);
    }
    
    /**
     * Get papers by user that are not part of a specific project.
     *
     * @param user The user whose papers to retrieve
     * @param project The project to exclude papers from
     * @return List of papers not in the project
     */
    @Transactional
    public List<Paper> getPapersNotInProject(User user, Project project) {
        // First get all the user's papers
        List<Paper> allUserPapers = paperRepository.findByUser(user);
        
        // Then filter out the ones that are already in the project
        if (project.getPapers() != null && !project.getPapers().isEmpty()) {
            allUserPapers.removeAll(project.getPapers());
        }
        
        return allUserPapers;
    }
}

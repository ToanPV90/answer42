package com.samjdtechnologies.answer42.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

/**
 * Service for handling paper operations.
 */
@Service
public class PaperService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaperService.class);
    
    private final PaperRepository paperRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    
    // Configure base upload directory for papers
    private final Path uploadDir = Paths.get("uploads/papers");
    
    @Autowired
    public PaperService(PaperRepository paperRepository, UserService userService, ObjectMapper objectMapper) {
        this.paperRepository = paperRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
        
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
     * Upload a paper file and create a paper record.
     *
     * @param file The paper file
     * @param title The paper title
     * @param authors The paper authors
     * @param currentUser The current user
     * @return The created paper
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
        
        // Create paper record
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setAuthors(Arrays.asList(authors)); // Convert String[] to List<String>
        paper.setUser(currentUser);
        paper.setFilePath(targetPath.toString());
        paper.setFileSize(file.getSize());
        paper.setFileType(file.getContentType());
        paper.setStatus("PENDING");
        paper.setProcessingStatus("pending");
        
        // Create initial metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("originalFilename", originalFilename);
        metadata.put("contentType", file.getContentType());
        metadata.put("size", file.getSize());
        metadata.put("uploadTimestamp", ZonedDateTime.now().toString());
        paper.setMetadata(metadata);
        
        return savePaper(paper);
    }
    
    /**
     * Update paper metadata.
     *
     * @param id The paper ID
     * @param title The new title
     * @param authors The new authors
     * @param abstract_ The new abstract
     * @param journal The new journal
     * @param year The new year
     * @param doi The new DOI
     * @return Optional containing the updated paper if found
     */
    @Transactional
    public Optional<Paper> updatePaperMetadata(UUID id, String title, String[] authors, 
                                             String abstract_, String journal, Integer year, String doi) {
        Optional<Paper> paperOpt = paperRepository.findById(id);
        
        if (paperOpt.isPresent()) {
            Paper paper = paperOpt.get();
            
            if (title != null) paper.setTitle(title);
            if (authors != null) paper.setAuthors(Arrays.asList(authors)); // Convert String[] to List<String>
            if (abstract_ != null) paper.setAbstract(abstract_);
            if (journal != null) paper.setJournal(journal);
            if (year != null) paper.setYear(year);
            if (doi != null) paper.setDoi(doi);
            
            paper.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(paperRepository.save(paper));
        }
        
        return Optional.empty();
    }
    
    /**
     * Update paper status.
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
}
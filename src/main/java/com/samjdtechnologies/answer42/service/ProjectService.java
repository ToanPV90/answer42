package com.samjdtechnologies.answer42.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.repository.ProjectRepository;

import jakarta.transaction.Transactional;

/**
 * Service for handling project operations.
 */
@Service
public class ProjectService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructs a new ProjectService with the necessary dependencies.
     * 
     * @param projectRepository the repository for Project entity operations
     * @param objectMapper the mapper for JSON and Java object conversion
     */
    public ProjectService(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get all projects with pagination.
     *
     * @param pageable Pagination information
     * @return Page of projects
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<Project> getAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }
    
    /**
     * Get projects for a specific user with pagination.
     * This method ensures the papers collection is initialized to avoid LazyInitializationException.
     *
     * @param user The user whose projects to retrieve
     * @param pageable Pagination information
     * @return Page of projects
     */
    @Transactional
    public Page<Project> getProjectsByUser(User user, Pageable pageable) {
        Page<Project> projects = projectRepository.findByUser(user, pageable);
        // Initialize the papers collection for each project to avoid LazyInitializationException
        projects.getContent().forEach(project -> project.getPapers().size());
        return projects;
    }
    
    /**
     * Get project by ID.
     * This method ensures the papers collection is initialized to avoid LazyInitializationException.
     *
     * @param id The project ID
     * @return Optional containing the project if found
     */
    @Transactional
    public Optional<Project> getProjectById(UUID id) {
        Optional<Project> projectOpt = projectRepository.findById(id);
        // Initialize the papers collection if project exists to avoid LazyInitializationException
        projectOpt.ifPresent(project -> project.getPapers().size());
        return projectOpt;
    }
    
    /**
     * Get project by ID and user.
     * This method ensures the papers collection is initialized to avoid LazyInitializationException.
     *
     * @param id The project ID
     * @param user The user
     * @return Optional containing the project if found
     */
    @Transactional
    public Optional<Project> getProjectByIdAndUser(UUID id, User user) {
        Optional<Project> projectOpt = projectRepository.findByIdAndUser(id, user);
        // Initialize the papers collection if project exists to avoid LazyInitializationException
        projectOpt.ifPresent(project -> project.getPapers().size());
        return projectOpt;
    }
    
    /**
     * Save a project.
     *
     * @param project The project to save
     * @return The saved project
     */
    @Transactional
    public Project saveProject(Project project) {
        if (project.getCreatedAt() == null) {
            project.setCreatedAt(ZonedDateTime.now());
        }
        project.setUpdatedAt(ZonedDateTime.now());
        return projectRepository.save(project);
    }
    
    /**
     * Create a new project.
     *
     * @param name The project name
     * @param description The project description
     * @param user The user creating the project
     * @return The created project
     */
    @Transactional
    public Project createProject(String name, String description, User user) {
        Project project = new Project(name, user);
        project.setDescription(description);
        
        // Create initial settings
        ObjectNode settings = objectMapper.createObjectNode();
        settings.put("createdTimestamp", ZonedDateTime.now().toString());
        settings.put("lastModifiedTimestamp", ZonedDateTime.now().toString());
        project.setSettings(settings);
        
        return saveProject(project);
    }
    
    /**
     * Delete a project by ID.
     *
     * @param id The project ID
     */
    @Transactional
    public void deleteProject(UUID id) {
        projectRepository.deleteById(id);
    }
    
    /**
     * Count projects for a user.
     *
     * @param user The user whose projects to count
     * @return The number of projects
     */
    public long countProjectsByUser(User user) {
        return projectRepository.countByUser(user);
    }
    
    /**
     * Get recent projects for a user.
     *
     * @param user The user whose projects to retrieve
     * @param limit The maximum number of projects to return
     * @return List of recent projects
     */
    public List<Project> getRecentProjectsByUser(User user, int limit) {
        return projectRepository.findRecentProjectsByUser(user, Pageable.ofSize(limit));
    }
    
    /**
     * Search projects by name or description.
     * This method ensures the papers collection is initialized to avoid LazyInitializationException.
     *
     * @param user The user whose projects to search
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching projects
     */
    @Transactional
    public Page<Project> searchProjectsByUser(User user, String searchTerm, Pageable pageable) {
        Page<Project> projects = projectRepository.searchProjectsByUser(user, searchTerm, pageable);
        // Initialize the papers collection for each project to avoid LazyInitializationException
        projects.getContent().forEach(project -> project.getPapers().size());
        return projects;
    }
    
    /**
     * Find public projects with pagination.
     * This method ensures the papers collection is initialized to avoid LazyInitializationException.
     *
     * @param pageable Pagination information
     * @return Page of public projects
     */
    @Transactional
    public Page<Project> getPublicProjects(Pageable pageable) {
        Page<Project> projects = projectRepository.findByIsPublicTrue(pageable);
        // Initialize the papers collection for each project to avoid LazyInitializationException
        projects.getContent().forEach(project -> project.getPapers().size());
        return projects;
    }
    
    /**
     * Update project details.
     *
     * @param id The project ID
     * @param name The new name
     * @param description The new description
     * @param isPublic The new public status
     * @return Optional containing the updated project if found
     */
    @Transactional
    public Optional<Project> updateProjectDetails(UUID id, String name, String description, Boolean isPublic) {
        Optional<Project> projectOpt = projectRepository.findById(id);
        
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            
            if (name != null) project.setName(name);
            if (description != null) project.setDescription(description);
            if (isPublic != null) project.setIsPublic(isPublic);
            
            project.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(projectRepository.save(project));
        }
        
        return Optional.empty();
    }
    
    /**
     * Update project settings.
     *
     * @param id The project ID
     * @param settings The new settings
     * @return Optional containing the updated project if found
     */
    @Transactional
    public Optional<Project> updateProjectSettings(UUID id, JsonNode settings) {
        Optional<Project> projectOpt = projectRepository.findById(id);
        
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setSettings(settings);
            project.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(projectRepository.save(project));
        }
        
        return Optional.empty();
    }
    
    /**
     * Add a paper to a project.
     *
     * @param projectId The project ID
     * @param paper The paper to add
     * @return Optional containing the updated project if found
     */
    @Transactional
    public Optional<Project> addPaperToProject(UUID projectId, Paper paper) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.addPaper(paper);
            project.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(projectRepository.save(project));
        }
        
        return Optional.empty();
    }
    
    /**
     * Remove a paper from a project.
     *
     * @param projectId The project ID
     * @param paper The paper to remove
     * @return Optional containing the updated project if found
     */
    @Transactional
    public Optional<Project> removePaperFromProject(UUID projectId, Paper paper) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.removePaper(paper);
            project.setUpdatedAt(ZonedDateTime.now());
            return Optional.of(projectRepository.save(project));
        }
        
        return Optional.empty();
    }
}

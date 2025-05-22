package com.samjdtechnologies.answer42.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;

/**
 * Utility class for Hibernate operations, particularly handling lazy loading issues.
 */
@Component
public class HibernateUtil {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateUtil.class);
    
    /**
     * Initialize lazy-loaded relationships for a Paper entity.
     * This method forces initialization of lazy-loaded collections and references
     * within a transaction to avoid LazyInitializationExceptions.
     * 
     * @param paper The paper entity to initialize
     * @return The initialized paper
     */
    @Transactional(readOnly = true)
    public Paper initializePaper(Paper paper) {
        if (paper == null) {
            return null;
        }
        
        LoggingUtil.debug(LOG, "initializePaper", "Initializing lazy-loaded relationships for paper: " + paper.getId());
        
        // Force initialization of the user reference
        if (paper.getUser() != null) {
            Hibernate.initialize(paper.getUser());
        }
        
        // Force initialization of other collections if needed
        // For example, if Paper has a collection of tags or comments
        
        return paper;
    }
    
    /**
     * Initialize lazy-loaded relationships for a collection of Paper entities.
     * 
     * @param papers The collection of papers to initialize
     * @return A list of initialized papers
     */
    @Transactional(readOnly = true)
    public List<Paper> initializePapers(Collection<Paper> papers) {
        if (papers == null || papers.isEmpty()) {
            return List.of();
        }
        
        LoggingUtil.debug(LOG, "initializePapers", "Initializing lazy-loaded relationships for " + papers.size() + " papers");
        
        return papers.stream()
                .map(this::initializePaper)
                .collect(Collectors.toList());
    }
    
    /**
     * Initialize lazy-loaded relationships for a Project entity.
     * 
     * @param project The project entity to initialize
     * @return The initialized project
     */
    @Transactional(readOnly = true)
    public Project initializeProject(Project project) {
        if (project == null) {
            return null;
        }
        
        LoggingUtil.debug(LOG, "initializeProject", "Initializing lazy-loaded relationships for project: " + project.getId());
        
        // Force initialization of the user reference
        if (project.getUser() != null) {
            Hibernate.initialize(project.getUser());
        }
        
        // Initialize papers collection
        if (project.getPapers() != null) {
            Set<Paper> initializedPapers = new HashSet<>();
            for (Paper paper : project.getPapers()) {
                initializedPapers.add(initializePaper(paper));
            }
            project.setPapers(initializedPapers);
        }
        
        return project;
    }
    
    /**
     * Initialize lazy-loaded relationships for a collection of Project entities.
     * 
     * @param projects The collection of projects to initialize
     * @return A list of initialized projects
     */
    @Transactional(readOnly = true)
    public List<Project> initializeProjects(Collection<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }
        
        LoggingUtil.debug(LOG, "initializeProjects", "Initializing lazy-loaded relationships for " + projects.size() + " projects");
        
        return projects.stream()
                .map(this::initializeProject)
                .collect(Collectors.toList());
    }
    
    /**
     * Initialize lazy-loaded relationships for a User entity.
     * 
     * @param user The user entity to initialize
     * @return The initialized user
     */
    @Transactional(readOnly = true)
    public User initializeUser(User user) {
        if (user == null) {
            return null;
        }
        
        LoggingUtil.debug(LOG, "initializeUser", "Initializing lazy-loaded relationships for user: " + user.getId());
        
        // Initialize any lazy-loaded user properties if needed
        
        return user;
    }
}

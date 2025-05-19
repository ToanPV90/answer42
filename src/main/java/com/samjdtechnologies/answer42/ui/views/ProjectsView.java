package com.samjdtechnologies.answer42.ui.views;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.Project;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.views.helpers.ProjectsHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * View for managing research projects.
 * This view displays the user's projects, allows searching, creating, and managing projects.
 */
@Route(value = UIConstants.ROUTE_PROJECTS, layout = MainLayout.class)
@PageTitle("Answer42 - Projects")
@Secured("ROLE_USER")
public class ProjectsView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsView.class);
    
    private final ProjectService projectService;
    private final PaperService paperService;

    private User currentUser;
    private final Grid<Project> grid = new Grid<>(Project.class, false);
    private TextField searchField;
    private Button createProjectButton;
    private String searchTerm = "";
    
    private int page = 0;
    private final int pageSize = 10;
    private final Button prevButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Span pageInfo = new Span("0 of 0");
    
    /**
     * Constructs a new ProjectsView with the necessary dependencies.
     * 
     * @param projectService Service for project operations
     * @param paperService Service for paper operations
     */
    public ProjectsView(ProjectService projectService, PaperService paperService) {
        this.projectService = projectService;
        this.paperService = paperService;
        
        addClassName(UIConstants.CSS_PROJECTS_VIEW);
        getStyle().setHeight("auto");
        LoggingUtil.debug(LOG, "ProjectsView", "ProjectsView initialized");
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Entering ProjectsView");
        
        // Get the current user from the session (stored by MainLayout)
        currentUser = MainLayout.getCurrentUser();
        
        if (currentUser != null) {
            LoggingUtil.debug(LOG, "beforeEnter", "Retrieved user from session: %s (ID: %s)", 
                currentUser.getUsername(), currentUser.getId());
            
            // Initialize the view with the user's data
            initializeView();
        } else {
            LoggingUtil.warn(LOG, "beforeEnter", "No user found in session, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
    
    /**
     * Initializes the view components.
     */
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing projects view components");
        
        // Configure the view
        removeAll();
        
        // Add components directly to the view
        add(createWelcomeSection(), createToolbar(), createContent());
        
        // Load initial data
        updateList();
    }
    
    /**
     * Creates the welcome section for the view.
     * 
     * @return The welcome section component
     */
    private Div createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("My Projects");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);
        
        Paragraph welcomeSubtitle = new Paragraph("Create and manage your research projects");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
    }
    
    /**
     * Creates the toolbar with search and create project button.
     * 
     * @return The toolbar component
     */
    private HorizontalLayout createToolbar() {
        // Search field
        searchField = new TextField();
        searchField.addClassName(UIConstants.CSS_SEARCH_FIELD);
        searchField.setPlaceholder("Search projects by name or description");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> {
            searchTerm = e.getValue();
            page = 0;
            updateList();
        });
        
        // Create project button
        createProjectButton = new Button("Create Project", new Icon(VaadinIcon.PLUS));
        createProjectButton.addClassName(UIConstants.CSS_CREATE_PROJECT_BUTTON);
        createProjectButton.addClickListener(e -> ProjectsHelper.showCreateProjectDialog(this::createProject));
        
        // Toolbar layout
        HorizontalLayout toolbar = new HorizontalLayout(searchField, createProjectButton);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.addClassName(UIConstants.CSS_TOOLBAR);
        
        return toolbar;
    }
    
    /**
     * Creates the content with grid and pagination.
     * 
     * @return The content component
     */
    private Component createContent() {
        // Configure the grid using the helper class
        ComponentRenderer<Component, Project> actionsRenderer = 
            new ComponentRenderer<>(project -> ProjectsHelper.createActions(
                project, 
                this::viewProject, 
                this::editProject, 
                this::deleteProject
            ));
        
        ComponentRenderer<Component, Project> detailsRenderer = 
            new ComponentRenderer<>(project -> ProjectsHelper.createProjectDetails(
                project, 
                this::addPaperToProject
            ));
        
        ProjectsHelper.configureGrid(grid, actionsRenderer, detailsRenderer);

        // Configure pagination
        HorizontalLayout pagination = new HorizontalLayout(prevButton, pageInfo, nextButton);
        pagination.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        pagination.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        prevButton.addClickListener(e -> {
            if (page > 0) {
                page--;
                updateList();
            }
        });
        
        nextButton.addClickListener(e -> {
            page++;
            updateList();
        });

        VerticalLayout content = new VerticalLayout(grid, pagination);
        content.setHeightFull();
        content.setPadding(false);
        content.setSpacing(false);

        return content;
    }
    
    /**
     * Updates the project list in the grid.
     */
    private void updateList() {
        if (currentUser == null) {
            LoggingUtil.warn(LOG, "updateList", "Attempted to load projects with no user");
            return;
        }
        
        // Fetch the projects page
        Page<Project> projectsPage = ProjectsHelper.fetchProjectsList(
            currentUser, 
            searchTerm, 
            page, 
            pageSize, 
            projectService
        );
        
        // Get the items from the page
        List<Project> projects = projectsPage.getContent();
        
        // Update the grid with the projects
        grid.setItems(projects);
        
        // Update pagination info and buttons
        int totalPages = projectsPage.getTotalPages();
        ProjectsHelper.updatePagination(page, totalPages, pageInfo, prevButton, nextButton);
        
        // Show empty state if no projects and on first page
        if (projects.isEmpty() && page == 0) {
            showEmptyState();
        } else {
            grid.setVisible(true);
            // Remove any previously added empty state
            getChildren().forEach(child -> {
                if (child instanceof Div div && div.hasClassName(UIConstants.CSS_EMPTY_PROJECTS)) {
                    remove(child);
                }
            });
        }
    }
    
    /**
     * Shows an empty state message when no projects are found.
     */
    private void showEmptyState() {
        // Create an empty grid overlay
        Div emptyState = new Div();
        emptyState.addClassName(UIConstants.CSS_EMPTY_PROJECTS);
        
        Icon folderIcon = new Icon(VaadinIcon.FOLDER_O);
        folderIcon.setSize("48px");
        
        H3 emptyTitle = new H3("No projects found");
        
        Paragraph emptyText;
        if (searchTerm.isEmpty()) {
            emptyText = new Paragraph("Create your first research project to organize and analyze your papers.");
            Button createButton = new Button("Create Project", new Icon(VaadinIcon.PLUS));
            createButton.addClickListener(e -> ProjectsHelper.showCreateProjectDialog(this::createProject));
            
            VerticalLayout content = new VerticalLayout(folderIcon, emptyTitle, emptyText, createButton);
            content.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, folderIcon, emptyTitle, emptyText, createButton);
            content.setSpacing(true);
            
            emptyState.add(content);
        } else {
            emptyText = new Paragraph("No projects match your search criteria. Try a different search term.");
            
            VerticalLayout content = new VerticalLayout(folderIcon, emptyTitle, emptyText);
            content.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, folderIcon, emptyTitle, emptyText);
            content.setSpacing(true);
            
            emptyState.add(content);
        }
        
        // Hide the grid and add the empty state
        grid.setVisible(false);
        add(emptyState);
    }
    
    /**
     * Creates a new project.
     * 
     * @param project The project to create
     */
    private void createProject(Project project) {
        try {
            Project newProject = projectService.createProject(
                project.getName(), 
                project.getDescription(), 
                currentUser
            );
            
            LoggingUtil.info(LOG, "createProject", "Created project '%s' with ID: %s", 
                project.getName(), newProject.getId());
            
            Notification notification = new Notification(
                "Project created successfully", 
                3000, 
                Notification.Position.BOTTOM_START
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.open();
            
            updateList();
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createProject", "Failed to create project: %s", e.getMessage());
            
            Notification notification = new Notification(
                "Failed to create project: " + e.getMessage(), 
                3000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
        }
    }
    
    /**
     * Handles viewing a project.
     * 
     * @param project The project to view
     */
    private void viewProject(Project project) {
        LoggingUtil.debug(LOG, "viewProject", "Viewing project: %s", project.getId());
        // Toggle item details in grid - the details component will show the project details
        grid.setDetailsVisible(project, !grid.isDetailsVisible(project));
    }
    
    /**
     * Handles editing a project.
     * 
     * @param project The project to edit
     */
    private void editProject(Project project) {
        LoggingUtil.debug(LOG, "editProject", "Editing project: %s", project.getId());
        
        ProjectsHelper.showEditProjectDialog(project, this::saveProjectChanges);
    }
    
    /**
     * Saves changes to a project.
     * 
     * @param project The project with updated values
     */
    private void saveProjectChanges(Project project) {
        try {
            Optional<Project> updatedProject = projectService.updateProjectDetails(
                project.getId(), 
                project.getName(), 
                project.getDescription(), 
                project.getIsPublic()
            );
            
            if (updatedProject.isEmpty()) {
                throw new Exception("Project not found");
            }
            
            LoggingUtil.info(LOG, "saveProjectChanges", "Updated project with ID: %s", project.getId());
            
            Notification notification = new Notification(
                "Project updated successfully", 
                3000, 
                Notification.Position.BOTTOM_START
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.open();
            
            updateList();
        } catch (Exception e) {
            LoggingUtil.error(LOG, "saveProjectChanges", "Failed to update project: %s", e.getMessage());
            
            Notification notification = new Notification(
                "Failed to update project: " + e.getMessage(), 
                3000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
        }
    }
    
    /**
     * Handles deleting a project.
     * 
     * @param project The project to delete
     */
    private void deleteProject(Project project) {
        LoggingUtil.debug(LOG, "deleteProject", "Deleting project: %s", project.getId());
        
        ProjectsHelper.showDeleteConfirmDialog(project, p -> {
            try {
                projectService.deleteProject(p.getId());
                
                LoggingUtil.info(LOG, "deleteProject", "Deleted project with ID: %s", p.getId());
                
                Notification notification = new Notification(
                    "Project deleted", 
                    3000, 
                    Notification.Position.BOTTOM_START
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.open();
                
                updateList();
            } catch (Exception e) {
                LoggingUtil.error(LOG, "deleteProject", "Failed to delete project: %s", e.getMessage());
                
                Notification notification = new Notification(
                    "Failed to delete project: " + e.getMessage(), 
                    3000, 
                    Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.open();
            }
        });
    }
    
    /**
     * Opens a dialog to add papers to a project.
     * 
     * @param project The project to add papers to
     */
    private void addPaperToProject(Project project) {
        LoggingUtil.debug(LOG, "addPaperToProject", "Adding papers to project: %s", project.getId());
        
        // Get user's papers that are not already in the project
        var availablePapers = paperService.getPapersNotInProject(currentUser, project);
        
        if (availablePapers.isEmpty()) {
            Notification.show(
                "You don't have any papers that aren't already in this project. Upload a new paper first.", 
                3000, 
                Notification.Position.MIDDLE
            );
            return;
        }
        
        // Use UI to navigate to upload paper view
        UI.getCurrent().navigate(UIConstants.ROUTE_UPLOAD_PAPER);
    }
}

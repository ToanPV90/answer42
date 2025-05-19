package com.samjdtechnologies.answer42.ui.views;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.Project;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    private final ProjectService projectService;
    private final PaperService paperService;

    private User currentUser;
    private FlexLayout projectsContainer;
    private TextField searchField;
    private Button createProjectButton;
    private String searchTerm = "";
    
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
        add(createWelcomeSection(), createToolbar(), createProjectsContainer());
        
        // Load initial data
        loadProjects();
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
            loadProjects();
        });
        
        // Create project button
        createProjectButton = new Button("Create Project", new Icon(VaadinIcon.PLUS));
        createProjectButton.addClassName(UIConstants.CSS_CREATE_PROJECT_BUTTON);
        createProjectButton.addClickListener(e -> showCreateProjectDialog());
        
        // Toolbar layout
        HorizontalLayout toolbar = new HorizontalLayout(searchField, createProjectButton);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.addClassName(UIConstants.CSS_TOOLBAR);
        
        return toolbar;
    }
    
    /**
     * Creates the container for the projects.
     * 
     * @return The projects container component
     */
    private FlexLayout createProjectsContainer() {
        projectsContainer = new FlexLayout();
        projectsContainer.addClassName(UIConstants.CSS_PROJECTS_CONTAINER);
        projectsContainer.setWidthFull();
        projectsContainer.getStyle().set("flex-wrap", "wrap");
        
        return projectsContainer;
    }
    
    /**
     * Loads projects for the current user.
     */
    private void loadProjects() {
        if (currentUser == null) {
            LoggingUtil.warn(LOG, "loadProjects", "Attempted to load projects with no user");
            return;
        }
        
        projectsContainer.removeAll();
        
        // Get projects sorted by updated_at
        PageRequest pageRequest = PageRequest.of(0, 50, Sort.by("updatedAt").descending());
        
        Page<Project> projectsPage;
        if (searchTerm.isEmpty()) {
            projectsPage = projectService.getProjectsByUser(currentUser, pageRequest);
        } else {
            projectsPage = projectService.searchProjectsByUser(currentUser, searchTerm, pageRequest);
        }
        
        List<Project> projects = projectsPage.getContent();
        
        if (projects.isEmpty()) {
            showEmptyState();
        } else {
            projects.forEach(this::createProjectCard);
        }
        
        LoggingUtil.info(LOG, "loadProjects", "Loaded %d projects for user %s", 
                projects.size(), currentUser.getUsername());
    }
    
    /**
     * Shows an empty state message when no projects are found.
     */
    private void showEmptyState() {
        VerticalLayout emptyState = new VerticalLayout();
        emptyState.addClassName(UIConstants.CSS_EMPTY_PROJECTS);
        emptyState.setWidthFull();
        
        Icon folderIcon = new Icon(VaadinIcon.FOLDER_O);
        folderIcon.setSize("48px");
        
        H3 emptyTitle = new H3("No projects found");
        
        Paragraph emptyText;
        if (searchTerm.isEmpty()) {
            emptyText = new Paragraph("Create your first research project to organize and analyze your papers.");
            Button createButton = new Button("Create Project", new Icon(VaadinIcon.PLUS), e -> showCreateProjectDialog());
            createButton.addClassName(UIConstants.CSS_CREATE_PROJECT_BUTTON);
            emptyState.add(folderIcon, emptyTitle, emptyText, createButton);
        } else {
            emptyText = new Paragraph("No projects match your search criteria. Try a different search term.");
            emptyState.add(folderIcon, emptyTitle, emptyText);
        }
        
        projectsContainer.add(emptyState);
    }
    
    /**
     * Creates a card for a project.
     * 
     * @param project The project to create a card for
     */
    private void createProjectCard(Project project) {
        Div card = new Div();
        card.addClassName(UIConstants.CSS_PROJECT_CARD);
        
        // Header section with title
        Div header = new Div();
        header.addClassName(UIConstants.CSS_PROJECT_HEADER);
        
        H3 title = new H3(project.getName());
        title.addClassName(UIConstants.CSS_PROJECT_TITLE);
        
        header.add(title);
        
        // Description section
        Paragraph description = new Paragraph(project.getDescription() != null ? project.getDescription() : "No description provided");
        description.addClassName(UIConstants.CSS_PROJECT_DESCRIPTION);
        
        // Metadata section
        Div metadata = new Div();
        metadata.addClassName(UIConstants.CSS_PROJECT_METADATA);
        
        Span papersCount = new Span();
        papersCount.addClassName(UIConstants.CSS_PROJECT_PAPERS_COUNT);
        papersCount.add(new Icon(VaadinIcon.FILE_TEXT_O));
        papersCount.add(new Span(project.getPapers().size() + " papers"));
        
        Span date = new Span();
        date.addClassName(UIConstants.CSS_PROJECT_DATE);
        date.add(new Icon(VaadinIcon.CALENDAR_CLOCK));
        date.add(new Span("Updated " + project.getUpdatedAt().format(DATE_FORMATTER)));
        
        metadata.add(papersCount, date);
        
        // Footer with buttons
        Div footer = new Div();
        footer.addClassName(UIConstants.CSS_PROJECT_FOOTER);
        
        Div actions = new Div();
        actions.addClassName(UIConstants.CSS_PROJECT_ACTIONS);
        
        Button viewButton = new Button("View", new Icon(VaadinIcon.EYE), e -> viewProject(project));
        Button editButton = new Button("Edit", new Icon(VaadinIcon.EDIT), e -> editProject(project));
        Button deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH), e -> deleteProject(project));
        
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        
        actions.add(viewButton, editButton, deleteButton);
        footer.add(actions);
        
        // Assemble card
        card.add(header, description, metadata, footer);
        projectsContainer.add(card);
    }
    
    /**
     * Shows the dialog for creating a new project.
     */
    private void showCreateProjectDialog() {
        Dialog dialog = new Dialog();
        dialog.addClassName(UIConstants.CSS_PROJECT_DIALOG);
        dialog.setHeaderTitle("Create New Project");
        
        FormLayout form = new FormLayout();
        
        TextField nameField = new TextField("Project Name");
        nameField.setRequired(true);
        nameField.setErrorMessage(UIConstants.VALIDATION_REQUIRED);
        nameField.setWidthFull();
        
        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setMinHeight("150px");
        descriptionField.setMaxHeight("200px");
        
        form.add(nameField, descriptionField);
        form.setColspan(descriptionField, 2);
        
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.addClassName(UIConstants.CSS_DIALOG_BUTTONS);
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        Button createButton = new Button("Create Project", e -> {
            if (createProject(nameField.getValue(), descriptionField.getValue())) {
                dialog.close();
            }
        });
        
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buttonLayout.add(cancelButton, createButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        
        VerticalLayout dialogLayout = new VerticalLayout(form, buttonLayout);
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);
        
        dialog.add(dialogLayout);
        dialog.open();
    }
    
    /**
     * Creates a new project.
     * 
     * @param name The name of the project
     * @param description The description of the project
     * @return true if the project was created successfully, false otherwise
     */
    private boolean createProject(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            Notification.show("Project name is required", 3000, Notification.Position.MIDDLE);
            return false;
        }
        
        try {
            Project newProject = projectService.createProject(name, description, currentUser);
            LoggingUtil.info(LOG, "createProject", "Created project '%s' with ID: %s", name, newProject.getId());
            
            Notification notification = new Notification("Project created successfully", 3000, Notification.Position.BOTTOM_START);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.open();
            
            loadProjects();
            return true;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createProject", "Failed to create project: %s", e.getMessage());
            Notification notification = new Notification("Failed to create project: " + e.getMessage(), 
                    3000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
            return false;
        }
    }
    
    /**
     * Handles viewing a project.
     * 
     * @param project The project to view
     */
    private void viewProject(Project project) {
        LoggingUtil.debug(LOG, "viewProject", "Opening project view: %s", project.getId());
        
        Dialog projectDialog = new Dialog();
        projectDialog.setHeaderTitle("Project: " + project.getName());
        projectDialog.setWidth("80%");
        projectDialog.setHeight("80%");
        
        VerticalLayout dialogContent = new VerticalLayout();
        dialogContent.setSizeFull();
        dialogContent.setPadding(false);
        
        // Project details section
        Div detailsSection = new Div();
        detailsSection.getStyle().set("padding", "var(--lumo-space-m)");
        
        H2 projectTitle = new H2(project.getName());
        
        Div descContainer = new Div();
        descContainer.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        
        H3 descTitle = new H3("Description");
        Paragraph descText = new Paragraph(project.getDescription() != null ? 
                project.getDescription() : "No description provided");
        
        descContainer.add(descTitle, descText);
        
        Div metaContainer = new Div();
        metaContainer.getStyle().set("display", "flex");
        metaContainer.getStyle().set("gap", "var(--lumo-space-m)");
        metaContainer.getStyle().set("margin-bottom", "var(--lumo-space-l)");
        
        Span created = new Span("Created: " + project.getCreatedAt().format(DATE_FORMATTER));
        Span updated = new Span("Last updated: " + project.getUpdatedAt().format(DATE_FORMATTER));
        Span visibility = new Span("Visibility: " + (project.getIsPublic() ? "Public" : "Private"));
        
        metaContainer.add(created, updated, visibility);
        
        detailsSection.add(projectTitle, descContainer, metaContainer);
        
        // Papers section
        Div papersSection = new Div();
        papersSection.getStyle().set("padding", "var(--lumo-space-m)");
        
        H3 papersTitle = new H3("Papers in Project");
        
        Grid<Paper> papersGrid = new Grid<>();
        papersGrid.setHeight("300px");
        
        papersGrid.addColumn(Paper::getTitle).setHeader("Title").setFlexGrow(3);
        papersGrid.addColumn(paper -> paper.getAuthors() != null ? 
                String.join(", ", paper.getAuthors()) : "").setHeader("Authors").setFlexGrow(2);
        papersGrid.addColumn(Paper::getYear).setHeader("Year").setFlexGrow(1);
        papersGrid.addColumn(Paper::getStatus).setHeader("Status").setFlexGrow(1);
        
        Set<Paper> papers = project.getPapers();
        papersGrid.setItems(papers);
        
        Button addPaperBtn = new Button("Add Paper", new Icon(VaadinIcon.PLUS));
        addPaperBtn.addClickListener(e -> addPaperToProject(project, projectDialog));
        
        HorizontalLayout papersHeader = new HorizontalLayout(papersTitle, addPaperBtn);
        papersHeader.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        papersHeader.setWidthFull();
        papersHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        
        if (papers.isEmpty()) {
            Paragraph emptyMessage = new Paragraph("No papers in this project yet.");
            emptyMessage.getStyle().set("padding", "var(--lumo-space-m)");
            emptyMessage.getStyle().set("color", "var(--lumo-tertiary-text-color)");
            emptyMessage.getStyle().set("text-align", "center");
            
            papersSection.add(papersHeader, emptyMessage);
        } else {
            papersSection.add(papersHeader, papersGrid);
        }
        
        // Actions bar
        HorizontalLayout actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setPadding(true);
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        
        Button editBtn = new Button("Edit Project", new Icon(VaadinIcon.EDIT));
        editBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        editBtn.addClickListener(e -> {
            projectDialog.close();
            editProject(project);
        });
        
        Button closeBtn = new Button("Close", e -> projectDialog.close());
        
        actions.add(closeBtn, editBtn);
        
        // Assemble dialog content
        dialogContent.add(detailsSection, papersSection, actions);
        
        projectDialog.add(dialogContent);
        projectDialog.open();
    }
    
    /**
     * Opens a dialog to add papers to a project.
     * 
     * @param project The project to add papers to
     * @param parentDialog The parent dialog to close on completion
     */
    private void addPaperToProject(Project project, Dialog parentDialog) {
        Dialog addPaperDialog = new Dialog();
        addPaperDialog.setHeaderTitle("Add Papers to Project");
        addPaperDialog.setWidth("70%");
        
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        
        // Get user's papers that are not already in the project
        List<Paper> availablePapers = paperService.getPapersNotInProject(currentUser, project);
        
        if (availablePapers.isEmpty()) {
            Paragraph noAvailablePapers = new Paragraph("You don't have any papers that aren't already in this project.");
            
            Button uploadBtn = new Button("Upload New Paper", e -> {
                addPaperDialog.close();
                parentDialog.close();
                UI.getCurrent().navigate(UIConstants.ROUTE_UPLOAD_PAPER);
            });
            uploadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            
            content.add(noAvailablePapers, uploadBtn);
            content.setAlignItems(FlexComponent.Alignment.CENTER);
        } else {
            // Create a grid of available papers
            Grid<Paper> papersGrid = new Grid<>();
            papersGrid.setSelectionMode(Grid.SelectionMode.MULTI);
            papersGrid.setHeight("300px");
            
            papersGrid.addColumn(Paper::getTitle).setHeader("Title").setFlexGrow(3);
            papersGrid.addColumn(paper -> paper.getAuthors() != null ? 
                    String.join(", ", paper.getAuthors()) : "").setHeader("Authors").setFlexGrow(2);
            papersGrid.addColumn(Paper::getYear).setHeader("Year").setFlexGrow(1);
            
            papersGrid.setItems(availablePapers);
            
            // Action buttons
            Button cancelBtn = new Button("Cancel", e -> addPaperDialog.close());
            
            Button addBtn = new Button("Add Selected", e -> {
                Set<Paper> selectedPapers = papersGrid.getSelectedItems();
                if (selectedPapers.isEmpty()) {
                    Notification.show("Please select at least one paper", 3000, Notification.Position.MIDDLE);
                    return;
                }
                
                addPaperDialog.close();
                
                int addedCount = 0;
                for (Paper paper : selectedPapers) {
                    try {
                        projectService.addPaperToProject(project.getId(), paper);
                        addedCount++;
                    } catch (Exception ex) {
                        LoggingUtil.error(LOG, "addPaperToProject", 
                                "Failed to add paper %s to project %s: %s", 
                                paper.getId().toString(), project.getId().toString(), ex.getMessage());
                    }
                }
                
                if (addedCount > 0) {
                    Notification.show(
                            addedCount + " paper" + (addedCount > 1 ? "s" : "") + " added to project", 
                            3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    
                    parentDialog.close();
                    viewProject(projectService.getProjectById(project.getId()).get());
                }
            });
            addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            
            HorizontalLayout buttons = new HorizontalLayout(cancelBtn, addBtn);
            buttons.setWidthFull();
            buttons.setPadding(true);
            buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            
            content.add(papersGrid, buttons);
        }
        
        addPaperDialog.add(content);
        addPaperDialog.open();
    }
    
    /**
     * Shows the dialog for editing a project.
     * 
     * @param project The project to edit
     */
    private void editProject(Project project) {
        Dialog dialog = new Dialog();
        dialog.addClassName(UIConstants.CSS_PROJECT_DIALOG);
        dialog.setHeaderTitle("Edit Project");
        
        FormLayout form = new FormLayout();
        
        TextField nameField = new TextField("Project Name");
        nameField.setRequired(true);
        nameField.setErrorMessage(UIConstants.VALIDATION_REQUIRED);
        nameField.setValue(project.getName());
        nameField.setWidthFull();
        
        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setMinHeight("150px");
        descriptionField.setMaxHeight("200px");
        descriptionField.setValue(project.getDescription() != null ? project.getDescription() : "");
        
        form.add(nameField, descriptionField);
        form.setColspan(descriptionField, 2);
        
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.addClassName(UIConstants.CSS_DIALOG_BUTTONS);
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        Button saveButton = new Button("Save Changes", e -> {
            if (updateProject(project, nameField.getValue(), descriptionField.getValue())) {
                dialog.close();
            }
        });
        
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buttonLayout.add(cancelButton, saveButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        
        VerticalLayout dialogLayout = new VerticalLayout(form, buttonLayout);
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);
        
        dialog.add(dialogLayout);
        dialog.open();
    }
    
    /**
     * Updates an existing project.
     * 
     * @param project The project to update
     * @param name The new name of the project
     * @param description The new description of the project
     * @return true if the project was updated successfully, false otherwise
     */
    private boolean updateProject(Project project, String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            Notification.show("Project name is required", 3000, Notification.Position.MIDDLE);
            return false;
        }
        
        try {
            Optional<Project> updatedProjectOpt = projectService.updateProjectDetails(
                    project.getId(), name, description, project.getIsPublic());
            
            if (updatedProjectOpt.isEmpty()) {
                throw new Exception("Project not found");
            }
            
            LoggingUtil.info(LOG, "updateProject", "Updated project with ID: %s", project.getId());
            
            Notification notification = new Notification("Project updated successfully", 3000, Notification.Position.BOTTOM_START);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.open();
            
            loadProjects();
            return true;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updateProject", "Failed to update project: %s", e.getMessage());
            Notification notification = new Notification("Failed to update project: " + e.getMessage(), 
                    3000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
            return false;
        }
    }
    
    /**
     * Shows the confirmation dialog for deleting a project.
     * 
     * @param project The project to delete
     */
    private void deleteProject(Project project) {
        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Delete Project");
        confirmDialog.setText(
                "Are you sure you want to delete the project \"" + project.getName() + "\"? " +
                "This action cannot be undone. Papers in this project will not be deleted, " +
                "but they will no longer be associated with this project.");
        
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");
        
        confirmDialog.setConfirmText("Delete");
        confirmDialog.setConfirmButtonTheme("error primary");
        
        confirmDialog.addConfirmListener(event -> {
            try {
                projectService.deleteProject(project.getId());
                LoggingUtil.info(LOG, "deleteProject", "Deleted project with ID: %s", project.getId());
                
                Notification notification = new Notification("Project deleted", 3000, Notification.Position.BOTTOM_START);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.open();
                
                loadProjects();
            } catch (Exception ex) {
                LoggingUtil.error(LOG, "deleteProject", "Failed to delete project: %s", ex.getMessage());
                Notification notification = new Notification("Failed to delete project: " + ex.getMessage(), 
                        3000, Notification.Position.MIDDLE);
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.open();
            }
        });
        
        confirmDialog.open();
    }
}

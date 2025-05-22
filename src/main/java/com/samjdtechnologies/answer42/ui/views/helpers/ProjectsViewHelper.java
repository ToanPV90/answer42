package com.samjdtechnologies.answer42.ui.views.helpers;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;

/**
 * Helper class for ProjectsView to handle non-UI rendering logic.
 */
public class ProjectsViewHelper {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProjectsViewHelper.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Configure the projects grid with columns and behaviors.
     * 
     * @param grid the Grid component to configure
     * @param actionsRenderer renderer for the actions column
     * @param detailsRenderer renderer for the expandable details
     * @param isPublicRenderer renderer for the public/private status column
     */
    public static void configureGrid(Grid<Project> grid, 
                                   ComponentRenderer<Component, Project> actionsRenderer,
                                   ComponentRenderer<Component, Project> detailsRenderer,
                                   ComponentRenderer<Component, Project> isPublicRenderer) {
        LoggingUtil.debug(LOG, "configureGrid", "Configuring projects grid");
        
        grid.addClassName(UIConstants.CSS_PAPERS_GRID);
        grid.setId("projects-table");
        
        // Add actions column first (on the left side)
        grid.addColumn(actionsRenderer)
            .setHeader("Actions")
            .setWidth("180px")
            .setFlexGrow(0);
        
        // Add data columns
        grid.addColumn(Project::getName)
            .setHeader("Name")
            .setFlexGrow(3)
            .setSortable(true);
        
        grid.addColumn(project -> project.getDescription() != null ? project.getDescription() : "")
            .setHeader("Description")
            .setFlexGrow(5);
        
        grid.addColumn(project -> project.getPapers().size())
            .setHeader("Papers")
            .setFlexGrow(1)
            .setSortable(true);
        
        grid.addColumn(project -> project.getUpdatedAt().format(DATE_FORMATTER))
            .setHeader("Last Updated")
            .setFlexGrow(2)
            .setSortable(true);
        
        // Add Is Public column after Last Updated
        grid.addColumn(isPublicRenderer)
            .setHeader("Is Public")
            .setWidth("120px")
            .setFlexGrow(0)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);
        
        // Configure row click to open project details
        grid.setItemDetailsRenderer(detailsRenderer);
        grid.setDetailsVisibleOnClick(true);
        
        LoggingUtil.debug(LOG, "configureGrid", "Grid configuration completed");
    }
    
    /**
     * Creates action buttons for a project in the grid.
     * 
     * @param project The project to create actions for
     * @param viewHandler The handler for view action
     * @param editHandler The handler for edit action
     * @param deleteHandler The handler for delete action
     * @return The actions component
     */
    public static Component createActions(Project project, 
                                       Consumer<Project> viewHandler,
                                       Consumer<Project> editHandler,
                                       Consumer<Project> deleteHandler) {
        // We'll style the row through the container
        Div container = new Div();
        // Apply styling class based on project visibility
        if (project.getIsPublic()) {
            container.addClassName("public-project");
        } else {
            container.addClassName("private-project");
        }
        
        // Download button (this would be a placeholder for consistency with other views)
        Button downloadButton = new Button(new Icon(VaadinIcon.DOWNLOAD));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        downloadButton.getElement().setAttribute("title", "View files");
        downloadButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);
        // For projects, the download button will navigate to view files
        downloadButton.addClickListener(e -> viewHandler.accept(project));
        
        // View button - now serves as a details button
        Button viewButton = new Button(new Icon(VaadinIcon.EYE));
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        viewButton.addClickListener(e -> viewHandler.accept(project));
        viewButton.getElement().setAttribute("title", "View details");
        viewButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Edit button
        Button editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        editButton.addClickListener(e -> editHandler.accept(project));
        editButton.getElement().setAttribute("title", "Edit project");
        editButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Delete button
        Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> deleteHandler.accept(project));
        deleteButton.getElement().setAttribute("title", "Delete project");
        deleteButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Create a compact layout for actions - without the toggle public/private button
        HorizontalLayout actions = new HorizontalLayout(
            downloadButton, viewButton, editButton, deleteButton
        );
        actions.setSpacing(false);
        actions.setMargin(false);
        actions.setPadding(false);
        actions.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTONS_CONTAINER);
        return actions;
    }
    
    /**
     * Creates the Is Public column component with toggle button.
     * 
     * @param project The project to create the component for
     * @param togglePublicHandler Handler for toggling public/private status
     * @return The Is Public column component
     */
    public static Component createIsPublicComponent(Project project, Consumer<Project> togglePublicHandler) {
        // Public/Private toggle button
        Button togglePublicButton = new Button();
        if (project.getIsPublic()) {
            // Project is public, show "make private" icon (lock)
            togglePublicButton.setIcon(new Icon(VaadinIcon.LOCK));
            togglePublicButton.getElement().setAttribute("title", "Make project private");
            togglePublicButton.setText("Public");
        } else {
            // Project is private, show "make public" icon (unlock)
            togglePublicButton.setIcon(new Icon(VaadinIcon.UNLOCK));
            togglePublicButton.getElement().setAttribute("title", "Make project public");
            togglePublicButton.setText("Private");
        }
        togglePublicButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        togglePublicButton.addClassName("is-public-button");
        togglePublicButton.addClickListener(e -> togglePublicHandler.accept(project));
        
        return togglePublicButton;
    }
    
    /**
     * Creates the details section for a project when expanded in the grid.
     * 
     * @param project The project to show details for
     * @param addPaperHandler Handler for adding papers to the project
     * @return The details component
     */
    public static Component createProjectDetails(Project project, Consumer<Project> addPaperHandler) {
        VerticalLayout details = new VerticalLayout();
        details.setSpacing(true);
        details.setPadding(true);
        
        // Create metadata section
        Div metaSection = new Div();
        
        // Created date
        Span created = new Span("Created: " + project.getCreatedAt().format(DATE_FORMATTER));
        created.getStyle().set("margin-right", "var(--lumo-space-m)");
        
        // Visibility (public/private)
        Span visibility = new Span("Visibility: " + (project.getIsPublic() ? "Public" : "Private"));
        
        metaSection.add(created, visibility);
        
        // List of papers in project (if any)
        H3 papersTitle = new H3("Papers in Project");
        
        // Add paper button
        Button addPaperBtn = new Button("Add Paper", new Icon(VaadinIcon.PLUS));
        addPaperBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addPaperBtn.addClickListener(e -> addPaperHandler.accept(project));
        
        HorizontalLayout papersHeader = new HorizontalLayout(papersTitle, addPaperBtn);
        papersHeader.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        papersHeader.setWidthFull();
        papersHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        
        // Add papers grid or empty message
        Set<Paper> papers = project.getPapers();
        
        if (papers.isEmpty()) {
            Paragraph emptyMessage = new Paragraph("No papers in this project yet.");
            details.add(metaSection, papersHeader, emptyMessage);
        } else {
            // Create a mini-grid for papers
            Grid<Paper> papersGrid = new Grid<>();
            papersGrid.setHeight("200px");
            
            papersGrid.addColumn(Paper::getTitle).setHeader("Title").setFlexGrow(3);
            papersGrid.addColumn(paper -> paper.getAuthors() != null ? 
                    String.join(", ", paper.getAuthors()) : "").setHeader("Authors").setFlexGrow(2);
            papersGrid.addColumn(Paper::getYear).setHeader("Year").setFlexGrow(1);
            papersGrid.addColumn(Paper::getStatus).setHeader("Status").setFlexGrow(1);
            
            papersGrid.setItems(papers);
            
            details.add(metaSection, papersHeader, papersGrid);
        }
        
        return details;
    }
    
    /**
     * Updates the pagination controls.
     * 
     * @param page Current page
     * @param totalPages Total number of pages
     * @param pageInfo Text display for pagination
     * @param prevButton Previous page button
     * @param nextButton Next page button
     * @return The adjusted page number
     */
    public static int updatePagination(int page, int totalPages, Span pageInfo, Button prevButton, Button nextButton) {
        // Update pagination info
        if (totalPages > 0) {
            pageInfo.setText((page + 1) + " of " + totalPages);
        } else {
            pageInfo.setText("0 of 0");
        }
        
        // Enable/disable navigation buttons
        prevButton.setEnabled(page > 0);
        nextButton.setEnabled(page < totalPages - 1);
        
        // Adjust page if beyond bounds
        if (page >= totalPages && totalPages > 0) {
            return totalPages - 1;
        }
        
        return page;
    }
    
    /**
     * Shows the dialog for creating a new project.
     * 
     * @param createHandler Handler to call when creating a project
     * @param paperService Service to get available papers
     * @param currentUser Current user for context
     */
    public static void showCreateProjectDialog(Consumer<Project> createHandler, 
                                          PaperService paperService,
                                          User currentUser) {
        Dialog dialog = new Dialog();
        dialog.addClassName(UIConstants.CSS_PROJECT_DIALOG);
        dialog.setHeaderTitle("Create New Project");
        dialog.setWidth("600px");
        
        // Add a close button to the corner
        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.getElement().getStyle().set("position", "absolute");
        closeButton.getElement().getStyle().set("right", "0");
        closeButton.getElement().getStyle().set("top", "0");
        closeButton.getElement().getStyle().set("margin", "var(--lumo-space-m)");
        closeButton.getElement().getStyle().set("cursor", "pointer");
        closeButton.addClickListener(e -> dialog.close());
        dialog.getHeader().add(closeButton);
        
        FormLayout form = new FormLayout();
        
        TextField nameField = new TextField("Project Name");
        nameField.setRequired(true);
        nameField.setErrorMessage(UIConstants.VALIDATION_REQUIRED);
        nameField.setWidthFull();
        
        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setMinHeight("150px");
        descriptionField.setMaxHeight("200px");
        
        // Add public checkbox with description
        Checkbox isPublicCheckbox = new Checkbox("Make project public");
        Div publicCheckboxDiv = new Div();
        publicCheckboxDiv.setWidthFull();
        
        Span publicDescription = new Span("Allow other users to view this project and its papers");
        publicDescription.getStyle().set("color", "var(--lumo-secondary-text-color)");
        publicDescription.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        publicDescription.getStyle().set("display", "block");
        publicDescription.getStyle().set("padding-left", "1.75em");
        
        publicCheckboxDiv.add(isPublicCheckbox, publicDescription);
        
        form.add(nameField, descriptionField, publicCheckboxDiv);
        form.setColspan(descriptionField, 2);
        form.setColspan(publicCheckboxDiv, 2);
        
        // Papers section - multi-select grid
        H3 papersHeader = new H3("Add Papers to Project");
        papersHeader.getStyle().set("margin-top", "var(--lumo-space-m)");
        papersHeader.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
        
        // Create paper selection tracking set
        Set<Paper> selectedGridPapers = new java.util.HashSet<>();
        
        Grid<Paper> papersGrid = new Grid<>();
        papersGrid.setHeight("200px");
        papersGrid.setWidthFull();
        
        // Configure columns - title, authors, year on the left, checkbox on the right
        papersGrid.addColumn(new ComponentRenderer<>(paper -> {
            VerticalLayout paperInfo = new VerticalLayout();
            paperInfo.setPadding(false);
            paperInfo.setSpacing(false);
            
            H3 title = new H3(paper.getTitle());
            title.getStyle().set("margin-top", "0");
            title.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
            title.getStyle().set("font-size", "var(--lumo-font-size-m)");
            
            String authors = paper.getAuthors() != null && !paper.getAuthors().isEmpty() 
                  ? String.join(", ", paper.getAuthors())
                  : "Unknown Author";
            
            Span authorSpan = new Span(authors);
            authorSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
            
            paperInfo.add(title, authorSpan);
            if (paper.getYear() != null) {
                Span yearSpan = new Span("Year: " + paper.getYear());
                yearSpan.getStyle()
                    .set("color", "var(--lumo-tertiary-text-color)")
                    .set("font-size", "var(--lumo-font-size-xs)");
                paperInfo.add(yearSpan);
            }
            return paperInfo;
        })).setHeader("Paper").setAutoWidth(true).setFlexGrow(1);
        
        // Add checkbox column on the right
        papersGrid.addColumn(new ComponentRenderer<>(paper -> {
            Checkbox checkbox = new Checkbox();
            checkbox.setValue(selectedGridPapers.contains(paper));
            
            checkbox.addValueChangeListener(event -> {
                if (event.getValue()) {
                    selectedGridPapers.add(paper);
                } else {
                    selectedGridPapers.remove(paper);
                }
            });
            
            return checkbox;
        })).setHeader("Select").setWidth("80px").setFlexGrow(0)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);
        
        // Load available papers
        List<Paper> availablePapers = new ArrayList<>();
        if (currentUser != null && paperService != null) {
            // Get all papers using default page request
            PageRequest pageRequest = PageRequest.of(0, 100); // Reasonable limit
            Page<Paper> papersPage = paperService.getPapersByUser(currentUser, pageRequest);
            availablePapers = papersPage.getContent();
        }
        
        if (!availablePapers.isEmpty()) {
            papersGrid.setItems(availablePapers);
        } else {
            papersHeader.setText("No papers available to add");
            papersGrid.setVisible(false);
        }
        
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.addClassName(UIConstants.CSS_DIALOG_BUTTONS);
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        Button createButton = new Button("Create Project", e -> {
            if (validateProjectForm(nameField)) {
                Project newProject = new Project();
                newProject.setName(nameField.getValue());
                newProject.setDescription(descriptionField.getValue());
                newProject.setIsPublic(isPublicCheckbox.getValue());
                
                // Add selected papers if any
                if (!selectedGridPapers.isEmpty()) {
                    newProject.setPapers(selectedGridPapers);
                }
                
                createHandler.accept(newProject);
                dialog.close();
            }
        });
        
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buttonLayout.add(cancelButton, createButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        
        VerticalLayout dialogLayout = new VerticalLayout(form, papersHeader, papersGrid, buttonLayout);
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);
        
        dialog.add(dialogLayout);
        dialog.open();
    }
    
    /**
     * Shows the dialog for editing a project.
     * 
     * @param project The project to edit
     * @param saveHandler Handler to call when saving changes
     */
    public static void showEditProjectDialog(Project project, Consumer<Project> saveHandler) {
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
            if (validateProjectForm(nameField)) {
                Project updatedProject = new Project();
                updatedProject.setId(project.getId());
                updatedProject.setName(nameField.getValue());
                updatedProject.setDescription(descriptionField.getValue());
                updatedProject.setIsPublic(project.getIsPublic());
                
                saveHandler.accept(updatedProject);
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
     * Shows the confirmation dialog for deleting a project.
     * 
     * @param project The project to delete
     * @param deleteHandler Handler to call when confirming deletion
     */
    public static void showDeleteConfirmDialog(Project project, Consumer<Project> deleteHandler) {
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
        
        confirmDialog.addConfirmListener(event -> deleteHandler.accept(project));
        
        confirmDialog.open();
    }
    
    /**
     * Validates the project form.
     * 
     * @param nameField The name field to validate
     * @return true if the form is valid, false otherwise
     */
    private static boolean validateProjectForm(TextField nameField) {
        if (nameField.getValue() == null || nameField.getValue().trim().isEmpty()) {
            Notification.show("Project name is required", 3000, Notification.Position.MIDDLE);
            return false;
        }
        return true;
    }
    
    /**
     * Update list of projects and refresh the grid based on search criteria.
     * 
     * @param currentUser The user whose projects are being displayed
     * @param searchTerm The search term to filter projects by
     * @param page The current page number (0-based)
     * @param pageSize The number of items per page
     * @param projectService The service for accessing project data
     * @return A Page object containing the projects
     */
    public static Page<Project> fetchProjectsList(User currentUser, String searchTerm, 
                                              int page, int pageSize, ProjectService projectService) {
        LoggingUtil.debug(LOG, "fetchProjectsList", "Fetching projects for user: " + 
            (currentUser != null ? currentUser.getUsername() + " (ID: " + currentUser.getId() + ")" : "null"));
            
        if (currentUser == null) {
            LoggingUtil.error(LOG, "fetchProjectsList", "Cannot fetch projects: Current user is null");
            return Page.empty(PageRequest.of(0, pageSize));
        }
        
        try {
            // Ensure params are not null to avoid NPE
            final String finalSearchTerm = searchTerm == null ? "" : searchTerm;
            
            LoggingUtil.debug(LOG, "fetchProjectsList", 
                "Using parameters: searchTerm='" + finalSearchTerm + 
                "', page=" + page +
                ", pageSize=" + pageSize);
            
            // Get projects sorted by updated_at
            PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by("updatedAt").descending());
            
            Page<Project> result;
            if (finalSearchTerm.isEmpty()) {
                result = projectService.getProjectsByUser(currentUser, pageRequest);
            } else {
                result = projectService.searchProjectsByUser(currentUser, finalSearchTerm, pageRequest);
            }
            
            LoggingUtil.info(LOG, "fetchProjectsList", "Retrieved " + result.getContent().size() + 
                " projects for user " + currentUser.getUsername() + " (total: " + result.getTotalElements() + ")");
            
            return result;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "fetchProjectsList", "Error fetching projects: " + e.getMessage(), e);
            return Page.empty(PageRequest.of(page, pageSize));
        }
    }
    
    /**
     * Update list of projects in the grid.
     *
     * @param currentUser The user whose projects are being displayed
     * @param searchTerm The search term to filter projects by
     * @param page The current page number (0-based)
     * @param pageSize The number of items per page
     * @param grid The Grid component to update
     * @param pageInfo The Span component for pagination info
     * @param prevButton The button for previous page
     * @param nextButton The button for next page
     * @param projectService The service for accessing project data
     * @param pageUpdateCallback Callback to update page number if needed
     */
    public static void updateList(User currentUser, 
                               String searchTerm,
                               int page,
                               int pageSize,
                               Grid<Project> grid,
                               Span pageInfo,
                               Button prevButton, 
                               Button nextButton,
                               ProjectService projectService,
                               Consumer<Integer> pageUpdateCallback) {
        
        LoggingUtil.debug(LOG, "updateList", "Updating with user: " + (currentUser != null ? currentUser.getUsername() : "null") + 
            ", search: '" + searchTerm + "'");
            
        // Use helper to fetch projects list
        Page<Project> projects = fetchProjectsList(
            currentUser, searchTerm, page, pageSize, projectService);
        
        // Update grid
        int contentSize = projects.getContent().size();
        LoggingUtil.info(LOG, "updateList", "Setting grid with " + contentSize + " projects");
        grid.setItems(projects.getContent());
        
        // Update pagination and potentially adjust page if beyond bounds
        int adjustedPage = updatePagination(
            projects.getNumber(), projects.getTotalPages(), pageInfo, prevButton, nextButton);
        
        // If page changed, update the current page and refresh
        if (adjustedPage != page && pageUpdateCallback != null) {
            LoggingUtil.debug(LOG, "updateList", "Page adjustment needed: " + page + " -> " + adjustedPage);
            pageUpdateCallback.accept(adjustedPage);
        }
    }
}

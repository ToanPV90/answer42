package com.samjdtechnologies.answer42.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.views.helpers.PapersHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * View for displaying and managing research papers.
 */
@Route(value = UIConstants.ROUTE_PAPERS, layout = MainLayout.class)
@PageTitle("Answer42 - Papers")
@Secured("ROLE_USER")
public class PapersView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(PapersView.class);
    
    private final PaperService paperService;
    private final UserService userService;
    

    private final Grid<Paper> grid = new Grid<>(Paper.class, false);
    private final TextField searchField = new TextField();
    private final Select<String> statusFilter = new Select<>();

    private User currentUser;
    private int page = 0;
    private final int pageSize = 10;
    private final Button prevButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Span pageInfo = new Span("0 of 0");

    public PapersView(PaperService paperService, UserService userService) {
        this.paperService = paperService;
        this.userService = userService;

        LoggingUtil.debug(LOG, "PapersView", "PapersView initialized");
        addClassName(UIConstants.CSS_PAPERS_VIEW);
        setSizeFull();
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing view components");
        // Configure the view
        removeAll();
        
        // Create a container with proper padding for consistent layout
        Div container = new Div();
        container.addClassName(UIConstants.CSS_CONTENT_CONTAINER);
        
        // Add content to the container
        container.add(createToolbar(), createContent());
        
        // Add container to the view
        add(container);

        // Load initial data
        updateList();
    }

    private Component createToolbar() {
        searchField.setPlaceholder("Search papers...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> {
            page = 0;
            updateList();
        });

        statusFilter.setItems("All", "PENDING", "PROCESSING", "PROCESSED", "FAILED");
        statusFilter.setValue("All");
        statusFilter.addValueChangeListener(e -> {
            page = 0;
            updateList();
        });

        Button uploadButton = new Button("Upload Paper", new Icon(VaadinIcon.UPLOAD));
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        uploadButton.addClickListener(e -> showUploadDialog());

        HorizontalLayout toolbar = new HorizontalLayout(searchField, statusFilter, uploadButton);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.addClassName(UIConstants.CSS_TOOLBAR);

        return toolbar;
    }

    private Component createContent() {
        configureGrid();

        HorizontalLayout pagination = new HorizontalLayout(prevButton, pageInfo, nextButton);
        pagination.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        pagination.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        prevButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        nextButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        
        prevButton.addClickListener(e -> {
            if (page > 0) {
                page--;
                updateList();
            }
        });
        
        nextButton.addClickListener(e -> {
            page++;
            updateList();
            // If we go beyond the last page, the updateList method will adjust
        });

        VerticalLayout content = new VerticalLayout(grid, pagination);
        content.setHeightFull();
        content.setPadding(false);
        content.setSpacing(false);

        return content;
    }

    private void configureGrid() {
        // Use helper class to configure the grid
        ComponentRenderer<Component, Paper> actionsRenderer = new ComponentRenderer<>(this::createActions);
        PapersHelper.configureGrid(grid, actionsRenderer, this::showPaperDetails);
    }

    private Component createActions(Paper paper) {
        // View button
        Button viewButton = new Button(new Icon(VaadinIcon.EYE));
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        viewButton.addClickListener(e -> showPaperDetails(paper));
        viewButton.getElement().setAttribute("title", "View details");

        // Edit button
        Button editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        editButton.addClickListener(e -> showEditDialog(paper));
        editButton.getElement().setAttribute("title", "Edit paper");

        // Download button
        Button downloadButton = new Button(new Icon(VaadinIcon.DOWNLOAD));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        downloadButton.addClickListener(e -> downloadPaper(paper));
        downloadButton.getElement().setAttribute("title", "Download paper");

        // Delete button
        Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Delete Paper");
            dialog.setText("Are you sure you want to delete this paper? This action cannot be undone.");
            dialog.setCancelable(true);
            
            dialog.setConfirmText("Delete");
            dialog.setConfirmButtonTheme("error primary");
            
            dialog.addConfirmListener(event -> {
                paperService.deletePaper(paper.getId());
                updateList();
                Notification.show("Paper deleted", 3000, Notification.Position.BOTTOM_START);
            });
            
            dialog.open();
        });
        deleteButton.getElement().setAttribute("title", "Delete paper");

        HorizontalLayout actions = new HorizontalLayout(viewButton, editButton, downloadButton, deleteButton);
        actions.setSpacing(true);
        return actions;
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        String statusValue = statusFilter.getValue();
        
        LoggingUtil.debug(LOG, "updateList", "Updating paper list with search: '%s', status: '%s', page: %d", 
            searchTerm, statusValue, page);
            
        // Use helper method to update the list, pagination, and grid
        PapersHelper.updateList(
            currentUser, 
            searchTerm, 
            statusValue, 
            page, 
            pageSize, 
            grid, 
            pageInfo, 
            prevButton, 
            nextButton, 
            paperService, 
            adjustedPage -> {
                // Update the page if it changed
                this.page = adjustedPage;
                // Recursive call to refresh with new page
                updateList();
            }
        );
    }

    private void showUploadDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Upload Paper");
        
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.addClassName(UIConstants.CSS_DIALOG_LAYOUT);
        
        // Title field
        TextField titleField = new TextField("Title");
        titleField.setRequired(true);
        titleField.setWidthFull();
        
        // Authors field (comma-separated)
        TextField authorsField = new TextField("Authors (comma-separated)");
        authorsField.setRequired(true);
        authorsField.setWidthFull();
        
        // File upload
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", ".pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        upload.setWidthFull();
        upload.setMaxFiles(1);
        
        Paragraph supportedFormats = new Paragraph("Supported formats: PDF, DOC, DOCX");
        supportedFormats.addClassName(UIConstants.CSS_SUPPORTED_FORMATS);
        
        Button submitButton = new Button("Upload");
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.setEnabled(false);
        
        // File upload state tracker
        final boolean[] fileUploaded = {false};
        
        // Enable button only when all required fields are filled
        titleField.addValueChangeListener(e -> 
            validateUploadForm(titleField, authorsField, fileUploaded[0], submitButton));
        authorsField.addValueChangeListener(e -> 
            validateUploadForm(titleField, authorsField, fileUploaded[0], submitButton));
        
        // Track upload success
        upload.addSucceededListener(e -> {
            fileUploaded[0] = true;
            validateUploadForm(titleField, authorsField, fileUploaded[0], submitButton);
        });
        
        // Track upload failure or file removal
        upload.addFailedListener(e -> {
            fileUploaded[0] = false;
            validateUploadForm(titleField, authorsField, fileUploaded[0], submitButton);
        });
        
        upload.addFileRejectedListener(e -> {
            fileUploaded[0] = false;
            validateUploadForm(titleField, authorsField, fileUploaded[0], submitButton);
        });
        
        submitButton.addClickListener(e -> {
            try {
                // Parse authors
                String[] authors = authorsField.getValue().split("\\s*,\\s*");
                
                // Use helper to create MultipartFile from buffer
                MultipartFile file = PapersHelper.createMultipartFileFromBuffer(buffer);
                
                // Upload the paper
                Paper paper = paperService.uploadPaper(
                    file, 
                    titleField.getValue(), 
                    authors,
                    currentUser
                );
                
                dialog.close();
                updateList();
                Notification.show("Paper uploaded successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
            } catch (Exception ex) {
                Notification.show("Upload failed: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        HorizontalLayout buttons = new HorizontalLayout(cancelButton, submitButton);
        buttons.addClassName(UIConstants.CSS_DIALOG_BUTTONS);
        
        dialogLayout.add(titleField, authorsField, upload, supportedFormats, buttons);
        dialog.addClassName(UIConstants.CSS_PAPER_DIALOG);
        dialog.add(dialogLayout);
        dialog.open();
    }
    
    // Delegating validation to the helper class
    private void validateUploadForm(TextField titleField, TextField authorsField, boolean fileUploaded, Button submitButton) {
        PapersHelper.validateUploadForm(titleField, authorsField, fileUploaded, submitButton);
    }

    private void showPaperDetails(Paper paper) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Paper Details");
        
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.addClassName(UIConstants.CSS_DIALOG_LAYOUT);
        
        // Paper title
        H2 title = new H2(paper.getTitle());
        title.addClassName(UIConstants.CSS_PAPER_TITLE);
        
        // Paper authors
        String authors = paper.getAuthors() != null ? 
                String.join(", ", paper.getAuthors()) : "";
        Paragraph authorsPara = new Paragraph("Authors: " + authors);
        
        // Paper metadata
        Div metadata = new Div();
        metadata.addClassName(UIConstants.CSS_METADATA_CONTAINER);
        
        if (paper.getJournal() != null) {
            metadata.add(new Paragraph("Journal: " + paper.getJournal()));
        }
        
        if (paper.getYear() != null) {
            metadata.add(new Paragraph("Year: " + paper.getYear()));
        }
        
        if (paper.getDoi() != null) {
            metadata.add(new Paragraph("DOI: " + paper.getDoi()));
        }
        
        // Status information
        Span status = new Span(paper.getStatus());
        status.getElement().getThemeList().add("badge " + paper.getStatus().toLowerCase());
        
        Paragraph processingStatus = new Paragraph("Processing Status: " + paper.getProcessingStatus());
        
        // Abstract
        if (paper.getAbstract() != null && !paper.getAbstract().isEmpty()) {
            H3 abstractHeader = new H3("Abstract");
            Paragraph abstractContent = new Paragraph(paper.getAbstract());
            dialogLayout.add(abstractHeader, abstractContent);
        }
        
        Button closeButton = new Button("Close", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        dialogLayout.add(title, authorsPara, metadata, status, processingStatus, closeButton);
        dialog.addClassName(UIConstants.CSS_PAPER_DETAILS_DIALOG);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void showEditDialog(Paper paper) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Paper Metadata");
        
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.addClassName(UIConstants.CSS_DIALOG_LAYOUT);
        
        // Title field
        TextField titleField = new TextField("Title");
        titleField.setValue(paper.getTitle());
        titleField.setRequired(true);
        titleField.setWidthFull();
        
        // Authors field (comma-separated)
        TextField authorsField = new TextField("Authors (comma-separated)");
        authorsField.setValue(paper.getAuthors() != null ? 
                String.join(", ", paper.getAuthors()) : "");
        authorsField.setRequired(true);
        authorsField.setWidthFull();
        
        // Journal field
        TextField journalField = new TextField("Journal");
        journalField.setValue(paper.getJournal() != null ? paper.getJournal() : "");
        journalField.setWidthFull();
        
        // Year field
        TextField yearField = new TextField("Year");
        yearField.setValue(paper.getYear() != null ? paper.getYear().toString() : "");
        yearField.setPattern("[0-9]*");
        yearField.setWidthFull();
        
        // DOI field
        TextField doiField = new TextField("DOI");
        doiField.setValue(paper.getDoi() != null ? paper.getDoi() : "");
        doiField.setWidthFull();
        
        // Abstract field
        TextField abstractField = new TextField("Abstract");
        abstractField.setValue(paper.getAbstract() != null ? paper.getAbstract() : "");
        abstractField.setWidthFull();
        
        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            try {
                // Parse authors
                String[] authors = authorsField.getValue().split("\\s*,\\s*");
                
                // Parse year
                Integer year = yearField.getValue().isEmpty() ? 
                        null : Integer.parseInt(yearField.getValue());
                
                // Update paper metadata
                paperService.updatePaperMetadata(
                    paper.getId(),
                    titleField.getValue(),
                    authors,
                    abstractField.getValue(),
                    journalField.getValue(),
                    year,
                    doiField.getValue()
                );
                
                dialog.close();
                updateList();
                Notification.show("Paper updated successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
            } catch (Exception ex) {
                Notification.show("Update failed: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        
        HorizontalLayout buttons = new HorizontalLayout(cancelButton, saveButton);
        buttons.addClassName(UIConstants.CSS_DIALOG_BUTTONS);
        
        dialogLayout.add(
            titleField, authorsField, journalField, yearField, 
            doiField, abstractField, buttons
        );
        
        dialog.addClassName(UIConstants.CSS_PAPER_DIALOG);
        dialog.add(dialogLayout);
        dialog.open();
    }

    private void downloadPaper(Paper paper) {
        // Delegate to helper method to handle download functionality
        getUI().ifPresent(ui -> PapersHelper.downloadPaper(paper, ui, this));
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting authentication");
        // Get authentication directly - no need to check isAuthenticated since @Secured will handle
        // access control through Spring Security 
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoggingUtil.debug(LOG, "beforeEnter", "Authentication: %s", auth != null ? 
                auth.getName() + " (authenticated: " + auth.isAuthenticated() + ")" : "null");
        
        // Try to get the current user - this is needed for the view to function properly
        try {
            if (auth != null) {
                currentUser = userService.findByUsername(auth.getName()).orElse(null);
                if (currentUser != null) {
                    LoggingUtil.info(LOG, "beforeEnter", "Current user loaded: %s (ID: %s)", 
                        currentUser.getUsername(), currentUser.getId());
                } else {
                    LoggingUtil.warn(LOG, "beforeEnter", "Could not find user with username: %s", auth.getName());
                }
            } else {
                LoggingUtil.warn(LOG, "beforeEnter", "Authentication is null, cannot load current user");
            }
            
            // Initialize the view regardless of user status
            // This will be controlled by Spring Security annotations
            initializeView();
            
        } catch (Exception e) {
            // Log error but still initialize view
            LOG.error("Error in beforeEnter: {}", e.getMessage(), e);
            initializeView();
        }
    }
}

package com.samjdtechnologies.answer42.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.multipart.MultipartFile;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.helpers.views.PapersViewHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
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

    private final Grid<Paper> grid = new Grid<>(Paper.class, false);
    private final TextField searchField = new TextField();
    private final Select<String> statusFilter = new Select<>();

    private User currentUser;
    private int page = 0;
    private final int pageSize = 10;
    private final Button prevButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Span pageInfo = new Span("0 of 0");

    /**
     * Constructs the Papers view with necessary service dependencies.
     * Initializes the view for displaying, searching, and managing uploaded papers.
     * 
     * @param paperService the service for paper-related operations including retrieval and management
     */
    public PapersView(PaperService paperService) {
        this.paperService = paperService;

        addClassName(UIConstants.CSS_PAPERS_VIEW);
        getStyle().setHeight("750px");

        LoggingUtil.debug(LOG, "PapersView", "PapersView initialized");
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing view components");
        
        // Configure the view
        removeAll();
        
        // Add components directly to the view
        add(createWelcomeSection(), createToolbar(), createContent());

        // Load initial data
        updateList();
    }

    private Component createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("My Papers");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);
        
        Paragraph welcomeSubtitle = new Paragraph("Upload, organize, and analyze your scientific papers");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
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
        
        Button bulkUploadButton = new Button("Bulk Upload", new Icon(VaadinIcon.FILE_TREE));
        bulkUploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bulkUploadButton.getElement().setAttribute("title", "Upload multiple papers at once");
        bulkUploadButton.addClickListener(e -> UI.getCurrent().navigate(UIConstants.ROUTE_BULK_UPLOAD));

        HorizontalLayout rightButtons = new HorizontalLayout(bulkUploadButton, uploadButton);
        rightButtons.setSpacing(true);
        
        HorizontalLayout toolbar = new HorizontalLayout(searchField, statusFilter, rightButtons);
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
        PapersViewHelper.configureGrid(grid, actionsRenderer, this::showPaperDetails);
        
        // Add the CSS class for styling
        grid.addClassName(UIConstants.CSS_PAPERS_GRID);
        
        // Ensure the grid has an ID for CSS targeting
        grid.setId("table");
    }

    private Component createActions(Paper paper) {
        // Create more compact buttons - use extra-small theme
        // View button
        Button viewButton = new Button(new Icon(VaadinIcon.EYE));
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        viewButton.addClickListener(e -> showPaperDetails(paper));
        viewButton.getElement().setAttribute("title", "View details");
        // Apply custom class for compact buttons
        viewButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Related Papers button
        Button relatedPapersButton = new Button(new Icon(VaadinIcon.CONNECT));
        relatedPapersButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        relatedPapersButton.addClickListener(e -> UI.getCurrent().navigate(UIConstants.ROUTE_RELATED_PAPERS + "/" + paper.getId()));
        relatedPapersButton.getElement().setAttribute("title", "View related papers");
        relatedPapersButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Edit button
        Button editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        editButton.addClickListener(e -> showEditDialog(paper));
        editButton.getElement().setAttribute("title", "Edit paper");
        editButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Download button
        Button downloadButton = new Button(new Icon(VaadinIcon.DOWNLOAD));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        downloadButton.addClickListener(e -> downloadPaper(paper));
        downloadButton.getElement().setAttribute("title", "Download paper");
        downloadButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Delete button
        final Paper paperForDelete = paper; // Make effectively final for lambda
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
                paperService.deletePaper(paperForDelete.getId());
                updateList();
                Notification.show("Paper deleted", 3000, Notification.Position.BOTTOM_START);
            });
            
            dialog.open();
        });
        deleteButton.getElement().setAttribute("title", "Delete paper");
        deleteButton.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTON);

        // Create a more compact layout - now includes related papers button
        HorizontalLayout actions = new HorizontalLayout(downloadButton, viewButton, relatedPapersButton, editButton, deleteButton);
        actions.setSpacing(false);
        actions.setMargin(false);
        actions.setPadding(false);
        actions.addClassName(UIConstants.CSS_PAPERS_ACTION_BUTTONS_CONTAINER);
        return actions;
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        String statusValue = statusFilter.getValue();
        
        LoggingUtil.debug(LOG, "updateList", "Updating paper list with search: '%s', status: '%s', page: %d", 
            searchTerm, statusValue, page);
            
        // Use helper method to update the list, pagination, and grid
        PapersViewHelper.updateList(
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
        FileBuffer buffer = new FileBuffer();
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
                MultipartFile file = PapersViewHelper.createMultipartFileFromBuffer(buffer);
                
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
        PapersViewHelper.validateUploadForm(titleField, authorsField, fileUploaded, submitButton);
    }

    private void showPaperDetails(Paper paper) {
        
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Paper Details");
        dialog.setWidth("800px");
        dialog.setMaxWidth("90vw");
        
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
        
        // Pipeline progress section for papers being processed
        Component progressSection = createPipelineProgressSection(paper);
        
        // Abstract
        if (paper.getPaperAbstract() != null && !paper.getPaperAbstract().isEmpty()) {
            H3 abstractHeader = new H3("Abstract");
            Paragraph abstractContent = new Paragraph(paper.getPaperAbstract());
            dialogLayout.add(abstractHeader, abstractContent);
        }
        
        Button closeButton = new Button("Close", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        dialogLayout.add(title, authorsPara, metadata, status, processingStatus, progressSection, closeButton);
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
        abstractField.setValue(paper.getPaperAbstract() != null ? paper.getPaperAbstract() : "");
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
        getUI().ifPresent(ui -> PapersViewHelper.downloadPaper(paper, ui, this));
    }
    
    /**
     * Creates a pipeline progress section for papers currently being processed.
     */
    private Component createPipelineProgressSection(Paper paper) {
        if (paper == null || paper.getProcessingStatus() == null) {
            return new Div(); // Empty div if no processing status
        }
        
        String processingStatus = paper.getProcessingStatus();
        
        // Only show progress section for papers that are actively being processed
        if (!"PROCESSING".equals(paper.getStatus()) && 
            !processingStatus.contains("PIPELINE") && 
            !processingStatus.contains("PROCESSING")) {
            return new Div(); // Empty div for non-processing papers
        }
        
        Div progressSection = new Div();
        progressSection.addClassName("pipeline-progress-section");
        progressSection.getStyle()
            .setMargin("var(--lumo-space-m) 0")
            .setPadding("var(--lumo-space-m)")
            .setBorder("1px solid var(--lumo-contrast-20pct)")
            .setBorderRadius("var(--lumo-border-radius-m)");
        
        H3 progressHeader = new H3("Multi-Agent Processing Pipeline");
        progressHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        
        // Create agent progress indicators
        String[] agents = {
            "Text Extraction", "Metadata Enhancement", "Content Summarization",
            "Concept Explanation", "Quality Checking", "Citation Formatting"
        };
        
        VerticalLayout agentList = new VerticalLayout();
        agentList.setPadding(false);
        agentList.setSpacing(true);
        
        for (String agentName : agents) {
            HorizontalLayout agentItem = createAgentProgressItem(agentName, processingStatus);
            agentList.add(agentItem);
        }
        
        // Overall progress bar
        Div progressBar = createOverallProgressBar(processingStatus);
        
        // Processing time estimate
        Paragraph timeEstimate = new Paragraph("Estimated completion: 5-10 minutes");
        timeEstimate.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)")
            .setMargin("var(--lumo-space-s) 0 0 0");
        
        progressSection.add(progressHeader, progressBar, agentList, timeEstimate);
        return progressSection;
    }
    
    /**
     * Creates a progress item for an individual agent.
     */
    private HorizontalLayout createAgentProgressItem(String agentName, String processingStatus) {
        HorizontalLayout item = new HorizontalLayout();
        item.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        item.setSpacing(true);
        item.setPadding(false);
        item.getStyle().setPadding("var(--lumo-space-xs) 0");
        
        // Status icon
        Icon statusIcon = determineAgentStatus(agentName, processingStatus);
        statusIcon.setSize("16px");
        
        // Agent name
        Span nameSpan = new Span(agentName);
        
        // Status text
        String status = getAgentStatusText(agentName, processingStatus);
        Span statusSpan = new Span(status);
        statusSpan.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)");
        
        item.add(statusIcon, nameSpan, statusSpan);
        item.setFlexGrow(1, nameSpan); // Use setFlexGrow instead of style.setFlex
        return item;
    }
    
    /**
     * Determines the appropriate status icon for an agent based on processing status.
     */
    private Icon determineAgentStatus(String agentName, String processingStatus) {
        if (processingStatus.contains("COMPLETED") || processingStatus.contains("PROCESSED")) {
            Icon icon = VaadinIcon.CHECK_CIRCLE.create();
            icon.getStyle().setColor("var(--lumo-success-color)");
            return icon;
        } else if (processingStatus.contains("PROCESSING") && processingStatus.contains(agentName.toUpperCase().replace(" ", "_"))) {
            Icon icon = VaadinIcon.COG.create();
            icon.getStyle().setColor("var(--lumo-primary-color)");
            return icon;
        } else if (processingStatus.contains("FAILED") || processingStatus.contains("ERROR")) {
            Icon icon = VaadinIcon.EXCLAMATION_CIRCLE.create();
            icon.getStyle().setColor("var(--lumo-error-color)");
            return icon;
        } else {
            Icon icon = VaadinIcon.CLOCK.create();
            icon.getStyle().setColor("var(--lumo-disabled-text-color)");
            return icon;
        }
    }
    
    /**
     * Gets the status text for an agent based on processing status.
     */
    private String getAgentStatusText(String agentName, String processingStatus) {
        if (processingStatus.contains("COMPLETED") || processingStatus.contains("PROCESSED")) {
            return "Completed";
        } else if (processingStatus.contains("PROCESSING") && processingStatus.contains(agentName.toUpperCase().replace(" ", "_"))) {
            return "Processing...";
        } else if (processingStatus.contains("FAILED") || processingStatus.contains("ERROR")) {
            return "Failed";
        } else {
            return "Pending";
        }
    }
    
    /**
     * Creates an overall progress bar for the pipeline.
     */
    private Div createOverallProgressBar(String processingStatus) {
        Div progressContainer = new Div();
        progressContainer.getStyle()
            .setWidth("100%")
            .setHeight("8px")
            .setBorderRadius("4px")
            .setBackgroundColor("var(--lumo-contrast-10pct)")
            .setMargin("0 0 var(--lumo-space-m) 0");
        
        Div progressFill = new Div();
        progressFill.getStyle()
            .setHeight("100%")
            .setBorderRadius("4px")
            .setBackgroundColor("var(--lumo-primary-color)")
            .setTransition("width 0.3s ease");
        
        // Calculate progress percentage based on status
        int progressPercentage = calculateProgressPercentage(processingStatus);
        progressFill.getStyle().setWidth(progressPercentage + "%");
        
        progressContainer.add(progressFill);
        return progressContainer;
    }
    
    /**
     * Calculates progress percentage based on processing status.
     */
    private int calculateProgressPercentage(String processingStatus) {
        if (processingStatus.contains("COMPLETED") || processingStatus.contains("PROCESSED")) {
            return 100;
        } else if (processingStatus.contains("CITATION")) {
            return 85;
        } else if (processingStatus.contains("QUALITY")) {
            return 70;
        } else if (processingStatus.contains("CONCEPT")) {
            return 55;
        } else if (processingStatus.contains("SUMMARIZATION")) {
            return 40;
        } else if (processingStatus.contains("METADATA")) {
            return 25;
        } else if (processingStatus.contains("EXTRACTION")) {
            return 10;
        } else {
            return 5;
        }
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting user from session");
        
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
}

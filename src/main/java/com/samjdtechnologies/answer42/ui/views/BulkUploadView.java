package com.samjdtechnologies.answer42.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.Project;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.views.helpers.BulkUploadHelper;
import com.samjdtechnologies.answer42.ui.views.helpers.FileEntry;
import com.samjdtechnologies.answer42.ui.views.helpers.FileStatus;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.MultiFileReceiver;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * View for bulk uploading papers.
 */
@Route(value = UIConstants.ROUTE_BULK_UPLOAD, layout = MainLayout.class)
@PageTitle("Answer42 - Bulk Upload Papers")
@Secured("ROLE_USER")
public class BulkUploadView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(BulkUploadView.class);
    
    private final PaperService paperService;
    private final ProjectService projectService;
    
    private User currentUser;
    private List<MemoryBuffer> fileBuffers = new ArrayList<>();
    private Map<String, FileEntry> fileEntries = new ConcurrentHashMap<>();
    
    // UI Components
    private Select<Project> projectSelect;
    private TextArea authorsField;
    private Checkbox publicCheckbox;
    private Upload upload;
    private ProgressBar progressBar;
    private Span progressLabel;
    private Button processButton;
    private VerticalLayout resultsContainer;
    private Div fileListContainer;
    
    /**
     * Constructs the bulk upload view with necessary service dependencies.
     * Initializes the view for uploading and processing multiple research papers in a batch.
     * 
     * @param paperService the service for paper-related operations including upload and metadata management
     * @param projectService the service for project-related operations and paper-project associations
     */
    public BulkUploadView(PaperService paperService, ProjectService projectService) {
        this.paperService = paperService;
        this.projectService = projectService;
        
        addClassName(UIConstants.CSS_BULK_UPLOAD_VIEW);
        getStyle().setHeight("auto");

        LoggingUtil.debug(LOG, "BulkUploadView", "BulkUploadView initialized");
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing view components");
        
        // Configure the view
        removeAll();
        
        // Create headers and section components
        H2 title = new H2("Bulk Upload Papers");
        Paragraph subtitle = new Paragraph("Upload and process multiple scientific papers in a single batch");
        
        // Add components to the view
        add(
            title, 
            subtitle,
            createSettingsSection(),
            createUploadSection(),
            createProgressSection(),
            createActionButtons(),
            createResultsSection()
        );
    }
    
    private Component createSettingsSection() {
        H3 sectionTitle = new H3("Common Settings");
        
        // Project selection
        projectSelect = new Select<>();
        projectSelect.setLabel("Project (Optional)");
        projectSelect.setWidthFull();
        projectSelect.setItemLabelGenerator(project -> project != null ? project.getName() : "");
        
        // Load user projects
        List<Project> projects = new ArrayList<>();
        if (currentUser != null) {
            projects = getProjectsByUser(currentUser);
        }
        
        // Set the items
        projectSelect.setItems(projects);
        projectSelect.setPlaceholder("No Project");
        
        // Authors field
        authorsField = new TextArea("Common Authors (Optional)");
        authorsField.setWidthFull();
        authorsField.setPlaceholder("Enter comma-separated list of authors common to all papers");
        authorsField.setHelperText("Leave blank to use filenames as paper titles without author information");
        authorsField.setHeight("100px");
        
        // Public checkbox
        publicCheckbox = new Checkbox("Make papers public");
        publicCheckbox.setWidthFull();
        Paragraph publicDescription = new Paragraph("Allow other users to view these papers");
        publicDescription.addClassName(UIConstants.CSS_HELP_TEXT_SECONDARY);
        
        // Create section container
        VerticalLayout settingsContainer = new VerticalLayout(
            projectSelect,
            authorsField,
            publicCheckbox,
            publicDescription
        );
        settingsContainer.setPadding(false);
        settingsContainer.setSpacing(true);
        
        VerticalLayout sectionContainer = new VerticalLayout(sectionTitle, settingsContainer);
        sectionContainer.addClassName(UIConstants.CSS_BULK_UPLOAD_CONTAINER);
        sectionContainer.setPadding(true);
        sectionContainer.setSpacing(true);
        
        return sectionContainer;
    }
    
    private Component createUploadSection() {
        H3 sectionTitle = new H3("Upload PDF Files");
        
        // Create upload component that handles multiple files
        upload = new Upload(createMultiFileReceiver());
        upload.setAcceptedFileTypes("application/pdf", ".pdf");
        upload.setWidthFull();
        upload.setMaxFiles(100);
        upload.setMaxFileSize(20 * 1024 * 1024); // 20MB per file
        upload.setDropAllowed(true);
        upload.setUploadButton(new Button("Upload files"));
        upload.setDropLabelIcon(new Icon(VaadinIcon.UPLOAD));
        
        Span dropLabel = new Span("or drag and drop multiple files");
        dropLabel.addClassName(UIConstants.CSS_DROP_LABEL);
        upload.setDropLabel(dropLabel);
        upload.addClassName(UIConstants.CSS_UPLOAD_CONTAINER);
        
        // Add info about max file size
        Paragraph fileInfo = new Paragraph("PDF files up to 20MB each, maximum 10 files per batch");
        fileInfo.addClassName(UIConstants.CSS_FILE_INFO);
        
        // Track upload success/failure
        upload.addSucceededListener(event -> {
            LoggingUtil.debug(LOG, "createUploadSection", "File uploaded successfully: %s", event.getFileName());
            updateFileList();
        });
        
        upload.addFailedListener(event -> {
            LoggingUtil.error(LOG, "createUploadSection", "File upload failed: " + event.getReason());
            Notification.show("Upload failed: " + event.getReason(), 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
        
        upload.addFileRejectedListener(event -> {
            LoggingUtil.error(LOG, "createUploadSection", "File rejected: " + event.getErrorMessage());
            Notification.show("File rejected: " + event.getErrorMessage(), 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
        
        // File list container
        fileListContainer = new Div();
        fileListContainer.addClassName(UIConstants.CSS_FILE_LIST);
        fileListContainer.setVisible(false);
        
        // Create step container
        VerticalLayout sectionContainer = new VerticalLayout(
            sectionTitle, 
            upload, 
            fileInfo,
            fileListContainer
        );
        sectionContainer.addClassName(UIConstants.CSS_BULK_UPLOAD_CONTAINER);
        sectionContainer.setPadding(true);
        sectionContainer.setSpacing(true);
        
        return sectionContainer;
    }
    
    private Component createProgressSection() {
        progressBar = new ProgressBar();
        progressBar.setMin(0);
        progressBar.setMax(1);
        progressBar.setValue(0);
        progressBar.setWidthFull();
        progressBar.addClassName(UIConstants.CSS_UPLOAD_PROGRESS);
        
        progressLabel = new Span("Ready to process");
        
        VerticalLayout progressContainer = new VerticalLayout(progressLabel, progressBar);
        progressContainer.setSpacing(false);
        progressContainer.setPadding(false);
        progressContainer.setVisible(false);
        progressContainer.addClassName(UIConstants.CSS_UPLOAD_PROGRESS);
        
        return progressContainer;
    }
    
    private Component createActionButtons() {
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> UI.getCurrent().navigate(UIConstants.ROUTE_PAPERS));
        
        processButton = new Button("Process Files");
        processButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        processButton.setEnabled(false);
        processButton.addClickListener(e -> processBulkUpload());
        
        HorizontalLayout actionButtons = new HorizontalLayout(cancelButton, processButton);
        actionButtons.addClassName(UIConstants.CSS_ACTION_BUTTONS);
        actionButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actionButtons.setWidthFull();
        
        return actionButtons;
    }
    
    private Component createResultsSection() {
        H3 resultsTitle = new H3("Processing Results");
        
        resultsContainer = new VerticalLayout();
        resultsContainer.addClassName(UIConstants.CSS_UPLOAD_RESULTS);
        resultsContainer.setVisible(false);
        
        VerticalLayout sectionContainer = new VerticalLayout(resultsTitle, resultsContainer);
        sectionContainer.addClassName(UIConstants.CSS_BULK_UPLOAD_CONTAINER);
        sectionContainer.setPadding(true);
        sectionContainer.setSpacing(true);
        sectionContainer.setVisible(false);
        
        return sectionContainer;
    }
    
    private MultiFileReceiver createMultiFileReceiver() {
        return (filename, mimeType) -> {
            // Create a new buffer for each file
            MemoryBuffer buffer = new MemoryBuffer();
            fileBuffers.add(buffer);
            
            // Return the receiver for this specific file
            return buffer.receiveUpload(filename, mimeType);
        };
    }
    
    private void updateFileList() {
        // Update file entries map
        fileEntries = BulkUploadHelper.createFileEntriesMap(fileBuffers);
        
        // Clear and rebuild the file list container
        fileListContainer.removeAll();
        
        if (!fileEntries.isEmpty()) {
            fileListContainer.setVisible(true);
            
            for (FileEntry entry : fileEntries.values()) {
                Div fileItem = createFileItemComponent(entry);
                fileListContainer.add(fileItem);
            }
            
            // Enable process button if we have files
            processButton.setEnabled(!fileEntries.isEmpty());
        } else {
            fileListContainer.setVisible(false);
            processButton.setEnabled(false);
        }
    }
    
    private Div createFileItemComponent(FileEntry entry) {
        Div fileItem = new Div();
        fileItem.addClassName(UIConstants.CSS_FILE_ITEM);
        
        // File name
        Span fileName = new Span(entry.getFileName());
        
        // File size
        String fileSizeText = formatFileSize(entry.getFileSize());
        Span fileSize = new Span(fileSizeText);
        
        // Status
        Span status = new Span(entry.getStatus().getDescription());
        status.addClassName(UIConstants.CSS_FILE_STATUS);
        status.getElement().getThemeList().add(entry.getStatus().name().toLowerCase());
        
        // Add components to layout
        HorizontalLayout layout = new HorizontalLayout(fileName, fileSize, status);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        layout.setWidthFull();
        
        fileItem.add(layout);
        return fileItem;
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        }
    }
    
    private void processBulkUpload() {
        if (fileBuffers.isEmpty()) {
            Notification.show("No files to process", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }
        
        LoggingUtil.info(LOG, "processBulkUpload", "Starting bulk upload process with %d files", fileBuffers.size());
        
        // Parse authors
        List<String> authors = BulkUploadHelper.parseAuthors(authorsField.getValue());
        
        // Get selected project
        Project selectedProject = projectSelect.getValue();
        
        // Show progress components
        progressBar.getParent().ifPresent(component -> component.setVisible(true));
        progressBar.setValue(0);
        
        // Show results container
        resultsContainer.getParent().ifPresent(component -> component.setVisible(true));
        resultsContainer.setVisible(true);
        resultsContainer.removeAll();
        
        // Disable inputs during processing
        setProcessingMode(true);
        
        // Process the files
        BulkUploadHelper.processBulkUpload(
            fileBuffers,
            fileEntries,
            authors,
            currentUser,
            selectedProject,
            publicCheckbox.getValue(),
            paperService,
            projectService,
            UI.getCurrent(),
            this::updateProgressInfo
        );
    }
    
    private void updateProgressInfo(String progressText) {
        progressLabel.setText(progressText);
        
        // Extract percentage from text (e.g., "Processing: 3/10 files (30.0%)")
        try {
            String percentText = progressText.substring(progressText.indexOf("(") + 1, progressText.indexOf("%"));
            double percent = Double.parseDouble(percentText);
            progressBar.setValue(percent / 100.0);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updateProgressInfo", "Error parsing progress percentage: " + e.getMessage());
        }
        
        // Update file statuses in the UI
        updateResultsDisplay();
    }
    
    private void updateResultsDisplay() {
        resultsContainer.removeAll();
        
        for (FileEntry entry : fileEntries.values()) {
            Component resultItem = createResultItemComponent(entry);
            resultsContainer.add(resultItem);
        }
    }
    
    private Component createResultItemComponent(FileEntry entry) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        
        // Status icon
        Icon icon;
        String statusClass;
        
        switch (entry.getStatus()) {
            case SUCCESS:
                icon = VaadinIcon.CHECK_CIRCLE.create();
                statusClass = "success";
                break;
            case ERROR:
                icon = VaadinIcon.EXCLAMATION_CIRCLE.create();
                statusClass = "error";
                break;
            case PROCESSING:
                icon = VaadinIcon.HOURGLASS.create();
                statusClass = "processing";
                break;
            default:
                icon = VaadinIcon.CIRCLE.create();
                statusClass = "pending";
                break;
        }
        
        icon.getElement().getThemeList().add(statusClass);
        
        // Filename
        Span fileName = new Span(entry.getFileName());
        
        // Status text
        String statusText = entry.getStatus().getDescription();
        if (entry.getStatusDetail() != null && !entry.getStatusDetail().isEmpty()) {
            statusText += ": " + entry.getStatusDetail();
        }
        
        Span status = new Span(statusText);
        status.addClassName(UIConstants.CSS_FILE_STATUS);
        status.addClassName(statusClass);
        
        // Add links for successful uploads
        if (entry.getStatus() == FileStatus.SUCCESS && entry.getPaperId() != null) {
            Button viewButton = new Button("View", VaadinIcon.EYE.create());
            viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            viewButton.addClickListener(e -> {
                UI.getCurrent().navigate(UIConstants.ROUTE_PAPERS + "/" + entry.getPaperId());
            });
            
            layout.add(icon, fileName, status, viewButton);
            layout.setFlexGrow(1, status);
        } else {
            layout.add(icon, fileName, status);
            layout.setFlexGrow(1, status);
        }
        
        return layout;
    }
    
    private void setProcessingMode(boolean processing) {
        projectSelect.setEnabled(!processing);
        authorsField.setEnabled(!processing);
        publicCheckbox.setEnabled(!processing);
        upload.setEnabled(!processing);
        processButton.setEnabled(!processing);
    }
    
    /**
     * Get all projects for a user, wrapper around the pageable method.
     * 
     * @param user The user whose projects to retrieve
     * @return List of projects
     */
    private List<Project> getProjectsByUser(User user) {
        return projectService.getProjectsByUser(user, PageRequest.ofSize(1000)).getContent();
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

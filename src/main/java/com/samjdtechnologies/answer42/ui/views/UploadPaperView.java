package com.samjdtechnologies.answer42.ui.views;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.multipart.MultipartFile;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.ui.components.AuthorContact;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.views.helpers.PapersViewHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * View for uploading a new paper.
 */
@Route(value = UIConstants.ROUTE_UPLOAD_PAPER, layout = MainLayout.class)
@PageTitle("Answer42 - Upload Paper")
@Secured("ROLE_USER")
public class UploadPaperView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(UploadPaperView.class);
    
    private final PaperService paperService;
    private final ProjectService projectService;
    
    private User currentUser;
    private boolean fileUploaded = false;
    private MemoryBuffer buffer = new MemoryBuffer();
    private TextField titleField;
    private TextArea abstractField;
    private DatePicker publicationDateField;
    private TextField journalField;
    private TextField doiField;
    private Select<Project> projectSelect;
    private Checkbox publicCheckbox;
    private Button uploadButton;
    private List<AuthorContact> authorEntries = new ArrayList<>();
    
    private VerticalLayout authorsContainer;

    /**
     * Constructs the upload paper view with necessary service dependencies.
     * Initializes the view for uploading and cataloging new research papers.
     * 
     * @param paperService the service for paper-related operations including upload and metadata management
     * @param projectService the service for project-related operations and paper-project associations
     */
    public UploadPaperView(PaperService paperService, ProjectService projectService) {
        this.paperService = paperService;
        this.projectService = projectService;
        
        addClassName(UIConstants.CSS_UPLOAD_PAPER_VIEW);
        getStyle().setHeight("auto");

        LoggingUtil.debug(LOG, "UploadPaperView", "UploadPaperView initialized");
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing view components");
        
        // Configure the view
        removeAll();
        
        // Create the header
        H2 title = new H2("Upload Paper");
        Paragraph subtitle = new Paragraph("Upload a scientific paper to analyze and manage in Answer42");
        
        // Action buttons - created before steps so we can position them
        Component actionButtons = createActionButtons();
        
        // Add components to the view in the desired order
        add(
            title, 
            subtitle, 
            createFileUploadStep(),
            actionButtons,
            createDetailsStep()
        );
    }
    
    private Component createFileUploadStep() {
        H3 stepTitle = new H3("Step 1: Upload PDF File");
        stepTitle.addClassName(UIConstants.CSS_STEP_TITLE);
        
        // Create upload component
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", ".pdf");
        upload.setWidthFull();
        upload.setMaxFiles(1);
        upload.setDropAllowed(true);
        upload.setUploadButton(new Button("Upload a file"));
        upload.setDropLabelIcon(new Icon(VaadinIcon.UPLOAD));
        
        // Use Span instead of Label (deprecated)
        Span dropLabel = new Span("or drag and drop");
        dropLabel.addClassName(UIConstants.CSS_DROP_LABEL);
        upload.setDropLabel(dropLabel);
        upload.addClassName(UIConstants.CSS_UPLOAD_CONTAINER);
        
        // Add info about max file size
        Paragraph fileInfo = new Paragraph("PDF up to 20MB");
        fileInfo.addClassName(UIConstants.CSS_FILE_INFO);
        
        // Track upload success/failure
        upload.addSucceededListener(e -> {
            fileUploaded = true;
            LoggingUtil.debug(LOG, "createFileUploadStep", "File uploaded successfully: %s", e.getFileName());
            validateForm();
        });
        
        upload.addFailedListener(e -> {
            fileUploaded = false;
            LoggingUtil.error(LOG, "createFileUploadStep", "File upload failed: " + e.getReason());
            Notification.show("Upload failed: " + e.getReason(), 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            validateForm();
        });
        
        upload.addFileRejectedListener(e -> {
            fileUploaded = false;
            LoggingUtil.error(LOG, "createFileUploadStep", "File rejected: " + e.getErrorMessage());
            Notification.show("File rejected: " + e.getErrorMessage(), 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            validateForm();
        });
        
        // Create step container
        VerticalLayout stepContainer = new VerticalLayout(stepTitle, upload, fileInfo);
        stepContainer.addClassName(UIConstants.CSS_STEP_CONTAINER);
        stepContainer.setPadding(true);
        stepContainer.setSpacing(true);
        
        return stepContainer;
    }
    
    private Component createDetailsStep() {
        H3 stepTitle = new H3("Step 2: Enter Paper Details");
        stepTitle.addClassName(UIConstants.CSS_STEP_TITLE);
        
        // Create form fields
        titleField = new TextField("Title");
        titleField.setRequired(true);
        titleField.setWidthFull();
        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        titleField.addValueChangeListener(e -> validateForm());
        
        // Authors section
        Span authorsLabel = new Span("Authors");
        authorsLabel.addClassName(UIConstants.CSS_AUTHOR_LABEL);
        
        // Container for author entries
        authorsContainer = new VerticalLayout();
        authorsContainer.setPadding(false);
        authorsContainer.setSpacing(true);
        authorsContainer.addClassName(UIConstants.CSS_AUTHORS_CONTAINER);
        
        // Add first author entry
        addAuthorEntry();
        
        // Add Author button
        Button addAuthorButton = new Button("Add Author", new Icon(VaadinIcon.PLUS));
        addAuthorButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addAuthorButton.addClickListener(e -> addAuthorEntry());
        
        // Abstract
        abstractField = new TextArea("Abstract");
        abstractField.setWidthFull();
        abstractField.setHeight("150px");
        
        // Create a layout for publication details with date and journal
        HorizontalLayout publicationDetails = new HorizontalLayout();
        publicationDetails.setWidthFull();
        publicationDetails.setSpacing(true);
        
        publicationDateField = new DatePicker("Publication Date");
        publicationDateField.setWidthFull();
        
        journalField = new TextField("Journal");
        journalField.setWidthFull();
        
        publicationDetails.add(publicationDateField, journalField);
        publicationDetails.setFlexGrow(1, publicationDateField, journalField);
        
        // DOI
        doiField = new TextField("DOI");
        doiField.setWidthFull();
        doiField.setPlaceholder("e.g. 10.1000/xyz123");
        
        // Project selection
        projectSelect = new Select<>();
        projectSelect.setLabel("Project");
        projectSelect.setWidthFull();
        
        // Set a safer item label generator that handles null
        projectSelect.setItemLabelGenerator(project -> project != null ? project.getName() : "");
        
        // Load user projects - this must come before setting empty selection
        List<Project> projects = new ArrayList<>();
        if (currentUser != null) {
            projects = getProjectsByUser(currentUser);
        }
        
        // First set the items
        projectSelect.setItems(projects);
        
        // Set placeholder text instead of empty selection
        projectSelect.setPlaceholder("No Project");
        
        // Public checkbox
        publicCheckbox = new Checkbox("Make paper public");
        publicCheckbox.setWidthFull();
        Paragraph publicDescription = new Paragraph("Allow other users to view this paper");
        publicDescription.addClassName(UIConstants.CSS_HELP_TEXT_SECONDARY);
        
        // Create details container
        VerticalLayout detailsContainer = new VerticalLayout(
            titleField, 
            authorsLabel,
            authorsContainer,
            addAuthorButton,
            abstractField,
            publicationDetails,
            doiField,
            projectSelect,
            publicCheckbox,
            publicDescription
        );
        
        detailsContainer.setPadding(false);
        detailsContainer.setSpacing(true);
        detailsContainer.addClassName(UIConstants.CSS_PAPER_DETAILS_CONTAINER);
        
        // Create step container
        VerticalLayout stepContainer = new VerticalLayout(stepTitle, detailsContainer);
        stepContainer.addClassName(UIConstants.CSS_STEP_CONTAINER);
        stepContainer.setPadding(true);
        stepContainer.setSpacing(true);
        
        return stepContainer;
    }
    
    private void addAuthorEntry() {
        AuthorContact authorEntry = new AuthorContact(authorsContainer, this::validateForm);
        authorEntries.add(authorEntry);
        authorsContainer.add(authorEntry);
    }
    
    private Component createActionButtons() {
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> UI.getCurrent().navigate(UIConstants.ROUTE_PAPERS));
        
        uploadButton = new Button("Upload Paper");
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        uploadButton.setEnabled(false);
        uploadButton.addClickListener(e -> uploadPaper());
        
        HorizontalLayout actionButtons = new HorizontalLayout(cancelButton, uploadButton);
        actionButtons.addClassName(UIConstants.CSS_ACTION_BUTTONS);
        actionButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actionButtons.setWidthFull();
        
        return actionButtons;
    }
    
    private void validateForm() {
        boolean titleValid = !titleField.isEmpty();
        boolean authorsValid = validateAuthors();
        
        LoggingUtil.debug(LOG, "validateForm", "Form validation: " +
            "titleValid=%s, authorsValid=%s, fileUploaded=%s", 
            titleValid, authorsValid, fileUploaded);
        
        uploadButton.setEnabled(titleValid && authorsValid && fileUploaded);
    }
    
    private boolean validateAuthors() {
        // At least one author with name should be present
        return authorEntries.stream().anyMatch(entry -> !entry.getNameField().isEmpty());
    }
    
    private void uploadPaper() {
        try {
            LoggingUtil.debug(LOG, "uploadPaper", "Starting paper upload process");
            
            // Get the list of authors from the entries
            List<String> authorsList = new ArrayList<>();
            
            for (AuthorContact entry : authorEntries) {
                if (!entry.getNameField().isEmpty()) {
                    String authorName = entry.getNameField().getValue();
                    String affiliation = entry.getAffiliationField().getValue();
                    String email = entry.getEmailField().getValue();
                    
                    // Format the author string with available information
                    StringBuilder authorBuilder = new StringBuilder(authorName);
                    
                    if (!affiliation.isEmpty() || !email.isEmpty()) {
                        authorBuilder.append(" (");
                        
                        if (!affiliation.isEmpty()) {
                            authorBuilder.append(affiliation);
                            
                            if (!email.isEmpty()) {
                                authorBuilder.append(", ");
                            }
                        }
                        
                        if (!email.isEmpty()) {
                            authorBuilder.append(email);
                        }
                        
                        authorBuilder.append(")");
                    }
                    
                    authorsList.add(authorBuilder.toString());
                }
            }
            
            // Convert authors list to array
            String[] authorsArray = authorsList.toArray(new String[0]);
            
            LoggingUtil.debug(LOG, "uploadPaper", "Preparing to upload file with %d authors", authorsArray.length);
            
            // Create MultipartFile from buffer
            MultipartFile file = PapersViewHelper.createMultipartFileFromBuffer(buffer);
            
            // Upload the paper
            Paper paper = paperService.uploadPaper(
                file,
                titleField.getValue(),
                authorsArray,
                currentUser
            );
            
            LoggingUtil.info(LOG, "uploadPaper", "Paper uploaded with ID: %s, updating metadata", paper.getId());
            
            // Update additional fields
            paper.setPaperAbstract(abstractField.getValue());
            paper.setJournal(journalField.getValue());
            paper.setPublicationDate(publicationDateField.getValue());
            paper.setDoi(doiField.getValue());
            paper.setIsPublic(publicCheckbox.getValue());
            
            // Set project if selected
            Project selectedProject = projectSelect.getValue();
            if (selectedProject != null) {
                // Update project-paper relationship
                updatePaperProject(paper, selectedProject);
            }
            
            // Update the paper with additional metadata
            paperService.updatePaperMetadata(
                paper.getId(),
                paper.getTitle(),
                authorsArray,
                paper.getPaperAbstract(),
                paper.getJournal(),
                paper.getYear(),
                paper.getDoi()
            );
            
            LoggingUtil.info(LOG, "uploadPaper", "Paper uploaded and metadata updated successfully");
            
            // Navigate back to papers view and show success message
            UI.getCurrent().navigate(UIConstants.ROUTE_PAPERS);
            Notification.show("Paper uploaded successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
        } catch (Exception ex) {
            LoggingUtil.error(LOG, "uploadPaper", "Error uploading paper: " + ex.getMessage(), ex);
            Notification.show("Upload failed: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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
    
    /**
     * Update the paper-project relationship.
     * 
     * @param paper The paper to update
     * @param project The project to associate with the paper
     */
    private void updatePaperProject(Paper paper, Project project) {
        LoggingUtil.debug(LOG, "updatePaperProject", "Associating paper %s with project %s", 
            paper.getId(), project.getId());
        
        try {
            projectService.addPaperToProject(project.getId(), paper);
            LoggingUtil.info(LOG, "updatePaperProject", "Paper-project association completed successfully");
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updatePaperProject", "Failed to associate paper with project: " + e.getMessage(), e);
            Notification.show("Failed to associate paper with project: " + e.getMessage(), 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
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

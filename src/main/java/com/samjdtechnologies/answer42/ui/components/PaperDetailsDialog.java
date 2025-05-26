package com.samjdtechnologies.answer42.ui.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;
import com.samjdtechnologies.answer42.repository.DiscoveredPaperRepository;
import com.samjdtechnologies.answer42.ui.helpers.components.PaperDetailsDialogComponentHelper;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Dialog component for displaying comprehensive details of a discovered paper.
 * Uses PaperDetailsDialogComponentHelper for creating rich UI sections.
 */
public class PaperDetailsDialog extends Dialog {
    
    private static final Logger LOG = LoggerFactory.getLogger(PaperDetailsDialog.class);
    
    private final DiscoveredPaper paper;
    private final DiscoveredPaperRepository discoveredPaperRepository;
    
    // Dialog dimensions
    private static final String DIALOG_WIDTH = "900px";
    private static final String DIALOG_MAX_WIDTH = "95vw";
    private static final String DIALOG_HEIGHT = "700px";
    private static final String DIALOG_MAX_HEIGHT = "90vh";
    
    /**
     * Creates a new paper details dialog.
     * 
     * @param paper the discovered paper to display
     * @param discoveredPaperRepository repository for updating paper access timestamps
     */
    public PaperDetailsDialog(DiscoveredPaper paper, DiscoveredPaperRepository discoveredPaperRepository) {
        this.paper = paper;
        this.discoveredPaperRepository = discoveredPaperRepository;
        
        initializeDialog();
        createDialogContent();
        logDialogOpening();
        updateLastAccessed();
        
        LoggingUtil.debug(LOG, "PaperDetailsDialog", "Created dialog for paper: %s", paper.getTitle());
    }
    
    /**
     * Convenience constructor without repository (read-only mode).
     */
    public PaperDetailsDialog(DiscoveredPaper paper) {
        this(paper, null);
    }
    
    /**
     * Initializes the dialog configuration.
     */
    private void initializeDialog() {
        // Dialog configuration
        setHeaderTitle("Paper Details");
        setWidth(DIALOG_WIDTH);
        setMaxWidth(DIALOG_MAX_WIDTH);
        setHeight(DIALOG_HEIGHT);
        setMaxHeight(DIALOG_MAX_HEIGHT);
        setResizable(true);
        setDraggable(true);
        setModal(true);
        
        // Add CSS class for styling
        addClassName(UIConstants.CSS_PAPER_DIALOG);
        
        // Close on escape key
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false); // Prevent accidental closes
        
        // Add header actions
        createHeaderActions();
    }
    
    /**
     * Creates header action buttons.
     */
    private void createHeaderActions() {
        // External link button
        if (paper.getAccessUrl() != null || paper.getDoi() != null) {
            Button openExternalButton = new Button(VaadinIcon.EXTERNAL_LINK.create());
            openExternalButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
            openExternalButton.getElement().setAttribute("title", "Open external link");
            
            String url = paper.getAccessUrl() != null ? paper.getAccessUrl() : 
                         "https://doi.org/" + paper.getDoi();
            
            openExternalButton.addClickListener(e -> {
                getUI().ifPresent(ui -> ui.getPage().open(url, "_blank"));
                LoggingUtil.info(LOG, "openExternalLink", "User opened external link for paper: %s", paper.getId());
            });
            
            getHeader().add(openExternalButton);
        }
        
        // Bookmark/save button (placeholder for future functionality)
        Button bookmarkButton = new Button(VaadinIcon.BOOKMARK.create());
        bookmarkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        bookmarkButton.getElement().setAttribute("title", "Bookmark paper");
        bookmarkButton.addClickListener(e -> {
            // Future: Add bookmarking functionality
            LoggingUtil.info(LOG, "bookmarkPaper", "User bookmarked paper: %s", paper.getId());
        });
        getHeader().add(bookmarkButton);
    }
    
    /**
     * Creates the main dialog content using helper methods.
     */
    private void createDialogContent() {
        try {
            VerticalLayout content = new VerticalLayout();
            content.setSpacing(true);
            content.setPadding(false);
            content.setWidthFull();
            
            // Create all sections using helper methods
            content.add(createPaperHeaderSection());
            content.add(createMetricsSection());
            content.add(createAbstractSection());
            content.add(createTopicsSection());
            content.add(createDiscoverySection());
            content.add(createAccessLinksSection());
            
            // Wrap in scrollable container
            Div scrollableContent = new Div(content);
            scrollableContent.getStyle()
                .set("overflow", "auto")
                .setMaxHeight("100%");
            
            // Add to dialog
            add(scrollableContent);
            
            // Footer with action buttons
            createFooter();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createDialogContent", "Failed to create dialog content", e);
            
            // Fallback content
            VerticalLayout errorContent = new VerticalLayout();
            errorContent.add(new Div("Error loading paper details: " + e.getMessage()));
            add(errorContent);
        }
    }
    
    /**
     * Creates the paper header section.
     */
    private Component createPaperHeaderSection() {
        try {
            return PaperDetailsDialogComponentHelper.createPaperHeader(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createPaperHeaderSection", "Failed to create header section", e);
            return new Div("Error loading paper header");
        }
    }
    
    /**
     * Creates the metrics section.
     */
    private Component createMetricsSection() {
        try {
            return PaperDetailsDialogComponentHelper.createMetricsSection(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createMetricsSection", "Failed to create metrics section", e);
            return new Div(); // Empty on error
        }
    }
    
    /**
     * Creates the abstract section.
     */
    private Component createAbstractSection() {
        try {
            return PaperDetailsDialogComponentHelper.createAbstractSection(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createAbstractSection", "Failed to create abstract section", e);
            return new Div(); // Empty on error
        }
    }
    
    /**
     * Creates the topics section.
     */
    private Component createTopicsSection() {
        try {
            return PaperDetailsDialogComponentHelper.createTopicsSection(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createTopicsSection", "Failed to create topics section", e);
            return new Div(); // Empty on error
        }
    }
    
    /**
     * Creates the discovery information section.
     */
    private Component createDiscoverySection() {
        try {
            return PaperDetailsDialogComponentHelper.createDiscoverySection(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createDiscoverySection", "Failed to create discovery section", e);
            return new Div(); // Empty on error
        }
    }
    
    /**
     * Creates the access links section.
     */
    private Component createAccessLinksSection() {
        try {
            return PaperDetailsDialogComponentHelper.createAccessLinksSection(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createAccessLinksSection", "Failed to create access links section", e);
            return new Div(); // Empty on error
        }
    }
    
    /**
     * Creates the footer with action buttons.
     */
    private void createFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setSpacing(true);
        footer.setPadding(true);
        footer.setWidthFull();
        footer.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);
        
        // Feedback button (placeholder for future functionality)
        Button feedbackButton = new Button("Provide Feedback", VaadinIcon.THUMBS_UP.create());
        feedbackButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        feedbackButton.addClickListener(e -> showFeedbackDialog());
        
        // Close button
        Button closeButton = new Button("Close", VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClickListener(e -> close());
        
        footer.add(feedbackButton, closeButton);
        getFooter().add(footer);
    }
    
    /**
     * Shows feedback dialog (placeholder implementation).
     */
    private void showFeedbackDialog() {
        Dialog feedbackDialog = new Dialog();
        feedbackDialog.setHeaderTitle("Provide Feedback");
        
        VerticalLayout content = new VerticalLayout();
        content.add(new Div("How relevant is this paper to your research?"));
        content.add(new Div("Paper: " + paper.getTitle()));
        
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.add(
            new Button("Very Relevant", e -> {
                LoggingUtil.info(LOG, "feedback", "User rated paper as very relevant: %s", paper.getId());
                feedbackDialog.close();
            }),
            new Button("Somewhat Relevant", e -> {
                LoggingUtil.info(LOG, "feedback", "User rated paper as somewhat relevant: %s", paper.getId());
                feedbackDialog.close();
            }),
            new Button("Not Relevant", e -> {
                LoggingUtil.info(LOG, "feedback", "User rated paper as not relevant: %s", paper.getId());
                feedbackDialog.close();
            })
        );
        
        content.add(buttons);
        
        Button cancelButton = new Button("Cancel", e -> feedbackDialog.close());
        content.add(cancelButton);
        
        feedbackDialog.add(content);
        feedbackDialog.open();
        
        LoggingUtil.debug(LOG, "showFeedbackDialog", "Opened feedback dialog for paper: %s", paper.getId());
    }
    
    /**
     * Logs the dialog opening event.
     */
    private void logDialogOpening() {
        try {
            PaperDetailsDialogComponentHelper.logPaperDetailsView(paper);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "logDialogOpening", "Failed to log dialog opening", e);
        }
    }
    
    /**
     * Updates the last accessed timestamp for the paper.
     */
    private void updateLastAccessed() {
        if (discoveredPaperRepository != null) {
            try {
                PaperDetailsDialogComponentHelper.updateLastAccessed(paper);
                discoveredPaperRepository.save(paper);
                LoggingUtil.debug(LOG, "updateLastAccessed", "Updated last accessed for paper: %s", paper.getId());
            } catch (Exception e) {
                LoggingUtil.error(LOG, "updateLastAccessed", "Failed to update last accessed timestamp", e);
            }
        }
    }
    
    /**
     * Gets the paper being displayed.
     */
    public DiscoveredPaper getPaper() {
        return paper;
    }
    
    /**
     * Static factory method for creating a paper details dialog.
     */
    public static PaperDetailsDialog createDialog(DiscoveredPaper paper, DiscoveredPaperRepository repository) {
        return new PaperDetailsDialog(paper, repository);
    }
    
    /**
     * Static factory method for creating a read-only paper details dialog.
     */
    public static PaperDetailsDialog createReadOnlyDialog(DiscoveredPaper paper) {
        return new PaperDetailsDialog(paper, null);
    }
    
    @Override
    public void open() {
        super.open();
        LoggingUtil.debug(LOG, "open", "Opened paper details dialog for: %s", paper.getTitle());
    }
    
    @Override
    public void close() {
        super.close();
        LoggingUtil.debug(LOG, "close", "Closed paper details dialog for: %s", paper.getTitle());
    }
}

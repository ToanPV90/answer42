package com.samjdtechnologies.answer42.ui.views;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.repository.DiscoveredPaperRepository;
import com.samjdtechnologies.answer42.service.DiscoveryFeedbackService;
import com.samjdtechnologies.answer42.service.PaperBookmarkService;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.discovery.DiscoveryCoordinator;
import com.samjdtechnologies.answer42.ui.components.RelatedPapersSection;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * View for displaying related papers discovered through the multi-source discovery system.
 * Accessed via route "related-papers/{paperId}" to show discoveries for a specific paper.
 */
@Route(value = UIConstants.ROUTE_RELATED_PAPERS, layout = MainLayout.class)
@PageTitle("Answer42 - Related Papers")
@Secured("ROLE_USER")
public class RelatedPapersView extends Div implements HasUrlParameter<UUID>, BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(RelatedPapersView.class);
    
    private final PaperService paperService;
    private final DiscoveredPaperRepository discoveredPaperRepository;
    private final DiscoveryCoordinator discoveryCoordinator;
    private final PaperBookmarkService paperBookmarkService;
    private final DiscoveryFeedbackService discoveryFeedbackService;
    
    private User currentUser;
    private Paper sourcePaper;
    private UUID paperId;

    /**
     * Constructs the Related Papers view with necessary service dependencies.
     */
    public RelatedPapersView(PaperService paperService, 
                           DiscoveredPaperRepository discoveredPaperRepository,
                           DiscoveryCoordinator discoveryCoordinator,
                           PaperBookmarkService paperBookmarkService,
                           DiscoveryFeedbackService discoveryFeedbackService) {
        this.paperService = paperService;
        this.discoveredPaperRepository = discoveredPaperRepository;
        this.discoveryCoordinator = discoveryCoordinator;
        this.paperBookmarkService = paperBookmarkService;
        this.discoveryFeedbackService = discoveryFeedbackService;
        
        addClassName("related-papers-view");
        getStyle().setHeight("100vh");
        getStyle().setPadding("var(--lumo-space-m)");
        
        LoggingUtil.debug(LOG, "RelatedPapersView", "RelatedPapersView initialized");
    }

    @Override
    public void setParameter(BeforeEvent event, UUID parameter) {
        this.paperId = parameter;
        LoggingUtil.debug(LOG, "setParameter", "Paper ID parameter set: %s", parameter);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting user from session");
        
        // Get the current user from the session
        currentUser = MainLayout.getCurrentUser();
        
        if (currentUser == null) {
            LoggingUtil.warn(LOG, "beforeEnter", "No user found in session, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
            return;
        }
        
        if (paperId == null) {
            LoggingUtil.warn(LOG, "beforeEnter", "No paper ID provided, redirecting to papers view");
            event.forwardTo(UIConstants.ROUTE_PAPERS);
            return;
        }
        
        // Load the source paper
        loadSourcePaper();
        
        if (sourcePaper == null) {
            LoggingUtil.warn(LOG, "beforeEnter", "Paper not found or access denied for ID: %s", paperId);
            event.forwardTo(UIConstants.ROUTE_PAPERS);
            return;
        }
        
        // Initialize the view
        initializeView();
    }
    
    private void loadSourcePaper() {
        try {
            Optional<Paper> paperOpt = paperService.getPaperById(paperId);
            if (paperOpt.isPresent()) {
                Paper paper = paperOpt.get();
                
                // Verify user has access to this paper
                if (paper.getUser().getId().equals(currentUser.getId())) {
                    sourcePaper = paper;
                    LoggingUtil.debug(LOG, "loadSourcePaper", "Loaded paper: %s", paper.getTitle());
                } else {
                    LoggingUtil.warn(LOG, "loadSourcePaper", "Access denied for paper ID: %s", paperId);
                }
            } else {
                LoggingUtil.warn(LOG, "loadSourcePaper", "Paper not found for ID: %s", paperId);
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "loadSourcePaper", "Failed to load paper", e);
        }
    }
    
    private void initializeView() {
        removeAll();
        
        // Create header section
        add(createHeaderSection());
        
        // Create paper info section
        add(createPaperInfoSection());
        
        // Create related papers section with all required dependencies
        RelatedPapersSection relatedPapersSection = new RelatedPapersSection(
            sourcePaper, 
            discoveredPaperRepository, 
            discoveryCoordinator,
            paperBookmarkService,
            discoveryFeedbackService);
        add(relatedPapersSection);
        
        LoggingUtil.debug(LOG, "initializeView", "View initialized for paper: %s", sourcePaper.getTitle());
    }
    
    private Div createHeaderSection() {
        Div headerSection = new Div();
        headerSection.addClassName("related-papers-header");
        
        // Back button and navigation
        HorizontalLayout navigation = new HorizontalLayout();
        navigation.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navigation.setWidthFull();
        
        Button backButton = new Button("Back to Papers", new Icon(VaadinIcon.ARROW_LEFT));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.addClickListener(e -> UI.getCurrent().navigate(UIConstants.ROUTE_PAPERS));
        
        H1 title = new H1("Related Papers Discovery");
        title.getStyle().setMargin("0");
        title.getStyle().setColor("var(--lumo-primary-text-color)");
        
        navigation.add(backButton);
        navigation.setFlexGrow(1, backButton);
        
        VerticalLayout headerContent = new VerticalLayout(navigation, title);
        headerContent.setSpacing(true);
        headerContent.setPadding(false);
        
        headerSection.add(headerContent);
        return headerSection;
    }
    
    private Div createPaperInfoSection() {
        Div paperInfoSection = new Div();
        paperInfoSection.addClassName("paper-info-section");
        paperInfoSection.getStyle()
            .setBorder("1px solid var(--lumo-contrast-20pct)")
            .setBorderRadius("var(--lumo-border-radius-m)")
            .setPadding("var(--lumo-space-m)")
            .setMarginBottom("var(--lumo-space-l)")
            .setBackgroundColor("var(--lumo-contrast-5pct)");
        
        // Paper title
        H2 paperTitle = new H2(sourcePaper.getTitle());
        paperTitle.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        paperTitle.getStyle().setColor("var(--lumo-header-text-color)");
        
        // Paper metadata
        HorizontalLayout metadata = new HorizontalLayout();
        metadata.setSpacing(true);
        metadata.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        
        // Authors
        if (sourcePaper.getAuthors() != null && !sourcePaper.getAuthors().isEmpty()) {
            Icon authorsIcon = VaadinIcon.USERS.create();
            authorsIcon.getStyle().setColor("var(--lumo-secondary-text-color)");
            authorsIcon.setSize("16px");
            
            String authorsText = sourcePaper.getAuthors().size() > 3 
                ? String.join(", ", sourcePaper.getAuthors().subList(0, 3)) + " et al."
                : String.join(", ", sourcePaper.getAuthors());
            
            Span authorsSpan = new Span(authorsText);
            authorsSpan.getStyle().setColor("var(--lumo-secondary-text-color)");
            
            HorizontalLayout authorsLayout = new HorizontalLayout(authorsIcon, authorsSpan);
            authorsLayout.setSpacing(false);
            authorsLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            
            metadata.add(authorsLayout);
        }
        
        // Journal
        if (sourcePaper.getJournal() != null && !sourcePaper.getJournal().isEmpty()) {
            Icon journalIcon = VaadinIcon.BOOK.create();
            journalIcon.getStyle().setColor("var(--lumo-secondary-text-color)");
            journalIcon.setSize("16px");
            
            Span journalSpan = new Span(sourcePaper.getJournal());
            journalSpan.getStyle().setColor("var(--lumo-secondary-text-color)");
            
            HorizontalLayout journalLayout = new HorizontalLayout(journalIcon, journalSpan);
            journalLayout.setSpacing(false);
            journalLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            
            metadata.add(journalLayout);
        }
        
        // Year
        if (sourcePaper.getYear() != null) {
            Icon yearIcon = VaadinIcon.CALENDAR.create();
            yearIcon.getStyle().setColor("var(--lumo-secondary-text-color)");
            yearIcon.setSize("16px");
            
            Span yearSpan = new Span(sourcePaper.getYear().toString());
            yearSpan.getStyle().setColor("var(--lumo-secondary-text-color)");
            
            HorizontalLayout yearLayout = new HorizontalLayout(yearIcon, yearSpan);
            yearLayout.setSpacing(false);
            yearLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            
            metadata.add(yearLayout);
        }
        
        // Status badge
        Span statusBadge = new Span(sourcePaper.getStatus());
        statusBadge.getElement().getThemeList().add("badge");
        statusBadge.getElement().getThemeList().add(getStatusTheme(sourcePaper.getStatus()));
        
        metadata.add(statusBadge);
        
        // Abstract (if available and not too long)
        VerticalLayout content = new VerticalLayout(paperTitle, metadata);
        content.setSpacing(true);
        content.setPadding(false);
        
        if (sourcePaper.getPaperAbstract() != null && !sourcePaper.getPaperAbstract().isEmpty()) {
            String abstractText = sourcePaper.getPaperAbstract();
            if (abstractText.length() > 300) {
                abstractText = abstractText.substring(0, 300) + "...";
            }
            
            Paragraph abstractPara = new Paragraph(abstractText);
            abstractPara.getStyle()
                .setColor("var(--lumo-secondary-text-color)")
                .setFontSize("var(--lumo-font-size-s)")
                .setMarginTop("var(--lumo-space-s)");
            
            content.add(abstractPara);
        }
        
        paperInfoSection.add(content);
        return paperInfoSection;
    }
    
    private String getStatusTheme(String status) {
        if (status == null) {
            return "normal";
        }
        
        switch (status.toUpperCase()) {
            case "PROCESSED":
                return "success";
            case "PROCESSING":
                return "primary";
            case "PENDING":
                return "normal";
            case "FAILED":
                return "error";
            default:
                return "normal";
        }
    }
    
    /**
     * Refreshes the view data.
     */
    public void refresh() {
        if (sourcePaper != null) {
            loadSourcePaper();
            if (sourcePaper != null) {
                initializeView();
            } else {
                Notification.show("Paper no longer available", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                UI.getCurrent().navigate(UIConstants.ROUTE_PAPERS);
            }
        }
    }
}

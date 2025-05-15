package com.samjdtechnologies.answer42.ui.views;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * Dashboard view that displays an overview of the user's research papers and projects.
 * This is the main landing page after authentication.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Answer42 - Dashboard")
@PermitAll
public class DashboardView extends Div implements AfterNavigationObserver, BeforeEnterObserver {

    private final PaperService paperService;
    private final UserService userService;

    private User currentUser;
    

    public DashboardView(PaperService paperService, UserService userService) {
        this.paperService = paperService;
        this.userService = userService;

        addClassName(UIConstants.CSS_DASHBOARD_VIEW);
        setSizeFull();
    }
    
    private void initializeView() {
        // Create welcome section
        Component welcomeSection = createWelcomeSection();
        
        // Add all components to the view
        add(welcomeSection);
    }
    
    private Component createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("Welcome, " + (currentUser != null ? currentUser.getUsername() : "User") + "!");
        welcomeTitle.addClassName("welcome-title");
        
        Paragraph welcomeSubtitle = new Paragraph("Manage your research papers and projects from this dashboard");
        welcomeSubtitle.addClassName("welcome-subtitle");
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
    }
    
    private Component createStatsCards() {
        Div statsContainer = new Div();
        statsContainer.addClassName(UIConstants.CSS_STATS_CONTAINER);
        
        // Paper stats card
        Component papersCard = createStatCard(
            "Total Papers", 
            "10", 
            VaadinIcon.FILE_TEXT, 
            "papers", 
            "View all papers",
            UIConstants.ROUTE_PAPERS
        );
        
        // Projects stats card
        Component projectsCard = createStatCard(
            "Projects", 
            "3", 
            VaadinIcon.FOLDER, 
            "projects", 
            "View all projects",
            UIConstants.ROUTE_PROJECTS
        );
        
        // AI Chat stats card
        Component aiChatCard = createStatCard(
            "AI Chat", 
            "Available", 
            VaadinIcon.COMMENTS, 
            "chat", 
            "Start chatting",
            UIConstants.ROUTE_AI_CHAT
        );
        
        statsContainer.add(papersCard, projectsCard, aiChatCard);
        return statsContainer;
    }
    
    private Component createStatCard(String title, String value, VaadinIcon iconType, 
                                    String iconClass, String linkText, String route) {
        Div card = new Div();
        card.addClassName(UIConstants.CSS_STAT_CARD);
        
        // Card header with title and icon
        Div header = new Div();
        header.addClassName("stat-header");
        
        Span titleSpan = new Span(title);
        titleSpan.addClassName("stat-title");
        
        Div iconContainer = new Div();
        iconContainer.addClassName("stat-icon");
        iconContainer.addClassName(iconClass);
        
        Icon icon = iconType.create();
        iconContainer.add(icon);
        
        header.add(titleSpan, iconContainer);
        
        // Card value
        Span valueSpan = new Span(value);
        valueSpan.addClassName("stat-value");
        
        // Card footer with link
        Div footer = new Div();
        footer.addClassName("stat-footer");
        
        Anchor link = new Anchor(route, linkText);
        link.addClassName("stat-link");
        
        Icon arrowIcon = VaadinIcon.ARROW_RIGHT.create();
        arrowIcon.setSize("14px");
        
        link.add(arrowIcon);
        footer.add(link);
        
        // Add all parts to the card
        card.add(header, valueSpan, footer);
        
        return card;
    }
    
    private Component createQuickActions() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_QUICK_ACTIONS);
        
        // Section header
        Div sectionHeader = new Div();
        sectionHeader.addClassName("section-header");
        
        H2 sectionTitle = new H2("Quick Actions");
        sectionTitle.addClassName("section-title");
        
        sectionHeader.add(sectionTitle);
        
        // Actions container
        Div actionsContainer = new Div();
        actionsContainer.addClassName("actions-container");
        
        // Upload Paper button
        Div uploadButton = createActionButton(
            VaadinIcon.UPLOAD, 
            "Upload Paper", 
            "primary",
            UIConstants.ROUTE_PAPERS
        );
        
        // Create Project button
        Div createProjectButton = createActionButton(
            VaadinIcon.PLUS, 
            "Create Project", 
            "success",
            UIConstants.ROUTE_PROJECTS
        );
        
        // Start Chat button
        Div startChatButton = createActionButton(
            VaadinIcon.COMMENT, 
            "Start New Chat", 
            "warning",
            UIConstants.ROUTE_AI_CHAT
        );
        
        actionsContainer.add(uploadButton, createProjectButton, startChatButton);
        
        // Add all components to the section
        section.add(sectionHeader, actionsContainer);
        
        return section;
    }
    
    private Div createActionButton(VaadinIcon iconType, String text, String variantClass, String route) {
        Div button = new Div();
        button.addClassName("action-button");
        if (variantClass != null && !variantClass.isEmpty()) {
            button.addClassName(variantClass);
        }
        
        Icon icon = iconType.create();
        icon.setSize("20px");
        
        Span label = new Span(text);
        
        button.add(icon, label);
        
        // Add click listener to navigate to the specified route
        button.getElement().addEventListener("click", e -> {
            button.getUI().ifPresent(ui -> ui.navigate(route));
        });
        
        return button;
    }
    
    private Component createRecentPapers() {
        Div section = new Div();
        
        // Section header
        Div sectionHeader = new Div();
        sectionHeader.addClassName("section-header");
        
        H2 sectionTitle = new H2("Recent Papers");
        sectionTitle.addClassName("section-title");
        
        Anchor viewAllLink = new Anchor(UIConstants.ROUTE_PAPERS, "View all");
        viewAllLink.addClassName("view-all");
        
        sectionHeader.add(sectionTitle, viewAllLink);
        
        // Papers list
        Div papersList = new Div();
        papersList.addClassName(UIConstants.CSS_PAPERS_LIST);
        
        // Example papers
        Component paper1 = createPaperCard(
            "Advances in CRISPR Gene Editing Therapies",
            "CRISPR-Cas gene editing technologies have revolutionized biomedical research and therapeutic development. This review summarizes the latest advances in CRISPR-based therapeutic approaches, highlighting improved delivery methods, enhanced specificity, and emerging clinical applications.",
            true
        );
        
        Component paper2 = createPaperCard(
            "Urban Planning for Climate Resilience",
            "This paper explores strategies for designing cities that can withstand and adapt to climate change impacts. It examines case studies from various urban centers implementing innovative approaches to water management, heat mitigation, and sustainable transportation.",
            true
        );
        
        Component paper3 = createPaperCard(
            "Neuroplasticity and Language Acquisition",
            "Investigating the neurological mechanisms underlying language learning across different age groups. The paper combines neuroimaging data with behavioral studies to map brain plasticity patterns during second language acquisition.",
            true
        );
        
        Component paper4 = createPaperCard(
            "Cybersecurity in Internet of Things Devices",
            "A comprehensive analysis of security vulnerabilities in IoT ecosystems and proposed mitigation strategies. The research highlights the need for standardized security protocols and improved firmware update mechanisms.",
            true
        );
        
        papersList.add(paper1, paper2, paper3, paper4);
        
        // Add all components to the section
        section.add(sectionHeader, papersList);
        
        return section;
    }
    
    private Component createPaperCard(String title, String content, boolean processed) {
        Div card = new Div();
        card.addClassName(UIConstants.CSS_PAPER_CARD);

        // Card header with title and status
        Div header = new Div();
        header.addClassName("paper-header");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("paper-title");

        Span statusSpan = new Span(processed ? "PROCESSED" : "PENDING");
        statusSpan.addClassName("paper-status");
        statusSpan.addClassName(processed ? "processed" : "pending");

        header.add(titleSpan, statusSpan);

        // Card content
        Paragraph contentParagraph = new Paragraph(content);
        contentParagraph.addClassName("paper-content");
        
        // Add metadata section
        Div metadata = new Div();
        metadata.addClassName("paper-metadata");
        
        // Author metadata
        Div authorMetadata = new Div();
        authorMetadata.addClassName("paper-metadata-item");
        
        Icon authorIcon = VaadinIcon.USER.create();
        authorIcon.addClassName("paper-metadata-icon");
        
        Span authorText = new Span("Various Authors");
        
        authorMetadata.add(authorIcon, authorText);
        
        // Date metadata
        Div dateMetadata = new Div();
        dateMetadata.addClassName("paper-metadata-item");
        
        Icon dateIcon = VaadinIcon.CALENDAR.create();
        dateIcon.addClassName("paper-metadata-icon");
        
        Span dateText = new Span("2022");
        
        dateMetadata.add(dateIcon, dateText);
        
        // Citations metadata
        Div citationsMetadata = new Div();
        citationsMetadata.addClassName("paper-metadata-item");
        
        Icon citationsIcon = VaadinIcon.CONNECT.create();
        citationsIcon.addClassName("paper-metadata-icon");
        
        Span citationsText = new Span("24 citations");
        
        citationsMetadata.add(citationsIcon, citationsText);
        
        metadata.add(authorMetadata, dateMetadata, citationsMetadata);

        // Card footer with action icons
        Div footer = new Div();
        footer.addClassName("paper-footer");

        Div viewAction = new Div(VaadinIcon.EYE.create());
        viewAction.addClassName("paper-action");

        Div editAction = new Div(VaadinIcon.EDIT.create());
        editAction.addClassName("paper-action");

        Div downloadAction = new Div(VaadinIcon.DOWNLOAD.create());
        downloadAction.addClassName("paper-action");

        footer.add(viewAction, editAction, downloadAction);

        // Add all parts to the card
        card.add(header, contentParagraph, metadata, footer);

        return card;
    }
    
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // This method is called after navigation to this view is complete.
        // Could be used to load data or perform other initialization tasks.
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Check if user is authenticated (not anonymous)
        if (authentication == null || "anonymousUser".equals(authentication.getPrincipal())) {
            // Redirect to login page
            event.forwardTo(UIConstants.ROUTE_LOGIN);
            return;
        }
        
        // Try to get the current user
        try {
            currentUser = userService.findByUsername(authentication.getName())
                    .orElse(null);
            
            // If no user found, redirect to login
            if (currentUser == null) {
                event.forwardTo(UIConstants.ROUTE_LOGIN);
                return;
            }
            
            // Initialize the view once we have a valid user
            removeAll(); // Clear any previous content
            initializeView();
            
        } catch (Exception e) {
            // Handle any errors by redirecting to login
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}

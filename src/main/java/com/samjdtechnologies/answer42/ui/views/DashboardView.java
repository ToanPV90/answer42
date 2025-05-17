package com.samjdtechnologies.answer42.ui.views;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
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

/**
 * Dashboard view that displays an overview of the user's research papers and projects.
 * This is the main landing page after authentication.
 */
@Route(value = UIConstants.ROUTE_MAIN, layout = MainLayout.class)
@PageTitle("Answer42 - Dashboard")
@Secured("ROLE_USER")
public class DashboardView extends Div implements AfterNavigationObserver, BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardView.class);
    
    private final UserService userService;
    private final PaperService paperService;
    private final ProjectService projectService;

    private User currentUser;
    

    public DashboardView(PaperService paperService, UserService userService, ProjectService projectService) {
        this.userService = userService;
        this.paperService = paperService;
        this.projectService = projectService;

        LoggingUtil.debug(LOG, "DashboardView", "DashboardView initialized");
        addClassName(UIConstants.CSS_DASHBOARD_VIEW);
        setSizeFull();
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing dashboard view components");
        // Create all dashboard sections
        removeAll();
        Component welcomeSection = createWelcomeSection();
        Component statsCards = createStatsCards();
        Component quickActions = createQuickActions();
        Component recentPapers = createRecentPapers();
        
        // Create a container with proper padding for consistent layout
        Div container = new Div();
        container.addClassName(UIConstants.CSS_CONTENT_CONTAINER);

        // Add content to the container
        container.add(welcomeSection, statsCards, quickActions, recentPapers);

        // Add all components to the view
        add(container);
        LoggingUtil.debug(LOG, "initializeView", "Dashboard view components initialized");
    }
    
    private Component createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("Welcome, " + (currentUser != null ? currentUser.getUsername() : "User") + "!");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);
        
        Paragraph welcomeSubtitle = new Paragraph("Manage your research papers and projects from this dashboard");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
    }
    
    private Component createStatsCards() {
        Div statsContainer = new Div();
        statsContainer.addClassName(UIConstants.CSS_STATS_CONTAINER);
        
        // Get actual paper count for the current user
        String paperCount = "0";
        if (currentUser != null) {
            long count = paperService.countPapersByUser(currentUser);
            paperCount = String.valueOf(count);
        }
        
        // Paper stats card with actual count
        Component papersCard = createStatCard(
            "Total Papers", 
            paperCount, 
            VaadinIcon.FILE_TEXT, 
            UIConstants.CSS_STAT_ICON_PAPERS, 
            "View all papers",
            UIConstants.ROUTE_PAPERS
        );
        
        // Projects stats card with actual count
        String projectCount = "0";
        if (currentUser != null) {
            long count = projectService.countProjectsByUser(currentUser);
            projectCount = String.valueOf(count);
        }
        
        Component projectsCard = createStatCard(
            "Projects", 
            projectCount, 
            VaadinIcon.FOLDER, 
            UIConstants.CSS_STAT_ICON_PROJECTS, 
            "View all projects",
            UIConstants.ROUTE_PROJECTS
        );
        
        // AI Chat stats card - showing different statuses based on user
        String aiChatStatus = "Available";
        if (currentUser == null) {
            aiChatStatus = "Login Required";
        } else if (currentUser.getRoles() != null && !currentUser.getRoles().contains("ROLE_PREMIUM")) {
            // If the user doesn't have the premium role, show a different status
            aiChatStatus = "Basic";
        }
        
        Component aiChatCard = createStatCard(
            "AI Chat", 
            aiChatStatus, 
            VaadinIcon.COMMENTS, 
            UIConstants.CSS_STAT_ICON_CHAT, 
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
        header.addClassName(UIConstants.CSS_STAT_HEADER);
        
        Span titleSpan = new Span(title);
        titleSpan.addClassName(UIConstants.CSS_STAT_TITLE);
        
        Div iconContainer = new Div();
        iconContainer.addClassName(UIConstants.CSS_STAT_ICON);
        // Map the iconClass string to the appropriate constant
        if ("papers".equals(iconClass)) {
            iconContainer.addClassName(UIConstants.CSS_STAT_ICON_PAPERS);
        } else if ("projects".equals(iconClass)) {
            iconContainer.addClassName(UIConstants.CSS_STAT_ICON_PROJECTS);
        } else if ("chat".equals(iconClass)) {
            iconContainer.addClassName(UIConstants.CSS_STAT_ICON_CHAT);
        } else {
            iconContainer.addClassName(iconClass); // Fallback to the passed class
        }
        
        Icon icon = iconType.create();
        iconContainer.add(icon);
        
        header.add(titleSpan, iconContainer);
        
        // Card value
        Span valueSpan = new Span(value);
        valueSpan.addClassName(UIConstants.CSS_STAT_VALUE);
        
        // Card footer with link
        Div footer = new Div();
        footer.addClassName(UIConstants.CSS_STAT_FOOTER);
        
        Anchor link = new Anchor(route, linkText);
        link.addClassName(UIConstants.CSS_STAT_LINK);
        
        Icon arrowIcon = VaadinIcon.ARROW_RIGHT.create();
        arrowIcon.addClassName(UIConstants.CSS_ARROW_ICON);
        
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
        sectionHeader.addClassName(UIConstants.CSS_SECTION_HEADER);
        
        H2 sectionTitle = new H2("Quick Actions");
        sectionTitle.addClassName(UIConstants.CSS_SECTION_TITLE);
        
        sectionHeader.add(sectionTitle);
        
        // Actions container
        Div actionsContainer = new Div();
        actionsContainer.addClassName(UIConstants.CSS_ACTIONS_CONTAINER);
        
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
        button.addClassName(UIConstants.CSS_ACTION_BTN);
        if (variantClass != null && !variantClass.isEmpty()) {
            button.addClassName(variantClass);
        }
        
        Icon icon = iconType.create();
        icon.addClassName(UIConstants.CSS_ACTION_BTN_ICON);
        
        Span label = new Span(text);
        
        button.add(icon, label);
        
        // Add click listener to navigate to the specified route
        button.getElement().addEventListener("click", e -> {
            button.getUI().ifPresent(ui -> ui.navigate(route));
        });
        
        return button;
    }
    
    private Component createRecentPapers() {
        LoggingUtil.debug(LOG, "createRecentPapers", "Creating recent papers section");
        Div section = new Div();
        
        // Section header
        Div sectionHeader = new Div();
        sectionHeader.addClassName(UIConstants.CSS_SECTION_HEADER);
        
        H2 sectionTitle = new H2("Recent Papers");
        sectionTitle.addClassName(UIConstants.CSS_SECTION_TITLE);
        
        Anchor viewAllLink = new Anchor(UIConstants.ROUTE_PAPERS, "View all");
        viewAllLink.addClassName(UIConstants.CSS_VIEW_ALL);
        
        sectionHeader.add(sectionTitle, viewAllLink);
        
        // Papers list
        Div papersList = new Div();
        papersList.addClassName(UIConstants.CSS_PAPERS_LIST);
        
        if (currentUser != null) {
            LoggingUtil.debug(LOG, "createRecentPapers", "Loading recent papers for user ID: %s", currentUser.getId());
            // Get real papers from service - limit to 4 for dashboard display
            List<Paper> recentPapers = paperService.getRecentPapersByUser(currentUser, 4);
            LoggingUtil.info(LOG, "createRecentPapers", "Retrieved %d recent papers for dashboard display", recentPapers.size());
            
            if (recentPapers.isEmpty()) {
                // If no papers, show a message
                Paragraph emptyMessage = new Paragraph("No papers yet. Upload your first paper!");
                emptyMessage.addClassName(UIConstants.CSS_EMPTY_STATE_MSG);
                papersList.add(emptyMessage);
            } else {
                // Add each paper to the list
                for (Paper paper : recentPapers) {
                    // Extract content for display - fall back to abstract if text content not available
                    String displayContent = paper.getTextContent();
                    if (displayContent == null || displayContent.isEmpty()) {
                        displayContent = paper.getAbstract();
                        if (displayContent == null || displayContent.isEmpty()) {
                            displayContent = "No content available for this paper.";
                        }
                    }
                    
                    // Truncate content if too long
                    if (displayContent.length() > 300) {
                        displayContent = displayContent.substring(0, 297) + "...";
                    }
                    
                    // Create and add the card
                    Component paperCard = createPaperCard(
                        paper.getTitle(),
                        displayContent,
                        "PROCESSED".equals(paper.getStatus())
                    );
                    
                    papersList.add(paperCard);
                }
            }
        } else {
            // If no user is logged in, show a login message
            Paragraph loginMessage = new Paragraph("Please log in to see your papers.");
            loginMessage.addClassName(UIConstants.CSS_EMPTY_STATE_MSG);
            papersList.add(loginMessage);
        }
        
        // Add all components to the section
        section.add(sectionHeader, papersList);
        
        return section;
    }
    
    private Component createPaperCard(String title, String content, boolean processed) {
        Div card = new Div();
        card.addClassName(UIConstants.CSS_PAPER_CARD);

        // Card header with title and status
        Div header = new Div();
        header.addClassName(UIConstants.CSS_PAPER_HEADER);

        Span titleSpan = new Span(title);
        titleSpan.addClassName(UIConstants.CSS_PAPER_TITLE);

        Span statusSpan = new Span(processed ? "PROCESSED" : "PENDING");
        statusSpan.addClassName(UIConstants.CSS_PAPER_STATUS);
        statusSpan.addClassName(processed ? "processed" : "pending");

        header.add(titleSpan, statusSpan);

        // Card content
        Paragraph contentParagraph = new Paragraph(content);
        contentParagraph.addClassName(UIConstants.CSS_PAPER_CONTENT);
        
        // Add metadata section
        Div metadata = new Div();
        metadata.addClassName(UIConstants.CSS_PAPER_METADATA);
        
        // Author metadata
        Div authorMetadata = new Div();
        authorMetadata.addClassName(UIConstants.CSS_PAPER_METADATA_ITEM);
        
        Icon authorIcon = VaadinIcon.USER.create();
        authorIcon.addClassName(UIConstants.CSS_PAPER_METADATA_ICON);
        
        Span authorText = new Span("Various Authors");
        
        authorMetadata.add(authorIcon, authorText);
        
        // Date metadata
        Div dateMetadata = new Div();
        dateMetadata.addClassName(UIConstants.CSS_PAPER_METADATA_ITEM);
        
        Icon dateIcon = VaadinIcon.CALENDAR.create();
        dateIcon.addClassName(UIConstants.CSS_PAPER_METADATA_ICON);
        
        Span dateText = new Span("2022");
        
        dateMetadata.add(dateIcon, dateText);
        
        // Citations metadata
        Div citationsMetadata = new Div();
        citationsMetadata.addClassName(UIConstants.CSS_PAPER_METADATA_ITEM);
        
        Icon citationsIcon = VaadinIcon.CONNECT.create();
        citationsIcon.addClassName(UIConstants.CSS_PAPER_METADATA_ICON);
        
        Span citationsText = new Span("24 citations");
        
        citationsMetadata.add(citationsIcon, citationsText);
        
        metadata.add(authorMetadata, dateMetadata, citationsMetadata);

        // Card footer with action icons
        Div footer = new Div();
        footer.addClassName(UIConstants.CSS_PAPER_FOOTER);

        Div viewAction = new Div(VaadinIcon.EYE.create());
        viewAction.addClassName(UIConstants.CSS_PAPER_ACTION);

        Div editAction = new Div(VaadinIcon.EDIT.create());
        editAction.addClassName(UIConstants.CSS_PAPER_ACTION);

        Div downloadAction = new Div(VaadinIcon.DOWNLOAD.create());
        downloadAction.addClassName(UIConstants.CSS_PAPER_ACTION);

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
                    LoggingUtil.debug(LOG, "beforeEnter", "Dashboard loading paper count for user: %s", currentUser.getId());
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

package com.samjdtechnologies.answer42.ui.views;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
public class DashboardView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardView.class);
    
    private final PaperService paperService;
    private final ProjectService projectService;

    private User currentUser;
    

    /**
     * Constructs the dashboard view with necessary service dependencies.
     * Initializes the main view that displays user's papers, projects, and quick actions.
     * 
     * @param paperService the service for paper-related operations including retrieval
     * @param projectService the service for project-related operations including retrieval
     */
    public DashboardView(PaperService paperService, ProjectService projectService) {
        this.paperService = paperService;
        this.projectService = projectService;

        addClassName(UIConstants.CSS_DASHBOARD_VIEW);
        getStyle().setHeight("auto");

        LoggingUtil.debug(LOG, "DashboardView", "DashboardView initialized");
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing dashboard view components");
        // Configure the view
        removeAll();

        // Add components directly to the view
        add(createWelcomeSection(), createStatsCards(), createQuickActions(), createRecentPapers());
        
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
        button.addClassName(UIConstants.CSS_DASHBOARD_ACTION_BTN);
        if (variantClass != null && !variantClass.isEmpty()) {
            button.addClassName(variantClass);
        }
        
        Icon icon = iconType.create();
        icon.addClassName(UIConstants.CSS_DASHBOARD_ACTION_BTN_ICON);
        
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
        
        // Create papers list container - styling now comes from the CSS
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
                        displayContent = paper.getPaperAbstract();
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
                        "PROCESSED".equals(paper.getStatus()),
                        paper
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
    
    private Component createPaperCard(String title, String content, boolean processed, Paper paper) {
        Div card = new Div();
        card.addClassName(UIConstants.CSS_PAPER_CARD);

        // Card header with title and status (with bottom border like project cards)
        Div header = new Div();
        header.addClassName(UIConstants.CSS_PAPER_HEADER);
        header.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

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

        // Card footer with action buttons (with top border like project cards)
        Div footer = new Div();
        footer.addClassName(UIConstants.CSS_PAPER_FOOTER);
        footer.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");
        
        Div actions = new Div();
        actions.addClassName(UIConstants.CSS_PROJECT_ACTIONS);
        
        // Create the action buttons with icons
        Button viewButton = new Button("View", new Icon(VaadinIcon.EYE), e -> viewPaper(paper));
        Button editButton = new Button("Edit", new Icon(VaadinIcon.EDIT), e -> editPaper(paper));
        Button downloadButton = new Button("Download", new Icon(VaadinIcon.DOWNLOAD), e -> downloadPaper(paper));
        Button deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH), e -> deletePaper(paper));
        
        // Apply theme variants for consistent styling
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        downloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        
        // Add buttons to the actions container - download button first
        actions.add(downloadButton, viewButton, editButton, deleteButton);
        footer.add(actions);

        // Add all parts to the card
        card.add(header, contentParagraph, metadata, footer);

        return card;
    }
    
    
    // Action handlers for paper cards
    private void viewPaper(Paper paper) {
        String title = paper != null ? paper.getTitle() : "Unknown";
        LoggingUtil.debug(LOG, "viewPaper", "View paper clicked: %s", title);
        // Navigate to paper view or open paper details dialog
        Notification.show("Viewing paper: " + title, 3000, Notification.Position.BOTTOM_START);
    }
    
    private void editPaper(Paper paper) {
        String title = paper != null ? paper.getTitle() : "Unknown";
        LoggingUtil.debug(LOG, "editPaper", "Edit paper clicked: %s", title);
        // Navigate to paper edit view or open edit dialog
        Notification.show("Editing paper: " + title, 3000, Notification.Position.BOTTOM_START);
    }
    
    private void downloadPaper(Paper paper) {
        String title = paper != null ? paper.getTitle() : "Unknown";
        LoggingUtil.debug(LOG, "downloadPaper", "Download paper clicked: %s", title);
        // Trigger paper download
        Notification.show("Downloading paper: " + title, 3000, Notification.Position.BOTTOM_START);
    }
    
    private void deletePaper(Paper paper) {
        if (paper == null) {
            LoggingUtil.error(LOG, "deletePaper", "Cannot delete null paper");
            return;
        }
        
        String title = paper.getTitle();
        LoggingUtil.debug(LOG, "deletePaper", "Delete paper clicked: %s", title);
        
        // Show confirmation dialog
        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Delete Paper");
        confirmDialog.setText("Are you sure you want to delete \"" + title + "\"? This action cannot be undone.");
        
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");
        
        confirmDialog.setConfirmText("Delete");
        confirmDialog.setConfirmButtonTheme("error primary");
        
        confirmDialog.addConfirmListener(event -> {
            try {
                // Use PaperService to delete the paper
                paperService.deletePaper(paper.getId());
                
                LoggingUtil.info(LOG, "deletePaper", "Successfully deleted paper: %s (ID: %s)", 
                    title, paper.getId());
                
                Notification notification = new Notification(
                    "Paper deleted successfully", 
                    3000, 
                    Notification.Position.BOTTOM_START
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.open();
                
                // Refresh the view
                initializeView();
            } catch (Exception e) {
                LoggingUtil.error(LOG, "deletePaper", "Failed to delete paper: %s", e.getMessage());
                
                Notification notification = new Notification(
                    "Failed to delete paper: " + e.getMessage(), 
                    3000, 
                    Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.open();
            }
        });
        
        confirmDialog.open();
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

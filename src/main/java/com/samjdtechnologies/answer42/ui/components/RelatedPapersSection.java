package com.samjdtechnologies.answer42.ui.components;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.db.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.repository.DiscoveredPaperRepository;
import com.samjdtechnologies.answer42.service.DiscoveryFeedbackService;
import com.samjdtechnologies.answer42.service.PaperBookmarkService;
import com.samjdtechnologies.answer42.service.discovery.DiscoveryCoordinator;
import com.samjdtechnologies.answer42.ui.helpers.components.RelatedPapersComponentHelper;
import com.samjdtechnologies.answer42.ui.helpers.components.RelatedPapersComponentHelper.DiscoveryStats;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H5;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Component for displaying related papers discovered through the multi-source discovery system.
 * Provides interactive exploration of citation networks, semantic similarities, and research trends.
 */
public class RelatedPapersSection extends VerticalLayout {

    private static final Logger LOG = LoggerFactory.getLogger(RelatedPapersSection.class);
    
    private final DiscoveredPaperRepository discoveredPaperRepository;
    private final DiscoveryCoordinator discoveryCoordinator;
    private final PaperBookmarkService paperBookmarkService;
    private final DiscoveryFeedbackService discoveryFeedbackService;
    
    private final Paper sourcePaper;
    private final Grid<DiscoveredPaper> discoveryGrid = new Grid<>(DiscoveredPaper.class, false);
    private final TextField searchField = new TextField();
    private final Select<String> sourceFilter = new Select<>();
    private final ComboBox<RelationshipType> relationshipFilter = new ComboBox<>();
    private final Button discoverButton = new Button("Discover Related Papers");
    private final Button visualizeButton = new Button("Citation Network");
    private final ProgressBar loadingBar = new ProgressBar();
    private final Div statsSection = new Div();
    
    private List<DiscoveredPaper> allDiscoveredPapers;
    private boolean discoveryInProgress = false;

    public RelatedPapersSection(Paper sourcePaper, 
                              DiscoveredPaperRepository discoveredPaperRepository,
                              DiscoveryCoordinator discoveryCoordinator,
                              PaperBookmarkService paperBookmarkService,
                              DiscoveryFeedbackService discoveryFeedbackService) {
        this.sourcePaper = sourcePaper;
        this.discoveredPaperRepository = discoveredPaperRepository;
        this.discoveryCoordinator = discoveryCoordinator;
        this.paperBookmarkService = paperBookmarkService;
        this.discoveryFeedbackService = discoveryFeedbackService;
        
        addClassName(UIConstants.CSS_DISCOVERY_SECTION);
        setSpacing(true);
        setPadding(true);
        
        initializeComponents();
        loadExistingDiscoveries();
        
        LoggingUtil.debug(LOG, "RelatedPapersSection", "Initialized for paper: %s", sourcePaper.getId());
    }
    
    private void initializeComponents() {
        // Header section
        add(createHeaderSection());
        
        // Controls section
        add(createControlsSection());
        
        // Loading indicator
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        add(loadingBar);
        
        // Discovery grid
        add(createDiscoveryGrid());
        
        // Discovery stats
        statsSection.addClassName(UIConstants.CSS_DISCOVERY_STATS);
        statsSection.getStyle().setMarginTop("var(--lumo-space-m)");
        add(statsSection);
    }
    
    private Component createHeaderSection() {
        HorizontalLayout header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        
        H3 title = new H3("Related Papers Discovery");
        title.getStyle().setMargin("0");
        
        // Discovery status indicator
        Span statusSpan = createDiscoveryStatusSpan();
        
        // Action buttons
        HorizontalLayout actionButtons = new HorizontalLayout();
        actionButtons.setSpacing(true);
        
        discoverButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        discoverButton.setIcon(VaadinIcon.SEARCH.create());
        discoverButton.addClickListener(e -> startAsyncDiscovery());
        
        visualizeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        visualizeButton.setIcon(VaadinIcon.CLUSTER.create());
        visualizeButton.addClickListener(e -> showCitationNetwork());
        visualizeButton.setEnabled(false); // Enable when papers are discovered
        
        actionButtons.add(discoverButton, visualizeButton);
        
        header.add(title, statusSpan);
        header.setFlexGrow(1, title);
        header.addClassName(UIConstants.CSS_DISCOVERY_HEADER);
        
        VerticalLayout headerSection = new VerticalLayout(header, actionButtons);
        headerSection.setSpacing(true);
        headerSection.setPadding(false);
        
        return headerSection;
    }
    
    private Span createDiscoveryStatusSpan() {
        long discoveredCount = discoveredPaperRepository.countBySourcePaperId(sourcePaper.getId());
        
        Span statusSpan = new Span();
        if (discoveredCount > 0) {
            statusSpan.setText(discoveredCount + " papers discovered");
            statusSpan.getElement().getThemeList().add("badge success");
        } else {
            statusSpan.setText("No discoveries yet");
            statusSpan.getElement().getThemeList().add("badge");
        }
        
        return statusSpan;
    }
    
    private Component createControlsSection() {
        // Search field
        searchField.setPlaceholder("Search discovered papers...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> filterDiscoveries());
        searchField.setWidth("300px");
        
        // Source filter - Implementation of Feature 1: Source filtering
        sourceFilter.setLabel("Discovery Source");
        sourceFilter.setItems("All Sources", "Crossref", "Semantic Scholar", "Perplexity");
        sourceFilter.setValue("All Sources");
        sourceFilter.addValueChangeListener(e -> filterDiscoveries());
        
        // Relationship filter - Implementation of Feature 1: Relationship filtering
        relationshipFilter.setLabel("Relationship Type");
        relationshipFilter.setItems(RelationshipType.values());
        relationshipFilter.setItemLabelGenerator(RelatedPapersComponentHelper::getRelationshipDisplayName);
        relationshipFilter.setPlaceholder("All relationships");
        relationshipFilter.addValueChangeListener(e -> filterDiscoveries());
        
        HorizontalLayout controls = new HorizontalLayout(searchField, sourceFilter, relationshipFilter);
        controls.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        controls.setSpacing(true);
        controls.addClassName(UIConstants.CSS_DISCOVERY_CONTROLS);
        
        return controls;
    }
    
    private Component createDiscoveryGrid() {
        discoveryGrid.addClassName(UIConstants.CSS_DISCOVERY_GRID);
        discoveryGrid.setHeight("400px");
        
        // Configure columns
        discoveryGrid.addColumn(DiscoveredPaper::getTitle)
            .setHeader("Title")
            .setResizable(true)
            .setFlexGrow(3);
            
        discoveryGrid.addColumn(new ComponentRenderer<>(this::createAuthorsComponent))
            .setHeader("Authors")
            .setResizable(true)
            .setFlexGrow(2);
            
        discoveryGrid.addColumn(new ComponentRenderer<>(this::createSourceBadge))
            .setHeader("Source")
            .setResizable(true)
            .setFlexGrow(1);
            
        discoveryGrid.addColumn(new ComponentRenderer<>(this::createRelationshipBadge))
            .setHeader("Relationship")
            .setResizable(true)
            .setFlexGrow(1);
            
        discoveryGrid.addColumn(new ComponentRenderer<>(this::createRelevanceScore))
            .setHeader("Relevance")
            .setResizable(true)
            .setFlexGrow(1);
            
        discoveryGrid.addColumn(new ComponentRenderer<>(this::createActions))
            .setHeader("Actions")
            .setFlexGrow(1);
        
        // Row click handler
        discoveryGrid.addItemClickListener(event -> showPaperDetails(event.getItem()));
        
        return discoveryGrid;
    }
    
    private Component createAuthorsComponent(DiscoveredPaper paper) {
        return new Span(RelatedPapersComponentHelper.formatAuthors(paper.getAuthors()));
    }
    
    private Component createSourceBadge(DiscoveredPaper paper) {
        String displayName = RelatedPapersComponentHelper.getSourceDisplayName(paper.getDiscoverySource());
        String themeClass = RelatedPapersComponentHelper.getSourceThemeClass(paper.getDiscoverySource());
        
        Span badge = new Span(displayName);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(themeClass);
        
        return badge;
    }
    
    private Component createRelationshipBadge(DiscoveredPaper paper) {
        RelationshipType relationship = RelatedPapersComponentHelper.determineRelationshipType(paper);
        String displayName = RelatedPapersComponentHelper.getRelationshipDisplayName(relationship);
        String themeClass = RelatedPapersComponentHelper.getRelationshipThemeClass(relationship);
        
        Span badge = new Span(displayName);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(themeClass);
        
        return badge;
    }
    
    private Component createRelevanceScore(DiscoveredPaper paper) {
        double score = paper.getRelevanceScore();
        String colorClass = RelatedPapersComponentHelper.getRelevanceColorClass(score);
        
        Span scoreSpan = new Span(String.format("%.2f", score));
        scoreSpan.getStyle().setFontWeight("bold");
        scoreSpan.getStyle().setColor(colorClass);
        
        return scoreSpan;
    }
    
    private Component createActions(DiscoveredPaper paper) {
        Button viewButton = new Button(VaadinIcon.EYE.create());
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        viewButton.addClickListener(e -> showPaperDetails(paper));
        viewButton.getElement().setAttribute("title", "View details");
        
        // Bookmark button with dynamic icon
        User currentUser = getCurrentUser();
        boolean isBookmarked = currentUser != null && 
                              paperBookmarkService.isBookmarked(currentUser.getId(), paper.getId());
        
        Button bookmarkButton = new Button(isBookmarked ? VaadinIcon.HEART.create() : VaadinIcon.HEART_O.create());
        bookmarkButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        if (isBookmarked) {
            bookmarkButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }
        bookmarkButton.addClickListener(e -> handleBookmarkToggle(paper, bookmarkButton));
        bookmarkButton.getElement().setAttribute("title", isBookmarked ? "Remove bookmark" : "Bookmark paper");
        
        Button feedbackButton = new Button(VaadinIcon.THUMBS_UP.create());
        feedbackButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        feedbackButton.addClickListener(e -> showFeedbackDialog(paper));
        feedbackButton.getElement().setAttribute("title", "Provide feedback");
        
        HorizontalLayout actions = new HorizontalLayout(viewButton, bookmarkButton, feedbackButton);
        actions.setSpacing(false);
        actions.setPadding(false);
        
        return actions;
    }
    
    private void loadExistingDiscoveries() {
        try {
            allDiscoveredPapers = RelatedPapersComponentHelper.loadExistingDiscoveries(sourcePaper, discoveredPaperRepository);
            updateGrid();
            updateDetailedStats(); // Feature 2: Detailed statistics display
            
            // Enable visualization if papers exist
            visualizeButton.setEnabled(!allDiscoveredPapers.isEmpty());
            
            LoggingUtil.debug(LOG, "loadExistingDiscoveries", "Loaded %d discovered papers", 
                allDiscoveredPapers.size());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "loadExistingDiscoveries", "Failed to load discoveries", e);
            Notification.show("Failed to load discoveries: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    // Feature 1: Enhanced relationship filtering implementation
    private void filterDiscoveries() {
        if (allDiscoveredPapers == null) {
            return;
        }
        
        String searchTerm = searchField.getValue();
        String sourceFilterValue = sourceFilter.getValue();
        RelationshipType relationshipFilterValue = relationshipFilter.getValue();
        
        List<DiscoveredPaper> filteredPapers = RelatedPapersComponentHelper.filterDiscoveries(
            allDiscoveredPapers, searchTerm, sourceFilterValue, relationshipFilterValue);
            
        discoveryGrid.setItems(filteredPapers);
        updateDetailedStats(); // Update stats with filtered data
    }
    
    private void updateGrid() {
        if (allDiscoveredPapers != null) {
            discoveryGrid.setItems(allDiscoveredPapers);
        }
    }
    
    // Feature 2: Detailed statistics display implementation
    private void updateDetailedStats() {
        statsSection.removeAll();
        
        if (allDiscoveredPapers == null || allDiscoveredPapers.isEmpty()) {
            statsSection.add(new Span("No discovery statistics available"));
            return;
        }
        
        // Get filtered papers for accurate stats
        List<DiscoveredPaper> visiblePapers = discoveryGrid.getGenericDataView()
            .getItems().collect(Collectors.toList());
        
        DiscoveryStats stats = RelatedPapersComponentHelper.calculateStats(visiblePapers);
        
        // Create detailed statistics layout
        HorizontalLayout statsLayout = new HorizontalLayout();
        statsLayout.setSpacing(true);
        statsLayout.setWidthFull();
        
        // Total papers card
        VerticalLayout totalCard = createStatCard("Total Papers", 
            String.valueOf(stats.getTotalPapers()), VaadinIcon.FILE_TEXT, "primary");
        
        // Average relevance card
        VerticalLayout relevanceCard = createStatCard("Avg Relevance", 
            String.format("%.2f", stats.getAvgRelevance()), VaadinIcon.STAR, "success");
        
        // Top source card
        VerticalLayout sourceCard = createStatCard("Top Source", 
            stats.getTopSource(), VaadinIcon.CONNECT, "contrast");
        
        // Top relationship card
        VerticalLayout relationshipCard = createStatCard("Top Relationship", 
            stats.getTopRelationship(), VaadinIcon.LINK, "normal");
        
        statsLayout.add(totalCard, relevanceCard, sourceCard, relationshipCard);
        
        // Detailed breakdown
        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setSpacing(false);
        detailsLayout.setPadding(false);
        
        H5 detailsHeader = new H5("Discovery Breakdown");
        detailsLayout.add(detailsHeader);
        
        // Source breakdown
        HorizontalLayout sourceBreakdown = new HorizontalLayout();
        sourceBreakdown.setSpacing(true);
        stats.getSourceStats().forEach((source, count) -> {
            Span sourceSpan = new Span(RelatedPapersComponentHelper.getSourceDisplayName(source) + ": " + count);
            sourceSpan.getElement().getThemeList().add("badge");
            sourceSpan.getElement().getThemeList().add(RelatedPapersComponentHelper.getSourceThemeClass(source));
            sourceBreakdown.add(sourceSpan);
        });
        
        // Relationship breakdown
        HorizontalLayout relationshipBreakdown = new HorizontalLayout();
        relationshipBreakdown.setSpacing(true);
        stats.getRelationshipStats().forEach((relationship, count) -> {
            Span relSpan = new Span(RelatedPapersComponentHelper.getRelationshipDisplayName(relationship) + ": " + count);
            relSpan.getElement().getThemeList().add("badge");
            relSpan.getElement().getThemeList().add(RelatedPapersComponentHelper.getRelationshipThemeClass(relationship));
            relationshipBreakdown.add(relSpan);
        });
        
        detailsLayout.add(sourceBreakdown, relationshipBreakdown);
        
        statsSection.add(statsLayout, detailsLayout);
    }
    
    private VerticalLayout createStatCard(String title, String value, VaadinIcon icon, String theme) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("stat-card");
        card.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        card.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        card.getStyle().set("padding", "var(--lumo-space-m)");
        card.setSpacing(false);
        card.setPadding(false);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        
        Icon iconComponent = icon.create();
        iconComponent.getStyle().set("color", "var(--lumo-" + theme + "-color)");
        iconComponent.setSize("2em");
        
        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");
        titleSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        valueSpan.getStyle().set("font-weight", "bold");
        valueSpan.getStyle().set("color", "var(--lumo-" + theme + "-color)");
        
        card.add(iconComponent, titleSpan, valueSpan);
        return card;
    }
    
    // Feature 3: Async discovery execution implementation
    private void startAsyncDiscovery() {
        if (discoveryInProgress) {
            return;
        }
        
        discoveryInProgress = true;
        discoverButton.setEnabled(false);
        discoverButton.setText("Discovering...");
        loadingBar.setVisible(true);
        
        LoggingUtil.info(LOG, "startAsyncDiscovery", "Starting async discovery for paper: %s", sourcePaper.getId());
        
        // Execute discovery asynchronously using RelatedPapersHelper
        RelatedPapersComponentHelper.executeDiscoveryAsync(
            sourcePaper,
            discoveryCoordinator,
            this::onDiscoverySuccess,
            this::onDiscoveryError
        ).whenComplete((result, throwable) -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                discoveryInProgress = false;
                discoverButton.setEnabled(true);
                discoverButton.setText("Discover Related Papers");
                loadingBar.setVisible(false);
                
                if (throwable == null && result != null) {
                    // Process successful discovery results
                    processDiscoveryResult(result);
                    Notification.show("Discovery completed! Found " + 
                        (result.getDiscoveredPapers() != null ? result.getDiscoveredPapers().size() : 0) + 
                        " related papers", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
            }));
        });
    }
    
    private void onDiscoverySuccess() {
        LoggingUtil.info(LOG, "onDiscoverySuccess", "Discovery completed successfully");
        getUI().ifPresent(ui -> ui.access(() -> {
            loadExistingDiscoveries(); // Refresh the data
            updateDiscoveryStatus();
        }));
    }
    
    private void onDiscoveryError() {
        LoggingUtil.error(LOG, "onDiscoveryError", "Discovery failed");
        getUI().ifPresent(ui -> ui.access(() -> {
            Notification.show("Discovery failed. Please try again.", 5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }));
    }
    
    private void processDiscoveryResult(RelatedPaperDiscoveryResult result) {
        if (result != null && result.getDiscoveredPapers() != null) {
            LoggingUtil.info(LOG, "processDiscoveryResult", 
                "Processing %d discovered papers", result.getDiscoveredPapers().size());
            
            // Refresh data to include new discoveries
            loadExistingDiscoveries();
            updateDiscoveryStatus();
            visualizeButton.setEnabled(true);
        }
    }
    
    private void showCitationNetwork() {
        if (allDiscoveredPapers == null || allDiscoveredPapers.isEmpty()) {
            Notification.show("No discovered papers to visualize. Please discover papers first.", 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }
        
        try {
            CitationNetworkDialog networkDialog = new CitationNetworkDialog(sourcePaper, allDiscoveredPapers);
            networkDialog.open();
            
            LoggingUtil.debug(LOG, "showCitationNetwork", "Opened citation network for %d papers", 
                allDiscoveredPapers.size());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "showCitationNetwork", "Failed to open citation network", e);
            Notification.show("Failed to open citation network: " + e.getMessage(), 
                5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    private void showPaperDetails(DiscoveredPaper paper) {
        try {
            // Create and open the comprehensive paper details dialog
            PaperDetailsDialog detailsDialog = PaperDetailsDialog.createDialog(paper, discoveredPaperRepository);
            detailsDialog.open();
            
            LoggingUtil.debug(LOG, "showPaperDetails", "Opened paper details dialog for: %s", paper.getTitle());
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "showPaperDetails", "Failed to open paper details dialog", e);
            
            // Fallback to simple notification
            Notification.show("Failed to load paper details: " + e.getMessage(), 
                5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    private void handleBookmarkToggle(DiscoveredPaper paper, Button bookmarkButton) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            Notification.show("Please log in to bookmark papers", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        
        try {
            boolean nowBookmarked = paperBookmarkService.toggleBookmark(currentUser.getId(), paper.getId());
            
            // Update button appearance
            Icon newIcon = nowBookmarked ? VaadinIcon.HEART.create() : VaadinIcon.HEART_O.create();
            bookmarkButton.setIcon(newIcon);
            
            if (nowBookmarked) {
                bookmarkButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
                bookmarkButton.getElement().setAttribute("title", "Remove bookmark");
                Notification.show("Paper bookmarked!", 2000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                bookmarkButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
                bookmarkButton.getElement().setAttribute("title", "Bookmark paper");
                Notification.show("Bookmark removed", 2000, Notification.Position.BOTTOM_START);
            }
            
            LoggingUtil.info(LOG, "handleBookmarkToggle", "Toggled bookmark for paper %s, now bookmarked: %s", 
                paper.getTitle(), nowBookmarked);
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "handleBookmarkToggle", "Error toggling bookmark", e);
            Notification.show("Error updating bookmark", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Convert relevance rating to numeric value (1-5 scale)
     * @param relevanceRating The relevance rating string from feedback dialog
     * @return Integer value between 1-5, or null if invalid/null input
     */
    private Integer convertRelevanceToNumeric(String relevanceRating) {
        if (relevanceRating == null || relevanceRating.trim().isEmpty()) {
            return null;
        }
        
        switch (relevanceRating.toLowerCase().trim()) {
            case "very relevant":
            case "highly relevant":
            case "excellent":
                return 5;
            case "relevant":
            case "good":
                return 4;
            case "somewhat relevant":
            case "moderately relevant":
            case "average":
            case "neutral":
                return 3;
            case "not very relevant":
            case "poor":
                return 2;
            case "not relevant":
            case "irrelevant":
            case "very poor":
                return 1;
            default:
                // Try to parse as integer if it's already numeric
                try {
                    int numericValue = Integer.parseInt(relevanceRating.trim());
                    if (numericValue >= 1 && numericValue <= 5) {
                        return numericValue;
                    }
                } catch (NumberFormatException e) {
                    // Not a valid number, fall through to return null
                }
                return null;
        }
    }

    /**
     * Convert accuracy rating to numeric value (1-5 scale)
     * @param accuracyRating The accuracy rating string from feedback dialog
     * @return Integer value between 1-5, or null if invalid/null input
     */
    private Integer convertAccuracyToNumeric(String accuracyRating) {
        if (accuracyRating == null || accuracyRating.trim().isEmpty()) {
            return null;
        }
        
        switch (accuracyRating.toLowerCase().trim()) {
            case "very accurate":
            case "highly accurate":
            case "perfect":
            case "excellent":
                return 5;
            case "accurate":
            case "mostly accurate":
            case "good":
                return 4;
            case "somewhat accurate":
            case "moderately accurate":
            case "average":
            case "neutral":
                return 3;
            case "not very accurate":
            case "inaccurate":
            case "poor":
                return 2;
            case "very inaccurate":
            case "completely wrong":
            case "very poor":
                return 1;
            default:
                // Try to parse as integer if it's already numeric
                try {
                    int numericValue = Integer.parseInt(accuracyRating.trim());
                    if (numericValue >= 1 && numericValue <= 5) {
                        return numericValue;
                    }
                } catch (NumberFormatException e) {
                    // Not a valid number, fall through to return null
                }
                return null;
        }
    }
    
    private void showFeedbackDialog(DiscoveredPaper paper) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            Notification.show("Please log in to provide feedback", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        
        DiscoveryFeedbackDialog feedbackDialog = new DiscoveryFeedbackDialog(paper, feedback -> {
            try {
                // Convert feedback dialog ratings to numeric values (1-5 scale)
                Integer relevanceRating = convertRelevanceToNumeric(feedback.getRelevanceRating());
                Integer qualityRating = convertAccuracyToNumeric(feedback.getRelationshipAccuracy());
                Integer usefulnessRating = relevanceRating; // Use relevance as usefulness for now
                
                // Get session metadata
                String sessionId = getUI().map(ui -> ui.getSession().getSession().getId()).orElse("unknown");
                String ipAddress = getUI().map(ui -> ui.getSession().getBrowser().getAddress()).orElse("unknown");
                String userAgent = getUI().map(ui -> ui.getSession().getBrowser().getBrowserApplication()).orElse("unknown");
                
                // Save explicit rating feedback to database
                discoveryFeedbackService.saveExplicitRating(
                    currentUser.getId(),
                    paper.getId(),
                    sourcePaper.getId(),
                    relevanceRating,
                    qualityRating,
                    usefulnessRating,
                    feedback.getComments(),
                    sessionId,
                    ipAddress,
                    userAgent
                );
                
                LoggingUtil.info(LOG, "showFeedbackDialog", 
                    "Saved feedback to database: user=%s, paper=%s, relevance=%s, quality=%s", 
                    currentUser.getId(), paper.getId(), relevanceRating, qualityRating);
                
                // Show success message
                Notification.show("Feedback saved! Thank you for helping improve our discovery algorithms.", 
                    3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    
            } catch (Exception e) {
                LoggingUtil.error(LOG, "showFeedbackDialog", "Error saving feedback to database", e);
                Notification.show("Error saving feedback: " + e.getMessage(), 
                    5000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        
        feedbackDialog.open();
    }
    
    /**
     * Gets the current user from the session.
     * @return Current user or null if not logged in
     */
    private User getCurrentUser() {
        return MainLayout.getCurrentUser();
    }
    
    /**
     * Refreshes the discovery data from the database.
     */
    public void refresh() {
        loadExistingDiscoveries();
    }
    
    /**
     * Updates the discovery status after new discoveries are made.
     */
    public void updateDiscoveryStatus() {
        // Refresh the status span
        Component headerSection = getComponentAt(0);
        if (headerSection instanceof VerticalLayout) {
            VerticalLayout headerLayout = (VerticalLayout) headerSection;
            Component header = headerLayout.getComponentAt(0);
            if (header instanceof HorizontalLayout) {
                HorizontalLayout headerHLayout = (HorizontalLayout) header;
                if (headerHLayout.getComponentCount() > 1) {
                    headerHLayout.replace(headerHLayout.getComponentAt(1), createDiscoveryStatusSpan());
                }
            }
        }
        
        refresh();
    }
}

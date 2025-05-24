# 6. UI Integration

## Overview

This document details the integration of semantic search capabilities into Answer42's existing Vaadin UI, providing users with powerful paper discovery features while maintaining the platform's consistent design language.

## Enhanced Papers View Integration

### Updated PapersView with Semantic Search

```java
@Route(value = "papers", layout = MainLayout.class)
@PageTitle("Papers - Answer42")
@CssImport("./styles/themes/answer42/components/papers.css")
@CssImport("./styles/themes/answer42/components/semantic-search.css")
public class PapersView extends VerticalLayout {
    
    private final PaperService paperService;
    private final SemanticSearchService semanticSearchService;
    private final AuthenticationService authService;
    
    // Search components
    private SemanticSearchComponent searchComponent;
    private SearchResultsGrid resultsGrid;
    private SearchFiltersPanel filtersPanel;
    private SearchModeToggle searchModeToggle;
    
    // Traditional view components
    private Grid<Paper> papersGrid;
    private TabSheet viewTabSheet;
    
    public PapersView(
            PaperService paperService,
            SemanticSearchService semanticSearchService,
            AuthenticationService authService) {
        this.paperService = paperService;
        this.semanticSearchService = semanticSearchService;
        this.authService = authService;
        
        createLayout();
        refreshPapersGrid();
    }
    
    private void createLayout() {
        addClassName("papers-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        
        // Header section with title and actions
        add(createHeaderSection());
        
        // Search section
        add(createSearchSection());
        
        // Main content area with tabs
        add(createMainContentArea());
    }
    
    private Component createSearchSection() {
        VerticalLayout searchSection = new VerticalLayout();
        searchSection.addClassName("search-section");
        searchSection.setPadding(true);
        searchSection.setSpacing(true);
        
        // Search mode toggle
        searchModeToggle = new SearchModeToggle();
        searchModeToggle.addValueChangeListener(e -> handleSearchModeChange(e.getValue()));
        
        // Semantic search component
        searchComponent = new SemanticSearchComponent();
        searchComponent.addSearchListener(this::handleSemanticSearch);
        searchComponent.setVisible(false); // Hidden by default
        
        // Search filters
        filtersPanel = new SearchFiltersPanel();
        filtersPanel.addFilterChangeListener(this::handleFilterChange);
        filtersPanel.setVisible(false);
        
        searchSection.add(searchModeToggle, searchComponent, filtersPanel);
        return searchSection;
    }
    
    private Component createMainContentArea() {
        viewTabSheet = new TabSheet();
        viewTabSheet.addClassName("papers-tab-sheet");
        viewTabSheet.setSizeFull();
        
        // Traditional papers tab
        Tab papersTab = viewTabSheet.add("My Papers", createPapersGrid());
        papersTab.setId("papers-tab");
        
        // Search results tab (initially hidden)
        resultsGrid = new SearchResultsGrid(semanticSearchService);
        resultsGrid.addPaperSelectListener(this::handlePaperSelection);
        
        Tab searchTab = viewTabSheet.add("Search Results", resultsGrid);
        searchTab.setId("search-results-tab");
        searchTab.setVisible(false);
        
        return viewTabSheet;
    }
    
    private void handleSearchModeChange(SearchMode mode) {
        boolean isSemanticMode = mode == SearchMode.SEMANTIC;
        
        searchComponent.setVisible(isSemanticMode);
        filtersPanel.setVisible(isSemanticMode);
        
        if (isSemanticMode) {
            searchComponent.focus();
        } else {
            // Reset to traditional view
            showPapersTab();
            resultsGrid.clearResults();
        }
    }
    
    private void handleSemanticSearch(SemanticSearchEvent event) {
        String query = event.getQuery();
        
        if (query.trim().isEmpty()) {
            showPapersTab();
            return;
        }
        
        try {
            // Show loading state
            searchComponent.setLoading(true);
            
            // Build search request
            SemanticSearchRequest request = buildSearchRequest(query);
            
            // Execute search asynchronously
            UI.getCurrent().access(() -> {
                try {
                    SemanticSearchResponse response = semanticSearchService.search(request);
                    
                    // Update results grid
                    resultsGrid.setResults(response);
                    
                    // Show search results tab
                    showSearchResultsTab(response.getTotalResults());
                    
                } catch (Exception e) {
                    LoggingUtil.error(LOG, "handleSemanticSearch", 
                        "Search failed: %s", e, e.getMessage());
                    
                    Notification.show("Search failed: " + e.getMessage(), 
                        3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                } finally {
                    searchComponent.setLoading(false);
                }
            });
            
        } catch (Exception e) {
            searchComponent.setLoading(false);
            LoggingUtil.error(LOG, "handleSemanticSearch", "Search error: %s", e, e.getMessage());
            
            Notification.show("Search error: " + e.getMessage(), 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    private SemanticSearchRequest buildSearchRequest(String query) {
        User currentUser = authService.getCurrentUser();
        List<String> searchTypes = filtersPanel.getSelectedSearchTypes();
        
        return SemanticSearchRequest.builder()
            .query(query)
            .searchTypes(searchTypes.isEmpty() ? List.of("abstract") : searchTypes)
            .similarityThreshold(filtersPanel.getSimilarityThreshold())
            .maxResults(filtersPanel.getMaxResults())
            .userId(filtersPanel.isMyPapersOnly() ? currentUser.getId() : null)
            .publicOnly(filtersPanel.isPublicOnly())
            .build();
    }
    
    private void showSearchResultsTab(int resultCount) {
        Tab searchTab = viewTabSheet.getTabAt(1);
        searchTab.setVisible(true);
        searchTab.setLabel("Search Results (" + resultCount + ")");
        viewTabSheet.setSelectedTab(searchTab);
    }
    
    private void showPapersTab() {
        Tab searchTab = viewTabSheet.getTabAt(1);
        searchTab.setVisible(false);
        viewTabSheet.setSelectedTab(0);
    }
}
```

## Semantic Search Components

### Core Search Component

```java
@CssImport("./styles/themes/answer42/components/semantic-search.css")
public class SemanticSearchComponent extends Div {
    
    private final TextField searchField;
    private final Button searchButton;
    private final ProgressBar loadingBar;
    private final SearchSuggestionsDropdown suggestionsDropdown;
    
    private final List<SearchListener> searchListeners = new ArrayList<>();
    
    public SemanticSearchComponent() {
        addClassName("semantic-search-component");
        createLayout();
    }
    
    private void createLayout() {
        // Search input section
        HorizontalLayout searchLayout = new HorizontalLayout();
        searchLayout.addClassName("search-input-layout");
        searchLayout.setWidthFull();
        searchLayout.setAlignItems(Alignment.CENTER);
        
        // Search field with suggestions
        searchField = new TextField();
        searchField.setPlaceholder("Describe what you're looking for...");
        searchField.addClassName("semantic-search-field");
        searchField.setWidthFull();
        searchField.addValueChangeListener(e -> handleSearchInput(e.getValue()));
        searchField.addKeyPressListener(Key.ENTER, e -> performSearch());
        
        // Search button with icon
        searchButton = new Button("Search");
        searchButton.addClassName("search-button");
        searchButton.setIcon(VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> performSearch());
        
        // Advanced search toggle
        Button advancedToggle = new Button("Advanced");
        advancedToggle.addClassName("advanced-toggle");
        advancedToggle.setIcon(VaadinIcon.COG.create());
        advancedToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        
        searchLayout.add(searchField, searchButton, advancedToggle);
        searchLayout.setFlexGrow(1, searchField);
        
        // Loading indicator
        loadingBar = new ProgressBar();
        loadingBar.addClassName("search-loading-bar");
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        
        // Search suggestions dropdown
        suggestionsDropdown = new SearchSuggestionsDropdown();
        suggestionsDropdown.addSuggestionSelectListener(this::selectSuggestion);
        
        // Quick search examples
        Div examplesDiv = createSearchExamples();
        
        add(searchLayout, loadingBar, suggestionsDropdown, examplesDiv);
    }
    
    private Div createSearchExamples() {
        Div examplesDiv = new Div();
        examplesDiv.addClassName("search-examples");
        
        Span examplesLabel = new Span("Try searching for:");
        examplesLabel.addClassName("examples-label");
        
        String[] examples = {
            "machine learning approaches for natural language processing",
            "climate change impact on agriculture",
            "neural network architectures for image recognition",
            "quantum computing applications in cryptography"
        };
        
        HorizontalLayout examplesLayout = new HorizontalLayout();
        examplesLayout.addClassName("examples-layout");
        
        for (String example : examples) {
            Button exampleButton = new Button(example);
            exampleButton.addClassName("example-button");
            exampleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            exampleButton.addClickListener(e -> {
                searchField.setValue(example);
                performSearch();
            });
            examplesLayout.add(exampleButton);
        }
        
        examplesDiv.add(examplesLabel, examplesLayout);
        return examplesDiv;
    }
    
    private void handleSearchInput(String value) {
        if (value.length() >= 3) {
            // Show search suggestions after 3 characters
            suggestionsDropdown.updateSuggestions(value);
            suggestionsDropdown.setVisible(true);
        } else {
            suggestionsDropdown.setVisible(false);
        }
    }
    
    private void performSearch() {
        String query = searchField.getValue().trim();
        if (!query.isEmpty()) {
            suggestionsDropdown.setVisible(false);
            fireSearchEvent(new SemanticSearchEvent(this, query));
        }
    }
    
    private void selectSuggestion(String suggestion) {
        searchField.setValue(suggestion);
        suggestionsDropdown.setVisible(false);
        performSearch();
    }
    
    public void setLoading(boolean loading) {
        loadingBar.setVisible(loading);
        searchButton.setEnabled(!loading);
        searchField.setEnabled(!loading);
        
        if (loading) {
            searchButton.setText("Searching...");
        } else {
            searchButton.setText("Search");
        }
    }
    
    public void focus() {
        searchField.focus();
    }
    
    // Event handling
    public void addSearchListener(SearchListener listener) {
        searchListeners.add(listener);
    }
    
    private void fireSearchEvent(SemanticSearchEvent event) {
        searchListeners.forEach(listener -> listener.onSearch(event));
    }
    
    @FunctionalInterface
    public interface SearchListener {
        void onSearch(SemanticSearchEvent event);
    }
    
    public static class SemanticSearchEvent extends ComponentEvent<SemanticSearchComponent> {
        private final String query;
        
        public SemanticSearchEvent(SemanticSearchComponent source, String query) {
            super(source, false);
            this.query = query;
        }
        
        public String getQuery() {
            return query;
        }
    }
}
```

### Search Results Grid

```java
public class SearchResultsGrid extends VerticalLayout {
    
    private final SemanticSearchService searchService;
    private final Grid<SemanticSearchMatch> grid;
    private final Div summaryDiv;
    private final Pagination pagination;
    
    private List<PaperSelectListener> selectListeners = new ArrayList<>();
    private SemanticSearchResponse lastResponse;
    
    public SearchResultsGrid(SemanticSearchService searchService) {
        this.searchService = searchService;
        
        addClassName("search-results-grid");
        setPadding(false);
        setSpacing(true);
        
        // Summary section
        summaryDiv = new Div();
        summaryDiv.addClassName("search-summary");
        
        // Results grid
        grid = createResultsGrid();
        
        // Pagination
        pagination = new Pagination();
        pagination.addPageChangeListener(this::handlePageChange);
        pagination.setVisible(false);
        
        add(summaryDiv, grid, pagination);
    }
    
    private Grid<SemanticSearchMatch> createResultsGrid() {
        Grid<SemanticSearchMatch> resultsGrid = new Grid<>(SemanticSearchMatch.class, false);
        resultsGrid.addClassName("semantic-search-results");
        resultsGrid.setHeightFull();
        
        // Title column with similarity highlighting
        resultsGrid.addComponentColumn(this::createTitleComponent)
            .setHeader("Paper Title")
            .setFlexGrow(2)
            .setSortable(false);
        
        // Similarity score column
        resultsGrid.addComponentColumn(this::createSimilarityComponent)
            .setHeader("Similarity")
            .setWidth("120px")
            .setSortable(true);
        
        // Match type column
        resultsGrid.addComponentColumn(this::createMatchTypeComponent)
            .setHeader("Match Type")
            .setWidth("150px")
            .setSortable(false);
        
        // Abstract preview column
        resultsGrid.addComponentColumn(this::createAbstractComponent)
            .setHeader("Abstract Preview")
            .setFlexGrow(3)
            .setSortable(false);
        
        // Actions column
        resultsGrid.addComponentColumn(this::createActionsComponent)
            .setHeader("Actions")
            .setWidth("120px")
            .setSortable(false);
        
        // Row selection
        resultsGrid.addSelectionListener(selection -> {
            selection.getFirstSelectedItem().ifPresent(match -> {
                firePaperSelectEvent(match.getPaperId());
            });
        });
        
        return resultsGrid;
    }
    
    private Component createTitleComponent(SemanticSearchMatch match) {
        VerticalLayout titleLayout = new VerticalLayout();
        titleLayout.setPadding(false);
        titleLayout.setSpacing(false);
        
        // Title with similarity highlighting
        Span titleSpan = new Span(match.getTitle());
        titleSpan.addClassName("result-title");
        
        if (match.isHighSimilarity()) {
            titleSpan.addClassName("high-similarity");
        } else if (match.isMediumSimilarity()) {
            titleSpan.addClassName("medium-similarity");
        }
        
        // Authors and metadata
        Span metadataSpan = new Span(formatMetadata(match));
        metadataSpan.addClassName("result-metadata");
        
        titleLayout.add(titleSpan, metadataSpan);
        return titleLayout;
    }
    
    private Component createSimilarityComponent(SemanticSearchMatch match) {
        VerticalLayout similarityLayout = new VerticalLayout();
        similarityLayout.setPadding(false);
        similarityLayout.setSpacing(false);
        similarityLayout.setAlignItems(Alignment.CENTER);
        
        // Similarity percentage
        Span scoreSpan = new Span(match.getFormattedSimilarityScore());
        scoreSpan.addClassName("similarity-score");
        
        if (match.isHighSimilarity()) {
            scoreSpan.addClassName("high-score");
        } else if (match.isMediumSimilarity()) {
            scoreSpan.addClassName("medium-score");
        } else {
            scoreSpan.addClassName("low-score");
        }
        
        // Similarity bar
        ProgressBar similarityBar = new ProgressBar(0, 1, match.getSimilarityScore());
        similarityBar.addClassName("similarity-bar");
        
        similarityLayout.add(scoreSpan, similarityBar);
        return similarityLayout;
    }
    
    private Component createMatchTypeComponent(SemanticSearchMatch match) {
        if (match.isMultiDimensionalMatch()) {
            VerticalLayout typeLayout = new VerticalLayout();
            typeLayout.setPadding(false);
            typeLayout.setSpacing(false);
            
            for (String element : match.getMatchingElements()) {
                Span typeSpan = new Span(formatMatchType(element));
                typeSpan.addClassName("match-type-badge");
                typeSpan.addClassName("match-type-" + element.toLowerCase());
                typeLayout.add(typeSpan);
            }
            
            return typeLayout;
        } else {
            Span typeSpan = new Span(formatMatchType(match.getMatchType()));
            typeSpan.addClassName("match-type-badge");
            typeSpan.addClassName("match-type-" + match.getMatchType().toLowerCase());
            return typeSpan;
        }
    }
    
    private Component createAbstractComponent(SemanticSearchMatch match) {
        String abstractText = match.getAbstract_();
        if (abstractText == null || abstractText.trim().isEmpty()) {
            return new Span("No abstract available");
        }
        
        // Truncate abstract for preview
        String preview = abstractText.length() > 200 ? 
            abstractText.substring(0, 200) + "..." : abstractText;
        
        Span abstractSpan = new Span(preview);
        abstractSpan.addClassName("abstract-preview");
        abstractSpan.getElement().setProperty("title", abstractText); // Full text on hover
        
        return abstractSpan;
    }
    
    private Component createActionsComponent(SemanticSearchMatch match) {
        HorizontalLayout actionsLayout = new HorizontalLayout();
        actionsLayout.setPadding(false);
        actionsLayout.setSpacing(true);
        
        // View paper button
        Button viewButton = new Button("View");
        viewButton.addClassName("action-button");
        viewButton.setIcon(VaadinIcon.EYE.create());
        viewButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        viewButton.addClickListener(e -> firePaperSelectEvent(match.getPaperId()));
        
        // Find similar button
        Button similarButton = new Button("Similar");
        similarButton.addClassName("action-button");
        similarButton.setIcon(VaadinIcon.SEARCH.create());
        similarButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        similarButton.addClickListener(e -> findSimilarPapers(match.getPaperId()));
        
        actionsLayout.add(viewButton, similarButton);
        return actionsLayout;
    }
    
    public void setResults(SemanticSearchResponse response) {
        this.lastResponse = response;
        
        // Update summary
        updateSummary(response);
        
        // Update grid
        grid.setItems(response.getMatches());
        
        // Update pagination if needed
        updatePagination(response);
        
        // Scroll to top
        getElement().executeJs("this.scrollTop = 0;");
    }
    
    private void updateSummary(SemanticSearchResponse response) {
        summaryDiv.removeAll();
        
        if (!response.hasResults()) {
            Span noResultsSpan = new Span("No papers found for your search query.");
            noResultsSpan.addClassName("no-results-message");
            summaryDiv.add(noResultsSpan);
            return;
        }
        
        // Results summary
        String summaryText = String.format("Found %d papers in %dms", 
            response.getTotalResults(), response.getExecutionTimeMs());
        
        Span summarySpan = new Span(summaryText);
        summarySpan.addClassName("results-summary");
        
        // Average similarity score
        response.getAverageSimilarityScore().ifPresent(avgScore -> {
            String avgText = String.format(" (avg similarity: %.1f%%)", avgScore * 100);
            Span avgSpan = new Span(avgText);
            avgSpan.addClassName("average-similarity");
            summaryDiv.add(summarySpan, avgSpan);
        });
        
        if (summaryDiv.getComponentCount() == 0) {
            summaryDiv.add(summarySpan);
        }
    }
    
    public void clearResults() {
        grid.setItems();
        summaryDiv.removeAll();
        pagination.setVisible(false);
    }
    
    // Event handling for paper selection
    public void addPaperSelectListener(PaperSelectListener listener) {
        selectListeners.add(listener);
    }
    
    private void firePaperSelectEvent(UUID paperId) {
        selectListeners.forEach(listener -> listener.onPaperSelected(paperId));
    }
    
    @FunctionalInterface
    public interface PaperSelectListener {
        void onPaperSelected(UUID paperId);
    }
}
```

## Search Filters Panel

### Advanced Search Filters

```java
public class SearchFiltersPanel extends Details {
    
    private final CheckboxGroup<String> searchTypesGroup;
    private final Slider similaritySlider;
    private final NumberField maxResultsField;
    private final Checkbox myPapersOnlyCheckbox;
    private final Checkbox publicOnlyCheckbox;
    
    private final List<FilterChangeListener> filterListeners = new ArrayList<>();
    
    public SearchFiltersPanel() {
        setSummaryText("Advanced Search Options");
        addClassName("search-filters-panel");
        
        createLayout();
        setDefaultValues();
    }
    
    private void createLayout() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        
        // Search types section
        content.add(createSearchTypesSection());
        
        // Similarity threshold section  
        content.add(createSimilaritySection());
        
        // Result limits section
        content.add(createResultLimitsSection());
        
        // Access control section
        content.add(createAccessControlSection());
        
        add(content);
    }
    
    private Component createSearchTypesSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        
        H4 title = new H4("Search In");
        title.addClassName("filter-section-title");
        
        searchTypesGroup = new CheckboxGroup<>();
        searchTypesGroup.addClassName("search-types-group");
        searchTypesGroup.setItems("abstract", "content", "title", "concepts", "methodology", "findings");
        searchTypesGroup.setItemLabelGenerator(this::formatSearchTypeLabel);
        searchTypesGroup.addValueChangeListener(e -> fireFilterChangeEvent());
        
        section.add(title, searchTypesGroup);
        return section;
    }
    
    private Component createSimilaritySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        
        H4 title = new H4("Minimum Similarity");
        title.addClassName("filter-section-title");
        
        similaritySlider = new Slider(0.1, 1.0, 0.5);
        similaritySlider.addClassName("similarity-slider");
        similaritySlider.setStep(0.05);
        similaritySlider.setWidth("100%");
        
        Span valueLabel = new Span();
        valueLabel.addClassName("slider-value-label");
        
        similaritySlider.addValueChangeListener(e -> {
            double value = e.getValue();
            valueLabel.setText(String.format("%.0f%%", value * 100));
            fireFilterChangeEvent();
        });
        
        // Set initial value
        valueLabel.setText("50%");
        
        section.add(title, similaritySlider, valueLabel);
        return section;
    }
    
    private Component createResultLimitsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        
        H4 title = new H4("Maximum Results");
        title.addClassName("filter-section-title");
        
        maxResultsField = new NumberField();
        maxResultsField.addClassName("max-results-field");
        maxResultsField.setMin(1);
        maxResultsField.setMax(100);
        maxResultsField.setStep(5);
        maxResultsField.setWidth("120px");
        maxResultsField.addValueChangeListener(e -> fireFilterChangeEvent());
        
        section.add(title, maxResultsField);
        return section;
    }
    
    private Component createAccessControlSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        
        H4 title = new H4("Paper Access");
        title.addClassName("filter-section-title");
        
        myPapersOnlyCheckbox = new Checkbox("My papers only");
        myPapersOnlyCheckbox.addClassName("access-checkbox");
        myPapersOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) {
                publicOnlyCheckbox.setValue(false);
            }
            fireFilterChangeEvent();
        });
        
        publicOnlyCheckbox = new Checkbox("Public papers only");
        publicOnlyCheckbox.addClassName("access-checkbox");
        publicOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) {
                myPapersOnlyCheckbox.setValue(false);
            }
            fireFilterChangeEvent();
        });
        
        section.add(title, myPapersOnlyCheckbox, publicOnlyCheckbox);
        return section;
    }
    
    private void setDefaultValues() {
        searchTypesGroup.setValue(Set.of("abstract"));
        similaritySlider.setValue(0.5);
        maxResultsField.setValue(20.0);
        myPapersOnlyCheckbox.setValue(false);
        publicOnlyCheckbox.setValue(false);
    }
    
    private String formatSearchTypeLabel(String type) {
        return switch (type) {
            case "abstract" -> "Abstract";
            case "content" -> "Full Content";
            case "title" -> "Title";
            case "concepts" -> "Key Concepts";
            case "methodology" -> "Methodology";
            case "findings" -> "Findings";
            default -> type;
        };
    }
    
    // Public getters for filter values
    public List<String> getSelectedSearchTypes() {
        return new ArrayList<>(searchTypesGroup.getValue());
    }
    
    public double getSimilarityThreshold() {
        return similaritySlider.getValue();
    }
    
    public int getMaxResults() {
        return maxResultsField.getValue().intValue();
    }
    
    public boolean isMyPapersOnly() {
        return myPapersOnlyCheckbox.getValue();
    }
    
    public boolean isPublicOnly() {
        return publicOnlyCheckbox.getValue();
    }
    
    // Event handling
    public void addFilterChangeListener(FilterChangeListener listener) {
        filterListeners.add(listener);
    }
    
    private void fireFilterChangeEvent() {
        FilterChangeEvent event = new FilterChangeEvent(this);
        filterListeners.forEach(listener -> listener.onFilterChange(event));
    }
    
    @FunctionalInterface
    public interface FilterChangeListener {
        void onFilterChange(FilterChangeEvent event);
    }
    
    public static class FilterChangeEvent extends ComponentEvent<SearchFiltersPanel> {
        public FilterChangeEvent(SearchFiltersPanel source) {
            super(source, false);
        }
    }
}
```

This UI integration provides a comprehensive semantic search experience that seamlessly integrates with Answer42's existing Vaadin interface while offering powerful search capabilities and intuitive user interactions.

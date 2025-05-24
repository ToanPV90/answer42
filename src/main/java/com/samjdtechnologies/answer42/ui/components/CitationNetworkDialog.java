package com.samjdtechnologies.answer42.ui.components;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.ui.components.helpers.CitationNetworkDialogComponentHelper;
import com.samjdtechnologies.answer42.ui.components.helpers.RelatedPapersComponentHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.dom.Style;

/**
 * Interactive citation network visualization dialog for exploring paper relationships.
 * Displays papers as interconnected nodes with different relationship types.
 */
public class CitationNetworkDialog extends Dialog {

    private static final Logger LOG = LoggerFactory.getLogger(CitationNetworkDialog.class);
    
    private final Paper sourcePaper;
    private final List<DiscoveredPaper> discoveredPapers;
    
    private final Div networkCanvas = new Div();
    private final CheckboxGroup<RelationshipType> relationshipFilter = new CheckboxGroup<>();
    private final Select<String> layoutSelect = new Select<>();
    private final Span networkStats = new Span();
    
    private NetworkVisualizationMode currentMode = NetworkVisualizationMode.FORCE_DIRECTED;
    private Set<RelationshipType> activeRelationships;

    public CitationNetworkDialog(Paper sourcePaper, List<DiscoveredPaper> discoveredPapers) {
        this.sourcePaper = sourcePaper;
        this.discoveredPapers = discoveredPapers;
        this.activeRelationships = Set.of(RelationshipType.values());
        
        initializeDialog();
        createNetworkVisualization();
        
        LoggingUtil.debug(LOG, "CitationNetworkDialog", 
            "Created citation network with %d papers for source: %s", 
            discoveredPapers.size(), sourcePaper.getTitle());
    }
    
    private void initializeDialog() {
        setHeaderTitle("Citation Network Visualization");
        setWidth("90vw");
        setHeight("80vh");
        setResizable(true);
        setDraggable(true);
        addClassName("citation-network-dialog");
        
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);
        
        layout.add(createControlsSection());
        
        networkCanvas.addClassName("network-canvas");
        networkCanvas.setSizeFull();
        networkCanvas.getStyle()
            .setBorder("1px solid var(--lumo-contrast-20pct)")
            .setBorderRadius("var(--lumo-border-radius-m)")
            .setBackgroundColor("var(--lumo-base-color)")
            .setPosition(Style.Position.RELATIVE)
            .setOverflow(Style.Overflow.HIDDEN);
        
        layout.add(networkCanvas);
        layout.setFlexGrow(1, networkCanvas);
        layout.add(createFooterSection());
        
        add(layout);
    }
    
    private Component createControlsSection() {
        HorizontalLayout controls = new HorizontalLayout();
        controls.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        controls.setWidthFull();
        controls.setPadding(true);
        controls.setSpacing(true);
        
        relationshipFilter.setLabel("Show Relationships");
        relationshipFilter.setItems(RelationshipType.values());
        relationshipFilter.setValue(activeRelationships);
        relationshipFilter.setItemLabelGenerator(RelatedPapersComponentHelper::getRelationshipDisplayName);
        relationshipFilter.addValueChangeListener(e -> {
            activeRelationships = e.getValue();
            updateNetworkVisualization();
        });
        
        layoutSelect.setLabel("Layout");
        layoutSelect.setItems("Force-Directed", "Hierarchical", "Circular", "Grid");
        layoutSelect.setValue("Force-Directed");
        layoutSelect.addValueChangeListener(e -> {
            currentMode = NetworkVisualizationMode.fromDisplayName(e.getValue());
            updateNetworkVisualization();
        });
        
        Button resetButton = new Button("Reset View", VaadinIcon.REFRESH.create());
        resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        resetButton.addClickListener(e -> resetNetworkView());
        
        Button exportButton = new Button("Export", VaadinIcon.DOWNLOAD.create());
        exportButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        exportButton.addClickListener(e -> exportNetwork());
        
        HorizontalLayout actionButtons = new HorizontalLayout(resetButton, exportButton);
        actionButtons.setSpacing(true);
        
        controls.add(relationshipFilter, layoutSelect, actionButtons);
        controls.setFlexGrow(1, relationshipFilter);
        
        return controls;
    }
    
    private Component createFooterSection() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footer.setWidthFull();
        footer.setPadding(true);
        footer.setSpacing(true);
        
        updateNetworkStats();
        
        Details helpDetails = new Details("How to Use", createHelpContent());
        helpDetails.setOpened(false);
        
        Button closeButton = new Button("Close", e -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        footer.add(networkStats, helpDetails, closeButton);
        footer.setFlexGrow(1, helpDetails);
        
        return footer;
    }
    
    private Component createHelpContent() {
        VerticalLayout help = new VerticalLayout();
        help.setPadding(false);
        help.setSpacing(true);
        
        help.add(new Paragraph("• Click on papers to view details and explore connections"));
        help.add(new Paragraph("• Use relationship filters to focus on specific connection types"));
        help.add(new Paragraph("• Try different layouts to better understand the network structure"));
        help.add(new Paragraph("• Papers are sized based on citation count and relevance"));
        help.add(new Paragraph("• Connection lines show relationship types with different colors and styles"));
        
        return help;
    }
    
    private void createNetworkVisualization() {
        networkCanvas.removeAll();
        
        Component visualization = switch (currentMode) {
            case FORCE_DIRECTED -> createForceDirectedVisualization();
            case HIERARCHICAL -> createHierarchicalVisualization();
            case CIRCULAR -> createCircularVisualization();
            case GRID -> createGridVisualization();
        };
        
        networkCanvas.add(visualization);
        updateNetworkStats();
    }
    
    private Component createForceDirectedVisualization() {
        Div visualization = new Div();
        visualization.setSizeFull();
        visualization.getStyle().setPosition(Style.Position.RELATIVE);
        
        Div sourcePaperNode = createPaperNode(sourcePaper, true);
        sourcePaperNode.getStyle()
            .setPosition(Style.Position.ABSOLUTE)
            .setLeft("50%")
            .setTop("50%")
            .setTransform("translate(-50%, -50%)")
            .set("z-index", "10");
        
        visualization.add(sourcePaperNode);
        
        List<DiscoveredPaper> filteredPapers = getFilteredPapers();
        int paperCount = filteredPapers.size();
        
        for (int i = 0; i < paperCount && i < 20; i++) {
            DiscoveredPaper paper = filteredPapers.get(i);
            Div paperNode = createDiscoveredPaperNode(paper);
            
            double angle = 2 * Math.PI * i / Math.min(paperCount, 20);
            double radius = 200 + (paper.getRelevanceScore() * 100);
            
            double x = 50 + (radius * Math.cos(angle)) / 10;
            double y = 50 + (radius * Math.sin(angle)) / 10;
            
            paperNode.getStyle()
                .setPosition(Style.Position.ABSOLUTE)
                .setLeft(x + "%")
                .setTop(y + "%")
                .setTransform("translate(-50%, -50%)");
            
            visualization.add(paperNode);
            
            // Enhanced connection lines using helper methods
            Div connectionLine = createEnhancedConnectionLine(angle, radius, paper);
            visualization.add(connectionLine);
        }
        
        if (paperCount > 20) {
            Div overflowMessage = new Div();
            overflowMessage.add(new Span("Showing 20 of " + paperCount + " papers"));
            overflowMessage.addClassName("overflow-message");
            overflowMessage.getStyle()
                .setPosition(Style.Position.ABSOLUTE)
                .setTop("10px")
                .setRight("10px")
                .setPadding("var(--lumo-space-s)")
                .setBackgroundColor("var(--lumo-primary-color-10pct)")
                .setBorderRadius("var(--lumo-border-radius-s)");
            
            visualization.add(overflowMessage);
        }
        
        return visualization;
    }
    
    private Component createHierarchicalVisualization() {
        VerticalLayout visualization = new VerticalLayout();
        visualization.setSizeFull();
        visualization.setPadding(true);
        visualization.setSpacing(true);
        
        HorizontalLayout sourceLevel = new HorizontalLayout();
        sourceLevel.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        sourceLevel.add(createPaperNode(sourcePaper, true));
        visualization.add(sourceLevel);
        
        getFilteredPapers().stream()
            .collect(Collectors.groupingBy(RelatedPapersComponentHelper::determineRelationshipType))
            .entrySet().stream()
            .filter(entry -> activeRelationships.contains(entry.getKey()))
            .forEach(entry -> {
                H4 levelHeader = new H4(RelatedPapersComponentHelper.getRelationshipDisplayName(entry.getKey()));
                levelHeader.getStyle().set("text-align", "center");
                visualization.add(levelHeader);
                
                HorizontalLayout level = new HorizontalLayout();
                level.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
                level.setSpacing(true);
                
                entry.getValue().stream()
                    .limit(8)
                    .forEach(paper -> level.add(createDiscoveredPaperNode(paper)));
                
                visualization.add(level);
            });
        
        return visualization;
    }
    
    private Component createCircularVisualization() {
        Div visualization = new Div();
        visualization.setSizeFull();
        visualization.getStyle().setPosition(Style.Position.RELATIVE);
        
        Div sourcePaperNode = createPaperNode(sourcePaper, true);
        sourcePaperNode.getStyle()
            .setPosition(Style.Position.ABSOLUTE)
            .setLeft("50%")
            .setTop("50%")
            .setTransform("translate(-50%, -50%)")
            .set("z-index", "10");
        
        visualization.add(sourcePaperNode);
        
        List<DiscoveredPaper> sortedPapers = getFilteredPapers().stream()
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .limit(24)
            .collect(Collectors.toList());
        
        createCircularRing(visualization, sortedPapers.subList(0, Math.min(8, sortedPapers.size())), 150);
        
        if (sortedPapers.size() > 8) {
            createCircularRing(visualization, sortedPapers.subList(8, Math.min(24, sortedPapers.size())), 250);
        }
        
        return visualization;
    }
    
    private void createCircularRing(Div container, List<DiscoveredPaper> papers, double radius) {
        int paperCount = papers.size();
        
        for (int i = 0; i < paperCount; i++) {
            DiscoveredPaper paper = papers.get(i);
            Div paperNode = createDiscoveredPaperNode(paper);
            
            double angle = 2 * Math.PI * i / paperCount;
            double x = 50 + (radius * Math.cos(angle)) / 8;
            double y = 50 + (radius * Math.sin(angle)) / 8;
            
            paperNode.getStyle()
                .setPosition(Style.Position.ABSOLUTE)
                .setLeft(x + "%")
                .setTop(y + "%")
                .setTransform("translate(-50%, -50%)");
            
            container.add(paperNode);
            
            Div connectionLine = createEnhancedConnectionLine(angle, radius / 8, paper);
            container.add(connectionLine);
        }
    }
    
    private Component createGridVisualization() {
        VerticalLayout visualization = new VerticalLayout();
        visualization.setSizeFull();
        visualization.setPadding(true);
        visualization.setSpacing(true);
        
        HorizontalLayout sourceRow = new HorizontalLayout();
        sourceRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        sourceRow.add(createPaperNode(sourcePaper, true));
        visualization.add(sourceRow);
        
        List<DiscoveredPaper> filteredPapers = getFilteredPapers();
        int cols = 4;
        int rows = (int) Math.ceil((double) Math.min(filteredPapers.size(), 16) / cols);
        
        for (int row = 0; row < rows; row++) {
            HorizontalLayout gridRow = new HorizontalLayout();
            gridRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
            gridRow.setSpacing(true);
            
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index < filteredPapers.size()) {
                    gridRow.add(createDiscoveredPaperNode(filteredPapers.get(index)));
                }
            }
            
            visualization.add(gridRow);
        }
        
        return visualization;
    }
    
    private Div createPaperNode(Paper paper, boolean isSource) {
        Div node = new Div();
        node.addClassName(isSource ? "source-paper" : "paper-node");
        
        String title = paper.getTitle();
        if (title.length() > 40) {
            title = title.substring(0, 37) + "...";
        }
        
        Span titleSpan = new Span(title);
        titleSpan.getStyle()
            .setFontWeight("bold")
            .setFontSize("var(--lumo-font-size-s)")
            .setDisplay(Style.Display.BLOCK)
            .set("text-align", "center");
        
        String author = paper.getAuthors() != null && !paper.getAuthors().isEmpty() 
            ? paper.getAuthors().get(0) : "Unknown";
        if (author.length() > 20) {
            author = author.substring(0, 17) + "...";
        }
        
        Span authorSpan = new Span(author);
        authorSpan.getStyle()
            .setFontSize("var(--lumo-font-size-xs)")
            .setColor("var(--lumo-secondary-text-color)")
            .setDisplay(Style.Display.BLOCK)
            .set("text-align", "center");
        
        node.add(titleSpan, authorSpan);
        
        node.getStyle()
            .setWidth("120px")
            .setHeight("80px")
            .setBorder("2px solid " + (isSource ? "var(--lumo-success-color)" : "var(--lumo-primary-color)"))
            .setBorderRadius("var(--lumo-border-radius-m)")
            .setPadding("var(--lumo-space-xs)")
            .setBackgroundColor(isSource ? "var(--lumo-success-color-10pct)" : "var(--lumo-base-color)")
            .set("cursor", "pointer")
            .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
            .setOverflow(Style.Overflow.HIDDEN)
            .setDisplay(Style.Display.FLEX)
            .set("flex-direction", "column")
            .set("justify-content", "center");
        
        node.addClickListener(e -> showPaperDetails(paper));
        
        node.getElement().addEventListener("mouseenter", 
            domEvent -> node.getStyle().setTransform("scale(1.05)"));
        node.getElement().addEventListener("mouseleave", 
            domEvent -> node.getStyle().setTransform("scale(1.0)"));
        
        return node;
    }
    
    private Div createDiscoveredPaperNode(DiscoveredPaper paper) {
        Div node = new Div();
        node.addClassName("discovered-paper-node");
        
        String title = paper.getTitle();
        if (title.length() > 40) {
            title = title.substring(0, 37) + "...";
        }
        
        Span titleSpan = new Span(title);
        titleSpan.getStyle()
            .setFontWeight("bold")
            .setFontSize("var(--lumo-font-size-s)")
            .setDisplay(Style.Display.BLOCK)
            .set("text-align", "center");
        
        Span scoreSpan = new Span(String.format("%.2f", paper.getRelevanceScore()));
        scoreSpan.getStyle()
            .setFontSize("var(--lumo-font-size-xs)")
            .setColor(RelatedPapersComponentHelper.getRelevanceColorClass(paper.getRelevanceScore()))
            .setDisplay(Style.Display.BLOCK)
            .set("text-align", "center")
            .setFontWeight("bold");
        
        node.add(titleSpan, scoreSpan);
        
        String borderColor = paper.getRelevanceScore() > 0.8 ? "var(--lumo-success-color)" :
                           paper.getRelevanceScore() > 0.6 ? "var(--lumo-primary-color)" : 
                           "var(--lumo-contrast-30pct)";
        
        node.getStyle()
            .setWidth("100px")
            .setHeight("70px")
            .setBorder("1px solid " + borderColor)
            .setBorderRadius("var(--lumo-border-radius-s)")
            .setPadding("var(--lumo-space-xs)")
            .setBackgroundColor("var(--lumo-base-color)")
            .set("cursor", "pointer")
            .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)")
            .setOverflow(Style.Overflow.HIDDEN)
            .setDisplay(Style.Display.FLEX)
            .set("flex-direction", "column")
            .set("justify-content", "center");
        
        node.addClickListener(e -> showPaperDetails(paper));
        
        return node;
    }
    
    private Div createEnhancedConnectionLine(double angle, double radius, DiscoveredPaper paper) {
        Div line = new Div();
        line.addClassName("connection-line");
        
        RelationshipType relationship = RelatedPapersComponentHelper.determineRelationshipType(paper);
        String lineColor = CitationNetworkDialogComponentHelper.getConnectionLineColor(relationship, paper.getRelevanceScore());
        String lineStyle = CitationNetworkDialogComponentHelper.getConnectionLineStyle(relationship);
        double lineWidth = CitationNetworkDialogComponentHelper.getConnectionLineWidth(paper.getRelevanceScore());
        
        double length = radius;
        double rotation = Math.toDegrees(angle);
        
        line.getStyle()
            .setPosition(Style.Position.ABSOLUTE)
            .setLeft("50%")
            .setTop("50%")
            .setWidth(length + "px")
            .setHeight(lineWidth + "px")
            .setBackgroundColor(lineColor)
            .set("transform-origin", "left center")
            .setTransform("rotate(" + rotation + "deg)")
            .set("z-index", "1")
            .set("opacity", String.valueOf(0.3 + (paper.getRelevanceScore() * 0.5)));
        
        if ("dashed".equals(lineStyle)) {
            line.getStyle().set("background-image", 
                "repeating-linear-gradient(to right, " + lineColor + " 0px, " + lineColor + " 5px, transparent 5px, transparent 10px)");
        } else if ("dotted".equals(lineStyle)) {
            line.getStyle().set("background-image", 
                "repeating-linear-gradient(to right, " + lineColor + " 0px, " + lineColor + " 2px, transparent 2px, transparent 4px)");
        }
        
        if (CitationNetworkDialogComponentHelper.isDirectionalRelationship(relationship)) {
            Div arrowHead = new Div();
            arrowHead.getStyle()
                .setPosition(Style.Position.ABSOLUTE)
                .setRight("0")
                .setTop("50%")
                .setWidth("0")
                .setHeight("0")
                .set("border-left", "8px solid " + lineColor)
                .set("border-top", "4px solid transparent")
                .set("border-bottom", "4px solid transparent")
                .setTransform("translateY(-50%)");
            
            line.add(arrowHead);
        }
        
        return line;
    }
    
    private List<DiscoveredPaper> getFilteredPapers() {
        return discoveredPapers.stream()
            .filter(paper -> {
                RelationshipType paperRelationship = RelatedPapersComponentHelper.determineRelationshipType(paper);
                return activeRelationships.contains(paperRelationship);
            })
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .collect(Collectors.toList());
    }
    
    private void updateNetworkVisualization() {
        createNetworkVisualization();
    }
    
    private void updateNetworkStats() {
        CitationNetworkDialogComponentHelper.NetworkStats stats = 
            CitationNetworkDialogComponentHelper.calculateNetworkStats(getFilteredPapers());
            
        String statsText = String.format("Network: %d papers, Avg relevance: %.2f", 
            stats.getTotalPapers(), stats.getAvgRelevance());
            
        networkStats.setText(statsText);
        networkStats.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)");
    }
    
    private void resetNetworkView() {
        activeRelationships = Set.of(RelationshipType.values());
        relationshipFilter.setValue(activeRelationships);
        layoutSelect.setValue("Force-Directed");
        currentMode = NetworkVisualizationMode.FORCE_DIRECTED;
        createNetworkVisualization();
        
        Notification.show("Network view reset", 2000, Notification.Position.BOTTOM_START);
    }
    
    private void exportNetwork() {
        String currentLayout = layoutSelect.getValue();
        List<DiscoveredPaper> filteredPapers = getFilteredPapers();
        
        CitationNetworkDialogComponentHelper.exportNetworkAsSVG(
            sourcePaper, 
            filteredPapers, 
            currentLayout
        );
    }
    
    private void showPaperDetails(Object paper) {
        if (paper instanceof Paper) {
            Notification.show("Paper details: " + ((Paper) paper).getTitle(), 3000, Notification.Position.BOTTOM_START);
        } else if (paper instanceof DiscoveredPaper) {
            Notification.show("Discovered paper details: " + ((DiscoveredPaper) paper).getTitle(), 3000, Notification.Position.BOTTOM_START);
        }
    }
    
    private enum NetworkVisualizationMode {
        FORCE_DIRECTED("Force-Directed"),
        HIERARCHICAL("Hierarchical"), 
        CIRCULAR("Circular"),
        GRID("Grid");
        
        private final String displayName;
        
        NetworkVisualizationMode(String displayName) {
            this.displayName = displayName;
        }
        
        public static NetworkVisualizationMode fromDisplayName(String displayName) {
            for (NetworkVisualizationMode mode : values()) {
                if (mode.displayName.equals(displayName)) {
                    return mode;
                }
            }
            return FORCE_DIRECTED;
        }
    }
}

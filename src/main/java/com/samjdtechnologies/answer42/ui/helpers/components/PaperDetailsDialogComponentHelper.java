package com.samjdtechnologies.answer42.ui.helpers.components;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Helper class for the PaperDetailsDialog component.
 * Contains utility methods for formatting paper data and creating UI components.
 */
public class PaperDetailsDialogComponentHelper {
    
    private static final Logger LOG = LoggerFactory.getLogger(PaperDetailsDialogComponentHelper.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    // Constants for styling
    public static final String CSS_PAPER_HEADER = "paper-details-header";
    public static final String CSS_PAPER_METADATA = "paper-details-metadata";
    public static final String CSS_PAPER_ABSTRACT = "paper-details-abstract";
    public static final String CSS_PAPER_METRICS = "paper-details-metrics";
    public static final String CSS_PAPER_SOURCES = "paper-details-sources";
    public static final String CSS_PAPER_DISCOVERY = "paper-details-discovery";
    public static final String CSS_METRIC_CARD = "metric-card";
    public static final String CSS_METRIC_VALUE = "metric-value";
    public static final String CSS_METRIC_LABEL = "metric-label";
    public static final String CSS_TAG_CONTAINER = "tag-container";
    public static final String CSS_PAPER_TAG = "paper-tag";
    public static final String CSS_ACCESS_LINKS = "access-links";
    
    /**
     * Creates the paper header section with title, authors, and primary metadata.
     */
    public static Component createPaperHeader(DiscoveredPaper paper) {
        VerticalLayout header = new VerticalLayout();
        header.addClassName(CSS_PAPER_HEADER);
        header.setSpacing(true);
        header.setPadding(false);
        
        // Paper title
        H4 title = new H4(paper.getTitle());
        title.getStyle()
            .setMargin("0")
            .setLineHeight("1.3")
            .setColor("var(--lumo-primary-text-color)");
        
        // Authors
        if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
            Span authors = new Span(formatAuthors(paper.getAuthors()));
            authors.getStyle()
                .setFontSize("var(--lumo-font-size-m)")
                .setColor("var(--lumo-secondary-text-color)")
                .setFontWeight("500");
            header.add(authors);
        }
        
        // Publication venue and year
        HorizontalLayout venueLayout = createVenueLayout(paper);
        if (venueLayout.getComponentCount() > 0) {
            header.add(venueLayout);
        }
        
        header.add(title);
        return header;
    }
    
    /**
     * Creates the venue and publication date layout.
     */
    private static HorizontalLayout createVenueLayout(DiscoveredPaper paper) {
        HorizontalLayout venueLayout = new HorizontalLayout();
        venueLayout.setSpacing(true);
        venueLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        
        // Journal/venue
        if (paper.getJournal() != null && !paper.getJournal().isEmpty()) {
            Span journal = new Span(paper.getJournal());
            journal.getStyle()
                .set("font-style", "italic")
                .setColor("var(--lumo-secondary-text-color)");
            venueLayout.add(journal);
        } else if (paper.getVenue() != null && !paper.getVenue().isEmpty()) {
            Span venue = new Span(paper.getVenue());
            venue.getStyle()
                .set("font-style", "italic")
                .setColor("var(--lumo-secondary-text-color)");
            venueLayout.add(venue);
        }
        
        // Year
        if (paper.getYear() != null) {
            if (venueLayout.getComponentCount() > 0) {
                venueLayout.add(new Span("•"));
            }
            Span year = new Span(paper.getYear().toString());
            year.getStyle().setColor("var(--lumo-secondary-text-color)");
            venueLayout.add(year);
        }
        
        // Publication date
        if (paper.getPublicationDate() != null) {
            if (venueLayout.getComponentCount() > 0) {
                venueLayout.add(new Span("•"));
            }
            Span pubDate = new Span(paper.getPublicationDate().format(DATE_FORMATTER));
            pubDate.getStyle().setColor("var(--lumo-secondary-text-color)");
            venueLayout.add(pubDate);
        }
        
        return venueLayout;
    }
    
    /**
     * Creates the metrics section with citation counts, relevance scores, etc.
     */
    public static Component createMetricsSection(DiscoveredPaper paper) {
        VerticalLayout metricsSection = new VerticalLayout();
        metricsSection.addClassName(CSS_PAPER_METRICS);
        metricsSection.setSpacing(true);
        metricsSection.setPadding(false);
        
        H5 metricsHeader = new H5("Paper Metrics");
        metricsHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        metricsSection.add(metricsHeader);
        
        HorizontalLayout metricsCards = new HorizontalLayout();
        metricsCards.setSpacing(true);
        metricsCards.setWidthFull();
        
        // Relevance score
        Component relevanceCard = createMetricCard(
            "Relevance Score", 
            String.format("%.2f", paper.getRelevanceScore()),
            VaadinIcon.STAR,
            getRelevanceScoreTheme(paper.getRelevanceScore())
        );
        metricsCards.add(relevanceCard);
        
        // Citation count
        if (paper.getCitationCount() != null) {
            Component citationCard = createMetricCard(
                "Citations", 
                paper.getCitationCount().toString(),
                VaadinIcon.QUOTE_LEFT,
                getCitationCountTheme(paper.getCitationCount())
            );
            metricsCards.add(citationCard);
        }
        
        // Confidence score
        if (paper.getConfidenceScore() != null) {
            Component confidenceCard = createMetricCard(
                "Confidence", 
                String.format("%.2f", paper.getConfidenceScore()),
                VaadinIcon.CHECK_CIRCLE,
                getConfidenceScoreTheme(paper.getConfidenceScore())
            );
            metricsCards.add(confidenceCard);
        }
        
        // Quality indicator
        Component qualityCard = createMetricCard(
            "Quality", 
            paper.getQualityIndicator(),
            VaadinIcon.DIAMOND,
            getQualityTheme(paper.getQualityIndicator())
        );
        metricsCards.add(qualityCard);
        
        metricsSection.add(metricsCards);
        return metricsSection;
    }
    
    /**
     * Creates a metric card component.
     */
    private static Component createMetricCard(String label, String value, VaadinIcon icon, String theme) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName(CSS_METRIC_CARD);
        card.setSpacing(false);
        card.setPadding(true);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.getStyle()
            .setBorder("1px solid var(--lumo-contrast-10pct)")
            .setBorderRadius("var(--lumo-border-radius-m)")
            .setMinWidth("120px");
        
        Icon iconComponent = icon.create();
        iconComponent.getStyle()
            .setColor("var(--lumo-" + theme + "-color)")
            .setMarginBottom("var(--lumo-space-xs)");
        
        Span valueSpan = new Span(value);
        valueSpan.addClassName(CSS_METRIC_VALUE);
        valueSpan.getStyle()
            .setFontSize("var(--lumo-font-size-l)")
            .setFontWeight("bold")
            .setColor("var(--lumo-" + theme + "-color)");
        
        Span labelSpan = new Span(label);
        labelSpan.addClassName(CSS_METRIC_LABEL);
        labelSpan.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)");
        
        card.add(iconComponent, valueSpan, labelSpan);
        return card;
    }
    
    /**
     * Creates the discovery information section.
     */
    public static Component createDiscoverySection(DiscoveredPaper paper) {
        VerticalLayout discoverySection = new VerticalLayout();
        discoverySection.addClassName(CSS_PAPER_DISCOVERY);
        discoverySection.setSpacing(true);
        discoverySection.setPadding(false);
        
        H5 discoveryHeader = new H5("Discovery Information");
        discoveryHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        discoverySection.add(discoveryHeader);
        
        // Discovery source and relationship
        HorizontalLayout sourceLayout = new HorizontalLayout();
        sourceLayout.setSpacing(true);
        sourceLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        
        // Source badge
        Span sourceBadge = new Span(getSourceDisplayName(paper.getDiscoverySource()));
        sourceBadge.getElement().getThemeList().add("badge");
        sourceBadge.getElement().getThemeList().add(getSourceThemeClass(paper.getDiscoverySource()));
        
        // Relationship badge
        Span relationshipBadge = new Span(getRelationshipDisplayName(paper.getRelationshipType()));
        relationshipBadge.getElement().getThemeList().add("badge");
        relationshipBadge.getElement().getThemeList().add(getRelationshipThemeClass(paper.getRelationshipType()));
        
        sourceLayout.add(sourceBadge, relationshipBadge);
        discoverySection.add(sourceLayout);
        
        // Discovery metadata
        if (paper.getDiscoverySessionId() != null) {
            Paragraph sessionInfo = new Paragraph("Session ID: " + paper.getDiscoverySessionId());
            sessionInfo.getStyle()
                .setFontSize("var(--lumo-font-size-s)")
                .setColor("var(--lumo-secondary-text-color)")
                .setMargin("var(--lumo-space-xs) 0 0 0");
            discoverySection.add(sessionInfo);
        }
        
        // Discovery timestamp
        if (paper.getDiscoveredAt() != null) {
            Paragraph discoveredTime = new Paragraph("Discovered: " + 
                paper.getDiscoveredAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
            discoveredTime.getStyle()
                .setFontSize("var(--lumo-font-size-s)")
                .setColor("var(--lumo-secondary-text-color)")
                .setMargin("0");
            discoverySection.add(discoveredTime);
        }
        
        return discoverySection;
    }
    
    /**
     * Creates the abstract section if available.
     */
    public static Component createAbstractSection(DiscoveredPaper paper) {
        if (paper.getPaperAbstract() == null || paper.getPaperAbstract().trim().isEmpty()) {
            return new Div(); // Empty component
        }
        
        VerticalLayout abstractSection = new VerticalLayout();
        abstractSection.addClassName(CSS_PAPER_ABSTRACT);
        abstractSection.setSpacing(true);
        abstractSection.setPadding(false);
        
        H5 abstractHeader = new H5("Abstract");
        abstractHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        
        Paragraph abstractText = new Paragraph(paper.getPaperAbstract());
        abstractText.getStyle()
            .setLineHeight("1.6")
            .set("text-align", "justify")
            .setMargin("0");
        
        abstractSection.add(abstractHeader, abstractText);
        return abstractSection;
    }
    
    /**
     * Creates the topics and fields of study section.
     */
    public static Component createTopicsSection(DiscoveredPaper paper) {
        List<String> topics = paper.getTopics();
        List<String> fieldsOfStudy = paper.getFieldsOfStudy();
        
        if ((topics == null || topics.isEmpty()) && (fieldsOfStudy == null || fieldsOfStudy.isEmpty())) {
            return new Div(); // Empty component
        }
        
        VerticalLayout topicsSection = new VerticalLayout();
        topicsSection.setSpacing(true);
        topicsSection.setPadding(false);
        
        // Topics
        if (topics != null && !topics.isEmpty()) {
            H5 topicsHeader = new H5("Topics");
            topicsHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
            
            HorizontalLayout topicsContainer = createTagContainer(topics, "topic");
            topicsSection.add(topicsHeader, topicsContainer);
        }
        
        // Fields of study
        if (fieldsOfStudy != null && !fieldsOfStudy.isEmpty()) {
            H5 fieldsHeader = new H5("Fields of Study");
            fieldsHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
            
            HorizontalLayout fieldsContainer = createTagContainer(fieldsOfStudy, "field");
            topicsSection.add(fieldsHeader, fieldsContainer);
        }
        
        return topicsSection;
    }
    
    /**
     * Creates the access links section.
     */
    public static Component createAccessLinksSection(DiscoveredPaper paper) {
        VerticalLayout accessSection = new VerticalLayout();
        accessSection.addClassName(CSS_ACCESS_LINKS);
        accessSection.setSpacing(true);
        accessSection.setPadding(false);
        
        boolean hasLinks = false;
        
        H5 accessHeader = new H5("Access Links");
        accessHeader.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        
        HorizontalLayout linksLayout = new HorizontalLayout();
        linksLayout.setSpacing(true);
        
        // DOI link
        if (paper.getDoi() != null && !paper.getDoi().isEmpty()) {
            Button doiButton = createAccessButton("DOI", 
                "https://doi.org/" + paper.getDoi(), VaadinIcon.EXTERNAL_LINK);
            linksLayout.add(doiButton);
            hasLinks = true;
        }
        
        // PDF link
        if (paper.getPdfUrl() != null && !paper.getPdfUrl().isEmpty()) {
            Button pdfButton = createAccessButton("PDF", 
                paper.getPdfUrl(), VaadinIcon.FILE_TEXT);
            linksLayout.add(pdfButton);
            hasLinks = true;
        }
        
        // Access URL
        if (paper.getAccessUrl() != null && !paper.getAccessUrl().isEmpty()) {
            Button accessButton = createAccessButton("View Paper", 
                paper.getAccessUrl(), VaadinIcon.GLOBE);
            linksLayout.add(accessButton);
            hasLinks = true;
        }
        
        // Open access indicator
        if (Boolean.TRUE.equals(paper.getOpenAccess())) {
            Span openAccessBadge = new Span("Open Access");
            openAccessBadge.getElement().getThemeList().add("badge success");
            linksLayout.add(openAccessBadge);
            hasLinks = true;
        }
        
        if (hasLinks) {
            accessSection.add(accessHeader, linksLayout);
            return accessSection;
        }
        
        return new Div(); // Empty component
    }
    
    /**
     * Creates an access button for external links.
     */
    private static Button createAccessButton(String text, String url, VaadinIcon icon) {
        Button button = new Button(text, icon.create());
        button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        button.addClickListener(e -> {
            // Open in new tab
            button.getUI().ifPresent(ui -> ui.getPage().open(url, "_blank"));
        });
        return button;
    }
    
    /**
     * Creates a container for tags (topics or fields).
     */
    private static HorizontalLayout createTagContainer(List<String> tags, String type) {
        HorizontalLayout container = new HorizontalLayout();
        container.addClassName(CSS_TAG_CONTAINER);
        container.setSpacing(true);
        
        tags.stream()
            .limit(10) // Limit to prevent UI overflow
            .forEach(tag -> {
                Span tagSpan = new Span(tag);
                tagSpan.addClassName(CSS_PAPER_TAG);
                tagSpan.getElement().getThemeList().add("badge");
                tagSpan.getElement().getThemeList().add(type.equals("topic") ? "normal" : "contrast");
                container.add(tagSpan);
            });
        
        if (tags.size() > 10) {
            Span moreSpan = new Span("+" + (tags.size() - 10) + " more");
            moreSpan.getStyle()
                .setFontSize("var(--lumo-font-size-s)")
                .setColor("var(--lumo-secondary-text-color)");
            container.add(moreSpan);
        }
        
        return container;
    }
    
    // Utility methods for formatting
    
    /**
     * Formats authors list for display.
     */
    public static String formatAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return "Unknown authors";
        }
        
        if (authors.size() == 1) {
            return authors.get(0);
        } else if (authors.size() <= 3) {
            return String.join(", ", authors);
        } else {
            return String.join(", ", authors.subList(0, 2)) + ", et al.";
        }
    }
    
    /**
     * Gets display name for discovery source.
     */
    public static String getSourceDisplayName(String source) {
        if (source == null) return "Unknown";
        
        switch (source.toUpperCase()) {
            case "CROSSREF": return "Crossref";
            case "SEMANTIC_SCHOLAR": return "Semantic Scholar";
            case "PERPLEXITY": return "Perplexity";
            default: return source;
        }
    }
    
    /**
     * Gets theme class for discovery source.
     */
    public static String getSourceThemeClass(String source) {
        if (source == null) return "normal";
        
        switch (source.toUpperCase()) {
            case "CROSSREF": return "primary";
            case "SEMANTIC_SCHOLAR": return "success";
            case "PERPLEXITY": return "contrast";
            default: return "normal";
        }
    }
    
    /**
     * Gets display name for relationship type.
     */
    public static String getRelationshipDisplayName(String relationshipType) {
        if (relationshipType == null) return "Unknown";
        
        switch (relationshipType.toUpperCase()) {
            case "CITES": return "Cites this paper";
            case "CITED_BY": return "Cited by this paper";
            case "SEMANTIC_SIMILARITY": return "Similar content";
            case "AUTHOR_NETWORK": return "Same author(s)";
            case "CO_CITATION": return "Co-cited";
            case "BIBLIOGRAPHIC_COUPLING": return "Bibliographic coupling";
            case "TRENDING": return "Currently trending";
            default: return relationshipType;
        }
    }
    
    /**
     * Gets theme class for relationship type.
     */
    public static String getRelationshipThemeClass(String relationshipType) {
        if (relationshipType == null) return "normal";
        
        switch (relationshipType.toUpperCase()) {
            case "CITES": return "primary";
            case "CITED_BY": return "success";
            case "SEMANTIC_SIMILARITY": return "contrast";
            case "AUTHOR_NETWORK": return "error";
            case "CO_CITATION": return "normal";
            case "BIBLIOGRAPHIC_COUPLING": return "normal";
            case "TRENDING": return "success";
            default: return "normal";
        }
    }
    
    // Theme methods for metrics
    
    private static String getRelevanceScoreTheme(Double score) {
        if (score == null) return "normal";
        if (score >= 0.8) return "success";
        if (score >= 0.6) return "primary";
        if (score >= 0.4) return "contrast";
        return "error";
    }
    
    private static String getCitationCountTheme(Integer count) {
        if (count == null) return "normal";
        if (count >= 100) return "success";
        if (count >= 50) return "primary";
        if (count >= 10) return "contrast";
        return "normal";
    }
    
    private static String getConfidenceScoreTheme(Double score) {
        if (score == null) return "normal";
        if (score >= 0.9) return "success";
        if (score >= 0.7) return "primary";
        if (score >= 0.5) return "contrast";
        return "error";
    }
    
    private static String getQualityTheme(String quality) {
        if (quality == null) return "normal";
        
        switch (quality.toUpperCase()) {
            case "HIGH": return "success";
            case "MEDIUM": return "primary";
            case "LOW": return "contrast";
            default: return "normal";
        }
    }
    
    /**
     * Logs paper details dialog interaction.
     */
    public static void logPaperDetailsView(DiscoveredPaper paper) {
        LoggingUtil.info(LOG, "logPaperDetailsView", 
            "User viewed paper details for: %s (ID: %s, Source: %s)", 
            paper.getTitle(), paper.getId(), paper.getDiscoverySource());
    }
    
    /**
     * Updates the last accessed timestamp for a paper.
     */
    public static void updateLastAccessed(DiscoveredPaper paper) {
        try {
            paper.updateLastAccessed();
            LoggingUtil.debug(LOG, "updateLastAccessed", 
                "Updated last accessed timestamp for paper: %s", paper.getId());
        } catch (Exception e) {
            LoggingUtil.error(LOG, "updateLastAccessed", 
                "Failed to update last accessed timestamp", e);
        }
    }
}

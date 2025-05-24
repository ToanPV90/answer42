package com.samjdtechnologies.answer42.ui.components.helpers;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.StreamResource;

/**
 * Helper class for Citation Network Dialog operations.
 * Provides utility methods for network visualization, export, and styling.
 */
public class CitationNetworkDialogComponentHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CitationNetworkDialogComponentHelper.class);

    private CitationNetworkDialogComponentHelper() {
        // Utility class
    }

    /**
     * Gets connection line color based on relationship type and relevance.
     */
    public static String getConnectionLineColor(RelationshipType relationshipType, double relevance) {
        String baseColor = switch (relationshipType) {
            case CITES -> "var(--lumo-primary-color)";
            case CITED_BY -> "var(--lumo-success-color)";
            case SEMANTIC_SIMILARITY -> "var(--lumo-warning-color)";
            case METHODOLOGICAL -> "var(--lumo-error-color)";
            case AUTHOR_NETWORK -> "var(--lumo-contrast-color)";
            case VENUE_SIMILARITY -> "var(--lumo-tertiary-color)";
            case FIELD_RELATED -> "var(--lumo-primary-color-50pct)";
            case TRENDING -> "var(--lumo-success-color-50pct)";
            default -> "var(--lumo-contrast-30pct)";
        };
        return baseColor;
    }

    /**
     * Gets connection line style (solid, dashed, dotted) based on relationship type.
     */
    public static String getConnectionLineStyle(RelationshipType relationshipType) {
        return switch (relationshipType) {
            case CITES, CITED_BY -> "solid";
            case SEMANTIC_SIMILARITY, METHODOLOGICAL -> "dashed";
            case AUTHOR_NETWORK, VENUE_SIMILARITY -> "dotted";
            case TRENDING -> "double";
            default -> "solid";
        };
    }

    /**
     * Gets connection line width based on relevance score.
     */
    public static double getConnectionLineWidth(double relevance) {
        return Math.max(1.0, 1.0 + (relevance * 3.0)); // Width between 1px and 4px
    }

    /**
     * Determines if a relationship type is directional (requires arrow).
     */
    public static boolean isDirectionalRelationship(RelationshipType relationshipType) {
        return relationshipType == RelationshipType.CITES || 
               relationshipType == RelationshipType.CITED_BY ||
               relationshipType == RelationshipType.TRENDING;
    }

    /**
     * Gets network statistics for display.
     */
    public static NetworkStats calculateNetworkStats(List<DiscoveredPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return new NetworkStats(0, 0.0, Map.of(), Map.of());
        }

        int totalPapers = papers.size();
        double avgRelevance = papers.stream()
            .mapToDouble(DiscoveredPaper::getRelevanceScore)
            .average()
            .orElse(0.0);

        Map<String, Long> sourceStats = papers.stream()
            .collect(Collectors.groupingBy(
                DiscoveredPaper::getDiscoverySource,
                Collectors.counting()));

        Map<RelationshipType, Long> relationshipStats = papers.stream()
            .collect(Collectors.groupingBy(
                RelatedPapersComponentHelper::determineRelationshipType,
                Collectors.counting()));

        return new NetworkStats(totalPapers, avgRelevance, sourceStats, relationshipStats);
    }

    /**
     * Exports citation network as SVG.
     */
    public static void exportNetworkAsSVG(
            Paper sourcePaper, 
            List<DiscoveredPaper> discoveredPapers,
            String layoutMode) {
        
        try {
            String svgContent = generateSVGNetwork(sourcePaper, discoveredPapers, layoutMode);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("citation_network_%s_%s.svg", 
                sanitizeFilename(sourcePaper.getTitle()), timestamp);
            
            StreamResource resource = new StreamResource(filename, 
                () -> new ByteArrayInputStream(svgContent.getBytes()));
            
            // Create download anchor
            Anchor downloadAnchor = new Anchor(resource, "");
            downloadAnchor.getElement().setAttribute("download", true);
            downloadAnchor.getElement().setAttribute("style", "display: none");
            
            UI.getCurrent().getElement().appendChild(downloadAnchor.getElement());
            downloadAnchor.getElement().callJsFunction("click");
            UI.getCurrent().getElement().removeChild(downloadAnchor.getElement());
            
            Notification.show("Citation network exported as SVG", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
            LoggingUtil.info(LOG, "exportNetworkAsSVG", "Exported network for paper: %s", sourcePaper.getTitle());
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "exportNetworkAsSVG", "Failed to export network", e);
            Notification.show("Failed to export network: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Generates SVG content for the citation network.
     */
    private static String generateSVGNetwork(Paper sourcePaper, List<DiscoveredPaper> discoveredPapers, String layoutMode) {
        StringBuilder svg = new StringBuilder();
        
        // SVG header
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg width=\"800\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">\n");
        svg.append("<defs>\n");
        svg.append("  <style>\n");
        svg.append("    .paper-node { fill: #ffffff; stroke: #2563eb; stroke-width: 2; }\n");
        svg.append("    .source-paper { fill: #dcfce7; stroke: #16a34a; stroke-width: 3; }\n");
        svg.append("    .paper-text { font-family: Arial, sans-serif; font-size: 10px; text-anchor: middle; }\n");
        svg.append("    .connection-line { stroke-width: 1; opacity: 0.6; }\n");
        svg.append("  </style>\n");
        svg.append("</defs>\n");
        
        // Background
        svg.append("<rect width=\"800\" height=\"600\" fill=\"#fafafa\"/>\n");
        
        // Title
        svg.append("<text x=\"400\" y=\"30\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"16\" font-weight=\"bold\">");
        svg.append("Citation Network - ").append(escapeXml(truncateTitle(sourcePaper.getTitle(), 50)));
        svg.append("</text>\n");
        
        // Generate network based on layout mode
        if ("Force-Directed".equals(layoutMode)) {
            generateForceDirectedSVG(svg, sourcePaper, discoveredPapers);
        } else if ("Circular".equals(layoutMode)) {
            generateCircularSVG(svg, sourcePaper, discoveredPapers);
        } else {
            generateGridSVG(svg, sourcePaper, discoveredPapers);
        }
        
        // Legend
        generateLegend(svg, discoveredPapers);
        
        // Footer
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        svg.append("<text x=\"10\" y=\"590\" font-family=\"Arial\" font-size=\"8\" fill=\"#666\">");
        svg.append("Generated by Answer42 on ").append(timestamp);
        svg.append("</text>\n");
        
        svg.append("</svg>");
        
        return svg.toString();
    }

    /**
     * Generates force-directed layout SVG.
     */
    private static void generateForceDirectedSVG(StringBuilder svg, Paper sourcePaper, List<DiscoveredPaper> papers) {
        // Central source paper
        svg.append("<circle cx=\"400\" cy=\"300\" r=\"30\" class=\"source-paper\"/>\n");
        svg.append("<text x=\"400\" y=\"305\" class=\"paper-text\">");
        svg.append(escapeXml(truncateTitle(sourcePaper.getTitle(), 20)));
        svg.append("</text>\n");
        
        // Discovered papers in circle
        int maxPapers = Math.min(papers.size(), 16);
        for (int i = 0; i < maxPapers; i++) {
            DiscoveredPaper paper = papers.get(i);
            double angle = 2 * Math.PI * i / maxPapers;
            double radius = 150 + (paper.getRelevanceScore() * 50);
            
            double x = 400 + radius * Math.cos(angle);
            double y = 300 + radius * Math.sin(angle);
            
            // Connection line
            RelationshipType relationship = RelatedPapersComponentHelper.determineRelationshipType(paper);
            String lineColor = getConnectionLineColor(relationship, paper.getRelevanceScore());
            String lineStyle = getConnectionLineStyle(relationship);
            double lineWidth = getConnectionLineWidth(paper.getRelevanceScore());
            
            svg.append(String.format("<line x1=\"400\" y1=\"300\" x2=\"%.1f\" y2=\"%.1f\" ", x, y));
            svg.append(String.format("stroke=\"%s\" stroke-width=\"%.1f\" ", lineColor, lineWidth));
            if ("dashed".equals(lineStyle)) {
                svg.append("stroke-dasharray=\"5,5\" ");
            } else if ("dotted".equals(lineStyle)) {
                svg.append("stroke-dasharray=\"2,2\" ");
            }
            svg.append("class=\"connection-line\"/>\n");
            
            // Paper node
            double nodeRadius = 15 + (paper.getRelevanceScore() * 5);
            svg.append(String.format("<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" class=\"paper-node\"/>\n", x, y, nodeRadius));
            svg.append(String.format("<text x=\"%.1f\" y=\"%.1f\" class=\"paper-text\">", x, y + 3));
            svg.append(escapeXml(truncateTitle(paper.getTitle(), 15)));
            svg.append("</text>\n");
        }
    }

    /**
     * Generates circular layout SVG.
     */
    private static void generateCircularSVG(StringBuilder svg, Paper sourcePaper, List<DiscoveredPaper> papers) {
        // Central source paper
        svg.append("<circle cx=\"400\" cy=\"300\" r=\"25\" class=\"source-paper\"/>\n");
        svg.append("<text x=\"400\" y=\"305\" class=\"paper-text\">");
        svg.append(escapeXml(truncateTitle(sourcePaper.getTitle(), 20)));
        svg.append("</text>\n");
        
        // Sort papers by relevance
        List<DiscoveredPaper> sortedPapers = papers.stream()
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .limit(20)
            .collect(Collectors.toList());
        
        // Inner circle - high relevance
        int innerCount = Math.min(8, sortedPapers.size());
        for (int i = 0; i < innerCount; i++) {
            DiscoveredPaper paper = sortedPapers.get(i);
            double angle = 2 * Math.PI * i / innerCount;
            double x = 400 + 100 * Math.cos(angle);
            double y = 300 + 100 * Math.sin(angle);
            
            addPaperNodeToSVG(svg, paper, x, y, 400, 300);
        }
        
        // Outer circle - medium relevance
        if (sortedPapers.size() > 8) {
            int outerCount = Math.min(12, sortedPapers.size() - 8);
            for (int i = 0; i < outerCount; i++) {
                DiscoveredPaper paper = sortedPapers.get(8 + i);
                double angle = 2 * Math.PI * i / outerCount;
                double x = 400 + 180 * Math.cos(angle);
                double y = 300 + 180 * Math.sin(angle);
                
                addPaperNodeToSVG(svg, paper, x, y, 400, 300);
            }
        }
    }

    /**
     * Generates grid layout SVG.
     */
    private static void generateGridSVG(StringBuilder svg, Paper sourcePaper, List<DiscoveredPaper> papers) {
        // Source paper at top
        svg.append("<circle cx=\"400\" cy=\"100\" r=\"25\" class=\"source-paper\"/>\n");
        svg.append("<text x=\"400\" y=\"105\" class=\"paper-text\">");
        svg.append(escapeXml(truncateTitle(sourcePaper.getTitle(), 20)));
        svg.append("</text>\n");
        
        // Grid of papers
        int cols = 4;
        int maxPapers = Math.min(papers.size(), 12);
        
        for (int i = 0; i < maxPapers; i++) {
            DiscoveredPaper paper = papers.get(i);
            int row = i / cols;
            int col = i % cols;
            
            double x = 250 + col * 100;
            double y = 200 + row * 80;
            
            addPaperNodeToSVG(svg, paper, x, y, 400, 100);
        }
    }

    /**
     * Adds a paper node to SVG.
     */
    private static void addPaperNodeToSVG(StringBuilder svg, DiscoveredPaper paper, double x, double y, double centerX, double centerY) {
        // Connection line
        RelationshipType relationship = RelatedPapersComponentHelper.determineRelationshipType(paper);
        String lineColor = getConnectionLineColor(relationship, paper.getRelevanceScore());
        double lineWidth = getConnectionLineWidth(paper.getRelevanceScore());
        
        svg.append(String.format("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" ", centerX, centerY, x, y));
        svg.append(String.format("stroke=\"%s\" stroke-width=\"%.1f\" class=\"connection-line\"/>\n", lineColor, lineWidth));
        
        // Paper node
        double radius = 12 + (paper.getRelevanceScore() * 8);
        svg.append(String.format("<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" class=\"paper-node\"/>\n", x, y, radius));
        svg.append(String.format("<text x=\"%.1f\" y=\"%.1f\" class=\"paper-text\">", x, y + 3));
        svg.append(escapeXml(truncateTitle(paper.getTitle(), 12)));
        svg.append("</text>\n");
    }

    /**
     * Generates legend for SVG.
     */
    private static void generateLegend(StringBuilder svg, List<DiscoveredPaper> papers) {
        svg.append("<g transform=\"translate(20, 50)\">\n");
        svg.append("<text x=\"0\" y=\"0\" font-family=\"Arial\" font-size=\"12\" font-weight=\"bold\">Legend</text>\n");
        
        // Connection types
        Map<RelationshipType, Long> relationshipCounts = papers.stream()
            .collect(Collectors.groupingBy(
                RelatedPapersComponentHelper::determineRelationshipType,
                Collectors.counting()));
        
        int y = 20;
        for (Map.Entry<RelationshipType, Long> entry : relationshipCounts.entrySet()) {
            RelationshipType type = entry.getKey();
            String color = getConnectionLineColor(type, 1.0);
            String style = getConnectionLineStyle(type);
            
            svg.append(String.format("<line x1=\"0\" y1=\"%d\" x2=\"20\" y2=\"%d\" stroke=\"%s\" stroke-width=\"2\"", y, y, color));
            if ("dashed".equals(style)) {
                svg.append(" stroke-dasharray=\"3,3\"");
            }
            svg.append("/>\n");
            
            svg.append(String.format("<text x=\"25\" y=\"%d\" font-family=\"Arial\" font-size=\"9\" dominant-baseline=\"middle\">", y + 2));
            svg.append(RelatedPapersComponentHelper.getRelationshipDisplayName(type));
            svg.append(" (").append(entry.getValue()).append(")</text>\n");
            
            y += 15;
        }
        
        svg.append("</g>\n");
    }

    /**
     * Utility methods for SVG generation.
     */
    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    private static String truncateTitle(String title, int maxLength) {
        if (title == null) return "";
        return title.length() > maxLength ? title.substring(0, maxLength - 3) + "..." : title;
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(filename.length(), 50));
    }

    /**
     * Data class for network statistics.
     */
    public static class NetworkStats {
        private final int totalPapers;
        private final double avgRelevance;
        private final Map<String, Long> sourceStats;
        private final Map<RelationshipType, Long> relationshipStats;

        public NetworkStats(int totalPapers, double avgRelevance, 
                          Map<String, Long> sourceStats, Map<RelationshipType, Long> relationshipStats) {
            this.totalPapers = totalPapers;
            this.avgRelevance = avgRelevance;
            this.sourceStats = sourceStats;
            this.relationshipStats = relationshipStats;
        }

        public int getTotalPapers() { return totalPapers; }
        public double getAvgRelevance() { return avgRelevance; }
        public Map<String, Long> getSourceStats() { return sourceStats; }
        public Map<RelationshipType, Long> getRelationshipStats() { return relationshipStats; }
    }
}

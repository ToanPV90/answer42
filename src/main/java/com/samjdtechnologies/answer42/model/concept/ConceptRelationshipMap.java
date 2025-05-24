package com.samjdtechnologies.answer42.model.concept;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents relationships between different concepts in a paper.
 * Maps how concepts relate to each other through various relationship types.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptRelationshipMap {
    @Builder.Default
    private List<ConceptNode> nodes = new ArrayList<>();
    @Builder.Default
    private List<ConceptEdge> edges = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Add a concept node to the map.
     */
    public void addNode(ConceptNode node) {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        if (!nodes.contains(node)) {
            nodes.add(node);
        }
    }
    
    /**
     * Add a relationship edge between concepts.
     */
    public void addEdge(ConceptEdge edge) {
        if (edges == null) {
            edges = new ArrayList<>();
        }
        edges.add(edge);
    }
    
    /**
     * Find a node by concept name.
     */
    public ConceptNode findNode(String conceptName) {
        if (nodes == null) {
            return null;
        }
        
        return nodes.stream()
            .filter(node -> node.getConcept().equalsIgnoreCase(conceptName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get all edges for a specific concept.
     */
    public List<ConceptEdge> getEdgesForConcept(String conceptName) {
        if (edges == null) {
            return List.of();
        }
        
        return edges.stream()
            .filter(edge -> edge.getFromConcept().equalsIgnoreCase(conceptName) || 
                           edge.getToConcept().equalsIgnoreCase(conceptName))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all related concepts for a given concept.
     */
    public List<String> getRelatedConcepts(String conceptName) {
        return getEdgesForConcept(conceptName).stream()
            .map(edge -> edge.getFromConcept().equalsIgnoreCase(conceptName) ? 
                        edge.getToConcept() : edge.getFromConcept())
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Get concepts grouped by relationship type.
     */
    public Map<RelationshipType, List<ConceptEdge>> getEdgesByType() {
        if (edges == null) {
            return Map.of();
        }
        
        return edges.stream()
            .collect(Collectors.groupingBy(ConceptEdge::getType));
    }
    
    /**
     * Get summary statistics for the relationship map.
     */
    public Map<String, Object> getSummaryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", nodes != null ? nodes.size() : 0);
        stats.put("totalEdges", edges != null ? edges.size() : 0);
        
        if (edges != null && !edges.isEmpty()) {
            Map<RelationshipType, Long> typeCount = edges.stream()
                .collect(Collectors.groupingBy(ConceptEdge::getType, Collectors.counting()));
            stats.put("relationshipTypes", typeCount);
        }
        
        return stats;
    }
    
    /**
     * Check if the relationship map has sufficient connections.
     */
    public boolean hasSufficientConnections() {
        return nodes != null && edges != null && 
               nodes.size() >= 2 && edges.size() >= 1;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConceptNode {
        private String concept;
        private String description;
        @Builder.Default
        private double importance = 0.0;
        @Builder.Default
        private Map<String, Object> properties = new HashMap<>();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConceptEdge {
        private String fromConcept;
        private String toConcept;
        private RelationshipType type;
        private String description;
        @Builder.Default
        private double strength = 0.0;
        @Builder.Default
        private Map<String, Object> properties = new HashMap<>();
    }
    
    public enum RelationshipType {
        HIERARCHICAL("Parent-child relationship"),
        CAUSAL("Cause-effect relationship"),
        DEPENDENCY("Dependency relationship"),
        SIMILARITY("Similarity relationship"),
        OPPOSITION("Opposition relationship"),
        TEMPORAL("Temporal sequence relationship"),
        COMPONENT("Part-whole relationship");
        
        private final String description;
        
        RelationshipType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

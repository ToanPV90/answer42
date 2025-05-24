package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Paper Processor Agent - Extracts text and analyzes document structure.
 * Uses OpenAI GPT-4 for superior text structure recognition and parsing.
 */
@Component
public class PaperProcessorAgent extends OpenAIBasedAgent {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public PaperProcessorAgent(AIConfig aiConfig, ThreadConfig threadConfig) {
        super(aiConfig, threadConfig);
    }
    
    @Override
    public AgentType getAgentType() {
        return AgentType.PAPER_PROCESSOR;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        LoggingUtil.info(LOG, "processWithConfig", "Processing paper extraction for task %s", task.getId());
        
        try {
            // Extract paper ID and content from task input
            JsonNode input = task.getInput();
            String paperId = input.get("paperId").asText();
            String textContent = input.has("textContent") ? input.get("textContent").asText() : null;
            
            if (textContent == null || textContent.trim().isEmpty()) {
                return AgentResult.failure(task.getId(), "No text content provided for processing");
            }
            
            // Analyze document structure using OpenAI
            StructuredDocument structuredDoc = analyzeDocumentStructure(textContent, paperId);
            
            // Create result with structured data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("paperId", paperId);
            resultData.put("extractedText", structuredDoc.getCleanedText());
            resultData.put("structure", structuredDoc.getStructure());
            resultData.put("sections", structuredDoc.getSections());
            resultData.put("metadata", structuredDoc.getMetadata());
            resultData.put("processingNotes", structuredDoc.getProcessingNotes());
            
            return AgentResult.success(task.getId(), resultData);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", "Failed to process paper", e);
            return AgentResult.failure(task.getId(), "Paper processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Analyzes document structure using OpenAI GPT-4.
     */
    private StructuredDocument analyzeDocumentStructure(String textContent, String paperId) {
        LoggingUtil.info(LOG, "analyzeDocumentStructure", "Analyzing structure for paper %s", paperId);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("text", textContent);
        variables.put("paperId", paperId);
        
        String structurePrompt = """
            Analyze the following academic paper text and extract its structure:
            
            Paper ID: {paperId}
            Text: {text}
            
            Please identify and extract:
            1. Title and authors with affiliations
            2. Abstract (complete text)
            3. Main sections with clear boundaries:
               - Introduction
               - Methods/Methodology  
               - Results
               - Discussion
               - Conclusion
               - References
            4. Key findings and contributions
            5. Technical terms and concepts
            6. Figure and table references
            
            Return structured information that preserves the academic content while organizing it clearly.
            Focus on accurate content extraction and logical section identification.
            """;
        
        Prompt prompt = optimizePromptForOpenAI(structurePrompt, variables);
        ChatResponse response = executePrompt(prompt);
        
        // Extract content from response - using the correct Spring AI API
        String aiResponse = response.getResult().getOutput().toString();
        
        return parseStructureResponse(aiResponse, textContent, paperId);
    }
    
    /**
     * Parses the AI response into a structured document.
     */
    private StructuredDocument parseStructureResponse(String aiResponse, String originalText, String paperId) {
        try {
            StructuredDocument.Builder builder = StructuredDocument.builder()
                .paperId(paperId)
                .originalText(originalText)
                .cleanedText(cleanText(originalText));
            
            // Parse AI response to extract structure
            Map<String, Object> structure = parseAIStructureResponse(aiResponse);
            builder.structure(structure);
            
            // Extract sections
            Map<String, String> sections = extractSections(aiResponse, originalText);
            builder.sections(sections);
            
            // Extract metadata
            Map<String, Object> metadata = extractMetadata(aiResponse);
            builder.metadata(metadata);
            
            // Add processing notes
            builder.processingNotes("Processed with OpenAI GPT-4 for structure analysis");
            
            return builder.build();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseStructureResponse", "Failed to parse structure response", e);
            
            // Fallback: create basic structure
            return createFallbackStructure(originalText, paperId);
        }
    }
    
    /**
     * Cleans and normalizes text content.
     */
    private String cleanText(String text) {
        return text != null ? text.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("\\n{3,}", "\n\n") : "";
    }
    
    /**
     * Extracts sections from AI response and original text.
     */
    private Map<String, String> extractSections(String aiResponse, String originalText) {
        Map<String, String> sections = new HashMap<>();
        
        // Basic section extraction (this would be more sophisticated in practice)
        sections.put("abstract", extractSection(originalText, "abstract"));
        sections.put("introduction", extractSection(originalText, "introduction"));
        sections.put("methodology", extractSection(originalText, "method"));
        sections.put("results", extractSection(originalText, "results"));
        sections.put("discussion", extractSection(originalText, "discussion"));
        sections.put("conclusion", extractSection(originalText, "conclusion"));
        
        return sections;
    }
    
    /**
     * Basic section extraction helper.
     */
    private String extractSection(String text, String sectionName) {
        // Simplified section extraction - in practice would use more sophisticated parsing
        String lowerText = text.toLowerCase();
        int startIndex = lowerText.indexOf(sectionName);
        
        if (startIndex == -1) {
            return "";
        }
        
        // Extract a reasonable chunk (limiting to 2000 chars for this example)
        int endIndex = Math.min(startIndex + 2000, text.length());
        return text.substring(startIndex, endIndex).trim();
    }
    
    /**
     * Extracts metadata from AI response.
     */
    private Map<String, Object> extractMetadata(String aiResponse) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingTimestamp", System.currentTimeMillis());
        metadata.put("processor", "PaperProcessorAgent");
        metadata.put("aiProvider", "OpenAI");
        metadata.put("contentLength", aiResponse.length());
        return metadata;
    }
    
    /**
     * Parses AI structure response into organized data.
     */
    private Map<String, Object> parseAIStructureResponse(String aiResponse) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("hasTitle", aiResponse.toLowerCase().contains("title"));
        structure.put("hasAbstract", aiResponse.toLowerCase().contains("abstract"));
        structure.put("hasReferences", aiResponse.toLowerCase().contains("reference"));
        structure.put("structureQuality", "basic");
        return structure;
    }
    
    /**
     * Creates fallback structure when AI parsing fails.
     */
    private StructuredDocument createFallbackStructure(String originalText, String paperId) {
        Map<String, Object> basicStructure = new HashMap<>();
        basicStructure.put("type", "fallback");
        basicStructure.put("contentLength", originalText.length());
        
        return StructuredDocument.builder()
            .paperId(paperId)
            .originalText(originalText)
            .cleanedText(cleanText(originalText))
            .structure(basicStructure)
            .sections(new HashMap<>())
            .metadata(Map.of("processingMode", "fallback"))
            .processingNotes("Used fallback processing due to parsing errors")
            .build();
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        if (input != null && input.has("textContent")) {
            int contentLength = input.get("textContent").asText().length();
            // Base: 30 seconds + 1 second per 1000 characters
            long estimatedSeconds = 30 + (contentLength / 1000);
            return Duration.ofSeconds(Math.min(estimatedSeconds, 300)); // Cap at 5 minutes
        }
        return Duration.ofMinutes(2);
    }
    
    /**
     * Simple data class for structured document representation.
     */
    public static class StructuredDocument {
        private final String paperId;
        private final String originalText;
        private final String cleanedText;
        private final Map<String, Object> structure;
        private final Map<String, String> sections;
        private final Map<String, Object> metadata;
        private final String processingNotes;
        
        private StructuredDocument(Builder builder) {
            this.paperId = builder.paperId;
            this.originalText = builder.originalText;
            this.cleanedText = builder.cleanedText;
            this.structure = builder.structure;
            this.sections = builder.sections;
            this.metadata = builder.metadata;
            this.processingNotes = builder.processingNotes;
        }
        
        // Getters
        public String getPaperId() { return paperId; }
        public String getOriginalText() { return originalText; }
        public String getCleanedText() { return cleanedText; }
        public Map<String, Object> getStructure() { return structure; }
        public Map<String, String> getSections() { return sections; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getProcessingNotes() { return processingNotes; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String paperId;
            private String originalText;
            private String cleanedText;
            private Map<String, Object> structure = new HashMap<>();
            private Map<String, String> sections = new HashMap<>();
            private Map<String, Object> metadata = new HashMap<>();
            private String processingNotes;
            
            public Builder paperId(String paperId) { this.paperId = paperId; return this; }
            public Builder originalText(String originalText) { this.originalText = originalText; return this; }
            public Builder cleanedText(String cleanedText) { this.cleanedText = cleanedText; return this; }
            public Builder structure(Map<String, Object> structure) { this.structure = structure; return this; }
            public Builder sections(Map<String, String> sections) { this.sections = sections; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder processingNotes(String processingNotes) { this.processingNotes = processingNotes; return this; }
            
            public StructuredDocument build() { return new StructuredDocument(this); }
        }
    }
}

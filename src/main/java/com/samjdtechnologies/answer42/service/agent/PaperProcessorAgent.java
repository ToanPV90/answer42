package com.samjdtechnologies.answer42.service.agent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Paper Processor Agent - Extracts text and analyzes document structure.
 * Uses OpenAI GPT-4 for superior text structure recognition and parsing.
 */
@Component
public class PaperProcessorAgent extends OpenAIBasedAgent {
    
    
    public PaperProcessorAgent(AIConfig aiConfig, ThreadConfig threadConfig, 
                              AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
        super(aiConfig, threadConfig, retryPolicy, rateLimiter);
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
        
        try {
            // Create structured prompt for OpenAI using the optimized prompt method
            Map<String, Object> variables = new HashMap<>();
            variables.put("paperId", paperId);
            variables.put("textLength", textContent.length());
            variables.put("text", textContent.length() > 8000 ? textContent.substring(0, 8000) + "..." : textContent);
            
            String structurePromptTemplate = """
                Analyze the following academic paper text and extract its structure:
                
                Paper ID: {paperId}
                Text Length: {textLength} characters
                
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
            
            // Use the optimized prompt method from OpenAIBasedAgent
            Prompt prompt = optimizePromptForOpenAI(structurePromptTemplate, variables);
            
            // Use executePrompt method from AbstractConfigurableAgent (includes retry and rate limiting)
            ChatResponse response = executePrompt(prompt);
            
            // Extract content from response - using correct Spring AI API
            String aiResponse = response.getResult().getOutput().getText();
            
            LoggingUtil.info(LOG, "analyzeDocumentStructure", 
                "Received AI response for paper %s: %d characters", paperId, aiResponse.length());
            
            return parseStructureResponse(aiResponse, textContent, paperId);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "analyzeDocumentStructure", 
                "Failed to analyze structure for paper %s", e, paperId);
            
            // Return fallback structure on error
            return createFallbackStructure(textContent, paperId);
        }
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
        if (text == null) return "";
        
        return text.trim()
            .replaceAll("\\s+", " ")           // Collapse multiple spaces
            .replaceAll("\\n{3,}", "\n\n")     // Collapse multiple newlines
            .replaceAll("\\r", "")             // Remove carriage returns
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""); // Remove control chars
    }
    
    /**
     * Extracts sections from AI response and original text.
     */
    private Map<String, String> extractSections(String aiResponse, String originalText) {
        Map<String, String> sections = new HashMap<>();
        
        // Use both AI response and original text for section extraction
        String combinedText = aiResponse + "\n\n" + originalText;
        
        // Extract standard academic sections
        sections.put("abstract", extractSection(combinedText, "abstract"));
        sections.put("introduction", extractSection(combinedText, "introduction"));
        sections.put("methodology", extractSection(combinedText, "method"));
        sections.put("results", extractSection(combinedText, "results"));
        sections.put("discussion", extractSection(combinedText, "discussion"));
        sections.put("conclusion", extractSection(combinedText, "conclusion"));
        sections.put("references", extractSection(combinedText, "reference"));
        
        LoggingUtil.info(LOG, "extractSections", "Extracted %d sections", sections.size());
        
        return sections;
    }
    
    /**
     * Basic section extraction helper with improved logic.
     */
    private String extractSection(String text, String sectionName) {
        if (text == null || text.isEmpty()) return "";
        
        String lowerText = text.toLowerCase();
        String[] sectionVariants = {
            sectionName.toLowerCase(),
            sectionName.toLowerCase() + "s",  // plural
            sectionName.toLowerCase() + ":",   // with colon
            "\n" + sectionName.toLowerCase(),  // at line start
            " " + sectionName.toLowerCase()    // after space
        };
        
        int bestStartIndex = -1;
        for (String variant : sectionVariants) {
            int index = lowerText.indexOf(variant);
            if (index != -1) {
                bestStartIndex = index;
                break;
            }
        }
        
        if (bestStartIndex == -1) {
            return "";
        }
        
        // Extract a reasonable chunk (limiting to 3000 chars for better content)
        int endIndex = Math.min(bestStartIndex + 3000, text.length());
        
        // Try to find a natural break point (paragraph or section)
        String extracted = text.substring(bestStartIndex, endIndex);
        int lastParagraph = extracted.lastIndexOf("\n\n");
        if (lastParagraph > 500) { // Only use if it gives us a decent amount of content
            extracted = extracted.substring(0, lastParagraph);
        }
        
        return extracted.trim();
    }
    
    /**
     * Extracts metadata from AI response.
     */
    private Map<String, Object> extractMetadata(String aiResponse) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingTimestamp", System.currentTimeMillis());
        metadata.put("processor", "PaperProcessorAgent");
        metadata.put("aiProvider", getProvider().name());
        metadata.put("responseLength", aiResponse.length());
        metadata.put("processingMode", "ai_enhanced");
        
        // Extract quality indicators
        String lowerResponse = aiResponse.toLowerCase();
        metadata.put("hasTitle", lowerResponse.contains("title"));
        metadata.put("hasAbstract", lowerResponse.contains("abstract"));
        metadata.put("hasReferences", lowerResponse.contains("reference"));
        metadata.put("hasMethods", lowerResponse.contains("method"));
        metadata.put("hasResults", lowerResponse.contains("result"));
        
        return metadata;
    }
    
    /**
     * Parses AI structure response into organized data.
     */
    private Map<String, Object> parseAIStructureResponse(String aiResponse) {
        Map<String, Object> structure = new HashMap<>();
        String lowerResponse = aiResponse.toLowerCase();
        
        // Structure quality assessment
        int structureScore = 0;
        if (lowerResponse.contains("title")) structureScore += 20;
        if (lowerResponse.contains("abstract")) structureScore += 20;
        if (lowerResponse.contains("introduction")) structureScore += 15;
        if (lowerResponse.contains("method")) structureScore += 15;
        if (lowerResponse.contains("result")) structureScore += 15;
        if (lowerResponse.contains("conclusion")) structureScore += 15;
        
        structure.put("structureScore", structureScore);
        structure.put("qualityLevel", structureScore >= 80 ? "high" : 
                                      structureScore >= 60 ? "medium" : "low");
        
        // Component detection
        structure.put("hasTitle", lowerResponse.contains("title"));
        structure.put("hasAbstract", lowerResponse.contains("abstract"));
        structure.put("hasIntroduction", lowerResponse.contains("introduction"));
        structure.put("hasMethods", lowerResponse.contains("method"));
        structure.put("hasResults", lowerResponse.contains("result"));
        structure.put("hasDiscussion", lowerResponse.contains("discussion"));
        structure.put("hasConclusion", lowerResponse.contains("conclusion"));
        structure.put("hasReferences", lowerResponse.contains("reference"));
        
        // Content analysis
        structure.put("wordCount", aiResponse.split("\\s+").length);
        structure.put("hasEquations", lowerResponse.contains("equation") || aiResponse.contains("="));
        structure.put("hasFigures", lowerResponse.contains("figure") || lowerResponse.contains("fig."));
        structure.put("hasTables", lowerResponse.contains("table"));
        
        return structure;
    }
    
    /**
     * Creates fallback structure when AI parsing fails.
     */
    private StructuredDocument createFallbackStructure(String originalText, String paperId) {
        LoggingUtil.warn(LOG, "createFallbackStructure", 
            "Creating fallback structure for paper %s", paperId);
        
        Map<String, Object> basicStructure = new HashMap<>();
        basicStructure.put("type", "fallback");
        basicStructure.put("contentLength", originalText.length());
        basicStructure.put("processingMode", "fallback");
        basicStructure.put("structureScore", 0);
        basicStructure.put("qualityLevel", "fallback");
        
        // Basic content analysis even without AI
        String lowerText = originalText.toLowerCase();
        basicStructure.put("hasAbstract", lowerText.contains("abstract"));
        basicStructure.put("hasIntroduction", lowerText.contains("introduction"));
        basicStructure.put("hasReferences", lowerText.contains("references"));
        
        Map<String, Object> fallbackMetadata = new HashMap<>();
        fallbackMetadata.put("processingMode", "fallback");
        fallbackMetadata.put("processingTimestamp", System.currentTimeMillis());
        fallbackMetadata.put("processor", "PaperProcessorAgent");
        fallbackMetadata.put("fallbackReason", "AI processing failed");
        
        return StructuredDocument.builder()
            .paperId(paperId)
            .originalText(originalText)
            .cleanedText(cleanText(originalText))
            .structure(basicStructure)
            .sections(new HashMap<>())
            .metadata(fallbackMetadata)
            .processingNotes("Used fallback processing due to AI processing errors")
            .build();
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        JsonNode input = task.getInput();
        if (input != null && input.has("textContent")) {
            int contentLength = input.get("textContent").asText().length();
            // Base: 30 seconds + 1 second per 1000 characters, with realistic caps
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
            this.structure = builder.structure != null ? builder.structure : new HashMap<>();
            this.sections = builder.sections != null ? builder.sections : new HashMap<>();
            this.metadata = builder.metadata != null ? builder.metadata : new HashMap<>();
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

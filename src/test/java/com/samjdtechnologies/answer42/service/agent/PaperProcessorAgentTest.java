package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.PaperContentRepository;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.repository.PaperSectionRepository;
import com.samjdtechnologies.answer42.repository.PaperTagRepository;
import com.samjdtechnologies.answer42.repository.TagRepository;
import com.samjdtechnologies.answer42.service.agent.PaperProcessorAgent.StructuredDocument;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class PaperProcessorAgentTest {

    @Mock
    private AIConfig mockAiConfig;
    
    @Mock
    private ThreadConfig mockThreadConfig;
    
    @Mock
    private AgentRetryPolicy mockRetryPolicy;
    
    @Mock
    private APIRateLimiter mockRateLimiter;
    
    @Mock
    private ChatClient mockChatClient;
    
    @Mock
    private OpenAiApi mockOpenAiApi;
    
    @Mock
    private OpenAiChatModel mockOpenAiChatModel;
    
    @Mock
    private PaperRepository mockPaperRepository;
    
    @Mock
    private PaperContentRepository mockPaperContentRepository;
    
    @Mock
    private PaperSectionRepository mockPaperSectionRepository;
    
    @Mock
    private TagRepository mockTagRepository;
    
    @Mock
    private PaperTagRepository mockPaperTagRepository;

    private PaperProcessorAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.openAiApi()).thenReturn(mockOpenAiApi);
        when(mockAiConfig.openAiChatModel(mockOpenAiApi)).thenReturn(mockOpenAiChatModel);
        when(mockAiConfig.openAiChatClient(mockOpenAiChatModel)).thenReturn(mockChatClient);
        
        agent = new PaperProcessorAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter,
            mockPaperRepository, mockPaperContentRepository, 
            mockPaperSectionRepository, mockTagRepository, mockPaperTagRepository);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.PAPER_PROCESSOR, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.PAPER_PROCESSOR, agentType);
    }

    @Test
    void testEstimateProcessingTime_WithTextContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Content with 5000 characters
        String content = "A".repeat(5000);
        input.put("textContent", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 30s + content: 5000/1000 = 5s = 35s total
        assertEquals(Duration.ofSeconds(35), duration);
    }

    @Test
    void testEstimateProcessingTime_LargeContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Very large content (500,000 characters)
        String largeContent = "A".repeat(500000);
        input.put("textContent", largeContent);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Should be capped at maximum of 300 seconds (5 minutes)
        assertEquals(Duration.ofSeconds(300), duration);
    }

    @Test
    void testEstimateProcessingTime_NoTextContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-123");
        // No textContent
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Default: 2 minutes
        assertEquals(Duration.ofMinutes(2), duration);
    }

    @Test
    void testEstimateProcessingTime_NullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Default: 2 minutes
        assertEquals(Duration.ofMinutes(2), duration);
    }

    @Test
    void testProcessWithConfig_NoTextContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-123");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-123");
        // Missing textContent
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-123", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No text content provided"));
    }

    @Test
    void testProcessWithConfig_EmptyTextContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-456");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-456");
        input.put("textContent", "");
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-456", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No text content provided"));
    }

    @Test
    void testProcessWithConfig_WhitespaceTextContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-789");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-789");
        input.put("textContent", "   \n\t   ");
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-789", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No text content provided"));
    }

    @Test
    void testInheritedBehaviorFromOpenAIBasedAgent() {
        // Test that it properly inherits from OpenAIBasedAgent
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.PAPER_PROCESSOR, agent.getAgentType());
        
        // Test that it can get configured chat client
        ChatClient chatClient = agent.getConfiguredChatClient();
        assertNotNull(chatClient);
        assertEquals(mockChatClient, chatClient);
        
        // Verify mock interactions
        verify(mockAiConfig).openAiApi();
        verify(mockAiConfig).openAiChatModel(mockOpenAiApi);
        verify(mockAiConfig).openAiChatClient(mockOpenAiChatModel);
    }

    @Test
    void testEstimateProcessingTime_SmallContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Small content (500 characters)
        String content = "A".repeat(500);
        input.put("textContent", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 30s + content: 500/1000 = 0s = 30s total
        assertEquals(Duration.ofSeconds(30), duration);
    }

    @Test
    void testEstimateProcessingTime_MediumContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Medium content (10,000 characters)
        String content = "A".repeat(10000);
        input.put("textContent", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 30s + content: 10000/1000 = 10s = 40s total
        assertEquals(Duration.ofSeconds(40), duration);
    }

    // StructuredDocument Tests
    @Test
    void testStructuredDocumentBuilder() {
        StructuredDocument.Builder builder = StructuredDocument.builder();
        
        assertNotNull(builder);
    }

    @Test
    void testStructuredDocumentBuilderWithData() {
        Map<String, Object> structure = new HashMap<>();
        structure.put("hasTitle", true);
        structure.put("structureScore", 85);
        
        Map<String, String> sections = new HashMap<>();
        sections.put("abstract", "This is the abstract section");
        sections.put("introduction", "This is the introduction section");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingMode", "ai_enhanced");
        metadata.put("qualityLevel", "high");
        
        StructuredDocument doc = StructuredDocument.builder()
            .paperId("paper-123")
            .originalText("Original paper text content")
            .cleanedText("Cleaned paper text content")
            .structure(structure)
            .sections(sections)
            .metadata(metadata)
            .processingNotes("Processed successfully with OpenAI")
            .build();
        
        assertNotNull(doc);
        assertEquals("paper-123", doc.getPaperId());
        assertEquals("Original paper text content", doc.getOriginalText());
        assertEquals("Cleaned paper text content", doc.getCleanedText());
        assertEquals(structure, doc.getStructure());
        assertEquals(sections, doc.getSections());
        assertEquals(metadata, doc.getMetadata());
        assertEquals("Processed successfully with OpenAI", doc.getProcessingNotes());
    }

    @Test
    void testStructuredDocumentBuilderWithNullValues() {
        StructuredDocument doc = StructuredDocument.builder()
            .paperId("paper-456")
            .originalText("Some text")
            .cleanedText("Some clean text")
            .structure(null)
            .sections(null)
            .metadata(null)
            .processingNotes("Some notes")
            .build();
        
        assertNotNull(doc);
        assertEquals("paper-456", doc.getPaperId());
        assertEquals("Some text", doc.getOriginalText());
        assertEquals("Some clean text", doc.getCleanedText());
        assertNotNull(doc.getStructure()); // Should be empty HashMap, not null
        assertNotNull(doc.getSections()); // Should be empty HashMap, not null
        assertNotNull(doc.getMetadata()); // Should be empty HashMap, not null
        assertEquals("Some notes", doc.getProcessingNotes());
    }

    @Test
    void testStructuredDocumentBuilderMinimal() {
        StructuredDocument doc = StructuredDocument.builder()
            .paperId("minimal-paper")
            .build();
        
        assertNotNull(doc);
        assertEquals("minimal-paper", doc.getPaperId());
        assertNull(doc.getOriginalText());
        assertNull(doc.getCleanedText());
        assertNotNull(doc.getStructure());
        assertNotNull(doc.getSections());
        assertNotNull(doc.getMetadata());
        assertNull(doc.getProcessingNotes());
    }

    @Test
    void testStructuredDocumentBuilderChaining() {
        StructuredDocument.Builder builder = StructuredDocument.builder();
        
        // Test that all builder methods return the builder for chaining
        StructuredDocument.Builder result = builder
            .paperId("chain-test")
            .originalText("original")
            .cleanedText("cleaned")
            .structure(new HashMap<>())
            .sections(new HashMap<>())
            .metadata(new HashMap<>())
            .processingNotes("notes");
        
        assertSame(builder, result); // Should be the same instance for method chaining
        
        StructuredDocument doc = result.build();
        assertNotNull(doc);
        assertEquals("chain-test", doc.getPaperId());
    }
}

package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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
import com.samjdtechnologies.answer42.service.agent.MetadataEnhancementAgent.EnhancementResult;
import com.samjdtechnologies.answer42.service.agent.MetadataEnhancementAgent.MetadataSource;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class MetadataEnhancementAgentTest {

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
    private ChatClient.ChatClientRequestSpec mockRequestSpec;
    
    @Mock
    private ChatClient.CallResponseSpec mockCallResponseSpec;
    
    @Mock
    private com.samjdtechnologies.answer42.repository.MetadataVerificationRepository mockMetadataVerificationRepository;

    private MetadataEnhancementAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.openAiApi()).thenReturn(mockOpenAiApi);
        when(mockAiConfig.openAiChatModel(mockOpenAiApi)).thenReturn(mockOpenAiChatModel);
        when(mockAiConfig.openAiChatClient(mockOpenAiChatModel)).thenReturn(mockChatClient);
        
        agent = new MetadataEnhancementAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter, mockMetadataVerificationRepository);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.METADATA_ENHANCER, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.METADATA_ENHANCER, agentType);
    }

    @Test
    void testInheritedBehaviorFromOpenAIBasedAgent() {
        // Test that it properly inherits from OpenAIBasedAgent
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.METADATA_ENHANCER, agent.getAgentType());
        
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
    void testEstimateProcessingTime() {
        AgentTask task = new AgentTask();
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Expected: 45 + (4 * 20) = 125 seconds
        assertEquals(Duration.ofSeconds(125), duration);
    }

    @Test
    void testProcessWithConfig_MissingTitle() {
        AgentTask task = new AgentTask();
        task.setId("test-task-no-title");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-123");
        // No title provided
        task.setInput(input);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-no-title", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No title provided"));
    }

    @Test
    void testProcessWithConfig_EmptyTitle() {
        AgentTask task = new AgentTask();
        task.setId("test-task-empty-title");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-123");
        input.put("title", "   "); // Empty/whitespace title
        task.setInput(input);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-empty-title", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No title provided"));
    }

    @Test
    void testProcessWithConfig_SuccessfulEnhancement() {
        AgentTask task = new AgentTask();
        task.setId("test-task-success");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-123");
        input.put("title", "Machine Learning in Academic Research");
        input.put("doi", "10.1234/example.doi");
        task.setInput(input);

        // Mock the AI response
        String mockAiResponse = """
            {
                "title": "Machine Learning in Academic Research",
                "doi": "10.1234/example.doi",
                "year": 2023,
                "journal": "Journal of AI Research",
                "publisher": "Academic Press",
                "volume": "42",
                "issue": "3",
                "pages": "123-145",
                "authors": ["Dr. Jane Smith", "Prof. John Doe"],
                "citationCount": 42,
                "publicationType": "journal-article",
                "confidence": {
                    "title": 0.95,
                    "doi": 0.98,
                    "year": 0.90,
                    "journal": 0.85,
                    "overall": 0.92
                },
                "conflicts": [],
                "sources": ["crossref", "semantic_scholar"]
            }
            """;

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-success", result.getTaskId());
        
        // Verify result data structure
        assertTrue(result.getResultData() instanceof Map);
        Map<String, Object> resultData = (Map<String, Object>) result.getResultData();
        
        assertEquals("paper-123", resultData.get("paperId"));
        assertTrue(resultData.containsKey("enhancedMetadata"));
        assertTrue(resultData.containsKey("sources"));
        assertTrue(resultData.containsKey("confidence"));
        assertTrue(resultData.containsKey("conflicts"));
        assertTrue(resultData.containsKey("processingNotes"));
        
        // Verify confidence is reasonable
        Double confidence = (Double) resultData.get("confidence");
        assertNotNull(confidence);
        assertTrue(confidence >= 0.0 && confidence <= 1.0);
    }

    @Test
    void testProcessWithConfig_WithAuthors() {
        AgentTask task = new AgentTask();
        task.setId("test-task-with-authors");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-456");
        input.put("title", "Deep Learning Applications");
        input.put("doi", "10.5678/deep.learning");
        
        // Add authors array
        input.putArray("authors")
            .add("Dr. Alice Johnson")
            .add("Prof. Bob Wilson");
        
        task.setInput(input);

        // Mock AI response with author information
        String mockAiResponse = """
            {
                "title": "Deep Learning Applications",
                "authors": ["Dr. Alice Johnson", "Prof. Bob Wilson"],
                "authorCount": 2,
                "confidence": {"overall": 0.88}
            }
            """;

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-with-authors", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_NoDOI() {
        AgentTask task = new AgentTask();
        task.setId("test-task-no-doi");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-789");
        input.put("title", "Research Without DOI");
        // No DOI provided
        task.setInput(input);

        String mockAiResponse = """
            {
                "title": "Research Without DOI",
                "confidence": {"overall": 0.75}
            }
            """;

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-no-doi", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_AIFailure() {
        AgentTask task = new AgentTask();
        task.setId("test-task-ai-failure");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-error");
        input.put("title", "Test Paper");
        task.setInput(input);

        // Mock AI failure
        when(mockChatClient.prompt(any(Prompt.class)))
            .thenThrow(new RuntimeException("AI service unavailable"));

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-ai-failure", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("Metadata enhancement failed"));
    }

    @Test
    void testProcessWithConfig_InvalidAIResponse() {
        AgentTask task = new AgentTask();
        task.setId("test-task-invalid-response");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-invalid");
        input.put("title", "Test Paper with Invalid Response");
        task.setInput(input);

        // Mock invalid AI response
        String invalidResponse = "This is not valid JSON or structured data";

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(invalidResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess()); // Should still succeed with fallback parsing
        assertEquals("test-task-invalid-response", result.getTaskId());
        
        Map<String, Object> resultData = (Map<String, Object>) result.getResultData();
        assertNotNull(resultData.get("enhancedMetadata"));
    }

    @Test
    void testMetadataSource() {
        String name = "test-source";
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Test Title");
        data.put("confidence", 0.85);
        double confidence = 0.85;

        MetadataSource source = new MetadataSource(name, data, confidence);

        assertEquals(name, source.getName());
        assertEquals(data, source.getData());
        assertEquals(confidence, source.getConfidence(), 0.001);
        
        String description = source.getDetailedDescription();
        assertNotNull(description);
        assertTrue(description.contains(name));
        assertTrue(description.contains("0.85"));
    }

    @Test
    void testEnhancementResultBuilder() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Test Paper");
        
        List<MetadataSource> sources = List.of(
            new MetadataSource("test-source", metadata, 0.9)
        );
        
        double confidence = 0.85;
        List<String> conflicts = List.of("Minor date discrepancy");
        String notes = "Test processing notes";

        EnhancementResult result = EnhancementResult.builder()
            .metadata(metadata)
            .sources(sources)
            .confidence(confidence)
            .conflicts(conflicts)
            .processingNotes(notes)
            .build();

        assertNotNull(result);
        assertEquals(metadata, result.getMetadata());
        assertEquals(sources, result.getSources());
        assertEquals(confidence, result.getConfidence(), 0.001);
        assertEquals(conflicts, result.getConflicts());
        assertEquals(notes, result.getProcessingNotes());
    }

    @Test
    void testEnhancementResultBuilderDefaults() {
        EnhancementResult result = EnhancementResult.builder().build();

        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().isEmpty());
        assertNotNull(result.getSources());
        assertTrue(result.getSources().isEmpty());
        assertEquals(0.0, result.getConfidence(), 0.001);
        assertNotNull(result.getConflicts());
        assertTrue(result.getConflicts().isEmpty());
        assertEquals("", result.getProcessingNotes());
    }

    @Test
    void testProcessWithConfig_RegexParsing() {
        AgentTask task = new AgentTask();
        task.setId("test-task-regex-parsing");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-regex");
        input.put("title", "Test Paper for Regex Parsing");
        task.setInput(input);

        // Mock AI response that should be parsed with regex patterns
        String mockAiResponse = """
            Title: Test Paper for Regex Parsing
            DOI: 10.1234/test.regex.parsing
            Year: 2023
            Journal: Test Journal of Regex
            Publisher: Regex Publishing House
            Citation Count: 25
            Author Count: 3
            Confidence: 0.88
            """;

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-regex-parsing", result.getTaskId());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) result.getResultData();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> enhancedMetadata = (Map<String, Object>) resultData.get("enhancedMetadata");
        assertNotNull(enhancedMetadata);
    }

    @Test
    void testProcessWithConfig_StructuredTextParsing() {
        AgentTask task = new AgentTask();
        task.setId("test-task-structured-parsing");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-structured");
        input.put("title", "Test Paper for Structured Parsing");
        task.setInput(input);

        // Mock AI response in structured text format
        String mockAiResponse = """
            title: Test Paper for Structured Parsing
            year: 2023
            journal: Structured Journal
            citationcount: 15
            confidence: 0.82
            """;

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-structured-parsing", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_FallbackResult() {
        AgentTask task = new AgentTask();
        task.setId("test-task-fallback");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-fallback");
        input.put("title", "Test Paper for Fallback");
        task.setInput(input);

        // Mock AI response that will cause parsing to fail and use fallback
        String mockAiResponse = "Completely unparseable response with no structure at all";

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-fallback", result.getTaskId());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) result.getResultData();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> enhancedMetadata = (Map<String, Object>) resultData.get("enhancedMetadata");
        assertNotNull(enhancedMetadata);
        
        // Should have fallback indicators
        assertTrue(enhancedMetadata.containsKey("enhanced"));
        assertTrue(enhancedMetadata.containsKey("parseStrategy"));
    }

    @Test
    void testProcessWithConfig_WithConflicts() {
        AgentTask task = new AgentTask();
        task.setId("test-task-conflicts");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-conflicts");
        input.put("title", "Test Paper with Conflicts");
        task.setInput(input);

        // Mock AI response with conflicts
        String mockAiResponse = """
            {
                "title": "Test Paper with Conflicts",
                "conflicts": ["Year mismatch between sources", "Publisher name variations"],
                "confidence": {"overall": 0.70}
            }
            """;

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn(mockAiResponse);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-conflicts", result.getTaskId());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) result.getResultData();
        
        @SuppressWarnings("unchecked")
        List<String> conflicts = (List<String>) resultData.get("conflicts");
        assertNotNull(conflicts);
        assertTrue(conflicts.size() > 0);
    }
}

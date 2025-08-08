package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
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
import com.samjdtechnologies.answer42.model.concept.ConceptExplanation;
import com.samjdtechnologies.answer42.model.concept.ConceptExplanationResult;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap;
import com.samjdtechnologies.answer42.model.concept.TechnicalTerm;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.EducationLevel;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.util.ConceptResponseParser;

public class ConceptExplainerAgentTest {

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
    private ConceptResponseParser mockResponseParser;
    
    @Mock
    private com.samjdtechnologies.answer42.repository.PaperRepository mockPaperRepository;
    
    @Mock
    private com.samjdtechnologies.answer42.repository.TagRepository mockTagRepository;
    
    @Mock
    private com.samjdtechnologies.answer42.repository.PaperTagRepository mockPaperTagRepository;

    private ConceptExplainerAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.openAiApi()).thenReturn(mockOpenAiApi);
        when(mockAiConfig.openAiChatModel(mockOpenAiApi)).thenReturn(mockOpenAiChatModel);
        when(mockAiConfig.openAiChatClient(mockOpenAiChatModel)).thenReturn(mockChatClient);
        
        agent = new ConceptExplainerAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter, mockResponseParser,
            mockPaperRepository, mockTagRepository, mockPaperTagRepository);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.CONCEPT_EXPLAINER, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.CONCEPT_EXPLAINER, agentType);
    }

    @Test
    void testEstimateProcessingTime_WithContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Content with approximately 1000 characters (5 expected terms)
        String content = "A".repeat(1000);
        input.put("content", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + terms: 5*4*15s + relationship: 30s = 390s
        assertEquals(Duration.ofSeconds(390), duration);
    }

    @Test
    void testEstimateProcessingTime_WithLargeContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Large content with approximately 10,000 characters (20 expected terms - max)
        String content = "A".repeat(10000);
        input.put("content", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + terms: 20*4*15s + relationship: 30s = 1290s
        assertEquals(Duration.ofSeconds(1290), duration);
    }

    @Test
    void testEstimateProcessingTime_WithNoContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Default: 2 minutes
        assertEquals(Duration.ofMinutes(2), duration);
    }

    @Test
    void testEstimateProcessingTime_WithNullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Default: 2 minutes
        assertEquals(Duration.ofMinutes(2), duration);
    }

    @Test
    void testProcessWithConfig_NoContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-123");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-123");
        // Missing content
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-123", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No content provided"));
    }

    @Test
    void testProcessWithConfig_EmptyContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-456");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-456");
        input.put("content", "");
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-456", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No content provided"));
    }

    @Test
    void testProcessWithConfig_WithValidContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-789");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-789");
        input.put("content", "This paper discusses neural networks and machine learning algorithms.");
        task.setInput(input);
        
        // Mock the response parser to return expected technical terms
        List<TechnicalTerm> mockTerms = List.of(
            TechnicalTerm.builder()
                .term("neural networks")
                .complexity(0.8)
                .context("neural networks and machine learning")
                .build(),
            TechnicalTerm.builder()
                .term("machine learning")
                .complexity(0.7)
                .context("neural networks and machine learning algorithms")
                .build()
        );
        
        when(mockResponseParser.parseTermsFromResponse(anyString())).thenReturn(mockTerms);
        
        // Mock explanations for different education levels
        Map<String, ConceptExplanation> mockExplanations = Map.of(
            "neural networks", ConceptExplanation.builder()
                .definition("Networks that mimic brain neurons")
                .analogy("Like interconnected brain cells")
                .importance("Core to AI systems")
                .confidence(0.9)
                .build()
        );
        
        when(mockResponseParser.parseExplanationsFromResponse(anyString(), any(EducationLevel.class)))
            .thenReturn(mockExplanations);
        
        // Mock relationship map
        ConceptRelationshipMap mockRelationshipMap = ConceptRelationshipMap.builder().build();
        when(mockResponseParser.parseRelationshipMapFromResponse(anyString()))
            .thenReturn(mockRelationshipMap);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-789", result.getTaskId());
        assertInstanceOf(ConceptExplanationResult.class, result.getResultData().get("result"));
        
        // Verify parser was called
        verify(mockResponseParser, atLeastOnce()).parseTermsFromResponse(anyString());
        verify(mockResponseParser, atLeastOnce()).parseExplanationsFromResponse(anyString(), any(EducationLevel.class));
        verify(mockResponseParser, atLeastOnce()).parseRelationshipMapFromResponse(anyString());
    }

    @Test
    void testInheritedBehaviorFromOpenAIBasedAgent() {
        // Test that it properly inherits from OpenAIBasedAgent
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.CONCEPT_EXPLAINER, agent.getAgentType());
        
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
        
        // Small content (100 characters = 0 terms, minimum 5)
        String content = "A".repeat(100);
        input.put("content", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + terms: 0*4*15s + relationship: 30s = 90s
        assertEquals(Duration.ofSeconds(90), duration);
    }

    @Test
    void testEstimateProcessingTime_MediumContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Medium content (2000 characters = 10 expected terms)
        String content = "A".repeat(2000);
        input.put("content", content);
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + terms: 10*4*15s + relationship: 30s = 690s
        assertEquals(Duration.ofSeconds(690), duration);
    }

    @Test
    void testProcessWithConfig_WithOnlyPaperId() {
        AgentTask task = new AgentTask();
        task.setId("test-task-only-paper");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-only");
        // No content field
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-only-paper", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No content provided"));
    }

    @Test
    void testProcessWithConfig_WithWhitespaceContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-whitespace");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "paper-whitespace");
        input.put("content", "   \n\t   ");
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-whitespace", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No content provided"));
    }
}

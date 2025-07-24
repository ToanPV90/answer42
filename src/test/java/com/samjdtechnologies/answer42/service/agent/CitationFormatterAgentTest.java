package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

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
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class CitationFormatterAgentTest {

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

    private CitationFormatterAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.openAiApi()).thenReturn(mockOpenAiApi);
        when(mockAiConfig.openAiChatModel(mockOpenAiApi)).thenReturn(mockOpenAiChatModel);
        when(mockAiConfig.openAiChatClient(mockOpenAiChatModel)).thenReturn(mockChatClient);
        
        agent = new CitationFormatterAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.CITATION_FORMATTER, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.CITATION_FORMATTER, agentType);
    }

    @Test
    void testEstimateProcessingTime_WithDocumentContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Create document content with approximately 5000 characters (5 expected citations)
        String documentContent = "A".repeat(5000);
        input.put("documentContent", documentContent);
        input.put("citationStyles", "APA,MLA");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + citations: 5*10s + styles: 2*30s = 170s
        assertEquals(Duration.ofSeconds(170), duration);
    }

    @Test
    void testEstimateProcessingTime_WithDefaultValues() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "12345");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + default citations: 20*10s + default style: 1*30s = 290s
        assertEquals(Duration.ofSeconds(290), duration);
    }

    @Test
    void testEstimateProcessingTime_LargeDocument() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        
        // Create very large document content (50,000 characters = 50 expected citations)
        String largeContent = "A".repeat(50000);
        input.put("documentContent", largeContent);
        input.put("citationStyles", "APA,MLA,Chicago");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + citations: 50*10s + styles: 3*30s = 650s
        assertEquals(Duration.ofSeconds(650), duration);
    }

    @Test
    void testEstimateProcessingTime_MultipleCitationStyles() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("documentContent", "Small document");
        input.put("citationStyles", "APA,MLA,Chicago,IEEE,Harvard");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + citations: 5*10s (minimum) + styles: 5*30s = 260s
        assertEquals(Duration.ofSeconds(260), duration);
    }

    @Test
    void testEstimateProcessingTime_EmptyDocumentContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("documentContent", "");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + citations: 5*10s (minimum) + styles: 1*30s = 140s
        assertEquals(Duration.ofSeconds(140), duration);
    }

    @Test
    void testProcessWithConfig_NoDocumentContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-456");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        // Missing documentContent and paperId
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-456", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No document content provided"));
    }

    @Test
    void testInheritedBehaviorFromOpenAIBasedAgent() {
        // Test that it properly inherits from OpenAIBasedAgent
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.CITATION_FORMATTER, agent.getAgentType());
        
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
    void testEstimateProcessingTime_WithNullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);
        
        // Should handle null input gracefully and use defaults
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Should use default values when input is null
        assertTrue(duration.getSeconds() > 0);
    }

    @Test
    void testEstimateProcessingTime_WithEmptyInput() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + default citations: 20*10s + default style: 1*30s = 290s
        assertEquals(Duration.ofSeconds(290), duration);
    }

    @Test
    void testEstimateProcessingTime_WithSingleCitationStyle() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("documentContent", "Medium document content");
        input.put("citationStyles", "APA");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + citations: 5*10s (minimum) + styles: 1*30s = 140s
        assertEquals(Duration.ofSeconds(140), duration);
    }

    @Test
    void testEstimateProcessingTime_VerySmallDocument() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("documentContent", "A"); // 1 character
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + citations: 5*10s (minimum even for tiny docs) + styles: 1*30s = 140s
        assertEquals(Duration.ofSeconds(140), duration);
    }
}

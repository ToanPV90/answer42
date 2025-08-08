package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;

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
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.repository.SummaryRepository;

public class ContentSummarizerAgentTest {

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
    private AnthropicApi mockAnthropicApi;
    
    @Mock
    private AnthropicChatModel mockAnthropicChatModel;
    
    @Mock
    private PaperRepository mockPaperRepository;
    
    @Mock
    private SummaryRepository mockSummaryRepository;

    private ContentSummarizerAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.anthropicApi()).thenReturn(mockAnthropicApi);
        when(mockAiConfig.anthropicChatModel(mockAnthropicApi)).thenReturn(mockAnthropicChatModel);
        when(mockAiConfig.anthropicChatClient(mockAnthropicChatModel)).thenReturn(mockChatClient);
        
        agent = new ContentSummarizerAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter, mockPaperRepository, mockSummaryRepository);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.ANTHROPIC, agent.getProvider());
        assertEquals(AgentType.CONTENT_SUMMARIZER, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.CONTENT_SUMMARIZER, agentType);
    }

    @Test
    void testEstimateProcessingTime_BriefSummary() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(2000)); // 2000 characters
        input.put("summaryType", "brief");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 30s + content: 2000/2000 = 1s = 31s total
        assertEquals(Duration.ofSeconds(31), duration);
    }

    @Test
    void testEstimateProcessingTime_StandardSummary() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(4000)); // 4000 characters
        input.put("summaryType", "standard");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s + content: 4000/2000 = 2s = 62s total
        assertEquals(Duration.ofSeconds(62), duration);
    }

    @Test
    void testEstimateProcessingTime_DetailedSummary() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(6000)); // 6000 characters
        input.put("summaryType", "detailed");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + content: 6000/2000 = 3s = 123s total
        assertEquals(Duration.ofSeconds(123), duration);
    }

    @Test
    void testEstimateProcessingTime_DefaultSummaryType() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(1000)); // 1000 characters
        // No summaryType specified, should default to "standard"
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 60s (standard) + content: 1000/2000 = 0s = 60s total
        assertEquals(Duration.ofSeconds(60), duration);
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
        // Default: 3 minutes
        assertEquals(Duration.ofMinutes(3), duration);
    }

    @Test
    void testEstimateProcessingTime_NullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Default: 3 minutes
        assertEquals(Duration.ofMinutes(3), duration);
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
    void testInheritedBehaviorFromAnthropicBasedAgent() {
        // Test that it properly inherits from AnthropicBasedAgent
        assertEquals(AIProvider.ANTHROPIC, agent.getProvider());
        assertEquals(AgentType.CONTENT_SUMMARIZER, agent.getAgentType());
        
        // Test that it can get configured chat client
        ChatClient chatClient = agent.getConfiguredChatClient();
        assertNotNull(chatClient);
        assertEquals(mockChatClient, chatClient);
        
        // Verify mock interactions
        verify(mockAiConfig).anthropicApi();
        verify(mockAiConfig).anthropicChatModel(mockAnthropicApi);
        verify(mockAiConfig).anthropicChatClient(mockAnthropicChatModel);
    }

    @Test
    void testEstimateProcessingTime_UnknownSummaryType() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(2000));
        input.put("summaryType", "unknown");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Should default to "standard" behavior
        // Base: 60s + content: 2000/2000 = 1s = 61s total
        assertEquals(Duration.ofSeconds(61), duration);
    }

    @Test
    void testEstimateProcessingTime_LargeContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(20000)); // Very large content
        input.put("summaryType", "detailed");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + content: 20000/2000 = 10s = 130s total
        assertEquals(Duration.ofSeconds(130), duration);
    }

    @Test
    void testEstimateProcessingTime_VerySmallContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "Small text");
        input.put("summaryType", "brief");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 30s + content: 10/2000 = 0s = 30s total
        assertEquals(Duration.ofSeconds(30), duration);
    }

    @Test
    void testEstimateProcessingTime_CaseInsensitiveSummaryType() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("textContent", "A".repeat(1000));
        input.put("summaryType", "DETAILED"); // Upper case
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + content: 1000/2000 = 0s = 120s total
        assertEquals(Duration.ofSeconds(120), duration);
    }
}

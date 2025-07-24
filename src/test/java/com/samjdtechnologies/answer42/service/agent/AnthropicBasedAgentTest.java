package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class AnthropicBasedAgentTest {

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

    private TestableAnthropicBasedAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.anthropicApi()).thenReturn(mockAnthropicApi);
        when(mockAiConfig.anthropicChatModel(mockAnthropicApi)).thenReturn(mockAnthropicChatModel);
        when(mockAiConfig.anthropicChatClient(mockAnthropicChatModel)).thenReturn(mockChatClient);
        
        agent = new TestableAnthropicBasedAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter);
    }

    @Test
    void testGetProvider() {
        AIProvider provider = agent.getProvider();
        
        assertEquals(AIProvider.ANTHROPIC, provider);
    }

    @Test
    void testGetConfiguredChatClient() {
        ChatClient chatClient = agent.getConfiguredChatClient();
        
        assertNotNull(chatClient);
        assertEquals(mockChatClient, chatClient);
        verify(mockAiConfig).anthropicApi();
        verify(mockAiConfig).anthropicChatModel(mockAnthropicApi);
        verify(mockAiConfig).anthropicChatClient(mockAnthropicChatModel);
    }

    @Test
    void testOptimizePromptForAnthropic() {
        String basePrompt = "Analyze this text";
        Map<String, Object> variables = new HashMap<>();
        variables.put("text", "sample text");
        
        Prompt prompt = agent.optimizePromptForAnthropic(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Analyze this text"));
        assertTrue(promptContent.contains("think step by step"));
        assertTrue(promptContent.contains("detailed reasoning"));
    }

    @Test
    void testOptimizePromptForAnthropic_WithNullVariables() {
        String basePrompt = "Analyze this text";
        
        Prompt prompt = agent.optimizePromptForAnthropic(basePrompt, null);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Analyze this text"));
        assertTrue(promptContent.contains("think step by step"));
    }

    @Test
    void testCreateQualityPrompt() {
        String basePrompt = "Evaluate the quality of this content";
        Map<String, Object> variables = new HashMap<>();
        variables.put("content", "sample content");
        
        Prompt prompt = agent.createQualityPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Evaluate the quality"));
        assertTrue(promptContent.contains("Accuracy verification"));
        assertTrue(promptContent.contains("Completeness evaluation"));
        assertTrue(promptContent.contains("Consistency check"));
        assertTrue(promptContent.contains("quality score (1-10)"));
        assertTrue(promptContent.contains("recommendations for improvement"));
    }

    @Test
    void testCreateQualityPrompt_WithEmptyVariables() {
        String basePrompt = "Evaluate the quality";
        Map<String, Object> variables = new HashMap<>();
        
        Prompt prompt = agent.createQualityPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Evaluate the quality"));
        assertTrue(promptContent.contains("thorough quality assessment"));
    }

    @Test
    void testCreateSummaryPrompt() {
        String basePrompt = "Summarize this document";
        Map<String, Object> variables = new HashMap<>();
        variables.put("document", "sample document");
        
        Prompt prompt = agent.createSummaryPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Summarize this document"));
        assertTrue(promptContent.contains("comprehensive summary"));
        assertTrue(promptContent.contains("main points accurately"));
        assertTrue(promptContent.contains("appropriate length"));
        assertTrue(promptContent.contains("clear, accessible language"));
        assertTrue(promptContent.contains("important context and nuance"));
    }

    @Test
    void testCreateSummaryPrompt_WithNullVariables() {
        String basePrompt = "Summarize this content";
        
        Prompt prompt = agent.createSummaryPrompt(basePrompt, null);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Summarize this content"));
        assertTrue(promptContent.contains("comprehensive summary"));
    }

    @Test
    void testInheritedBehaviorFromAbstractConfigurableAgent() {
        // Test that it properly inherits from AbstractConfigurableAgent
        assertEquals(AIProvider.ANTHROPIC, agent.getProvider());
        assertEquals(AgentType.CONTENT_SUMMARIZER, agent.getAgentType());
    }

    // Test implementation of AnthropicBasedAgent for testing purposes
    private static class TestableAnthropicBasedAgent extends AnthropicBasedAgent {

        public TestableAnthropicBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                                         AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
            super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        }

        @Override
        protected AgentResult processWithConfig(AgentTask task) {
            return AgentResult.success(task.getId(), "test result");
        }

        @Override
        public AgentType getAgentType() {
            return AgentType.CONTENT_SUMMARIZER;
        }
    }
}

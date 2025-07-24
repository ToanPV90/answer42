package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class PerplexityBasedAgentTest {

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
    private OpenAiApi mockPerplexityApi;
    
    @Mock
    private OpenAiChatModel mockPerplexityChatModel;

    private TestablePerplexityBasedAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.perplexityApi()).thenReturn(mockPerplexityApi);
        when(mockAiConfig.perplexityChatModel(mockPerplexityApi)).thenReturn(mockPerplexityChatModel);
        when(mockAiConfig.perplexityChatClient(mockPerplexityChatModel)).thenReturn(mockChatClient);
        
        agent = new TestablePerplexityBasedAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter);
    }

    @Test
    void testGetProvider() {
        AIProvider provider = agent.getProvider();
        
        assertEquals(AIProvider.PERPLEXITY, provider);
    }

    @Test
    void testGetConfiguredChatClient() {
        ChatClient chatClient = agent.getConfiguredChatClient();
        
        assertNotNull(chatClient);
        assertEquals(mockChatClient, chatClient);
        verify(mockAiConfig).perplexityApi();
        verify(mockAiConfig).perplexityChatModel(mockPerplexityApi);
        verify(mockAiConfig).perplexityChatClient(mockPerplexityChatModel);
    }

    @Test
    void testOptimizePromptForPerplexity() {
        String basePrompt = "Research this topic";
        Map<String, Object> variables = new HashMap<>();
        variables.put("topic", "sample topic");
        
        Prompt prompt = agent.optimizePromptForPerplexity(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Research this topic"));
        assertTrue(promptContent.contains("current, well-sourced information"));
        assertTrue(promptContent.contains("citations and confidence levels"));
    }

    @Test
    void testOptimizePromptForPerplexity_WithNullVariables() {
        String basePrompt = "Research this topic";
        
        Prompt prompt = agent.optimizePromptForPerplexity(basePrompt, null);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Research this topic"));
        assertTrue(promptContent.contains("well-sourced information"));
    }

    @Test
    void testCreateFactCheckPrompt() {
        String basePrompt = "Verify these claims";
        Map<String, Object> variables = new HashMap<>();
        variables.put("claims", "sample claims");
        
        Prompt prompt = agent.createFactCheckPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Verify these claims"));
        assertTrue(promptContent.contains("Verify these facts against current, authoritative sources"));
        assertTrue(promptContent.contains("Confirm accuracy with multiple sources"));
        assertTrue(promptContent.contains("Identify any conflicting information"));
        assertTrue(promptContent.contains("confidence levels (high/medium/low)"));
        assertTrue(promptContent.contains("publication dates and source quality"));
        assertTrue(promptContent.contains("outdated or disputed claims"));
    }

    @Test
    void testCreateFactCheckPrompt_WithEmptyVariables() {
        String basePrompt = "Verify facts";
        Map<String, Object> variables = new HashMap<>();
        
        Prompt prompt = agent.createFactCheckPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Verify facts"));
        assertTrue(promptContent.contains("authoritative sources"));
    }

    @Test
    void testCreateResearchPrompt() {
        String basePrompt = "Research this field";
        Map<String, Object> variables = new HashMap<>();
        variables.put("field", "sample field");
        
        Prompt prompt = agent.createResearchPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Research this field"));
        assertTrue(promptContent.contains("Conduct comprehensive research"));
        assertTrue(promptContent.contains("Current state of research"));
        assertTrue(promptContent.contains("Recent developments and findings"));
        assertTrue(promptContent.contains("Key researchers and institutions"));
        assertTrue(promptContent.contains("Emerging trends and future directions"));
        assertTrue(promptContent.contains("Related topics and cross-references"));
        assertTrue(promptContent.contains("proper citations with links"));
    }

    @Test
    void testCreateResearchPrompt_WithNullVariables() {
        String basePrompt = "Research this area";
        
        Prompt prompt = agent.createResearchPrompt(basePrompt, null);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Research this area"));
        assertTrue(promptContent.contains("comprehensive research"));
    }

    @Test
    void testInheritedBehaviorFromAbstractConfigurableAgent() {
        // Test that it properly inherits from AbstractConfigurableAgent
        assertEquals(AIProvider.PERPLEXITY, agent.getProvider());
        assertEquals(AgentType.RELATED_PAPER_DISCOVERY, agent.getAgentType());
    }

    // Test implementation of PerplexityBasedAgent for testing purposes
    private static class TestablePerplexityBasedAgent extends PerplexityBasedAgent {

        public TestablePerplexityBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                                          AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
            super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        }

        @Override
        protected AgentResult processWithConfig(AgentTask task) {
            return AgentResult.success(task.getId(), "test result");
        }

        @Override
        public AgentType getAgentType() {
            return AgentType.RELATED_PAPER_DISCOVERY;
        }
    }
}

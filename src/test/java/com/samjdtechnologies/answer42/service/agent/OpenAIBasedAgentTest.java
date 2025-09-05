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

public class OpenAIBasedAgentTest {

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

    private TestableOpenAIBasedAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.openAiApi()).thenReturn(mockOpenAiApi);
        when(mockAiConfig.openAiChatModel(mockOpenAiApi)).thenReturn(mockOpenAiChatModel);
        when(mockAiConfig.openAiChatClient(mockOpenAiChatModel)).thenReturn(mockChatClient);
        
        agent = new TestableOpenAIBasedAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter);
    }

    @Test
    void testGetProvider() {
        AIProvider provider = agent.getProvider();
        
        assertEquals(AIProvider.OPENAI, provider);
    }

    @Test
    void testGetConfiguredChatClient() {
        ChatClient chatClient = agent.getConfiguredChatClient();
        
        assertNotNull(chatClient);
        assertEquals(mockChatClient, chatClient);
        verify(mockAiConfig).openAiApi();
        verify(mockAiConfig).openAiChatModel(mockOpenAiApi);
        verify(mockAiConfig).openAiChatClient(mockOpenAiChatModel);
    }

    @Test
    void testOptimizePromptForOpenAI() {
        String basePrompt = "Process this data";
        Map<String, Object> variables = new HashMap<>();
        variables.put("data", "sample data");
        
        Prompt prompt = agent.optimizePromptForOpenAI(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Process this data"));
        assertTrue(promptContent.contains("well-structured format"));
        assertTrue(promptContent.contains("clear sections and formatting"));
    }

    @Test
    void testOptimizePromptForOpenAI_WithNullVariables() {
        String basePrompt = "Process this data";
        
        Prompt prompt = agent.optimizePromptForOpenAI(basePrompt, null);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Process this data"));
        assertTrue(promptContent.contains("well-structured format"));
    }

    @Test
    void testCreateJsonPrompt() {
        String basePrompt = "Generate response for this request";
        Map<String, Object> variables = new HashMap<>();
        variables.put("request", "sample request");
        
        Prompt prompt = agent.createJsonPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Generate response for this request"));
        assertTrue(promptContent.contains("Return your response as valid JSON"));
        assertTrue(promptContent.contains("\"status\": \"success|error\""));
        assertTrue(promptContent.contains("\"data\": {...}"));
        assertTrue(promptContent.contains("\"message\": \"...\""));
    }

    @Test
    void testCreateJsonPrompt_WithEmptyVariables() {
        String basePrompt = "Generate JSON response";
        Map<String, Object> variables = new HashMap<>();
        
        Prompt prompt = agent.createJsonPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Generate JSON response"));
        assertTrue(promptContent.contains("valid JSON"));
    }

    @Test
    void testCreateAnalysisPrompt() {
        String basePrompt = "Analyze this document";
        Map<String, Object> variables = new HashMap<>();
        variables.put("document", "sample document");
        
        Prompt prompt = agent.createAnalysisPrompt(basePrompt, variables);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Analyze this document"));
        assertTrue(promptContent.contains("detailed analysis"));
        assertTrue(promptContent.contains("Summary of key findings"));
        assertTrue(promptContent.contains("Supporting evidence"));
        assertTrue(promptContent.contains("Confidence level (1-10)"));
        assertTrue(promptContent.contains("Recommendations or next steps"));
    }

    @Test
    void testCreateAnalysisPrompt_WithNullVariables() {
        String basePrompt = "Analyze this content";
        
        Prompt prompt = agent.createAnalysisPrompt(basePrompt, null);
        
        assertNotNull(prompt);
        String promptContent = prompt.getContents();
        assertTrue(promptContent.contains("Analyze this content"));
        assertTrue(promptContent.contains("detailed analysis"));
    }

    @Test
    void testInheritedBehaviorFromAbstractConfigurableAgent() {
        // Test that it properly inherits from AbstractConfigurableAgent
        assertEquals(AIProvider.OPENAI, agent.getProvider());
        assertEquals(AgentType.CONCEPT_EXPLAINER, agent.getAgentType());
    }

    // Test implementation of OpenAIBasedAgent for testing purposes
    private static class TestableOpenAIBasedAgent extends OpenAIBasedAgent {

        public TestableOpenAIBasedAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                                       AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
            super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        }

        @Override
        protected AgentResult processWithConfig(AgentTask task) {
            return AgentResult.success(task.getId(), "test result");
        }

        @Override
        public AgentType getAgentType() {
            return AgentType.CONCEPT_EXPLAINER;
        }
    }
}

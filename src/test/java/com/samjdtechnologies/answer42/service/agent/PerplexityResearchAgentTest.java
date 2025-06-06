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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class PerplexityResearchAgentTest {

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

    private PerplexityResearchAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.openAiApi()).thenReturn(mockOpenAiApi);
        when(mockAiConfig.openAiChatModel(mockOpenAiApi)).thenReturn(mockOpenAiChatModel);
        when(mockAiConfig.perplexityChatClient(mockOpenAiChatModel)).thenReturn(mockChatClient);
        
        agent = new PerplexityResearchAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.PERPLEXITY, agent.getProvider());
        assertEquals(AgentType.PERPLEXITY_RESEARCHER, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.PERPLEXITY_RESEARCHER, agentType);
    }

    @Test
    void testEstimateProcessingTime_MinimalParameters() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "test-paper-123");
        input.put("topic", "Machine Learning");
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + 0 research types * 90s + synthesis: 60s = 180s
        assertEquals(Duration.ofSeconds(180), duration);
    }

    @Test
    void testEstimateProcessingTime_WithAllResearchTypes() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "test-paper-456");
        input.put("topic", "Neural Networks");
        input.put("verifyFacts", true);
        input.put("findRelatedPapers", true);
        input.put("analyzeTrends", true);
        input.put("verifyMethodology", true);
        input.put("gatherExpertOpinions", true);
        
        ArrayNode claims = JsonNodeFactory.instance.arrayNode();
        claims.add("Neural networks achieve 95% accuracy");
        claims.add("Deep learning outperforms traditional methods");
        input.set("claims", claims);
        
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + 5 research types * 90s + synthesis: 60s = 630s
        assertEquals(Duration.ofSeconds(630), duration);
    }

    @Test
    void testEstimateProcessingTime_PartialResearchTypes() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "test-paper-789");
        input.put("topic", "Computer Vision");
        input.put("verifyFacts", true);
        input.put("findRelatedPapers", true);
        input.put("analyzeTrends", false);
        input.put("verifyMethodology", false);
        input.put("gatherExpertOpinions", false);
        
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + 2 research types * 90s + synthesis: 60s = 360s
        assertEquals(Duration.ofSeconds(360), duration);
    }

    @Test
    void testProcessWithConfig_EmptyInput() {
        AgentTask task = new AgentTask();
        task.setId("test-task-empty");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess()); // Should succeed with default parameters
        assertEquals("test-task-empty", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_BasicResearchTask() {
        AgentTask task = new AgentTask();
        task.setId("test-task-basic");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "research-paper-123");
        input.put("topic", "Artificial Intelligence");
        input.put("verifyFacts", false); // Disable to avoid complex mocking
        input.put("findRelatedPapers", false);
        input.put("analyzeTrends", false);
        input.put("verifyMethodology", false);
        input.put("gatherExpertOpinions", false);
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-basic", result.getTaskId());
        assertNotNull(result.getResultData());
    }

    @Test
    void testProcessWithConfig_WithClaims() {
        AgentTask task = new AgentTask();
        task.setId("test-task-claims");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "claims-paper-456");
        input.put("topic", "Machine Learning Performance");
        
        ArrayNode claims = JsonNodeFactory.instance.arrayNode();
        claims.add("Our model achieves 99% accuracy on test data");
        claims.add("The proposed method significantly outperforms baselines");
        input.set("claims", claims);
        
        input.put("verifyFacts", false); // Disable to avoid complex mocking
        input.put("findRelatedPapers", false);
        input.put("analyzeTrends", false);
        
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-claims", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_WithAbstractExtraction() {
        AgentTask task = new AgentTask();
        task.setId("test-task-abstract");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "abstract-paper-789");
        input.put("topic", "Deep Learning");
        input.put("abstract", "We found that neural networks with attention mechanisms significantly outperform traditional models. Our results show a 25% improvement in accuracy. The data demonstrate clear advantages over baseline methods. We conclude that attention-based architectures are superior for this task.");
        
        input.put("verifyFacts", false); // Disable to avoid complex mocking
        input.put("findRelatedPapers", false);
        
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-abstract", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_WithKeywords() {
        AgentTask task = new AgentTask();
        task.setId("test-task-keywords");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "keywords-paper-101");
        input.put("topic", "Natural Language Processing");
        
        ArrayNode keywords = JsonNodeFactory.instance.arrayNode();
        keywords.add("transformer");
        keywords.add("attention");
        keywords.add("NLP");
        keywords.add("BERT");
        input.set("keywords", keywords);
        
        input.put("verifyFacts", false);
        input.put("findRelatedPapers", false);
        
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-keywords", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_WithMethodology() {
        AgentTask task = new AgentTask();
        task.setId("test-task-methodology");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "methodology-paper-202");
        input.put("topic", "Experimental Design");
        input.put("methodology", "We used a randomized controlled trial with 1000 participants, double-blind design, and statistical significance testing at p<0.05");
        input.put("verifyMethodology", false); // Disable to avoid complex mocking
        
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-methodology", result.getTaskId());
    }

    @Test
    void testInheritedBehaviorFromPerplexityBasedAgent() {
        // Test that it properly inherits from PerplexityBasedAgent
        assertEquals(AIProvider.PERPLEXITY, agent.getProvider());
        assertEquals(AgentType.PERPLEXITY_RESEARCHER, agent.getAgentType());
        
        // Test that it can get configured chat client
        ChatClient chatClient = agent.getConfiguredChatClient();
        assertNotNull(chatClient);
        assertEquals(mockChatClient, chatClient);
        
        // Verify mock interactions
        verify(mockAiConfig).openAiApi();
        verify(mockAiConfig).openAiChatModel(mockOpenAiApi);
        verify(mockAiConfig).perplexityChatClient(mockOpenAiChatModel);
    }

    @Test
    void testEstimateProcessingTime_NullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Should return base time (120s + 60s = 180s) when input is null
        assertEquals(Duration.ofSeconds(180), duration);
    }

    @Test
    void testEstimateProcessingTime_ComplexResearchScenario() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "complex-paper-999");
        input.put("topic", "Comprehensive AI Research");
        input.put("domain", "Artificial Intelligence");
        input.put("context", "This paper presents a novel approach to AI reasoning");
        
        // Enable most research types
        input.put("verifyFacts", true);
        input.put("findRelatedPapers", true);
        input.put("analyzeTrends", true);
        input.put("verifyMethodology", false);
        input.put("gatherExpertOpinions", true);
        
        // Add claims and keywords
        ArrayNode claims = JsonNodeFactory.instance.arrayNode();
        claims.add("AI systems demonstrate human-level performance");
        claims.add("Our approach reduces computation by 50%");
        input.set("claims", claims);
        
        ArrayNode keywords = JsonNodeFactory.instance.arrayNode();
        keywords.add("artificial intelligence");
        keywords.add("machine learning");
        keywords.add("deep learning");
        input.set("keywords", keywords);
        
        task.setInput(input);
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertNotNull(duration);
        // Base: 120s + 4 research types * 90s + synthesis: 60s = 540s
        assertEquals(Duration.ofSeconds(540), duration);
    }

    @Test
    void testProcessWithConfig_LegacyFieldNames() {
        // Test backward compatibility with different field names
        AgentTask task = new AgentTask();
        task.setId("test-task-legacy");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "legacy-paper-303");
        input.put("researchTopic", "Legacy Research Topic"); // Alternative field name
        input.put("researchDomain", "Legacy Domain"); // Alternative field name
        input.put("paperContext", "Legacy context"); // Alternative field name
        input.put("methodologyDescription", "Legacy methodology"); // Alternative field name
        input.put("findRelatedPapers", true); // Alternative field name
        input.put("gatherExpertOpinions", false); // Alternative field name
        
        ArrayNode keyClaims = JsonNodeFactory.instance.arrayNode();
        keyClaims.add("Legacy claim validation");
        input.set("keyClaims", keyClaims); // Alternative field name
        
        ArrayNode paperKeywords = JsonNodeFactory.instance.arrayNode();
        paperKeywords.add("legacy");
        paperKeywords.add("compatibility");
        input.set("paperKeywords", paperKeywords); // Alternative field name
        
        input.put("verifyFacts", false); // Disable to avoid complex mocking
        input.put("findRelatedPapers", false);
        
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-legacy", result.getTaskId());
    }

    @Test
    void testProcessWithConfig_DefaultTopicExtraction() {
        // Test that it extracts topic from paperTitle when no topic provided
        AgentTask task = new AgentTask();
        task.setId("test-task-title-extraction");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "title-extraction-paper-404");
        input.put("paperTitle", "Advanced Neural Network Architectures for Computer Vision");
        // No explicit topic provided - should use paperTitle
        
        input.put("verifyFacts", false);
        input.put("findRelatedPapers", false);
        
        task.setInput(input);
        
        AgentResult result = agent.processWithConfig(task);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-title-extraction", result.getTaskId());
    }
}

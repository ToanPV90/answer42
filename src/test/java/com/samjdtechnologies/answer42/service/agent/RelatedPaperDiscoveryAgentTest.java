package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.discovery.RelatedPaperDiscoveryResult;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.service.discovery.DiscoveryCoordinator;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class RelatedPaperDiscoveryAgentTest {

    @Mock
    private AIConfig mockAiConfig;
    
    @Mock
    private ThreadConfig mockThreadConfig;
    
    @Mock
    private AgentRetryPolicy mockRetryPolicy;
    
    @Mock
    private APIRateLimiter mockRateLimiter;
    
    @Mock
    private DiscoveryCoordinator mockDiscoveryCoordinator;
    
    @Mock
    private PaperRepository mockPaperRepository;
    
    @Mock
    private ChatClient mockChatClient;
    
    @Mock
    private AnthropicApi mockAnthropicApi;
    
    @Mock
    private AnthropicChatModel mockAnthropicChatModel;
    
    @Mock
    private Paper mockPaper;

    private RelatedPaperDiscoveryAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.anthropicApi()).thenReturn(mockAnthropicApi);
        when(mockAiConfig.anthropicChatModel(mockAnthropicApi)).thenReturn(mockAnthropicChatModel);
        when(mockAiConfig.anthropicChatClient(mockAnthropicChatModel)).thenReturn(mockChatClient);
        
        agent = new RelatedPaperDiscoveryAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter, mockDiscoveryCoordinator, mockPaperRepository);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.ANTHROPIC, agent.getProvider());
        assertEquals(AgentType.RELATED_PAPER_DISCOVERY, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.RELATED_PAPER_DISCOVERY, agentType);
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
        
        // Verify mock interactions
        verify(mockAiConfig).anthropicApi();
        verify(mockAiConfig).anthropicChatModel(mockAnthropicApi);
        verify(mockAiConfig).anthropicChatClient(mockAnthropicChatModel);
    }

    @Test
    void testEstimateProcessingTime_WithValidConfiguration() {
        AgentTask task = new AgentTask();
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("maxTotalPapers", 50);
        input.put("enableAISynthesis", true);
        task.setInput(input);

        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        Duration duration = agent.estimateProcessingTime(task);

        assertNotNull(duration);
        assertTrue(duration.toMinutes() >= 2); // At least base time
        assertTrue(duration.toMinutes() <= 8); // Maximum reasonable time
    }

    @Test
    void testEstimateProcessingTime_WithInvalidInput() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        // No paperId provided
        task.setInput(input);

        Duration duration = agent.estimateProcessingTime(task);

        assertNotNull(duration);
        assertEquals(Duration.ofMinutes(5), duration); // Default fallback
    }

    @Test
    void testEstimateProcessingTime_NullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);

        Duration duration = agent.estimateProcessingTime(task);

        assertNotNull(duration);
        assertEquals(Duration.ofMinutes(5), duration); // Default fallback
    }

    @Test
    void testCanHandle_ValidTask() {
        AgentTask task = new AgentTask();
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        task.setInput(input);

        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        boolean canHandle = agent.canHandle(task);

        assertTrue(canHandle);
        verify(mockPaperRepository).findById(paperId);
    }

    @Test
    void testCanHandle_InvalidPaperId() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", "invalid-uuid");
        task.setInput(input);

        boolean canHandle = agent.canHandle(task);

        assertFalse(canHandle);
    }

    @Test
    void testCanHandle_PaperNotFound() {
        AgentTask task = new AgentTask();
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        task.setInput(input);

        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.empty());

        boolean canHandle = agent.canHandle(task);

        assertFalse(canHandle);
        verify(mockPaperRepository).findById(paperId);
    }

    @Test
    void testCanHandle_NullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);

        boolean canHandle = agent.canHandle(task);

        assertFalse(canHandle);
    }

    @Test
    void testProcessWithConfig_SuccessfulDiscovery() {
        AgentTask task = new AgentTask();
        task.setId("test-task-123");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("maxTotalPapers", 25);
        task.setInput(input);

        // Mock paper repository
        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        // Mock discovery coordinator
        RelatedPaperDiscoveryResult mockResult = mock(RelatedPaperDiscoveryResult.class);
        when(mockResult.getDiscoverySummary()).thenReturn("Found 10 related papers");
        CompletableFuture<RelatedPaperDiscoveryResult> discoveryFuture = 
            CompletableFuture.completedFuture(mockResult);
        when(mockDiscoveryCoordinator.coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class)))
            .thenReturn(discoveryFuture);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-123", result.getTaskId());
        assertEquals(mockResult, result.getResultData());

        verify(mockPaperRepository).findById(paperId);
        verify(mockDiscoveryCoordinator).coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class));
        verify(mockResult).setDiscoveryStartTime(any());
        verify(mockResult).setDiscoveryEndTime(any());
        verify(mockResult).setTotalProcessingTimeMs(anyLong());
    }

    @Test
    void testProcessWithConfig_InvalidInput() {
        AgentTask task = new AgentTask();
        task.setId("test-task-invalid");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        // No paperId provided
        task.setInput(input);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-invalid", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("Invalid task input"));
    }

    @Test
    void testProcessWithConfig_PaperNotFound() {
        AgentTask task = new AgentTask();
        task.setId("test-task-not-found");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        task.setInput(input);

        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.empty());

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-not-found", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("Invalid task input"));

        verify(mockPaperRepository).findById(paperId);
    }

    @Test
    void testProcessWithConfig_DiscoveryFailure() {
        AgentTask task = new AgentTask();
        task.setId("test-task-failure");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        task.setInput(input);

        // Mock paper repository
        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        // Mock discovery coordinator failure
        CompletableFuture<RelatedPaperDiscoveryResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Discovery service unavailable"));
        when(mockDiscoveryCoordinator.coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class)))
            .thenReturn(failedFuture);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-failure", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("Discovery operation failed"));

        verify(mockPaperRepository).findById(paperId);
        verify(mockDiscoveryCoordinator).coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class));
    }

    @Test
    void testProcessWithConfig_WithCustomConfiguration() {
        AgentTask task = new AgentTask();
        task.setId("test-task-custom-config");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        input.put("configurationType", "comprehensive");
        input.put("maxTotalPapers", 100);
        input.put("enableAISynthesis", true);
        task.setInput(input);

        // Mock paper repository
        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        // Mock discovery coordinator
        RelatedPaperDiscoveryResult mockResult = mock(RelatedPaperDiscoveryResult.class);
        when(mockResult.getDiscoverySummary()).thenReturn("Found 50 related papers");
        CompletableFuture<RelatedPaperDiscoveryResult> discoveryFuture = 
            CompletableFuture.completedFuture(mockResult);
        when(mockDiscoveryCoordinator.coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class)))
            .thenReturn(discoveryFuture);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-custom-config", result.getTaskId());
        assertEquals(mockResult, result.getResultData());

        verify(mockPaperRepository).findById(paperId);
        verify(mockDiscoveryCoordinator).coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class));
    }

    @Test
    void testProcessWithConfig_WithAlternativeFieldNames() {
        AgentTask task = new AgentTask();
        task.setId("test-task-alternative-fields");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("sourcePaperId", paperId.toString()); // Alternative field name
        input.put("configType", "fast"); // Alternative field name
        task.setInput(input);

        // Mock paper repository
        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        // Mock discovery coordinator
        RelatedPaperDiscoveryResult mockResult = mock(RelatedPaperDiscoveryResult.class);
        when(mockResult.getDiscoverySummary()).thenReturn("Found 15 related papers");
        CompletableFuture<RelatedPaperDiscoveryResult> discoveryFuture = 
            CompletableFuture.completedFuture(mockResult);
        when(mockDiscoveryCoordinator.coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class)))
            .thenReturn(discoveryFuture);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-alternative-fields", result.getTaskId());
        assertEquals(mockResult, result.getResultData());

        verify(mockPaperRepository).findById(paperId);
        verify(mockDiscoveryCoordinator).coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class));
    }

    @Test
    void testProcessWithConfig_WithNestedPaperObject() {
        AgentTask task = new AgentTask();
        task.setId("test-task-nested-paper");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        ObjectNode paperNode = JsonNodeFactory.instance.objectNode();
        paperNode.put("id", paperId.toString());
        input.set("paper", paperNode);
        task.setInput(input);

        // Mock paper repository
        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        // Mock discovery coordinator
        RelatedPaperDiscoveryResult mockResult = mock(RelatedPaperDiscoveryResult.class);
        when(mockResult.getDiscoverySummary()).thenReturn("Found 20 related papers");
        CompletableFuture<RelatedPaperDiscoveryResult> discoveryFuture = 
            CompletableFuture.completedFuture(mockResult);
        when(mockDiscoveryCoordinator.coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class)))
            .thenReturn(discoveryFuture);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-nested-paper", result.getTaskId());
        assertEquals(mockResult, result.getResultData());

        verify(mockPaperRepository).findById(paperId);
        verify(mockDiscoveryCoordinator).coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class));
    }

    @Test
    void testProcessWithConfig_WithExplicitConfiguration() {
        AgentTask task = new AgentTask();
        task.setId("test-task-explicit-config");
        UUID paperId = UUID.randomUUID();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("paperId", paperId.toString());
        
        // Add explicit configuration
        ObjectNode configNode = JsonNodeFactory.instance.objectNode();
        configNode.put("maxTotalPapers", 75);
        configNode.put("minimumRelevanceScore", 0.7);
        configNode.put("timeoutSeconds", 600);
        configNode.put("parallelExecution", true);
        configNode.put("enableAISynthesis", false);
        input.set("configuration", configNode);
        task.setInput(input);

        // Mock paper repository
        when(mockPaperRepository.findById(paperId)).thenReturn(Optional.of(mockPaper));
        when(mockPaper.getId()).thenReturn(paperId);
        when(mockPaper.getTitle()).thenReturn("Test Paper");

        // Mock discovery coordinator
        RelatedPaperDiscoveryResult mockResult = mock(RelatedPaperDiscoveryResult.class);
        when(mockResult.getDiscoverySummary()).thenReturn("Found 30 related papers");
        CompletableFuture<RelatedPaperDiscoveryResult> discoveryFuture = 
            CompletableFuture.completedFuture(mockResult);
        when(mockDiscoveryCoordinator.coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class)))
            .thenReturn(discoveryFuture);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-explicit-config", result.getTaskId());
        assertEquals(mockResult, result.getResultData());

        verify(mockPaperRepository).findById(paperId);
        verify(mockDiscoveryCoordinator).coordinateDiscovery(eq(mockPaper), any(DiscoveryConfiguration.class));
    }
}

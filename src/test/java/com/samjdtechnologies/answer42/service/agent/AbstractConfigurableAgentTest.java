package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.agent.AgentRetryStatistics;
import com.samjdtechnologies.answer42.model.agent.ProcessingMetrics;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.enums.LoadStatus;
import com.samjdtechnologies.answer42.service.UserPreferencesService;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentCircuitBreaker;
import com.samjdtechnologies.answer42.service.pipeline.AgentCircuitBreaker.CircuitBreakerStatus;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;

public class AbstractConfigurableAgentTest {

    @Mock
    private AIConfig mockAiConfig;
    
    @Mock
    private ThreadConfig mockThreadConfig;
    
    @Mock
    private AgentRetryPolicy mockRetryPolicy;
    
    @Mock
    private APIRateLimiter mockRateLimiter;
    
    @Mock
    private AgentCircuitBreaker mockCircuitBreaker;
    
    @Mock
    private ThreadPoolTaskExecutor mockExecutor;
    
    @Mock
    private ThreadPoolExecutor mockThreadPoolExecutor;
    
    @Mock
    private ChatClient mockChatClient;
    
    @Mock
    private UserPreferencesService mockUserPreferencesService;

    private TestableAbstractConfigurableAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors
        when(mockThreadConfig.taskExecutor()).thenReturn(mockExecutor);
        when(mockExecutor.getActiveCount()).thenReturn(5);
        when(mockExecutor.getMaxPoolSize()).thenReturn(10);
        when(mockExecutor.getPoolSize()).thenReturn(8);
        when(mockExecutor.getThreadPoolExecutor()).thenReturn(mockThreadPoolExecutor);
        when(mockThreadPoolExecutor.getQueue()).thenReturn(new java.util.concurrent.LinkedBlockingQueue<>());
        
        agent = new TestableAbstractConfigurableAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter);
    }

    @Test
    void testCanHandle_WithValidTask() {
        AgentTask task = new AgentTask();
        task.setInput(JsonNodeFactory.instance.textNode("test input"));
        
        boolean result = agent.canHandle(task);
        
        assertTrue(result);
    }

    @Test
    void testCanHandle_WithNullTask() {
        boolean result = agent.canHandle(null);
        
        assertFalse(result);
    }

    @Test
    void testCanHandle_WithNullInput() {
        AgentTask task = new AgentTask();
        task.setInput(null);
        
        boolean result = agent.canHandle(task);
        
        assertFalse(result);
    }

    @Test
    void testCreateProcessingMetrics() {
        Instant startTime = Instant.now().minusSeconds(10);
        
        ProcessingMetrics metrics = agent.createProcessingMetrics(startTime);
        
        assertNotNull(metrics);
        assertEquals(AgentType.PAPER_PROCESSOR, metrics.getAgentType());
        assertEquals(AIProvider.OPENAI, metrics.getProvider());
        assertEquals(startTime, metrics.getStartTime());
        assertNotNull(metrics.getEndTime());
        assertNotNull(metrics.getProcessingTime());
        assertNotNull(metrics.getThreadPoolStatus());
    }

    @Test
    void testEstimateProcessingTime() {
        AgentTask task = new AgentTask();
        
        Duration duration = agent.estimateProcessingTime(task);
        
        assertEquals(Duration.ofMinutes(2), duration);
    }

    @Test
    void testGetLoadStatus_LowLoad() {
        when(mockExecutor.getActiveCount()).thenReturn(2);
        when(mockExecutor.getMaxPoolSize()).thenReturn(10);
        
        LoadStatus status = agent.getLoadStatus();
        
        assertEquals(LoadStatus.LOW, status);
    }

    @Test
    void testGetLoadStatus_MediumLoad() {
        when(mockExecutor.getActiveCount()).thenReturn(7);
        when(mockExecutor.getMaxPoolSize()).thenReturn(10);
        
        LoadStatus status = agent.getLoadStatus();
        
        assertEquals(LoadStatus.MEDIUM, status);
    }

    @Test
    void testGetLoadStatus_HighLoad() {
        when(mockExecutor.getActiveCount()).thenReturn(9);
        when(mockExecutor.getMaxPoolSize()).thenReturn(10);
        
        LoadStatus status = agent.getLoadStatus();
        
        assertEquals(LoadStatus.HIGH, status);
    }

    @Test
    void testGetCircuitBreakerStatus() {
        when(mockRetryPolicy.getCircuitBreakerStatus(AgentType.PAPER_PROCESSOR))
            .thenReturn(CircuitBreakerStatus.CLOSED);
        
        String status = agent.getCircuitBreakerStatus();
        
        assertEquals("CLOSED", status);
    }

    @Test
    void testGetRetryStatistics() {
        AgentRetryStatistics stats = AgentRetryStatistics.builder()
            .totalAttempts(10)
            .totalRetries(2)
            .overallSuccessRate(0.8)
            .build();
        when(mockRetryPolicy.getAgentRetryStatistics(AgentType.PAPER_PROCESSOR)).thenReturn(stats);
        
        String result = agent.getRetryStatistics();
        
        assertEquals("Attempts: 10, Retries: 2, Success Rate: 80.00%", result);
    }

    @Test
    void testGetTaskIdentifier() {
        AgentTask task = new AgentTask();
        task.setId("task-123");
        
        String identifier = agent.getTaskIdentifier(task);
        
        assertEquals("PAPER_PROCESSOR[task-123]", identifier);
    }

    @Test
    void testIsRetryableException_NetworkTimeout() {
        Exception ex = new java.net.SocketTimeoutException("Connection timeout");
        
        boolean result = agent.isRetryableException(ex);
        
        assertTrue(result);
    }

    @Test
    void testIsRetryableException_RateLimitMessage() {
        Exception ex = new RuntimeException("rate limit exceeded");
        
        boolean result = agent.isRetryableException(ex);
        
        assertTrue(result);
    }

    @Test
    void testIsRetryableException_AuthenticationError() {
        Exception ex = new RuntimeException("401 unauthorized");
        
        boolean result = agent.isRetryableException(ex);
        
        assertFalse(result);
    }

    @Test
    void testIsRetryableException_UnknownError() {
        Exception ex = new RuntimeException("Unknown error");
        
        boolean result = agent.isRetryableException(ex);
        
        assertFalse(result);
    }

    @Test
    void testValidateTaskRequirements_ValidTask() {
        AgentTask task = new AgentTask();
        task.setInput(JsonNodeFactory.instance.textNode("valid input"));
        
        assertDoesNotThrow(() -> agent.validateTaskRequirements(task));
    }

    @Test
    void testValidateTaskRequirements_NullTask() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> agent.validateTaskRequirements(null));
        
        assertEquals("Task cannot be null", ex.getMessage());
    }

    @Test
    void testValidateTaskRequirements_TaskCannotBeHandled() {
        AgentTask task = new AgentTask();
        task.setInput(null); // This will make canHandle return false
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> agent.validateTaskRequirements(task));
        
        assertTrue(ex.getMessage().contains("Agent PAPER_PROCESSOR cannot handle task"));
    }

    @Test
    void testProcess_ValidTask() {
        AgentTask task = new AgentTask();
        task.setId("test-task");
        task.setInput(JsonNodeFactory.instance.textNode("test input"));
        
        AgentResult expectedResult = AgentResult.success("test-task", "test result");
        CompletableFuture<AgentResult> futureResult = CompletableFuture.completedFuture(expectedResult);
        
        when(mockRetryPolicy.executeWithRetry(eq(AgentType.PAPER_PROCESSOR), any(), eq(task)))
            .thenAnswer(invocation -> futureResult);
        
        CompletableFuture<AgentResult> result = agent.process(task);
        
        assertNotNull(result);
        verify(mockRetryPolicy).executeWithRetry(eq(AgentType.PAPER_PROCESSOR), any(), eq(task));
    }

    @Test
    void testProcess_TaskValidationFails() {
        AgentTask task = new AgentTask();
        task.setInput(null); // Invalid task
        
        CompletableFuture<AgentResult> result = agent.process(task);
        
        assertNotNull(result);
        assertTrue(result.isDone());
        AgentResult agentResult = result.join();
        assertFalse(agentResult.isSuccess());
        assertTrue(agentResult.getErrorMessage().contains("Agent PAPER_PROCESSOR cannot handle task"));
    }

    @Test
    void testExecutePrompt_ReturnsNull() {
        // Test the default implementation that returns null
        org.springframework.ai.chat.prompt.Prompt prompt = mock(org.springframework.ai.chat.prompt.Prompt.class);
        
        org.springframework.ai.chat.model.ChatResponse result = agent.executePrompt(prompt);
        
        assertNull(result);
    }


    @Test
    void testCreateProcessingMetrics_WithNullStartTime() {
        ProcessingMetrics metrics = agent.createProcessingMetrics(null);
        
        assertNotNull(metrics);
        assertEquals(AgentType.PAPER_PROCESSOR, metrics.getAgentType());
        assertEquals(AIProvider.OPENAI, metrics.getProvider());
        assertNull(metrics.getStartTime());
        assertNotNull(metrics.getEndTime());
        assertNotNull(metrics.getProcessingTime());
        assertNotNull(metrics.getThreadPoolStatus());
    }

    @Test
    void testGetLoadStatus_EdgeCase_ExactThreshold() {
        // Test exactly 50% load (edge case for medium/low boundary)
        when(mockExecutor.getActiveCount()).thenReturn(5);
        when(mockExecutor.getMaxPoolSize()).thenReturn(10);
        
        LoadStatus status = agent.getLoadStatus();
        
        assertEquals(LoadStatus.MEDIUM, status);
    }

    @Test
    void testGetLoadStatus_EdgeCase_HighThreshold() {
        // Test exactly 80% load (edge case for high/medium boundary)
        when(mockExecutor.getActiveCount()).thenReturn(8);
        when(mockExecutor.getMaxPoolSize()).thenReturn(10);
        
        LoadStatus status = agent.getLoadStatus();
        
        assertEquals(LoadStatus.HIGH, status);
    }

    @Test
    void testGetRetryStatistics_WithNullStats() {
        when(mockRetryPolicy.getAgentRetryStatistics(AgentType.PAPER_PROCESSOR)).thenReturn(null);
        
        // The actual implementation will throw NullPointerException if stats is null
        assertThrows(NullPointerException.class, () -> agent.getRetryStatistics());
    }

    @Test
    void testGetTaskIdentifier_WithNullTaskId() {
        AgentTask task = new AgentTask();
        task.setId(null);
        
        String identifier = agent.getTaskIdentifier(task);
        
        assertEquals("PAPER_PROCESSOR[null]", identifier);
    }

    @Test
    void testIsRetryableException_ConnectionRefused() {
        Exception ex = new java.net.ConnectException("Connection refused");
        
        boolean result = agent.isRetryableException(ex);
        
        assertTrue(result);
    }

    @Test
    void testIsRetryableException_ServiceUnavailable() {
        Exception ex = new RuntimeException("503 service unavailable");
        
        boolean result = agent.isRetryableException(ex);
        
        assertTrue(result);
    }

    @Test
    void testIsRetryableException_TooManyRequests() {
        Exception ex = new RuntimeException("429 too many requests");
        
        boolean result = agent.isRetryableException(ex);
        
        assertTrue(result);
    }

    @Test
    void testCanHandle_WithEmptyJsonInput() {
        AgentTask task = new AgentTask();
        task.setInput(JsonNodeFactory.instance.textNode(""));
        
        boolean result = agent.canHandle(task);
        
        assertTrue(result); // Empty string is still a valid input node
    }

    @Test
    void testCanHandle_WithWhitespaceOnlyInput() {
        AgentTask task = new AgentTask();
        task.setInput(JsonNodeFactory.instance.textNode("   "));
        
        boolean result = agent.canHandle(task);
        
        assertTrue(result); // Whitespace is still a valid input node
    }

    // Test implementation of AbstractConfigurableAgent for testing purposes
    private static class TestableAbstractConfigurableAgent extends AbstractConfigurableAgent {

        public TestableAbstractConfigurableAgent(AIConfig aiConfig, ThreadConfig threadConfig,
                                                AgentRetryPolicy retryPolicy, APIRateLimiter rateLimiter) {
            super(aiConfig, threadConfig, retryPolicy, rateLimiter);
        }

        @Override
        protected ChatClient getConfiguredChatClient() {
            return mock(ChatClient.class);
        }

        @Override
        protected AgentResult processWithConfig(AgentTask task) {
            return AgentResult.success(task.getId(), "test result");
        }

        @Override
        public AgentType getAgentType() {
            return AgentType.PAPER_PROCESSOR;
        }

        @Override
        public AIProvider getProvider() {
            return AIProvider.OPENAI;
        }

        protected CompletableFuture<org.springframework.ai.chat.model.ChatResponse> executePromptWithRetryInternal(
                org.springframework.ai.chat.prompt.Prompt prompt, AgentTask originalTask) {
            // Test implementation returns null to match test expectations
            return null;
        }
    }
}

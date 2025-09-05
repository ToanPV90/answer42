package com.samjdtechnologies.answer42.service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
import com.samjdtechnologies.answer42.model.enums.QualityCheckType;
import com.samjdtechnologies.answer42.model.quality.QualityCheckResult;
import com.samjdtechnologies.answer42.model.quality.QualityIssue;
import com.samjdtechnologies.answer42.service.pipeline.APIRateLimiter;
import com.samjdtechnologies.answer42.service.pipeline.AgentRetryPolicy;
import com.samjdtechnologies.answer42.util.QualityResponseParser;

public class QualityCheckerAgentTest {

    @Mock
    private AIConfig mockAiConfig;
    
    @Mock
    private ThreadConfig mockThreadConfig;
    
    @Mock
    private AgentRetryPolicy mockRetryPolicy;
    
    @Mock
    private APIRateLimiter mockRateLimiter;
    
    @Mock
    private QualityResponseParser mockResponseParser;
    
    @Mock
    private ChatClient mockChatClient;
    
    @Mock
    private AnthropicApi mockAnthropicApi;
    
    @Mock
    private AnthropicChatModel mockAnthropicChatModel;
    
    @Mock
    private ChatClient.ChatClientRequestSpec mockRequestSpec;
    
    @Mock
    private ChatClient.CallResponseSpec mockCallResponseSpec;

    private QualityCheckerAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors for ChatClient creation
        when(mockAiConfig.anthropicApi()).thenReturn(mockAnthropicApi);
        when(mockAiConfig.anthropicChatModel(mockAnthropicApi)).thenReturn(mockAnthropicChatModel);
        when(mockAiConfig.anthropicChatClient(mockAnthropicChatModel)).thenReturn(mockChatClient);
        
        agent = new QualityCheckerAgent(mockAiConfig, mockThreadConfig, 
            mockRetryPolicy, mockRateLimiter, mockResponseParser);
    }

    @Test
    void testConstructor() {
        assertNotNull(agent);
        assertEquals(AIProvider.ANTHROPIC, agent.getProvider());
        assertEquals(AgentType.QUALITY_CHECKER, agent.getAgentType());
    }

    @Test
    void testGetAgentType() {
        AgentType agentType = agent.getAgentType();
        
        assertEquals(AgentType.QUALITY_CHECKER, agentType);
    }

    @Test
    void testInheritedBehaviorFromAnthropicBasedAgent() {
        // Test that it properly inherits from AnthropicBasedAgent
        assertEquals(AIProvider.ANTHROPIC, agent.getProvider());
        assertEquals(AgentType.QUALITY_CHECKER, agent.getAgentType());
        
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
    void testEstimateProcessingTime_ShortContent() {
        AgentTask task = new AgentTask();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("generatedContent", "Short content for testing quality checks.");
        task.setInput(input);

        when(mockResponseParser.extractGeneratedContent(task))
            .thenReturn("Short content for testing quality checks.");

        Duration duration = agent.estimateProcessingTime(task);

        assertNotNull(duration);
        // Base: 90s + minimal content: ~0s + checks: 75s = ~165s
        assertTrue(duration.getSeconds() >= 160);
        assertTrue(duration.getSeconds() <= 170);
    }

    @Test
    void testEstimateProcessingTime_LongContent() {
        AgentTask task = new AgentTask();
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is a long piece of content for testing quality check processing time estimation. ");
        }
        
        when(mockResponseParser.extractGeneratedContent(task))
            .thenReturn(longContent.toString());

        Duration duration = agent.estimateProcessingTime(task);

        assertNotNull(duration);
        // Base: 90s + content: up to 180s + checks: 75s = up to 345s
        assertTrue(duration.getSeconds() >= 200);
        assertTrue(duration.getSeconds() <= 350);
    }

    @Test
    void testEstimateProcessingTime_NullContent() {
        AgentTask task = new AgentTask();
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(null);

        Duration duration = agent.estimateProcessingTime(task);

        assertNotNull(duration);
        // Base: 90s + default content: ~0s + checks: 75s = ~165s
        assertTrue(duration.getSeconds() >= 160);
        assertTrue(duration.getSeconds() <= 170);
    }

    @Test
    void testProcessWithConfig_EmptyGeneratedContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-empty");
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn("");
        when(mockResponseParser.extractSourceContent(task)).thenReturn("Some source content");

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-empty", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No generated content provided"));
    }

    @Test
    void testProcessWithConfig_NullGeneratedContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-null");
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(null);
        when(mockResponseParser.extractSourceContent(task)).thenReturn("Some source content");

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-null", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("No generated content provided"));
    }

    @Test
    void testProcessWithConfig_ValidContentWithSource() {
        AgentTask task = new AgentTask();
        task.setId("test-task-valid-with-source");
        
        String generatedContent = "This is the generated content that needs quality checking. It contains various claims and statements that should be verified against the source material.";
        String sourceContent = "This is the original source material that provides context and facts for verification.";
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(generatedContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn(sourceContent);
        
        // Mock quality check results
        QualityCheckResult accuracyResult = createMockQualityCheckResult(QualityCheckType.ACCURACY, 0.85);
        QualityCheckResult consistencyResult = createMockQualityCheckResult(QualityCheckType.CONSISTENCY, 0.90);
        QualityCheckResult biasResult = createMockQualityCheckResult(QualityCheckType.BIAS_DETECTION, 0.95);
        QualityCheckResult hallucinationResult = createMockQualityCheckResult(QualityCheckType.HALLUCINATION_DETECTION, 0.88);
        QualityCheckResult coherenceResult = createMockQualityCheckResult(QualityCheckType.LOGICAL_COHERENCE, 0.92);
        
        // Mock chat client responses
        setupMockChatClientResponses();
        
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.ACCURACY), anyString()))
            .thenReturn(accuracyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.CONSISTENCY), anyString()))
            .thenReturn(consistencyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.BIAS_DETECTION), anyString()))
            .thenReturn(biasResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.HALLUCINATION_DETECTION), anyString()))
            .thenReturn(hallucinationResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.LOGICAL_COHERENCE), anyString()))
            .thenReturn(coherenceResult);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-valid-with-source", result.getTaskId());
        
        // Verify result data is a quality report
        assertTrue(result.getResultData() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) result.getResultData();
        
        assertTrue(report.containsKey("overallScore"));
        assertTrue(report.containsKey("overallGrade"));
        assertTrue(report.containsKey("passed"));
        assertTrue(report.containsKey("checkResults"));
        assertTrue(report.containsKey("totalIssues"));
        assertTrue(report.containsKey("recommendations"));
        assertTrue(report.containsKey("timestamp"));
        
        // Verify all quality checks were called
        verify(mockResponseParser, times(5)).parseQualityCheckResponse(any(QualityCheckType.class), anyString());
    }

    @Test
    void testProcessWithConfig_ValidContentWithoutSource() {
        AgentTask task = new AgentTask();
        task.setId("test-task-valid-no-source");
        
        String generatedContent = "This is generated content without source material. We can still check consistency, bias, and logical coherence.";
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(generatedContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn(null);
        
        // Mock quality check results (no accuracy or hallucination checks without source)
        QualityCheckResult consistencyResult = createMockQualityCheckResult(QualityCheckType.CONSISTENCY, 0.90);
        QualityCheckResult biasResult = createMockQualityCheckResult(QualityCheckType.BIAS_DETECTION, 0.95);
        QualityCheckResult coherenceResult = createMockQualityCheckResult(QualityCheckType.LOGICAL_COHERENCE, 0.92);
        
        // Mock chat client responses
        setupMockChatClientResponses();
        
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.CONSISTENCY), anyString()))
            .thenReturn(consistencyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.BIAS_DETECTION), anyString()))
            .thenReturn(biasResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.LOGICAL_COHERENCE), anyString()))
            .thenReturn(coherenceResult);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-valid-no-source", result.getTaskId());
        
        // Should have fewer checks (no accuracy or hallucination detection)
        verify(mockResponseParser, times(3)).parseQualityCheckResponse(any(QualityCheckType.class), anyString());
        verify(mockResponseParser, never()).parseQualityCheckResponse(eq(QualityCheckType.ACCURACY), anyString());
        verify(mockResponseParser, never()).parseQualityCheckResponse(eq(QualityCheckType.HALLUCINATION_DETECTION), anyString());
    }

    @Test
    void testProcessWithConfig_EmptySourceContent() {
        AgentTask task = new AgentTask();
        task.setId("test-task-empty-source");
        
        String generatedContent = "Generated content with empty source.";
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(generatedContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn("   "); // Whitespace only
        
        // Mock quality check results (should treat empty source same as null)
        QualityCheckResult consistencyResult = createMockQualityCheckResult(QualityCheckType.CONSISTENCY, 0.90);
        QualityCheckResult biasResult = createMockQualityCheckResult(QualityCheckType.BIAS_DETECTION, 0.95);
        QualityCheckResult coherenceResult = createMockQualityCheckResult(QualityCheckType.LOGICAL_COHERENCE, 0.92);
        
        setupMockChatClientResponses();
        
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.CONSISTENCY), anyString()))
            .thenReturn(consistencyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.BIAS_DETECTION), anyString()))
            .thenReturn(biasResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.LOGICAL_COHERENCE), anyString()))
            .thenReturn(coherenceResult);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-empty-source", result.getTaskId());
        
        // Should have only 3 checks (no accuracy or hallucination with empty source)
        verify(mockResponseParser, times(3)).parseQualityCheckResponse(any(QualityCheckType.class), anyString());
    }

    @Test
    void testProcessWithConfig_QualityCheckFailure() {
        AgentTask task = new AgentTask();
        task.setId("test-task-check-failure");
        
        String generatedContent = "Content that will cause quality check failure.";
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(generatedContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn(null);
        
        // Mock chat client to throw exception
        when(mockChatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenThrow(new RuntimeException("Quality check service unavailable"));

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-check-failure", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("Quality check failed"));
    }

    @Test
    void testProcessWithConfig_ParsingFailure() {
        AgentTask task = new AgentTask();
        task.setId("test-task-parsing-failure");
        
        String generatedContent = "Content with parsing issues.";
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(generatedContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn(null);
        
        setupMockChatClientResponses();
        
        // Mock parser to throw exception
        when(mockResponseParser.parseQualityCheckResponse(any(QualityCheckType.class), anyString()))
            .thenThrow(new RuntimeException("Failed to parse quality check response"));

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("test-task-parsing-failure", result.getTaskId());
        assertTrue(result.getErrorMessage().contains("Quality check failed"));
    }

    @Test
    void testProcessWithConfig_LongContentTruncation() {
        AgentTask task = new AgentTask();
        task.setId("test-task-long-content");
        
        // Create content longer than MAX_CONTENT_LENGTH (8000 characters)
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longContent.append("This is a very long piece of content that will be truncated. ");
        }
        String veryLongContent = longContent.toString(); // Should be > 8000 chars
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(veryLongContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn(null);
        
        QualityCheckResult consistencyResult = createMockQualityCheckResult(QualityCheckType.CONSISTENCY, 0.90);
        QualityCheckResult biasResult = createMockQualityCheckResult(QualityCheckType.BIAS_DETECTION, 0.95);
        QualityCheckResult coherenceResult = createMockQualityCheckResult(QualityCheckType.LOGICAL_COHERENCE, 0.92);
        
        setupMockChatClientResponses();
        
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.CONSISTENCY), anyString()))
            .thenReturn(consistencyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.BIAS_DETECTION), anyString()))
            .thenReturn(biasResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.LOGICAL_COHERENCE), anyString()))
            .thenReturn(coherenceResult);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-long-content", result.getTaskId());
        
        // Verify that content was processed (should be truncated internally but still work)
        verify(mockResponseParser, times(3)).parseQualityCheckResponse(any(QualityCheckType.class), anyString());
    }

    @Test
    void testProcessWithConfig_WithQualityIssues() {
        AgentTask task = new AgentTask();
        task.setId("test-task-with-issues");
        
        String generatedContent = "Content with potential quality issues.";
        String sourceContent = "Source material for comparison.";
        
        when(mockResponseParser.extractGeneratedContent(task)).thenReturn(generatedContent);
        when(mockResponseParser.extractSourceContent(task)).thenReturn(sourceContent);
        
        // Create quality check results with issues
        QualityIssue issue1 = mock(QualityIssue.class);
        when(issue1.isActionable()).thenReturn(true);
        when(issue1.getShortDescription()).thenReturn("Inconsistent data");
        
        QualityCheckResult accuracyResult = QualityCheckResult.withIssues(
            QualityCheckType.ACCURACY, 0.65, Arrays.asList(issue1), Arrays.asList("Fix factual errors"));
        QualityCheckResult consistencyResult = createMockQualityCheckResult(QualityCheckType.CONSISTENCY, 0.80);
        QualityCheckResult biasResult = createMockQualityCheckResult(QualityCheckType.BIAS_DETECTION, 0.90);
        QualityCheckResult hallucinationResult = createMockQualityCheckResult(QualityCheckType.HALLUCINATION_DETECTION, 0.75);
        QualityCheckResult coherenceResult = createMockQualityCheckResult(QualityCheckType.LOGICAL_COHERENCE, 0.85);
        
        setupMockChatClientResponses();
        
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.ACCURACY), anyString()))
            .thenReturn(accuracyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.CONSISTENCY), anyString()))
            .thenReturn(consistencyResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.BIAS_DETECTION), anyString()))
            .thenReturn(biasResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.HALLUCINATION_DETECTION), anyString()))
            .thenReturn(hallucinationResult);
        when(mockResponseParser.parseQualityCheckResponse(eq(QualityCheckType.LOGICAL_COHERENCE), anyString()))
            .thenReturn(coherenceResult);

        AgentResult result = agent.processWithConfig(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test-task-with-issues", result.getTaskId());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) result.getResultData();
        
        // Should have quality issues reported
        assertTrue(((Integer) report.get("totalIssues")) > 0);
        assertNotNull(report.get("recommendations"));
    }

    private QualityCheckResult createMockQualityCheckResult(QualityCheckType type, double score) {
        return QualityCheckResult.success(type, score, "Quality check completed successfully");
    }

    private void setupMockChatClientResponses() {
        when(mockChatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn("""
            {
                "score": 0.85,
                "issues": [],
                "summary": "Quality check completed successfully"
            }
            """);
    }
}

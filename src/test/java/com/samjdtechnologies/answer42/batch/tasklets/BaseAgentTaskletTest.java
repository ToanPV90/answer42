package com.samjdtechnologies.answer42.batch.tasklets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;

import com.samjdtechnologies.answer42.model.agent.AgentResult;

public class BaseAgentTaskletTest {

    @Mock
    private ChunkContext mockChunkContext;
    
    @Mock
    private StepContext mockStepContext;
    
    @Mock
    private StepExecution mockStepExecution;
    
    @Mock
    private JobExecution mockJobExecution;
    
    @Mock
    private Map<String, Object> mockJobParameters;
    
    @Mock
    private ExecutionContext mockExecutionContext;

    private BaseAgentTasklet tasklet;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create a concrete implementation for testing
        tasklet = new BaseAgentTasklet() {
            @Override
            public org.springframework.batch.repeat.RepeatStatus execute(
                    org.springframework.batch.core.StepContribution contribution, 
                    ChunkContext chunkContext) throws Exception {
                return org.springframework.batch.repeat.RepeatStatus.FINISHED;
            }
        };
        
        // Setup mock chain
        when(mockChunkContext.getStepContext()).thenReturn(mockStepContext);
        when(mockStepContext.getStepExecution()).thenReturn(mockStepExecution);
        when(mockStepExecution.getJobExecution()).thenReturn(mockJobExecution);
        when(mockStepContext.getJobParameters()).thenReturn(mockJobParameters);
        when(mockJobExecution.getExecutionContext()).thenReturn(mockExecutionContext);
    }

    @Test
    void testGetUserId_FromJobParameters() {
        UUID testUserId = UUID.randomUUID();
        when(mockJobParameters.get("userId")).thenReturn(testUserId.toString());

        UUID result = tasklet.getUserId(mockChunkContext);

        assertEquals(testUserId, result);
    }

    @Test
    void testGetUserId_FromAlternativeParameterNames() {
        UUID testUserId = UUID.randomUUID();
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockJobParameters.get("user")).thenReturn(testUserId.toString());

        UUID result = tasklet.getUserId(mockChunkContext);

        assertEquals(testUserId, result);
    }

    @Test
    void testGetUserId_FromExecutionContext() {
        UUID testUserId = UUID.randomUUID();
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockJobParameters.get("user")).thenReturn(null);
        when(mockJobParameters.get("userGuid")).thenReturn(null);
        when(mockJobParameters.get("user_id")).thenReturn(null);
        when(mockExecutionContext.get("userId")).thenReturn(testUserId.toString());

        UUID result = tasklet.getUserId(mockChunkContext);

        assertEquals(testUserId, result);
    }

    @Test
    void testGetUserId_FromExecutionContextAsUUID() {
        UUID testUserId = UUID.randomUUID();
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockJobParameters.get("user")).thenReturn(null);
        when(mockJobParameters.get("userGuid")).thenReturn(null);
        when(mockJobParameters.get("user_id")).thenReturn(null);
        when(mockExecutionContext.get("userId")).thenReturn(testUserId);

        UUID result = tasklet.getUserId(mockChunkContext);

        assertEquals(testUserId, result);
    }

    @Test
    void testGetUserId_MissingParameter() {
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockJobParameters.get("user")).thenReturn(null);
        when(mockJobParameters.get("userGuid")).thenReturn(null);
        when(mockJobParameters.get("user_id")).thenReturn(null);
        when(mockExecutionContext.get("userId")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tasklet.getUserId(mockChunkContext)
        );

        assertTrue(exception.getMessage().contains("Required parameter 'userId' is missing"));
    }

    @Test
    void testGetUserId_EmptyParameter() {
        when(mockJobParameters.get("userId")).thenReturn("   ");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tasklet.getUserId(mockChunkContext)
        );

        assertTrue(exception.getMessage().contains("Required parameter 'userId' is missing"));
    }

    @Test
    void testGetUserId_InvalidUUID() {
        when(mockJobParameters.get("userId")).thenReturn("invalid-uuid");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tasklet.getUserId(mockChunkContext)
        );

        assertTrue(exception.getMessage().contains("Error extracting user ID"));
    }

    @Test
    void testGetPaperId_FromJobParameters() {
        UUID testPaperId = UUID.randomUUID();
        when(mockJobParameters.get("paperId")).thenReturn(testPaperId.toString());

        UUID result = tasklet.getPaperId(mockChunkContext);

        assertEquals(testPaperId, result);
    }

    @Test
    void testGetPaperId_FromAlternativeParameterNames() {
        UUID testPaperId = UUID.randomUUID();
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("paper")).thenReturn(testPaperId.toString());

        UUID result = tasklet.getPaperId(mockChunkContext);

        assertEquals(testPaperId, result);
    }

    @Test
    void testGetPaperId_FromExecutionContext() {
        UUID testPaperId = UUID.randomUUID();
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("paper")).thenReturn(null);
        when(mockJobParameters.get("paperGuid")).thenReturn(null);
        when(mockJobParameters.get("paper_id")).thenReturn(null);
        when(mockJobParameters.get("documentId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(testPaperId.toString());

        UUID result = tasklet.getPaperId(mockChunkContext);

        assertEquals(testPaperId, result);
    }

    @Test
    void testGetPaperId_FromExecutionContextAsUUID() {
        UUID testPaperId = UUID.randomUUID();
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("paper")).thenReturn(null);
        when(mockJobParameters.get("paperGuid")).thenReturn(null);
        when(mockJobParameters.get("paper_id")).thenReturn(null);
        when(mockJobParameters.get("documentId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(testPaperId);

        UUID result = tasklet.getPaperId(mockChunkContext);

        assertEquals(testPaperId, result);
    }

    @Test
    void testGetPaperId_MissingParameter() {
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("paper")).thenReturn(null);
        when(mockJobParameters.get("paperGuid")).thenReturn(null);
        when(mockJobParameters.get("paper_id")).thenReturn(null);
        when(mockJobParameters.get("documentId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tasklet.getPaperId(mockChunkContext)
        );

        assertTrue(exception.getMessage().contains("Paper ID is required but not found"));
    }

    @Test
    void testGetPreviousStepResult_Success() {
        AgentResult expectedResult = AgentResult.success("test-task", new HashMap<>());
        when(mockExecutionContext.get("testResult")).thenReturn(expectedResult);

        AgentResult result = tasklet.getPreviousStepResult(mockChunkContext, "testResult");

        assertEquals(expectedResult, result);
    }

    @Test
    void testGetPreviousStepResult_NotFound() {
        when(mockExecutionContext.get("missingResult")).thenReturn(null);

        AgentResult result = tasklet.getPreviousStepResult(mockChunkContext, "missingResult");

        assertNull(result);
    }

    @Test
    void testGetPreviousStepResult_WrongType() {
        when(mockExecutionContext.get("wrongType")).thenReturn("not an AgentResult");

        AgentResult result = tasklet.getPreviousStepResult(mockChunkContext, "wrongType");

        assertNull(result);
    }

    @Test
    void testGetPreviousStepResult_Exception() {
        when(mockExecutionContext.get("errorResult")).thenThrow(new RuntimeException("Test error"));

        AgentResult result = tasklet.getPreviousStepResult(mockChunkContext, "errorResult");

        assertNull(result);
    }

    @Test
    void testStoreStepResult_Success() {
        AgentResult testResult = AgentResult.success("test-task", new HashMap<>());

        tasklet.storeStepResult(mockChunkContext, "testKey", testResult);

        verify(mockExecutionContext).put("testKey", testResult);
    }

    @Test
    void testStoreStepResult_Exception() {
        AgentResult testResult = AgentResult.success("test-task", new HashMap<>());
        doThrow(new RuntimeException("Storage error")).when(mockExecutionContext).put("testKey", testResult);

        // Should not throw exception, just log error
        assertDoesNotThrow(() -> tasklet.storeStepResult(mockChunkContext, "testKey", testResult));
    }

    @Test
    void testHandleTaskletFailure() {
        Exception testError = new RuntimeException("Test failure");
        String taskletName = "TestTasklet";
        String resultKey = "testResult";

        tasklet.handleTaskletFailure(mockChunkContext, taskletName, resultKey, testError);

        verify(mockExecutionContext).put(eq(resultKey), any(AgentResult.class));
    }

    @Test
    void testLogProcessingComplete() {
        UUID testPaperId = UUID.randomUUID();
        Instant startTime = Instant.now().minusSeconds(5);
        String taskletName = "TestTasklet";

        // Should not throw exception
        assertDoesNotThrow(() -> tasklet.logProcessingComplete(taskletName, testPaperId, startTime));
    }

    @Test
    void testValidatePreviousStepResult_Success() {
        AgentResult successResult = AgentResult.success("test-task", new HashMap<>());

        // Should not throw exception
        assertDoesNotThrow(() -> tasklet.validatePreviousStepResult(successResult, "TestStep"));
    }

    @Test
    void testValidatePreviousStepResult_NullResult() {
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tasklet.validatePreviousStepResult(null, "TestStep")
        );

        assertEquals("TestStep result not available", exception.getMessage());
    }

    @Test
    void testValidatePreviousStepResult_FailedResult() {
        AgentResult failedResult = AgentResult.failure("test-task", "Test error message");

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tasklet.validatePreviousStepResult(failedResult, "TestStep")
        );

        assertEquals("TestStep failed: Test error message", exception.getMessage());
    }

    @Test
    void testExtractStringFromResult_Success() {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("key1", "value1");
        resultData.put("key2", "value2");
        AgentResult testResult = AgentResult.success("test-task", resultData);

        String result = tasklet.extractStringFromResult(testResult, "key1", "key3");

        assertEquals("value1", result);
    }

    @Test
    void testExtractStringFromResult_FallbackKey() {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("key2", "value2");
        AgentResult testResult = AgentResult.success("test-task", resultData);

        String result = tasklet.extractStringFromResult(testResult, "key1", "key2", "key3");

        assertEquals("value2", result);
    }

    @Test
    void testExtractStringFromResult_NullResult() {
        String result = tasklet.extractStringFromResult(null, "key1");

        assertNull(result);
    }

    @Test
    void testExtractStringFromResult_NullResultData() {
        AgentResult testResult = AgentResult.success("test-task", null);

        String result = tasklet.extractStringFromResult(testResult, "key1");

        assertNull(result);
    }

    @Test
    void testExtractStringFromResult_EmptyString() {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("key1", "   ");
        resultData.put("key2", "value2");
        AgentResult testResult = AgentResult.success("test-task", resultData);

        String result = tasklet.extractStringFromResult(testResult, "key1", "key2");

        assertEquals("value2", result);
    }

    @Test
    void testExtractStringFromResult_NonStringValue() {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("key1", 123);
        resultData.put("key2", "value2");
        AgentResult testResult = AgentResult.success("test-task", resultData);

        String result = tasklet.extractStringFromResult(testResult, "key1", "key2");

        assertEquals("value2", result);
    }

    @Test
    void testExtractStringFromResult_NoMatchingKeys() {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("otherKey", "otherValue");
        AgentResult testResult = AgentResult.success("test-task", resultData);

        String result = tasklet.extractStringFromResult(testResult, "key1", "key2");

        assertNull(result);
    }

    @Test
    void testGetUserId_WithTrimming() {
        UUID testUserId = UUID.randomUUID();
        when(mockJobParameters.get("userId")).thenReturn("  " + testUserId.toString() + "  ");

        UUID result = tasklet.getUserId(mockChunkContext);

        assertEquals(testUserId, result);
    }

    @Test
    void testGetPaperId_WithTrimming() {
        UUID testPaperId = UUID.randomUUID();
        when(mockJobParameters.get("paperId")).thenReturn("  " + testPaperId.toString() + "  ");

        UUID result = tasklet.getPaperId(mockChunkContext);

        assertEquals(testPaperId, result);
    }

    @Test
    void testGetUserId_AlternativeParameterSequence() {
        UUID testUserId = UUID.randomUUID();
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockJobParameters.get("user")).thenReturn(null);
        when(mockJobParameters.get("userGuid")).thenReturn(testUserId.toString());

        UUID result = tasklet.getUserId(mockChunkContext);

        assertEquals(testUserId, result);
    }

    @Test
    void testGetPaperId_AlternativeParameterSequence() {
        UUID testPaperId = UUID.randomUUID();
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("paper")).thenReturn(null);
        when(mockJobParameters.get("paperGuid")).thenReturn(null);
        when(mockJobParameters.get("paper_id")).thenReturn(testPaperId.toString());

        UUID result = tasklet.getPaperId(mockChunkContext);

        assertEquals(testPaperId, result);
    }
}

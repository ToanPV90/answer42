package com.samjdtechnologies.answer42.batch.tasklets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.db.AgentTask;
import com.samjdtechnologies.answer42.service.agent.CitationFormatterAgent;

public class CitationFormatterTaskletTest {

    @Mock
    private CitationFormatterAgent mockCitationFormatterAgent;
    
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
    
    @Mock
    private StepContribution mockStepContribution;

    private CitationFormatterTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new CitationFormatterTasklet(mockCitationFormatterAgent);
        
        testPaperId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        
        // Setup mock chain
        when(mockChunkContext.getStepContext()).thenReturn(mockStepContext);
        when(mockStepContext.getStepExecution()).thenReturn(mockStepExecution);
        when(mockStepExecution.getJobExecution()).thenReturn(mockJobExecution);
        when(mockStepContext.getJobParameters()).thenReturn(mockJobParameters);
        when(mockJobExecution.getExecutionContext()).thenReturn(mockExecutionContext);
        
        // Setup default parameter returns
        when(mockJobParameters.get("paperId")).thenReturn(testPaperId.toString());
        when(mockJobParameters.get("userId")).thenReturn(testUserId.toString());
    }

    @Test
    void testConstructor() {
        CitationFormatterTasklet tasklet = new CitationFormatterTasklet(mockCitationFormatterAgent);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success() throws Exception {
        // Setup previous step result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Sample paper content with citations [1][2]");
        paperResultData.put("title", "Test Paper Title");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup citation formatter agent result
        Map<String, Object> citationResultData = new HashMap<>();
        citationResultData.put("formattedCitations", Map.of("APA", "Citation 1", "MLA", "Citation 2"));
        citationResultData.put("citationCount", 2);
        citationResultData.put("rawCitations", "[1][2]");
        AgentResult agentResult = AgentResult.success("citation-formatter", citationResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockCitationFormatterAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithMinimalContent() throws Exception {
        // Setup previous step result with minimal content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("content", "Basic paper text");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup citation formatter agent result
        Map<String, Object> citationResultData = new HashMap<>();
        citationResultData.put("citationCount", 0);
        AgentResult agentResult = AgentResult.success("citation-formatter", citationResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockCitationFormatterAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithAllCitationFields() throws Exception {
        // Setup previous step result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("extractedText", "Comprehensive paper content with multiple citations");
        paperResultData.put("paperTitle", "Comprehensive Test Paper");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup comprehensive citation formatter agent result
        Map<String, Object> citationResultData = new HashMap<>();
        citationResultData.put("formattedCitations", Map.of("APA", "Citation APA", "MLA", "Citation MLA"));
        citationResultData.put("rawCitations", "[1][2][3]");
        citationResultData.put("citationCount", 3);
        citationResultData.put("validationResults", Map.of("valid", 2, "invalid", 1));
        citationResultData.put("supportedStyles", "APA,MLA,Chicago,IEEE");
        citationResultData.put("bibliography", "Bibliography entries");
        citationResultData.put("doiValidation", Map.of("validDOIs", 2));
        AgentResult agentResult = AgentResult.success("citation-formatter", citationResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockCitationFormatterAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_MissingPaperId() {
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("paper")).thenReturn(null);
        when(mockJobParameters.get("paperGuid")).thenReturn(null);
        when(mockJobParameters.get("paper_id")).thenReturn(null);
        when(mockJobParameters.get("documentId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(null);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Citation formatting failed"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_MissingUserId() {
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockJobParameters.get("user")).thenReturn(null);
        when(mockJobParameters.get("userGuid")).thenReturn(null);
        when(mockJobParameters.get("user_id")).thenReturn(null);
        when(mockExecutionContext.get("userId")).thenReturn(null);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Citation formatting failed"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_MissingPaperProcessorResult() {
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(null);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Citation formatting failed"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_FailedPaperProcessorResult() {
        AgentResult failedResult = AgentResult.failure("paper-processor", "Processing failed");
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(failedResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Citation formatting failed"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_NoTextContent() {
        // Setup previous step result without text content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("metadata", "some metadata");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("No text content available for citation formatting"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_EmptyTextContent() {
        // Setup previous step result with empty text content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "   ");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("No text content available for citation formatting"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_AgentProcessingFails() throws Exception {
        // Setup previous step result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Sample paper content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup failed agent result
        AgentResult failedResult = AgentResult.failure("citation-formatter", "Citation processing failed");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(failedResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Citation formatting failed: Citation processing failed"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_AgentThrowsException() throws Exception {
        // Setup previous step result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Sample paper content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Agent processing error"));
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Citation formatting failed"));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ExtractContentFromAlternativeFields() throws Exception {
        // Setup previous step result with content in alternative field
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Alternative field content");
        paperResultData.put("documentTitle", "Alternative Title Field");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup citation formatter agent result
        Map<String, Object> citationResultData = new HashMap<>();
        citationResultData.put("citationCount", 0);
        AgentResult agentResult = AgentResult.success("citation-formatter", citationResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockCitationFormatterAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_ParameterExtractionFromExecutionContext() throws Exception {
        // Setup IDs to come from execution context instead of job parameters
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(testPaperId);
        when(mockExecutionContext.get("userId")).thenReturn(testUserId);
        
        // Setup previous step result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content from execution context");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup citation formatter agent result
        Map<String, Object> citationResultData = new HashMap<>();
        citationResultData.put("citationCount", 1);
        AgentResult agentResult = AgentResult.success("citation-formatter", citationResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockCitationFormatterAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_NullAgentResultData() throws Exception {
        // Setup previous step result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Sample content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent result with null data
        AgentResult agentResult = AgentResult.success("citation-formatter", null);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockCitationFormatterAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockCitationFormatterAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("citationFormatterResult"), any(AgentResult.class));
    }
}

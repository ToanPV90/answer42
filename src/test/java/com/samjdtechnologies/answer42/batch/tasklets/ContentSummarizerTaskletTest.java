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
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.service.agent.ContentSummarizerAgent;

public class ContentSummarizerTaskletTest {

    @Mock
    private ContentSummarizerAgent mockContentSummarizerAgent;
    
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

    private ContentSummarizerTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new ContentSummarizerTasklet(mockContentSummarizerAgent);
        
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
        ContentSummarizerTasklet tasklet = new ContentSummarizerTasklet(mockContentSummarizerAgent);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success_AllSummaryTypes() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "This is a comprehensive academic paper discussing artificial intelligence and machine learning applications in modern research. The paper contains detailed analysis of various algorithms and their practical implementations.");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup successful summarizer agent results for all types
        setupSuccessfulSummaryResults();
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockContentSummarizerAgent, times(3)).process(any(AgentTask.class)); // brief, standard, detailed
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithAlternativeContentField() throws Exception {
        // Setup paper processor result with alternative field name
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("extractedText", "Alternative field content for summarization processing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup successful summarizer agent results
        setupSuccessfulSummaryResults();
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockContentSummarizerAgent, times(3)).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_PartialFailure() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("content", "Content for partial summarization testing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup mixed success/failure results
        setupPartialSummaryResults();
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockContentSummarizerAgent, times(3)).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithComprehensiveMetadata() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Comprehensive academic content with detailed analysis");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup comprehensive summarizer results with all metadata
        setupComprehensiveSummaryResults();
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockContentSummarizerAgent, times(3)).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Content summarization failed"));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Content summarization failed"));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_MissingPaperProcessorResult() {
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(null);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Content summarization failed"));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_FailedPaperProcessorResult() {
        AgentResult failedResult = AgentResult.failure("paper-processor", "Paper processing failed");
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(failedResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Content summarization failed"));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_NoTextContent() {
        // Setup paper processor result without text content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("metadata", "some metadata");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("No text content available for summarization"));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_EmptyTextContent() {
        // Setup paper processor result with empty text content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "   ");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("No text content available for summarization"));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_AllSummaryTypesFailure() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for failed summarization");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup all summary types to fail
        setupFailedSummaryResults();
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockContentSummarizerAgent, times(3)).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_SummarizerAgentException() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for exception testing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Summarization service error"));
        when(mockContentSummarizerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockExecutionContext).put(eq("contentSummarizerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ParameterExtractionFromExecutionContext() throws Exception {
        // Setup IDs to come from execution context instead of job parameters
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(testPaperId);
        when(mockExecutionContext.get("userId")).thenReturn(testUserId);
        
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content from execution context");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup successful summarizer results
        setupSuccessfulSummaryResults();
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockContentSummarizerAgent, times(3)).process(any(AgentTask.class));
    }

    private void setupSuccessfulSummaryResults() throws Exception {
        // Brief summary result
        Map<String, Object> briefData = new HashMap<>();
        briefData.put("summary", "Brief summary of the content");
        briefData.put("wordCount", 50);
        AgentResult briefResult = AgentResult.success("summarizer_brief", briefData);
        
        // Standard summary result
        Map<String, Object> standardData = new HashMap<>();
        standardData.put("summary", "Standard summary with more details");
        standardData.put("wordCount", 150);
        AgentResult standardResult = AgentResult.success("summarizer_standard", standardData);
        
        // Detailed summary result
        Map<String, Object> detailedData = new HashMap<>();
        detailedData.put("summary", "Detailed comprehensive summary");
        detailedData.put("wordCount", 300);
        AgentResult detailedResult = AgentResult.success("summarizer_detailed", detailedData);
        
        when(mockContentSummarizerAgent.process(any(AgentTask.class)))
            .thenReturn(CompletableFuture.completedFuture(briefResult))
            .thenReturn(CompletableFuture.completedFuture(standardResult))
            .thenReturn(CompletableFuture.completedFuture(detailedResult));
    }

    private void setupPartialSummaryResults() throws Exception {
        // Brief summary succeeds
        Map<String, Object> briefData = new HashMap<>();
        briefData.put("summary", "Brief summary");
        briefData.put("wordCount", 50);
        AgentResult briefResult = AgentResult.success("summarizer_brief", briefData);
        
        // Standard summary fails
        AgentResult standardResult = AgentResult.failure("summarizer_standard", "Standard summarization failed");
        
        // Detailed summary succeeds
        Map<String, Object> detailedData = new HashMap<>();
        detailedData.put("summary", "Detailed summary");
        detailedData.put("wordCount", 300);
        AgentResult detailedResult = AgentResult.success("summarizer_detailed", detailedData);
        
        when(mockContentSummarizerAgent.process(any(AgentTask.class)))
            .thenReturn(CompletableFuture.completedFuture(briefResult))
            .thenReturn(CompletableFuture.completedFuture(standardResult))
            .thenReturn(CompletableFuture.completedFuture(detailedResult));
    }

    private void setupComprehensiveSummaryResults() throws Exception {
        // Brief summary with comprehensive metadata
        Map<String, Object> briefData = new HashMap<>();
        briefData.put("summary", "Brief summary");
        briefData.put("wordCount", 50);
        briefData.put("keyFindings", "Key finding 1, Key finding 2");
        briefData.put("qualityScore", 0.85);
        AgentResult briefResult = AgentResult.success("summarizer_brief", briefData);
        
        // Standard summary with comprehensive metadata
        Map<String, Object> standardData = new HashMap<>();
        standardData.put("summary", "Standard summary");
        standardData.put("wordCount", 150);
        standardData.put("keyFindings", "Comprehensive findings");
        standardData.put("qualityScore", 0.90);
        AgentResult standardResult = AgentResult.success("summarizer_standard", standardData);
        
        // Detailed summary with comprehensive metadata
        Map<String, Object> detailedData = new HashMap<>();
        detailedData.put("summary", "Detailed summary");
        detailedData.put("wordCount", 300);
        detailedData.put("keyFindings", "Detailed analysis findings");
        detailedData.put("qualityScore", 0.92);
        AgentResult detailedResult = AgentResult.success("summarizer_detailed", detailedData);
        
        when(mockContentSummarizerAgent.process(any(AgentTask.class)))
            .thenReturn(CompletableFuture.completedFuture(briefResult))
            .thenReturn(CompletableFuture.completedFuture(standardResult))
            .thenReturn(CompletableFuture.completedFuture(detailedResult));
    }

    private void setupFailedSummaryResults() throws Exception {
        AgentResult briefResult = AgentResult.failure("summarizer_brief", "Brief summarization failed");
        AgentResult standardResult = AgentResult.failure("summarizer_standard", "Standard summarization failed");
        AgentResult detailedResult = AgentResult.failure("summarizer_detailed", "Detailed summarization failed");
        
        when(mockContentSummarizerAgent.process(any(AgentTask.class)))
            .thenReturn(CompletableFuture.completedFuture(briefResult))
            .thenReturn(CompletableFuture.completedFuture(standardResult))
            .thenReturn(CompletableFuture.completedFuture(detailedResult));
    }
}

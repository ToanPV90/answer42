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
import com.samjdtechnologies.answer42.service.agent.RelatedPaperDiscoveryAgent;

public class RelatedPaperDiscoveryTaskletTest {

    @Mock
    private RelatedPaperDiscoveryAgent mockDiscoveryAgent;
    
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

    private RelatedPaperDiscoveryTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new RelatedPaperDiscoveryTasklet(mockDiscoveryAgent);
        
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
        RelatedPaperDiscoveryTasklet tasklet = new RelatedPaperDiscoveryTasklet(mockDiscoveryAgent);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success_WithPaperResultOnly() throws Exception {
        // Setup paper processor result only
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Test Paper Title");
        paperResultData.put("textContent", "This is a test paper content for discovery analysis");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(null);
        
        // Setup discovery agent result
        Map<String, Object> discoveryResultData = new HashMap<>();
        discoveryResultData.put("totalPapersFound", 15);
        discoveryResultData.put("relevantPapers", "papers list");
        AgentResult agentResult = AgentResult.success("related-paper-discovery", discoveryResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("relatedPaperDiscoveryResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithBothPaperAndSummaryResults() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("paperTitle", "Academic Research Paper");
        paperResultData.put("extractedText", "This is comprehensive academic content for related paper analysis");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup content summarizer result
        Map<String, Object> summaryResultData = new HashMap<>();
        summaryResultData.put("standardSummary", "This paper discusses advanced research methodologies");
        AgentResult summaryResult = AgentResult.success("content-summarizer", summaryResultData);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup discovery agent result
        Map<String, Object> discoveryResultData = new HashMap<>();
        discoveryResultData.put("totalPapersFound", 25);
        discoveryResultData.put("synthesizedResults", "comprehensive analysis");
        AgentResult agentResult = AgentResult.success("related-paper-discovery", discoveryResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("relatedPaperDiscoveryResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithLongContent() throws Exception {
        // Setup paper processor result with long content (>3000 chars)
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longContent.append("This is part ").append(i).append(" of the long paper content. ");
        }
        
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Long Research Paper");
        paperResultData.put("content", longContent.toString());
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup discovery agent result
        Map<String, Object> discoveryResultData = new HashMap<>();
        discoveryResultData.put("totalPapersFound", 30);
        AgentResult agentResult = AgentResult.success("related-paper-discovery", discoveryResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithFailedSummaryResult() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("documentTitle", "Research Paper");
        paperResultData.put("text", "Paper content for discovery");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup failed summary result (should be ignored)
        AgentResult failedSummaryResult = AgentResult.failure("content-summarizer", "Summary failed");
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(failedSummaryResult);
        
        // Setup discovery agent result
        AgentResult agentResult = AgentResult.success("related-paper-discovery", new HashMap<>());
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithEmptyPaperContent() throws Exception {
        // Setup paper processor result with minimal content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Minimal Paper");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup discovery agent result
        AgentResult agentResult = AgentResult.success("related-paper-discovery", new HashMap<>());
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithDiscoveryWarning() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Test Paper");
        paperResultData.put("textContent", "Content for discovery");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup discovery agent result with warning (success but with issues)
        Map<String, Object> discoveryResultData = new HashMap<>();
        discoveryResultData.put("totalPapersFound", 5);
        discoveryResultData.put("warnings", "Limited results due to API constraints");
        AgentResult agentResult = AgentResult.success("related-paper-discovery", discoveryResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
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
        
        assertTrue(exception.getMessage().contains("Related paper discovery failed"));
        verify(mockExecutionContext).put(eq("relatedPaperDiscoveryResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Related paper discovery failed"));
        verify(mockExecutionContext).put(eq("relatedPaperDiscoveryResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_DiscoveryAgentFailure() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Test Paper");
        paperResultData.put("textContent", "Content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup failed discovery agent result
        AgentResult failedResult = AgentResult.failure("related-paper-discovery", "Discovery service unavailable");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(failedResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("relatedPaperDiscoveryResult"), eq(failedResult));
    }

    @Test
    void testExecute_DiscoveryAgentException() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Test Paper");
        paperResultData.put("textContent", "Content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Discovery processing error"));
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Related paper discovery failed"));
        verify(mockExecutionContext).put(eq("relatedPaperDiscoveryResult"), any(AgentResult.class));
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
        paperResultData.put("title", "Context Test Paper");
        paperResultData.put("textContent", "Content from execution context");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup discovery agent result
        AgentResult agentResult = AgentResult.success("related-paper-discovery", new HashMap<>());
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_NullPaperProcessorResult() throws Exception {
        // Paper processor result is null (optional dependency)
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(null);
        
        // Setup discovery agent result
        AgentResult agentResult = AgentResult.success("related-paper-discovery", new HashMap<>());
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_ExtractFromAlternativeFields() throws Exception {
        // Setup paper processor result with content in alternative fields
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("documentTitle", "Alternative Title Field");
        paperResultData.put("text", "Alternative content field");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup summary with alternative field
        Map<String, Object> summaryResultData = new HashMap<>();
        summaryResultData.put("content", "Alternative summary field");
        AgentResult summaryResult = AgentResult.success("content-summarizer", summaryResultData);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup discovery agent result
        AgentResult agentResult = AgentResult.success("related-paper-discovery", new HashMap<>());
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_NullSummaryResultData() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("title", "Test Paper");
        paperResultData.put("textContent", "Content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup summary result with null data
        AgentResult summaryResult = AgentResult.success("content-summarizer", null);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup discovery agent result
        AgentResult agentResult = AgentResult.success("related-paper-discovery", new HashMap<>());
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockDiscoveryAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockDiscoveryAgent).process(any(AgentTask.class));
    }
}

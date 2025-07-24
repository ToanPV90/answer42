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
import com.samjdtechnologies.answer42.service.agent.PerplexityResearchAgent;

public class PerplexityResearchTaskletTest {

    @Mock
    private PerplexityResearchAgent mockResearchAgent;
    
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

    private PerplexityResearchTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new PerplexityResearchTasklet(mockResearchAgent);
        
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
        PerplexityResearchTasklet tasklet = new PerplexityResearchTasklet(mockResearchAgent);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success_WithPaperResultOnly() throws Exception {
        // Setup paper processor result only
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Research paper content about artificial intelligence and machine learning applications");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(null);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 15);
        researchResultData.put("relatedPapersFound", 8);
        researchResultData.put("trendsAnalyzed", 3);
        researchResultData.put("researchFindings", "Comprehensive research completed");
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("perplexityResearchResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithBothPaperAndSummaryResults() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("extractedText", "Academic paper discussing quantum computing advances and applications");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup content summarizer result
        Map<String, Object> summaryResultData = new HashMap<>();
        summaryResultData.put("detailedSummary", "This paper explores quantum computing breakthroughs and their practical implementations");
        AgentResult summaryResult = AgentResult.success("content-summarizer", summaryResultData);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 20);
        researchResultData.put("relatedPapersFound", 12);
        researchResultData.put("trendsAnalyzed", 5);
        researchResultData.put("verificationScore", 0.92);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("perplexityResearchResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithLongContent() throws Exception {
        // Setup paper processor result with long content (>2000 chars)
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longContent.append("This is part ").append(i).append(" of the research paper content. ");
        }
        
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("content", longContent.toString());
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 25);
        researchResultData.put("contentTruncated", true);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithFailedSummaryResult() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Research content for external verification");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup failed summary result (should be ignored gracefully)
        AgentResult failedSummaryResult = AgentResult.failure("content-summarizer", "Summary generation failed");
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(failedSummaryResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 10);
        researchResultData.put("summaryUnavailable", true);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithMinimalContent() throws Exception {
        // Setup paper processor result with minimal content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Basic research content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 3);
        researchResultData.put("limitedScope", true);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithResearchFailure() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for research");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup research agent result with failure
        AgentResult agentResult = AgentResult.failure("perplexity-researcher", "Research API unavailable");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("perplexityResearchResult"), eq(agentResult));
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
        
        assertTrue(exception.getMessage().contains("Perplexity research failed"));
        verify(mockExecutionContext).put(eq("perplexityResearchResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Perplexity research failed"));
        verify(mockExecutionContext).put(eq("perplexityResearchResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ResearchAgentException() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for research");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Perplexity API connection error"));
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Perplexity research failed"));
        verify(mockExecutionContext).put(eq("perplexityResearchResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ExtractContentFromAlternativeFields() throws Exception {
        // Setup paper processor result with content in alternative field
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Alternative field content for research");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 8);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
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
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 12);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_NullPaperProcessorResult() throws Exception {
        // Paper processor result is null (optional dependency)
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(null);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 0);
        researchResultData.put("noContentProvided", true);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithSummaryFromAlternativeFields() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Research content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup summary with alternative field
        Map<String, Object> summaryResultData = new HashMap<>();
        summaryResultData.put("summary", "Alternative summary field content");
        AgentResult summaryResult = AgentResult.success("content-summarizer", summaryResultData);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 14);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithNullSummaryResultData() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for research");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup summary result with null data
        AgentResult summaryResult = AgentResult.success("content-summarizer", null);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 9);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithEmptyContent() throws Exception {
        // Setup paper processor result with empty content fields
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "   ");
        paperResultData.put("metadata", "some metadata");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup research agent result
        Map<String, Object> researchResultData = new HashMap<>();
        researchResultData.put("factsVerified", 0);
        researchResultData.put("emptyContent", true);
        AgentResult agentResult = AgentResult.success("perplexity-researcher", researchResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockResearchAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockResearchAgent).process(any(AgentTask.class));
    }
}

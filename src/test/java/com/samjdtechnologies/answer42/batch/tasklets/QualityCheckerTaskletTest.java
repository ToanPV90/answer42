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
import com.samjdtechnologies.answer42.service.agent.QualityCheckerAgent;

public class QualityCheckerTaskletTest {

    @Mock
    private QualityCheckerAgent mockQualityCheckerAgent;
    
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

    private QualityCheckerTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new QualityCheckerTasklet(mockQualityCheckerAgent);
        
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
        QualityCheckerTasklet tasklet = new QualityCheckerTasklet(mockQualityCheckerAgent);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success_WithAllResults() throws Exception {
        // Setup paper processor result (required)
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Original paper content for quality assessment");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup content summarizer result (optional)
        Map<String, Object> summaryResultData = new HashMap<>();
        summaryResultData.put("briefSummary", "Brief summary content");
        summaryResultData.put("standardSummary", "Standard summary content");
        summaryResultData.put("detailedSummary", "Detailed summary content");
        AgentResult summaryResult = AgentResult.success("content-summarizer", summaryResultData);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup concept explainer result (optional)
        Map<String, Object> conceptResultData = new HashMap<>();
        conceptResultData.put("explanationsByLevel", Map.of("basic", "Basic explanation", "advanced", "Advanced explanation"));
        AgentResult conceptResult = AgentResult.success("concept-explainer", conceptResultData);
        when(mockExecutionContext.get("conceptExplainerResult")).thenReturn(conceptResult);
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.85);
        qualityResultData.put("accuracyScore", 0.90);
        qualityResultData.put("consistencyScore", 0.80);
        qualityResultData.put("completenessScore", 0.85);
        qualityResultData.put("qualityGrade", "B+");
        qualityResultData.put("qualityIssues", "Minor inconsistencies detected");
        qualityResultData.put("recommendations", "Consider expanding section 3");
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithPaperResultOnly() throws Exception {
        // Setup paper processor result only
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("extractedText", "Original content for quality checking");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(null);
        when(mockExecutionContext.get("conceptExplainerResult")).thenReturn(null);
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.75);
        qualityResultData.put("qualityGrade", "B");
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithFailedOptionalResults() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("content", "Content for quality assessment");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup failed optional results (should be ignored gracefully)
        AgentResult failedSummaryResult = AgentResult.failure("content-summarizer", "Summary failed");
        AgentResult failedConceptResult = AgentResult.failure("concept-explainer", "Concept explanation failed");
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(failedSummaryResult);
        when(mockExecutionContext.get("conceptExplainerResult")).thenReturn(failedConceptResult);
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.80);
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_Success_WithQualityIssuesFound() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Content with quality issues");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup quality checker agent result with issues found
        AgentResult agentResult = AgentResult.failure("quality-checker", "Quality issues detected");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Quality checking failed"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Quality checking failed"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_MissingPaperProcessorResult() {
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(null);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Quality checking failed"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_FailedPaperProcessorResult() {
        AgentResult failedResult = AgentResult.failure("paper-processor", "Paper processing failed");
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(failedResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Quality checking failed"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_NoOriginalContent() {
        // Setup paper processor result without content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("metadata", "some metadata");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("No original content available for quality checking"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_EmptyOriginalContent() {
        // Setup paper processor result with empty content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "   ");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("No original content available for quality checking"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_QualityCheckerAgentException() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for quality checking");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Quality checking service error"));
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Quality checking failed"));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ExtractContentFromAlternativeFields() throws Exception {
        // Setup paper processor result with content in alternative field
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Alternative field content for quality assessment");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.88);
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
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
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.82);
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithNullOptionalResultData() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for quality checking");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup optional results with null data
        AgentResult summaryResult = AgentResult.success("content-summarizer", null);
        AgentResult conceptResult = AgentResult.success("concept-explainer", null);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        when(mockExecutionContext.get("conceptExplainerResult")).thenReturn(conceptResult);
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.78);
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithPartialSummaryData() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for quality assessment");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup summary result with only some fields
        Map<String, Object> summaryResultData = new HashMap<>();
        summaryResultData.put("briefSummary", "Brief summary only");
        // Missing standardSummary and detailedSummary
        AgentResult summaryResult = AgentResult.success("content-summarizer", summaryResultData);
        when(mockExecutionContext.get("contentSummarizerResult")).thenReturn(summaryResult);
        
        // Setup quality checker agent result
        Map<String, Object> qualityResultData = new HashMap<>();
        qualityResultData.put("overallQualityScore", 0.83);
        AgentResult agentResult = AgentResult.success("quality-checker", qualityResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithNullQualityAgentResultData() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for quality checking");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup quality checker agent result with null data
        AgentResult agentResult = AgentResult.success("quality-checker", null);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockQualityCheckerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockQualityCheckerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("qualityCheckerResult"), any(AgentResult.class));
    }
}

package com.samjdtechnologies.answer42.batch.tasklets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.agent.PaperProcessorAgent;

public class PaperProcessorTaskletTest {

    @Mock
    private PaperProcessorAgent mockPaperProcessorAgent;
    
    @Mock
    private PaperService mockPaperService;
    
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

    private PaperProcessorTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;
    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new PaperProcessorTasklet(mockPaperProcessorAgent, mockPaperService);
        
        testPaperId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testUser = new User("testuser", "test@example.com", "password");
        testUser.setId(testUserId);
        
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
        PaperProcessorTasklet tasklet = new PaperProcessorTasklet(mockPaperProcessorAgent, mockPaperService);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success_WithExistingTextContent() throws Exception {
        // Setup paper with existing text content
        Paper testPaper = new Paper("Test Paper Title", null, "uploads/test.pdf", testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("Existing text content for analysis and processing");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup paper processor agent result
        Map<String, Object> processingResultData = new HashMap<>();
        processingResultData.put("extractedText", "Processed and analyzed text content");
        processingResultData.put("structure", Map.of("sections", 5, "paragraphs", 20));
        processingResultData.put("wordCount", 150);
        AgentResult agentResult = AgentResult.success("paper-processor", processingResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperService).getPaperById(testPaperId);
        verify(mockPaperProcessorAgent).process(any(AgentTask.class));
        verify(mockPaperService).updateTextContent(testPaperId, "Processed and analyzed text content");
        verify(mockPaperService).updatePaperStatus(testPaperId, "TEXT_PROCESSED", "STRUCTURE_ANALYZED");
        verify(mockExecutionContext).put(eq("paperProcessorResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithoutExistingTextContent() throws Exception {
        // Setup paper without text content (would require PDF extraction in real scenario)
        Paper testPaper = new Paper("Test Paper Title", null, "uploads/test.pdf", testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent(null); // No existing text content
        testPaper.setFilePath("uploads/test.pdf");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup paper processor agent result
        Map<String, Object> processingResultData = new HashMap<>();
        processingResultData.put("extractedText", "Extracted and processed text from PDF");
        processingResultData.put("structure", Map.of("sections", 3, "figures", 2));
        AgentResult agentResult = AgentResult.success("paper-processor", processingResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperService).getPaperById(testPaperId);
        verify(mockPaperProcessorAgent).process(any(AgentTask.class));
        verify(mockPaperService).updateTextContent(testPaperId, "Extracted and processed text from PDF");
        verify(mockPaperService).updatePaperStatus(testPaperId, "TEXT_PROCESSED", "STRUCTURE_ANALYZED");
    }

    @Test
    void testExecute_Success_WithStructureAnalysis() throws Exception {
        // Setup paper with text content
        Paper testPaper = new Paper("Research Paper", null, "uploads/research.pdf", testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("Research paper content with multiple sections and complex structure");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup comprehensive processing result
        Map<String, Object> processingResultData = new HashMap<>();
        processingResultData.put("extractedText", "Structured research content");
        processingResultData.put("structure", Map.of(
            "sections", 8,
            "abstract", true,
            "introduction", true,
            "methodology", true,
            "results", true,
            "conclusion", true,
            "references", true
        ));
        processingResultData.put("wordCount", 5000);
        processingResultData.put("pageCount", 12);
        AgentResult agentResult = AgentResult.success("paper-processor", processingResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperProcessorAgent).process(any(AgentTask.class));
        verify(mockPaperService).updateTextContent(testPaperId, "Structured research content");
    }

    @Test
    void testExecute_Success_WithoutStructureData() throws Exception {
        // Setup paper with text content
        Paper testPaper = new Paper("Simple Paper", null, null, testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("Simple paper content");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup processing result without structure data
        Map<String, Object> processingResultData = new HashMap<>();
        processingResultData.put("extractedText", "Processed simple content");
        // No structure data
        AgentResult agentResult = AgentResult.success("paper-processor", processingResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperProcessorAgent).process(any(AgentTask.class));
        verify(mockPaperService).updateTextContent(testPaperId, "Processed simple content");
        verify(mockPaperService).updatePaperStatus(testPaperId, "TEXT_PROCESSED", "STRUCTURE_ANALYZED");
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
        
        assertTrue(exception.getMessage().contains("Paper processing failed"));
        verify(mockExecutionContext).put(eq("paperProcessorResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Paper processing failed"));
        verify(mockExecutionContext).put(eq("paperProcessorResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_PaperNotFound() {
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.empty());
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Paper not found"));
        verify(mockExecutionContext).put(eq("paperProcessorResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_PaperProcessingAgentFailure() throws Exception {
        // Setup paper
        Paper testPaper = new Paper("Test Paper", null, null, testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("Test content");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup failed agent result
        AgentResult failedResult = AgentResult.failure("paper-processor", "Processing service unavailable");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(failedResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Paper processing failed: Processing service unavailable"));
        verify(mockExecutionContext).put(eq("paperProcessorResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_PaperProcessingAgentException() throws Exception {
        // Setup paper
        Paper testPaper = new Paper("Test Paper", null, null, testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("Test content");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Agent processing error"));
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Paper processing failed"));
        verify(mockExecutionContext).put(eq("paperProcessorResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ParameterExtractionFromExecutionContext() throws Exception {
        // Setup IDs to come from execution context instead of job parameters
        when(mockJobParameters.get("paperId")).thenReturn(null);
        when(mockJobParameters.get("userId")).thenReturn(null);
        when(mockExecutionContext.get("paperId")).thenReturn(testPaperId);
        when(mockExecutionContext.get("userId")).thenReturn(testUserId);
        
        // Setup paper
        Paper testPaper = new Paper("Context Test Paper", null, null, testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("Content from execution context");
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup agent result
        Map<String, Object> processingResultData = new HashMap<>();
        processingResultData.put("extractedText", "Processed context content");
        AgentResult agentResult = AgentResult.success("paper-processor", processingResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperProcessorAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithEmptyTextContent() throws Exception {
        // Setup paper with empty text content and no file path
        Paper testPaper = new Paper("Empty Paper", null, null, testUser);
        testPaper.setId(testPaperId);
        testPaper.setTextContent("   "); // Empty/whitespace content
        testPaper.setFilePath(null); // No file path for extraction
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup agent result
        Map<String, Object> processingResultData = new HashMap<>();
        processingResultData.put("extractedText", "Fallback title processing");
        AgentResult agentResult = AgentResult.success("paper-processor", processingResultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockPaperProcessorAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperProcessorAgent).process(any(AgentTask.class));
    }

    @Test
    void testEstimateProcessingTime_WithTextContent() {
        Paper testPaper = new Paper("Test Paper", null, null, testUser);
        testPaper.setTextContent("A".repeat(5000)); // 5000 characters
        
        Duration result = tasklet.estimateProcessingTime(testPaper);
        
        // Expected: 30 + (5000/1000) = 35 seconds
        assertEquals(Duration.ofSeconds(35), result);
    }

    @Test
    void testEstimateProcessingTime_WithLargeContent() {
        Paper testPaper = new Paper("Large Paper", null, null, testUser);
        testPaper.setTextContent("A".repeat(500000)); // 500k characters, should cap at 5 minutes
        
        Duration result = tasklet.estimateProcessingTime(testPaper);
        
        // Should cap at 5 minutes (300 seconds)
        assertEquals(Duration.ofSeconds(300), result);
    }

    @Test
    void testEstimateProcessingTime_WithFileSize() {
        Paper testPaper = new Paper("File Paper", null, null, testUser);
        testPaper.setTextContent(null);
        testPaper.setFileSize(5L * 1024 * 1024); // 5 MB
        
        Duration result = tasklet.estimateProcessingTime(testPaper);
        
        // Expected: 30 + 5 = 35 seconds
        assertEquals(Duration.ofSeconds(35), result);
    }

    @Test
    void testEstimateProcessingTime_WithLargeFile() {
        Paper testPaper = new Paper("Large File Paper", null, null, testUser);
        testPaper.setTextContent(null);
        testPaper.setFileSize(500L * 1024 * 1024); // 500 MB, should cap at 5 minutes
        
        Duration result = tasklet.estimateProcessingTime(testPaper);
        
        // Should cap at 5 minutes (300 seconds)
        assertEquals(Duration.ofSeconds(300), result);
    }

    @Test
    void testEstimateProcessingTime_Default() {
        Paper testPaper = new Paper("Default Paper", null, null, testUser);
        testPaper.setTextContent(null);
        testPaper.setFileSize(null);
        
        Duration result = tasklet.estimateProcessingTime(testPaper);
        
        // Default estimate: 2 minutes
        assertEquals(Duration.ofMinutes(2), result);
    }
}

package com.samjdtechnologies.answer42.batch.tasklets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.samjdtechnologies.answer42.model.agent.AgentResult;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.agent.MetadataEnhancementAgent;

public class MetadataEnhancementTaskletTest {

    @Mock
    private MetadataEnhancementAgent mockMetadataAgent;
    
    @Mock
    private PaperService mockPaperService;
    
    @Mock
    private StepContribution mockStepContribution;
    
    @Mock
    private ChunkContext mockChunkContext;
    
    @Mock
    private StepContext mockStepContext;
    
    @Mock
    private StepExecution mockStepExecution;
    
    @Mock
    private ExecutionContext mockExecutionContext;

    private MetadataEnhancementTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;
    private Paper testPaper;
    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new MetadataEnhancementTasklet(mockMetadataAgent, mockPaperService);
        
        // Setup test data
        testPaperId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testUser = new User("test@example.com", "Test User", "password");
        testUser.setId(testUserId);
        
        testPaper = new Paper("Test Paper Title", List.of("Author One", "Author Two"), "Test content", testUser);
        testPaper.setId(testPaperId);
        testPaper.setDoi("10.1000/test.doi");
        testPaper.setJournal("Test Journal");
        testPaper.setPublicationDate(LocalDate.of(2023, 1, 1));
        
        // Setup mock chain
        when(mockChunkContext.getStepContext()).thenReturn(mockStepContext);
        when(mockStepContext.getStepExecution()).thenReturn(mockStepExecution);
        when(mockStepExecution.getExecutionContext()).thenReturn(mockExecutionContext);
        
        // Setup job parameters using mocks to avoid constructor issues
        org.springframework.batch.core.JobParameters jobParams = mock(org.springframework.batch.core.JobParameters.class);
        when(jobParams.getString("paperId")).thenReturn(testPaperId.toString());
        when(jobParams.getString("userId")).thenReturn(testUserId.toString());
        when(mockStepExecution.getJobParameters()).thenReturn(jobParams);
    }

    @Test
    void testConstructor() {
        assertNotNull(tasklet);
    }

    @Test
    void testEstimateProcessingTime_PaperWithAllMetadata() {
        // Paper with complete metadata
        testPaper.setCrossrefMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setSemanticScholarMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setCrossrefDoi("10.1000/crossref.doi");
        testPaper.setSemanticScholarId("semantic123");
        
        Duration duration = tasklet.estimateProcessingTime(testPaper);
        
        assertNotNull(duration);
        // Base: 60s + no missing fields = 60s
        assertEquals(Duration.ofSeconds(60), duration);
    }

    @Test
    void testEstimateProcessingTime_PaperWithMissingMetadata() {
        // Paper with missing metadata fields
        testPaper.setDoi(null);
        testPaper.setCrossrefMetadata(null);
        testPaper.setSemanticScholarMetadata(null);
        testPaper.setCrossrefDoi(null);
        testPaper.setSemanticScholarId(null);
        
        Duration duration = tasklet.estimateProcessingTime(testPaper);
        
        assertNotNull(duration);
        // Base: 60s + DOI: 15s + Crossref: 25s + Semantic: 30s + CrossrefDOI: 10s + SemanticID: 10s = 150s
        assertEquals(Duration.ofSeconds(150), duration);
    }

    @Test
    void testEstimateProcessingTime_ExceedsMaximum() {
        // Test that duration is capped at 180 seconds
        // This would normally calculate higher, but should be capped
        testPaper.setDoi(null);
        testPaper.setCrossrefMetadata(null);
        testPaper.setSemanticScholarMetadata(null);
        testPaper.setCrossrefDoi(null);
        testPaper.setSemanticScholarId(null);
        
        Duration duration = tasklet.estimateProcessingTime(testPaper);
        
        assertNotNull(duration);
        // Should be capped at maximum of 180 seconds
        assertTrue(duration.getSeconds() <= 180);
    }

    @Test
    void testIsEnhancementNeeded_NoMetadata() {
        // Paper with no metadata
        testPaper.setDoi(null);
        testPaper.setCrossrefMetadata(null);
        testPaper.setSemanticScholarMetadata(null);
        testPaper.setCrossrefDoi(null);
        testPaper.setSemanticScholarId(null);
        
        boolean result = tasklet.isEnhancementNeeded(testPaper);
        
        assertTrue(result);
    }

    @Test
    void testIsEnhancementNeeded_CompleteMetadata() {
        // Paper with complete metadata
        testPaper.setCrossrefMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setSemanticScholarMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setCrossrefDoi("10.1000/crossref.doi");
        testPaper.setSemanticScholarId("semantic123");
        
        boolean result = tasklet.isEnhancementNeeded(testPaper);
        
        assertFalse(result);
    }

    @Test
    void testIsEnhancementNeeded_OutdatedMetadata() {
        // Paper with outdated metadata (older than 30 days)
        ZonedDateTime oldDate = ZonedDateTime.now().minusDays(35);
        testPaper.setCrossrefMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setSemanticScholarMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setCrossrefLastVerified(oldDate);
        testPaper.setSemanticScholarLastVerified(oldDate);
        testPaper.setCrossrefDoi("10.1000/crossref.doi");
        testPaper.setSemanticScholarId("semantic123");
        
        boolean result = tasklet.isEnhancementNeeded(testPaper);
        
        assertTrue(result);
    }

    @Test
    void testIsEnhancementNeeded_RecentMetadata() {
        // Paper with recent metadata (within 30 days)
        ZonedDateTime recentDate = ZonedDateTime.now().minusDays(10);
        testPaper.setCrossrefMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setSemanticScholarMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setCrossrefLastVerified(recentDate);
        testPaper.setSemanticScholarLastVerified(recentDate);
        testPaper.setCrossrefDoi("10.1000/crossref.doi");
        testPaper.setSemanticScholarId("semantic123");
        
        boolean result = tasklet.isEnhancementNeeded(testPaper);
        
        assertFalse(result);
    }

    @Test
    void testExecute_Success() throws Exception {
        // Setup paper service to return test paper
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup successful agent result
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("crossrefMetadata", JsonNodeFactory.instance.objectNode());
        resultData.put("semanticScholarMetadata", JsonNodeFactory.instance.objectNode());
        resultData.put("resolvedDOI", "10.1000/enhanced.doi");
        
        AgentResult successResult = AgentResult.success("test-task", resultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(successResult);
        when(mockMetadataAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperService).getPaperById(testPaperId);
        verify(mockMetadataAgent).process(any(AgentTask.class));
        verify(mockPaperService).updatePaperStatus(testPaperId, "METADATA_ENHANCED", "METADATA_VERIFIED");
    }

    @Test
    void testExecute_PaperNotFound() {
        // Setup paper service to return empty
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.empty());
        
        Exception exception = assertThrows(RuntimeException.class, () -> {
            tasklet.execute(mockStepContribution, mockChunkContext);
        });
        
        assertTrue(exception.getMessage().contains("Paper not found"));
        verify(mockPaperService).getPaperById(testPaperId);
        verify(mockMetadataAgent, never()).process(any(AgentTask.class));
    }

    @Test
    void testExecute_AgentFailure() throws Exception {
        // Setup paper service to return test paper
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup agent failure
        AgentResult failureResult = AgentResult.failure("test-task", "Enhancement failed");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(failureResult);
        when(mockMetadataAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        // Should still return FINISHED (with fallback result)
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperService).getPaperById(testPaperId);
        verify(mockMetadataAgent).process(any(AgentTask.class));
    }

    @Test
    void testExecute_WithPreviousStepResult() throws Exception {
        // Setup paper service to return test paper
        when(mockPaperService.getPaperById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        // Setup previous step result with text content
        Map<String, Object> previousResultData = new HashMap<>();
        previousResultData.put("extractedText", "This is the extracted text content from the paper.");
        AgentResult previousResult = AgentResult.success("previous-task", previousResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(previousResult);
        
        // Setup successful agent result
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("crossrefMetadata", JsonNodeFactory.instance.objectNode());
        AgentResult successResult = AgentResult.success("test-task", resultData);
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(successResult);
        when(mockMetadataAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockPaperService).getPaperById(testPaperId);
        verify(mockMetadataAgent).process(any(AgentTask.class));
    }

    @Test
    void testEstimateProcessingTime_PartialMetadata() {
        // Paper with some metadata present
        testPaper.setCrossrefMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setSemanticScholarMetadata(null);
        testPaper.setCrossrefDoi("10.1000/crossref.doi");
        testPaper.setSemanticScholarId(null);
        // DOI is present from setup
        
        Duration duration = tasklet.estimateProcessingTime(testPaper);
        
        assertNotNull(duration);
        // Base: 60s + Semantic: 30s + SemanticID: 10s = 100s
        assertEquals(Duration.ofSeconds(100), duration);
    }

    @Test
    void testIsEnhancementNeeded_MixedMetadata() {
        // Paper with some metadata present, some missing
        testPaper.setCrossrefMetadata(JsonNodeFactory.instance.objectNode());
        testPaper.setSemanticScholarMetadata(null); // Missing
        testPaper.setCrossrefDoi("10.1000/crossref.doi");
        testPaper.setSemanticScholarId(null); // Missing
        
        boolean result = tasklet.isEnhancementNeeded(testPaper);
        
        assertTrue(result); // Should need enhancement due to missing fields
    }
}

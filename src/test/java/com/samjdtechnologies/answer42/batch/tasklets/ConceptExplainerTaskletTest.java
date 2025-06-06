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
import com.samjdtechnologies.answer42.model.concept.ConceptExplanationResult;
import com.samjdtechnologies.answer42.model.concept.ConceptExplanations;
import com.samjdtechnologies.answer42.model.concept.ConceptRelationshipMap;
import com.samjdtechnologies.answer42.model.daos.AgentTask;
import com.samjdtechnologies.answer42.model.enums.EducationLevel;
import com.samjdtechnologies.answer42.service.agent.ConceptExplainerAgent;

public class ConceptExplainerTaskletTest {

    @Mock
    private ConceptExplainerAgent mockConceptExplainerAgent;
    
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

    private ConceptExplainerTasklet tasklet;
    private UUID testPaperId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tasklet = new ConceptExplainerTasklet(mockConceptExplainerAgent);
        
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
        ConceptExplainerTasklet tasklet = new ConceptExplainerTasklet(mockConceptExplainerAgent);
        
        assertNotNull(tasklet);
    }

    @Test
    void testExecute_Success_WithFullContent() throws Exception {
        // Setup paper processor result with full content
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "This is a comprehensive academic paper discussing machine learning algorithms, neural networks, and deep learning architectures. The paper explores various optimization techniques and their applications.");
        paperResultData.put("title", "Advanced Machine Learning Techniques");
        paperResultData.put("abstract", "This paper presents novel approaches to machine learning optimization");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup concept explainer agent result
        ConceptExplanationResult conceptResult = createMockConceptExplanationResult();
        Map<String, Object> agentResultData = new HashMap<>();
        agentResultData.put("result", conceptResult);
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(agentResultData)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockConceptExplainerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithAlternativeContentFields() throws Exception {
        // Setup paper processor result with alternative field names
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("extractedText", "Alternative field content for concept explanation");
        paperResultData.put("paperTitle", "Alternative Title Field");
        paperResultData.put("abstractText", "Alternative abstract field content");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup concept explainer agent result
        ConceptExplanationResult conceptResult = createMockConceptExplanationResult();
        Map<String, Object> agentResultData = new HashMap<>();
        agentResultData.put("result", conceptResult);
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(agentResultData)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockConceptExplainerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithMinimalContent() throws Exception {
        // Setup paper processor result with minimal content (no title/abstract)
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("content", "Minimal content for concept explanation testing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup concept explainer agent result
        ConceptExplanationResult conceptResult = createMockConceptExplanationResult();
        Map<String, Object> agentResultData = new HashMap<>();
        agentResultData.put("result", conceptResult);
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(agentResultData)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockConceptExplainerAgent).process(any(AgentTask.class));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_Success_WithEmptyOptionalFields() throws Exception {
        // Setup paper processor result with empty optional fields
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("text", "Content for concept explanation");
        paperResultData.put("title", "   "); // Empty title
        paperResultData.put("abstract", ""); // Empty abstract
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup concept explainer agent result
        ConceptExplanationResult conceptResult = createMockConceptExplanationResult();
        Map<String, Object> agentResultData = new HashMap<>();
        agentResultData.put("result", conceptResult);
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(agentResultData)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockConceptExplainerAgent).process(any(AgentTask.class));
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
        
        assertTrue(exception.getMessage().contains("Concept explanation failed"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("Concept explanation failed"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_MissingPaperProcessorResult() {
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(null);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Concept explanation failed"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_FailedPaperProcessorResult() {
        AgentResult failedResult = AgentResult.failure("paper-processor", "Paper processing failed");
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(failedResult);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Concept explanation failed"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("No text content available for concept explanation"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
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
        
        assertTrue(exception.getMessage().contains("No text content available for concept explanation"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ConceptExplainerAgentFailure() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for failed concept explanation");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup failed agent result
        AgentResult failedResult = AgentResult.failure("concept-explainer", "Concept explanation service unavailable");
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(failedResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Concept explanation failed: Concept explanation service unavailable"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_ConceptExplainerAgentException() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for exception testing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent to throw exception
        CompletableFuture<AgentResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Concept explanation service error"));
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Concept explanation failed"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_UnexpectedResultType() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for unexpected result type testing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent result with unexpected data type
        Map<String, Object> agentResultData = new HashMap<>();
        agentResultData.put("result", "not a ConceptExplanationResult");
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(agentResultData)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Unexpected result type from ConceptExplainerAgent"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
    }

    @Test
    void testExecute_NullResultData() throws Exception {
        // Setup paper processor result
        Map<String, Object> paperResultData = new HashMap<>();
        paperResultData.put("textContent", "Content for null result testing");
        AgentResult paperResult = AgentResult.success("paper-processor", paperResultData);
        when(mockExecutionContext.get("paperProcessorResult")).thenReturn(paperResult);
        
        // Setup agent result with null data
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(null)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tasklet.execute(mockStepContribution, mockChunkContext));
        
        assertTrue(exception.getMessage().contains("Unexpected result type from ConceptExplainerAgent: null"));
        verify(mockExecutionContext).put(eq("conceptExplainerResult"), any(AgentResult.class));
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
        
        // Setup concept explainer agent result
        ConceptExplanationResult conceptResult = createMockConceptExplanationResult();
        Map<String, Object> agentResultData = new HashMap<>();
        agentResultData.put("result", conceptResult);
        AgentResult agentResult = AgentResult.builder()
            .taskId("concept-explainer")
            .success(true)
            .resultData(agentResultData)
            .build();
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(agentResult);
        when(mockConceptExplainerAgent.process(any(AgentTask.class))).thenReturn(future);
        
        RepeatStatus result = tasklet.execute(mockStepContribution, mockChunkContext);
        
        assertEquals(RepeatStatus.FINISHED, result);
        verify(mockConceptExplainerAgent).process(any(AgentTask.class));
    }

    private ConceptExplanationResult createMockConceptExplanationResult() {
        // Create mock explanations by level
        Map<EducationLevel, ConceptExplanations> explanationsByLevel = new HashMap<>();
        
        // Create high school level explanations
        ConceptExplanations highSchoolLevel = ConceptExplanations.builder()
            .level(EducationLevel.HIGH_SCHOOL)
            .totalTermsProcessed(3)
            .averageConfidence(0.85)
            .build();
        explanationsByLevel.put(EducationLevel.HIGH_SCHOOL, highSchoolLevel);
        
        // Create graduate level explanations
        ConceptExplanations graduateLevel = ConceptExplanations.builder()
            .level(EducationLevel.GRADUATE)
            .totalTermsProcessed(5)
            .averageConfidence(0.90)
            .build();
        explanationsByLevel.put(EducationLevel.GRADUATE, graduateLevel);
        
        // Create mock relationship map
        ConceptRelationshipMap relationshipMap = mock(ConceptRelationshipMap.class);
        when(relationshipMap.hasSufficientConnections()).thenReturn(true);
        
        // Create processing metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingTime", 1500);
        metadata.put("conceptsIdentified", 10);
        metadata.put("explanationsGenerated", 8);
        
        return ConceptExplanationResult.builder()
            .explanations(explanationsByLevel)
            .relationshipMap(relationshipMap)
            .overallQualityScore(0.89)
            .processingMetadata(metadata)
            .build();
    }
}

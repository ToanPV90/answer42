package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.model.db.AnalysisResult;
import com.samjdtechnologies.answer42.model.db.AnalysisTask;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.AnalysisType;
import com.samjdtechnologies.answer42.repository.AnalysisResultRepository;
import com.samjdtechnologies.answer42.repository.AnalysisTaskRepository;
import com.samjdtechnologies.answer42.service.helpers.AIInteractionHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing paper analysis operations.
 * Implements the task-based workflow for generating and storing AI-powered analyses of papers.
 */
@Service
public class PaperAnalysisService {
    private static final Logger LOG = LoggerFactory.getLogger(PaperAnalysisService.class);
    
    private final AnalysisTaskRepository taskRepository;
    private final AnalysisResultRepository resultRepository;
    private final PaperService paperService;
    private final AIInteractionHelper aiInteractionHelper;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param taskRepository repository for storing analysis tasks
     * @param resultRepository repository for storing analysis results
     * @param paperService service for accessing paper data
     * @param aiConfig configuration for AI providers
     * @param aiInteractionHelper helper for AI interactions
     */
    public PaperAnalysisService(AnalysisTaskRepository taskRepository,
                               AnalysisResultRepository resultRepository,
                               PaperService paperService,
                               AIConfig aiConfig,
                               AIInteractionHelper aiInteractionHelper) {
        this.taskRepository = taskRepository;
        this.resultRepository = resultRepository;
        this.paperService = paperService;
        this.aiInteractionHelper = aiInteractionHelper;
    }
    
    /**
     * Request an analysis for a paper. This creates a task and may immediately process it.
     * 
     * @param paperId The ID of the paper to analyze
     * @param analysisType The type of analysis to perform
     * @param user The user requesting the analysis
     * @param executeImmediately Whether to execute the analysis immediately or queue it
     * @return The analysis task
     */
    @Transactional
    public AnalysisTask requestAnalysis(UUID paperId, AnalysisType analysisType, User user, boolean executeImmediately) {
        LoggingUtil.info(LOG, "requestAnalysis", "Requesting %s for paper ID: %s, user: %s, immediate: %b", 
                analysisType, paperId, user.getId(), executeImmediately);
        
        // Check if there's already a completed task
        Optional<AnalysisTask> existingTask = taskRepository.findByPaperIdAndAnalysisType(paperId, analysisType);
        
        if (existingTask.isPresent() && existingTask.get().getStatus() == AnalysisTask.Status.COMPLETED) {
            LoggingUtil.info(LOG, "requestAnalysis", "Found existing completed task: %s", existingTask.get().getId());
            
            // Update the last accessed time of the result
            AnalysisResult result = existingTask.get().getResult();
            if (result != null) {
                result.updateLastAccessed();
                resultRepository.save(result);
            }
            
            return existingTask.get();
        }
        
        // Get paper
        Paper paper = paperService.getPaperById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found with ID: " + paperId));
        
        // Create a new task
        AnalysisTask task = new AnalysisTask(paper, user, analysisType);
        task = taskRepository.save(task);
        
        if (executeImmediately) {
            // Execute the task immediately
            processTask(task.getId());
        }
        
        return task;
    }
    
    /**
     * Get or generate an analysis for a paper.
     * This is the main method to be called by clients wanting an analysis.
     * 
     * @param paperId The ID of the paper to analyze
     * @param analysisType The type of analysis to perform
     * @param user The user requesting the analysis
     * @return The analysis result (may be generated or retrieved from cache)
     * @throws IllegalStateException if the task is not completed after immediate execution
     */
    @Transactional
    public AnalysisResult getOrGenerateAnalysis(UUID paperId, AnalysisType analysisType, User user) {
        LoggingUtil.info(LOG, "getOrGenerateAnalysis", "Getting or generating %s for paper ID: %s", 
                analysisType, paperId);
        
        // Try to find existing result directly
        Optional<AnalysisResult> existingResult = resultRepository.findByPaperIdAndAnalysisType(paperId, analysisType);
        
        if (existingResult.isPresent()) {
            LoggingUtil.debug(LOG, "getOrGenerateAnalysis", "Using existing analysis result");
            AnalysisResult result = existingResult.get();
            // Update last accessed time
            result.updateLastAccessed();
            return resultRepository.save(result);
        }
        
        // No existing result, request an analysis and wait for it to complete
        AnalysisTask task = requestAnalysis(paperId, analysisType, user, true);
        
        // Wait for task completion if necessary
        if (task.getStatus() != AnalysisTask.Status.COMPLETED) {
            throw new IllegalStateException("Analysis task not completed: " + task.getId());
        }
        
        return task.getResult();
    }
    
    /**
     * Process a queued analysis task.
     * 
     * @param taskId The ID of the task to process
     * @return The updated task
     * @throws RuntimeException if the task processing fails
     */
    @Transactional
    public AnalysisTask processTask(UUID taskId) {
        LoggingUtil.info(LOG, "processTask", "Processing task ID: %s", taskId);
        
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));
        
        // Only process pending tasks
        if (task.getStatus() != AnalysisTask.Status.PENDING) {
            LoggingUtil.warn(LOG, "processTask", "Task %s is not pending (status: %s)", taskId, task.getStatus());
            return task;
        }
        
        try {
            // Mark task as processing
            task.markAsProcessing();
            task = taskRepository.save(task);
            
            // Generate the analysis
            Paper paper = task.getPaper();
            AnalysisType analysisType = task.getAnalysisType();
            
            // Generate analysis using Claude AI
            String analysisPrompt = generatePromptForAnalysisType(paper, analysisType);
            
            // Create prompt with system message
            String systemPrompt = "You are a scholarly research assistant specializing in academic paper analysis. " +
                    "Your task is to provide a detailed and insightful " + analysisType.toString().toLowerCase().replace('_', ' ') + 
                    " of the paper. Use an academic tone, be thorough but concise, and focus on providing " +
                    "valuable insights. Support your points with evidence from the paper when possible.";
            
            String analysisContent;
            try {
                // Get response from AI using the AIInteractionHelper
                // This ensures any Claude-specific markers are cleaned from the response
                analysisContent = aiInteractionHelper.getAIResponse(
                        AIProvider.ANTHROPIC,
                        List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(analysisPrompt)
                        )
                );
                
                // Get the user from the task
                User user = task.getUser();
                
                // Create and save the analysis result ONLY if we get a successful response
                AnalysisResult result = new AnalysisResult(paper, task, user, analysisType, analysisContent);
                result = resultRepository.save(result);
                
                // Mark task as completed
                task.markAsCompleted(result);
                task = taskRepository.save(task);
                
                LoggingUtil.info(LOG, "processTask", "Completed task ID: %s, result ID: %s", taskId, result.getId());
            } catch (AIInteractionHelper.AITimeoutException e) {
                // Don't create a result for timeout errors - just mark the task as failed with a user-friendly message
                String errorMessage = "Anthropic API is currently busy. Please try again later.";
                
                LoggingUtil.warn(LOG, "processTask", 
                        "API timeout when processing task %s: %s", taskId, e.getMessage());
                
                task.markAsFailed(errorMessage);
                task = taskRepository.save(task);
                
                throw new RuntimeException(errorMessage, e);
            }
            
            return task;
        } catch (Exception e) {
            // Mark task as failed
            LoggingUtil.error(LOG, "processTask", "Error processing task %s: %s", taskId, e.getMessage(), e);
            
            task.markAsFailed(e.getMessage());
            task = taskRepository.save(task);
            
            throw new RuntimeException("Failed to process analysis task: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get an analysis task by ID.
     * 
     * @param taskId The ID of the task
     * @return The task if found
     */
    public Optional<AnalysisTask> getTask(UUID taskId) {
        return taskRepository.findById(taskId);
    }
    
    /**
     * Get tasks for a specific paper.
     * 
     * @param paperId The ID of the paper
     * @return List of tasks for the paper
     */
    public List<AnalysisTask> getTasksForPaper(UUID paperId) {
        return taskRepository.findByPaperId(paperId);
    }
    
    /**
     * Get tasks by status.
     * 
     * @param status The status to filter by
     * @return List of tasks with the given status
     */
    public List<AnalysisTask> getTasksByStatus(AnalysisTask.Status status) {
        return taskRepository.findByStatus(status);
    }
    
    /**
     * Get an analysis result by ID.
     * 
     * @param resultId The ID of the result
     * @return The result if found
     */
    public Optional<AnalysisResult> getResult(UUID resultId) {
        return resultRepository.findById(resultId);
    }
    
    /**
     * Get results for a specific paper.
     * 
     * @param paperId The ID of the paper
     * @return List of results for the paper
     */
    public List<AnalysisResult> getResultsForPaper(UUID paperId) {
        return resultRepository.findByPaperId(paperId);
    }
    
    /**
     * Archive a result.
     * 
     * @param resultId The ID of the result to archive
     * @return The updated result
     */
    @Transactional
    public AnalysisResult archiveResult(UUID resultId) {
        AnalysisResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found with ID: " + resultId));
        
        result.setArchived(true);
        return resultRepository.save(result);
    }
    
    /**
     * Generate the appropriate prompt for the analysis type.
     * 
     * @param paper The paper to analyze
     * @param analysisType The type of analysis to perform
     * @return The prompt for the AI
     */
    private String generatePromptForAnalysisType(Paper paper, AnalysisType analysisType) {
        StringBuilder prompt = new StringBuilder();
        String paperContent = paper.getTextContent() != null ? paper.getTextContent() : 
                             (paper.getPaperAbstract() != null ? paper.getPaperAbstract() : "");
        
        prompt.append("Paper Title: ").append(paper.getTitle()).append("\n\n");
        
        if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
            prompt.append("Authors: ").append(String.join(", ", paper.getAuthors())).append("\n\n");
        }
        
        if (paper.getJournal() != null) {
            prompt.append("Journal: ").append(paper.getJournal()).append("\n\n");
        }
        
        if (paper.getYear() != null) {
            prompt.append("Year: ").append(paper.getYear()).append("\n\n");
        }
        
        prompt.append("Paper Content:\n").append(paperContent).append("\n\n");
        
        // Add specific instructions based on analysis type
        switch (analysisType) {
            case DEEP_SUMMARY:
                prompt.append("Please provide a comprehensive summary of this paper, covering the key " +
                        "points, methodologies, findings, and conclusions. The summary should be " +
                        "thorough but concise, making the paper's content accessible to a reader " +
                        "without requiring them to read the full paper.");
                break;
                
            case METHODOLOGY_ANALYSIS:
                prompt.append("Please analyze the methodology used in this paper. Include an examination " +
                        "of the research design, data collection methods, analytical approaches, and " +
                        "any potential methodological strengths or limitations. Explain how the " +
                        "methodology supports or constrains the paper's findings.");
                break;
                
            case RESULTS_INTERPRETATION:
                prompt.append("Please interpret the results presented in this paper. Explain what the " +
                        "findings mean in the context of the research question, how they compare to " +
                        "existing literature, and what their significance is for the field. Include " +
                        "any nuances or complexities in the results that require careful interpretation.");
                break;
                
            case CRITICAL_EVALUATION:
                prompt.append("Please provide a critical evaluation of this paper, assessing its " +
                        "strengths and weaknesses. Consider the validity of the research design, " +
                        "the quality of evidence, the logical flow of arguments, and the significance " +
                        "of the findings. Identify any gaps, limitations, or areas where the paper " +
                        "could be improved.");
                break;
                
            case RESEARCH_IMPLICATIONS:
                prompt.append("Please discuss the broader research implications of this paper. Explain " +
                        "how the findings contribute to the field, what future research directions they " +
                        "suggest, and what practical or theoretical implications they might have. " +
                        "Consider how these findings might influence both academic research and " +
                        "real-world applications.");
                break;
                
            case GENERAL:
            default:
                prompt.append("Please provide a general analysis of this paper, including its purpose, " +
                        "methods, findings, and significance. Highlight any particularly noteworthy " +
                        "aspects of the paper and assess its overall contribution to the field.");
                break;
        }
        
        return prompt.toString();
    }
}

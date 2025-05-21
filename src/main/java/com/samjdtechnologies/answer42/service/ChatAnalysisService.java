package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.model.ChatAnalysisResult;
import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.enums.AnalysisType;
import com.samjdtechnologies.answer42.repository.ChatAnalysisResultRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing paper analysis operations.
 * Handles generating and storing AI-powered analyses of papers.
 */
@Service
public class ChatAnalysisService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatAnalysisService.class);
    
    private final ChatAnalysisResultRepository analysisRepository;
    private final PaperService paperService;
    private final AIConfig aiConfig;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param analysisRepository repository for storing analysis results
     * @param paperService service for accessing paper data
     * @param aiConfig configuration for AI providers
     */
    public ChatAnalysisService(ChatAnalysisResultRepository analysisRepository, 
                               PaperService paperService,
                               AIConfig aiConfig) {
        this.analysisRepository = analysisRepository;
        this.paperService = paperService;
        this.aiConfig = aiConfig;
    }
    
    /**
     * Get an existing analysis or generate a new one if it doesn't exist.
     * 
     * @param paperId The ID of the paper to analyze
     * @param analysisType The type of analysis to perform
     * @return The analysis result
     */
    @Transactional
    public ChatAnalysisResult getOrGenerateAnalysis(UUID paperId, AnalysisType analysisType) {
        LoggingUtil.info(LOG, "getOrGenerateAnalysis", "Getting or generating %s for paper ID: %s", 
                analysisType, paperId);
        
        // Try to find existing analysis
        Optional<ChatAnalysisResult> existingAnalysis = 
                analysisRepository.findByPaperIdAndAnalysisType(paperId, analysisType);
        
        if (existingAnalysis.isPresent()) {
            LoggingUtil.debug(LOG, "getOrGenerateAnalysis", "Using existing analysis");
            ChatAnalysisResult result = existingAnalysis.get();
            // Update last accessed time
            result.updateLastAccessed();
            return analysisRepository.save(result);
        } else {
            LoggingUtil.debug(LOG, "getOrGenerateAnalysis", "Generating new analysis");
            return generateAnalysis(paperId, analysisType);
        }
    }
    
    /**
     * Generate a new analysis for a paper.
     * 
     * @param paperId The ID of the paper to analyze
     * @param analysisType The type of analysis to perform
     * @return The new analysis result
     */
    @Transactional
    public ChatAnalysisResult generateAnalysis(UUID paperId, AnalysisType analysisType) {
        LoggingUtil.info(LOG, "generateAnalysis", "Generating %s for paper ID: %s", 
                analysisType, paperId);
        
        // Get paper
        Paper paper = paperService.getPaperById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found with ID: " + paperId));
        
    // Use Anthropic for paper analysis as recommended
    ChatClient chatClient = aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
    
    // Generate analysis using Claude AI
    String analysisPrompt = generatePromptForAnalysisType(paper, analysisType);
    
    // Create prompt with system message
    String systemPrompt = "You are a scholarly research assistant specializing in academic paper analysis. " +
            "Your task is to provide a detailed and insightful " + analysisType.toString().toLowerCase().replace('_', ' ') + 
            " of the paper. Use an academic tone, be thorough but concise, and focus on providing " +
            "valuable insights. Support your points with evidence from the paper when possible.";
    
    // Get response from AI using the anthropic chat client
    // We're using the fluent API pattern as defined in the ChatClient interface
    String analysisContent = chatClient.prompt()
            .system(systemPrompt)
            .user(analysisPrompt)
            .call()
            .content();
        
        // Create and save the analysis result
        ChatAnalysisResult result = new ChatAnalysisResult(paper, analysisType, analysisContent);
        return analysisRepository.save(result);
    }
    
    /**
     * Get all existing analyses for a paper.
     * 
     * @param paperId The ID of the paper
     * @return List of all analyses for the paper
     */
    public List<ChatAnalysisResult> getAnalysesForPaper(UUID paperId) {
        LoggingUtil.debug(LOG, "getAnalysesForPaper", "Getting all analyses for paper ID: %s", paperId);
        return analysisRepository.findByPaperIdOrderByLastAccessedAtDesc(paperId);
    }
    
    /**
     * Get a specific analysis for a paper if it exists.
     * 
     * @param paperId The ID of the paper
     * @param analysisType The type of analysis
     * @return Optional containing the analysis if it exists
     */
    public Optional<ChatAnalysisResult> getAnalysis(UUID paperId, AnalysisType analysisType) {
        LoggingUtil.debug(LOG, "getAnalysis", "Getting %s for paper ID: %s", 
                analysisType, paperId);
        
        Optional<ChatAnalysisResult> result = 
                analysisRepository.findByPaperIdAndAnalysisType(paperId, analysisType);
        
        if (result.isPresent()) {
            // Update last accessed time
            ChatAnalysisResult analysis = result.get();
            analysis.updateLastAccessed();
            analysisRepository.save(analysis);
        }
        
        return result;
    }
    
    /**
     * Delete an analysis.
     * 
     * @param analysisId The ID of the analysis to delete
     */
    @Transactional
    public void deleteAnalysis(UUID analysisId) {
        LoggingUtil.info(LOG, "deleteAnalysis", "Deleting analysis with ID: %s", analysisId);
        analysisRepository.deleteById(analysisId);
    }
    
    /**
     * Archive an analysis.
     * 
     * @param analysisId The ID of the analysis to archive
     * @return The updated analysis
     */
    @Transactional
    public ChatAnalysisResult archiveAnalysis(UUID analysisId) {
        LoggingUtil.info(LOG, "archiveAnalysis", "Archiving analysis with ID: %s", analysisId);
        
        ChatAnalysisResult analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found with ID: " + analysisId));
        
        analysis.setArchived(true);
        return analysisRepository.save(analysis);
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
                             (paper.getAbstract() != null ? paper.getAbstract() : "");
        
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

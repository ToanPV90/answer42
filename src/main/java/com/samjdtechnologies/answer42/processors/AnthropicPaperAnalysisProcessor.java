package com.samjdtechnologies.answer42.processors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.daos.AnalysisResult;
import com.samjdtechnologies.answer42.model.daos.ChatSession;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.model.enums.AnalysisType;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.service.PaperAnalysisService;
import com.samjdtechnologies.answer42.ui.components.AIChatGeneralMesssageBubble;
import com.samjdtechnologies.answer42.ui.components.AIChatProgressMessageBubble;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

/**
 * Processor class for handling paper analysis requests.
 * Manages the workflow of requesting, displaying, and saving analysis results.
 * Uses Spring's thread management for background processing.
 */
@Component
public class AnthropicPaperAnalysisProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(AnthropicPaperAnalysisProcessor.class);
    
    private final Executor taskExecutor;

    /**
     * Creates a new PaperAnalysisProcessor with Spring's task executor.
     * 
     * @param taskExecutor The thread pool task executor from Spring's configuration
     */
    public AnthropicPaperAnalysisProcessor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Trigger a paper analysis and add the results to the chat.
     * Follows the proper analysis workflow using PaperAnalysisService.
     * 
     * @param instruction The analysis instruction (e.g., "Deep Summary")
     * @param chatService The chat service for adding messages
     * @param paperAnalysisService The service for generating analyses
     * @param session The current chat session
     * @param messagesContainer The UI container to add messages to
     */
    public void triggerAnalysis(String instruction, 
                               ChatService chatService,
                               PaperAnalysisService paperAnalysisService,
                               ChatSession session, 
                               VerticalLayout messagesContainer) {
        LoggingUtil.info(LOG, "triggerAnalysis", "Starting paper analysis with instruction: %s", instruction);
        
        if (session == null) {
            LoggingUtil.warn(LOG, "triggerAnalysis", "Attempted analysis with null session");
            Notification.show("Please select a paper first", 3000, Notification.Position.MIDDLE);
            return;
        }
        
        // Refresh the session to ensure we have the most recent data
        UUID sessionId = session.getId();
        LoggingUtil.info(LOG, "triggerAnalysis", "Refreshing session with ID: %s", sessionId);
        ChatSession refreshedSession = chatService.refreshSession(sessionId);
        
        if (refreshedSession == null) {
            LoggingUtil.error(LOG, "triggerAnalysis", "Failed to refresh session %s", sessionId);
            Notification.show("Failed to refresh session data", 3000, Notification.Position.MIDDLE);
            return;
        }
        
        // Use the refreshed session for getting paper IDs
        LoggingUtil.info(LOG, "triggerAnalysis", "Using refreshed session with ID: %s", refreshedSession.getId());
        List<UUID> paperIds = chatService.getPaperIdsFromSession(refreshedSession);
        LoggingUtil.info(LOG, "triggerAnalysis", "Retrieved %d paper IDs from session %s", 
                paperIds.size(), session.getId());
        
        if (paperIds.isEmpty()) {
            LoggingUtil.warn(LOG, "triggerAnalysis", "No papers found in session %s", session.getId());
            Notification.show("No papers found in session. Please add a paper first.", 
                    3000, Notification.Position.MIDDLE);
            return;
        }
        
        // Currently we only support analyzing one paper at a time
        UUID paperId = paperIds.get(0);
        
        // Convert instruction to enum
        AnalysisType analysisType;
        try {
            // Convert "Deep Summary" to DEEP_SUMMARY
            String enumName = instruction.toUpperCase().replace(' ', '_');
            analysisType = AnalysisType.valueOf(enumName);
        } catch (IllegalArgumentException e) {
            LoggingUtil.error(LOG, "triggerAnalysis", "Invalid analysis type: %s", e, instruction);
            Notification.show("Invalid analysis type: " + instruction, 
                    3000, Notification.Position.MIDDLE);
            return;
        }
        
        // Get the current user from the session
        User currentUser = session.getUser();
        if (currentUser == null) {
            LoggingUtil.error(LOG, "triggerAnalysis", "No user associated with session %s", session.getId());
            Notification.show("User information not available", 3000, Notification.Position.MIDDLE);
            return;
        }
        
        // Add user message to UI showing the analysis request
        String analysisPrompt = "Perform a " + instruction.toLowerCase() + " of this paper.";
        com.vaadin.flow.component.Component userMessage = new AIChatGeneralMesssageBubble(true, analysisPrompt);
        messagesContainer.add(userMessage);
        LoggingUtil.debug(LOG, "triggerAnalysis", "Added user analysis request to UI");
        
        // Add progress message with progress bar
        com.vaadin.flow.component.Component progressMessage = new AIChatProgressMessageBubble("Analyzing paper...");
        messagesContainer.add(progressMessage);
        LoggingUtil.info(LOG, "triggerAnalysis", "Added progress message with progress bar");
        
        // Get the progress bar component for updating
        ProgressBar progressBar = (ProgressBar) ((Div) ((HorizontalLayout) progressMessage).getComponentAt(1)).getComponentAt(1);
        progressBar.setValue(0.1); // Initial progress value
        
        // Scroll to bottom to show the progress bar using the improved approach
        UI.getCurrent().getPage().executeJs(
            "setTimeout(function() {" +
            "  const container = document.querySelector('.messages-container');" +
            "  if (container) {" +
            "    container.scrollTop = container.scrollHeight;" +
            "    console.log('Scrolled to bottom for progress bar, height:', container.scrollHeight);" +
            "  }" +
            "}, 100);"
        );
        LoggingUtil.debug(LOG, "triggerAnalysis", "Used improved scroll method for progress indicator");
        
        // Get UI instance for updating from background thread
        UI ui = UI.getCurrent();
        
        // Store final values for async thread
        final UUID finalPaperId = paperId;
        final AnalysisType finalAnalysisType = analysisType;
        final com.vaadin.flow.component.Component finalProgressMessage = progressMessage;
        final ProgressBar finalProgressBar = progressBar;
        
        // Start analysis in a background thread using Spring's task executor instead of manual thread creation
        taskExecutor.execute(() -> {
            try {
                // Simulate progress updates
                ui.access(() -> {
                    finalProgressBar.setValue(0.2);
                    LoggingUtil.debug(LOG, "triggerAnalysis", "Updated progress to 20%");
                });
                
                // Short delay to show progress
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                
                // Generate or retrieve analysis via PaperAnalysisService
                LoggingUtil.info(LOG, "triggerAnalysis", "Generating analysis for paper %s with type %s", 
                        finalPaperId, finalAnalysisType);
                
                // Update progress to 40%
                ui.access(() -> {
                    finalProgressBar.setValue(0.4);
                    LoggingUtil.debug(LOG, "triggerAnalysis", "Updated progress to 40%");
                });
                
                // Start generating the analysis
                AnalysisResult analysis = 
                        paperAnalysisService.getOrGenerateAnalysis(finalPaperId, finalAnalysisType, currentUser);
                
                // Update progress to 80%
                ui.access(() -> {
                    finalProgressBar.setValue(0.8);
                    LoggingUtil.debug(LOG, "triggerAnalysis", "Updated progress to 80%");
                });
                
                // Short delay to show progress
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                
                // Final progress update
                ui.access(() -> {
                    finalProgressBar.setValue(1.0);
                    LoggingUtil.debug(LOG, "triggerAnalysis", "Updated progress to 100%");
                });
                
                LoggingUtil.info(LOG, "triggerAnalysis", "Analysis completed: ID %s, size %d chars", 
                        analysis.getId(), analysis.getContent().length());
                
                // Update UI in the UI thread - use runnable inside access() to ensure UI is updated immediately
                ui.access(() -> {
                    try {
                        // First access - remove progress message and add the analysis message
                        messagesContainer.remove(finalProgressMessage);
                        LoggingUtil.debug(LOG, "triggerAnalysis", "Removed progress message from UI");
                        
                        // Use push to ensure UI updates immediately
                        ui.push();
                        
                        // Add analysis intro message in a second access call to ensure proper rendering
                        com.vaadin.flow.component.Component introMessage = new AIChatGeneralMesssageBubble(false, 
                                "I've completed the " + instruction + " of this paper:");
                        messagesContainer.add(introMessage);
                        
                        // Force UI update after intro message
                        ui.push();
                        
                        // Add analysis content message in a third access call
                        com.vaadin.flow.component.Component analysisMessage = new AIChatGeneralMesssageBubble(false, analysis.getContent());
                        messagesContainer.add(analysisMessage);
                        LoggingUtil.debug(LOG, "triggerAnalysis", "Added analysis messages to UI");
                        
                        // Force UI update again
                        ui.push();
                        
                        // Add messages to chat message table
                        try {
                            // Create introduction message
                            chatService.sendMessage(refreshedSession.getId(), 
                                    "I've completed the " + instruction + " of this paper:");
                            
                            // Create content message with the actual analysis
                            chatService.sendMessage(refreshedSession.getId(), analysis.getContent());
                            
                            // Update the session context to include this analysis
                            Map<String, Object> updatedContext = refreshedSession.getContext();
                            if (updatedContext == null) {
                                updatedContext = new HashMap<>();
                            }
                            
                            // Create a new typed analyses list
                            List<Map<String, String>> analyses = new ArrayList<>();
                            
                            // Add/update analyses list in context
                            if (updatedContext.containsKey("analyses")) {
                                Object analysesObj = updatedContext.get("analyses");
                                if (analysesObj instanceof List<?>) {
                                    // Type-check each element before adding to the new list
                                    for (Object item : (List<?>) analysesObj) {
                                        if (item instanceof Map<?, ?>) {
                                            try {
                                                // Create a new typed map to ensure type safety
                                                Map<String, String> typedMap = new HashMap<>();
                                                ((Map<?, ?>) item).forEach((key, value) -> {
                                                    if (key instanceof String && value instanceof String) {
                                                        typedMap.put((String) key, (String) value);
                                                    }
                                                });
                                                
                                                // Only add the map if it has content
                                                if (!typedMap.isEmpty()) {
                                                    analyses.add(typedMap);
                                                }
                                            } catch (ClassCastException e) {
                                                LoggingUtil.warn(LOG, "triggerAnalysis", 
                                                    "Skipping invalid analysis map entry: %s", e.getMessage());
                                            }
                                        }
                                    }
                                } else {
                                    LoggingUtil.warn(LOG, "triggerAnalysis", 
                                        "Expected List for analyses but got %s", 
                                        analysesObj != null ? analysesObj.getClass().getName() : "null");
                                }
                            }
                            
                            // Update the context with our type-safe list
                            updatedContext.put("analyses", analyses);
                            
                            // Add this analysis info
                            Map<String, String> analysisInfo = new HashMap<>();
                            analysisInfo.put("id", analysis.getId().toString());
                            analysisInfo.put("type", finalAnalysisType.toString());
                            analysisInfo.put("paperId", finalPaperId.toString());
                            analyses.add(analysisInfo);
                            
                            // Update session context using the refreshed session
                            refreshedSession.setContext(updatedContext);
                            chatService.refreshSession(refreshedSession.getId());
                            
                            LoggingUtil.info(LOG, "triggerAnalysis", "Saved analysis information to chat session context for session %s", refreshedSession.getId());
                        } catch (Exception ex) {
                            LoggingUtil.error(LOG, "triggerAnalysis", "Error saving analysis messages: %s", ex, ex.getMessage());
                        }
                        
                        // Ensure scrolling works by using a more reliable approach with a slight delay
                        // This ensures the DOM has fully updated before scrolling
                        UI.getCurrent().getPage().executeJs(
                            "setTimeout(function() {" +
                            "  const container = document.querySelector('.messages-container');" +
                            "  if (container) {" +
                            "    container.scrollTop = container.scrollHeight;" +
                            "    console.log('Scrolled to bottom, height:', container.scrollHeight);" +
                            "  }" +
                            "}, 100);"
                        );
                        LoggingUtil.debug(LOG, "triggerAnalysis", "Using improved scroll method to show analysis response");
                    } catch (Exception ex) {
                        LoggingUtil.error(LOG, "triggerAnalysis", "Error updating UI after analysis: %s", ex, ex.getMessage());
                    }
                });
            } catch (Exception e) {
                ui.access(() -> {
                    // Remove progress message in case of error
                    messagesContainer.remove(finalProgressMessage);
                    LoggingUtil.debug(LOG, "triggerAnalysis", "Removed progress message due to error");
                    
                    boolean isTimeout = (e instanceof RuntimeException) && 
                                       e.getMessage() != null && 
                                       e.getMessage().contains("Anthropic API is currently busy");
                    
                    if (isTimeout) {
                        // Handle timeout with user-friendly message
                        LoggingUtil.warn(LOG, "triggerAnalysis", "API timeout during analysis: %s", e.getMessage());
                        
                        // Add timeout message to chat
                        com.vaadin.flow.component.Component timeoutMessage = new AIChatGeneralMesssageBubble(false, 
                            "I'm sorry, but Anthropic AI is currently busy. Please try your analysis request again in a few moments.");
                        messagesContainer.add(timeoutMessage);
                        
                        // Also show notification
                        Notification notification = Notification.show(
                            "Anthropic API is currently busy. Please try again later.", 
                            5000, 
                            Notification.Position.MIDDLE
                        );
                        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                    } else {
                        // Handle other errors
                        LoggingUtil.error(LOG, "triggerAnalysis", "Error generating analysis: %s", e, e.getMessage());
                        Notification notification = Notification.show(
                            "Error generating analysis: " + e.getMessage(), 
                            3000, 
                            Notification.Position.MIDDLE
                        );
                        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                    
                    // Also ensure scroll works in error case
                    UI.getCurrent().getPage().executeJs(
                        "setTimeout(function() {" +
                        "  const container = document.querySelector('.messages-container');" +
                        "  if (container) {" +
                        "    container.scrollTop = container.scrollHeight;" +
                        "    console.log('Scrolled to bottom after error, height:', container.scrollHeight);" +
                        "  }" +
                        "}, 100);"
                    );
                });
            }
        });
    }
}

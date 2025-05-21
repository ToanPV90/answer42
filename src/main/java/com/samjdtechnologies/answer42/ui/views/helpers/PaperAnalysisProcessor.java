package com.samjdtechnologies.answer42.ui.views.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.AnalysisResult;
import com.samjdtechnologies.answer42.model.ChatSession;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.model.enums.AnalysisType;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.service.PaperAnalysisService;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Processor class for handling paper analysis requests.
 * Manages the workflow of requesting, displaying, and saving analysis results.
 */
public class PaperAnalysisProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(PaperAnalysisProcessor.class);

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
    public static void triggerAnalysis(String instruction, 
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
        Component userMessage = AIChatViewHelper.createMessageBubble(true, analysisPrompt);
        messagesContainer.add(userMessage);
        LoggingUtil.debug(LOG, "triggerAnalysis", "Added user analysis request to UI");
        
        // Add thinking message with spinner
        Component thinkingMessage = AIChatViewHelper.createThinkingMessage();
        messagesContainer.add(thinkingMessage);
        LoggingUtil.info(LOG, "triggerAnalysis", "Added thinking message with spinner");
        
        // Scroll to bottom to show the thinking message
        messagesContainer.getElement().executeJs(
            "this.scrollTop = this.scrollHeight"
        );
        LoggingUtil.debug(LOG, "triggerAnalysis", "Scrolled chat to bottom to show thinking indicator");
        
        // Get UI instance for updating from background thread
        UI ui = UI.getCurrent();
        
        // Store final values for async thread
        final UUID finalPaperId = paperId;
        final AnalysisType finalAnalysisType = analysisType;
        
        // Start analysis in a background thread
        new Thread(() -> {
            try {
                // Generate or retrieve analysis via PaperAnalysisService
                LoggingUtil.info(LOG, "triggerAnalysis", "Generating analysis for paper %s with type %s", 
                        finalPaperId, finalAnalysisType);
                
                AnalysisResult analysis = 
                        paperAnalysisService.getOrGenerateAnalysis(finalPaperId, finalAnalysisType, currentUser);
                
                LoggingUtil.info(LOG, "triggerAnalysis", "Analysis completed: ID %s, size %d chars", 
                        analysis.getId(), analysis.getContent().length());
                
                // Update UI in the UI thread - use runnable inside access() to ensure UI is updated immediately
                ui.access(() -> {
                    try {
                        // First access - remove thinking message and add the analysis message
                        messagesContainer.remove(thinkingMessage);
                        LoggingUtil.debug(LOG, "triggerAnalysis", "Removed thinking message from UI");
                        
                        // Use push to ensure UI updates immediately
                        ui.push();
                        
                        // Add analysis intro message in a second access call to ensure proper rendering
                        Component introMessage = AIChatViewHelper.createMessageBubble(false, 
                                "I've completed the " + instruction + " of this paper:");
                        messagesContainer.add(introMessage);
                        
                        // Force UI update after intro message
                        ui.push();
                        
                        // Add analysis content message in a third access call
                        Component analysisMessage = AIChatViewHelper.createMessageBubble(false, analysis.getContent());
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
                        
                        // Scroll to bottom to show the response
                        messagesContainer.getElement().executeJs(
                            "this.scrollTop = this.scrollHeight"
                        );
                        LoggingUtil.debug(LOG, "triggerAnalysis", "Scrolled chat to bottom to show analysis response");
                    } catch (Exception ex) {
                        LoggingUtil.error(LOG, "triggerAnalysis", "Error updating UI after analysis: %s", ex, ex.getMessage());
                    }
                });
            } catch (Exception e) {
                ui.access(() -> {
                    // Remove thinking message in case of error
                    messagesContainer.remove(thinkingMessage);
                    LoggingUtil.debug(LOG, "triggerAnalysis", "Removed thinking message due to error");
                    
                    LoggingUtil.error(LOG, "triggerAnalysis", "Error generating analysis: %s", e, e.getMessage());
                    Notification notification = Notification.show(
                        "Error generating analysis: " + e.getMessage(), 
                        3000, 
                        Notification.Position.MIDDLE
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                });
            }
        }).start();
    }
}

package com.samjdtechnologies.answer42.processors;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.daos.ChatMessage;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.ui.components.AIChatGeneralMesssageBubble;
import com.samjdtechnologies.answer42.ui.components.AIChatThinkingMessageBubble;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Helper class specifically for AI Chat UI feedback elements and thread handling.
 * Handles the "Thinking..." spinner, message bubbles, and asynchronous processing.
 * Uses Spring's thread management for background processing.
 */
@Component
public class AIChatMessageProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(AIChatMessageProcessor.class);
    
    private final Executor taskExecutor;
    
    /**
     * Creates a new AIChatChatMessage with Spring's task executor.
     * 
     * @param taskExecutor The thread pool task executor from Spring's configuration
     */
    public AIChatMessageProcessor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Send a message to the AI in a background thread with proper UI feedback.
     * This method handles showing/hiding the "Thinking..." animation and updating the UI.
     *
     * @param chatService The chat service to use
     * @param sessionId The chat session ID
     * @param message The message to send
     * @param messagesContainer The container where messages are shown
     * @param messageInput The input field for messages
     * @param sendButton The button to send messages
     */
    public void sendMessageWithUIFeedback(
            ChatService chatService,
            UUID sessionId,
            String message,
            VerticalLayout messagesContainer,
            TextField messageInput,
            Button sendButton) {
        
        // Already checked in caller that message isn't empty
        LoggingUtil.info(LOG, "sendMessageWithUIFeedback", "Sending message: '%s'", message);
        
        // Clear input and disable send button immediately
        messageInput.clear();
        sendButton.setEnabled(false);
        LoggingUtil.debug(LOG, "sendMessageWithUIFeedback", "Disabled send button");
        
        // Add user message to UI
        com.vaadin.flow.component.Component userMessageComponent = new AIChatGeneralMesssageBubble(true, message);
        messagesContainer.add(userMessageComponent);
        LoggingUtil.debug(LOG, "sendMessageWithUIFeedback", "Added user message bubble to UI");
        
        // Add thinking message with spinner
        com.vaadin.flow.component.Component thinkingMessage = new AIChatThinkingMessageBubble();
        messagesContainer.add(thinkingMessage);
        LoggingUtil.info(LOG, "sendMessageWithUIFeedback", "Added thinking message with spinner to UI");
        
        // Scroll to bottom to show the thinking message using a consistent approach
        UI.getCurrent().getPage().executeJs(
            "setTimeout(function() {" +
            "  const container = document.querySelector('.messages-container');" +
            "  if (container) {" +
            "    container.scrollTop = container.scrollHeight;" +
            "    console.log('Scrolled to bottom for thinking message, height:', container.scrollHeight);" +
            "  }" +
            "}, 100);"
        );
        LoggingUtil.debug(LOG, "sendMessageWithUIFeedback", "Scrolled chat to bottom");
        
        // Get the current UI reference
        final UI ui = UI.getCurrent();
        
        // Use Vaadin's background execution utility
        ui.setPollInterval(1000); // Enable polling to update UI during long operations
        
        // Store the final value for use in the lambda
        final String finalMessage = message;
        
        // Run the AI service call in a background thread using Spring's task executor
        taskExecutor.execute(() -> {
            try {
                // First, save the user message
                chatService.sendMessage(sessionId, finalMessage);
                
                // Then get the AI response
                ChatMessage response = chatService.sendUserMessageAndGetResponse(sessionId, finalMessage);
                LoggingUtil.info(LOG, "sendMessageWithUIFeedback", "Received response from AI service");
                
                // Update UI in the UI thread using Vaadin's access method
                ui.access(() -> {
                    try {
                        // Remove thinking message
                        messagesContainer.remove(thinkingMessage);
                        LoggingUtil.info(LOG, "sendMessageWithUIFeedback", "Removed thinking message");
                        
                        // Add AI response to UI
                        com.vaadin.flow.component.Component aiMessageComponent = new AIChatGeneralMesssageBubble(false, response.getContent());
                        messagesContainer.add(aiMessageComponent);
                        LoggingUtil.debug(LOG, "sendMessageWithUIFeedback", "Added AI response bubble to UI");
                        
                        // Scroll to bottom again to show the response using a consistent approach
                        UI.getCurrent().getPage().executeJs(
                            "setTimeout(function() {" +
                            "  const container = document.querySelector('.messages-container');" +
                            "  if (container) {" +
                            "    container.scrollTop = container.scrollHeight;" +
                            "    console.log('Scrolled to bottom for AI response, height:', container.scrollHeight);" +
                            "  }" +
                            "}, 100);"
                        );
                        LoggingUtil.debug(LOG, "sendMessageWithUIFeedback", "Scrolled chat to bottom again");
                    } finally {
                        // Re-enable send button
                        sendButton.setEnabled(true);
                        LoggingUtil.info(LOG, "sendMessageWithUIFeedback", "Re-enabled send button");
                        
                        // Re-focus on input field
                        messageInput.focus();
                        
                        // Disable polling after the task is done
                        ui.setPollInterval(-1);
                    }
                });
            } catch (Exception e) {
                // Handle errors by updating the UI
                ui.access(() -> {
                    try {
                        // Remove thinking message
                        messagesContainer.remove(thinkingMessage);
                        LoggingUtil.info(LOG, "sendMessageWithUIFeedback", "Removed thinking message due to error");
                        
                        LoggingUtil.error(LOG, "sendMessageWithUIFeedback", "Error sending message: %s", e, e.getMessage());
                        Notification notification = Notification.show(
                            "Error: " + e.getMessage(), 
                            3000, 
                            Notification.Position.MIDDLE
                        );
                        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    } finally {
                        // Always re-enable the send button and focus, even after errors
                        sendButton.setEnabled(true);
                        messageInput.focus();
                        
                        // Disable polling after the task is done
                        ui.setPollInterval(-1);
                    }
                });
            }
        });
    }
    
    /**
     * Process an analysis request with a "Thinking..." message and spinner.
     *
     * @param chatService The chat service to use
     * @param analysisType The type of analysis to perform
     * @param sessionId The chat session ID
     * @param messagesContainer The container where messages are shown
     * @param onAnalysisComplete Callback to run after analysis completion
     */
    public void processAnalysisWithUIFeedback(
            ChatService chatService,
            String analysisType,
            UUID sessionId,
            VerticalLayout messagesContainer,
            Consumer<com.vaadin.flow.component.Component> onAnalysisComplete) {
        
        LoggingUtil.info(LOG, "processAnalysisWithUIFeedback", "Starting analysis: %s", analysisType);
        
        // Generate the analysis prompt
        String analysisPrompt = "Perform a " + analysisType.toLowerCase() + " of this paper.";
        
        // Add user message with analysis prompt
        com.vaadin.flow.component.Component userMessageComponent = new AIChatGeneralMesssageBubble(true, analysisPrompt);
        messagesContainer.add(userMessageComponent);
        LoggingUtil.debug(LOG, "processAnalysisWithUIFeedback", "Added user analysis request to UI");
        
        // Add thinking message with spinner
        com.vaadin.flow.component.Component thinkingMessage = new AIChatThinkingMessageBubble();
        messagesContainer.add(thinkingMessage);
        LoggingUtil.info(LOG, "processAnalysisWithUIFeedback", "Added thinking message with spinner to UI");
        
        // Scroll to bottom to show the thinking message using a consistent approach
        UI.getCurrent().getPage().executeJs(
            "setTimeout(function() {" +
            "  const container = document.querySelector('.messages-container');" +
            "  if (container) {" +
            "    container.scrollTop = container.scrollHeight;" +
            "    console.log('Scrolled to bottom for analysis thinking, height:', container.scrollHeight);" +
            "  }" +
            "}, 100);"
        );
        LoggingUtil.debug(LOG, "processAnalysisWithUIFeedback", "Scrolled chat to bottom");
        
        // Get the current UI reference
        final UI ui = UI.getCurrent();
        
        // Use Vaadin's background execution utility
        ui.setPollInterval(1000); // Enable polling to update UI during long operations
        
        // Run the AI service call in a background thread using Spring's task executor
        taskExecutor.execute(() -> {
            try {
                // First, save the user message
                chatService.sendMessage(sessionId, analysisPrompt);
                
                // Then get the AI response
                ChatMessage response = chatService.sendUserMessageAndGetResponse(sessionId, analysisPrompt);
                LoggingUtil.info(LOG, "processAnalysisWithUIFeedback", "Received analysis response from AI service");
                
                // Update UI in the UI thread
                ui.access(() -> {
                    try {
                        // Remove thinking message
                        messagesContainer.remove(thinkingMessage);
                        LoggingUtil.info(LOG, "processAnalysisWithUIFeedback", "Removed thinking message");
                        
                        // Add AI response to UI
                        com.vaadin.flow.component.Component aiMessageComponent = new AIChatGeneralMesssageBubble(false, response.getContent());
                        messagesContainer.add(aiMessageComponent);
                        LoggingUtil.debug(LOG, "processAnalysisWithUIFeedback", "Added AI analysis response to UI");
                        
                        // Scroll to bottom again to show the response using a consistent approach
                        UI.getCurrent().getPage().executeJs(
                            "setTimeout(function() {" +
                            "  const container = document.querySelector('.messages-container');" +
                            "  if (container) {" +
                            "    container.scrollTop = container.scrollHeight;" +
                            "    console.log('Scrolled to bottom for analysis response, height:', container.scrollHeight);" +
                            "  }" +
                            "}, 100);"
                        );
                        LoggingUtil.debug(LOG, "processAnalysisWithUIFeedback", "Scrolled chat to bottom again");
                        
                        // Call the callback with the AI response component
                        if (onAnalysisComplete != null) {
                            onAnalysisComplete.accept(aiMessageComponent);
                        }
                    } finally {
                        // Disable polling after the task is done
                        ui.setPollInterval(-1);
                    }
                });
            } catch (Exception e) {
                // Handle errors by updating the UI
                ui.access(() -> {
                    try {
                        // Remove thinking message
                        messagesContainer.remove(thinkingMessage);
                        LoggingUtil.info(LOG, "processAnalysisWithUIFeedback", "Removed thinking message due to error");
                        
                        LoggingUtil.error(LOG, "processAnalysisWithUIFeedback", "Error during analysis: %s", e, e.getMessage());
                        Notification notification = Notification.show(
                            "Analysis error: " + e.getMessage(), 
                            3000, 
                            Notification.Position.MIDDLE
                        );
                        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    } finally {
                        // Disable polling after the task is done
                        ui.setPollInterval(-1);
                    }
                });
            }
        });
    }
    
    
}

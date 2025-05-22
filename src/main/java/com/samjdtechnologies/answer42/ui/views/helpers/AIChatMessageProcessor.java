package com.samjdtechnologies.answer42.ui.views.helpers;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.ChatMessage;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
     * Create a message bubble for the chat UI.
     * 
     * @param isUser Whether the message is from the user
     * @param message The message content
     * @return The message component
     */
    public static com.vaadin.flow.component.Component createMessageBubble(boolean isUser, String message) {
        HorizontalLayout container = new HorizontalLayout();
        container.addClassName(UIConstants.CSS_MESSAGE_CONTAINER);
        container.addClassNames(isUser ? UIConstants.CSS_USER_MESSAGE : UIConstants.CSS_ASSISTANT_MESSAGE);
        
        Avatar avatar = new Avatar();
        avatar.addClassName(UIConstants.CSS_MESSAGE_AVATAR);
        
        if (isUser) {
            avatar.setName("You");
            avatar.setImage("frontend/images/icons/user_avatar_icon.svg");
        } else {
            avatar.setName("AI");
            avatar.addClassName(UIConstants.CSS_AI_AVATAR);
            avatar.setImage("frontend/images/icons/ai_chatbot_avatar_blue.svg");
        }
        
        Div bubble = new Div();
        bubble.addClassName(UIConstants.CSS_MESSAGE_BUBBLE);
        
        if (message.contains("```")) {
            // Handle code blocks in message
            String[] parts = message.split("```");
            for (int i = 0; i < parts.length; i++) {
                if (i % 2 == 0) {
                    // Regular text
                    if (!parts[i].isEmpty()) {
                        Paragraph text = new Paragraph(parts[i]);
                        bubble.add(text);
                    }
                } else {
                    // Code block
                    Div codeBlock = new Div();
                    codeBlock.addClassName(UIConstants.CSS_CODE_BLOCK);
                    
                    String code = parts[i];
                    // Remove language identifier if present
                    if (code.contains("\n")) {
                        code = code.substring(code.indexOf("\n"));
                    }
                    
                    Paragraph codeParagraph = new Paragraph(code);
                    codeParagraph.getStyle().set("white-space", "pre-wrap");
                    codeBlock.add(codeParagraph);
                    bubble.add(codeBlock);
                }
            }
        } else {
            // Simple text message
            Paragraph text = new Paragraph(message);
            bubble.add(text);
        }
        
        if (isUser) {
            container.add(bubble, avatar);
        } else {
            container.add(avatar, bubble);
        }
        
        return container;
    }
    
    /**
     * Creates a "Thinking..." message with an animated spinner to indicate the AI is processing.
     * 
     * @return The component representing the thinking state
     */
    public static com.vaadin.flow.component.Component createThinkingMessage() {
        HorizontalLayout container = new HorizontalLayout();
        container.addClassName(UIConstants.CSS_MESSAGE_CONTAINER);
        container.addClassName(UIConstants.CSS_ASSISTANT_MESSAGE);
        
        Avatar avatar = new Avatar();
        avatar.addClassName(UIConstants.CSS_MESSAGE_AVATAR);
        avatar.setName("AI");
        avatar.addClassName(UIConstants.CSS_AI_AVATAR);
        avatar.setImage("frontend/images/icons/ai_chatbot_avatar_blue.svg");
        
        Div bubble = new Div();
        bubble.addClassName(UIConstants.CSS_MESSAGE_BUBBLE);
        
        // Create spinner
        Div spinner = new Div();
        spinner.addClassName(UIConstants.CSS_THINKING_SPINNER);
        
        // Create thinking text
        Span thinkingText = new Span("Thinking...");
        thinkingText.addClassName(UIConstants.CSS_THINKING_TEXT);
        
        // Create the layout for spinner and text
        HorizontalLayout thinkingLayout = new HorizontalLayout(spinner, thinkingText);
        thinkingLayout.addClassName(UIConstants.CSS_THINKING_LAYOUT);
        thinkingLayout.setSpacing(true);
        thinkingLayout.setPadding(false);
        thinkingLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        
        bubble.add(thinkingLayout);
        
        container.add(avatar, bubble);
        
        return container;
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
        com.vaadin.flow.component.Component userMessageComponent = createMessageBubble(true, message);
        messagesContainer.add(userMessageComponent);
        LoggingUtil.debug(LOG, "sendMessageWithUIFeedback", "Added user message bubble to UI");
        
        // Add thinking message with spinner
        com.vaadin.flow.component.Component thinkingMessage = createThinkingMessage();
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
                        com.vaadin.flow.component.Component aiMessageComponent = createMessageBubble(false, response.getContent());
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
        com.vaadin.flow.component.Component userMessageComponent = createMessageBubble(true, analysisPrompt);
        messagesContainer.add(userMessageComponent);
        LoggingUtil.debug(LOG, "processAnalysisWithUIFeedback", "Added user analysis request to UI");
        
        // Add thinking message with spinner
        com.vaadin.flow.component.Component thinkingMessage = createThinkingMessage();
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
                        com.vaadin.flow.component.Component aiMessageComponent = createMessageBubble(false, response.getContent());
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
    
    /**
     * Get the welcome message for a specific chat mode.
     * 
     * @param mode The chat mode to get the welcome message for
     * @return The welcome message for the specified mode
     */
    public static String getWelcomeMessageForMode(com.samjdtechnologies.answer42.model.enums.ChatMode mode) {
        switch (mode) {
            case CHAT:
                return "Select a paper from the top to start chatting about it with Claude AI.";
                
            case CROSS_REFERENCE:
                return "Select multiple papers to compare and find connections between them using GPT-4.";
                
            case RESEARCH_EXPLORER:
                return "Explore research topics and discover new papers with Perplexity AI.";
                
            default:
                return "Select a paper to begin.";
        }
    }
    
    /**
     * Get the helper text for the current mode.
     * 
     * @param mode The current chat mode
     * @return A span with the appropriate helper text
     */
    public static Span getSelectionHelperText(com.samjdtechnologies.answer42.model.enums.ChatMode mode) {
        String text;
        
        switch (mode) {
            case CHAT:
                text = "Select exactly 1 paper for Paper Chat mode";
                break;
                
            case CROSS_REFERENCE:
                text = "Select 2-5 papers to compare";
                break;
                
            case RESEARCH_EXPLORER:
                text = "Select papers for contextual research (optional)";
                break;
                
            default:
                text = "Select papers to begin";
        }
        
        Span span = new Span(text);
        span.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        
        return span;
    }
}

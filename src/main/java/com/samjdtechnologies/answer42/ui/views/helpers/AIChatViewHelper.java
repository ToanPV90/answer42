package com.samjdtechnologies.answer42.ui.views.helpers;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.daos.ChatSession;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.processors.AnthropicPaperAnalysisProcessor;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.service.PaperAnalysisService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Helper class for AIChatView to keep the view class under 300 lines.
 * Contains methods for creating UI components and handling chat interactions.
 */
@Component
public class AIChatViewHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AIChatViewHelper.class);
    
    private final AnthropicPaperAnalysisProcessor paperAnalysisProcessor;
    
    /**
     * Creates a new AIChatViewHelper with dependency on PaperAnalysisProcessor.
     * 
     * @param paperAnalysisProcessor The processor for paper analysis
     */
    public AIChatViewHelper(AnthropicPaperAnalysisProcessor paperAnalysisProcessor) {
        this.paperAnalysisProcessor = paperAnalysisProcessor;
    }

    /**
     * Create an icon for the paper chat tab using SVG file.
     * 
     * @return The icon component
     */
    public static com.vaadin.flow.component.Component createPaperChatIcon() {
        // Use Vaadin's Image component to display the SVG file
        com.vaadin.flow.component.html.Image image = new com.vaadin.flow.component.html.Image(
                "frontend/images/icons/paper_chat_icon.svg", "Anthropic Claude icon");
        image.setHeight("20px");
        image.setWidth("20px");
        
        return image;
    }

    /**
     * Create an icon for the OpenAI (ChatGPT) tab using SVG file.
     * 
     * @return The icon component
     */
    public static com.vaadin.flow.component.Component createOpenAIIcon() {
        // Use Vaadin's Image component to display the SVG file
        com.vaadin.flow.component.html.Image image = new com.vaadin.flow.component.html.Image(
                "frontend/images/icons/openai_icon.svg", "OpenAI icon");
        image.setHeight("20px");
        image.setWidth("20px");
        
        return image;
    }

    /**
     * Create an icon for the Perplexity tab using SVG file.
     * 
     * @return The icon component
     */
    public static com.vaadin.flow.component.Component createPerplexityIcon() {
        // Use Vaadin's Image component to display the SVG file
        com.vaadin.flow.component.html.Image image = new com.vaadin.flow.component.html.Image(
                "frontend/images/icons/perplexity_icon.svg", "Perplexity icon");
        image.setHeight("20px");
        image.setWidth("20px");
        
        return image;
    }

    /**
     * Create a button for paper analysis functions.
     * 
     * @param text The button text
     * @param iconType The icon type to use for the button
     * @param color The icon color CSS variable
     * @param clickHandler The handler for button clicks
     * @return The styled button
     */
    public static Button createAnalysisButton(String text, VaadinIcon iconType, String color, 
                                             Runnable clickHandler) {
        Button button = new Button(text);
        Icon icon = iconType.create();
        icon.addClassName(UIConstants.CSS_ICON_COLOR);
        icon.getStyle().set("--icon-color", color);
        
        button.setIcon(icon);
        button.addClassName(UIConstants.CSS_ANALYSIS_BUTTON);
        
        button.addClickListener(e -> clickHandler.run());
        return button;
    }

    /**
     * Trigger a paper analysis and add the results to the chat.
     * This is a delegate method that calls the PaperAnalysisProcessor.
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
        // Delegate to the PaperAnalysisProcessor to handle paper analysis
        paperAnalysisProcessor.triggerAnalysis(instruction, chatService, paperAnalysisService, 
                                             session, messagesContainer);
    }
    
    /**
     * Create or update a chat session based on selected papers and mode.
     * 
     * @param chatService The chat service
     * @param currentUser The current user
     * @param selectedPaperIds List of selected paper IDs
     * @param mode The chat mode
     * @param provider The AI provider
     * @return The created or updated chat session
     */
    public static ChatSession createOrUpdateChatSession(ChatService chatService, User currentUser,
                                                     List<UUID> selectedPaperIds, 
                                                     ChatMode mode, AIProvider provider) {
        if (currentUser == null || selectedPaperIds.isEmpty()) {
            return null;
        }
        
        try {
            // Create a new chat session
            ChatSession session = chatService.createChatSession(currentUser, mode, provider);
            LoggingUtil.info(LOG, "createOrUpdateChatSession", "Created new chat session with ID: %s", session.getId());
            
            // Add papers to the session
            for (UUID paperId : selectedPaperIds) {
                chatService.addPaperToSession(session.getId(), paperId);
            }
            
            return session;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createOrUpdateChatSession", "Error creating chat session: %s", e, e.getMessage());
            Notification notification = Notification.show(
                "Error creating chat session: " + e.getMessage(), 
                3000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return null;
        }
    }
    
    /**
     * Interface for handling paper selection events.
     */
    public interface PaperSelectionHandler {
        /**
         * Called when papers are selected in the paper selection dialog.
         * 
         * @param selectedPapers The list of papers that were selected
         */
        void onPapersSelected(List<Paper> selectedPapers);
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

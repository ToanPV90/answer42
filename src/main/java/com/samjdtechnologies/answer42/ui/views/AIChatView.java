package com.samjdtechnologies.answer42.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.daos.ChatSession;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.processors.AIChatMessageProcessor;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.service.PaperAnalysisService;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.ui.components.AIChatContainer;
import com.samjdtechnologies.answer42.ui.components.AIChatGeneralMesssageBubble;
import com.samjdtechnologies.answer42.ui.components.AIChatModeTabs;
import com.samjdtechnologies.answer42.ui.components.AIChatWelcomeSection;
import com.samjdtechnologies.answer42.ui.components.AnthropicPoweredAnalysisSection;
import com.samjdtechnologies.answer42.ui.components.PaperPill;
import com.samjdtechnologies.answer42.ui.components.PaperSelectionDialog;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.helpers.views.AIChatViewHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The AI Chat View provides an interface for users to interact with AI assistants
 * for analyzing and discussing research papers.
 */
@Route(value = UIConstants.ROUTE_AI_CHAT, layout = MainLayout.class)
@PageTitle("AI Chat | Answer42")
@Secured("ROLE_USER")
public class AIChatView extends Div implements BeforeEnterObserver {
    private static final Logger LOG = LoggerFactory.getLogger(AIChatView.class);
    
    private final ChatService chatService;
    private final PaperService paperService;
    private final PaperAnalysisService paperAnalysisService;
    private final AIChatViewHelper aiChatViewHelper;
    private final AIChatMessageProcessor aIChatMessageProcessor;
    
    // UI Components
    private final HorizontalLayout selectedPapersContainer = new HorizontalLayout();
    private final VerticalLayout messagesContainer = new VerticalLayout();
    private final TextField messageInput = new TextField();
    private final Button sendButton = new Button("Send");
    
    private AIChatModeTabs chatModeTabs;
    
    private VerticalLayout analysisContainer = new VerticalLayout();
    
    // State variables
    private User currentUser;
    private ChatSession activeSession;
    private ChatMode currentMode = ChatMode.CHAT;
    private AIProvider currentProvider = AIProvider.ANTHROPIC;
    private final List<UUID> selectedPaperIds = new ArrayList<>();
    private Dialog paperSelectionDialog;
    
    /**
     * Creates a new AIChatView with the necessary services.
     * 
     * @param chatService The chat service for AI interactions
     * @param paperService The paper service for accessing papers
     * @param paperAnalysisService The service for generating paper analyses
     * @param aiChatViewHelper The helper for chat-related UI operations
     * @param aIChatMessageProcessor The helper for AI chat UI feedback
     */
    public AIChatView(ChatService chatService, 
                      PaperService paperService, 
                      PaperAnalysisService paperAnalysisService,
                      AIChatViewHelper aiChatViewHelper,
                      AIChatMessageProcessor aIChatMessageProcessor) {
        this.paperAnalysisService = paperAnalysisService;
        this.chatService = chatService;
        this.paperService = paperService;
        this.aiChatViewHelper = aiChatViewHelper;
        this.aIChatMessageProcessor = aIChatMessageProcessor;
        
        addClassName(UIConstants.CSS_AI_CHAT_VIEW);
        getStyle().setHeight("auto");
        
        // Initialization is done in beforeEnter to ensure we have the user
    }

    private void initializeView() {
        removeAll();
        
        chatModeTabs = new AIChatModeTabs(this::handleChatModeChange);
        
        // Paper selection area
        initializePaperSelectionArea();

        analysisContainer = new AnthropicPoweredAnalysisSection(this::triggerAnalysis);
        
        // Add all main components to view
        add(new AIChatWelcomeSection(this::openPaperSelectionDialog), 
            chatModeTabs, 
            createPaperSelectionArea(), 
            analysisContainer, 
            new AIChatContainer(messagesContainer, 
                messageInput, 
                sendButton, 
                this::sendMessage));
        
        // Initialize paper selection dialog (hidden until needed)
        initializePaperSelectionDialog();
        
        // Add welcome message
        addAssistantMessage("Select a paper from the top to start chatting about it.");
    }

    /**
     * Initialize the paper selection area.
     */
    private void initializePaperSelectionArea() {
        selectedPapersContainer.addClassName(UIConstants.CSS_SELECTED_PAPERS_CONTAINER);
        selectedPapersContainer.setWidthFull();
    }

    /**
     * Initialize the paper selection dialog.
     */
    private void initializePaperSelectionDialog() {
        List<Paper> availablePapers = paperService.getRecentPapersByUser(currentUser, 100);
        
        paperSelectionDialog = new PaperSelectionDialog(
                availablePapers,
                selectedPaperIds,
                currentMode,
                this::handlePaperSelection);
    }

    /**
     * Handle paper selection from the dialog.
     */
    private void handlePaperSelection(List<Paper> selectedPapers) {
        // Clear existing selections
        selectedPaperIds.clear();
        selectedPapersContainer.removeAll();
        
        // Add new selections
        for (Paper paper : selectedPapers) {
            selectedPaperIds.add(paper.getId());
            
            // Create pill button for paper
            Div paperPill = new PaperPill(paper, this::removePaper);
            selectedPapersContainer.add(paperPill);
        }
        
        // Create or update chat session if papers are selected
        if (!selectedPaperIds.isEmpty()) {
            createOrUpdateChatSession();
            
            // Enable chat input
            messageInput.setEnabled(true);
            sendButton.setEnabled(true);
            
            // Show analysis buttons for paper chat mode
            updateAnalysisAreaVisibility();
            
            // Clear previous messages
            messagesContainer.removeAll();
            
            // Add acknowledgment message about selected papers
            String paperAcknowledgment;
            if (selectedPapers.size() == 1) {
                Paper paper = selectedPapers.get(0);
                paperAcknowledgment = "I'm ready to chat about \"" + paper.getTitle() + "\". What would you like to know about this paper?";
            } else {
                // For multiple papers in cross-reference mode
                StringBuilder paperTitles = new StringBuilder();
                for (int i = 0; i < selectedPapers.size(); i++) {
                    if (i > 0) {
                        paperTitles.append(i == selectedPapers.size() - 1 ? " and " : ", ");
                    }
                    paperTitles.append("\"").append(selectedPapers.get(i).getTitle()).append("\"");
                }
                paperAcknowledgment = "I'm ready to compare " + paperTitles.toString() + ". Ask me about relationships, similarities, or differences between these papers.";
            }
            
            addAssistantMessage(paperAcknowledgment);
        } else {
            // Disable chat input if no papers
            messageInput.setEnabled(false);
            sendButton.setEnabled(false);
            analysisContainer.setVisible(false);
        }
    }
    
    /**
     * Handle chat mode changes from the AIChatModeTabs component.
     *
     * @param mode The new chat mode
     * @param provider The new AI provider
     */
    private void handleChatModeChange(ChatMode mode, AIProvider provider) {
        LoggingUtil.debug(LOG, "handleChatModeChange", "Switching to %s mode with %s provider", mode, provider);
        
        // Update the current mode and provider
        currentMode = mode;
        currentProvider = provider;
        
        // Clear active session and messages
        activeSession = null;
        messagesContainer.removeAll();
        
        // Add welcome message for the selected mode
        String welcomeMessage = AIChatViewHelper.getWelcomeMessageForMode(currentMode);
        addAssistantMessage(welcomeMessage);
        
        // Update UI for this mode
        updatePaperSelectionAreaForMode();
        updateAnalysisAreaVisibility();
    }
    
    /**
     * Create or update the chat session with selected papers.
     */
    private void createOrUpdateChatSession() {
        activeSession = AIChatViewHelper.createOrUpdateChatSession(
                chatService, 
                currentUser, 
                selectedPaperIds, 
                currentMode, 
                currentProvider);
    }

    /**
     * Create the paper selection area layout.
     */
    private Component createPaperSelectionArea() {
        VerticalLayout paperSelectionArea = new VerticalLayout();
        paperSelectionArea.addClassName(UIConstants.CSS_PAPER_SELECTION_AREA);
        paperSelectionArea.setPadding(false);
        paperSelectionArea.setSpacing(false);
        
        // Mode-specific selection helper text
        Span selectionHelper = AIChatViewHelper.getSelectionHelperText(currentMode);
        
        // Show selected papers with the selection helper text
        paperSelectionArea.add(selectionHelper, selectedPapersContainer);
        return paperSelectionArea;
    }

    /**
     * Update the paper selection area based on the current mode.
     */
    private void updatePaperSelectionAreaForMode() {
        // If switching modes, clear selected papers
        selectedPaperIds.clear();
        selectedPapersContainer.removeAll();
        
        // Disable chat input
        messageInput.setEnabled(false);
        sendButton.setEnabled(false);
    }
    
    /**
     * Update the visibility of the analysis area.
     */
    private void updateAnalysisAreaVisibility() {
        // Only show for Paper Chat mode with selected papers
        analysisContainer.setVisible(currentMode == ChatMode.CHAT && !selectedPaperIds.isEmpty());
    }
    
    /**
     * Open the paper selection dialog.
     */
    private void openPaperSelectionDialog() {
        // Refresh dialog with current papers and mode
        initializePaperSelectionDialog();
        paperSelectionDialog.open();
    }
    
    /**
     * Remove a paper from the selection.
     */
    private void removePaper(UUID paperId) {
        selectedPaperIds.remove(paperId);
        
        // Refresh the paper pills
        selectedPapersContainer.removeAll();
        
        // Re-add remaining papers
        for (UUID id : selectedPaperIds) {
            paperService.getPaperById(id).ifPresent(paper -> {
                Div paperPill = new PaperPill(paper, this::removePaper);
                selectedPapersContainer.add(paperPill);
            });
        }
        
        // Update chat session
        if (activeSession != null) {
            chatService.removePaperFromSession(activeSession.getId(), paperId);
        }
        
        // If no papers left, disable input and hide analysis area
        if (selectedPaperIds.isEmpty()) {
            messageInput.setEnabled(false);
            sendButton.setEnabled(false);
            analysisContainer.setVisible(false);
            
            // Clear active session
            activeSession = null;
        }
    }
    
    /**
     * Send a message to the AI chat.
     */
    private void sendMessage() {
        String message = messageInput.getValue().trim();
        if (message.isEmpty() || activeSession == null) {
            LoggingUtil.debug(LOG, "sendMessage", "Empty message or no active session, not sending");
            return;
        }

        // Use the AIChatMessageProcessor to handle the message sending with UI feedback
        aIChatMessageProcessor.sendMessageWithUIFeedback(
                chatService,
                activeSession.getId(),
                message,
                messagesContainer,
                messageInput,
                sendButton);
    }
    
    /**
     * Trigger an analysis action.
     */
    private void triggerAnalysis(String analysisType) {
        LoggingUtil.info(LOG, "triggerAnalysis", "Starting analysis: %s", analysisType);
        
        // Get all analysis buttons to disable them during processing
        List<Button> analysisButtons = new ArrayList<>();
        analysisContainer.getChildren()
            .filter(component -> component instanceof HorizontalLayout)
            .forEach(layout -> {
                ((HorizontalLayout) layout).getChildren()
                    .filter(component -> component instanceof Button)
                    .forEach(button -> analysisButtons.add((Button) button));
            });
        
        LoggingUtil.debug(LOG, "triggerAnalysis", "Found %d analysis buttons to disable", analysisButtons.size());
        
        // Disable all analysis buttons
        analysisButtons.forEach(button -> button.setEnabled(false));
        LoggingUtil.info(LOG, "triggerAnalysis", "Disabled all analysis buttons");
        
        try {
            // Trigger the analysis with the helper method using AIChatViewHelper
            LoggingUtil.info(LOG, "triggerAnalysis", "Calling aiChatViewHelper.triggerAnalysis with type: %s", analysisType);
            aiChatViewHelper.triggerAnalysis(analysisType, chatService, paperAnalysisService, activeSession, messagesContainer);
        } finally {
            // Re-enable all analysis buttons
            analysisButtons.forEach(button -> button.setEnabled(true));
            LoggingUtil.info(LOG, "triggerAnalysis", "Re-enabled all analysis buttons");
        }
    }
    
    /**
     * Add an assistant message to the chat.
     */
    private void addAssistantMessage(String message) {
        Component messageComponent = new AIChatGeneralMesssageBubble(false, message);
        messagesContainer.add(messageComponent);
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting user from session");
        
        // Get the current user from MainLayout
        currentUser = MainLayout.getCurrentUser();
        
        if (currentUser != null) {
            LoggingUtil.debug(LOG, "beforeEnter", "Retrieved user from session: %s (ID: %s)", 
                    currentUser.getUsername(), currentUser.getId());
            
            // Initialize the view with the user's data
            initializeView();
        } else {
            LoggingUtil.warn(LOG, "beforeEnter", "No user found in session, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}

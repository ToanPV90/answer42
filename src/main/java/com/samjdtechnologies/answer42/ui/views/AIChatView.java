package com.samjdtechnologies.answer42.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.ChatSession;
import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.ui.views.helpers.AIChatUIHelper;
import com.samjdtechnologies.answer42.ui.views.helpers.AIChatViewHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
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
    
    // UI Components
    private final HorizontalLayout selectedPapersContainer = new HorizontalLayout();
    private final VerticalLayout messagesContainer = new VerticalLayout();
    private final TextField messageInput = new TextField();
    private final Button sendButton = new Button("Send");
    
    private final Tabs chatModeTabs = new Tabs();
    private final Tab paperChatTab = new Tab();
    private final Tab comparePapersTab = new Tab();
    private final Tab researchExplorerTab = new Tab();
    
    private final VerticalLayout analysisContainer = new VerticalLayout();
    
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
     */
    public AIChatView(ChatService chatService, PaperService paperService) {
        this.chatService = chatService;
        this.paperService = paperService;
        
        addClassName(UIConstants.CSS_AI_CHAT_VIEW);
        getStyle().setHeight("auto");
        
        // Initialization is done in beforeEnter to ensure we have the user
    }
    
    /**
     * Initialize view components after we have the user.
     */
    /**
     * Creates a welcome section for the AI Chat view.
     * @return The welcome section component
     */
    private Component createWelcomeSection() {
        // Main container
        VerticalLayout section = new VerticalLayout();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        
        // Container for header content with title/description and button
        HorizontalLayout headerContent = new HorizontalLayout();
        headerContent.setWidthFull();
        headerContent.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        
        // Title and description
        H1 welcomeTitle = new H1("AI Chat");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);

        Paragraph welcomeSubtitle = new Paragraph(
                "Add Papers, then chat with Claude AI, compare papers with ChatGPT, or explore research with Perplexity to analyze, understand, and discover scientific literature.");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);

        // Left side with title and description
        VerticalLayout headerLeft = new VerticalLayout(welcomeTitle, welcomeSubtitle);
        headerLeft.setPadding(false);
        headerLeft.setSpacing(false);
        
        // Right side with Add Papers button
        Button addPapersButton = new Button("Add Papers");
        addPapersButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addPapersButton.setIcon(VaadinIcon.PLUS.create());
        addPapersButton.addClickListener(e -> openPaperSelectionDialog());
        
        // Add button to its own container for alignment
        Div buttonContainer = new Div(addPapersButton);
        buttonContainer.getStyle().set("margin-top", "var(--lumo-space-m)");
        
        headerContent.add(headerLeft, buttonContainer);
        section.add(headerContent);
        
        return section;
    }
    
    private void initializeView() {
        removeAll();
        
        // Chat mode tabs
        initializeChatModeTabs();
        
        // Paper selection area
        initializePaperSelectionArea();
        
        // Analysis buttons area
        initializeAnalysisArea();
        
        // Add all main components to view
        add(createWelcomeSection(), chatModeTabs, createPaperSelectionArea(), analysisContainer, createChatContainer());
        
        // Initialize paper selection dialog (hidden until needed)
        initializePaperSelectionDialog();
        
        // Add welcome message
        addAssistantMessage("Select a paper from the top to start chatting about it.");
    }
    
    /**
     * Initialize the chat mode tabs.
     */
    private void initializeChatModeTabs() {
        chatModeTabs.removeAll();
        chatModeTabs.addClassName(UIConstants.CSS_CHAT_TABS);
        
        // Paper Chat Tab - use HorizontalLayout to place icon next to label
        HorizontalLayout paperChatContent = new HorizontalLayout();
        paperChatContent.setPadding(false);
        paperChatContent.setSpacing(true);
        paperChatContent.setAlignItems(FlexComponent.Alignment.CENTER);
        
        Component paperChatIcon = AIChatViewHelper.createPaperChatIcon();
        paperChatIcon.getElement().getStyle().set("margin-left", "4px");
        Span paperChatLabel = new Span("Paper Chat");
        
        paperChatContent.add(paperChatLabel, paperChatIcon);
        paperChatTab.add(paperChatContent);
        
        // Compare Papers Tab - use HorizontalLayout to place icon next to label
        HorizontalLayout comparePapersContent = new HorizontalLayout();
        comparePapersContent.setPadding(false);
        comparePapersContent.setSpacing(true);
        comparePapersContent.setAlignItems(Alignment.CENTER);
        
        Component openAIIcon = AIChatViewHelper.createOpenAIIcon();
        openAIIcon.getElement().getStyle().set("margin-left", "4px");
        Span comparePapersLabel = new Span("Compare Papers");
        
        comparePapersContent.add(comparePapersLabel, openAIIcon);
        comparePapersTab.add(comparePapersContent);
        
        // Research Explorer Tab - use HorizontalLayout to place icon next to label
        HorizontalLayout researchExplorerContent = new HorizontalLayout();
        researchExplorerContent.setPadding(false);
        researchExplorerContent.setSpacing(true);
        researchExplorerContent.setAlignItems(Alignment.CENTER);
        
        Component perplexityIcon = AIChatViewHelper.createPerplexityIcon();
        perplexityIcon.getElement().getStyle().set("margin-left", "4px");
        Span researchExplorerLabel = new Span("Research Explorer");
        
        researchExplorerContent.add(researchExplorerLabel, perplexityIcon);
        researchExplorerTab.add(researchExplorerContent);
        
        // Add tabs and set up change listener
        chatModeTabs.add(paperChatTab, comparePapersTab, researchExplorerTab);
        chatModeTabs.addSelectedChangeListener(e -> updateChatMode());
        
        // Select Paper Chat tab by default
        chatModeTabs.setSelectedTab(paperChatTab);
    }
    
    /**
     * Update the current chat mode based on the selected tab.
     */
    private void updateChatMode() {
        Tab selectedTab = chatModeTabs.getSelectedTab();
        
        if (selectedTab == paperChatTab) {
            LoggingUtil.debug(LOG, "updateChatMode", "Switching to Paper Chat mode");
            currentMode = ChatMode.CHAT;
            currentProvider = AIProvider.ANTHROPIC;
        } else if (selectedTab == comparePapersTab) {
            LoggingUtil.debug(LOG, "updateChatMode", "Switching to Compare Papers mode");
            currentMode = ChatMode.CROSS_REFERENCE;
            currentProvider = AIProvider.OPENAI;
        } else if (selectedTab == researchExplorerTab) {
            LoggingUtil.debug(LOG, "updateChatMode", "Switching to Research Explorer mode");
            currentMode = ChatMode.RESEARCH_EXPLORER;
            currentProvider = AIProvider.PERPLEXITY;
        }
        
        // Clear active session and messages
        activeSession = null;
        messagesContainer.removeAll();
        
        // Add welcome message for the selected mode
        String welcomeMessage = AIChatUIHelper.getWelcomeMessageForMode(currentMode);
        addAssistantMessage(welcomeMessage);
        
        // Update UI for this mode
        updatePaperSelectionAreaForMode();
        updateAnalysisAreaVisibility();
    }
    
    /**
     * Initialize the paper selection area.
     */
    private void initializePaperSelectionArea() {
        selectedPapersContainer.addClassName(UIConstants.CSS_SELECTED_PAPERS_CONTAINER);
        selectedPapersContainer.setWidthFull();
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
        Span selectionHelper = AIChatUIHelper.getSelectionHelperText(currentMode);
        
        // Show selected papers with the selection helper text
        paperSelectionArea.add(selectionHelper, selectedPapersContainer);
        return paperSelectionArea;
    }
    
    /**
     * Initialize the analysis area with buttons.
     */
    private void initializeAnalysisArea() {
        analysisContainer.addClassName(UIConstants.CSS_ANALYSIS_CONTAINER);
        analysisContainer.setPadding(false);
        analysisContainer.setSpacing(false);
        analysisContainer.setVisible(false);
        
        H4 analysisTitle = new H4("Anthropic-Powered Paper Analysis");
        analysisTitle.addClassName(UIConstants.CSS_ANALYSIS_TITLE);
        
        Span analysisInfo = new Span("Analysis may take 1-2 minutes to complete");
        analysisInfo.addClassName(UIConstants.CSS_ANALYSIS_INFO);
        
        HorizontalLayout analysisButtons = new HorizontalLayout();
        analysisButtons.addClassName(UIConstants.CSS_ANALYSIS_BUTTONS);
        
        // Add analysis buttons
        Button deepSummaryButton = AIChatViewHelper.createAnalysisButton(
                "Deep Summary", 
                VaadinIcon.PENCIL,
                "var(--lumo-primary-color)",
                () -> triggerAnalysis("Deep Summary"));
        
        Button methodologyButton = AIChatViewHelper.createAnalysisButton(
                "Methodology Analysis", 
                VaadinIcon.CLIPBOARD_TEXT,
                "var(--lumo-success-color)",
                () -> triggerAnalysis("Methodology Analysis"));
        
        Button resultsButton = AIChatViewHelper.createAnalysisButton(
                "Results Interpretation", 
                VaadinIcon.CHART,
                "var(--lumo-primary-color-50pct)",
                () -> triggerAnalysis("Results Interpretation"));
        
        Button evaluationButton = AIChatViewHelper.createAnalysisButton(
                "Critical Evaluation", 
                VaadinIcon.SCALE,
                "var(--lumo-error-color)",
                () -> triggerAnalysis("Critical Evaluation"));
        
        Button implicationsButton = AIChatViewHelper.createAnalysisButton(
                "Research Implications", 
                VaadinIcon.LIGHTBULB,
                "var(--lumo-contrast-60pct)",
                () -> triggerAnalysis("Research Implications"));
        
        analysisButtons.add(
                deepSummaryButton, 
                methodologyButton, 
                resultsButton, 
                evaluationButton, 
                implicationsButton);
        
        analysisContainer.add(analysisTitle, analysisInfo, analysisButtons);
    }
    
    /**
     * Create the chat container with messages and input.
     */
    private Component createChatContainer() {
        VerticalLayout chatContainer = new VerticalLayout();
        chatContainer.addClassName(UIConstants.CSS_CHAT_CONTAINER);
        chatContainer.setSizeFull();
        chatContainer.getStyle().set("min-height", "500px");
        chatContainer.setPadding(false);
        chatContainer.setSpacing(false);
        
        // Messages container
        messagesContainer.addClassName(UIConstants.CSS_MESSAGES_CONTAINER);
        messagesContainer.setPadding(true);
        messagesContainer.setHeightFull();
        
        // Input container
        HorizontalLayout inputContainer = new HorizontalLayout();
        inputContainer.addClassName(UIConstants.CSS_MESSAGE_INPUT_CONTAINER);
        inputContainer.setWidthFull();
        
        messageInput.addClassName(UIConstants.CSS_MESSAGE_INPUT);
        messageInput.setPlaceholder("Ask a question about this paper...");
        messageInput.setClearButtonVisible(true);
        messageInput.addThemeVariants(TextFieldVariant.LUMO_ALIGN_CENTER);
        messageInput.setWidthFull();
        messageInput.setEnabled(false); // Disabled until paper is selected
        messageInput.setValueChangeMode(ValueChangeMode.EAGER);
        
        // Add a keyboard shortcut for the send button
        sendButton.addClickShortcut(Key.ENTER);
        
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.setIcon(VaadinIcon.PAPERPLANE.create());
        sendButton.setEnabled(false); // Disabled until paper is selected
        sendButton.addClickListener(e -> sendMessage());
        
        inputContainer.add(messageInput, sendButton);
        
        // Help text
        Span shortcutHelp = new Span("Press Enter to send, Shift+Enter for new line");
        shortcutHelp.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("text-align", "center");
        
        Span tipHelp = new Span("Ask specific questions about the selected papers with Claude AI and get cited answers");
        tipHelp.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("text-align", "center")
                .set("margin-bottom", "var(--lumo-space-m)");
        
        chatContainer.add(messagesContainer, tipHelp, inputContainer, shortcutHelp);
        
        return chatContainer;
    }
    
    /**
     * Initialize the paper selection dialog.
     */
    private void initializePaperSelectionDialog() {
        List<Paper> availablePapers = paperService.getRecentPapersByUser(currentUser, 100);
        
        paperSelectionDialog = AIChatViewHelper.createPaperSelectionDialog(
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
            Div paperPill = createPaperPill(paper);
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
     * Create a pill button for a selected paper.
     */
    private Div createPaperPill(Paper paper) {
        Div pill = new Div();
        pill.addClassName(UIConstants.CSS_PAPER_PILL);
        
        String title = paper.getTitle();
        // Truncate if too long
        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }
        
        // Paper title text
        Span titleSpan = new Span(title);
        
        // Remove button
        Icon removeIcon = VaadinIcon.CLOSE_SMALL.create();
        removeIcon.addClassName(UIConstants.CSS_REMOVE_BUTTON);
        removeIcon.addClickListener(e -> removePaper(paper.getId()));
        removeIcon.getElement().setAttribute("title", "Remove paper");
        removeIcon.getStyle().set("cursor", "pointer");
        
        pill.add(titleSpan, removeIcon);
        return pill;
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
                Div paperPill = createPaperPill(paper);
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
     * Send a message to the AI chat.
     */
    private void sendMessage() {
        String message = messageInput.getValue().trim();
        if (message.isEmpty() || activeSession == null) {
            LoggingUtil.debug(LOG, "sendMessage", "Empty message or no active session, not sending");
            return;
        }

        // Use the AIChatUIHelper to handle the message sending with UI feedback
        AIChatUIHelper.sendMessageWithUIFeedback(
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
            // Trigger the analysis with the helper method
            LoggingUtil.info(LOG, "triggerAnalysis", "Calling AIChatViewHelper.triggerAnalysis with type: %s", analysisType);
            AIChatViewHelper.triggerAnalysis(analysisType, chatService, activeSession, messagesContainer);
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
        Component messageComponent = AIChatViewHelper.createMessageBubble(false, message);
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

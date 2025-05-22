package com.samjdtechnologies.answer42.ui.views.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.ChatSession;
import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.service.ChatService;
import com.samjdtechnologies.answer42.service.PaperAnalysisService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Helper class for AIChatView to keep the view class under 300 lines.
 * Contains methods for creating UI components and handling chat interactions.
 */
@Component
public class AIChatViewHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AIChatViewHelper.class);
    
    private final PaperAnalysisProcessor paperAnalysisProcessor;
    
    /**
     * Creates a new AIChatViewHelper with dependency on PaperAnalysisProcessor.
     * 
     * @param paperAnalysisProcessor The processor for paper analysis
     */
    public AIChatViewHelper(PaperAnalysisProcessor paperAnalysisProcessor) {
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
     * Create a paper selection dialog.
     * 
     * @param papers List of available papers
     * @param selectedPaperIds List of currently selected paper IDs
     * @param mode The chat mode which affects paper selection behavior
     * @param selectionHandler Handler for when papers are selected
     * @return The configured dialog
     */
    public static Dialog createPaperSelectionDialog(List<Paper> papers, List<UUID> selectedPaperIds,
                                                  ChatMode mode, PaperSelectionHandler selectionHandler) {
        Dialog dialog = new Dialog();
        dialog.addClassName(UIConstants.CSS_PAPER_SELECTION_DIALOG);
        dialog.setMinWidth("600px"); // Ensure the dialog has a minimum width of 600px
        dialog.setHeaderTitle("Select Papers");
        
        // Add close button to the corner (matching ProjectsHelper style)
        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.getElement().getStyle().set("position", "absolute");
        closeButton.getElement().getStyle().set("right", "0");
        closeButton.getElement().getStyle().set("top", "0");
        closeButton.getElement().getStyle().set("margin", "var(--lumo-space-m)");
        closeButton.getElement().getStyle().set("cursor", "pointer");
        closeButton.addClickListener(e -> dialog.close());
        dialog.getHeader().add(closeButton);
        
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSizeFull();
        dialogLayout.setPadding(false);
        
        // Create the mode-specific selection instructions
        String modeInstructions;
        switch (mode) {
            case CHAT:
                modeInstructions = "Select exactly 1 paper for Paper Chat";
                break;
            case CROSS_REFERENCE:
                modeInstructions = "Select 2-4 papers for comparison";
                break;
            case RESEARCH_EXPLORER:
                modeInstructions = "Select 1-4 papers for research context";
                break;
            default:
                modeInstructions = "Select papers";
        }
        
        Span instructionsSpan = new Span(modeInstructions);
        instructionsSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "var(--lumo-space-m)");
        
        // Search field
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search papers by title, author, or keyword...");
        searchField.setWidthFull();
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        
        // Create validation message for selection count
        Span validationMessage = new Span();
        validationMessage.getStyle()
                .set("color", "var(--lumo-error-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-top", "var(--lumo-space-s)")
                .set("visibility", "hidden"); // Hidden by default
        
        // List of currently selected papers
        List<Paper> selectedPapers = new ArrayList<>();
        
        // Create a new custom grid without using the default selection mechanism
        Grid<Paper> customGrid = new Grid<>();
        customGrid.addClassName(UIConstants.CSS_PAPERS_GRID);
        
        // Pre-select papers based on selectedPaperIds
        for (Paper paper : papers) {
            if (selectedPaperIds.contains(paper.getId())) {
                selectedPapers.add(paper);
            }
        }
        
        // Button layout
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.addClassName(UIConstants.CSS_DIALOG_BUTTONS);
        
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());
        
        Button doneButton = new Button("Done");
        doneButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        doneButton.setEnabled(false); // Disabled until a valid selection is made
        
        // Validation function
        final Runnable validateSelection = () -> {
            int selectionCount = selectedPapers.size();
            boolean isValid = false;
            
            switch (mode) {
                case CHAT:
                    isValid = selectionCount == 1;
                    validationMessage.setText(selectionCount == 0 ? 
                            "Please select exactly 1 paper" : 
                            selectionCount > 1 ? "Paper Chat mode allows only 1 paper" : "");
                    break;
                    
                case CROSS_REFERENCE:
                    isValid = selectionCount >= 2 && selectionCount <= 4;
                    validationMessage.setText(selectionCount < 2 ? 
                            "Please select at least 2 papers" : 
                            selectionCount > 4 ? "Maximum 4 papers allowed" : "");
                    break;
                    
                case RESEARCH_EXPLORER:
                    isValid = selectionCount >= 1 && selectionCount <= 4;
                    validationMessage.setText(selectionCount == 0 ? 
                            "Please select at least 1 paper" : 
                            selectionCount > 4 ? "Maximum 4 papers allowed" : "");
                    break;
                    
                default:
                    isValid = selectionCount > 0;
                    validationMessage.setText(selectionCount == 0 ? "Please select at least 1 paper" : "");
            }
            
            // Show/hide validation message
            validationMessage.getStyle().set("visibility", isValid ? "hidden" : "visible");
            
            // Enable/disable done button
            doneButton.setEnabled(isValid);
        };
        
        // Configure columns - title and author on the left, checkbox on the right
        customGrid.addColumn(new ComponentRenderer<>(paper -> {
            VerticalLayout paperInfo = new VerticalLayout();
            paperInfo.setPadding(false);
            paperInfo.setSpacing(false);
            
            H5 title = new H5(paper.getTitle());
            title.addClassName(UIConstants.CSS_PAPER_TITLE);
            
            String authors = paper.getAuthors() != null && !paper.getAuthors().isEmpty() 
                  ? String.join(", ", paper.getAuthors())
                  : "Unknown Author";
            
            Span authorSpan = new Span(authors);
            authorSpan.addClassName(UIConstants.CSS_PAPER_AUTHORS);
            
            paperInfo.add(title, authorSpan);
            return paperInfo;
        })).setHeader("Paper").setAutoWidth(true).setFlexGrow(1);
        
        // Add checkbox column on the right
        customGrid.addColumn(new ComponentRenderer<>(paper -> {
            Checkbox checkbox = new Checkbox();
            checkbox.setValue(selectedPapers.contains(paper));
            
            checkbox.addValueChangeListener(event -> {
                if (event.getValue()) {
                    if (!selectedPapers.contains(paper)) {
                        selectedPapers.add(paper);
                    }
                } else {
                    selectedPapers.remove(paper);
                }
                
                validateSelection.run();
            });
            
            return checkbox;
        })).setHeader("Select").setWidth("120px").setFlexGrow(0)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);
        
        // Configure grid with initial items and disable the row click selection
        customGrid.setItems(papers);
        
        
        // Filter papers based on search
        searchField.addValueChangeListener(event -> {
            String filter = event.getValue().toLowerCase();
            List<Paper> filteredPapers;
            
            if (filter.isEmpty()) {
                filteredPapers = papers;
            } else {
                filteredPapers = papers.stream()
                    .filter(p -> {
                        if (p.getTitle() != null && p.getTitle().toLowerCase().contains(filter)) {
                            return true;
                        }
                        if (p.getAuthors() != null) {
                            return p.getAuthors().stream()
                                .anyMatch(author -> author.toLowerCase().contains(filter));
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            }
            
            customGrid.setItems(filteredPapers);
        });
        
        // Configure done button click handler
        doneButton.addClickListener(e -> {
            selectionHandler.onPapersSelected(selectedPapers);
            dialog.close();
        });
        
        // Run initial validation
        validateSelection.run();
        
        buttonLayout.add(cancelButton, doneButton);
        
        dialogLayout.add(instructionsSpan, searchField, customGrid, validationMessage, buttonLayout);
        dialog.add(dialogLayout);
        
        return dialog;
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
        thinkingLayout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        
        bubble.add(thinkingLayout);
        
        container.add(avatar, bubble);
        
        return container;
    }
    
    /**
     * Creates a processing message with a progress bar to indicate the AI is analyzing the paper.
     * 
     * @param label The label to display above the progress bar
     * @return The component representing the processing state with progress bar
     */
    public static com.vaadin.flow.component.Component createProgressMessage(String label) {
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
        bubble.setWidth("100%");
        
        // Create a label for the progress
        Span progressLabel = new Span(label);
        progressLabel.getStyle()
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");
        
        // Create a progress bar
        ProgressBar progressBar = new com.vaadin.flow.component.progressbar.ProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setValue(0.0);
        progressBar.setMin(0.0);
        progressBar.setMax(1.0);
        progressBar.setWidth("100%");
        progressBar.getElement().setAttribute("id", "analysis-progress-bar");
        
        // Add components to bubble
        bubble.add(progressLabel, progressBar);
        
        container.add(avatar, bubble);
        container.setWidthFull();
        
        return container;
    }
}

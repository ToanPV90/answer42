package com.samjdtechnologies.answer42.ui.views.components;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;

public class AIChatContainer extends VerticalLayout{
    /**
     * Create the chat container with messages and input.
     * 
     * @param messagesContainer The container where chat messages will be displayed
     * @param messageInput The text field for user input
     * @param sendButton The button that sends the message
     * @param sendMessage The runnable action that handles sending the message
     * @throws IllegalArgumentException if sendMessage is null
     */
    public AIChatContainer(VerticalLayout messagesContainer, 
        TextField messageInput, 
        Button sendButton, 
        Runnable sendMessage) {
            if (sendMessage == null) {
            throw new IllegalArgumentException("Send Message action cannot be null");
        }
        this.addClassName(UIConstants.CSS_CHAT_CONTAINER);
        this.setSizeFull();
        this.getStyle().set("min-height", "500px");
        this.setPadding(false);
        this.setSpacing(false);
        
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
        sendButton.addClickListener(e -> sendMessage.run());
        
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
        
        this.add(messagesContainer, tipHelp, inputContainer, shortcutHelp);
        
    }
}

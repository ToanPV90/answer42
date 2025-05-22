package com.samjdtechnologies.answer42.ui.views.components;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class AIChatGeneralMesssageBubble extends HorizontalLayout{
    /**
     * Create a message bubble for the chat UI.
     * 
     * @param isUser Whether the message is from the user
     * @param message The message content
     */
    public AIChatGeneralMesssageBubble(boolean isUser, String message) {
        this.addClassName(UIConstants.CSS_MESSAGE_CONTAINER);
        this.addClassNames(isUser ? UIConstants.CSS_USER_MESSAGE : UIConstants.CSS_ASSISTANT_MESSAGE);
        
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
            this.add(bubble, avatar);
        } else {
            this.add(avatar, bubble);
        }
        
    }
}

package com.samjdtechnologies.answer42.ui.views.components;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class AIChatThinkingMessageBubble extends HorizontalLayout{
    /**
     * Creates a "Thinking..." message with an animated spinner to indicate the AI is processing.
     */
    public AIChatThinkingMessageBubble() {
        this.addClassName(UIConstants.CSS_MESSAGE_CONTAINER);
        this.addClassName(UIConstants.CSS_ASSISTANT_MESSAGE);
        
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
        
        this.add(avatar, bubble);
        
    }
}

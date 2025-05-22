package com.samjdtechnologies.answer42.ui.views.components;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

public class AIChatProgressMessageBubble extends HorizontalLayout{
    /**
     * Creates a processing message with a progress bar to indicate the AI is analyzing the paper.
     * 
     * @param label The label to display above the progress bar
     */
    public AIChatProgressMessageBubble(String label) {
        this.addClassName(UIConstants.CSS_MESSAGE_CONTAINER);
        this.addClassName(UIConstants.CSS_ASSISTANT_MESSAGE);
        
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
        
        this.add(avatar, bubble);
        this.setWidthFull();
    }
}

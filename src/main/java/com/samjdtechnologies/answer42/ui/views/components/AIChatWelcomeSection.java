package com.samjdtechnologies.answer42.ui.views.components;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Welcome section component for the AI Chat view.
 * Displays a title, subtitle, and an "Add Papers" button.
 */
public class AIChatWelcomeSection extends VerticalLayout {
    
    /**
     * Creates a welcome section for the AI Chat view with a button that triggers the provided action.
     * 
     * @param addPapersAction The action to execute when the "Add Papers" button is clicked
     * @throws IllegalArgumentException if addPapersAction is null
     */
    public AIChatWelcomeSection(Runnable addPapersAction) {
        if (addPapersAction == null) {
            throw new IllegalArgumentException("Add papers action cannot be null");
        }
        
        // Main container
        this.addClassName(UIConstants.CSS_WELCOME_SECTION);
        this.setPadding(false);
        this.setSpacing(false);
        this.setWidthFull();
        
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
        addPapersButton.addClickListener(e -> addPapersAction.run());
        
        // Add button to its own container for alignment
        Div buttonContainer = new Div(addPapersButton);
        buttonContainer.getStyle().set("margin-top", "var(--lumo-space-m)");
        
        headerContent.add(headerLeft, buttonContainer);
        this.add(headerContent);
    }
}

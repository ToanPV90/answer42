package com.samjdtechnologies.answer42.ui.components;

import java.util.function.Consumer;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.helpers.views.AIChatViewHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class AnthropicPoweredAnalysisSection extends VerticalLayout{
    
    private final Consumer<String> triggerAnalysis;
    
    /**
     * Initialize the analysis area with buttons.
     * 
     * @param triggerAnalysis Consumer function that accepts the analysis type string to trigger
     */
    public AnthropicPoweredAnalysisSection(Consumer<String> triggerAnalysis) {
        this.triggerAnalysis = triggerAnalysis;
        this.addClassName(UIConstants.CSS_ANALYSIS_CONTAINER);
        this.setPadding(false);
        this.setSpacing(false);
        this.setVisible(false);
        
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
                () -> triggerAnalysisWithType("Deep Summary"));
        
        Button methodologyButton = AIChatViewHelper.createAnalysisButton(
                "Methodology Analysis", 
                VaadinIcon.CLIPBOARD_TEXT,
                "var(--lumo-success-color)",
                () -> triggerAnalysisWithType("Methodology Analysis"));
        
        Button resultsButton = AIChatViewHelper.createAnalysisButton(
                "Results Interpretation", 
                VaadinIcon.CHART,
                "var(--lumo-primary-color-50pct)",
                () -> triggerAnalysisWithType("Results Interpretation"));
        
        Button evaluationButton = AIChatViewHelper.createAnalysisButton(
                "Critical Evaluation", 
                VaadinIcon.SCALE,
                "var(--lumo-error-color)",
                () -> triggerAnalysisWithType("Critical Evaluation"));
        
        Button implicationsButton = AIChatViewHelper.createAnalysisButton(
                "Research Implications", 
                VaadinIcon.LIGHTBULB,
                "var(--lumo-contrast-60pct)",
                () -> triggerAnalysisWithType("Research Implications"));
        
        analysisButtons.add(
                deepSummaryButton, 
                methodologyButton, 
                resultsButton, 
                evaluationButton, 
                implicationsButton);
        
        this.add(analysisTitle, analysisInfo, analysisButtons);
    }
    
    /**
     * Triggers analysis with specific type.
     * 
     * @param type The type of analysis to perform
     */
    private void triggerAnalysisWithType(String type) {
        this.triggerAnalysis.accept(type);
    }
}

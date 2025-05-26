package com.samjdtechnologies.answer42.ui.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.daos.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.ui.helpers.components.RelatedPapersComponentHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * Dialog component for collecting user feedback on discovered papers.
 * Provides structured feedback collection for relevance and relationship accuracy.
 */
public class DiscoveryFeedbackDialog extends Dialog {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryFeedbackDialog.class);

    private final DiscoveredPaper paper;
    private final FeedbackSubmitCallback onFeedbackSubmit;
    
    private RadioButtonGroup<String> relevanceRating;
    private RadioButtonGroup<String> relationshipAccuracy;
    private TextArea commentsField;

    /**
     * Creates a new feedback dialog for the specified discovered paper.
     * 
     * @param paper The discovered paper to provide feedback for
     * @param onFeedbackSubmit Callback function to execute when feedback is submitted
     */
    public DiscoveryFeedbackDialog(DiscoveredPaper paper, FeedbackSubmitCallback onFeedbackSubmit) {
        this.paper = paper;
        this.onFeedbackSubmit = onFeedbackSubmit;
        
        initializeDialog();
        createContent();
        
        LoggingUtil.debug(LOG, "DiscoveryFeedbackDialog", "Created feedback dialog for paper: %s", 
            paper.getTitle());
    }
    
    /**
     * Initializes the dialog properties.
     */
    private void initializeDialog() {
        setHeaderTitle("Paper Relevance Feedback");
        setWidth("600px");
        setMaxWidth("90vw");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);
    }
    
    /**
     * Creates and adds the dialog content.
     */
    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);
        
        // Paper information section
        content.add(createPaperInfoSection());
        
        // Relevance rating section
        content.add(createRelevanceRatingSection());
        
        // Relationship accuracy section
        content.add(createRelationshipAccuracySection());
        
        // Additional comments section
        content.add(createCommentsSection());
        
        // Action buttons
        content.add(createActionButtons());
        
        add(content);
    }
    
    /**
     * Creates the paper information section.
     */
    private VerticalLayout createPaperInfoSection() {
        VerticalLayout infoSection = new VerticalLayout();
        infoSection.setSpacing(false);
        infoSection.setPadding(false);
        
        H4 paperTitle = new H4(paper.getTitle());
        paperTitle.getStyle().setMargin("0 0 var(--lumo-space-s) 0");
        
        Paragraph paperInfo = new Paragraph("Authors: " + RelatedPapersComponentHelper.formatAuthors(paper.getAuthors()));
        paperInfo.getStyle().setMargin("0 0 var(--lumo-space-m) 0");
        paperInfo.getStyle().setColor("var(--lumo-secondary-text-color)");
        
        // Add discovery source and relevance score
        HorizontalLayout metaInfo = new HorizontalLayout();
        metaInfo.setSpacing(true);
        
        Span sourceSpan = new Span("Source: " + RelatedPapersComponentHelper.getSourceDisplayName(paper.getDiscoverySource()));
        sourceSpan.getElement().getThemeList().add("badge");
        sourceSpan.getElement().getThemeList().add(RelatedPapersComponentHelper.getSourceThemeClass(paper.getDiscoverySource()));
        
        Span relevanceSpan = new Span("Current Relevance: " + String.format("%.2f", paper.getRelevanceScore()));
        relevanceSpan.getElement().getThemeList().add("badge");
        
        metaInfo.add(sourceSpan, relevanceSpan);
        
        infoSection.add(paperTitle, paperInfo, metaInfo);
        return infoSection;
    }
    
    /**
     * Creates the relevance rating section.
     */
    private VerticalLayout createRelevanceRatingSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);
        
        Span relevanceLabel = new Span("How relevant is this paper to your research?");
        relevanceLabel.getStyle().setFontWeight("bold");
        
        relevanceRating = new RadioButtonGroup<>();
        relevanceRating.setItems("Very Relevant", "Somewhat Relevant", "Slightly Relevant", "Not Relevant");
        relevanceRating.setValue("Somewhat Relevant"); // Default selection
        
        section.add(relevanceLabel, relevanceRating);
        return section;
    }
    
    /**
     * Creates the relationship accuracy section.
     */
    private VerticalLayout createRelationshipAccuracySection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);
        section.getStyle().setMarginTop("var(--lumo-space-m)");
        
        Span relationshipLabel = new Span("How accurate is the relationship classification?");
        relationshipLabel.getStyle().setFontWeight("bold");
        
        RelationshipType currentRelationship = RelatedPapersComponentHelper.determineRelationshipType(paper);
        Span currentRelationshipSpan = new Span("Current: " + RelatedPapersComponentHelper.getRelationshipDisplayName(currentRelationship));
        currentRelationshipSpan.getStyle().setColor("var(--lumo-secondary-text-color)");
        
        relationshipAccuracy = new RadioButtonGroup<>();
        relationshipAccuracy.setItems("Accurate", "Partially Accurate", "Inaccurate");
        relationshipAccuracy.setValue("Accurate"); // Default selection
        
        section.add(relationshipLabel, currentRelationshipSpan, relationshipAccuracy);
        return section;
    }
    
    /**
     * Creates the comments section.
     */
    private VerticalLayout createCommentsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);
        section.getStyle().setMarginTop("var(--lumo-space-m)");
        
        Span commentsLabel = new Span("Additional comments (optional):");
        commentsLabel.getStyle().setFontWeight("bold");
        
        commentsField = new TextArea();
        commentsField.setPlaceholder("Please provide any additional feedback about this discovery...");
        commentsField.setWidthFull();
        commentsField.setMaxLength(500);
        commentsField.setHelperText("Maximum 500 characters");
        
        section.add(commentsLabel, commentsField);
        return section;
    }
    
    /**
     * Creates the action buttons.
     */
    private HorizontalLayout createActionButtons() {
        Button submitButton = new Button("Submit Feedback");
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.addClickListener(e -> handleFeedbackSubmit());
        
        Button cancelButton = new Button("Cancel", e -> close());
        
        HorizontalLayout buttons = new HorizontalLayout(cancelButton, submitButton);
        buttons.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);
        buttons.getStyle().setMarginTop("var(--lumo-space-m)");
        
        return buttons;
    }
    
    /**
     * Handles feedback submission.
     */
    private void handleFeedbackSubmit() {
        try {
            FeedbackData feedback = new FeedbackData(
                relevanceRating.getValue(),
                relationshipAccuracy.getValue(),
                commentsField.getValue(),
                paper
            );
            
            LoggingUtil.info(LOG, "handleFeedbackSubmit", "Feedback submitted for paper: %s, relevance: %s", 
                paper.getTitle(), feedback.getRelevanceRating());
            
            if (onFeedbackSubmit != null) {
                onFeedbackSubmit.onSubmit(feedback);
            }
            
            // Show success message
            Notification.show("Thank you for your feedback! This helps improve our discovery algorithms.", 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            
            close();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "handleFeedbackSubmit", "Error submitting feedback", e);
            Notification.show("Error submitting feedback. Please try again.", 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    /**
     * Data class to hold feedback information.
     */
    public static class FeedbackData {
        private final String relevanceRating;
        private final String relationshipAccuracy;
        private final String comments;
        private final DiscoveredPaper paper;
        
        public FeedbackData(String relevanceRating, String relationshipAccuracy, 
                           String comments, DiscoveredPaper paper) {
            this.relevanceRating = relevanceRating;
            this.relationshipAccuracy = relationshipAccuracy;
            this.comments = comments;
            this.paper = paper;
        }
        
        public String getRelevanceRating() { return relevanceRating; }
        public String getRelationshipAccuracy() { return relationshipAccuracy; }
        public String getComments() { return comments; }
        public DiscoveredPaper getPaper() { return paper; }
        
        /**
         * Converts relevance rating to a numeric score.
         * @return Score between 0.0 and 1.0
         */
        public double getRelevanceScore() {
            switch (relevanceRating) {
                case "Very Relevant": return 1.0;
                case "Somewhat Relevant": return 0.7;
                case "Slightly Relevant": return 0.4;
                case "Not Relevant": return 0.1;
                default: return 0.5;
            }
        }
        
        /**
         * Converts relationship accuracy to a numeric score.
         * @return Score between 0.0 and 1.0
         */
        public double getAccuracyScore() {
            switch (relationshipAccuracy) {
                case "Accurate": return 1.0;
                case "Partially Accurate": return 0.6;
                case "Inaccurate": return 0.2;
                default: return 0.5;
            }
        }
        
        @Override
        public String toString() {
            return "FeedbackData{" +
                    "relevanceRating='" + relevanceRating + '\'' +
                    ", relationshipAccuracy='" + relationshipAccuracy + '\'' +
                    ", comments='" + comments + '\'' +
                    ", paperTitle='" + (paper != null ? paper.getTitle() : "null") + '\'' +
                    '}';
        }
    }
    
    /**
     * Functional interface for feedback submission callbacks.
     */
    @FunctionalInterface
    public interface FeedbackSubmitCallback {
        void onSubmit(FeedbackData feedback);
    }
}

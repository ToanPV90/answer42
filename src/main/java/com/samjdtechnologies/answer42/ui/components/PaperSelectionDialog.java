package com.samjdtechnologies.answer42.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.views.helpers.AIChatViewHelper.PaperSelectionHandler;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

public class PaperSelectionDialog extends Dialog{
    /**
     * Create a paper selection dialog.
     * 
     * @param papers List of available papers
     * @param selectedPaperIds List of currently selected paper IDs
     * @param mode The chat mode which affects paper selection behavior
     * @param selectionHandler Handler for when papers are selected
     */
    public PaperSelectionDialog(List<Paper> papers, List<UUID> selectedPaperIds,
                                                  ChatMode mode, PaperSelectionHandler selectionHandler) {
        this.addClassName(UIConstants.CSS_PAPER_SELECTION_DIALOG);
        this.setMinWidth("600px"); // Ensure the this has a minimum width of 600px
        this.setHeaderTitle("Select Papers");
        
        // Add close button to the corner (matching ProjectsHelper style)
        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.getElement().getStyle().set("position", "absolute");
        closeButton.getElement().getStyle().set("right", "0");
        closeButton.getElement().getStyle().set("top", "0");
        closeButton.getElement().getStyle().set("margin", "var(--lumo-space-m)");
        closeButton.getElement().getStyle().set("cursor", "pointer");
        closeButton.addClickListener(e -> this.close());
        this.getHeader().add(closeButton);
        
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
        cancelButton.addClickListener(e -> this.close());
        
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
            this.close();
        });
        
        // Run initial validation
        validateSelection.run();
        
        buttonLayout.add(cancelButton, doneButton);
        
        dialogLayout.add(instructionsSpan, searchField, customGrid, validationMessage, buttonLayout);
        this.add(dialogLayout);
        
    }
}

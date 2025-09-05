package com.samjdtechnologies.answer42.ui.components;

import java.util.UUID;
import java.util.function.Consumer;

import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

public class PaperPill extends Div{

    private final Consumer<UUID> removePaper;

    /**
     * Create a pill button for a selected paper.
     * 
     * @param paper The paper to display in the pill
     * @param removePaper The callback to invoke when removing the paper
     */
    public PaperPill(Paper paper, Consumer<UUID> removePaper) {
        this.removePaper = removePaper;
        this.addClassName(UIConstants.CSS_PAPER_PILL);
        
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
        removeIcon.addClickListener(e -> removePaperWithPaperId(paper.getId()));
        removeIcon.getElement().setAttribute("title", "Remove paper");
        removeIcon.getStyle().set("cursor", "pointer");
        
        this.add(titleSpan, removeIcon);
    }

    /**
     * Removes paper.
     * 
     * @param paperId The ID of the paper to remove
     */
    private void removePaperWithPaperId(UUID paperId) {
        this.removePaper.accept(paperId);
    }
}

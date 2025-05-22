package com.samjdtechnologies.answer42.ui.views.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Component representing a single author entry with name, affiliation and email fields.
 * Used in the UploadPaperView to allow adding/removing paper authors.
 */
public class AuthorContact extends HorizontalLayout {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorContact.class);
    
    private final TextField nameField;
    private final TextField affiliationField;
    private final TextField emailField;
    private final Button removeButton;
    private final VerticalLayout parent;
    private final Runnable formValidator;
    
    /**
     * Create a new author entry with fields for name, affiliation, and email.
     * 
     * @param parent The parent layout that contains this author entry
     * @param formValidator Callback to validate the form when this entry changes
     */
    public AuthorContact(VerticalLayout parent, Runnable formValidator) {
        this.parent = parent;
        this.formValidator = formValidator;
        
        LoggingUtil.debug(LOG, "constructor", "Creating new author entry");
        
        nameField = new TextField();
        nameField.setPlaceholder("Author name");
        nameField.setRequired(true);
        nameField.setValueChangeMode(ValueChangeMode.EAGER);
        nameField.addValueChangeListener(e -> {
            LoggingUtil.debug(LOG, "nameField.valueChange", "Name field changed: %s", e.getValue());
            if (formValidator != null) {
                formValidator.run();
            }
        });
        
        affiliationField = new TextField();
        affiliationField.setPlaceholder("University/Organization");
        
        emailField = new TextField();
        emailField.setPlaceholder("author@example.com");
        
        removeButton = new Button(new Icon(VaadinIcon.TRASH));
        removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        removeButton.addClickListener(e -> removeAuthor());
        removeButton.getElement().setAttribute("title", "Remove author");
        removeButton.setVisible(false); // Hide for first author
        
        setSpacing(true);
        setPadding(false);
        setWidthFull();
        
        add(nameField, affiliationField, emailField, removeButton);
        
        // Set flex grow to allocate space proportionally
        setFlexGrow(3, nameField);
        setFlexGrow(3, affiliationField);
        setFlexGrow(3, emailField);
        setFlexGrow(0, removeButton);
        
        addClassName(UIConstants.CSS_AUTHOR_ENTRY);
        
        // If this is not the first author entry, show the remove button
        if (parent.getComponentCount() > 0) {
            removeButton.setVisible(true);
        }
    }
    
    /**
     * Remove this author entry from its parent layout.
     */
    private void removeAuthor() {
        LoggingUtil.debug(LOG, "removeAuthor", "Removing author entry");
        parent.remove(this);
        if (formValidator != null) {
            formValidator.run();
        }
    }
    
    /**
     * Get the author name field.
     * 
     * @return The name TextField
     */
    public TextField getNameField() {
        return nameField;
    }
    
    /**
     * Get the author affiliation field.
     * 
     * @return The affiliation TextField
     */
    public TextField getAffiliationField() {
        return affiliationField;
    }
    
    /**
     * Get the author email field.
     * 
     * @return The email TextField
     */
    public TextField getEmailField() {
        return emailField;
    }
}

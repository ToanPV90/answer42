package com.samjdtechnologies.answer42.ui.layout;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.vaadin.flow.component.Component;

import com.vaadin.flow.component.html.Div;

/**
 * Main layout configuration class.
 * Configures CSS imports for the application.
 */
public class MainLayout extends Div {

    public MainLayout() {
        setSizeFull();
        addClassName(UIConstants.CSS_MAIN_VIEW);
    }

    public void add(Component... components) {
        super.add(components);
    }
}

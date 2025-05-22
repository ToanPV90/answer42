package com.samjdtechnologies.answer42.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.samjdtechnologies.answer42.ui.views.helpers.AIChatMessageProcessor;
import com.samjdtechnologies.answer42.ui.views.helpers.PaperAnalysisProcessor;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.spring.annotation.SpringComponent;

/**
 * Configuration class for Vaadin session initialization.
 * This class is responsible for registering helper components in the Vaadin session.
 * It is separate from ThreadConfig to avoid circular dependencies.
 */
@SpringComponent
@Configuration
public class VaadinSessionConfig implements VaadinServiceInitListener {
    
    @Autowired
    private AIChatMessageProcessor aIChatMessageProcessor;
    
    @Autowired
    private PaperAnalysisProcessor paperAnalysisProcessor;
    
    /**
     * This method is called when Vaadin service is initialized.
     * It stores the helper classes in the session to make them accessible to the UI.
     */
    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInit -> {
            // Register [] in the Vaadin session
            sessionInit.getSession().setAttribute(AIChatMessageProcessor.class, aIChatMessageProcessor);
            sessionInit.getSession().setAttribute(PaperAnalysisProcessor.class, paperAnalysisProcessor);
        });
    }
}

package com.samjdtechnologies.answer42.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.samjdtechnologies.answer42.processors.AIChatMessageProcessor;
import com.samjdtechnologies.answer42.processors.AnthropicPaperAnalysisProcessor;
import com.samjdtechnologies.answer42.util.LoggingUtil;
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
    
    private static final Logger LOG = LoggerFactory.getLogger(VaadinSessionConfig.class);
    
    @Autowired(required = false)
    private AIChatMessageProcessor aIChatMessageProcessor;
    
    @Autowired(required = false)
    private AnthropicPaperAnalysisProcessor paperAnalysisProcessor;
    
    /**
     * This method is called when Vaadin service is initialized.
     * It stores the helper classes in the session to make them accessible to the UI.
     * Uses defensive programming to handle potential initialization timing issues.
     */
    @Override
    public void serviceInit(ServiceInitEvent event) {
        try {
            LoggingUtil.debug(LOG, "serviceInit", "Initializing Vaadin service with processors");
            
            event.getSource().addSessionInitListener(sessionInit -> {
                try {
                    // Register processors in the Vaadin session with null checks
                    if (aIChatMessageProcessor != null) {
                        sessionInit.getSession().setAttribute(AIChatMessageProcessor.class, aIChatMessageProcessor);
                        LoggingUtil.debug(LOG, "serviceInit", "Registered AIChatMessageProcessor in session");
                    } else {
                        LoggingUtil.warn(LOG, "serviceInit", "AIChatMessageProcessor not available during session init");
                    }
                    
                    if (paperAnalysisProcessor != null) {
                        sessionInit.getSession().setAttribute(AnthropicPaperAnalysisProcessor.class, paperAnalysisProcessor);
                        LoggingUtil.debug(LOG, "serviceInit", "Registered AnthropicPaperAnalysisProcessor in session");
                    } else {
                        LoggingUtil.warn(LOG, "serviceInit", "AnthropicPaperAnalysisProcessor not available during session init");
                    }
                    
                } catch (Exception e) {
                    LoggingUtil.error(LOG, "serviceInit", "Error during session initialization", e);
                    // Don't rethrow - allow Vaadin to continue initializing
                }
            });
            
            LoggingUtil.info(LOG, "serviceInit", "Vaadin service initialization completed successfully");
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "serviceInit", "Error during Vaadin service initialization", e);
            // Don't rethrow - allow Vaadin to continue initializing
        }
    }
}

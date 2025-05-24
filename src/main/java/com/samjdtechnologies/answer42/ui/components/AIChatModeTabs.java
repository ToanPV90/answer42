package com.samjdtechnologies.answer42.ui.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.views.helpers.AIChatViewHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

/**
 * Component for the AI Chat mode selection tabs.
 * Displays three tabs for different AI chat modes: Paper Chat, Compare Papers, and Research Explorer.
 */
public class AIChatModeTabs extends Tabs {
    private static final Logger LOG = LoggerFactory.getLogger(AIChatModeTabs.class);
    
    private final Tab paperChatTab = new Tab();
    private final Tab comparePapersTab = new Tab();
    private final Tab researchExplorerTab = new Tab();
    
    /**
     * Interface for chat mode change callbacks.
     */
    public interface ChatModeChangeListener {
        /**
         * Called when the chat mode is changed.
         * 
         * @param mode The new chat mode
         * @param provider The new AI provider
         */
        void onChatModeChanged(ChatMode mode, AIProvider provider);
    }
    
    /**
     * Creates a new AIChatModeTabs component with a callback for mode changes.
     * 
     * @param listener The listener to call when the mode changes
     * @throws IllegalArgumentException if listener is null
     */
    public AIChatModeTabs(ChatModeChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Chat mode change listener cannot be null");
        }
        
        addClassName(UIConstants.CSS_CHAT_TABS);
        
        // Paper Chat Tab - use HorizontalLayout to place icon next to label
        HorizontalLayout paperChatContent = new HorizontalLayout();
        paperChatContent.setPadding(false);
        paperChatContent.setSpacing(true);
        paperChatContent.setAlignItems(FlexComponent.Alignment.CENTER);
        
        Component paperChatIcon = AIChatViewHelper.createPaperChatIcon();
        paperChatIcon.getElement().getStyle().set("margin-left", "4px");
        Span paperChatLabel = new Span("Paper Chat");
        
        paperChatContent.add(paperChatLabel, paperChatIcon);
        paperChatTab.add(paperChatContent);
        
        // Compare Papers Tab - use HorizontalLayout to place icon next to label
        HorizontalLayout comparePapersContent = new HorizontalLayout();
        comparePapersContent.setPadding(false);
        comparePapersContent.setSpacing(true);
        comparePapersContent.setAlignItems(FlexComponent.Alignment.CENTER);
        
        Component openAIIcon = AIChatViewHelper.createOpenAIIcon();
        openAIIcon.getElement().getStyle().set("margin-left", "4px");
        Span comparePapersLabel = new Span("Compare Papers");
        
        comparePapersContent.add(comparePapersLabel, openAIIcon);
        comparePapersTab.add(comparePapersContent);
        
        // Research Explorer Tab - use HorizontalLayout to place icon next to label
        HorizontalLayout researchExplorerContent = new HorizontalLayout();
        researchExplorerContent.setPadding(false);
        researchExplorerContent.setSpacing(true);
        researchExplorerContent.setAlignItems(FlexComponent.Alignment.CENTER);
        
        Component perplexityIcon = AIChatViewHelper.createPerplexityIcon();
        perplexityIcon.getElement().getStyle().set("margin-left", "4px");
        Span researchExplorerLabel = new Span("Research Explorer");
        
        researchExplorerContent.add(researchExplorerLabel, perplexityIcon);
        researchExplorerTab.add(researchExplorerContent);
        
        // Add tabs and set up change listener
        add(paperChatTab, comparePapersTab, researchExplorerTab);
        
        // Handle tab selection changes
        addSelectedChangeListener(e -> {
            Tab selectedTab = getSelectedTab();
            
            if (selectedTab == paperChatTab) {
                LoggingUtil.debug(LOG, "onSelectedChange", "Switching to Paper Chat mode");
                listener.onChatModeChanged(ChatMode.CHAT, AIProvider.ANTHROPIC);
            } else if (selectedTab == comparePapersTab) {
                LoggingUtil.debug(LOG, "onSelectedChange", "Switching to Compare Papers mode");
                listener.onChatModeChanged(ChatMode.CROSS_REFERENCE, AIProvider.OPENAI);
            } else if (selectedTab == researchExplorerTab) {
                LoggingUtil.debug(LOG, "onSelectedChange", "Switching to Research Explorer mode");
                listener.onChatModeChanged(ChatMode.RESEARCH_EXPLORER, AIProvider.PERPLEXITY);
            }
        });
        
        // Select Paper Chat tab by default
        setSelectedTab(paperChatTab);
    }
    
    /**
     * Get the paper chat tab.
     * 
     * @return The paper chat tab
     */
    public Tab getPaperChatTab() {
        return paperChatTab;
    }
    
    /**
     * Get the compare papers tab.
     * 
     * @return The compare papers tab
     */
    public Tab getComparePapersTab() {
        return comparePapersTab;
    }
    
    /**
     * Get the research explorer tab.
     * 
     * @return The research explorer tab
     */
    public Tab getResearchExplorerTab() {
        return researchExplorerTab;
    }
}

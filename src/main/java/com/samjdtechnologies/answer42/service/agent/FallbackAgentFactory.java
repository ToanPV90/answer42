package com.samjdtechnologies.answer42.service.agent;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.samjdtechnologies.answer42.model.enums.AgentType;
import com.samjdtechnologies.answer42.model.interfaces.AIAgent;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for managing fallback agents in the Ollama local fallback system.
 * Provides access to locally-running Ollama-based agents when cloud providers fail.
 * Only active when Ollama is enabled in the configuration.
 * 
 * @since Phase 3: Retry Policy Integration
 */
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class FallbackAgentFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(FallbackAgentFactory.class);
    
    private final Map<AgentType, AIAgent> fallbackAgents;
    
    /**
     * Constructor that initializes the fallback agents map with available Ollama-based agents.
     * Agents are injected as optional dependencies since they're conditionally loaded.
     */
    public FallbackAgentFactory(
            @Autowired(required = false) ContentSummarizerFallbackAgent contentSummarizer,
            @Autowired(required = false) ConceptExplainerFallbackAgent conceptExplainer,
            @Autowired(required = false) MetadataEnhancementFallbackAgent metadataEnhancer,
            @Autowired(required = false) PaperProcessorFallbackAgent paperProcessor,
            @Autowired(required = false) QualityCheckerFallbackAgent qualityChecker,
            @Autowired(required = false) CitationFormatterFallbackAgent citationFormatter) {
        
        this.fallbackAgents = new HashMap<>();
        
        // Register available fallback agents
        if (contentSummarizer != null) {
            fallbackAgents.put(AgentType.CONTENT_SUMMARIZER, contentSummarizer);
            LoggingUtil.info(LOG, "constructor", "Registered ContentSummarizerFallbackAgent");
        }
        
        if (conceptExplainer != null) {
            fallbackAgents.put(AgentType.CONCEPT_EXPLAINER, conceptExplainer);
            LoggingUtil.info(LOG, "constructor", "Registered ConceptExplainerFallbackAgent");
        }
        
        if (metadataEnhancer != null) {
            fallbackAgents.put(AgentType.METADATA_ENHANCER, metadataEnhancer);
            LoggingUtil.info(LOG, "constructor", "Registered MetadataEnhancementFallbackAgent");
        }
        
        if (paperProcessor != null) {
            fallbackAgents.put(AgentType.PAPER_PROCESSOR, paperProcessor);
            LoggingUtil.info(LOG, "constructor", "Registered PaperProcessorFallbackAgent");
        }
        
        if (qualityChecker != null) {
            fallbackAgents.put(AgentType.QUALITY_CHECKER, qualityChecker);
            LoggingUtil.info(LOG, "constructor", "Registered QualityCheckerFallbackAgent");
        }
        
        if (citationFormatter != null) {
            fallbackAgents.put(AgentType.CITATION_FORMATTER, citationFormatter);
            LoggingUtil.info(LOG, "constructor", "Registered CitationFormatterFallbackAgent");
        }
        
        LoggingUtil.info(LOG, "constructor", 
            "FallbackAgentFactory initialized with %d fallback agents", fallbackAgents.size());
    }
    
    /**
     * Get the fallback agent for a specific agent type.
     * 
     * @param agentType The type of agent to get fallback for
     * @return The fallback agent, or null if no fallback is available
     */
    public AIAgent getFallbackAgent(AgentType agentType) {
        AIAgent fallbackAgent = fallbackAgents.get(agentType);
        
        if (fallbackAgent != null) {
            LoggingUtil.debug(LOG, "getFallbackAgent", 
                "Found fallback agent for %s: %s", agentType, fallbackAgent.getClass().getSimpleName());
        } else {
            LoggingUtil.debug(LOG, "getFallbackAgent", 
                "No fallback agent available for %s", agentType);
        }
        
        return fallbackAgent;
    }
    
    /**
     * Check if a fallback agent is available for the specified agent type.
     * 
     * @param agentType The type of agent to check
     * @return true if a fallback is available, false otherwise
     */
    public boolean hasFallbackFor(AgentType agentType) {
        boolean hasFallback = fallbackAgents.containsKey(agentType);
        
        LoggingUtil.debug(LOG, "hasFallbackFor", 
            "Fallback availability for %s: %s", agentType, hasFallback);
        
        return hasFallback;
    }
    
    /**
     * Get all available fallback agent types.
     * 
     * @return Set of agent types that have fallback implementations
     */
    public java.util.Set<AgentType> getAvailableFallbackTypes() {
        return fallbackAgents.keySet();
    }
    
    /**
     * Get the total number of registered fallback agents.
     * 
     * @return Number of available fallback agents
     */
    public int getFallbackCount() {
        return fallbackAgents.size();
    }
    
    /**
     * Get information about the fallback system for monitoring purposes.
     * 
     * @return String describing the fallback system status
     */
    public String getFallbackSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Ollama Fallback System: ");
        info.append(fallbackAgents.size()).append(" agents available [");
        
        boolean first = true;
        for (AgentType type : fallbackAgents.keySet()) {
            if (!first) {
                info.append(", ");
            }
            info.append(type.name());
            first = false;
        }
        
        info.append("]");
        return info.toString();
    }
}

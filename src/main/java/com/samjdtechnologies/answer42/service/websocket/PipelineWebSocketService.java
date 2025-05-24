package com.samjdtechnologies.answer42.service.websocket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.model.enums.StageType;
import com.samjdtechnologies.answer42.model.events.AgentTaskEvent;
import com.samjdtechnologies.answer42.model.pipeline.PipelineProgressUpdate;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;

/**
 * WebSocket-like service for real-time pipeline progress updates using Vaadin Push.
 * Provides real-time communication between backend pipeline processing and frontend UI.
 */
@Service
public class PipelineWebSocketService {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineWebSocketService.class);
    
    // Map of paper ID to list of UI instances listening for updates
    private final Map<UUID, Map<UI, Consumer<PipelineProgressUpdate>>> subscribers = new ConcurrentHashMap<>();
    
    // Map of user ID to their active UI sessions
    private final Map<UUID, Map<UI, VaadinSession>> userSessions = new ConcurrentHashMap<>();
    
    // Map agent types to pipeline stages
    private final Map<String, StageType> agentToStageMap = Map.of(
        "paper-processor", StageType.TEXT_EXTRACTION,
        "metadata-enhancer", StageType.METADATA_ENHANCEMENT,
        "content-summarizer", StageType.CONTENT_ANALYSIS,
        "concept-explainer", StageType.CONCEPT_EXTRACTION,
        "quality-checker", StageType.QUALITY_CHECK,
        "citation-formatter", StageType.CITATION_PROCESSING
    );
    
    /**
     * Register a user session with their UI and Vaadin session.
     */
    public void registerUserSession(UUID userId, UI ui, VaadinSession session) {
        userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                   .put(ui, session);
        
        LoggingUtil.debug(LOG, "registerUserSession", 
            "Registered UI %s for user %s", ui.getUIId(), userId);
    }
    
    /**
     * Unregister a user session.
     */
    public void unregisterUserSession(UUID userId, UI ui) {
        Map<UI, VaadinSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(ui);
            
            // Clean up empty maps
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        
        LoggingUtil.debug(LOG, "unregisterUserSession", 
            "Unregistered UI %s for user %s", ui.getUIId(), userId);
    }
    
    /**
     * Get all active UI sessions for a user.
     */
    public Map<UI, VaadinSession> getUserSessions(UUID userId) {
        return userSessions.getOrDefault(userId, new ConcurrentHashMap<>());
    }
    
    /**
     * Check if a user has any active sessions.
     */
    public boolean hasActiveSessions(UUID userId) {
        Map<UI, VaadinSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
    
    /**
     * Broadcast a message to all UI sessions for a specific user.
     */
    public void broadcastToUser(UUID userId, Consumer<UI> messageHandler) {
        Map<UI, VaadinSession> sessions = userSessions.get(userId);
        
        if (sessions == null || sessions.isEmpty()) {
            LoggingUtil.debug(LOG, "broadcastToUser", 
                "No active sessions for user %s", userId);
            return;
        }
        
        LoggingUtil.debug(LOG, "broadcastToUser", 
            "Broadcasting to %d sessions for user %s", sessions.size(), userId);
        
        // Send message to all user's UI sessions
        sessions.entrySet().removeIf(entry -> {
            UI ui = entry.getKey();
            VaadinSession session = entry.getValue();
            
            try {
                // Check if session is still valid
                if (session == null || !session.hasLock()) {
                    LoggingUtil.debug(LOG, "broadcastToUser", 
                        "Removing invalid session for UI %s", ui.getUIId());
                    return true; // Remove this session
                }
                
                // Use Vaadin's UI.access for thread-safe UI updates
                ui.access(() -> {
                    try {
                        messageHandler.accept(ui);
                    } catch (Exception e) {
                        LoggingUtil.error(LOG, "broadcastToUser", 
                            "Error in message handler for UI %s", e, ui.getUIId());
                    }
                });
                return false; // Keep this session
                
            } catch (Exception e) {
                LoggingUtil.warn(LOG, "broadcastToUser", 
                    "Failed to send message to UI %s, removing session: %s", 
                    ui.getUIId(), e.getMessage());
                return true; // Remove this session
            }
        });
    }
    
    /**
     * Subscribe a UI to pipeline progress updates for a specific paper.
     */
    public void subscribeToPipelineUpdates(UUID paperId, UI ui, Consumer<PipelineProgressUpdate> updateHandler) {
        subscribers.computeIfAbsent(paperId, k -> new ConcurrentHashMap<>())
                  .put(ui, updateHandler);
        
        LoggingUtil.debug(LOG, "subscribeToPipelineUpdates", 
            "UI %s subscribed to pipeline updates for paper %s", ui.getUIId(), paperId);
    }
    
    /**
     * Subscribe a UI to pipeline progress updates and register the user session.
     */
    public void subscribeToPipelineUpdates(UUID paperId, UUID userId, UI ui, VaadinSession session, 
                                         Consumer<PipelineProgressUpdate> updateHandler) {
        // Register user session
        registerUserSession(userId, ui, session);
        
        // Subscribe to pipeline updates
        subscribeToPipelineUpdates(paperId, ui, updateHandler);
        
        LoggingUtil.debug(LOG, "subscribeToPipelineUpdates", 
            "UI %s subscribed to pipeline updates for paper %s and registered session for user %s", 
            ui.getUIId(), paperId, userId);
    }
    
    /**
     * Unsubscribe a UI from pipeline progress updates.
     */
    public void unsubscribeFromPipelineUpdates(UUID paperId, UI ui) {
        Map<UI, Consumer<PipelineProgressUpdate>> paperSubscribers = subscribers.get(paperId);
        if (paperSubscribers != null) {
            paperSubscribers.remove(ui);
            
            // Clean up empty maps
            if (paperSubscribers.isEmpty()) {
                subscribers.remove(paperId);
            }
        }
        
        LoggingUtil.debug(LOG, "unsubscribeFromPipelineUpdates", 
            "UI %s unsubscribed from pipeline updates for paper %s", ui.getUIId(), paperId);
    }
    
    /**
     * Unsubscribe a UI from pipeline progress updates and unregister user session.
     */
    public void unsubscribeFromPipelineUpdates(UUID paperId, UUID userId, UI ui) {
        // Unsubscribe from pipeline updates
        unsubscribeFromPipelineUpdates(paperId, ui);
        
        // Unregister user session
        unregisterUserSession(userId, ui);
        
        LoggingUtil.debug(LOG, "unsubscribeFromPipelineUpdates", 
            "UI %s unsubscribed from pipeline updates for paper %s and unregistered session for user %s", 
            ui.getUIId(), paperId, userId);
    }
    
    /**
     * Broadcast a pipeline progress update to all subscribed UIs.
     */
    public void broadcastPipelineUpdate(UUID paperId, PipelineProgressUpdate progressUpdate) {
        Map<UI, Consumer<PipelineProgressUpdate>> paperSubscribers = subscribers.get(paperId);
        
        if (paperSubscribers == null || paperSubscribers.isEmpty()) {
            LoggingUtil.debug(LOG, "broadcastPipelineUpdate", 
                "No subscribers for paper %s pipeline updates", paperId);
            return;
        }
        
        LoggingUtil.debug(LOG, "broadcastPipelineUpdate", 
            "Broadcasting pipeline update for paper %s to %d subscribers", 
            paperId, paperSubscribers.size());
        
        // Send updates to all subscribed UIs
        paperSubscribers.entrySet().removeIf(entry -> {
            UI ui = entry.getKey();
            Consumer<PipelineProgressUpdate> handler = entry.getValue();
            
            try {
                // Use Vaadin's UI.access for thread-safe UI updates
                ui.access(() -> {
                    try {
                        handler.accept(progressUpdate);
                    } catch (Exception e) {
                        LoggingUtil.error(LOG, "broadcastPipelineUpdate", 
                            "Error in progress update handler for UI %s", e, ui.getUIId());
                    }
                });
                return false; // Keep this subscriber
                
            } catch (Exception e) {
                LoggingUtil.warn(LOG, "broadcastPipelineUpdate", 
                    "Failed to send update to UI %s, removing subscriber: %s", 
                    ui.getUIId(), e.getMessage());
                return true; // Remove this subscriber
            }
        });
    }
    
    /**
     * Broadcast a pipeline progress update to a specific user's sessions only.
     */
    public void broadcastPipelineUpdateToUser(UUID userId, UUID paperId, PipelineProgressUpdate progressUpdate) {
        Map<UI, VaadinSession> userUIs = userSessions.get(userId);
        
        if (userUIs == null || userUIs.isEmpty()) {
            LoggingUtil.debug(LOG, "broadcastPipelineUpdateToUser", 
                "No active sessions for user %s", userId);
            return;
        }
        
        Map<UI, Consumer<PipelineProgressUpdate>> paperSubscribers = subscribers.get(paperId);
        if (paperSubscribers == null || paperSubscribers.isEmpty()) {
            LoggingUtil.debug(LOG, "broadcastPipelineUpdateToUser", 
                "No subscribers for paper %s", paperId);
            return;
        }
        
        LoggingUtil.debug(LOG, "broadcastPipelineUpdateToUser", 
            "Broadcasting pipeline update for paper %s to user %s (%d sessions)", 
            paperId, userId, userUIs.size());
        
        // Send updates only to this user's UIs that are subscribed to this paper
        userUIs.keySet().forEach(ui -> {
            Consumer<PipelineProgressUpdate> handler = paperSubscribers.get(ui);
            if (handler != null) {
                try {
                    ui.access(() -> {
                        try {
                            handler.accept(progressUpdate);
                        } catch (Exception e) {
                            LoggingUtil.error(LOG, "broadcastPipelineUpdateToUser", 
                                "Error in progress update handler for UI %s", e, ui.getUIId());
                        }
                    });
                } catch (Exception e) {
                    LoggingUtil.warn(LOG, "broadcastPipelineUpdateToUser", 
                        "Failed to send update to UI %s: %s", ui.getUIId(), e.getMessage());
                }
            }
        });
    }
    
    /**
     * Handle agent task events and convert them to pipeline progress updates.
     */
    @EventListener
    public void handleAgentTaskEvent(AgentTaskEvent event) {
        try {
            UUID paperId = extractPaperIdFromEvent(event);
            if (paperId != null) {
                PipelineProgressUpdate update = convertEventToProgressUpdate(event, paperId);
                broadcastPipelineUpdate(paperId, update);
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "handleAgentTaskEvent", 
                "Failed to handle agent task event", e);
        }
    }
    
    /**
     * Extract paper ID from agent task event.
     */
    private UUID extractPaperIdFromEvent(AgentTaskEvent event) {
        try {
            String referenceId = event.getTask().getInput().get("paperId").asText();
            return UUID.fromString(referenceId);
        } catch (Exception e) {
            LoggingUtil.warn(LOG, "extractPaperIdFromEvent", 
                "Could not extract paper ID from event: %s", e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert agent task event to pipeline progress update.
     */
    private PipelineProgressUpdate convertEventToProgressUpdate(AgentTaskEvent event, UUID paperId) {
        String agentId = event.getTask().getAgentId();
        StageType stage = agentToStageMap.getOrDefault(agentId, StageType.TEXT_EXTRACTION);
        
        switch (event.getEventType()) {
            case TASK_STARTED:
                return PipelineProgressUpdate.forStage(paperId, stage, 0.0);
            case TASK_COMPLETED:
                return PipelineProgressUpdate.forStage(paperId, stage, 100.0);
            case TASK_FAILED:
                String errorMessage = event.getTask().getError();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "Agent " + agentId + " failed";
                }
                return PipelineProgressUpdate.failed(paperId, errorMessage);
            default:
                return PipelineProgressUpdate.forStage(paperId, stage, 50.0);
        }
    }
}

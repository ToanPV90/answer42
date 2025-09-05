package com.samjdtechnologies.answer42.service.helpers;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.db.ChatSession;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.repository.ChatSessionRepository;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Helper class for chat session operations.
 * Isolates logic for session management to keep the main service under 300 lines.
 */
@Component
public class ChatSessionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChatSessionHelper.class);
    
    private final ChatSessionRepository sessionRepository;
    private final PaperService paperService;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param sessionRepository repository for chat sessions
     * @param paperService service for paper access
     */
    public ChatSessionHelper(ChatSessionRepository sessionRepository, PaperService paperService) {
        this.sessionRepository = sessionRepository;
        this.paperService = paperService;
    }
    
    /**
     * Create a new chat session.
     * 
     * @param user the user who owns the session
     * @param mode the chat mode
     * @param provider the AI provider
     * @return the created chat session
     */
    @Transactional
    public ChatSession createChatSession(User user, ChatMode mode, AIProvider provider) {
        LoggingUtil.info(LOG, "createChatSession", "Creating chat session for user %s with mode %s and provider %s", 
                user.getUsername(), mode, provider);
        
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setMode(mode.toString());
        session.setProvider(provider.toString());
        session.setCreatedAt(ZonedDateTime.now());
        session.setUpdatedAt(ZonedDateTime.now());
        session.setContext(new HashMap<>());
        
        return sessionRepository.save(session);
    }
    
    /**
     * Add a paper to a chat session.
     * 
     * @param sessionId the session ID
     * @param paperId the paper ID
     * @return the updated session
     */
    @Transactional
    public ChatSession addPaperToSession(UUID sessionId, UUID paperId) {
        LoggingUtil.info(LOG, "addPaperToSession", "Adding paper %s to session %s", paperId, sessionId);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
        }
        
        // Get or create paper IDs list
        List<UUID> paperIds;
        if (context.containsKey("paperIds")) {
            try {
                Object paperIdsObj = context.get("paperIds");
                if (paperIdsObj instanceof List) {
                    paperIds = convertToPaperIdsList((List<?>) paperIdsObj);
                } else {
                    LoggingUtil.warn(LOG, "addPaperToSession", 
                            "Expected List for paperIds but got %s", 
                            paperIdsObj != null ? paperIdsObj.getClass().getName() : "null");
                    paperIds = new ArrayList<>();
                }
            } catch (Exception e) {
                LoggingUtil.error(LOG, "addPaperToSession", 
                        "Error converting paperIds: %s", e, e.getMessage());
                paperIds = new ArrayList<>();
            }
        } else {
            paperIds = new ArrayList<>();
        }
        
        // Add paper ID if not already present
        if (!paperIds.contains(paperId)) {
            paperIds.add(paperId);
        }
        
        // Update context
        context.put("paperIds", paperIds);
        session.setContext(context);
        
        return sessionRepository.save(session);
    }
    
    /**
     * Remove a paper from a chat session.
     * 
     * @param sessionId the session ID
     * @param paperId the paper ID
     * @return the updated session
     */
    @Transactional
    public ChatSession removePaperFromSession(UUID sessionId, UUID paperId) {
        LoggingUtil.info(LOG, "removePaperFromSession", "Removing paper %s from session %s", paperId, sessionId);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        Map<String, Object> context = session.getContext();
        if (context == null || !context.containsKey("paperIds")) {
            return session;
        }
        
        // Get paper IDs list
        List<UUID> paperIds;
        try {
            Object paperIdsObj = context.get("paperIds");
            if (paperIdsObj instanceof List) {
                paperIds = convertToPaperIdsList((List<?>) paperIdsObj);
            } else {
                return session;
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "removePaperFromSession", 
                    "Error converting paperIds: %s", e, e.getMessage());
            return session;
        }
        
        // Remove paper ID
        paperIds.remove(paperId);
        
        // Update context
        context.put("paperIds", paperIds);
        session.setContext(context);
        
        return sessionRepository.save(session);
    }
    
    /**
     * Get the paper IDs from a chat session.
     * 
     * @param session the chat session
     * @return list of paper IDs
     */
    public List<UUID> getPaperIdsFromSession(ChatSession session) {
        if (session == null || session.getContext() == null) {
            return new ArrayList<>();
        }
        
        Map<String, Object> context = session.getContext();
        if (!context.containsKey("paperIds")) {
            return new ArrayList<>();
        }
        
        try {
            Object paperIdsObj = context.get("paperIds");
            if (paperIdsObj instanceof List) {
                return convertToPaperIdsList((List<?>) paperIdsObj);
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getPaperIdsFromSession", 
                    "Error converting paperIds: %s", e, e.getMessage());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Get the papers from a chat session.
     * 
     * @param session the chat session
     * @return list of papers
     */
    public List<Paper> getPapersFromSession(ChatSession session) {
        List<UUID> paperIds = getPaperIdsFromSession(session);
        List<Paper> papers = new ArrayList<>();
        
        for (UUID id : paperIds) {
            paperService.getPaperById(id).ifPresent(papers::add);
        }
        
        return papers;
    }
    
    /**
     * Convert an object to a list of paper IDs.
     *
     * @param list The source list containing objects to be converted to paper UUIDs
     * @return A list of paper UUIDs extracted from the source list
     */
    private List<UUID> convertToPaperIdsList(List<?> list) {
        List<UUID> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof UUID) {
                result.add((UUID) item);
            } else if (item instanceof String) {
                try {
                    result.add(UUID.fromString((String) item));
                } catch (IllegalArgumentException e) {
                    LoggingUtil.warn(LOG, "convertToPaperIdsList", 
                            "Invalid UUID string: %s", item);
                }
            } else {
                LoggingUtil.warn(LOG, "convertToPaperIdsList", 
                        "Unknown item type in paperIds list: %s", 
                        item != null ? item.getClass().getName() : "null");
            }
        }
        return result;
    }
    
    /**
     * Convert an object to a list of analysis IDs.
     * 
     * @param list The source list containing objects to be converted to UUIDs
     * @return A list of UUIDs extracted from the source list
     */
    public List<UUID> convertToAnalysisIdsList(List<?> list) {
        List<UUID> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof UUID) {
                result.add((UUID) item);
            } else if (item instanceof String) {
                try {
                    result.add(UUID.fromString((String) item));
                } catch (IllegalArgumentException e) {
                    LoggingUtil.warn(LOG, "convertToAnalysisIdsList", 
                            "Invalid UUID string: %s", item);
                }
            }
        }
        return result;
    }
    
    /**
     * Set a title for a chat session.
     * 
     * @param sessionId the session ID
     * @param title the title to set
     * @return the updated session
     */
    @Transactional
    public ChatSession setSessionTitle(UUID sessionId, String title) {
        LoggingUtil.info(LOG, "setSessionTitle", "Setting title for session %s: %s", sessionId, title);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        session.setTitle(title);
        return sessionRepository.save(session);
    }
    
    /**
     * Archive a chat session.
     * 
     * @param sessionId the session ID
     * @return the archived session
     */
    @Transactional
    public ChatSession archiveSession(UUID sessionId) {
        LoggingUtil.info(LOG, "archiveSession", "Archiving session %s", sessionId);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        // We mark archived sessions by setting title to null
        session.setTitle(null);
        return sessionRepository.save(session);
    }
    
    /**
     * Update the last message timestamp for a session.
     * 
     * @param session the session to update
     * @return the updated session
     */
    @Transactional
    public ChatSession updateUpdatedAtTimestamp(ChatSession session) {
        session.setUpdatedAt(ZonedDateTime.now());
        return sessionRepository.save(session);
    }
}

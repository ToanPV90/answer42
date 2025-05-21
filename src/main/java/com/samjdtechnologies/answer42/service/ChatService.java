package com.samjdtechnologies.answer42.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.AnalysisResult;
import com.samjdtechnologies.answer42.model.ChatMessage;
import com.samjdtechnologies.answer42.model.ChatSession;
import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.repository.ChatMessageRepository;
import com.samjdtechnologies.answer42.repository.ChatSessionRepository;
import com.samjdtechnologies.answer42.service.helper.AIInteractionHelper;
import com.samjdtechnologies.answer42.service.helper.ChatMessageHelper;
import com.samjdtechnologies.answer42.service.helper.ChatSessionHelper;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing chat sessions and messages.
 * Provides an interface for chat functionality using the normalized message storage strategy.
 */
@Service
public class ChatService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionHelper sessionHelper;
    private final ChatMessageHelper messageHelper;
    private final AIInteractionHelper aiHelper;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param sessionRepository repository for chat sessions
     * @param messageRepository repository for chat messages
     * @param sessionHelper helper for session operations
     * @param messageHelper helper for message operations
     * @param aiHelper helper for AI interactions
     */
    public ChatService(ChatSessionRepository sessionRepository, 
                     ChatMessageRepository messageRepository,
                     ChatSessionHelper sessionHelper,
                     ChatMessageHelper messageHelper,
                     AIInteractionHelper aiHelper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.sessionHelper = sessionHelper;
        this.messageHelper = messageHelper;
        this.aiHelper = aiHelper;
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
        return sessionHelper.createChatSession(user, mode, provider);
    }
    
    /**
     * Get a chat session by ID.
     * 
     * @param sessionId the session ID
     * @return Optional containing the session if found
     */
    public Optional<ChatSession> getChatSession(UUID sessionId) {
        return sessionRepository.findById(sessionId);
    }
    
    /**
     * Refresh a chat session from the database.
     * 
     * @param sessionId the session ID
     * @return the refreshed session, or null if not found
     */
    public ChatSession refreshSession(UUID sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }
    
    /**
     * Get all chat sessions for a user.
     * 
     * @param user the user
     * @return list of chat sessions
     */
    public List<ChatSession> getChatSessionsByUser(User user) {
        return sessionRepository.findByUserOrderByLastMessageAtDesc(user);
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
        return sessionHelper.addPaperToSession(sessionId, paperId);
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
        return sessionHelper.removePaperFromSession(sessionId, paperId);
    }
    
    /**
     * Get the paper IDs from a chat session.
     * 
     * @param session the chat session
     * @return list of paper IDs
     */
    public List<UUID> getPaperIdsFromSession(ChatSession session) {
        return sessionHelper.getPaperIdsFromSession(session);
    }
    
    /**
     * Get the papers from a chat session.
     * 
     * @param session the chat session
     * @return list of papers
     */
    public List<Paper> getPapersFromSession(ChatSession session) {
        return sessionHelper.getPapersFromSession(session);
    }
    
    /**
     * Send a message to a chat session.
     * 
     * @param sessionId the session ID
     * @param content the message content
     * @return the created message
     */
    @Transactional
    public ChatMessage sendMessage(UUID sessionId, String content) {
        LoggingUtil.info(LOG, "sendMessage", "Sending assistant message to session %s", sessionId);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        return messageHelper.createMessage(session, "assistant", content);
    }
    
    /**
     * Send a user message and get a response from the AI.
     * 
     * @param sessionId the session ID
     * @param userContent the user message content
     * @return the AI response message
     */
    @Transactional
    public ChatMessage sendUserMessageAndGetResponse(UUID sessionId, String userContent) {
        LoggingUtil.info(LOG, "sendUserMessageAndGetResponse", "Processing user message for session %s", sessionId);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        // Save user message
        messageHelper.createMessage(session, "user", userContent);
        
        // Get papers for context
        List<Paper> papers = sessionHelper.getPapersFromSession(session);
        
        // Get recent messages for context
        List<ChatMessage> recentMessages = messageHelper.getRecentMessages(session.getId(), 20);
        
        // Create system prompt
        ChatMode mode = ChatMode.valueOf(session.getMode());
        String systemPrompt = aiHelper.createSystemPrompt(mode, papers);
        
        // Convert to AI messages
        List<Message> messageList = aiHelper.convertToAIMessages(recentMessages, systemPrompt);
        
        // Get AI response
        AIProvider provider = AIProvider.valueOf(session.getProvider());
        String response = aiHelper.getAIResponse(provider, messageList);
        
        // Save assistant message
        return messageHelper.createMessage(session, "assistant", response);
    }
    
    /**
     * Add an analysis result to a chat session.
     * 
     * @param sessionId the session ID
     * @param analysisResult the analysis result to add
     * @return list of created messages
     */
    @Transactional
    public List<ChatMessage> addAnalysisToChat(UUID sessionId, AnalysisResult analysisResult) {
        LoggingUtil.info(LOG, "addAnalysisToChat", "Adding analysis %s to session %s", 
                analysisResult.getId(), sessionId);
        
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        return messageHelper.addAnalysisToChat(session, analysisResult);
    }
    
    /**
     * Get recent messages from a chat session.
     * 
     * @param sessionId the session ID
     * @param limit the maximum number of messages to return
     * @return list of recent messages
     */
    public List<ChatMessage> getRecentMessages(UUID sessionId, int limit) {
        return messageHelper.getRecentMessages(sessionId, limit);
    }
    
    /**
     * Get all messages from a chat session.
     * 
     * @param sessionId the session ID
     * @return list of all messages
     */
    public List<ChatMessage> getAllMessages(UUID sessionId) {
        return messageHelper.getAllMessages(sessionId);
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
        return sessionHelper.setSessionTitle(sessionId, title);
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
        return sessionHelper.archiveSession(sessionId);
    }
    
    /**
     * Delete a chat session and its messages.
     * 
     * @param sessionId the session ID
     * @return true if deleted successfully
     */
    @Transactional
    public boolean deleteSession(UUID sessionId) {
        LoggingUtil.info(LOG, "deleteSession", "Deleting session %s", sessionId);
        
        try {
            // Delete messages first
            messageHelper.deleteAllMessages(sessionId);
            
            // Delete session
            sessionRepository.deleteById(sessionId);
            return true;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "deleteSession", 
                    "Error deleting session %s: %s", e, sessionId, e.getMessage());
            return false;
        }
    }
}

package com.samjdtechnologies.answer42.service.helpers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.model.daos.AnalysisResult;
import com.samjdtechnologies.answer42.model.daos.ChatMessage;
import com.samjdtechnologies.answer42.model.daos.ChatSession;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.repository.ChatMessageRepository;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Helper class for chat message operations.
 * Handles creation, retrieval, and management of chat messages.
 */
@Component
public class ChatMessageHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChatMessageHelper.class);
    
    private final ChatMessageRepository messageRepository;
    private final PaperService paperService;
    private final ChatSessionHelper sessionHelper;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param messageRepository repository for chat messages
     * @param paperService service for paper access
     * @param sessionHelper helper for session operations
     */
    public ChatMessageHelper(ChatMessageRepository messageRepository, 
                          PaperService paperService,
                          ChatSessionHelper sessionHelper) {
        this.messageRepository = messageRepository;
        this.paperService = paperService;
        this.sessionHelper = sessionHelper;
    }
    
    /**
     * Create a new message in a chat session.
     * 
     * @param session the chat session
     * @param role the message role (user/assistant)
     * @param content the message content
     * @return the created message
     */
    @Transactional
    public ChatMessage createMessage(ChatSession session, String role, String content) {
        LoggingUtil.debug(LOG, "createMessage", "Creating %s message for session %s", role, session.getId());
        
        // Get the next sequence number
        Integer nextSequence = messageRepository.getNextSequenceNumber(session.getId());
        
        // Create message
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        message.setSequenceNumber(nextSequence);
        message.setMessageType("message");
        message.setIsEdited(false);
        
        // Update session timestamp
        sessionHelper.updateLastMessageTimestamp(session);
        
        // Save message
        return messageRepository.save(message);
    }
    
    /**
     * Create a message with custom metadata.
     * 
     * @param session the chat session
     * @param role the message role
     * @param content the message content
     * @param messageType the message type
     * @param metadata the metadata map
     * @return the created message
     */
    @Transactional
    public ChatMessage createMessageWithMetadata(ChatSession session, String role, String content, 
                                            String messageType, Map<String, Object> metadata) {
        LoggingUtil.debug(LOG, "createMessageWithMetadata", 
                "Creating %s message of type %s for session %s", role, messageType, session.getId());
        
        // Get the next sequence number
        Integer nextSequence = messageRepository.getNextSequenceNumber(session.getId());
        
        // Create message
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        message.setSequenceNumber(nextSequence);
        message.setMessageType(messageType);
        message.setMetadata(metadata);
        message.setIsEdited(false);
        
        // Update session timestamp
        sessionHelper.updateLastMessageTimestamp(session);
        
        // Save message
        return messageRepository.save(message);
    }
    
    /**
     * Add an analysis result to a chat session.
     * 
     * @param session the chat session
     * @param analysisResult the analysis result to add
     * @return list of created messages
     */
    @Transactional
    public List<ChatMessage> addAnalysisToChat(ChatSession session, AnalysisResult analysisResult) {
        LoggingUtil.info(LOG, "addAnalysisToChat", "Adding analysis %s to session %s", 
                analysisResult.getId(), session.getId());
        
        // Update session context to include this analysis ID
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
        }
        
        // Get or create analysis IDs list
        List<UUID> analysisIds;
        if (context.containsKey("analysisIds")) {
            try {
                Object analysisIdsObj = context.get("analysisIds");
                if (analysisIdsObj instanceof List) {
                    analysisIds = sessionHelper.convertToAnalysisIdsList((List<?>) analysisIdsObj);
                } else {
                    analysisIds = List.of();
                }
            } catch (Exception e) {
                analysisIds = List.of();
            }
        } else {
            analysisIds = List.of();
        }
        
        // Add analysis ID if not already present
        UUID analysisId = analysisResult.getId();
        if (!analysisIds.contains(analysisId)) {
            analysisIds.add(analysisId);
        }
        
        // Update context
        context.put("analysisIds", analysisIds);
        session.setContext(context);
        sessionHelper.updateLastMessageTimestamp(session);
        
        // Format the analysis type for display
        String analysisType = analysisResult.getAnalysisType().toString()
                .replace("_", " ")
                .toLowerCase();
        String formattedAnalysisType = analysisType.substring(0, 1).toUpperCase() + analysisType.substring(1);
        
        // Paper title
        String paperTitle = "this paper";
        Paper paper = analysisResult.getPaper();
        if (paper != null) {
            paperTitle = "\"" + paper.getTitle() + "\"";
        }
        
        // Create introduction message metadata
        Map<String, Object> introMetadata = new HashMap<>();
        Map<String, Object> analysisMetadata = new HashMap<>();
        analysisMetadata.put("id", analysisId.toString());
        analysisMetadata.put("type", analysisResult.getAnalysisType().toString());
        analysisMetadata.put("paper_id", analysisResult.getPaper().getId().toString());
        introMetadata.put("analysis", analysisMetadata);
        introMetadata.put("message_type", "analysis");
        
        // Create introduction message
        String introContent = String.format("I've completed the %s of %s. Ask me specific questions about it.", 
                formattedAnalysisType, paperTitle);
        ChatMessage introMessage = createMessageWithMetadata(
                session, "assistant", introContent, "analysis", introMetadata);
        
        // Create content message metadata
        Map<String, Object> contentMetadata = new HashMap<>();
        contentMetadata.put("analysis", analysisMetadata);
        contentMetadata.put("message_type", "analysis_content");
        
        // Create content message
        ChatMessage contentMessage = createMessageWithMetadata(
                session, "assistant", analysisResult.getContent(), "analysis_content", contentMetadata);
        
        return List.of(introMessage, contentMessage);
    }
    
    /**
     * Edit an existing message.
     * 
     * @param messageId the ID of the message to edit
     * @param newContent the new content for the message
     * @return the updated message
     */
    @Transactional
    public ChatMessage editMessage(UUID messageId, String newContent) {
        LoggingUtil.info(LOG, "editMessage", "Editing message %s", messageId);
        
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        
        message.edit(newContent);
        return messageRepository.save(message);
    }
    
    /**
     * Get all messages for a chat session in order.
     * 
     * @param sessionId the session ID
     * @return list of all messages in order
     */
    public List<ChatMessage> getAllMessages(UUID sessionId) {
        return messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
    }
    
    /**
     * Get recent messages from a session.
     * 
     * @param sessionId the session ID
     * @param limit the maximum number of messages to return
     * @return list of recent messages
     */
    public List<ChatMessage> getRecentMessages(UUID sessionId, int limit) {
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
        
        // Return the last 'limit' messages
        if (messages.size() > limit) {
            return messages.subList(messages.size() - limit, messages.size());
        }
        
        return messages;
    }
    
    /**
     * Count the number of messages in a session.
     * 
     * @param sessionId the session ID
     * @return the number of messages
     */
    public long countMessages(UUID sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }
    
    /**
     * Delete all messages for a session.
     * 
     * @param sessionId the session ID
     */
    @Transactional
    public void deleteAllMessages(UUID sessionId) {
        LoggingUtil.info(LOG, "deleteAllMessages", "Deleting all messages for session %s", sessionId);
        messageRepository.deleteBySessionId(sessionId);
    }
}

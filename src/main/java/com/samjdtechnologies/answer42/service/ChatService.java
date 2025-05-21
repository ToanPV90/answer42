package com.samjdtechnologies.answer42.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.model.ChatMessage;
import com.samjdtechnologies.answer42.model.ChatSession;
import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.model.enums.AIProvider;
import com.samjdtechnologies.answer42.model.enums.ChatMode;
import com.samjdtechnologies.answer42.repository.ChatMessageRepository;
import com.samjdtechnologies.answer42.repository.ChatSessionRepository;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service for managing chat sessions and generating AI responses.
 */
@Service
public class ChatService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    
    @Autowired
    private ChatSessionRepository chatSessionRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @Autowired
    private PaperService paperService;
    
    @Autowired
    private AIConfig aiConfig;
    
    // System prompts for different chat modes
    private static final String PAPER_CHAT_SYSTEM_PROMPT = 
            "You are Claude, an AI assistant that helps researchers understand academic papers. " +
            "You are specifically analyzing the paper titled \"{{paperTitle}}\". " +
            "The paper content may be brief, but provide as much information about this specific paper as you can. " +
            "Be precise and academic in your responses. " +
            "Acknowledge when you're uncertain, and DON'T discuss or reference any other papers that aren't specified in this prompt. " +
            "If the user asks about this paper, focus exclusively on \"{{paperTitle}}\" and don't discuss any other papers. " +
            "Do not reference or mention other papers not included in this prompt.";
    
    private static final String CROSS_REFERENCE_SYSTEM_PROMPT = 
            "You are an AI assistant that helps researchers compare multiple academic papers. " +
            "You are analyzing the following papers: {{paperTitles}}. " +
            "Highlight similarities, differences, and relationships between them. " +
            "When answering questions, consider the context across all papers, noting agreements and contradictions. " +
            "Be precise and academic in your responses. Cite specific papers and sections when possible.";
    
    private static final String RESEARCH_EXPLORER_SYSTEM_PROMPT = 
            "You are an AI assistant that helps researchers explore scientific literature and discover new papers. " +
            "When the user asks questions, provide comprehensive answers based on your knowledge, " +
            "but also suggest additional papers they might want to explore. " +
            "Be precise and academic in your responses.";
    
    /**
     * Create a new chat session for a user.
     * 
     * @param user The user creating the session
     * @param mode The chat mode
     * @param provider The AI provider to use
     * @return The created chat session
     */
    @Transactional
    public ChatSession createChatSession(User user, ChatMode mode, AIProvider provider) {
        LoggingUtil.info(LOG, "createChatSession", "Creating new chat session for user %s with mode %s and provider %s", 
                user.getId(), mode.getValue(), provider.getValue());
        
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setMode(mode.getValue());
        session.setProvider(provider.getValue());
        session.setCreatedAt(LocalDateTime.now());
        
        Map<String, Object> context = new HashMap<>();
        context.put("paperIds", new ArrayList<UUID>());
        context.put("settings", Map.of("temperature", 0.7));
        session.setContext(context);
        
        return chatSessionRepository.save(session);
    }
    
    /**
     * Add a paper to a chat session.
     * 
     * @param sessionId The ID of the chat session
     * @param paperId The ID of the paper to add
     * @return The updated chat session
     */
    @Transactional
    public ChatSession addPaperToSession(UUID sessionId, UUID paperId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));
        
        Paper paper = paperService.getPaperById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found: " + paperId));
        
        // Get current paper IDs from context
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
        }
        
        // Get existing paper IDs
        List<UUID> paperIds = getPaperIdsFromSession(session);
        
        // Add paper ID if not already present
        if (!paperIds.contains(paperId)) {
            paperIds.add(paperId);
            context.put("paperIds", paperIds);
            session.setContext(context);
            
            // Set title based on first paper if not already set
            if (session.getTitle() == null || session.getTitle().isEmpty()) {
                session.setTitle(paper.getTitle());
            }
            
            session = chatSessionRepository.save(session);
            LoggingUtil.info(LOG, "addPaperToSession", "Added paper %s to session %s", paperId, sessionId);
        }
        
        return session;
    }
    
    /**
     * Remove a paper from a chat session.
     * 
     * @param sessionId The ID of the chat session
     * @param paperId The ID of the paper to remove
     * @return The updated chat session
     */
    @Transactional
    public ChatSession removePaperFromSession(UUID sessionId, UUID paperId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));
        
        // Get current paper IDs from context
        Map<String, Object> context = session.getContext();
        if (context == null || !context.containsKey("paperIds")) {
            return session;
        }
        
        // Get existing paper IDs
        List<UUID> paperIds = getPaperIdsFromSession(session);
        
        // Remove paper ID if present
        if (paperIds.contains(paperId)) {
            paperIds.remove(paperId);
            context.put("paperIds", paperIds);
            session.setContext(context);
            session = chatSessionRepository.save(session);
            LoggingUtil.info(LOG, "removePaperFromSession", "Removed paper %s from session %s", paperId, sessionId);
        }
        
        return session;
    }
    
    /**
     * Send a message to a chat session and get an AI response.
     * 
     * @param sessionId The ID of the chat session
     * @param userMessage The message from the user
     * @return The AI assistant's response message
     * @throws IllegalArgumentException If the specified chat session does not exist
     * @throws IllegalStateException If using paper chat mode without selecting any papers
     */
    @Transactional
    public ChatMessage sendMessage(UUID sessionId, String userMessage) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));
        
        // Check if there are papers for paper chat mode
        if (ChatMode.CHAT.getValue().equals(session.getMode()) && getPaperIdsFromSession(session).isEmpty()) {
            throw new IllegalStateException("Paper chat requires at least one paper to be selected");
        }
        
        // Create and save user message
        Integer nextSeq = chatMessageRepository.getNextSequenceNumber(sessionId);
        ChatMessage userChatMessage = new ChatMessage(sessionId, "user", userMessage, nextSeq);
        userChatMessage = chatMessageRepository.save(userChatMessage);
        
        // Generate AI response
        String aiResponse = generateAIResponse(session, userMessage);
        
        // Create and save assistant message
        nextSeq = chatMessageRepository.getNextSequenceNumber(sessionId);
        ChatMessage assistantChatMessage = new ChatMessage(sessionId, "assistant", aiResponse, nextSeq);
        assistantChatMessage = chatMessageRepository.save(assistantChatMessage);
        
        // Update session's last message timestamp
        session.updateLastMessageTimestamp();
        chatSessionRepository.save(session);
        
        return assistantChatMessage;
    }
    
    /**
     * Generate an AI response for a message in a chat session.
     * 
     * @param session The chat session
     * @param userMessage The latest message from the user
     * @return The AI assistant's response
     */
    private String generateAIResponse(ChatSession session, String userMessage) {
        AIProvider provider = AIProvider.fromValue(session.getProvider());
        ChatMode mode = ChatMode.fromValue(session.getMode());
        
        // Get the appropriate system prompt based on chat mode
        String systemPrompt = getSystemPromptForSession(session, mode);
        LoggingUtil.debug(LOG, "generateAIResponse", "Using provider: %s, mode: %s", provider, mode);
        LoggingUtil.debug(LOG, "generateAIResponse", "System prompt length: %d characters", 
                systemPrompt != null ? systemPrompt.length() : 0);
        
        // Get conversation history
        List<ChatMessage> previousMessages = chatMessageRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        LoggingUtil.debug(LOG, "generateAIResponse", "Found %d previous messages for session: %s", 
                previousMessages.size(), session.getId());
        
        List<String> userMessages = previousMessages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .collect(Collectors.toList());
        
        List<String> assistantMessages = previousMessages.stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .collect(Collectors.toList());
                
        LoggingUtil.debug(LOG, "generateAIResponse", "Generated %d user messages and %d assistant messages", 
                userMessages.size(), assistantMessages.size());
        
        // Build messages list for AI request
        List<Message> messages = new ArrayList<>();
        
        // Add system message if provided
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        
        // Add conversation history (excluding the latest user message which we'll add at the end)
        for (int i = 0; i < userMessages.size() - 1; i++) {
            messages.add(new UserMessage(userMessages.get(i)));
            if (i < assistantMessages.size()) {
                messages.add(new AssistantMessage(assistantMessages.get(i)));
            }
        }
        
        // Add the latest user message
        messages.add(new UserMessage(userMessage));
        
        // Get the appropriate ChatClient based on provider
        ChatClient chatClient;
        switch (provider) {
            case ANTHROPIC:
                chatClient = aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
                break;
            case OPENAI:
                chatClient = aiConfig.openAiChatClient(aiConfig.openAiChatModel(aiConfig.openAiApi()));
                break;
            case PERPLEXITY:
                chatClient = aiConfig.perplexityChatClient(aiConfig.perplexityChatModel(aiConfig.perplexityApi()));
                break;
            default:
                chatClient = aiConfig.anthropicChatClient(aiConfig.anthropicChatModel(aiConfig.anthropicApi()));
        }
        
        try {
            LoggingUtil.debug(LOG, "generateAIResponse", "Generating completion with %d messages", messages.size());
            Prompt prompt = new Prompt(messages);
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            LoggingUtil.error(LOG, "generateAIResponse", "Error generating completion: %s", e, e.getMessage());
            return "I'm sorry, but I encountered an error processing your request. Please try again later.";
        }
    }
    
    /**
     * Get the appropriate system prompt for a chat session based on its mode.
     * 
     * @param session The chat session
     * @param mode The chat mode
     * @return The system prompt to use
     */
    private String getSystemPromptForSession(ChatSession session, ChatMode mode) {
        Map<String, Object> promptVars = new HashMap<>();
        
        switch (mode) {
            case CHAT:
                // For paper chat, get the paper content and details
                List<UUID> paperIds = getPaperIdsFromSession(session);
                if (!paperIds.isEmpty()) {
                    UUID paperId = paperIds.get(0);
                    
                    // Log the paper ID we're trying to retrieve
                    LoggingUtil.info(LOG, "getSystemPromptForSession", "Loading paper with ID: %s", paperId);
                    
                    // Store reference to the paper outside the lambda
                    Paper selectedPaper = paperService.getPaperById(paperId).orElse(null);
                    if (selectedPaper == null) {
                        LoggingUtil.error(LOG, "getSystemPromptForSession", "Failed to load paper with ID: %s", paperId);
                        return "You are Claude, an AI assistant that helps researchers understand academic papers.";
                    }
                    
                    // Log what paper was found
                    LoggingUtil.info(LOG, "getSystemPromptForSession", "Loaded paper: '%s' with ID: %s", 
                        selectedPaper.getTitle(), selectedPaper.getId());
                    
                    // Add paper ID to logs for debugging
                    LoggingUtil.debug(LOG, "getSystemPromptForSession", "Processing paper with ID: %s", selectedPaper.getId());
                    
                    promptVars.put("paperTitle", selectedPaper.getTitle());
                    
                    // Add paper content details
                    StringBuilder paperContent = new StringBuilder();
                    
                    // Add title, authors, and journal
                    paperContent.append("Title: ").append(selectedPaper.getTitle()).append("\n\n");
                    
                    if (selectedPaper.getAuthors() != null && !selectedPaper.getAuthors().isEmpty()) {
                        paperContent.append("Authors: ").append(String.join(", ", selectedPaper.getAuthors())).append("\n\n");
                    }
                    
                    if (selectedPaper.getJournal() != null) {
                        paperContent.append("Journal: ").append(selectedPaper.getJournal()).append("\n\n");
                    }
                    
                    if (selectedPaper.getYear() != null) {
                        paperContent.append("Year: ").append(selectedPaper.getYear()).append("\n\n");
                    }
                    
                    // Add abstract if available
                    if (selectedPaper.getAbstract() != null && !selectedPaper.getAbstract().isEmpty()) {
                        paperContent.append("Abstract: ").append(selectedPaper.getAbstract()).append("\n\n");
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "Added abstract with length: %d", 
                            selectedPaper.getAbstract().length());
                    } else {
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "Paper has no abstract");
                        
                        // Handle potentially missing DOI
                        if (selectedPaper.getDoi() != null && !selectedPaper.getDoi().isEmpty()) {
                            paperContent.append("DOI: ").append(selectedPaper.getDoi()).append("\n\n");
                        }
                    }
                    
                    // Add text content if available (this could be large)
                    if (selectedPaper.getTextContent() != null && !selectedPaper.getTextContent().isEmpty()) {
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "Adding text content with length: %d", 
                            selectedPaper.getTextContent().length());
                        // Limit the content length to 10000 characters to ensure it fits in the context window
                        String textContent = selectedPaper.getTextContent();
                        if (textContent.length() > 10000) {
                            textContent = textContent.substring(0, 10000) + "... [truncated for length]";
                            LoggingUtil.debug(LOG, "getSystemPromptForSession", "Truncated text content to 10000 characters");
                        }
                        paperContent.append("Full Content:\n").append(textContent).append("\n\n");
                    } else {
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "Paper has no text content");
                    }
                    
                    // Add the paper content to prompt variables
                    promptVars.put("paperContent", paperContent.toString());
                    
                    // Log the prompt variables to debug
                    LoggingUtil.debug(LOG, "getSystemPromptForSession", "Paper content length: %d characters", 
                        paperContent.toString().length());
                        
                    if (promptVars.containsKey("paperTitle") && promptVars.containsKey("paperContent")) {
                        // Create enhanced system prompt with direct variable substitution to avoid template issues
                        String paperTitle = selectedPaper.getTitle();
                        String paperContentStr = (String)promptVars.get("paperContent");
                        
                        // Directly substitute variables to avoid template issues
                        String systemPromptBase = PAPER_CHAT_SYSTEM_PROMPT
                                .replace("{{paperTitle}}", paperTitle);
                        
                        String enhancedPrompt = systemPromptBase + 
                                "\n\nIMPORTANT: You are ONLY analyzing the paper titled \"" + paperTitle + "\" with ID: " + selectedPaper.getId() + 
                                "\n\nHere is the complete paper content to analyze (DO NOT reference any other papers):\n\n" + paperContentStr;
                        
                        // Enhanced logging for debugging paper identification issues
                        LoggingUtil.info(LOG, "getSystemPromptForSession", "===== PAPER IDENTIFICATION INFO =====");
                        LoggingUtil.info(LOG, "getSystemPromptForSession", "Paper ID: %s", selectedPaper.getId());
                        LoggingUtil.info(LOG, "getSystemPromptForSession", "Paper title: %s", paperTitle);
                        LoggingUtil.info(LOG, "getSystemPromptForSession", "Paper content length: %d characters", paperContentStr.length());
                        LoggingUtil.info(LOG, "getSystemPromptForSession", "Paper content first 100 chars: %s", 
                                paperContentStr.substring(0, Math.min(100, paperContentStr.length())));
                        
                        // Log full system prompt
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "System prompt start: %s", 
                                enhancedPrompt.substring(0, Math.min(200, enhancedPrompt.length())));
                        
                        // Get a sample of the direct substitution results to verify
                        LoggingUtil.info(LOG, "getSystemPromptForSession", "Title substitution check: %s", 
                                systemPromptBase.substring(0, Math.min(150, systemPromptBase.length())));
                        
                        String renderedPrompt = enhancedPrompt;
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "Final prompt length: %d chars", renderedPrompt.length());
                        LoggingUtil.debug(LOG, "getSystemPromptForSession", "Final prompt title check: %s", 
                                renderedPrompt.contains(paperTitle) ? "SUCCESS - Title found in prompt" : "FAILURE - Title not in prompt");
                        
                        return renderedPrompt;
                    }
                }
                return "You are Claude, an AI assistant that helps researchers understand academic papers.";
                
            case CROSS_REFERENCE:
                // For cross-reference, get all paper contents
                List<UUID> crossRefPaperIds = getPaperIdsFromSession(session);
                if (!crossRefPaperIds.isEmpty()) {
                    // Collect titles for reference in the basic prompt
                    List<String> paperTitles = new ArrayList<>();
                    
                    // Build paper contents for enhanced prompt
                    StringBuilder paperContents = new StringBuilder();
                    paperContents.append("Here are the papers to compare:\n\n");
                    
                    // Use atomic integer for thread safety and to make it effectively final for lambda
                    final AtomicInteger paperCounter = new AtomicInteger(1);
                    
                    for (UUID paperId : crossRefPaperIds) {
                        paperService.getPaperById(paperId).ifPresent(paper -> {
                            // Add title to the list for the basic prompt
                            paperTitles.add(paper.getTitle());
                            
                            // Add detailed paper information for the enhanced prompt
                            int currentPaperNumber = paperCounter.getAndIncrement();
                            paperContents.append("PAPER ").append(currentPaperNumber).append(":\n");
                            paperContents.append("Title: ").append(paper.getTitle()).append("\n");
                            
                            if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
                                paperContents.append("Authors: ").append(String.join(", ", paper.getAuthors())).append("\n");
                            }
                            
                            if (paper.getJournal() != null) {
                                paperContents.append("Journal: ").append(paper.getJournal()).append("\n");
                            }
                            
                            if (paper.getYear() != null) {
                                paperContents.append("Year: ").append(paper.getYear()).append("\n");
                            }
                            
                            if (paper.getAbstract() != null && !paper.getAbstract().isEmpty()) {
                                paperContents.append("Abstract: ").append(paper.getAbstract()).append("\n");
                            }
                            
                            // Add a limited amount of full text content if available
                            if (paper.getTextContent() != null && !paper.getTextContent().isEmpty()) {
                                // Limit the content to avoid overwhelming the context window
                                String textContent = paper.getTextContent();
                                int maxLength = 5000; // Set a reasonable limit
                                if (textContent.length() > maxLength) {
                                    textContent = textContent.substring(0, maxLength) + "... [truncated for length]";
                                }
                                paperContents.append("Content: ").append(textContent).append("\n");
                            }
                            
                            paperContents.append("\n-----------------------------------\n\n");
                        });
                    }
                    
                    promptVars.put("paperTitles", String.join(", ", paperTitles));
                    promptVars.put("paperContents", paperContents.toString());
                    
                    // Return enhanced prompt with paper contents
                    String enhancedPrompt = CROSS_REFERENCE_SYSTEM_PROMPT + 
                            "\n\n{{paperContents}}";
                    
                    return new PromptTemplate(enhancedPrompt).render(promptVars);
                }
                return "You are an AI assistant that helps researchers compare multiple academic papers.";
                
            case RESEARCH_EXPLORER:
                return RESEARCH_EXPLORER_SYSTEM_PROMPT;
                
            default:
                return "You are an AI assistant that helps researchers with their academic work.";
        }
    }
    
    /**
     * Get the list of paper IDs from a chat session's context.
     * 
     * @param session The chat session
     * @return List of paper IDs
     */
    private List<UUID> getPaperIdsFromSession(ChatSession session) {
        LoggingUtil.debug(LOG, "getPaperIdsFromSession", "Getting paper IDs for session: %s", session.getId());
        
        Map<String, Object> context = session.getContext();
        if (context == null) {
            LoggingUtil.warn(LOG, "getPaperIdsFromSession", "Context is null for session: %s", session.getId());
            return new ArrayList<>();
        }
        
        if (!context.containsKey("paperIds")) {
            LoggingUtil.warn(LOG, "getPaperIdsFromSession", "No paperIds key in context for session: %s", session.getId());
            return new ArrayList<>();
        }
        
        Object paperIdsObj = context.get("paperIds");
        if (paperIdsObj == null) {
            LoggingUtil.warn(LOG, "getPaperIdsFromSession", "paperIds value is null for session: %s", session.getId());
            return new ArrayList<>();
        }
        
        if (!(paperIdsObj instanceof List)) {
            LoggingUtil.error(LOG, "getPaperIdsFromSession", 
                    "paperIds is not a List but a %s for session: %s", 
                    paperIdsObj.getClass().getName(), session.getId());
            return new ArrayList<>();
        }
        
        List<?> idList = (List<?>) paperIdsObj;
        LoggingUtil.debug(LOG, "getPaperIdsFromSession", "Raw paper IDs count: %d for session: %s", 
                idList.size(), session.getId());
        
        List<UUID> uuidList = new ArrayList<>();
        
        for (int i = 0; i < idList.size(); i++) {
            Object id = idList.get(i);
            if (id == null) {
                LoggingUtil.warn(LOG, "getPaperIdsFromSession", 
                        "Null paper ID at index %d for session: %s", i, session.getId());
                continue;
            }
            
            if (id instanceof UUID) {
                uuidList.add((UUID) id);
                LoggingUtil.debug(LOG, "getPaperIdsFromSession", 
                        "Added UUID paper ID: %s for session: %s", id, session.getId());
            } else if (id instanceof String) {
                try {
                    UUID uuid = UUID.fromString((String) id);
                    uuidList.add(uuid);
                    LoggingUtil.debug(LOG, "getPaperIdsFromSession", 
                            "Converted string to UUID paper ID: %s for session: %s", uuid, session.getId());
                } catch (IllegalArgumentException e) {
                    LoggingUtil.error(LOG, "getPaperIdsFromSession", 
                        "Invalid UUID string: %s at index %d for session: %s", e, id, i, session.getId());
                }
            } else {
                LoggingUtil.error(LOG, "getPaperIdsFromSession", 
                        "Unsupported paper ID type: %s at index %d for session: %s", 
                        id.getClass().getName(), i, session.getId());
            }
        }
        
        LoggingUtil.info(LOG, "getPaperIdsFromSession", "Found %d valid paper IDs for session: %s", 
                uuidList.size(), session.getId());
        return uuidList;
    }
    
    /**
     * Get all messages for a chat session.
     * 
     * @param sessionId The ID of the chat session
     * @return List of chat messages in order
     */
    public List<ChatMessage> getChatMessages(UUID sessionId) {
        return chatMessageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
    }
    
    /**
     * Get chat sessions for a user.
     * 
     * @param user The user
     * @return List of chat sessions owned by the user
     */
    public List<ChatSession> getChatSessionsForUser(User user) {
        return chatSessionRepository.findByUserOrderByLastMessageAtDesc(user);
    }
    
    /**
     * Get chat sessions for a user and specific mode.
     * 
     * @param user The user
     * @param mode The chat mode
     * @return List of chat sessions matching the criteria
     */
    public List<ChatSession> getChatSessionsForUserAndMode(User user, ChatMode mode) {
        return chatSessionRepository.findByUserAndMode(user, mode.getValue());
    }
    
    /**
     * Delete a chat session and all its messages.
     * 
     * @param sessionId The ID of the chat session to delete
     */
    @Transactional
    public void deleteChatSession(UUID sessionId) {
        // First delete all messages
        chatMessageRepository.deleteBySessionId(sessionId);
        
        // Then delete the session
        chatSessionRepository.deleteById(sessionId);
        
        LoggingUtil.info(LOG, "deleteChatSession", "Deleted chat session %s", sessionId);
    }
}

# Using Spring Boot to Manage a Chat Session

I'd be happy to explain how to use Spring Boot to manage a chat session. Let me walk you through the key components and steps needed to build a chat application with Spring Boot.

## Core Components

To manage chat sessions in Spring Boot, you'll need to implement:

1. WebSocket support for real-time communication
2. Session management to track active users
3. Message handling for processing and distributing chat messages

## Implementation Steps

### 1. Set Up Dependencies

Add these dependencies to your `pom.xml`:

```xml
<dependencies>
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- WebSocket Support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- For working with JSON -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-json</artifactId>
    </dependency>
</dependencies>
```

### 2. Configure WebSocket

Create a WebSocket configuration class:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");  // Prefix for outbound messages
        config.setApplicationDestinationPrefixes("/app");  // Prefix for inbound messages
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();  // Fallback options
    }
}
```

### 3. Create Message Models

Define classes for your chat messages:

```java
public class ChatMessage {
    private MessageType type;
    private String content;
    private String sender;
    private String timestamp;

    // Getters, setters, constructors
}

public enum MessageType {
    CHAT, JOIN, LEAVE
}
```

### 4. Implement Session Handling

Create a session handler to track user connections:

```java
@Component
public class ChatSessionHandler {
    private Map<String, Set<String>> activeUsers = new ConcurrentHashMap<>();

    public void addUserToSession(String sessionId, String username) {
        activeUsers.computeIfAbsent(sessionId, k -> new ConcurrentHashMap().newKeySet())
                   .add(username);
    }

    public void removeUserFromSession(String sessionId, String username) {
        Set<String> sessionUsers = activeUsers.get(sessionId);
        if (sessionUsers != null) {
            sessionUsers.remove(username);
            if (sessionUsers.isEmpty()) {
                activeUsers.remove(sessionId);
            }
        }
    }

    public Set<String> getUsersInSession(String sessionId) {
        return activeUsers.getOrDefault(sessionId, Collections.emptySet());
    }
}
```

### 5. Create Controller for Messages

Handle incoming and outgoing messages:

```java
@Controller
public class ChatController {

    @Autowired
    private ChatSessionHandler sessionHandler;

    @MessageMapping("/chat.sendMessage/{sessionId}")
    @SendTo("/topic/public/{sessionId}")
    public ChatMessage sendMessage(@DestinationVariable String sessionId, 
                                  @Payload ChatMessage chatMessage) {
        chatMessage.setTimestamp(new SimpleDateFormat("HH:mm:ss").format(new Date()));
        return chatMessage;
    }

    @MessageMapping("/chat.addUser/{sessionId}")
    @SendTo("/topic/public/{sessionId}")
    public ChatMessage addUser(@DestinationVariable String sessionId,
                              @Payload ChatMessage chatMessage,
                              SimpMessageHeaderAccessor headerAccessor) {
        // Add username to WebSocket session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        headerAccessor.getSessionAttributes().put("chatSessionId", sessionId);

        // Track user in our custom session handler
        sessionHandler.addUserToSession(sessionId, chatMessage.getSender());

        return chatMessage;
    }
}
```

### 6. Implement Event Listener for Disconnects

Handle WebSocket connection events:

```java
@Component
public class WebSocketEventListener {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private ChatSessionHandler sessionHandler;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String sessionId = (String) headerAccessor.getSessionAttributes().get("chatSessionId");

        if(username != null && sessionId != null) {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setType(MessageType.LEAVE);
            chatMessage.setSender(username);

            sessionHandler.removeUserFromSession(sessionId, username);

            messagingTemplate.convertAndSend("/topic/public/" + sessionId, chatMessage);
        }
    }
}
```

### 7. Create REST Endpoints for Session Management

Add REST endpoints to create and manage chat sessions:

```java
@RestController
@RequestMapping("/api/chat")
public class ChatSessionController {

    @Autowired
    private ChatSessionHandler sessionHandler;

    // Generate a new session ID
    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession() {
        String sessionId = UUID.randomUUID().toString();
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        return ResponseEntity.ok(response);
    }

    // Get active users in a session
    @GetMapping("/session/{sessionId}/users")
    public ResponseEntity<Set<String>> getSessionUsers(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionHandler.getUsersInSession(sessionId));
    }
}
```

## Client Integration

On the client side, you'll need JavaScript to connect to the WebSocket:

```javascript
// Connect to WebSocket
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

// Join chat session
function joinChatSession(sessionId, username) {
    stompClient.connect({}, function() {
        // Subscribe to session channel
        stompClient.subscribe('/topic/public/' + sessionId, onMessageReceived);

        // Send join message
        stompClient.send('/app/chat.addUser/' + sessionId, {}, 
            JSON.stringify({
                sender: username,
                type: 'JOIN'
            })
        );
    });
}

// Send message
function sendMessage(sessionId, username, message) {
    stompClient.send('/app/chat.sendMessage/' + sessionId, {}, 
        JSON.stringify({
            sender: username,
            content: message,
            type: 'CHAT'
        })
    );
}
```

## Additional Features to Consider

1. **Message persistence**: Store messages in a database for history
2. **Private messaging**: Implement direct user-to-user messaging
3. **Read receipts**: Track when messages are seen
4. **Typing indicators**: Show when users are typing
5. **File sharing**: Allow users to share files/images

Would you like me to explain any specific part of this implementation in more detail?

package com.example.Backend;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final List<ChatMessage> chatHistory = new CopyOnWriteArrayList<>();
    
    // Track Active Session IDs (This fixes the duplicate count issue)
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Helper to get count
    public int getActiveUserCount() {
        return activeSessions.size();
    }
    
    // Helper to remove user (Called by EventListener)
    public void removeUser(String username) {
        // In a real app, we'd map SessionID to User, but for now just decrementing generic sessions
        // Since we track session IDs in the Set, we remove via the session ID logic in the listener
        // But to keep it simple for this specific code structure:
        // We will rely on the Set size.
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // Add username to session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        
        // Track this specific session ID
        activeSessions.add(headerAccessor.getSessionId());

        // Add Time to the JOIN message
        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // Send History
        String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
        if (chatMessage.getSenderId() != null) {
            for (ChatMessage oldMsg : chatHistory) {
                messagingTemplate.convertAndSend(uniqueUserChannel, oldMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        // Always set the server time immediately
        chatMessage.setTime(getCurrentTime());

        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            chatHistory.clear();
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } 
        else if (chatMessage.getType() == ChatMessage.MessageType.TYPING) {
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            chatHistory.add(chatMessage);
            chatMessage.setType(ChatMessage.MessageType.CHAT);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    // Utility to get HH:mm AM/PM
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
    
    // Listener calls this when a tab closes
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }
}
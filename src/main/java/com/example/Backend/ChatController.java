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

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRepository chatRepository; // Must have this!

    // RAM is ONLY for user count, NOT for messages
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    // Helper methods
    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }
    public int getActiveUserCount() { return activeSessions.size(); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Session Tracking
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        activeSessions.add(headerAccessor.getSessionId());

        // 2. Broadcast Join
        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. LOAD HISTORY (Crucial Step)
        if (chatMessage.getGroupName() != null) {
            System.out.println("Loading history for group: " + chatMessage.getGroupName()); // Debug Log
            
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            
            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            
            for (ChatMessage oldMsg : history) {
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                historyMsg.setReactions(oldMsg.getReactions());
                historyMsg.setRead(oldMsg.isRead());
                
                // Send directly to the user
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());
        chatMessage.setOnlineCount(activeSessions.size());

        // Debug Log
        System.out.println("Received message type: " + chatMessage.getType() + " for group: " + chatMessage.getGroupName());

        // SAVE LOGIC
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || 
            chatMessage.getType() == ChatMessage.MessageType.VOICE || 
            chatMessage.getType() == ChatMessage.MessageType.IMAGE) {
            
            // CRITICAL: Only save if Group Name exists
            if(chatMessage.getGroupName() != null) {
                chatRepository.save(chatMessage);
                System.out.println("✅ Saved to MongoDB: " + chatMessage.getText());
            } else {
                System.out.println("❌ Error: Group Name is NULL. Message not saved.");
            }
            
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
                System.out.println("🗑️ History Deleted for group: " + chatMessage.getGroupName());
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            // Other types (Typing, Calls, etc.) - Just forward, don't save
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
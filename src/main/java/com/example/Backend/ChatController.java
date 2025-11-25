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
    private final ChatRepository chatRepository; // <--- MongoDB Connection

    // RAM: Only for counting who is online right now
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    // Helper methods called by WebSocketEventListener
    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }
    public int getActiveUserCount() { return activeSessions.size(); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Track Online User
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        activeSessions.add(headerAccessor.getSessionId());

        // 2. Broadcast "User Joined" + Current Count
        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. LOAD HISTORY FROM MONGODB
        if (chatMessage.getGroupName() != null) {
            // Fetch from DB
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            
            // Send to the specific user only (Private Channel)
            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            
            for (ChatMessage oldMsg : history) {
                // We send it as 'HISTORY' type so frontend handles it correctly
                // We create a copy to ensure we don't mess up the DB object
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                historyMsg.setReactions(oldMsg.getReactions());
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setRead(oldMsg.isRead());
                
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());

        // --- A. HANDLE DELETE (CLEAR) ---
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                // Find all messages in this group
                List<ChatMessage> msgsToDelete = chatRepository.findByGroupName(chatMessage.getGroupName());
                // Delete them from MongoDB
                chatRepository.deleteAll(msgsToDelete);
            }
            // Tell frontend to clear screen
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
            return;
        } 
        
        // --- B. HANDLE CHAT / VOICE ---
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || chatMessage.getType() == ChatMessage.MessageType.VOICE) {
            // Save to MongoDB
            if(chatMessage.getGroupName() != null) {
                chatRepository.save(chatMessage);
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        
        // --- C. HANDLE REACTIONS ---
        else if (chatMessage.getType() == ChatMessage.MessageType.REACTION) {
            // Find message in MongoDB
            ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
            if (existing != null) {
                // Update DB
                existing.getReactions().put(chatMessage.getFrom(), chatMessage.getText());
                chatRepository.save(existing);
                
                // Broadcast update
                chatMessage.setReactions(existing.getReactions());
                messagingTemplate.convertAndSend("/topic/public", chatMessage);
            }
        }
        
        // --- D. HANDLE READ RECEIPTS ---
        else if (chatMessage.getType() == ChatMessage.MessageType.READ) {
             ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
             if(existing != null) { 
                 existing.setRead(true); 
                 chatRepository.save(existing); 
                 messagingTemplate.convertAndSend("/topic/public", chatMessage);
             }
        }
        
        // --- E. TYPING / CALLS (Don't Save to DB) ---
        else {
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
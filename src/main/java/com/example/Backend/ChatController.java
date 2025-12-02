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
    private final ChatRepository chatRepository;

    // RAM for counting users (Not messages)
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    // Helper to get count
    public int getActiveUserCount() { return activeSessions.size(); }
    // Helper to remove session (Called by EventListener)
    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Register Session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        activeSessions.add(headerAccessor.getSessionId());

        System.out.println("➕ USER JOINED: " + chatMessage.getFrom() + " | TOTAL ONLINE: " + activeSessions.size());

        // 2. Broadcast Join to Public
        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size()); // <--- SEND COUNT
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. Load History from MongoDB
        if (chatMessage.getGroupName() != null) {
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            System.out.println("📚 LOADING HISTORY: Found " + history.size() + " messages for group " + chatMessage.getGroupName());

            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            
            for (ChatMessage oldMsg : history) {
                // Send as HISTORY type so it renders quietly
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                historyMsg.setRead(oldMsg.isRead());
                historyMsg.setReactions(oldMsg.getReactions());
                
                // Keep media types correct
                if (oldMsg.getType() == ChatMessage.MessageType.IMAGE || oldMsg.getType() == ChatMessage.MessageType.VOICE) {
                    historyMsg.setType(oldMsg.getType());
                }

                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());
        chatMessage.setOnlineCount(activeSessions.size()); // Always send count

        // Handle Clear
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
                System.out.println("🗑️ CLEARED DB for group: " + chatMessage.getGroupName());
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
            return;
        } 
        
        // Handle Chat/Media Saving
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || 
            chatMessage.getType() == ChatMessage.MessageType.VOICE || 
            chatMessage.getType() == ChatMessage.MessageType.IMAGE) {
            
            if(chatMessage.getGroupName() != null) {
                chatRepository.save(chatMessage);
                System.out.println("💾 SAVED: " + chatMessage.getText());
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            // Typing, Calls, Reactions (Pass through)
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
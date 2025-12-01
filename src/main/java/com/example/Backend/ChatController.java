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
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }
    public int getActiveUserCount() { return activeSessions.size(); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        activeSessions.add(headerAccessor.getSessionId());

        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // --- DEBUG LOGS FOR HISTORY ---
        System.out.println("👉 USER JOINED: " + chatMessage.getFrom());
        System.out.println("👉 REQUESTING HISTORY FOR GROUP: " + chatMessage.getGroupName());

        if (chatMessage.getGroupName() != null) {
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            
            System.out.println("✅ FOUND " + history.size() + " MESSAGES IN DATABASE.");

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
                
                // Fix for Images/Voice in history
                if (oldMsg.getType() == ChatMessage.MessageType.IMAGE || oldMsg.getType() == ChatMessage.MessageType.VOICE) {
                    historyMsg.setType(oldMsg.getType());
                }

                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        } else {
            System.out.println("❌ ERROR: User joined with NULL Group Name. History cannot be loaded.");
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());
        chatMessage.setOnlineCount(activeSessions.size());

        // --- DEBUG LOGS FOR SAVING ---
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || 
            chatMessage.getType() == ChatMessage.MessageType.VOICE || 
            chatMessage.getType() == ChatMessage.MessageType.IMAGE) {
            
            if(chatMessage.getGroupName() != null && !chatMessage.getGroupName().isEmpty()) {
                chatRepository.save(chatMessage);
                System.out.println("💾 SAVED TO DB: " + chatMessage.getText() + " | GROUP: " + chatMessage.getGroupName());
            } else {
                System.out.println("❌ FAILED TO SAVE: Group Name is Missing!");
            }
            
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
                System.out.println("🗑️ CLEARED HISTORY FOR: " + chatMessage.getGroupName());
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            // Typing, Read, Reaction, etc.
            if(chatMessage.getType() == ChatMessage.MessageType.REACTION || chatMessage.getType() == ChatMessage.MessageType.READ) {
                 ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
                 if(existing != null) {
                     if(chatMessage.getType() == ChatMessage.MessageType.READ) existing.setRead(true);
                     else existing.setReactions(chatMessage.getReactions());
                     chatRepository.save(existing);
                 }
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
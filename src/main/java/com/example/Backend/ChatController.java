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

    // TRACKING ONLINE USERS
    public static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Add User to Session List
        activeSessions.add(headerAccessor.getSessionId());
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());

        System.out.println("➕ JOIN: " + chatMessage.getFrom() + " | Group: " + chatMessage.getGroupName());

        // 2. Broadcast Join
        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size()); // Send accurate count
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. LOAD HISTORY (The Fix)
        if (chatMessage.getGroupName() != null) {
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            System.out.println("📚 DB CHECK: Found " + history.size() + " messages for " + chatMessage.getGroupName());

            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            
            for (ChatMessage oldMsg : history) {
                // Send as HISTORY type (so it doesn't play sound)
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime()); // Use stored time
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                historyMsg.setRead(oldMsg.isRead());
                historyMsg.setReactions(oldMsg.getReactions());
                
                // Preserve media types
                if (oldMsg.getType() == ChatMessage.MessageType.IMAGE || oldMsg.getType() == ChatMessage.MessageType.VOICE) {
                    historyMsg.setType(oldMsg.getType());
                }

                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        // 1. Fix Time
        chatMessage.setTime(getCurrentTime());
        // 2. Fix Count
        chatMessage.setOnlineCount(activeSessions.size());

        // A. DELETE HISTORY
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
                System.out.println("🗑️ DB CLEARED for " + chatMessage.getGroupName());
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
            return;
        } 
        
        // B. SAVE MESSAGES
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || 
            chatMessage.getType() == ChatMessage.MessageType.VOICE || 
            chatMessage.getType() == ChatMessage.MessageType.IMAGE) {
            
            if(chatMessage.getGroupName() != null) {
                chatRepository.save(chatMessage);
                System.out.println("💾 SAVED TO DB: " + chatMessage.getText());
            } else {
                System.out.println("❌ ERROR: Group Name is NULL. Message lost.");
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            // Typing, Read, Reaction (Don't save typing, do save others)
            if(chatMessage.getType() == ChatMessage.MessageType.READ || chatMessage.getType() == ChatMessage.MessageType.REACTION) {
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
package com.example.Backend;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

        if (chatMessage.getGroupName() != null) {
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            for (ChatMessage oldMsg : history) {
                // We send history WITHOUT modifying the original time, 
                // so the client sees when it was actually sent.
                oldMsg.setType(ChatMessage.MessageType.HISTORY); 
                messagingTemplate.convertAndSend(uniqueUserChannel, oldMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        // Set Server Time (UTC or Server Local) - Client will convert this
        if(chatMessage.getTime() == null) {
            chatMessage.setTime(getCurrentTime());
        }

        // Handle WebRTC Signaling (Calls) - Just pass them through, don't save to DB
        if (isCallSignal(chatMessage.getType())) {
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
            return;
        }

        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } 
        else if (chatMessage.getType() == ChatMessage.MessageType.VOICE) {
            // Save voice notes to DB
            if(chatMessage.getGroupName() != null) chatRepository.save(chatMessage);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else if (chatMessage.getType() == ChatMessage.MessageType.REACTION) {
            ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
            if (existing != null) {
                existing.getReactions().put(chatMessage.getFrom(), chatMessage.getText());
                chatRepository.save(existing);
                chatMessage.setReactions(existing.getReactions());
                messagingTemplate.convertAndSend("/topic/public", chatMessage);
            }
        }
        else if (chatMessage.getType() == ChatMessage.MessageType.READ || chatMessage.getType() == ChatMessage.MessageType.TYPING) {
             messagingTemplate.convertAndSend("/topic/public", chatMessage);
             if(chatMessage.getType() == ChatMessage.MessageType.READ) {
                 ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
                 if(existing != null) { existing.setRead(true); chatRepository.save(existing); }
             }
        }
        else {
            // Normal Chat
            if(chatMessage.getGroupName() != null) chatRepository.save(chatMessage);
            chatMessage.setType(ChatMessage.MessageType.CHAT);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private boolean isCallSignal(ChatMessage.MessageType type) {
        return type == ChatMessage.MessageType.offer || 
               type == ChatMessage.MessageType.answer || 
               type == ChatMessage.MessageType.candidate || 
               type == ChatMessage.MessageType.hangup;
    }
    
    private String getCurrentTime() {
        // ISO format is better for clients to parse, but keeping simple string for now
        return ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
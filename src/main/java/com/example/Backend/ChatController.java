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
    
    // --- THIS IS THE PART YOU WERE MISSING ---
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    // These are the methods the EventListener is looking for:
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

    public int getActiveUserCount() {
        return activeSessions.size();
    }
    // ----------------------------------------

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        
        // Track the session
        activeSessions.add(headerAccessor.getSessionId());

        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size()); // Send new count
        
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // Send History
        if (chatMessage.getGroupName() != null) {
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            for (ChatMessage oldMsg : history) {
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                historyMsg.setRead(oldMsg.isRead());
                historyMsg.setReactions(oldMsg.getReactions());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());

        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } 
        else if (chatMessage.getType() == ChatMessage.MessageType.TYPING) {
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else if (chatMessage.getType() == ChatMessage.MessageType.READ) {
            ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
            if (existing != null) {
                existing.setRead(true);
                chatRepository.save(existing);
                messagingTemplate.convertAndSend("/topic/public", chatMessage);
            }
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
        else {
            if(chatMessage.getGroupName() != null) {
                chatRepository.save(chatMessage);
            }
            chatMessage.setType(ChatMessage.MessageType.CHAT);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
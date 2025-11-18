package com.example.Backend;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger; // For counting users

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final List<ChatMessage> chatHistory = new CopyOnWriteArrayList<>();
    
    // Thread-safe User Counter
    private static final AtomicInteger activeUsers = new AtomicInteger(0);

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        
        // Increment User Count
        int currentCount = activeUsers.incrementAndGet();

        // Notify Everyone
        ChatMessage joinMessage = new ChatMessage();
        joinMessage.setFrom(chatMessage.getFrom());
        joinMessage.setType(ChatMessage.MessageType.JOIN);
        joinMessage.setOnlineCount(currentCount); // Send count
        messagingTemplate.convertAndSend("/topic/public", joinMessage);

        // Send History (Private Whisper)
        String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
        if (chatMessage.getSenderId() != null) {
            for (ChatMessage oldMsg : chatHistory) {
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            chatHistory.clear();
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } 
        else if (chatMessage.getType() == ChatMessage.MessageType.TYPING) {
            // Just pass the typing signal through, don't save to history
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            // Normal Chat
            chatHistory.add(chatMessage);
            chatMessage.setType(ChatMessage.MessageType.CHAT);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
}
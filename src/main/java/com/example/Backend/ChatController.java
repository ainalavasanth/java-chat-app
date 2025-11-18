package com.example.Backend;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final List<ChatMessage> chatHistory = new CopyOnWriteArrayList<>();

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Add username to session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        
        // 2. Broadcast "User Joined" to EVERYONE
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. Send History ONLY to the new user (Private Channel)
        // We use the senderId sent by the client to target the message
        if (chatMessage.getSenderId() != null) {
            for (ChatMessage oldMsg : chatHistory) {
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                
                // Send to /topic/history/THE_USER_ID
                messagingTemplate.convertAndSend("/topic/history/" + chatMessage.getSenderId(), historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            chatHistory.clear();
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } else {
            chatHistory.add(chatMessage);
            chatMessage.setType(ChatMessage.MessageType.CHAT);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
}
package com.example.Backend;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // When a new user joins
    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // Put username in WebSocket session attributes
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        messagingTemplate.convertAndSend("/topic/public", chatMessage);
    }

    // When a user sends a chat message
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setType(ChatMessage.MessageType.CHAT);
        messagingTemplate.convertAndSend("/topic/public", chatMessage);
    }
}

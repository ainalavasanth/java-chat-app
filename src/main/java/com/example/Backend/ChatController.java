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
    
    // In-Memory Storage for Chat History (Global)
    private final List<ChatMessage> chatHistory = new CopyOnWriteArrayList<>();

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Add username to session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        
        // 2. Broadcast that user joined
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. Send HISTORY to the user (Loop through history and send individually or as a batch)
        // We send them individually so the frontend logic remains simple
        for (ChatMessage oldMsg : chatHistory) {
            // We change type to HISTORY so the frontend knows not to show a notification
            ChatMessage historyMsg = new ChatMessage();
            historyMsg.setFrom(oldMsg.getFrom());
            historyMsg.setText(oldMsg.getText());
            historyMsg.setType(ChatMessage.MessageType.HISTORY); 
            
            messagingTemplate.convertAndSend("/topic/public", historyMsg);
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            // If someone sends CLEAR, wipe the server memory
            chatHistory.clear();
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } else {
            // Normal message: Store it, then broadcast it
            chatHistory.add(chatMessage);
            chatMessage.setType(ChatMessage.MessageType.CHAT);
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
}
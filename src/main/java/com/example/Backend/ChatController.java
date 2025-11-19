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
    private final ChatRepository chatRepository; // <--- Database Connection

    // Keep track of live users in RAM (this is fine)
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    // Helper for the EventListener
    public int getActiveUserCount() { return activeSessions.size(); }
    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        activeSessions.add(headerAccessor.getSessionId());

        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 🚀 LOAD HISTORY FROM DATABASE
        if (chatMessage.getGroupName() != null) {
            // Find messages ONLY for this group
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            
            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            
            for (ChatMessage oldMsg : history) {
                // We copy it to a new object to change the type to HISTORY
                // This prevents the frontend from playing the "Ping" sound for old messages
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                historyMsg.setGroupName(oldMsg.getGroupName());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());

        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            // 🚀 DELETE FROM DATABASE
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        } 
        else if (chatMessage.getType() == ChatMessage.MessageType.TYPING) {
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        else {
            // 🚀 SAVE TO DATABASE
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
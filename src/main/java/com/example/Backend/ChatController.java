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

    private static final String SERVER_PIN = "1234"; 
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }
    public int getActiveUserCount() { return activeSessions.size(); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        activeSessions.add(headerAccessor.getSessionId());
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());

        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        if (chatMessage.getGroupName() != null) {
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            for (ChatMessage oldMsg : history) {
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                // Preserve the original type (IMAGE/VOICE/CHAT) so it renders correctly
                historyMsg.setType(oldMsg.getType()); 
                historyMsg.setReactions(oldMsg.getReactions());
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setRead(oldMsg.isRead());
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());
        chatMessage.setOnlineCount(activeSessions.size());

        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if (SERVER_PIN.equals(chatMessage.getText())) {
                if(chatMessage.getGroupName() != null) {
                    List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                    chatRepository.deleteAll(msgs);
                }
                messagingTemplate.convertAndSend("/topic/public", chatMessage);
            }
            return;
        } 
        
        // SAVE: Chat, Voice, AND IMAGES
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || 
            chatMessage.getType() == ChatMessage.MessageType.VOICE || 
            chatMessage.getType() == ChatMessage.MessageType.IMAGE) {
            
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
        else if (chatMessage.getType() == ChatMessage.MessageType.READ) {
             ChatMessage existing = chatRepository.findByMessageId(chatMessage.getMessageId());
             if(existing != null) { 
                 existing.setRead(true); 
                 chatRepository.save(existing); 
                 messagingTemplate.convertAndSend("/topic/public", chatMessage);
             }
        }
        else {
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
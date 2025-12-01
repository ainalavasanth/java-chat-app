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

    // RAM is ONLY for counting online users. Messages go to DB.
    private static final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatRepository chatRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatRepository = chatRepository;
    }

    public void removeSession(String sessionId) { activeSessions.remove(sessionId); }
    public int getActiveUserCount() { return activeSessions.size(); }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Session Tracking
        headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
        activeSessions.add(headerAccessor.getSessionId());

        // 2. Broadcast Join
        chatMessage.setTime(getCurrentTime());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setOnlineCount(activeSessions.size());
        messagingTemplate.convertAndSend("/topic/public", chatMessage);

        // 3. LOAD HISTORY FROM MONGODB (Crucial Step)
        if (chatMessage.getGroupName() != null) {
            System.out.println("📥 Fetching history for group: " + chatMessage.getGroupName());
            
            List<ChatMessage> history = chatRepository.findByGroupName(chatMessage.getGroupName());
            
            if (history.isEmpty()) {
                System.out.println("⚠️ No history found for this group in MongoDB.");
            }

            String uniqueUserChannel = "/topic/history/" + chatMessage.getSenderId();
            
            for (ChatMessage oldMsg : history) {
                // Send as HISTORY type so it loads quietly
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.setMessageId(oldMsg.getMessageId());
                historyMsg.setFrom(oldMsg.getFrom());
                historyMsg.setText(oldMsg.getText());
                historyMsg.setTime(oldMsg.getTime());
                historyMsg.setType(ChatMessage.MessageType.HISTORY); 
                historyMsg.setReactions(oldMsg.getReactions());
                historyMsg.setRead(oldMsg.isRead());
                // Preserve image types
                if (oldMsg.getType() == ChatMessage.MessageType.IMAGE || oldMsg.getType() == ChatMessage.MessageType.VOICE) {
                    historyMsg.setType(oldMsg.getType()); 
                }
                
                messagingTemplate.convertAndSend(uniqueUserChannel, historyMsg);
            }
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTime(getCurrentTime());
        chatMessage.setOnlineCount(activeSessions.size());

        // A. DELETE HISTORY
        if (chatMessage.getType() == ChatMessage.MessageType.CLEAR) {
            if(chatMessage.getGroupName() != null) {
                List<ChatMessage> msgs = chatRepository.findByGroupName(chatMessage.getGroupName());
                chatRepository.deleteAll(msgs);
                System.out.println("🗑️ Deleted all messages for group: " + chatMessage.getGroupName());
            }
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
            return;
        } 
        
        // B. SAVE MESSAGES (Text, Voice, Images)
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT || 
            chatMessage.getType() == ChatMessage.MessageType.VOICE || 
            chatMessage.getType() == ChatMessage.MessageType.IMAGE) {
            
            if(chatMessage.getGroupName() != null) {
                chatRepository.save(chatMessage);
                System.out.println("💾 Saved to MongoDB: " + chatMessage.getType());
            } else {
                System.out.println("❌ ERROR: Message has no Group Name. Not saving.");
            }
            
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
        // C. REACTIONS & READ STATUS (Update DB)
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
            // Typing, Calls, etc. (Pass through)
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
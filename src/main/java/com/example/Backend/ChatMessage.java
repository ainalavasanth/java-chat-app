package com.example.Backend;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "messages")
public class ChatMessage {
    
    @Id
    private String id;
    private String messageId;
    private String from;
    private String text; // Stores Text OR Base64 Audio Data OR WebRTC Signal
    private String senderId;
    private String time;
    private String groupName;
    private boolean isRead;
    private Map<String, String> reactions = new HashMap<>();
    private MessageType type;
    private int onlineCount;
    
    // For Calling (WebRTC)
    private String candidate; 
    private String sdp; 

    public enum MessageType { 
        CHAT, JOIN, LEAVE, HISTORY, CLEAR, TYPING, READ, REACTION, 
        VOICE, // <--- New: Voice Note
        offer, answer, candidate, hangup // <--- New: Calling Signals
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public Map<String, String> getReactions() { return reactions; }
    public void setReactions(Map<String, String> reactions) { this.reactions = reactions; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public int getOnlineCount() { return onlineCount; }
    public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; }
    
    // WebRTC Getters/Setters
    public String getCandidate() { return candidate; }
    public void setCandidate(String candidate) { this.candidate = candidate; }
    public String getSdp() { return sdp; }
    public void setSdp(String sdp) { this.sdp = sdp; }
}
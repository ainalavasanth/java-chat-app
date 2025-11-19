package com.example.Backend;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "messages") // <--- This tells MongoDB to create a "messages" table
public class ChatMessage {
    
    @Id
    private String id; // Unique DB ID
    private String from;
    private String text;
    private String senderId;
    private String time;
    private String groupName; 
    private MessageType type;
    private int onlineCount;

    public enum MessageType { CHAT, JOIN, LEAVE, HISTORY, CLEAR, TYPING }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public int getOnlineCount() { return onlineCount; }
    public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; }
}
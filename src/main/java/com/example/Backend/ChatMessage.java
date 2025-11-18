package com.example.Backend;

public class ChatMessage {
    private String from;
    private String text;
    private String senderId;
    private String time; // New: Stores the actual time
    private MessageType type;
    private int onlineCount;

    public enum MessageType {
        CHAT, JOIN, LEAVE, HISTORY, CLEAR, TYPING
    }

    // Getters and Setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getTime() { return time; } // New Getter
    public void setTime(String time) { this.time = time; } // New Setter

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public int getOnlineCount() { return onlineCount; }
    public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; }
}
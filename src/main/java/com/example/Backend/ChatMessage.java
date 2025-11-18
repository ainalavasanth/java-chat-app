package com.example.Backend;

public class ChatMessage {
    private String from;
    private String text;
    private String senderId;
    private MessageType type;
    private int onlineCount; // New: To send the live user count

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        HISTORY,
        CLEAR,
        TYPING // New: Typing status
    }

    // Getters and Setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public int getOnlineCount() { return onlineCount; } // New Getter
    public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; } // New Setter
}
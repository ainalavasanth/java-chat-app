package com.example.Backend;

public class ChatMessage {
    private String from;
    private String text;
    private String senderId; // New: Unique ID for the user
    private MessageType type;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        HISTORY,
        CLEAR
    }

    // Getters and Setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSenderId() { return senderId; } // New Getter
    public void setSenderId(String senderId) { this.senderId = senderId; } // New Setter

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
}
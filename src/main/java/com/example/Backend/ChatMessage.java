package com.example.Backend;

public class ChatMessage {
    private String from;
    private String text;
    private MessageType type;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        HISTORY, // New: To send list of old messages
        CLEAR    // New: To wipe the chat
    }

    // Getters and Setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
}
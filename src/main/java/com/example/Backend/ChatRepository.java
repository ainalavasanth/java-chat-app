package com.example.Backend;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    // This automagically creates a MongoDB query to find by group
    List<ChatMessage> findByGroupName(String groupName);
    
    // Helper to find a specific message (for reactions/read status)
    ChatMessage findByMessageId(String messageId);
}
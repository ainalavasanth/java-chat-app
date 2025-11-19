package com.example.Backend;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByGroupName(String groupName);
    
    // Find a specific message to React to or Mark as Read
    ChatMessage findByMessageId(String messageId);
}
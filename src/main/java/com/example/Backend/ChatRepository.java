package com.example.Backend;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByGroupName(String groupName);
    ChatMessage findByMessageId(String messageId);
}
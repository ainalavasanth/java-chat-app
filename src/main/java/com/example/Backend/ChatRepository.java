package com.example.Backend;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    // This finds all messages belonging to a specific group (e.g. "Family")
    List<ChatMessage> findByGroupName(String groupName);
}
package com.example.Backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Public endpoint to connect using SockJS or native WebSocket
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // messages sent to destinations prefixed with /app go to @MessageMapping handlers
        config.setApplicationDestinationPrefixes("/app");
        // simple in-memory broker that broadcasts to subscribers of /topic
        config.enableSimpleBroker("/topic");
    }
}

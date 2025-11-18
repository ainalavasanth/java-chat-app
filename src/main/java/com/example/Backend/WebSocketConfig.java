package com.example.Backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic");
    }

    // ⬇️ ADD THIS PART TO ALLOW SENDING IMAGES/VIDEOS ⬇️
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Allow messages up to 10MB (default is usually 64KB)
        registration.setMessageSizeLimit(10 * 1024 * 1024); 
        registration.setSendBufferSizeLimit(10 * 1024 * 1024);
        registration.setSendTimeLimit(20 * 10000);
    }
}
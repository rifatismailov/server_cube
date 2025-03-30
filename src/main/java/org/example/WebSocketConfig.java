package org.example;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ConcurrentHashMap<String, WebSocketSession> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> saveMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> clientStatus = new ConcurrentHashMap<>();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MessageWebSocketHandler(clients, saveMessages, clientStatus), "/ws").setAllowedOrigins("*");
    }
}


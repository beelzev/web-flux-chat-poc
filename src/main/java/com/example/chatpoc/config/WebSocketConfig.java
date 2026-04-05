package com.example.chatpoc.config;

import com.example.chatpoc.chat.ChatWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

/**
 * WebSocket 엔드포인트 매핑을 구성한다.
 */
@Configuration
public class WebSocketConfig {

    /**
     * 채팅 WebSocket 경로를 핸들러에 바인딩한다.
     *
     * @param chatWebSocketHandler 채팅 핸들러
     * @return URL 핸들러 매핑
     */
    @Bean
    HandlerMapping webSocketMapping(ChatWebSocketHandler chatWebSocketHandler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of("/ws/chat", chatWebSocketHandler));
        return mapping;
    }

    /**
     * WebFlux WebSocket 처리 어댑터를 등록한다.
     *
     * @return WebSocket 핸들러 어댑터
     */
    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

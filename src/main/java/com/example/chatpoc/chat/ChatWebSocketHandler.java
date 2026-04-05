package com.example.chatpoc.chat;

import com.example.chatpoc.auth.SessionUserRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.HttpCookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 단일 공용 채팅방의 WebSocket 입출력과 브로드캐스트를 처리한다.
 */
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final int MAX_PI_SCALE = 50;
    private static final long MAX_STEP_DELAY_MS = 3000L;
    private static final long DEFAULT_STEP_DELAY_MS = 120L;
    private static final int PI_NUMERATOR = 103993;
    private static final int PI_DENOMINATOR = 33102;
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final SessionUserRegistry sessionUserRegistry;
    private final ChatMessageValidator chatMessageValidator;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<ServerChatMessage> broadcaster = Sinks.many().multicast().directBestEffort();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> webSocketSessionsByHttpSession = new ConcurrentHashMap<>();

    /**
     * 핸들러 의존성을 초기화한다.
     *
     * @param sessionUserRegistry 세션-사용자 매핑 저장소
     * @param chatMessageValidator 메시지 유효성 검증기
     * @param objectMapper JSON 직렬화/역직렬화 도구
     */
    public ChatWebSocketHandler(
        SessionUserRegistry sessionUserRegistry,
        ChatMessageValidator chatMessageValidator,
        ObjectMapper objectMapper
    ) {
        this.sessionUserRegistry = sessionUserRegistry;
        this.chatMessageValidator = chatMessageValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * WebSocket 연결을 수립하고 수신 메시지를 전체 구독자에게 브로드캐스트한다.
     *
     * @param session 현재 WebSocket 세션
     * @return 핸들러 처리 결과
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = extractSessionId(session).orElse("");
        Optional<String> userIdCandidate = sessionUserRegistry.findUserIdBySessionId(sessionId);
        if (userIdCandidate.isEmpty()) {
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        String senderId = userIdCandidate.get();
        registerWebSocketSession(sessionId, session);

        Mono<Void> inbound = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(payload -> processIncomingMessage(payload, senderId, sessionId, session))
            .then();

        Flux<WebSocketMessage> outbound = broadcaster.asFlux()
            .map(this::toJson)
            .map(session::textMessage);

        return session.send(outbound)
            .and(inbound)
            .doFinally(signal -> unregisterWebSocketSession(sessionId, session));
    }

    /**
     * 클라이언트 메시지를 파싱하고 검증 후 브로드캐스트한다.
     *
     * @param payload 수신한 텍스트 프레임
     * @param senderId 발신자 사용자 ID
     * @return 처리 결과
     */
    private Mono<Void> processIncomingMessage(
        String payload,
        String senderId,
        String sessionId,
        WebSocketSession session
    ) {
        Optional<String> currentUser = sessionUserRegistry.findUserIdBySessionId(sessionId);
        if (currentUser.isEmpty() || currentUser.get().equals(senderId) == false) {
            return session.close(CloseStatus.POLICY_VIOLATION).then();
        }

        try {
            ClientChatMessage incoming = objectMapper.readValue(payload, ClientChatMessage.class);
            Optional<String> normalized = chatMessageValidator.validateAndNormalize(incoming.content());
            if (normalized.isEmpty()) {
                return Mono.empty();
            }

            String content = normalized.get();
            if (content.startsWith("!pi")) {
                startPiStream(senderId, content);
                return Mono.empty();
            }

            broadcaster.tryEmitNext(new ServerChatMessage(senderId, content, Instant.now(), null, false));
            return Mono.empty();
        } catch (JsonProcessingException ignored) {
            return Mono.empty();
        }
    }

    /**
     * 서버 메시지를 JSON 문자열로 변환한다.
     *
     * @param message 서버 메시지
     * @return JSON 문자열
     */
    private String toJson(ServerChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize outbound chat message", ex);
            return "{}";
        }
    }

    /**
     * 핸드셰이크 쿠키에서 세션 ID를 추출한다.
     *
     * @param session 현재 WebSocket 세션
     * @return 세션 ID(Optional)
     */
    private Optional<String> extractSessionId(WebSocketSession session) {
        List<String> cookieHeaders = session.getHandshakeInfo().getHeaders().get("Cookie");
        if (cookieHeaders == null || cookieHeaders.isEmpty()) {
            return Optional.empty();
        }

        for (String cookieHeader : cookieHeaders) {
            List<HttpCookie> parsed = HttpCookie.parse(cookieHeader);
            for (HttpCookie cookie : parsed) {
                if (cookie.getName().equals(SESSION_COOKIE_NAME)) {
                    return Optional.ofNullable(cookie.getValue());
                }
            }
        }

        return Optional.empty();
    }

    private void startPiStream(String senderId, String command) {
        PiCommand piCommand = parsePiCommand(command);
        int maxScale = Math.min(piCommand.scale(), MAX_PI_SCALE);

        String streamId = UUID.randomUUID().toString();
        AtomicInteger remainder = new AtomicInteger(PI_NUMERATOR % PI_DENOMINATOR);
        StringBuilder current = new StringBuilder();
        current.append(PI_NUMERATOR / PI_DENOMINATOR);
        if (maxScale > 0) {
            current.append(".");
        }

        broadcaster.tryEmitNext(new ServerChatMessage(senderId, current.toString(), Instant.now(), streamId, true));

        Flux.range(1, maxScale)
            .concatMap(step -> Mono.delay(Duration.ofMillis(piCommand.stepDelayMs())).doOnNext(tick -> {
                int nextDividend = remainder.get() * 10;
                int nextDigit = nextDividend / PI_DENOMINATOR;
                remainder.set(nextDividend % PI_DENOMINATOR);
                current.append(nextDigit);
                broadcaster.tryEmitNext(
                    new ServerChatMessage(senderId, current.toString(), Instant.now(), streamId, true)
                );
            }))
            .doOnError(ex -> log.warn("Pi stream failed for senderId={}", senderId, ex))
            .subscribe();
    }

    private PiCommand parsePiCommand(String command) {
        String[] tokens = command.trim().split("\\s+");
        int scale = 10;
        long delayMs = DEFAULT_STEP_DELAY_MS;

        if (tokens.length >= 2) {
            try {
                scale = Math.max(0, Integer.parseInt(tokens[1]));
            } catch (NumberFormatException ignored) {
                scale = 10;
            }
        }

        if (tokens.length >= 3) {
            try {
                double seconds = Double.parseDouble(tokens[2]);
                long parsedDelay = (long) (seconds * 1000L);
                delayMs = Math.max(0L, Math.min(parsedDelay, MAX_STEP_DELAY_MS));
            } catch (NumberFormatException ignored) {
                delayMs = DEFAULT_STEP_DELAY_MS;
            }
        }

        return new PiCommand(scale, delayMs);
    }

    private record PiCommand(int scale, long stepDelayMs) {
    }

    /**
     * 특정 HTTP 세션에 매달린 WebSocket 연결을 모두 끊는다.
     *
     * @param httpSessionId HTTP 세션 ID
     */
    public void disconnectByHttpSessionId(String httpSessionId) {
        Set<WebSocketSession> sessions = webSocketSessionsByHttpSession.remove(httpSessionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        for (WebSocketSession ws : sessions) {
            ws.close(CloseStatus.NORMAL).subscribe();
        }
    }

    private void registerWebSocketSession(String httpSessionId, WebSocketSession wsSession) {
        webSocketSessionsByHttpSession
            .computeIfAbsent(httpSessionId, ignored -> ConcurrentHashMap.newKeySet())
            .add(wsSession);
    }

    private void unregisterWebSocketSession(String httpSessionId, WebSocketSession wsSession) {
        Set<WebSocketSession> sessions = webSocketSessionsByHttpSession.get(httpSessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(wsSession);
        if (sessions.isEmpty()) {
            webSocketSessionsByHttpSession.remove(httpSessionId);
        }
    }
}

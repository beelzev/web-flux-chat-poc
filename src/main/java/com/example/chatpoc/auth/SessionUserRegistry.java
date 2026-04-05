package com.example.chatpoc.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 세션 ID와 사용자 ID 매핑을 메모리에서 관리한다.
 */
@Component
public class SessionUserRegistry {

    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    /**
     * 세션과 사용자 매핑을 등록하거나 갱신한다.
     *
     * @param sessionId 웹 세션 ID
     * @param userId 사용자 ID
     */
    public void register(String sessionId, String userId) {
        sessionToUser.put(sessionId, userId);
    }

    /**
     * 세션 ID로 사용자 ID를 조회한다.
     *
     * @param sessionId 웹 세션 ID
     * @return 사용자 ID(Optional)
     */
    public Optional<String> findUserIdBySessionId(String sessionId) {
        return Optional.ofNullable(sessionToUser.get(sessionId));
    }

    /**
     * 세션 매핑을 제거한다.
     *
     * @param sessionId 웹 세션 ID
     */
    public void remove(String sessionId) {
        sessionToUser.remove(sessionId);
    }
}

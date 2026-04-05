package com.example.chatpoc.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 요청 세션에서 사용자 정보를 읽어 레지스트리를 동기화하고,
 * 인증 없는 WebSocket 접속을 차단한다.
 */
@Component
public class SessionAuthenticationWebFilter implements WebFilter {

    private final SessionUserRegistry sessionUserRegistry;

    /**
     * 필터 의존성을 초기화한다.
     *
     * @param sessionUserRegistry 세션-사용자 매핑 저장소
     */
    public SessionAuthenticationWebFilter(SessionUserRegistry sessionUserRegistry) {
        this.sessionUserRegistry = sessionUserRegistry;
    }

    /**
     * 세션 사용자 정보를 반영하고 WebSocket 인증을 검사한다.
     *
     * @param exchange 현재 요청/응답 컨텍스트
     * @param chain 다음 필터 체인
     * @return 필터 처리 결과
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getSession().flatMap(webSession -> {
            Object userId = webSession.getAttributes().get(SessionConstants.USER_ID_ATTR);
            if (userId instanceof String value && value.isBlank() == false) {
                sessionUserRegistry.register(webSession.getId(), value);
            } else {
                sessionUserRegistry.remove(webSession.getId());
            }

            PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
            if (path.value().equals("/ws/chat") && userId == null) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        });
    }
}

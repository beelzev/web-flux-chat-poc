package com.example.chatpoc.auth;

import com.example.chatpoc.chat.ChatWebSocketHandler;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

/**
 * 인증 및 세션 관련 HTTP 엔드포인트를 제공한다.
 */
@RestController
public class AuthController {

    private final AccountService accountService;
    private final SessionUserRegistry sessionUserRegistry;
    private final ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 컨트롤러 의존성을 초기화한다.
     *
     * @param accountService 계정 서비스
     * @param sessionUserRegistry 세션-사용자 매핑 저장소
     */
    public AuthController(
        AccountService accountService,
        SessionUserRegistry sessionUserRegistry,
        ChatWebSocketHandler chatWebSocketHandler
    ) {
        this.accountService = accountService;
        this.sessionUserRegistry = sessionUserRegistry;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    /**
     * 회원가입 후 세션을 생성한다.
     *
     * @param request 회원가입 요청
     * @param session 웹 세션
     * @return 성공 시 사용자 정보, 실패 시 오류 응답
     */
    @PostMapping("/signup")
    public Mono<ResponseEntity<?>> signup(@Valid @RequestBody AuthRequest request, WebSession session) {
        return accountService.signup(request.userId(), request.password())
            .<ResponseEntity<?>>map(userId -> {
                session.getAttributes().put(SessionConstants.USER_ID_ATTR, userId);
                sessionUserRegistry.register(session.getId(), userId);
                return ResponseEntity.ok(new AuthResponse(userId));
            })
            .onErrorResume(IllegalStateException.class,
                ex -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()))));
    }

    /**
     * 로그인 성공 시 세션에 사용자 정보를 저장한다.
     *
     * @param request 로그인 요청
     * @param session 웹 세션
     * @return 성공 시 사용자 정보, 실패 시 오류 응답
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(@Valid @RequestBody AuthRequest request, WebSession session) {
        return accountService.login(request.userId(), request.password())
            .<ResponseEntity<?>>map(userId -> {
                session.getAttributes().put(SessionConstants.USER_ID_ATTR, userId);
                sessionUserRegistry.register(session.getId(), userId);
                return ResponseEntity.ok(new AuthResponse(userId));
            })
            .onErrorResume(IllegalArgumentException.class,
                ex -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(ex.getMessage()))));
    }

    /**
     * 현재 세션을 무효화하여 로그아웃한다.
     *
     * @param session 웹 세션
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(WebSession session) {
        sessionUserRegistry.remove(session.getId());
        chatWebSocketHandler.disconnectByHttpSessionId(session.getId());
        return session.invalidate().thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * 현재 로그인된 사용자 정보를 조회한다.
     *
     * @param session 웹 세션
     * @return 인증 상태에 따른 사용자 정보 또는 오류 응답
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<?>> me(WebSession session) {
        Object userId = session.getAttributes().get(SessionConstants.USER_ID_ATTR);
        if (userId instanceof String value && value.isBlank() == false) {
            return Mono.just(ResponseEntity.ok(new MeResponse(value)));
        }
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Not authenticated")));
    }

    /**
     * 가입된 전체 사용자 ID 목록을 조회한다.
     *
     * @return 사용자 ID 목록
     */
    @GetMapping("/users")
    public Mono<ResponseEntity<List<String>>> users() {
        return accountService.getAllUserIds()
            .collectList()
            .map(ResponseEntity::ok);
    }
}

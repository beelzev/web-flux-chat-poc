package com.example.chatpoc.auth;

import com.example.chatpoc.user.UserAccount;
import com.example.chatpoc.user.UserAccountRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Flux;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 계정 생성과 비밀번호 검증을 담당한다.
 */
@Service
public class AccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 서비스 의존성을 초기화한다.
     *
     * @param userAccountRepository 계정 저장소
     */
    public AccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 신규 계정을 생성한다.
     *
     * @param userId 사용자 ID
     * @param password 평문 비밀번호
     * @return 생성된 사용자 ID
     */
    public Mono<String> signup(String userId, String password) {
        String normalizedId = userId.trim();
        return userAccountRepository.findById(normalizedId)
            .flatMap(existing -> Mono.<String>error(new IllegalStateException("User already exists")))
            .switchIfEmpty(
                userAccountRepository.save(
                    new UserAccount(normalizedId, passwordEncoder.encode(password), Instant.now())
                ).map(UserAccount::userId)
            )
            .onErrorMap(DataIntegrityViolationException.class, ex -> new IllegalStateException("User already exists"));
    }

    /**
     * 사용자 ID/비밀번호 조합을 검증해 로그인 가능 여부를 판단한다.
     *
     * @param userId 사용자 ID
     * @param password 평문 비밀번호
     * @return 인증된 사용자 ID
     */
    public Mono<String> login(String userId, String password) {
        String normalizedId = userId.trim();
        return userAccountRepository.findById(normalizedId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid credentials")))
            .flatMap(account -> {
                boolean matched = passwordEncoder.matches(password, account.passwordHash());
                if (matched == false) {
                    return Mono.error(new IllegalArgumentException("Invalid credentials"));
                }
                return Mono.just(account.userId());
            });
    }

    /**
     * 전체 사용자 ID 목록을 조회한다.
     *
     * @return 사용자 ID 스트림
     */
    public Flux<String> getAllUserIds() {
        return userAccountRepository.findAllUserIds();
    }
}

---
name: webflux-review-v2
description: Activated when writing or reviewing Spring WebFlux / reactive code. Enforces reactive correctness: no blocking on event loop, Reactor Context over ThreadLocal, reactive HTTP client boundaries, WebFilter-based security, codec memory limits, Netty resource sharing, dependency conflicts, API versioning.
triggers:
  - WebFlux
  - Mono
  - Flux
  - WebClient
  - WebFilter
  - RouterFunction
  - R2DBC
  - spring-boot-starter-webflux
---

# WebFlux 리뷰 규칙

심각도: 🔴 반드시 수정 / 🟡 확인 요청 / 🔵 권고

---

## 🔴 1. End-to-End Reactive + Blocking 금지

두 항목을 함께 점검한다.

**blocking 여부 확인 후 사용자에게 질문:**
> "이 흐름 전체가 end-to-end reactive로 유지될 수 있나요? Blocking I/O가 있다면 알려주세요."

| 금지 패턴 | 대안 |
|-----------|------|
| `JdbcTemplate`, `JPA` | `R2DBC` |
| `block()` / `blockFirst()` / `blockLast()` | 체인 유지 |
| event loop에서 blocking I/O | `Schedulers.boundedElastic()`으로 격리 |

```java
// ✅ blocking 코드 격리
Mono.fromCallable(() -> blockingCall())
    .subscribeOn(Schedulers.boundedElastic());
```

---

## 🔴 2. ThreadLocal → Reactor Context / Micrometer Context Propagation

| 금지 | 대안 |
|------|------|
| `MDC.put/get` 직접 사용 | `contextWrite(Context.of(...))` |
| `SecurityContextHolder` | `ReactiveSecurityContextHolder` |
| `ThreadLocal` 커스텀 전달 | Reactor Context |

---

## 🔴 3. HTTP Service Client Reactive 경계

Spring Boot 4 `@HttpExchange` 사용 시 반환형을 반드시 `Mono`/`Flux`로 유지한다.
동기 반환형(`User`, `List<User>`)은 내부에서 `block()`을 유발해 "겉만 선언적, 안은 blocking" 구조가 된다.

- `@HttpExchange` 메서드 반환형: `Mono<T>` / `Flux<T>` 필수
- `WebClient` 체인 내 `.block()` 호출 금지
- `RestClient`(blocking) WebFlux 앱 혼용 금지

---

## 🔴 4. WebFlux 보안 설정 (WebFilter 기반)

MVC `Filter`/`HandlerInterceptor` 혼용 불가. `SecurityWebFilterChain` 사용.

- `@EnableWebFluxSecurity` 확인 (`@EnableWebSecurity`와 혼동 주의)
- `ServerSecurityContextRepository` 설정 (stateless 여부)
- 401/403 예외 핸들러 등록: `authenticationEntryPoint`, `accessDeniedHandler`
- REST API: `.csrf(ServerHttpSecurity.CsrfSpec::disable)` 명시

---

## 🟡 5. Codec / 메모리 설정

| 설정 범위 | 키 |
|-----------|----|
| 공통 HTTP codec (기본 256KB) | `spring.http.codecs.max-in-memory-size` |
| Multipart | `spring.webflux.multipart.max-in-memory-size` |
| 수동 생성 WebClient | `ExchangeStrategies` 직접 지정 필수 |

- `spring.http.codecs.*`는 auto-configured 인스턴스에만 적용됨. 수동 생성 `WebClient`는 별도 설정 필요.
- 한도 초과 시 `DataBufferLimitException`. 대용량은 `Flux<DataBuffer>` 스트리밍으로 처리.

---

## 🟡 6. Reactor Netty 자원 공유

- `WebClient`를 요청마다 신규 생성 금지 → 빈 등록 또는 `WebClient.Builder` 주입 재사용
- `ConnectionProvider`, `LoopResources` 공유 구조 확인
- 테스트 코드의 `WebClient` 매 생성 패턴 주의 (자원 누수)

---

## 🟡 7. 의존성 충돌

`spring-boot-starter-web` + `spring-boot-starter-webflux` 공존 시 Boot가 MVC(Tomcat)로 기동된다.

- 두 스타터 동시 존재 여부 확인
- WebFlux 전용이면 `spring-boot-starter-web` 제거
- 공존 불가피 시: `spring.main.web-application-type=reactive` 명시

---

## 🔵 8. API Versioning

- 버전 방식 일관성: URI `/v1/`, 헤더, 미디어타입 중 하나로 통일
- `RouterFunction` 버전별 경로 분리 여부
- deprecated 버전 처리 전략 존재 여부

---

## 응답 규칙

1. 해당 항목 번호와 심각도 이모지를 명시한다.
2. 🔴는 수정 코드를 함께 제시한다.
3. 🟡는 현황을 확인하고 문제면 수정 방향을 안내한다.
4. 🔵는 권고 사항으로 제시한다.
5. 코드에서 확인 불가능한 사항은 사용자에게 직접 질문한다.

---
name: webflux-review
description: Automatically activated when writing or reviewing Spring WebFlux code. Checks for reactive pitfalls including blocking calls, ThreadLocal misuse, HTTP client boundaries, and security configuration.
triggers:
  - WebFlux
  - reactive
  - Mono
  - Flux
  - RouterFunction
  - WebFilter
  - WebClient
  - Spring Reactive
---

# WebFlux 코드 작성/리뷰 체크리스트

WebFlux 관련 코드를 작성하거나 리뷰할 때 아래 5가지 항목을 반드시 점검한다.

---

## 1. End-to-End Reactive 여부 확인

전체 흐름이 reactive로 유지되는지 점검한다. Blocking 요소가 하나라도 있으면 reactive의 이점이 사라진다.

**점검 항목:**
- DB 접근: `JdbcTemplate`, `JPA` 등 blocking driver 사용 여부 → `R2DBC`로 대체 필요
- 파일 I/O, 외부 시스템 호출이 blocking인지 확인
- `block()`, `blockFirst()`, `blockLast()` 호출 위치 확인

**사용자에게 확인 요청할 것:**
> "이 흐름 전체가 end-to-end reactive로 유지될 수 있나요? Blocking I/O가 포함된 부분이 있다면 알려주세요."

---

## 2. Blocking API를 Event Loop에서 실행 금지

Netty event loop 스레드에서 blocking 호출은 전체 서버를 멈출 수 있다.

**금지 패턴:**
```java
// ❌ event loop에서 절대 금지
Mono.fromCallable(() -> jdbcTemplate.query(...))  // blocking
    .subscribe();

// ✅ boundedElastic 스케줄러로 격리
Mono.fromCallable(() -> jdbcTemplate.query(...))
    .subscribeOn(Schedulers.boundedElastic());
```

**점검 항목:**
- `Schedulers.boundedElastic()` 없이 blocking 코드를 `Mono.fromCallable`로 감싸는 패턴
- `@Blocking` 없이 blocking 메서드를 reactive 체인에서 직접 호출하는 경우

---

## 3. ThreadLocal 대신 Reactor Context / Micrometer Context Propagation 사용

Reactive 스트림은 여러 스레드를 넘나들므로 `ThreadLocal` 기반 처리는 값이 유실된다.

**대표 사례 — MDC 로깅:**
```java
// ❌ ThreadLocal 기반 MDC (reactive에서 유실됨)
MDC.put("traceId", traceId);

// ✅ Reactor Context + Micrometer Context Propagation
return chain.filter(exchange)
    .contextWrite(Context.of("traceId", traceId));
```

**점검 항목:**
- `MDC.put/get` 직접 사용 여부
- Spring Security의 `SecurityContextHolder` 직접 참조 (WebFlux에서는 `ReactiveSecurityContextHolder` 사용)
- `ThreadLocal` 기반 커스텀 컨텍스트 전달 패턴

---

## 4. HTTP Service Client의 Reactive 경계 명확화

Spring Boot 4의 HTTP Service Client auto-configuration은 편리하지만, 반환형을 잘못 설계하면 "겉만 선언적이고 안은 blocking"인 구조가 된다.

**경계 기준:**
```java
// ❌ WebFlux 앱에서 blocking 반환형
@HttpExchange("/users/{id}")
User getUser(@PathVariable String id);  // 내부에서 block() 발생

// ✅ reactive 반환형 유지
@HttpExchange("/users/{id}")
Mono<User> getUser(@PathVariable String id);
```

**점검 항목:**
- `@HttpExchange` 인터페이스 메서드의 반환형이 `Mono`/`Flux`인지
- `WebClient` 체인 중간에 `.block()` 호출 여부
- `RestClient`(blocking)를 WebFlux 앱에서 혼용하는 경우

---

## 5. WebFlux 보안 설정 점검 (WebFilter 기반)

WebFlux 보안은 `WebFilter` 기반으로 동작한다. MVC의 `Filter`/`HandlerInterceptor` 방식과 다르다.

**점검 항목:**
```java
// ✅ WebFlux 보안 설정 기본 구조
@Bean
SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/public/**").permitAll()
            .anyExchange().authenticated())
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()) // stateless
        .exceptionHandling(e -> e
            .authenticationEntryPoint(...)   // 401 처리
            .accessDeniedHandler(...))       // 403 처리
        .build();
}
```

- `ServerSecurityContextRepository` 설정 확인 (세션 vs stateless)
- 인증 실패(`401`), 인가 실패(`403`) 예외 핸들러 등록 여부
- `ReactiveSecurityContextHolder`로 보안 컨텍스트 접근하는지 확인
- `@EnableWebFluxSecurity` 사용 여부 (`@EnableWebSecurity`와 혼동 주의)
- CSRF 설정: REST API라면 `.csrf(ServerHttpSecurity.CsrfSpec::disable)` 명시 필요

---

## 6. Codec / 메모리 설정 점검

Spring Boot 4 + WebFlux에서 codec / 메모리 설정은 3군데로 나눠서 점검한다.

| 설정 범위 | 키 / 방법 |
|-----------|-----------|
| 공통 HTTP codec 버퍼 한도 | `spring.http.codecs.max-in-memory-size` (기본 256KB) |
| Multipart 업로드 한도 | `spring.webflux.multipart.max-in-memory-size` |
| WebClient 개별 한도 | `WebClient.Builder` 빌드 시 `ExchangeStrategies` 직접 지정 |

**핵심:**
- `spring.http.codecs.max-in-memory-size`는 auto-configured WebFlux 서버와 WebClient에만 적용된다. 수동으로 생성한 `WebClient`는 적용되지 않으므로 개별 설정 필요.
- 한도를 초과하면 `DataBufferLimitException` 발생. 대용량 요청/응답이 있는 경우 반드시 값을 조정하거나 스트리밍(`Flux<DataBuffer>`)으로 처리할 것.

**점검 항목:**
- `DataBufferLimitException` 발생 가능성이 있는 엔드포인트(파일 업로드, 대용량 JSON) 확인
- 수동 생성 `WebClient`에 `ExchangeStrategies` 설정 누락 여부
- multipart 처리 시 메모리 vs 디스크 임계값 설정 여부

---

## 7. Reactor Netty 서버/클라이언트 자원 공유 점검

`WebClient`를 매 요청마다 새로 생성하면 Reactor Netty의 커넥션 풀과 이벤트 루프 자원이 공유되지 않아 자원 낭비가 발생한다.

**점검 항목:**
- `WebClient`는 빈으로 등록하거나 `WebClient.Builder`를 주입받아 재사용하는지 확인
- `HttpClient` / `TcpClient` 커스텀 시 `ConnectionProvider`와 `LoopResources`를 공유하는 구조인지 확인
- 테스트 코드에서 `WebClient`를 매번 생성해 자원 누수가 발생하는 패턴 여부

---

## 8. 의존성 충돌 점검 (spring-boot-starter-web vs webflux)

`spring-boot-starter-web`과 `spring-boot-starter-webflux`가 동시에 존재하면 Spring Boot는 기본적으로 MVC(Tomcat)로 기동된다. WebFlux로 동작하려면 명시적 설정이 필요하다.

**점검 항목:**
- `build.gradle` / `pom.xml`에 두 스타터가 함께 있는지 확인
- 함께 사용해야 할 이유가 없다면 `spring-boot-starter-web` 제거
- 불가피하게 공존할 경우 `spring.main.web-application-type=reactive` 명시 여부 확인

---

## 9. API Versioning 확인

WebFlux에서 API 버전 관리 방식이 일관성 없으면 라우팅 충돌이나 유지보수 문제가 생긴다.

**점검 항목:**
- 버전 관리 방식이 일관된지 확인 (URI 경로 `/v1/`, 헤더 `Accept-Version`, 미디어타입 중 하나로 통일)
- `RouterFunction` 기반 라우팅에서 버전별 경로가 명확히 분리되어 있는지
- 버전 전환 시 하위 호환성 유지 여부 (deprecated 버전 처리 전략)

---

## 리뷰 시 기본 응답 형식

WebFlux 코드를 검토할 때 위 항목 중 해당하는 항목을 명시하고, 문제가 있으면 수정 코드를 제시한다. 불명확한 부분은 사용자에게 확인을 요청한다.

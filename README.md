# Chat PoC (Spring Boot 4 + Java 25 + WebFlux)

단일 Docker 서버에서 동작하는 그룹 채팅 PoC입니다.

## 기능
- 웹 화면 제공: 회원가입, 로그인, 채팅
- 세션+쿠키 기반 인증 (`POST /signup`, `POST /login`, `POST /logout`, `GET /me`)
- H2 file mode 기반 계정 저장 (비밀번호 BCrypt 해시)
- WebSocket(`/ws/chat`) 기반 실시간 브로드캐스트
- 채팅 메시지는 서버에 저장하지 않음 (재접속 시 과거 메시지 없음)

## 로컬 개발 실행
Java 25/Gradle 환경이 준비되어 있으면 아래로 실행할 수 있습니다.

```bash
./gradlew bootRun
```

## Docker 실행
```bash
docker build -t chat-poc .
docker run --rm -p 8080:8080 -v $(pwd)/.data:/data chat-poc
```

브라우저에서 `http://localhost:8080` 접속 후 테스트합니다.

## WebSocket payload
클라이언트 전송:
```json
{ "content": "hello" }
```

서버 브로드캐스트:
```json
{ "senderId": "alice", "content": "hello", "sentAt": "2026-04-04T00:00:00Z" }
```

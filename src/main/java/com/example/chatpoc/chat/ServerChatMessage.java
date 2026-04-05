package com.example.chatpoc.chat;

import java.time.Instant;

/**
 * 서버가 브로드캐스트하는 채팅 메시지 본문이다.
 */
public record ServerChatMessage(
    String senderId,
    String content,
    Instant sentAt,
    String streamId,
    boolean append
) {
}

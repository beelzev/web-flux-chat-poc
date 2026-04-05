package com.example.chatpoc.chat;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 채팅 메시지의 기본 유효성 검사를 담당한다.
 */
@Component
public class ChatMessageValidator {

    /**
     * 메시지를 검증하고 전송 가능한 형태로 정규화한다.
     *
     * @param rawContent 원본 메시지
     * @return 유효할 경우 정규화된 메시지(Optional)
     */
    public Optional<String> validateAndNormalize(String rawContent) {
        if (rawContent == null) {
            return Optional.empty();
        }

        String trimmed = rawContent.trim();
        if (trimmed.isBlank()) {
            return Optional.empty();
        }

        if (trimmed.length() > 500) {
            return Optional.empty();
        }

        return Optional.of(trimmed);
    }
}

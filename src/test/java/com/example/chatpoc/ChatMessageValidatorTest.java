package com.example.chatpoc;

import com.example.chatpoc.chat.ChatMessageValidator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatMessageValidator 단위 테스트다.
 */
class ChatMessageValidatorTest {

    private final ChatMessageValidator validator = new ChatMessageValidator();

    /**
     * 공백이 포함된 정상 메시지는 트림 후 통과하는지 검증한다.
     */
    @Test
    void shouldAcceptTrimmedMessageWithinLimit() {
        assertThat(validator.validateAndNormalize("  hello  ")).contains("hello");
    }

    /**
     * 공백만 있는 메시지는 거절하는지 검증한다.
     */
    @Test
    void shouldRejectBlankMessage() {
        assertThat(validator.validateAndNormalize("   ")).isEmpty();
    }

    /**
     * 최대 길이를 초과한 메시지는 거절하는지 검증한다.
     */
    @Test
    void shouldRejectMessageOverLimit() {
        String tooLong = "a".repeat(501);
        assertThat(validator.validateAndNormalize(tooLong)).isEmpty();
    }
}

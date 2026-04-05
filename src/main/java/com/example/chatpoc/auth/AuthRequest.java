package com.example.chatpoc.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입/로그인 요청 본문이다.
 */
public record AuthRequest(
    @NotBlank
    @Size(min = 3, max = 50)
    String userId,
    @NotBlank
    @Size(min = 4, max = 100)
    String password
) {
}

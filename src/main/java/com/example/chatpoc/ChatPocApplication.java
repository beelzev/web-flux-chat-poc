package com.example.chatpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chat PoC 애플리케이션의 진입점이다.
 */
@SpringBootApplication
public class ChatPocApplication {

    /**
     * Spring Boot 애플리케이션을 시작한다.
     *
     * @param args 실행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatPocApplication.class, args);
    }
}

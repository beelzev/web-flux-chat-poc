package com.example.chatpoc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 관련 빈을 등록한다.
 */
@Configuration
public class JacksonConfig {

    /**
     * 애플리케이션에서 공통으로 사용하는 ObjectMapper 빈을 등록한다.
     *
     * @return ObjectMapper 인스턴스
     */
    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}

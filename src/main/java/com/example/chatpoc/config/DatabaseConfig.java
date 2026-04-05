package com.example.chatpoc.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

/**
 * R2DBC 초기 스키마를 적용하는 데이터베이스 설정이다.
 */
@Configuration
public class DatabaseConfig {

    /**
     * 애플리케이션 시작 시 schema.sql을 실행하도록 초기화기를 등록한다.
     *
     * @param connectionFactory R2DBC 연결 팩토리
     * @return 연결 팩토리 초기화기
     */
    @Bean
    ConnectionFactoryInitializer connectionFactoryInitializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }
}

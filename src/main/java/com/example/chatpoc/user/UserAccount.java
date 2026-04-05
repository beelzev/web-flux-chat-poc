package com.example.chatpoc.user;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 사용자 계정 정보를 저장하는 R2DBC 엔티티다.
 */
@Table("users")
public record UserAccount(
    @Id
    @Column("user_id")
    String userId,
    @Column("password_hash")
    String passwordHash,
    @Column("created_at")
    Instant createdAt
) {
}

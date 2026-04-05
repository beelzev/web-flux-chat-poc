package com.example.chatpoc.user;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * 사용자 계정 CRUD를 제공하는 반응형 저장소다.
 */
public interface UserAccountRepository extends ReactiveCrudRepository<UserAccount, String> {

    /**
     * 사용자 ID 전체 목록을 직접 조회한다.
     *
     * @return 사용자 ID 스트림
     */
    @Query("SELECT \"user_id\" FROM \"users\" ORDER BY \"created_at\" ASC")
    Flux<String> findAllUserIds();
}

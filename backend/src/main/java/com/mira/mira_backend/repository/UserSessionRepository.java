package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository
        extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHash(
            String refreshTokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM UserSession s
            WHERE s.id = :sessionId
            """)
    Optional<UserSession> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );
}

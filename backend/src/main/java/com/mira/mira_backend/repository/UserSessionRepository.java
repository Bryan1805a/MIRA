package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    // finds the session by the current refresh-token hash
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM UserSession s
            WHERE s.refreshTokenHash = :refreshTokenHash
            """)
    Optional<UserSession> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash
);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE UserSession s
            SET s.revokedAt = :revokedAt
            WHERE s.user.id = :userId
              AND s.revokedAt IS NULL
            """)
    int revokeAllActiveForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt
    );
}

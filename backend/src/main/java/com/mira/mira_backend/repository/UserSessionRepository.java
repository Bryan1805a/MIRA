package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository
        extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHash(
            String refreshTokenHash
    );
}

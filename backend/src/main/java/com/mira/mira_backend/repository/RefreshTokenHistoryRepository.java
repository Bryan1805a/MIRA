package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.RefreshTokenHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenHistoryRepository
        extends JpaRepository<RefreshTokenHistory, UUID> {

    Optional<RefreshTokenHistory> findByTokenHash(String tokenHash);
}

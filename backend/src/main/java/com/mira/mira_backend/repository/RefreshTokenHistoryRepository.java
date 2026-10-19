package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.RefreshTokenHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenHistoryRepository
        extends JpaRepository<RefreshTokenHistory, UUID> {

    Optional<RefreshTokenHistory> findByTokenHash(String tokenHash);

    @Query("""
            SELECT h.session.id
            FROM RefreshTokenHistory h
            WHERE h.tokenHash = :tokenHash
            """)
    Optional<UUID> findSessionIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );
}

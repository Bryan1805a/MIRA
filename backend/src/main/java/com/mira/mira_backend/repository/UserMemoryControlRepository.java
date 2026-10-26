package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.UserMemoryControl;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserMemoryControlRepository
        extends JpaRepository<UserMemoryControl, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM UserMemoryControl c
            WHERE c.userId = :userId
            """)
    Optional<UserMemoryControl> findByIdForUpdate(
            @Param("userId") UUID userId
    );

    @Modifying
    @Query(value = """
            INSERT INTO user_memory_control (user_id)
            VALUES (:userId)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int ensureExists(@Param("userId") UUID userId);
}
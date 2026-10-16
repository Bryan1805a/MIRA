package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    @Query("""
            SELECT u
            FROM UserAccount u
            WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))
            """)
    Optional<UserAccount> findByNormalizedEmail(
            @Param("email") String email
    );
}

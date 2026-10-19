package com.mira.mira_backend.service;

import com.mira.mira_backend.entity.RefreshTokenHistory;
import com.mira.mira_backend.entity.UserSession;
import com.mira.mira_backend.repository.RefreshTokenHistoryRepository;
import com.mira.mira_backend.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenTransactionService {

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenHistoryRepository historyRepository;
    private final TokenService tokenService;

    public RefreshTokenTransactionService(
            UserSessionRepository userSessionRepository,
            RefreshTokenHistoryRepository historyRepository,
            TokenService tokenService
    ) {
        this.userSessionRepository = userSessionRepository;
        this.historyRepository = historyRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public RotationResult rotate(String presentedRefreshToken) {
        Objects.requireNonNull(
                presentedRefreshToken,
                "presentedRefreshToken must not be null"
        );

        String presentedHash =
                tokenService.hashRefreshToken(presentedRefreshToken);

        Optional<UUID> sessionId =
                historyRepository.findSessionIdByTokenHash(presentedHash);

        if (sessionId.isEmpty()) {
            return RotationResult.rejected();
        }

        Optional<UserSession> lockedSession =
                userSessionRepository.findByIdForUpdate(sessionId.get());

        if (lockedSession.isEmpty()) {
            return RotationResult.rejected();
        }

        UserSession session = lockedSession.get();

        // Re-read history only after acquiring the session lock.
        Optional<RefreshTokenHistory> historyEntry =
                historyRepository.findByTokenHash(presentedHash);

        if (historyEntry.isEmpty()) {
            return RotationResult.rejected();
        }

        RefreshTokenHistory history = historyEntry.get();
        Instant now = Instant.now();

        // A consumed token is a replay. Return normally so revocation commits.
        if (history.getConsumedAt() != null) {
            session.revoke(now);
            return RotationResult.replayDetected();
        }

        // Fail closed if the history and current session state disagree.
        if (!presentedHash.equals(session.getRefreshTokenHash())) {
            session.revoke(now);
            return RotationResult.replayDetected();
        }

        if (session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now)) {
            return RotationResult.rejected();
        }

        String newRefreshToken = tokenService.createRefreshToken();
        String newRefreshTokenHash =
                tokenService.hashRefreshToken(newRefreshToken);

        history.consume(now);
        session.rotateRefreshTokenHash(newRefreshTokenHash, now);

        RefreshTokenHistory nextHistory = new RefreshTokenHistory(
                session,
                newRefreshTokenHash,
                now
        );

        historyRepository.saveAndFlush(nextHistory);

        TokenService.AccessToken accessToken =
                tokenService.createAccessToken(session.getUser().getId());

        return RotationResult.rotated(
                session.getUser().getId(),
                session.getUser().getEmail(),
                session.getId(),
                accessToken,
                newRefreshToken,
                session.getExpiresAt()
        );
    }

    public enum Status {
        ROTATED,
        REJECTED,
        REPLAY_DETECTED
    }

    public record RotationResult(
            Status status,
            UUID userId,
            String email,
            UUID sessionId,
            TokenService.AccessToken accessToken,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
        static RotationResult rejected() {
            return new RotationResult(
                    Status.REJECTED, null, null, null, null, null, null
            );
        }

        static RotationResult replayDetected() {
            return new RotationResult(
                    Status.REPLAY_DETECTED, null, null, null, null, null, null
            );
        }

        static RotationResult rotated(
                UUID userId,
                String email,
                UUID sessionId,
                TokenService.AccessToken accessToken,
                String refreshToken,
                Instant refreshTokenExpiresAt
        ) {
            return new RotationResult(
                    Status.ROTATED,
                    userId,
                    email,
                    sessionId,
                    accessToken,
                    refreshToken,
                    refreshTokenExpiresAt
            );
        }
    }
}

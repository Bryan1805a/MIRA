package com.mira.mira_backend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenTransactionService transactionService;

    public RefreshTokenService(
            RefreshTokenTransactionService transactionService
    ) {
        this.transactionService = transactionService;
    }

    public RefreshResult refresh(String refreshToken) {
        if (refreshToken == null
                || refreshToken.isBlank()
                || refreshToken.length() > 128) {
            throw new InvalidCredentialsException();
        }

        RefreshTokenTransactionService.RotationResult result =
                transactionService.rotate(refreshToken);

        if (result.status()
                != RefreshTokenTransactionService.Status.ROTATED) {
            // The transaction has returned and committed before this error.
            throw new InvalidCredentialsException();
        }

        return new RefreshResult(
                result.userId(),
                result.email(),
                result.sessionId(),
                result.accessToken().value(),
                result.accessToken().expiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt()
        );
    }

    public record RefreshResult(
            UUID userId,
            String email,
            UUID sessionId,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
    }
}

package com.mira.mira_backend.service;

import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.entity.UserSession;
import com.mira.mira_backend.repository.UserAccountRepository;
import com.mira.mira_backend.repository.UserSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Service
public class AuthenticationService {

    private static final long REFRESH_TOKEN_DAYS = 30;

    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        if (email == null || email.isBlank()
                || rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidCredentialsException();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        UserAccount account = userAccountRepository
                .findByNormalizedEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(
                rawPassword,
                account.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        TokenService.AccessToken accessToken =
                tokenService.createAccessToken(account.getId());

        String refreshToken = tokenService.createRefreshToken();
        String refreshTokenHash =
                tokenService.hashRefreshToken(refreshToken);

        Instant refreshTokenExpiresAt = Instant.now()
                .plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS);

        UserSession session = new UserSession(
                account,
                refreshTokenHash,
                refreshTokenExpiresAt
        );

        UserSession savedSession =
                userSessionRepository.saveAndFlush(session);

        return new LoginResult(
                account.getId(),
                account.getEmail(),
                savedSession.getId(),
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken,
                refreshTokenExpiresAt
        );
    }

    public record LoginResult(
            java.util.UUID userId,
            String email,
            java.util.UUID sessionId,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
        public LoginResult {
            Objects.requireNonNull(userId);
            Objects.requireNonNull(email);
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(accessToken);
            Objects.requireNonNull(accessTokenExpiresAt);
            Objects.requireNonNull(refreshToken);
            Objects.requireNonNull(refreshTokenExpiresAt);
        }
    }
}

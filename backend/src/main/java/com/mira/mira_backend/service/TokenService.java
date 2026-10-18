package com.mira.mira_backend.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class TokenService {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final long ACCESS_TOKEN_MINUTES = 15;

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            @Value("${mira.jwt.secret-base64}") String secretBase64,
            @Value("${mira.jwt.issuer:mira-backend}") String issuer
    ) {
        Objects.requireNonNull(secretBase64, "JWT secret must not be null");
        this.issuer = Objects.requireNonNull(
                issuer,
                "JWT issuer must not be null"
        );

        if (issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }

        final byte[] secretBytes;

        try {
            secretBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "MIRA_JWT_SECRET_BASE64 must contain valid Base64",
                    exception
            );
        }

        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT signing secret must contain at least 32 random bytes"
            );
        }

        SecretKey secretKey = new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );

        this.jwtEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(secretKey)
        );
    }

    public AccessToken createAccessToken(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(
                ACCESS_TOKEN_MINUTES,
                ChronoUnit.MINUTES
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new AccessToken(token, expiresAt);
    }

    public String createRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    public String hashRefreshToken(String refreshToken) {
        Objects.requireNonNull(
                refreshToken,
                "refreshToken must not be null"
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    refreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            // SHA-256 is required by every standard Java implementation.
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    public record AccessToken(
            String value,
            Instant expiresAt
    ) {
        public AccessToken {
            Objects.requireNonNull(value, "value must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
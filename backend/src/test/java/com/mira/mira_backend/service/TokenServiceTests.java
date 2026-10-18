package com.mira.mira_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import java.time.temporal.ChronoUnit;


class TokenServiceTests {

    private static final String TEST_SECRET_BASE64 =
            "VGhpc0lzT25seUZvclRlc3RzTm90UHJvZHVjdGlvbiFLZXk=";

    private TokenService tokenService;
    private NimbusJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        byte[] secretBytes = Base64.getDecoder()
                .decode(TEST_SECRET_BASE64);

        SecretKey secretKey = new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );

        tokenService = new TokenService(
                TEST_SECRET_BASE64,
                "https://mira-backend"
        );

        jwtDecoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Test
    void accessTokenShouldContainExpectedClaimsAndExpireIn15Minutes() {
        UUID userId = UUID.randomUUID();
        Instant beforeCreation = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        TokenService.AccessToken result =
                tokenService.createAccessToken(userId);

        Instant afterCreation = Instant.now();
        Jwt jwt = jwtDecoder.decode(result.value());

        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals("https://mira-backend", jwt.getIssuer().toString());
        assertNotNull(jwt.getId());
        assertEquals(result.expiresAt(), jwt.getExpiresAt());

        assertNotNull(jwt.getIssuedAt());
        assertFalse(jwt.getIssuedAt().isBefore(beforeCreation));
        assertFalse(jwt.getIssuedAt().isAfter(afterCreation));

        assertEquals(
                15,
                Duration.between(
                        jwt.getIssuedAt(),
                        jwt.getExpiresAt()
                ).toMinutes()
        );
    }

    @Test
    void refreshTokensShouldBeUniqueAndUrlSafe() {
        String first = tokenService.createRefreshToken();
        String second = tokenService.createRefreshToken();

        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertEquals(43, first.length());
    }

    @Test
    void refreshTokenHashShouldBe64HexCharactersAndDeterministic() {
        String refreshToken = tokenService.createRefreshToken();

        String firstHash =
                tokenService.hashRefreshToken(refreshToken);
        String secondHash =
                tokenService.hashRefreshToken(refreshToken);

        assertEquals(firstHash, secondHash);
        assertEquals(64, firstHash.length());
        assertTrue(firstHash.matches("[0-9a-f]{64}"));
    }

    @Test
    void differentRefreshTokensShouldHaveDifferentHashes() {
        String firstHash = tokenService.hashRefreshToken(
                tokenService.createRefreshToken()
        );
        String secondHash = tokenService.hashRefreshToken(
                tokenService.createRefreshToken()
        );

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void constructorShouldRejectShortSigningSecret() {
        String shortSecret = Base64.getEncoder()
                .encodeToString(new byte[16]);

        assertThrows(
                IllegalStateException.class,
                () -> new TokenService(shortSecret, "https://mira-backend")
        );
    }
}
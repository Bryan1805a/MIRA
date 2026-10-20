package com.mira.mira_backend.config;

import com.mira.mira_backend.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtConfigurationTests {

    private static final String SECRET =
            "VGhpc0lzT25seUZvclRlc3RzTm90UHJvZHVjdGlvbiFLZXk=";
    private static final String ISSUER = "https://mira-backend";

    @Test
    void decoderAcceptsTokenCreatedByTokenService() {
        JwtConfiguration configuration = new JwtConfiguration();

        SecretKey key = configuration.jwtSigningKey(SECRET);
        JwtDecoder decoder = configuration.jwtDecoder(key, ISSUER);

        TokenService tokenService = new TokenService(SECRET, ISSUER);
        UUID userId = UUID.randomUUID();

        TokenService.AccessToken token =
                tokenService.createAccessToken(userId);

        Jwt jwt = decoder.decode(token.value());

        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals(ISSUER, jwt.getIssuer().toString());
    }
}
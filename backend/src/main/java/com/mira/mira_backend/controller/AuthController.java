package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.RegisterRequest;
import com.mira.mira_backend.dto.RegisterResponse;
import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.service.UserAccountService;
import com.mira.mira_backend.dto.LoginRequest;
import com.mira.mira_backend.dto.LoginResponse;
import com.mira.mira_backend.service.AuthenticationService;
import com.mira.mira_backend.dto.RefreshRequest;
import com.mira.mira_backend.dto.RefreshResponse;
import com.mira.mira_backend.service.RefreshTokenService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
        UserAccountService userAccountService,
        AuthenticationService authenticationService,
        RefreshTokenService refreshTokenService) {
                this.userAccountService = userAccountService;
                this.authenticationService = authenticationService;
                this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserAccount account = userAccountService.register(
                request.email(),
                request.password()
        );

        RegisterResponse response = new RegisterResponse(
                account.getId(),
                account.getEmail(),
                account.getCreatedAt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthenticationService.LoginResult result =
                authenticationService.login(
                        request.email(),
                        request.password()
                );
    
        LoginResponse response = new LoginResponse(
                result.userId(),
                result.email(),
                result.sessionId(),
                "Bearer",
                result.accessToken(),
                result.accessTokenExpiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt()
        );
    
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        RefreshTokenService.RefreshResult result =
                refreshTokenService.refresh(request.refreshToken());
    
        RefreshResponse response = new RefreshResponse(
                result.userId(),
                result.email(),
                result.sessionId(),
                "Bearer",
                result.accessToken(),
                result.accessTokenExpiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt()
        );
    
        return ResponseEntity.ok(response);
    }
}

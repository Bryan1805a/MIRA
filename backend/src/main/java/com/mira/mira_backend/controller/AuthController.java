package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.RegisterRequest;
import com.mira.mira_backend.dto.RegisterResponse;
import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.service.UserAccountService;
import com.mira.mira_backend.dto.LoginRequest;
import com.mira.mira_backend.dto.LoginResponse;
import com.mira.mira_backend.service.AuthenticationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;

    public AuthController(
        UserAccountService userAccountService,
        AuthenticationService authenticationService) {
                this.userAccountService = userAccountService;
                this.authenticationService = authenticationService;
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
}

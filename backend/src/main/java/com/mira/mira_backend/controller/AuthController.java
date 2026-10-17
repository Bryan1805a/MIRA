package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.RegisterRequest;
import com.mira.mira_backend.dto.RegisterResponse;
import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.service.UserAccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
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
}

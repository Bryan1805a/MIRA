package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.CurrentUserResponse;
import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.service.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final UserAccountService userAccountService;

    public MeController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        UserAccount account = userAccountService.getById(userId);

        return ResponseEntity.ok(
                new CurrentUserResponse(
                        account.getId(),
                        account.getEmail(),
                        account.getCreatedAt()
                )
        );
    }
}
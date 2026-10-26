package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.MemorySettingsResponse;
import com.mira.mira_backend.dto.StyleProfileResponse;
import com.mira.mira_backend.dto.UpdateMemorySettingsRequest;
import com.mira.mira_backend.dto.UpdateStyleProfileRequest;
import com.mira.mira_backend.service.StyleProfileService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/style")
public class MemoryController {

    private final StyleProfileService styleProfileService;

    public MemoryController(
            StyleProfileService styleProfileService
    ) {
        this.styleProfileService = styleProfileService;
    }

    @GetMapping
    public StyleProfileResponse get(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return styleProfileService.get(userId(jwt));
    }

    @PutMapping
    public StyleProfileResponse replace(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStyleProfileRequest request
    ) {
        return styleProfileService.replace(
                userId(jwt),
                request.expectedVersion(),
                request.profile()
        );
    }

    @PatchMapping("/settings")
    public MemorySettingsResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMemorySettingsRequest request
    ) {
        return styleProfileService.updateSettings(
                userId(jwt),
                request.enabled()
        );
    }

    @DeleteMapping
    public ResponseEntity<Void> purge(
            @AuthenticationPrincipal Jwt jwt
    ) {
        styleProfileService.purge(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
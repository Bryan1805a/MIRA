package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.MemoryCandidateListResponse;
import com.mira.mira_backend.dto.StyleProfileResponse;
import com.mira.mira_backend.service.MemoryCandidateService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/candidates")
public class MemoryCandidateController {

    private final MemoryCandidateService candidateService;

    public MemoryCandidateController(
            MemoryCandidateService candidateService
    ) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public MemoryCandidateListResponse list(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return candidateService.list(userId(jwt));
    }

    @PostMapping("/{candidateId}/accept")
    public StyleProfileResponse accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID candidateId
    ) {
        return candidateService.accept(
                userId(jwt),
                candidateId
        );
    }

    @PostMapping("/{candidateId}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID candidateId
    ) {
        candidateService.reject(
                userId(jwt),
                candidateId
        );

        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
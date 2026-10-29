package com.mira.mira_backend.service;

import com.mira.mira_backend.dto.MemoryCandidateListResponse;
import com.mira.mira_backend.dto.MemoryCandidateResponse;
import com.mira.mira_backend.dto.StyleProfileResponse;
import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;
import com.mira.mira_backend.entity.UserMemoryControl;
import com.mira.mira_backend.entity.UserStyleProfile;
import com.mira.mira_backend.repository.UserMemoryCandidateRepository;
import com.mira.mira_backend.repository.UserMemoryControlRepository;
import com.mira.mira_backend.repository.UserStyleProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemoryCandidateService {

    private final UserMemoryCandidateRepository candidateRepository;
    private final UserMemoryControlRepository controlRepository;
    private final UserStyleProfileRepository profileRepository;

    public MemoryCandidateService(
            UserMemoryCandidateRepository candidateRepository,
            UserMemoryControlRepository controlRepository,
            UserStyleProfileRepository profileRepository
    ) {
        this.candidateRepository = candidateRepository;
        this.controlRepository = controlRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public MemoryCandidateListResponse list(UUID userId) {
        List<MemoryCandidateResponse> items =
                candidateRepository
                        .findByUserIdAndStatusOrderByCreatedAtDesc(
                                userId,
                                MemoryCandidateStatus.PENDING
                        )
                        .stream()
                        .map(MemoryCandidateResponse::from)
                        .toList();

        return new MemoryCandidateListResponse(items);
    }

    @Transactional
    public StyleProfileResponse accept(
            UUID userId,
            UUID candidateId
    ) {
        controlRepository.ensureExists(userId);
        profileRepository.ensureExists(userId);

        UserMemoryControl control = controlRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Memory control row was not created."
                ));

        UserStyleProfile profile = profileRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Style profile row was not created."
                ));

        UserMemoryCandidate candidate = candidateRepository
                .findByIdAndUserIdForUpdate(candidateId, userId)
                .orElseThrow(MemoryCandidateNotFoundException::new);

        if (candidate.getStatus() != MemoryCandidateStatus.PENDING) {
            throw new MemoryCandidateNotFoundException();
        }

        if (candidate.getGeneration() != control.getGeneration()) {
            throw new InvalidRequestException(
                    "Memory candidate is no longer valid."
            );
        }

        if (!StyleProfileService.isAllowedProfileValue(
                candidate.getField(),
                candidate.getValue()
        )) {
            throw new InvalidRequestException(
                    "Memory candidate is invalid."
            );
        }

        Map<String, String> current = profile.getProfile();

        if (!candidate.getValue().equals(
                current.get(candidate.getField())
        )) {
            Map<String, String> next = new LinkedHashMap<>(current);
            next.put(candidate.getField(), candidate.getValue());

            profile.replaceProfile(next);
            profileRepository.saveAndFlush(profile);
        }

        candidate.accept();
        candidateRepository.saveAndFlush(candidate);

        return new StyleProfileResponse(
                profile.isEnabled(),
                profile.getVersion(),
                profile.getProfile(),
                profile.getUpdatedAt()
        );
    }

    @Transactional
    public void reject(UUID userId, UUID candidateId) {
        controlRepository.ensureExists(userId);
        controlRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Memory control row was not created."
                ));

        UserMemoryCandidate candidate = candidateRepository
                .findByIdAndUserIdForUpdate(candidateId, userId)
                .orElseThrow(MemoryCandidateNotFoundException::new);

        if (candidate.getStatus() != MemoryCandidateStatus.PENDING) {
            throw new MemoryCandidateNotFoundException();
        }

        candidate.reject();
        candidateRepository.saveAndFlush(candidate);
    }
}
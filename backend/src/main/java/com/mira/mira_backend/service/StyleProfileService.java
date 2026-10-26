package com.mira.mira_backend.service;

import com.mira.mira_backend.dto.MemorySettingsResponse;
import com.mira.mira_backend.dto.StyleProfileResponse;
import com.mira.mira_backend.entity.UserMemoryControl;
import com.mira.mira_backend.entity.UserStyleProfile;
import com.mira.mira_backend.repository.UserMemoryControlRepository;
import com.mira.mira_backend.repository.UserStyleProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StyleProfileService {

    private static final Map<String, Set<String>> ALLOWED_VALUES =
            Map.of(
                    "explanation_depth",
                    Set.of("concise", "balanced", "detailed"),

                    "tone",
                    Set.of(
                            "conversational",
                            "formal",
                            "academic"
                    ),

                    "examples",
                    Set.of(
                            "prefer_concrete_examples",
                            "theory_first",
                            "no_strong_preference"
                    ),

                    "formatting",
                    Set.of(
                            "structured",
                            "prose",
                            "bulleted_lists",
                            "no_strong_preference"
                    ),

                    "teaching_sequence",
                    Set.of(
                            "principles_first",
                            "code_first",
                            "analogy_first",
                            "no_strong_preference"
                    )
            );

    private final UserMemoryControlRepository controlRepository;
    private final UserStyleProfileRepository profileRepository;

    public StyleProfileService(
            UserMemoryControlRepository controlRepository,
            UserStyleProfileRepository profileRepository
    ) {
        this.controlRepository = controlRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public StyleProfileResponse get(UUID userId) {
        LockedProfile locked = provisionAndLock(userId);
        return toResponse(locked.profile());
    }

    @Transactional
    public StyleProfileResponse replace(
            UUID userId,
            int expectedVersion,
            Map<String, String> profile
    ) {
        Map<String, String> validated = validate(profile);

        LockedProfile locked = provisionAndLock(userId);
        UserStyleProfile entity = locked.profile();

        if (entity.getVersion() != expectedVersion) {
            throw new VersionConflictException(entity.getVersion());
        }

        entity.replaceProfile(validated);
        profileRepository.saveAndFlush(entity);

        return toResponse(entity);
    }

    @Transactional
    public MemorySettingsResponse updateSettings(
            UUID userId,
            boolean enabled
    ) {
        LockedProfile locked = provisionAndLock(userId);
        UserStyleProfile entity = locked.profile();

        entity.setEnabled(enabled);
        profileRepository.saveAndFlush(entity);

        return new MemorySettingsResponse(
                entity.isEnabled(),
                entity.getVersion(),
                entity.getUpdatedAt()
        );
    }

    @Transactional
    public void purge(UUID userId) {
        LockedProfile locked = provisionAndLock(userId);

        UserMemoryControl control = locked.control();
        control.incrementGeneration();
        controlRepository.saveAndFlush(control);

        UserStyleProfile profile = locked.profile();
        profile.purge();
        profileRepository.saveAndFlush(profile);
    }

    private LockedProfile provisionAndLock(UUID userId) {
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

        return new LockedProfile(control, profile);
    }

    private Map<String, String> validate(
            Map<String, String> input
    ) {
        if (input == null) {
            throw new InvalidRequestException(
                    "profile must not be null."
            );
        }

        Map<String, String> validated = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : input.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();

            Set<String> allowed = ALLOWED_VALUES.get(field);

            if (allowed == null) {
                throw new InvalidRequestException(
                        "Unsupported profile field: " + field
                );
            }

            if (value == null || !allowed.contains(value)) {
                throw new InvalidRequestException(
                        "Invalid value for profile field: " + field
                );
            }

            validated.put(field, value);
        }

        return validated;
    }

    private StyleProfileResponse toResponse(UserStyleProfile profile) {
        return new StyleProfileResponse(
                profile.isEnabled(),
                profile.getVersion(),
                profile.getProfile(),
                profile.getUpdatedAt()
        );
    }

    private record LockedProfile(
            UserMemoryControl control,
            UserStyleProfile profile
    ) {
    }
}
package com.mira.mira_backend.service;

import com.mira.mira_backend.entity.MemoryCandidateClassification;
import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.entity.MemoryJobStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;
import com.mira.mira_backend.entity.UserMemoryControl;
import com.mira.mira_backend.entity.UserStyleProfile;
import com.mira.mira_backend.repository.MemoryExtractionJobRepository;
import com.mira.mira_backend.repository.UserMemoryCandidateRepository;
import com.mira.mira_backend.repository.UserMemoryControlRepository;
import com.mira.mira_backend.repository.UserStyleProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MemoryExtractionCommitService {

    private final UserMemoryControlRepository controlRepository;
    private final UserStyleProfileRepository profileRepository;
    private final UserMemoryCandidateRepository candidateRepository;
    private final MemoryExtractionJobRepository jobRepository;

    public MemoryExtractionCommitService(
            UserMemoryControlRepository controlRepository,
            UserStyleProfileRepository profileRepository,
            UserMemoryCandidateRepository candidateRepository,
            MemoryExtractionJobRepository jobRepository
    ) {
        this.controlRepository = controlRepository;
        this.profileRepository = profileRepository;
        this.candidateRepository = candidateRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public void commit(
            MemoryExtractionService.JobWork job,
            MemoryExtractionResult result
    ) {
        controlRepository.ensureExists(job.userId());
        profileRepository.ensureExists(job.userId());

        UserMemoryControl control = controlRepository
                .findByIdForUpdate(job.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Memory control row was not created."
                ));

        UserStyleProfile profile = profileRepository
                .findByIdForUpdate(job.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Style profile row was not created."
                ));

        MemoryExtractionJob entity = jobRepository
                .findByIdForUpdate(job.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Extraction job was not found."
                ));

        if (entity.getStatus() != MemoryJobStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Extraction job is not processing."
            );
        }

        if (control.getGeneration() != job.expectedGeneration()
                || !profile.isEnabled()
                || profile.getVersion() != job.expectedVersion()) {
            entity.markSkipped();
            jobRepository.saveAndFlush(entity);
            return;
        }

        Map<String, String> explicit = explicitUpdates(
                result.candidates()
        );

        if (!explicit.isEmpty()) {
            for (Map.Entry<String, String> entry
                    : explicit.entrySet()) {
                if (!StyleProfileService.isAllowedProfileValue(
                        entry.getKey(),
                        entry.getValue()
                )) {
                    throw new IllegalArgumentException(
                            "Invalid extracted profile value."
                    );
                }
            }

            Map<String, String> next = new LinkedHashMap<>(
                    profile.getProfile()
            );
            next.putAll(explicit);
            profile.replaceProfile(next);
        }

        Set<String> staged = new HashSet<>();

        for (MemoryExtractionResult.ExtractedCandidate candidate
                : result.candidates()) {
            if (candidate.classification()
                    != MemoryCandidateClassification.INFERRED) {
                continue;
            }

            if (explicit.containsKey(candidate.field())) {
                continue;
            }

            if (candidate.value().equals(
                    profile.getProfile().get(candidate.field())
            )) {
                continue;
            }

            String key = candidate.field() + "|" + candidate.value();

            if (!staged.add(key)) {
                continue;
            }

            Optional<UserMemoryCandidate> existing =
                    candidateRepository
                            .findByUserIdAndFieldAndValueAndStatus(
                                    job.userId(),
                                    candidate.field(),
                                    candidate.value(),
                                    MemoryCandidateStatus.PENDING
                            );

            if (existing.isPresent()) {
                existing.get().recordEvidence(
                        candidate.evidence()
                );
                candidateRepository.save(existing.get());
            } else {
                candidateRepository.save(
                        new UserMemoryCandidate(
                                job.userId(),
                                candidate.field(),
                                candidate.value(),
                                MemoryCandidateClassification.INFERRED,
                                candidate.evidence(),
                                control.getGeneration()
                        )
                );
            }
        }

        if (!explicit.isEmpty()) {
            profileRepository.save(profile);
        }

        entity.markCompleted();
        jobRepository.saveAndFlush(entity);
    }

    private Map<String, String> explicitUpdates(
            List<MemoryExtractionResult.ExtractedCandidate> candidates
    ) {
        Map<String, String> updates = new LinkedHashMap<>();

        for (MemoryExtractionResult.ExtractedCandidate candidate
                : candidates) {
            if (candidate.classification()
                    == MemoryCandidateClassification.EXPLICIT_DURABLE) {
                updates.putIfAbsent(
                        candidate.field(),
                        candidate.value()
                );
            }
        }

        for (MemoryExtractionResult.ExtractedCandidate candidate
                : candidates) {
            if (candidate.classification()
                    == MemoryCandidateClassification.EXPLICIT_CORRECTION) {
                updates.put(
                        candidate.field(),
                        candidate.value()
                );
            }
        }

        return updates;
    }
}
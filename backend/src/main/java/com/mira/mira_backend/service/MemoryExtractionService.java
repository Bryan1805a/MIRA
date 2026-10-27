package com.mira.mira_backend.service;

import com.mira.mira_backend.entity.AiResponse;
import com.mira.mira_backend.entity.AiResponseStatus;
import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.entity.MemoryJobStatus;
import com.mira.mira_backend.entity.UserMemoryControl;
import com.mira.mira_backend.entity.UserStyleProfile;
import com.mira.mira_backend.repository.AiResponseRepository;
import com.mira.mira_backend.repository.MemoryExtractionJobRepository;
import com.mira.mira_backend.repository.UserMemoryControlRepository;
import com.mira.mira_backend.repository.UserStyleProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class MemoryExtractionService {

    private final MemoryExtractionJobRepository jobRepository;
    private final UserMemoryControlRepository controlRepository;
    private final UserStyleProfileRepository profileRepository;
    private final AiResponseRepository aiResponseRepository;

    public MemoryExtractionService(
            MemoryExtractionJobRepository jobRepository,
            UserMemoryControlRepository controlRepository,
            UserStyleProfileRepository profileRepository,
            AiResponseRepository aiResponseRepository
    ) {
        this.jobRepository = jobRepository;
        this.controlRepository = controlRepository;
        this.profileRepository = profileRepository;
        this.aiResponseRepository = aiResponseRepository;
    }

    public record JobWork(
            UUID id,
            UUID userId,
            UUID conversationId,
            UUID responseId,
            int expectedGeneration,
            int expectedVersion
    ) {
    }

    public record Turn(
            String userContent,
            String assistantContent
    ) {
    }

    @Transactional
    public void scheduleAfterCompletion(
            UUID userId,
            UUID conversationId,
            UUID responseId
    ) {
        controlRepository.ensureExists(userId);
        profileRepository.ensureExists(userId);

        UserStyleProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Style profile row was not created."
                ));

        if (!profile.isEnabled()) {
            return;
        }

        if (jobRepository.findByResponseId(responseId).isPresent()) {
            return;
        }

        UserMemoryControl control = controlRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Memory control row was not created."
                ));

        jobRepository.saveAndFlush(
                new MemoryExtractionJob(
                        userId,
                        conversationId,
                        responseId,
                        control.getGeneration(),
                        profile.getVersion()
                )
        );
    }

    @Transactional
    public JobWork claimNextJob() {
        Optional<MemoryExtractionJob> next = jobRepository
                .findFirstByStatusOrderByCreatedAtAsc(
                        MemoryJobStatus.PENDING
                );

        return next.map(this::claimInternal).orElse(null);
    }

    @Transactional
    public JobWork claimJob(UUID jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .map(this::claimInternal)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Turn loadTurn(UUID responseId) {
        AiResponse response = aiResponseRepository
                .findWithMessages(responseId)
                .orElse(null);

        if (response == null
                || response.getStatus() != AiResponseStatus.COMPLETED
                || response.getAssistantMessage() == null) {
            return null;
        }

        return new Turn(
                response.getUserMessage().getContent(),
                response.getAssistantMessage().getContent()
        );
    }

    @Transactional
    public void markSkipped(UUID jobId) {
        jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
            job.markSkipped();
            jobRepository.saveAndFlush(job);
        });
    }

    @Transactional
    public void markFailed(UUID jobId, String errorCategory) {
        jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
            job.markFailed(errorCategory);
            jobRepository.saveAndFlush(job);
        });
    }

    private JobWork claimInternal(MemoryExtractionJob job) {
        if (job.getStatus() != MemoryJobStatus.PENDING) {
            return null;
        }

        job.markProcessing();
        jobRepository.saveAndFlush(job);

        return new JobWork(
                job.getId(),
                job.getUserId(),
                job.getConversationId(),
                job.getResponseId(),
                job.getExpectedGeneration(),
                job.getExpectedVersion()
        );
    }
}
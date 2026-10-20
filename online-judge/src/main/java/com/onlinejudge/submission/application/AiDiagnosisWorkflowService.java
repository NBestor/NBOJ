package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.AiDiagnosisRun;
import com.onlinejudge.submission.domain.AiDiagnosisStageRun;
import com.onlinejudge.submission.dto.AiDiagnosisProgressResponse;
import com.onlinejudge.submission.persistence.AiDiagnosisRunRepository;
import com.onlinejudge.submission.persistence.AiDiagnosisStageRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDiagnosisWorkflowService {

    public static final String RUN_QUEUED = "QUEUED";
    public static final String RUN_RUNNING = "RUNNING";
    public static final String RUN_RETRYING = "RETRYING";
    public static final String RUN_RESULT_READY = "RESULT_READY";
    public static final String RUN_COMPLETED = "COMPLETED";
    public static final String RUN_FAILED = "FAILED";

    public static final String STAGE_PENDING = "PENDING";
    public static final String STAGE_RUNNING = "RUNNING";
    public static final String STAGE_SUCCEEDED = "SUCCEEDED";
    public static final String STAGE_RETRYABLE_FAILED = "RETRYABLE_FAILED";
    public static final String STAGE_FAILED = "FAILED";

    private final AiDiagnosisRunRepository runRepository;
    private final AiDiagnosisStageRunRepository stageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public synchronized AiDiagnosisRun beginRun(Long submissionId, String generationKey) {
        if (submissionId == null || generationKey == null || generationKey.isBlank()) {
            throw new IllegalArgumentException("submissionId and generationKey are required");
        }
        Optional<AiDiagnosisRun> existing = runRepository.findByGenerationKey(generationKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        int nextVersion = runRepository.findTopBySubmissionIdOrderByVersionNumberDesc(submissionId)
                .map(AiDiagnosisRun::getVersionNumber)
                .orElse(0) + 1;
        return runRepository.saveAndFlush(AiDiagnosisRun.builder()
                .submissionId(submissionId)
                .generationKey(generationKey)
                .versionNumber(nextVersion)
                .status(RUN_QUEUED)
                .currentStage("PREPARING")
                .officialVersion(false)
                .resultSaved(false)
                .build());
    }

    @Transactional
    public AiDiagnosisRun markRunning(Long runId, String currentStage) {
        AiDiagnosisRun run = requireRun(runId);
        run.setStatus(RUN_RUNNING);
        run.setCurrentStage(clean(currentStage));
        run.setFailureReason(null);
        return runRepository.save(run);
    }

    public <T> T executeStage(Long runId,
                              String stageKey,
                              String stageType,
                              String issueId,
                              Object input,
                              Class<T> outputType,
                              String provider,
                              String model,
                              String promptVersion,
                              StageCallable<T> callable) throws Exception {
        if (runId == null) {
            return callable.call();
        }
        AiDiagnosisStageRun stage = prepareStage(runId, stageKey, stageType, issueId, input, provider, model, promptVersion);
        if (STAGE_SUCCEEDED.equals(stage.getStatus()) && stage.getOutputJson() != null) {
            return read(stage.getOutputJson(), outputType);
        }
        long started = System.nanoTime();
        try {
            T output = callable.call();
            completeStage(stage.getId(), output, elapsedMs(started), model);
            return output;
        } catch (Exception exception) {
            failStage(stage.getId(), exception, elapsedMs(started), true);
            throw exception;
        }
    }

    @Transactional
    public synchronized AiDiagnosisStageRun prepareStage(Long runId,
                                                         String stageKey,
                                                         String stageType,
                                                         String issueId,
                                                         Object input,
                                                         String provider,
                                                         String model,
                                                         String promptVersion) {
        AiDiagnosisStageRun stage = stageRepository.findByRunIdAndStageKey(runId, stageKey)
                .orElseGet(() -> AiDiagnosisStageRun.builder()
                        .runId(runId)
                        .stageKey(stageKey)
                        .stageType(stageType)
                        .issueId(clean(issueId))
                        .status(STAGE_PENDING)
                        .attemptCount(0)
                        .build());
        if (STAGE_SUCCEEDED.equals(stage.getStatus())) {
            return stage;
        }
        if (STAGE_RUNNING.equals(stage.getStatus())) {
            throw new IllegalStateException("diagnosis stage is already running: " + stageKey);
        }
        stage.setStatus(STAGE_RUNNING);
        stage.setAttemptCount((stage.getAttemptCount() == null ? 0 : stage.getAttemptCount()) + 1);
        stage.setInputFingerprint(fingerprint(input));
        stage.setProvider(clean(provider));
        stage.setModel(clean(model));
        stage.setPromptVersion(clean(promptVersion));
        stage.setFailureReason(null);
        stage.setStartedAt(LocalDateTime.now());
        stage.setCompletedAt(null);
        AiDiagnosisStageRun saved = stageRepository.saveAndFlush(stage);
        markRunning(runId, stageType);
        return saved;
    }

    @Transactional
    public void completeStage(Long stageId, Object output, long latencyMs, String actualModel) {
        AiDiagnosisStageRun stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("diagnosis stage not found: " + stageId));
        stage.setStatus(STAGE_SUCCEEDED);
        stage.setOutputJson(write(output));
        stage.setLatencyMs(Math.max(0, latencyMs));
        stage.setModel(clean(actualModel).isBlank() ? stage.getModel() : clean(actualModel));
        stage.setFailureReason(null);
        stage.setCompletedAt(LocalDateTime.now());
        stageRepository.save(stage);
    }

    @Transactional
    public void failStage(Long stageId, Throwable failure, long latencyMs, boolean retryable) {
        AiDiagnosisStageRun stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("diagnosis stage not found: " + stageId));
        stage.setStatus(retryable ? STAGE_RETRYABLE_FAILED : STAGE_FAILED);
        stage.setLatencyMs(Math.max(0, latencyMs));
        stage.setFailureReason(limit(failure == null ? "UNKNOWN_STAGE_FAILURE" : failure.getMessage(), 4000));
        stage.setCompletedAt(LocalDateTime.now());
        stageRepository.save(stage);
        AiDiagnosisRun run = requireRun(stage.getRunId());
        run.setStatus(retryable ? RUN_RETRYING : RUN_FAILED);
        run.setCurrentStage(stage.getStageType());
        run.setFailureReason(stage.getFailureReason());
        runRepository.save(run);
    }

    @Transactional
    public void markResultReady(Long runId) {
        if (runId == null) {
            return;
        }
        AiDiagnosisRun run = requireRun(runId);
        run.setResultSaved(true);
        run.setStatus(RUN_RESULT_READY);
        run.setCurrentStage("POST_DIAGNOSIS_PROJECTIONS");
        run.setFailureReason(null);
        runRepository.save(run);
    }

    @Transactional
    public void completeRun(Long runId) {
        if (runId == null) {
            return;
        }
        AiDiagnosisRun run = requireRun(runId);
        List<AiDiagnosisStageRun> requiredFailures = stageRepository.findByRunIdOrderByIdAsc(runId).stream()
                .filter(stage -> isRequiredStage(stage.getStageType()))
                .filter(stage -> !STAGE_SUCCEEDED.equals(stage.getStatus()))
                .toList();
        if (!requiredFailures.isEmpty()) {
            AiDiagnosisStageRun first = requiredFailures.get(0);
            run.setResultSaved(true);
            run.setStatus(requiredFailures.stream().anyMatch(stage -> STAGE_RETRYABLE_FAILED.equals(stage.getStatus()))
                    ? RUN_RETRYING
                    : RUN_FAILED);
            run.setCurrentStage(first.getStageType());
            run.setFailureReason(first.getFailureReason());
            runRepository.save(run);
            return;
        }
        runRepository.findBySubmissionIdOrderByVersionNumberDesc(run.getSubmissionId()).forEach(candidate -> {
            if (candidate.isOfficialVersion() && !candidate.getId().equals(runId)) {
                candidate.setOfficialVersion(false);
                runRepository.save(candidate);
            }
        });
        run.setResultSaved(true);
        run.setOfficialVersion(true);
        run.setStatus(RUN_COMPLETED);
        run.setCurrentStage("COMPLETED");
        run.setFailureReason(null);
        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);
    }

    private boolean isRequiredStage(String stageType) {
        return List.of("CORE_DIAGNOSIS", "ISSUE_ATTACHMENT", "STUDENT_OUTPUT", "TEACHER_OUTPUT")
                .contains(clean(stageType));
    }

    @Transactional
    public void failRun(Long runId, Throwable failure) {
        if (runId == null) {
            return;
        }
        AiDiagnosisRun run = requireRun(runId);
        run.setStatus(RUN_FAILED);
        run.setFailureReason(limit(failure == null ? "FULL_CHAIN_FAILED" : failure.getMessage(), 4000));
        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);
    }

    @Transactional
    public List<AiDiagnosisRun> recoverInterruptedRuns() {
        List<AiDiagnosisRun> runs = runRepository.findByStatusIn(List.of(RUN_QUEUED, RUN_RUNNING, RUN_RETRYING));
        for (AiDiagnosisRun run : runs) {
            for (AiDiagnosisStageRun stage : stageRepository.findByRunIdAndStatusIn(run.getId(), List.of(STAGE_RUNNING))) {
                stage.setStatus(STAGE_RETRYABLE_FAILED);
                stage.setFailureReason("PROCESS_RESTARTED_BEFORE_STAGE_COMPLETION");
                stage.setCompletedAt(LocalDateTime.now());
                stageRepository.save(stage);
            }
            run.setStatus(RUN_RETRYING);
            run.setCurrentStage("RECOVERING");
            runRepository.save(run);
        }
        return runs;
    }

    @Transactional(readOnly = true)
    public AiDiagnosisProgressResponse progressForSubmission(Long submissionId) {
        return runRepository.findTopBySubmissionIdOrderByVersionNumberDesc(submissionId)
                .map(this::toProgress)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<AiDiagnosisRun> latestRun(Long submissionId) {
        return runRepository.findTopBySubmissionIdOrderByVersionNumberDesc(submissionId);
    }

    private AiDiagnosisProgressResponse toProgress(AiDiagnosisRun run) {
        List<AiDiagnosisStageRun> stages = stageRepository.findByRunIdOrderByIdAsc(run.getId());
        int completed = (int) stages.stream().filter(stage -> STAGE_SUCCEEDED.equals(stage.getStatus())).count();
        boolean retrying = RUN_RETRYING.equals(run.getStatus()) || stages.stream()
                .anyMatch(stage -> STAGE_RETRYABLE_FAILED.equals(stage.getStatus()));
        return AiDiagnosisProgressResponse.builder()
                .runId(run.getId())
                .versionNumber(run.getVersionNumber())
                .status(publicStatus(run))
                .stage(publicStage(run.getCurrentStage()))
                .completedStages(completed)
                .totalStages(stages.size())
                .retrying(retrying)
                .resultAvailable(run.isResultSaved())
                .updatedAt(run.getUpdatedAt())
                .build();
    }

    private String publicStatus(AiDiagnosisRun run) {
        if (RUN_COMPLETED.equals(run.getStatus())) {
            return "COMPLETED";
        }
        if (RUN_FAILED.equals(run.getStatus())) {
            return "FAILED";
        }
        return RUN_RETRYING.equals(run.getStatus()) ? "RETRYING" : "PROCESSING";
    }

    private String publicStage(String stage) {
        String value = clean(stage).toUpperCase();
        if (value.contains("ATTACHMENT") || value.contains("NAVIGATION")) {
            return "MATCHING_KNOWLEDGE_PATHS";
        }
        if (value.contains("OUTPUT") || value.contains("ADVICE")) {
            return "GENERATING_COMPLETE_FEEDBACK";
        }
        if (value.contains("PROJECTION") || value.contains("VALIDATION") || value.contains("SAVE")) {
            return "VERIFYING_EVIDENCE_AND_SAFETY";
        }
        if (value.contains("COMPLETE")) {
            return "COMPLETED";
        }
        return "UNDERSTANDING_EVIDENCE";
    }

    private AiDiagnosisRun requireRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("diagnosis run not found: " + runId));
    }

    private <T> T read(String json, Class<T> outputType) {
        try {
            return objectMapper.readValue(json, outputType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("persisted diagnosis stage output is invalid", exception);
        }
    }

    private String write(Object value) {
        try {
            return value == null ? "null" : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("diagnosis stage output cannot be serialized", exception);
        }
    }

    private String fingerprint(Object input) {
        String value = write(input);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int max) {
        String cleaned = clean(value);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    @FunctionalInterface
    public interface StageCallable<T> {
        T call() throws Exception;
    }
}

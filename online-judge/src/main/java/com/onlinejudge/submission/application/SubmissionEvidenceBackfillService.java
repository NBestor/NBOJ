package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionEvidenceBackfillBatch;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.SubmissionEvidenceBackfillResponse;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRevisionRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionEvidenceBackfillBatchRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionEvidenceBackfillService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository analysisRepository;
    private final StudentAiFeedbackRepository feedbackRepository;
    private final StudentAiFeedbackRevisionRepository revisionRepository;
    private final StudentAiFeedbackEventRepository eventRepository;
    private final SubmissionDiagnosisFactRepository factRepository;
    private final SubmissionEvidenceBackfillBatchRepository batchRepository;
    private final SubmissionDiagnosisFactProjector factProjector;
    private final ObjectMapper objectMapper;
    private SubmissionIssueLifecycleService issueLifecycleService;

    @Autowired(required = false)
    void setIssueLifecycleService(SubmissionIssueLifecycleService issueLifecycleService) {
        this.issueLifecycleService = issueLifecycleService;
    }

    public SubmissionEvidenceBackfillResponse preview(Long cursor, Integer batchSize) {
        return execute(cursor, batchSize, true);
    }

    public SubmissionEvidenceBackfillResponse backfill(Long cursor, Integer batchSize) {
        return execute(cursor, batchSize, false);
    }

    private SubmissionEvidenceBackfillResponse execute(Long cursorValue, Integer requestedBatchSize, boolean dryRun) {
        long cursor = cursorValue == null ? 0L : Math.max(0L, cursorValue);
        int batchSize = requestedBatchSize == null ? 200 : Math.max(1, Math.min(requestedBatchSize, 1000));
        String batchKey = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        List<Submission> remaining = submissionRepository.findAll().stream()
                .filter(submission -> submission.getId() != null && submission.getId() > cursor)
                .sorted(Comparator.comparing(Submission::getId))
                .toList();
        List<Submission> batch = remaining.stream().limit(batchSize).toList();
        List<Long> ids = batch.stream().map(Submission::getId).toList();
        Map<Long, SubmissionAnalysis> analyses = ids.isEmpty() ? Map.of() : analysisRepository.findBySubmissionIdIn(ids).stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, Function.identity()));
        Map<Long, StudentAiFeedback> feedbacks = ids.isEmpty() ? Map.of() : feedbackRepository.findBySubmissionIdIn(ids).stream()
                .collect(Collectors.toMap(StudentAiFeedback::getSubmissionId, Function.identity()));
        Map<Long, List<StudentAiFeedbackEvent>> events = ids.isEmpty() ? Map.of() : eventRepository.findBySubmissionIdIn(ids).stream()
                .collect(Collectors.groupingBy(StudentAiFeedbackEvent::getSubmissionId));

        long identityMissing = 0;
        long analysisMissing = 0;
        long pathUnclassified = 0;
        long versionMissing = 0;
        long versionCreated = 0;
        long factCreated = 0;
        long eventLinked = 0;
        long success = 0;
        long skipped = 0;
        List<String> failures = new ArrayList<>();

        for (Submission submission : batch) {
            boolean changed = false;
            try {
                if (submission.getAssignmentId() != null && submission.getStudentProfileId() == null) {
                    identityMissing++;
                }
                SubmissionAnalysis analysis = analyses.get(submission.getId());
                if (analysis == null) {
                    analysisMissing++;
                } else if (factRepository.findByAnalysisId(analysis.getId()).isEmpty()) {
                    SubmissionAnalysisResponse response = parseAnalysis(analysis);
                    pathUnclassified += countUnclassified(response);
                    if (!dryRun && response != null) {
                        int inserted = factProjector.project(analysis, response).inserted();
                        factCreated += inserted;
                        changed |= inserted > 0;
                    }
                } else {
                    List<com.onlinejudge.submission.domain.SubmissionDiagnosisFact> existingFacts = factRepository.findByAnalysisId(analysis.getId());
                    pathUnclassified += existingFacts.stream()
                            .filter(fact -> "UNCLASSIFIED".equalsIgnoreCase(fact.getKnowledgePathStatus()))
                            .count();
                    if (issueLifecycleService != null) {
                        int normalizedFacts = issueLifecycleService.normalizeFacts(existingFacts, dryRun);
                        changed |= normalizedFacts > 0 && !dryRun;
                        if (!dryRun) {
                            issueLifecycleService.rebuildForSubmission(submission.getId());
                        }
                    }
                }

                StudentAiFeedback feedback = feedbacks.get(submission.getId());
                if (feedback != null) {
                    StudentAiFeedbackRevision revision = findRevision(feedback);
                    if (revision == null) {
                        versionMissing++;
                        if (!dryRun) {
                            revision = createRevision(feedback, analysis);
                            versionCreated++;
                            changed = true;
                        }
                    }
                    if (!dryRun && revision != null) {
                        for (StudentAiFeedbackEvent event : events.getOrDefault(submission.getId(), List.of())) {
                            if (event.getFeedbackRevisionId() == null) {
                                event.setFeedbackRevisionId(revision.getId());
                                eventRepository.save(event);
                                eventLinked++;
                                changed = true;
                            }
                        }
                    }
                }
                if (changed) {
                    success++;
                } else {
                    skipped++;
                }
            } catch (Exception exception) {
                failures.add("submission=" + submission.getId() + ":" + exception.getClass().getSimpleName() + ":" + safeMessage(exception));
            }
        }

        long failed = failures.size();
        Long nextCursor = batch.isEmpty() ? cursor : batch.get(batch.size() - 1).getId();
        boolean hasMore = remaining.size() > batch.size();
        SubmissionEvidenceBackfillBatch record = SubmissionEvidenceBackfillBatch.builder()
                .batchKey(batchKey)
                .dryRun(dryRun)
                .cursorStart(cursor)
                .cursorEnd(nextCursor)
                .processedCount(batch.size())
                .successCount(success)
                .skippedCount(skipped)
                .failedCount(failed)
                .errorJson(writeJson(failures))
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build();
        batchRepository.save(record);
        return SubmissionEvidenceBackfillResponse.builder()
                .batchKey(batchKey)
                .dryRun(dryRun)
                .cursorStart(cursor)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .processedCount(batch.size())
                .successCount(success)
                .skippedCount(skipped)
                .failedCount(failed)
                .identityMissingCount(identityMissing)
                .analysisMissingCount(analysisMissing)
                .pathUnclassifiedCount(pathUnclassified)
                .feedbackVersionMissingCount(versionMissing)
                .feedbackVersionCreatedCount(versionCreated)
                .diagnosisFactCreatedCount(factCreated)
                .feedbackEventLinkedCount(eventLinked)
                .failures(failures)
                .build();
    }

    private StudentAiFeedbackRevision findRevision(StudentAiFeedback feedback) {
        if (feedback.getGenerationKey() != null && !feedback.getGenerationKey().isBlank()) {
            return revisionRepository.findBySubmissionIdAndGenerationKey(feedback.getSubmissionId(), feedback.getGenerationKey())
                    .orElse(null);
        }
        return revisionRepository.findTopBySubmissionIdOrderByVersionNumberDesc(feedback.getSubmissionId()).orElse(null);
    }

    private StudentAiFeedbackRevision createRevision(StudentAiFeedback feedback, SubmissionAnalysis analysis) {
        String generationKey = feedback.getGenerationKey();
        if (generationKey == null || generationKey.isBlank()) {
            generationKey = "backfill-" + feedback.getSubmissionId() + "-" + feedback.getId();
            feedback.setGenerationKey(generationKey);
        }
        int versionNumber = revisionRepository.findTopBySubmissionIdOrderByVersionNumberDesc(feedback.getSubmissionId())
                .map(item -> item.getVersionNumber() + 1)
                .orElse(1);
        StudentAiFeedbackRevision revision = revisionRepository.save(StudentAiFeedbackRevision.builder()
                .submissionId(feedback.getSubmissionId())
                .feedbackId(feedback.getId())
                .analysisId(analysis == null ? null : analysis.getId())
                .versionNumber(versionNumber)
                .generationKey(generationKey)
                .status(feedback.getStatus())
                .source(feedback.getSource())
                .feedbackJson(feedback.getFeedbackJson())
                .failureReason(feedback.getFailureReason())
                .promptVersion("legacy")
                .schemaVersion("student-ai-feedback-v1")
                .generatedAt(feedback.getGeneratedAt())
                .build());
        feedback.setLatestRevisionId(revision.getId());
        feedbackRepository.save(feedback);
        return revision;
    }

    private SubmissionAnalysisResponse parseAnalysis(SubmissionAnalysis analysis) {
        if (analysis.getReportJson() == null || analysis.getReportJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(analysis.getReportJson(), SubmissionAnalysisResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("analysis-json-invalid", exception);
        }
    }

    private long countUnclassified(SubmissionAnalysisResponse response) {
        if (response == null) {
            return 0;
        }
        long basic = response.getBasicLayerAdvice() == null ? 0 : response.getBasicLayerAdvice().stream()
                .filter(item -> item == null || item.getKnowledgePath() == null || item.getKnowledgePath().isEmpty()
                        || "UNCLASSIFIED".equalsIgnoreCase(item.getKnowledgePathStatus()))
                .count();
        long improvement = response.getImprovementLayerAdvice() == null ? 0 : response.getImprovementLayerAdvice().stream()
                .filter(item -> item == null || item.getKnowledgePath() == null || item.getKnowledgePath().isEmpty()
                        || "UNCLASSIFIED".equalsIgnoreCase(item.getKnowledgePathStatus()))
                .count();
        return basic + improvement;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "unknown";
        }
        String normalized = message.replaceAll("[\\r\\n]+", " ");
        return normalized.substring(0, Math.min(240, normalized.length()));
    }
}

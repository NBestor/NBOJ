package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionIssueTransitionRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionIssueLifecycleService {

    public static final String PROJECTION_VERSION = "issue-lifecycle-v1";
    private static final String UNCOMPARABLE_KEY = "scope:uncomparable";

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository analysisRepository;
    private final SubmissionDiagnosisFactRepository factRepository;
    private final SubmissionIssueTransitionRepository transitionRepository;
    private final IssuePointKeyFactory pointKeyFactory;
    private final SubmissionEvidenceProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProjectionSummary rebuildForSubmission(Long submissionId) {
        if (!properties.isLifecycleProjectionEnabled() || submissionId == null) {
            return new ProjectionSummary(0, 0, 0);
        }
        Submission target = submissionRepository.findById(submissionId).orElse(null);
        if (target == null || target.getStudentProfileId() == null || target.getProblemId() == null) {
            return new ProjectionSummary(0, 0, 0);
        }
        List<Submission> scope = scopeSubmissions(target);
        return rebuildScope(scope);
    }

    @Transactional
    public ProjectionSummary rebuildScope(List<Submission> input) {
        List<Submission> submissions = safe(input).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Submission::getId))
                .toList();
        if (submissions.isEmpty()) {
            return new ProjectionSummary(0, 0, 0);
        }
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        List<SubmissionDiagnosisFact> facts = factRepository.findBySubmissionIdIn(submissionIds);
        int normalized = normalizeFacts(facts, false);
        Map<Long, List<SubmissionDiagnosisFact>> factsBySubmission = facts.stream()
                .collect(Collectors.groupingBy(SubmissionDiagnosisFact::getSubmissionId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> analyzedSubmissionIds = analysisRepository.findBySubmissionIdIn(submissionIds).stream()
                .map(SubmissionAnalysis::getSubmissionId)
                .collect(Collectors.toSet());
        List<SubmissionIssueTransition> existing = transitionRepository.findByCurrentSubmissionIdIn(submissionIds);
        if (!existing.isEmpty()) {
            transitionRepository.deleteAll(existing);
            transitionRepository.flush();
        }

        Map<String, PointCounter> counters = new LinkedHashMap<>();
        Map<String, SubmissionDiagnosisFact> previousFacts = Map.of();
        Submission previousSubmission = null;
        List<SubmissionIssueTransition> projected = new ArrayList<>();
        int incomparable = 0;

        for (Submission current : submissions) {
            Map<String, SubmissionDiagnosisFact> currentFacts = factsBySubmission.getOrDefault(current.getId(), List.of()).stream()
                    .filter(fact -> hasText(fact.getNormalizedPointKey()))
                    .collect(Collectors.toMap(
                            SubmissionDiagnosisFact::getNormalizedPointKey,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            boolean comparable = analyzedSubmissionIds.contains(current.getId());
            String fingerprint = pointKeyFactory.sourceFingerprint(current.getSourceCode());
            boolean effectiveAttempt = comparable
                    && isEffectiveAttempt(previousSubmission, current, previousFacts.keySet(), currentFacts.keySet());

            if (!comparable) {
                projected.add(transition(current, previousSubmission, null, null, UNCOMPARABLE_KEY,
                        "EVIDENCE", "UNCOMPARABLE", "UNCOMPARABLE", fingerprint, false, new PointCounter()));
                incomparable++;
                continue;
            }

            for (Map.Entry<String, SubmissionDiagnosisFact> entry : currentFacts.entrySet()) {
                String pointKey = entry.getKey();
                SubmissionDiagnosisFact fact = entry.getValue();
                PointCounter counter = counters.computeIfAbsent(pointKey, ignored -> new PointCounter());
                String status;
                if (previousSubmission == null) {
                    status = "NEW";
                } else if (previousFacts.containsKey(pointKey)) {
                    status = "PERSISTED";
                } else if (counter.rawOccurrenceCount > 0) {
                    status = "RECURRED";
                } else {
                    status = "NEW";
                }
                counter.observe(current.getId(), effectiveAttempt);
                projected.add(transition(current, previousSubmission, fact, previousFacts.get(pointKey), pointKey,
                        fact.getDisplayCategory(), status, label(status, counter), fingerprint, effectiveAttempt, counter));
            }
            if (previousSubmission != null) {
                for (Map.Entry<String, SubmissionDiagnosisFact> entry : previousFacts.entrySet()) {
                    if (currentFacts.containsKey(entry.getKey())) {
                        continue;
                    }
                    PointCounter counter = counters.computeIfAbsent(entry.getKey(), ignored -> new PointCounter());
                    String status = current.getVerdict() == Submission.Verdict.ACCEPTED ? "RECOVERED" : "NOT_OBSERVED";
                    projected.add(transition(current, previousSubmission, null, entry.getValue(), entry.getKey(),
                            entry.getValue().getDisplayCategory(), status, label(status, counter), fingerprint,
                            effectiveAttempt, counter));
                    counter.breakContinuity();
                }
            }
            if (effectiveAttempt) {
                previousSubmission = current;
                previousFacts = currentFacts;
            }
        }
        transitionRepository.saveAll(projected);
        log.info("Rebuilt submission issue lifecycle. studentProfileId={}, assignmentId={}, problemId={}, submissions={}, transitions={}, incomparable={}",
                submissions.get(0).getStudentProfileId(), submissions.get(0).getAssignmentId(), submissions.get(0).getProblemId(),
                submissions.size(), projected.size(), incomparable);
        return new ProjectionSummary(normalized, projected.size(), incomparable);
    }

    @Transactional
    public int normalizeFacts(Collection<SubmissionDiagnosisFact> input, boolean dryRun) {
        int changed = 0;
        for (SubmissionDiagnosisFact fact : input == null ? List.<SubmissionDiagnosisFact>of() : input) {
            if (fact == null) {
                continue;
            }
            IssuePointKeyFactory.Identity identity = pointKeyFactory.identity(fact, parseJsonList(fact.getKnowledgePathJson()));
            String expectedDisplayCategory = displayCategory(fact.getFactType());
            if (Objects.equals(identity.key(), fact.getNormalizedPointKey())
                    && Objects.equals(identity.source(), fact.getPointKeySource())
                    && Objects.equals(identity.version(), fact.getPointKeyVersion())
                    && Objects.equals(expectedDisplayCategory, fact.getDisplayCategory())) {
                continue;
            }
            changed++;
            if (dryRun) {
                continue;
            }
            fact.setNormalizedPointKey(identity.key());
            fact.setPointKeySource(identity.source());
            fact.setPointKeyVersion(identity.version());
            fact.setDisplayCategory(expectedDisplayCategory);
            factRepository.save(fact);
        }
        return changed;
    }

    private List<Submission> scopeSubmissions(Submission target) {
        if (target.getAssignmentId() == null) {
            return submissionRepository.findByProblemIdOrderBySubmittedAtAsc(target.getProblemId()).stream()
                    .filter(item -> item.getAssignmentId() == null)
                    .filter(item -> Objects.equals(item.getStudentProfileId(), target.getStudentProfileId()))
                    .toList();
        }
        return submissionRepository.findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(
                target.getAssignmentId(), target.getProblemId(), target.getStudentProfileId());
    }

    private boolean isEffectiveAttempt(
            Submission previous,
            Submission current,
            Set<String> previousPoints,
            Set<String> currentPoints
    ) {
        if (previous == null) {
            return true;
        }
        return !Objects.equals(pointKeyFactory.sourceFingerprint(previous.getSourceCode()), pointKeyFactory.sourceFingerprint(current.getSourceCode()))
                || !Objects.equals(previous.getVerdict(), current.getVerdict())
                || !Objects.equals(previousPoints, currentPoints);
    }

    private SubmissionIssueTransition transition(
            Submission current,
            Submission previous,
            SubmissionDiagnosisFact currentFact,
            SubmissionDiagnosisFact previousFact,
            String pointKey,
            String displayCategory,
            String status,
            String personalLabel,
            String fingerprint,
            boolean effectiveAttempt,
            PointCounter counter
    ) {
        SubmissionDiagnosisFact displayFact = currentFact == null ? previousFact : currentFact;
        return SubmissionIssueTransition.builder()
                .transitionKey(limit(current.getId() + ":" + pointKey + ":" + status, 320))
                .studentProfileId(current.getStudentProfileId())
                .assignmentId(current.getAssignmentId())
                .problemId(current.getProblemId())
                .currentSubmissionId(current.getId())
                .previousSubmissionId(previous == null ? null : previous.getId())
                .currentFactId(currentFact == null ? null : currentFact.getId())
                .previousFactId(previousFact == null ? null : previousFact.getId())
                .normalizedPointKey(pointKey)
                .pointKeySource(displayFact == null ? "SCOPE" : displayFact.getPointKeySource())
                .factType(displayFact == null ? null : displayFact.getFactType())
                .displayCategory(displayCategory)
                .title(displayFact == null ? null : displayFact.getTitle())
                .transitionType(status)
                .personalLabel(personalLabel)
                .rawOccurrenceCount(counter.rawOccurrenceCount)
                .effectiveOccurrenceCount(counter.effectiveOccurrenceCount)
                .consecutiveEffectiveCount(counter.consecutiveEffectiveCount)
                .affectedProblemCount(pointKey.equals(UNCOMPARABLE_KEY) ? 0 : 1)
                .effectiveAttempt(effectiveAttempt)
                .sourceFingerprint(fingerprint)
                .firstSeenSubmissionId(counter.firstSeenSubmissionId)
                .lastSeenSubmissionId(counter.lastSeenSubmissionId)
                .evidenceSubmissionIdsJson(writeJson(counter.evidenceSubmissionIds))
                .projectionVersion(PROJECTION_VERSION)
                .build();
    }

    private String label(String status, PointCounter counter) {
        if ("RECOVERED".equals(status)) {
            return "RECOVERED";
        }
        if ("RECURRED".equals(status)) {
            return "RECURRING_ERROR";
        }
        if ("UNCOMPARABLE".equals(status)) {
            return "EVIDENCE_INSUFFICIENT";
        }
        if (counter.consecutiveEffectiveCount >= properties.getPersistentDifficultyThreshold()) {
            return "PERSISTENT_DIFFICULTY";
        }
        if (counter.rawOccurrenceCount <= 1) {
            return "SINGLE_OBSERVATION";
        }
        return "OBSERVING";
    }

    private String displayCategory(String factType) {
        return "IMPROVEMENT".equalsIgnoreCase(factType) ? "IMPROVEMENT" : "REPAIR";
    }

    private List<String> parseJsonList(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String limit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ProjectionSummary(int normalizedFactCount, int transitionCount, int incomparableCount) {
    }

    private static final class PointCounter {
        private long rawOccurrenceCount;
        private long effectiveOccurrenceCount;
        private long consecutiveEffectiveCount;
        private Long firstSeenSubmissionId;
        private Long lastSeenSubmissionId;
        private final Set<Long> evidenceSubmissionIds = new LinkedHashSet<>();

        private void observe(Long submissionId, boolean effectiveAttempt) {
            rawOccurrenceCount++;
            if (effectiveAttempt) {
                effectiveOccurrenceCount++;
                consecutiveEffectiveCount++;
            }
            if (firstSeenSubmissionId == null) {
                firstSeenSubmissionId = submissionId;
            }
            lastSeenSubmissionId = submissionId;
            evidenceSubmissionIds.add(submissionId);
        }

        private void breakContinuity() {
            consecutiveEffectiveCount = 0;
        }
    }
}

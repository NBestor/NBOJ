package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.application.SubmissionEvidenceProperties;
import com.onlinejudge.submission.application.SubmissionGrowthSummaryService;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class SubmissionEvidenceAnalyticsService {

    private static final String UNCLASSIFIED = "UNCLASSIFIED";
    private static final int RECENT_WINDOW_DAYS = 30;

    private final SubmissionDiagnosisFactRepository factRepository;
    private final TeacherDiagnosisCorrectionRepository correctionRepository;
    private final StudentAiFeedbackEventRepository feedbackEventRepository;
    private final ObjectMapper objectMapper;
    private final SubmissionEvidenceProperties properties;
    private SubmissionGrowthSummaryService growthSummaryService;

    @Autowired
    public SubmissionEvidenceAnalyticsService(
            SubmissionDiagnosisFactRepository factRepository,
            TeacherDiagnosisCorrectionRepository correctionRepository,
            StudentAiFeedbackEventRepository feedbackEventRepository,
            ObjectMapper objectMapper,
            SubmissionEvidenceProperties properties
    ) {
        this.factRepository = factRepository;
        this.correctionRepository = correctionRepository;
        this.feedbackEventRepository = feedbackEventRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public SubmissionEvidenceAnalyticsService(
            SubmissionDiagnosisFactRepository factRepository,
            TeacherDiagnosisCorrectionRepository correctionRepository,
            StudentAiFeedbackEventRepository feedbackEventRepository,
            ObjectMapper objectMapper
    ) {
        this(factRepository, correctionRepository, feedbackEventRepository, objectMapper, new SubmissionEvidenceProperties());
    }

    @Autowired(required = false)
    void setGrowthSummaryService(SubmissionGrowthSummaryService growthSummaryService) {
        this.growthSummaryService = growthSummaryService;
    }

    @Transactional(readOnly = true)
    public EvidenceSummary summarize(
            List<Submission> input,
            List<StudentProfile> classStudents,
            Map<Long, SubmissionAnalysis> analyses
    ) {
        List<Submission> submissions = safe(input).stream()
                .filter(Objects::nonNull)
                .filter(submission -> submission.getId() != null)
                .toList();
        Set<Long> rosterIds = safe(classStudents).stream()
                .map(StudentProfile::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Submission> legal = submissions.stream()
                .filter(submission -> isLegalIdentity(submission, rosterIds))
                .toList();
        if (!properties.isAnalyticsReadEnabled()) {
            return disabledSummary(submissions, legal, rosterIds);
        }
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        List<SubmissionDiagnosisFact> facts = submissionIds.isEmpty()
                ? List.of()
                : factRepository.findBySubmissionIdIn(submissionIds);
        Map<Long, TeacherDiagnosisCorrection> corrections = latestCorrections(submissionIds);
        List<StudentAiFeedbackEvent> events = submissionIds.isEmpty()
                ? List.of()
                : feedbackEventRepository.findBySubmissionIdIn(submissionIds);

        Set<Long> legalIds = legal.stream().map(Submission::getId).collect(Collectors.toSet());
        Map<Long, List<EffectiveFact>> effectiveFacts = facts.stream()
                .filter(fact -> legalIds.contains(fact.getSubmissionId()))
                .filter(this::isErrorFact)
                .map(fact -> effectiveFact(fact, corrections.get(fact.getSubmissionId())))
                .collect(Collectors.groupingBy(
                        EffectiveFact::submissionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        long submittedStudentCount = legal.stream()
                .map(Submission::getStudentProfileId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        Map<Long, SubmissionGrowthSummaryResponse> growthBySubmission = growthSummaryService == null
                ? Map.of()
                : growthSummaryService.summarize(legal);
        List<AssignmentOverviewResponse.KnowledgePathStat> pathStats = buildPathStats(
                legal,
                effectiveFacts,
                submittedStudentCount,
                growthBySubmission
        );
        RecoveryResult recovery = buildRecovery(legal, effectiveFacts, events, analyses == null ? Map.of() : analyses);
        Map<Long, AssignmentOverviewResponse.StudentRecentState> recentStates = buildRecentStates(
                legal,
                effectiveFacts,
                recovery.evidence(),
                growthBySubmission
        );
        AssignmentOverviewResponse.DataCompleteness completeness = buildCompleteness(
                submissions,
                legal,
                rosterIds,
                analyses == null ? Map.of() : analyses,
                facts,
                events
        );

        long passedStudentCount = legal.stream()
                .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                .map(Submission::getStudentProfileId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long passedAttemptCount = legal.stream()
                .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                .count();
        long rosterStudentCount = rosterIds.size();

        return new EvidenceSummary(
                rosterStudentCount,
                submittedStudentCount,
                Math.max(0, rosterStudentCount - submittedStudentCount),
                legal.size(),
                passedAttemptCount,
                ratio(passedStudentCount, submittedStudentCount),
                ratio(passedAttemptCount, legal.size()),
                completeness,
                pathStats,
                recovery.summary(),
                recentStates
        );
    }

    private EvidenceSummary disabledSummary(List<Submission> submissions, List<Submission> legal, Set<Long> rosterIds) {
        long submitted = legal.stream().map(Submission::getStudentProfileId).filter(Objects::nonNull).distinct().count();
        long passedStudents = legal.stream()
                .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED)
                .map(Submission::getStudentProfileId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long passedAttempts = legal.stream().filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED).count();
        AssignmentOverviewResponse.DataCompleteness completeness = AssignmentOverviewResponse.DataCompleteness.builder()
                .totalSubmissionCount(submissions.size())
                .legalIdentityCount(legal.size())
                .identityMissingCount(submissions.stream().filter(item -> item.getStudentProfileId() == null).count())
                .completeRate(null)
                .build();
        return new EvidenceSummary(
                rosterIds.size(), submitted, Math.max(0, rosterIds.size() - submitted), legal.size(), passedAttempts,
                ratio(passedStudents, submitted), ratio(passedAttempts, legal.size()), completeness, List.of(),
                AssignmentOverviewResponse.RecoverySummary.builder().evidence(List.of()).build(), Map.of()
        );
    }

    private Map<Long, TeacherDiagnosisCorrection> latestCorrections(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return correctionRepository.findBySubmissionIdIn(submissionIds).stream()
                .filter(item -> item.getSubmissionId() != null)
                .collect(Collectors.toMap(
                        TeacherDiagnosisCorrection::getSubmissionId,
                        Function.identity(),
                        this::newerCorrection,
                        LinkedHashMap::new
                ));
    }

    private TeacherDiagnosisCorrection newerCorrection(TeacherDiagnosisCorrection left, TeacherDiagnosisCorrection right) {
        if (left.getCorrectedAt() == null) {
            return right;
        }
        if (right.getCorrectedAt() == null) {
            return left;
        }
        return right.getCorrectedAt().isAfter(left.getCorrectedAt()) ? right : left;
    }

    private boolean isLegalIdentity(Submission submission, Set<Long> rosterIds) {
        if (submission.getStudentProfileId() == null) {
            return false;
        }
        return rosterIds.contains(submission.getStudentProfileId());
    }

    private boolean isErrorFact(SubmissionDiagnosisFact fact) {
        return fact != null && !"IMPROVEMENT".equalsIgnoreCase(fact.getFactType());
    }

    private EffectiveFact effectiveFact(SubmissionDiagnosisFact fact, TeacherDiagnosisCorrection correction) {
        boolean correctionApplies = correctionApplies(fact, correction);
        String issueId = firstNonBlank(
                correctionApplies ? correction.getCorrectedFineGrainedTag() : null,
                correctionApplies ? correction.getCorrectedIssueTag() : null,
                fact.getMistakePointId(),
                fact.getIssueId(),
                fact.getTitle(),
                "fact-" + fact.getId()
        );
        List<String> path = correctionApplies && hasText(correction.getCorrectedKnowledgePath())
                ? parseCorrectedPath(correction.getCorrectedKnowledgePath())
                : parseJsonList(fact.getKnowledgePathJson());
        String pathStatus = correctionApplies && !path.isEmpty()
                ? "PROVISIONAL"
                : normalizePathStatus(fact.getKnowledgePathStatus(), path);
        List<AssignmentOverviewResponse.KnowledgePathNode> nodes = buildPathNodes(path, issueId, pathStatus);
        return new EffectiveFact(
                fact.getSubmissionId(),
                firstNonBlank(
                        correctionApplies ? correction.getCorrectedFineGrainedTag() : null,
                        correctionApplies ? correction.getCorrectedIssueTag() : null,
                        fact.getNormalizedPointKey(),
                        issueId
                ),
                issueId,
                nodes,
                pathStatus,
                normalizeLibraryFit(fact.getLibraryFit()),
                correctionApplies ? "TEACHER_OVERRIDE" : "AI_PROJECTION",
                correctionApplies ? correction.getId() : null
        );
    }

    private boolean correctionApplies(SubmissionDiagnosisFact fact, TeacherDiagnosisCorrection correction) {
        if (correction == null) {
            return false;
        }
        String target = clean(correction.getTargetIssueId());
        if (!hasText(target)) {
            return true;
        }
        return target.equals(clean(fact.getIssueId()))
                || target.equals(clean(fact.getMistakePointId()))
                || target.equals(clean(fact.getFactKey()));
    }

    private List<AssignmentOverviewResponse.KnowledgePathStat> buildPathStats(
            List<Submission> legal,
            Map<Long, List<EffectiveFact>> effectiveFacts,
            long submittedStudentCount,
            Map<Long, SubmissionGrowthSummaryResponse> growthBySubmission
    ) {
        Map<String, PathAccumulator> buckets = new LinkedHashMap<>();
        Map<String, BucketLifecycle> lifecycle = buildBucketLifecycle(legal, effectiveFacts, growthBySubmission);
        for (Submission submission : legal) {
            for (EffectiveFact fact : effectiveFacts.getOrDefault(submission.getId(), List.of())) {
                for (int index = 0; index < fact.path().size(); index++) {
                    int nodeIndex = index;
                    AssignmentOverviewResponse.KnowledgePathNode node = fact.path().get(index);
                    String key = bucketKey(fact, nodeIndex);
                    PathAccumulator accumulator = buckets.computeIfAbsent(key, ignored -> new PathAccumulator(
                            fact,
                            nodeIndex,
                            lifecycle.getOrDefault(key, new BucketLifecycle()),
                            submittedStudentCount,
                            properties
                    ));
                    accumulator.add(submission);
                }
            }
        }
        return buckets.values().stream()
                .map(PathAccumulator::toResponse)
                .sorted(Comparator.comparingLong(AssignmentOverviewResponse.KnowledgePathStat::getErrorOccurrenceCount)
                        .reversed()
                        .thenComparing(AssignmentOverviewResponse.KnowledgePathStat::getLabel, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private Map<String, BucketLifecycle> buildBucketLifecycle(
            List<Submission> legal,
            Map<Long, List<EffectiveFact>> effectiveFacts,
            Map<Long, SubmissionGrowthSummaryResponse> growthBySubmission
    ) {
        if (growthBySubmission == null || growthBySubmission.isEmpty()) {
            return buildLegacyBucketLifecycle(legal, effectiveFacts);
        }
        Map<String, BucketLifecycle> metrics = new LinkedHashMap<>();
        Map<String, List<Submission>> scopes = legal.stream()
                .filter(item -> item.getStudentProfileId() != null && item.getProblemId() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getStudentProfileId() + ":" + item.getAssignmentId() + ":" + item.getProblemId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (List<Submission> scope : scopes.values()) {
            List<Submission> ordered = scope.stream()
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                            .thenComparing(Submission::getId))
                    .toList();
            Map<String, Set<String>> bucketKeysByPoint = new LinkedHashMap<>();
            for (Submission submission : ordered) {
                for (EffectiveFact fact : effectiveFacts.getOrDefault(submission.getId(), List.of())) {
                    bucketKeysByPoint.computeIfAbsent(fact.normalizedPointKey(), ignored -> new LinkedHashSet<>())
                            .addAll(bucketKeys(List.of(fact)));
                }
            }
            Set<String> latestEffectiveKeys = Set.of();
            Long studentId = ordered.isEmpty() ? null : ordered.get(0).getStudentProfileId();
            for (Submission current : ordered) {
                SubmissionGrowthSummaryResponse growth = growthBySubmission.get(current.getId());
                Set<String> currentKeys = bucketKeys(effectiveFacts.getOrDefault(current.getId(), List.of()));
                boolean effectiveAttempt = growth != null && growth.isEffectiveAttempt();
                for (String key : currentKeys) {
                    BucketLifecycle metric = metrics.computeIfAbsent(key, ignored -> new BucketLifecycle());
                    if (effectiveAttempt) {
                        metric.effectiveOccurrenceCount++;
                    }
                }
                if (growth != null && effectiveAttempt) {
                    for (SubmissionGrowthSummaryResponse.IssueSignal signal : safe(growth.getIssueSignals())) {
                        Set<String> signalBuckets = bucketKeysByPoint.getOrDefault(signal.getNormalizedPointKey(), Set.of());
                        if ("PERSISTED".equals(signal.getChangeStatus())) {
                            signalBuckets.forEach(key -> metrics.computeIfAbsent(key, ignored -> new BucketLifecycle())
                                    .repeatedStudentIds.add(studentId));
                        } else if ("RECURRED".equals(signal.getChangeStatus())) {
                            signalBuckets.forEach(key -> {
                                BucketLifecycle metric = metrics.computeIfAbsent(key, ignored -> new BucketLifecycle());
                                metric.repeatedStudentIds.add(studentId);
                                metric.recurringStudentIds.add(studentId);
                            });
                        } else if ("RECOVERED".equals(signal.getChangeStatus())) {
                            signalBuckets.forEach(key -> metrics.computeIfAbsent(key, ignored -> new BucketLifecycle())
                                    .recoveredStudentIds.add(studentId));
                        }
                    }
                    latestEffectiveKeys = currentKeys;
                }
            }
            latestEffectiveKeys.forEach(key -> metrics.computeIfAbsent(key, ignored -> new BucketLifecycle())
                    .unresolvedStudentIds.add(studentId));
        }
        return metrics;
    }

    private Map<String, BucketLifecycle> buildLegacyBucketLifecycle(
            List<Submission> legal,
            Map<Long, List<EffectiveFact>> effectiveFacts
    ) {
        Map<String, BucketLifecycle> metrics = new LinkedHashMap<>();
        Map<String, List<Submission>> scopes = legal.stream()
                .filter(item -> item.getStudentProfileId() != null && item.getProblemId() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getStudentProfileId() + ":" + item.getAssignmentId() + ":" + item.getProblemId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (List<Submission> scope : scopes.values()) {
            List<Submission> ordered = scope.stream()
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                            .thenComparing(Submission::getId))
                    .toList();
            Set<String> seen = new LinkedHashSet<>();
            Set<String> previousKeys = Set.of();
            Submission previousEffective = null;
            for (Submission current : ordered) {
                Set<String> currentKeys = bucketKeys(effectiveFacts.getOrDefault(current.getId(), List.of()));
                boolean effectiveAttempt = previousEffective == null
                        || !Objects.equals(previousEffective.getSourceCode(), current.getSourceCode())
                        || !Objects.equals(previousEffective.getVerdict(), current.getVerdict())
                        || !Objects.equals(previousKeys, currentKeys);
                if (!effectiveAttempt) {
                    continue;
                }
                for (String key : currentKeys) {
                    BucketLifecycle metric = metrics.computeIfAbsent(key, ignored -> new BucketLifecycle());
                    metric.effectiveOccurrenceCount++;
                    if (previousKeys.contains(key)) {
                        metric.repeatedStudentIds.add(current.getStudentProfileId());
                    } else if (seen.contains(key)) {
                        metric.recurringStudentIds.add(current.getStudentProfileId());
                        metric.repeatedStudentIds.add(current.getStudentProfileId());
                    }
                    seen.add(key);
                }
                if (current.getVerdict() == Submission.Verdict.ACCEPTED) {
                    for (String key : seen) {
                        if (!currentKeys.contains(key)) {
                            metrics.computeIfAbsent(key, ignored -> new BucketLifecycle())
                                    .recoveredStudentIds.add(current.getStudentProfileId());
                        }
                    }
                }
                previousEffective = current;
                previousKeys = currentKeys;
            }
            for (String key : previousKeys) {
                metrics.computeIfAbsent(key, ignored -> new BucketLifecycle())
                        .unresolvedStudentIds.add(ordered.get(ordered.size() - 1).getStudentProfileId());
            }
        }
        return metrics;
    }

    private Set<String> bucketKeys(List<EffectiveFact> facts) {
        Set<String> keys = new LinkedHashSet<>();
        for (EffectiveFact fact : safe(facts)) {
            for (int index = 0; index < fact.path().size(); index++) {
                keys.add(bucketKey(fact, index));
            }
        }
        return keys;
    }

    private String bucketKey(EffectiveFact fact, int nodeIndex) {
        String pathKey = fact.path().subList(0, nodeIndex + 1).stream()
                .map(AssignmentOverviewResponse.KnowledgePathNode::getLabel)
                .collect(Collectors.joining("/"));
        AssignmentOverviewResponse.KnowledgePathNode node = fact.path().get(nodeIndex);
        return node.getKind() + ":" + fact.pathStatus() + ":" + pathKey;
    }

    private RecoveryResult buildRecovery(
            List<Submission> legal,
            Map<Long, List<EffectiveFact>> effectiveFacts,
            List<StudentAiFeedbackEvent> events,
            Map<Long, SubmissionAnalysis> analyses
    ) {
        Map<Long, List<StudentAiFeedbackEvent>> eventsBySubmission = safe(events).stream()
                .filter(event -> event.getSubmissionId() != null)
                .collect(Collectors.groupingBy(StudentAiFeedbackEvent::getSubmissionId));
        Map<String, List<Submission>> grouped = legal.stream()
                .filter(submission -> submission.getStudentProfileId() != null && submission.getProblemId() != null)
                .collect(Collectors.groupingBy(
                        submission -> submission.getStudentProfileId() + ":" + submission.getProblemId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<AssignmentOverviewResponse.SubmissionChange> changes = new ArrayList<>();
        for (List<Submission> group : grouped.values()) {
            List<Submission> ordered = group.stream()
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .toList();
            Submission previousEffective = null;
            Set<String> previousIssues = Set.of();
            for (Submission current : ordered) {
                if (!analyses.containsKey(current.getId())) {
                    continue;
                }
                Set<String> currentIssues = issueIds(effectiveFacts.get(current.getId()));
                boolean effectiveAttempt = previousEffective == null
                        || !Objects.equals(previousEffective.getSourceCode(), current.getSourceCode())
                        || !Objects.equals(previousEffective.getVerdict(), current.getVerdict())
                        || !Objects.equals(previousIssues, currentIssues);
                if (!effectiveAttempt) {
                    continue;
                }
                if (previousEffective != null
                        && !previousIssues.isEmpty()
                        && previousEffective.getVerdict() != Submission.Verdict.ACCEPTED) {
                    boolean viewed = feedbackViewedBefore(
                            eventsBySubmission.get(previousEffective.getId()),
                            current.getSubmittedAt()
                    );
                    String status = compare(previousEffective, current, previousIssues, currentIssues);
                    changes.add(change(previousEffective, current, previousIssues, currentIssues, status, viewed));
                }
                previousEffective = current;
                previousIssues = currentIssues;
            }
            if (previousEffective != null
                    && !previousIssues.isEmpty()
                    && previousEffective.getVerdict() != Submission.Verdict.ACCEPTED) {
                changes.add(change(previousEffective, null, previousIssues, Set.of(), "AWAITING_FOLLOWUP", false));
            }
        }
        long comparable = changes.stream().filter(this::isComparableChange).count();
        long recovered = countStatus(changes, "RECOVERED");
        long shifted = countStatus(changes, "ISSUE_SHIFTED");
        long recoveryNumerator = recovered + shifted;
        long viewedComparable = changes.stream().filter(AssignmentOverviewResponse.SubmissionChange::isFeedbackViewed)
                .filter(this::isComparableChange)
                .count();
        long viewedRecovered = changes.stream().filter(AssignmentOverviewResponse.SubmissionChange::isFeedbackViewed)
                .filter(change -> Set.of("RECOVERED", "ISSUE_SHIFTED").contains(change.getStatus()))
                .count();
        AssignmentOverviewResponse.RecoverySummary summary = AssignmentOverviewResponse.RecoverySummary.builder()
                .recoveryNumerator(recoveryNumerator)
                .recoveryDenominator(comparable)
                .comparableSampleCount(comparable)
                .recoveredCount(recovered)
                .sameIssueCount(countStatus(changes, "SAME_ISSUE"))
                .shiftedCount(shifted)
                .regressedCount(countStatus(changes, "REGRESSED"))
                .verdictChangedCount(countStatus(changes, "VERDICT_CHANGED"))
                .noClearChangeCount(countStatus(changes, "NO_CLEAR_CHANGE"))
                .awaitingFollowupCount(countStatus(changes, "AWAITING_FOLLOWUP") + countStatus(changes, "EVIDENCE_INSUFFICIENT"))
                .feedbackViewedComparableCount(viewedComparable)
                .feedbackViewedRecoveredCount(viewedRecovered)
                .recoveryRate(ratio(recoveryNumerator, comparable))
                .feedbackViewedRecoveryRate(ratio(viewedRecovered, viewedComparable))
                .evidence(changes.stream().sorted(Comparator.comparing(
                                AssignmentOverviewResponse.SubmissionChange::getBeforeSubmissionId,
                                Comparator.nullsLast(Long::compareTo)).reversed())
                        .limit(30)
                        .toList())
                .build();
        return new RecoveryResult(summary, changes);
    }

    private String compare(Submission before, Submission after, Set<String> beforeIssues, Set<String> afterIssues) {
        Set<String> overlap = new LinkedHashSet<>(beforeIssues);
        overlap.retainAll(afterIssues);
        if (!overlap.isEmpty()) {
            return "SAME_ISSUE";
        }
        if (after.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "RECOVERED";
        }
        if (afterIssues.isEmpty()) {
            return "EVIDENCE_INSUFFICIENT";
        }
        if (verdictRank(after.getVerdict()) > verdictRank(before.getVerdict())) {
            return "REGRESSED";
        }
        if (!afterIssues.isEmpty()) {
            return "ISSUE_SHIFTED";
        }
        if (!Objects.equals(before.getVerdict(), after.getVerdict())) {
            return "VERDICT_CHANGED";
        }
        return "NO_CLEAR_CHANGE";
    }

    private int verdictRank(Submission.Verdict verdict) {
        if (verdict == null) {
            return 6;
        }
        return switch (verdict) {
            case ACCEPTED -> 0;
            case WRONG_ANSWER -> 2;
            case TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED -> 3;
            case RUNTIME_ERROR, COMPILATION_ERROR -> 4;
            case INTERNAL_ERROR -> 5;
            case PENDING -> 6;
        };
    }

    private boolean feedbackViewedBefore(List<StudentAiFeedbackEvent> events, LocalDateTime afterTime) {
        return safe(events).stream()
                .filter(event -> StudentAiFeedbackEvent.EVENT_VIEWED.equals(event.getEventType()))
                .anyMatch(event -> afterTime == null || event.getCreatedAt() == null || !event.getCreatedAt().isAfter(afterTime));
    }

    private AssignmentOverviewResponse.SubmissionChange change(
            Submission before,
            Submission after,
            Set<String> beforeIssues,
            Set<String> afterIssues,
            String status,
            boolean viewed
    ) {
        return AssignmentOverviewResponse.SubmissionChange.builder()
                .studentProfileId(before.getStudentProfileId())
                .problemId(before.getProblemId())
                .beforeSubmissionId(before.getId())
                .afterSubmissionId(after == null ? null : after.getId())
                .beforeVerdict(verdict(before))
                .afterVerdict(verdict(after))
                .beforeIssueIds(List.copyOf(beforeIssues))
                .afterIssueIds(List.copyOf(afterIssues))
                .status(status)
                .feedbackViewed(viewed)
                .build();
    }

    private Map<Long, AssignmentOverviewResponse.StudentRecentState> buildRecentStates(
            List<Submission> legal,
            Map<Long, List<EffectiveFact>> facts,
            List<AssignmentOverviewResponse.SubmissionChange> changes,
            Map<Long, SubmissionGrowthSummaryResponse> growthBySubmission
    ) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECENT_WINDOW_DAYS);
        Map<Long, List<Submission>> byStudent = legal.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .filter(submission -> submission.getSubmittedAt() == null || !submission.getSubmittedAt().isBefore(cutoff))
                .collect(Collectors.groupingBy(Submission::getStudentProfileId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, AssignmentOverviewResponse.StudentRecentState> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Submission>> entry : byStudent.entrySet()) {
            List<Submission> ordered = entry.getValue().stream()
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(20)
                    .toList();
            Map<String, Set<Long>> submissionIdsByIssue = new LinkedHashMap<>();
            Map<String, Set<Long>> problemIdsByIssue = new LinkedHashMap<>();
            for (Submission submission : ordered) {
                SubmissionGrowthSummaryResponse growth = growthBySubmission.get(submission.getId());
                if (growthBySubmission != null && !growthBySubmission.isEmpty()
                        && (growth == null || !growth.isEffectiveAttempt())) {
                    continue;
                }
                for (EffectiveFact fact : facts.getOrDefault(submission.getId(), List.of())) {
                    submissionIdsByIssue.computeIfAbsent(fact.normalizedPointKey(), ignored -> new LinkedHashSet<>()).add(submission.getId());
                    if (submission.getProblemId() != null) {
                        problemIdsByIssue.computeIfAbsent(fact.normalizedPointKey(), ignored -> new LinkedHashSet<>()).add(submission.getProblemId());
                    }
                }
            }
            String repeatedIssue = submissionIdsByIssue.entrySet().stream()
                    .filter(item -> item.getValue().size() >= 2)
                    .max(Comparator.comparingInt(item -> item.getValue().size()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            AssignmentOverviewResponse.SubmissionChange latestChange = changes.stream()
                    .filter(change -> Objects.equals(change.getStudentProfileId(), entry.getKey()))
                    .filter(change -> change.getAfterSubmissionId() != null)
                    .max(Comparator.comparing(AssignmentOverviewResponse.SubmissionChange::getAfterSubmissionId))
                    .orElse(null);
            String status = recentStatus(ordered, repeatedIssue, latestChange);
            result.put(entry.getKey(), AssignmentOverviewResponse.StudentRecentState.builder()
                    .status(status)
                    .evidenceStatus(ordered.size() <= 1 ? "EVIDENCE_INSUFFICIENT" : "OBSERVED")
                    .independentSubmissionCount(growthBySubmission == null || growthBySubmission.isEmpty()
                            ? ordered.size()
                            : ordered.stream()
                            .map(Submission::getId)
                            .map(growthBySubmission::get)
                            .filter(Objects::nonNull)
                            .filter(SubmissionGrowthSummaryResponse::isEffectiveAttempt)
                            .count())
                    .problemCount(ordered.stream().map(Submission::getProblemId).filter(Objects::nonNull).distinct().count())
                    .repeatedIssueId(repeatedIssue)
                    .repeatedIssueCount(repeatedIssue == null ? 0 : submissionIdsByIssue.get(repeatedIssue).size())
                    .repeatedIssueProblemCount(repeatedIssue == null ? 0 : problemIdsByIssue.getOrDefault(repeatedIssue, Set.of()).size())
                    .latestChangeStatus(latestChange == null ? "AWAITING_EVIDENCE" : latestChange.getStatus())
                    .evidenceSubmissionIds(ordered.stream().map(Submission::getId).toList())
                    .issueTrajectories(buildStudentIssueTrajectories(ordered, facts))
                    .build());
        }
        return result;
    }

    private List<AssignmentOverviewResponse.StudentIssueTrajectory> buildStudentIssueTrajectories(
            List<Submission> submissions,
            Map<Long, List<EffectiveFact>> facts
    ) {
        Map<String, StudentIssueAccumulator> accumulators = new LinkedHashMap<>();
        Map<Long, List<Submission>> byProblem = safe(submissions).stream()
                .filter(item -> item.getProblemId() != null)
                .collect(Collectors.groupingBy(Submission::getProblemId, LinkedHashMap::new, Collectors.toList()));
        for (List<Submission> problemSubmissions : byProblem.values()) {
            List<Submission> ordered = problemSubmissions.stream()
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                            .thenComparing(Submission::getId))
                    .toList();
            Set<String> seen = new LinkedHashSet<>();
            Set<String> previousKeys = Set.of();
            Map<String, Long> consecutive = new LinkedHashMap<>();
            Submission previous = null;
            for (Submission current : ordered) {
                Map<String, EffectiveFact> currentFacts = facts.getOrDefault(current.getId(), List.of()).stream()
                        .collect(Collectors.toMap(
                                EffectiveFact::normalizedPointKey,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));
                Set<String> currentKeys = currentFacts.keySet();
                boolean effectiveAttempt = previous == null
                        || !Objects.equals(previous.getSourceCode(), current.getSourceCode())
                        || !Objects.equals(previous.getVerdict(), current.getVerdict())
                        || !Objects.equals(previousKeys, currentKeys);
                for (Map.Entry<String, EffectiveFact> factEntry : currentFacts.entrySet()) {
                    String key = factEntry.getKey();
                    EffectiveFact fact = factEntry.getValue();
                    StudentIssueAccumulator accumulator = accumulators.computeIfAbsent(
                            key,
                            ignored -> new StudentIssueAccumulator(key, fact.issueId(), "REPAIR")
                    );
                    boolean recurred = seen.contains(key) && !previousKeys.contains(key);
                    long currentConsecutive = previousKeys.contains(key)
                            ? consecutive.getOrDefault(key, 0L) + (effectiveAttempt ? 1 : 0)
                            : effectiveAttempt ? 1 : 0;
                    consecutive.put(key, currentConsecutive);
                    accumulator.observe(current, effectiveAttempt, currentConsecutive, recurred);
                    seen.add(key);
                }
                for (String previousKey : previousKeys) {
                    if (!currentKeys.contains(previousKey)) {
                        consecutive.put(previousKey, 0L);
                    }
                }
                previous = current;
                previousKeys = new LinkedHashSet<>(currentKeys);
            }
            if (!ordered.isEmpty()) {
                Submission latest = ordered.get(ordered.size() - 1);
                for (String key : seen) {
                    StudentIssueAccumulator accumulator = accumulators.get(key);
                    if (previousKeys.contains(key)) {
                        accumulator.active = true;
                    } else if (latest.getVerdict() == Submission.Verdict.ACCEPTED) {
                        accumulator.recovered = true;
                    }
                }
            }
        }
        return accumulators.values().stream()
                .map(StudentIssueAccumulator::toResponse)
                .sorted(Comparator.comparing((AssignmentOverviewResponse.StudentIssueTrajectory item) ->
                                !"UNRESOLVED".equals(item.getCurrentStatus()))
                        .thenComparing(AssignmentOverviewResponse.StudentIssueTrajectory::getEffectiveOccurrenceCount,
                                Comparator.reverseOrder())
                        .thenComparing(AssignmentOverviewResponse.StudentIssueTrajectory::getLabel,
                                Comparator.nullsLast(String::compareTo)))
                .limit(20)
                .toList();
    }

    private String recentStatus(
            List<Submission> ordered,
            String repeatedIssue,
            AssignmentOverviewResponse.SubmissionChange latestChange
    ) {
        if (ordered.size() <= 1) {
            return "SINGLE_OBSERVATION";
        }
        if (latestChange != null && "RECOVERED".equals(latestChange.getStatus())) {
            return "RECENTLY_RECOVERED";
        }
        if (repeatedIssue != null) {
            return "REPEATED_ISSUE";
        }
        if (latestChange != null && "ISSUE_SHIFTED".equals(latestChange.getStatus())) {
            return "ISSUE_CHANGING";
        }
        return "OBSERVING";
    }

    private AssignmentOverviewResponse.DataCompleteness buildCompleteness(
            List<Submission> all,
            List<Submission> legal,
            Set<Long> rosterIds,
            Map<Long, SubmissionAnalysis> analyses,
            List<SubmissionDiagnosisFact> facts,
            List<StudentAiFeedbackEvent> events
    ) {
        Set<Long> legalIds = legal.stream().map(Submission::getId).collect(Collectors.toSet());
        Set<Long> diagnosedIds = safe(facts).stream()
                .map(SubmissionDiagnosisFact::getSubmissionId)
                .filter(legalIds::contains)
                .collect(Collectors.toSet());
        Set<Long> eventIds = safe(events).stream()
                .map(StudentAiFeedbackEvent::getSubmissionId)
                .filter(legalIds::contains)
                .collect(Collectors.toSet());
        Set<Long> analysisIds = analyses.keySet().stream().filter(legalIds::contains).collect(Collectors.toSet());
        long complete = legalIds.stream()
                .filter(analysisIds::contains)
                .filter(diagnosedIds::contains)
                .filter(eventIds::contains)
                .count();
        long identityMissing = all.stream().filter(submission -> submission.getStudentProfileId() == null).count();
        long invalidContext = rosterIds.isEmpty() ? 0 : all.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .filter(submission -> !rosterIds.contains(submission.getStudentProfileId()))
                .count();
        long unclassified = safe(facts).stream()
                .filter(fact -> legalIds.contains(fact.getSubmissionId()))
                .filter(this::isErrorFact)
                .filter(fact -> "UNCLASSIFIED".equalsIgnoreCase(fact.getKnowledgePathStatus()))
                .count();
        return AssignmentOverviewResponse.DataCompleteness.builder()
                .totalSubmissionCount(all.size())
                .legalIdentityCount(legal.size())
                .identityMissingCount(identityMissing)
                .invalidContextCount(invalidContext)
                .analysisReadyCount(analysisIds.size())
                .analysisMissingCount(Math.max(0, legal.size() - analysisIds.size()))
                .diagnosisFactCount(safe(facts).stream().filter(fact -> legalIds.contains(fact.getSubmissionId())).count())
                .diagnosedSubmissionCount(diagnosedIds.size())
                .unclassifiedFactCount(unclassified)
                .feedbackEventSubmissionCount(eventIds.size())
                .completeSubmissionCount(complete)
                .completeRate(ratio(complete, legal.size()))
                .build();
    }

    private List<AssignmentOverviewResponse.KnowledgePathNode> buildPathNodes(
            List<String> rawPath,
            String issueId,
            String pathStatus
    ) {
        List<String> path = rawPath.stream().filter(this::hasText).map(String::trim).toList();
        if (path.isEmpty() || "UNCLASSIFIED".equals(pathStatus)) {
            return List.of(
                    node(UNCLASSIFIED, "chapter"),
                    node(UNCLASSIFIED, "knowledgePoint"),
                    node(UNCLASSIFIED, "skillUnit"),
                    node(firstNonBlank(issueId, UNCLASSIFIED), "mistakePoint")
            );
        }
        String chapter = path.get(0);
        String knowledge = path.size() >= 2 ? path.get(1) : UNCLASSIFIED;
        String skill = path.size() >= 3 ? path.get(path.size() - 2) : UNCLASSIFIED;
        String mistake = path.size() >= 2 ? path.get(path.size() - 1) : firstNonBlank(issueId, UNCLASSIFIED);
        return List.of(
                node(chapter, "chapter"),
                node(knowledge, "knowledgePoint"),
                node(skill, "skillUnit"),
                node(mistake, "mistakePoint")
        );
    }

    private AssignmentOverviewResponse.KnowledgePathNode node(String label, String kind) {
        return AssignmentOverviewResponse.KnowledgePathNode.builder().label(label).kind(kind).build();
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

    private List<String> parseCorrectedPath(String path) {
        if (!hasText(path)) {
            return List.of();
        }
        return List.of(path.split("\\s*(?:>|/|／|›|→|\\|)\\s*")).stream()
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }

    private String normalizePathStatus(String value, List<String> path) {
        String normalized = clean(value).toUpperCase();
        if (Set.of("FORMAL", "PROVISIONAL", "INFERRED", "UNCLASSIFIED").contains(normalized)) {
            return normalized;
        }
        return path.isEmpty() ? "UNCLASSIFIED" : "INFERRED";
    }

    private String normalizeLibraryFit(String value) {
        String normalized = clean(value).toUpperCase();
        return Set.of("HIT", "PARTIAL", "MISS").contains(normalized) ? normalized : "UNKNOWN";
    }

    private Set<String> issueIds(List<EffectiveFact> facts) {
        return safe(facts).stream()
                .map(EffectiveFact::normalizedPointKey)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isComparableChange(AssignmentOverviewResponse.SubmissionChange change) {
        return !Set.of("AWAITING_FOLLOWUP", "EVIDENCE_INSUFFICIENT").contains(change.getStatus());
    }

    private long countStatus(List<AssignmentOverviewResponse.SubmissionChange> changes, String status) {
        return changes.stream().filter(change -> status.equals(change.getStatus())).count();
    }

    private String verdict(Submission submission) {
        return submission == null || submission.getVerdict() == null ? null : submission.getVerdict().name();
    }

    private Double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record EvidenceSummary(
            long rosterStudentCount,
            long submittedStudentCount,
            long unsubmittedStudentCount,
            long attemptCount,
            long passedAttemptCount,
            Double studentPassRate,
            Double attemptPassRate,
            AssignmentOverviewResponse.DataCompleteness dataCompleteness,
            List<AssignmentOverviewResponse.KnowledgePathStat> knowledgePathStats,
            AssignmentOverviewResponse.RecoverySummary recoverySummary,
            Map<Long, AssignmentOverviewResponse.StudentRecentState> recentStates
    ) {
    }

    private record EffectiveFact(
            Long submissionId,
            String normalizedPointKey,
            String issueId,
            List<AssignmentOverviewResponse.KnowledgePathNode> path,
            String pathStatus,
            String libraryFit,
            String source,
            Long teacherCorrectionId
    ) {
    }

    private record RecoveryResult(
            AssignmentOverviewResponse.RecoverySummary summary,
            List<AssignmentOverviewResponse.SubmissionChange> evidence
    ) {
    }

    private static final class BucketLifecycle {
        private long effectiveOccurrenceCount;
        private final Set<Long> repeatedStudentIds = new LinkedHashSet<>();
        private final Set<Long> unresolvedStudentIds = new LinkedHashSet<>();
        private final Set<Long> recurringStudentIds = new LinkedHashSet<>();
        private final Set<Long> recoveredStudentIds = new LinkedHashSet<>();
    }

    private final class StudentIssueAccumulator {
        private final String normalizedPointKey;
        private final String label;
        private final String factType;
        private long rawOccurrenceCount;
        private long effectiveOccurrenceCount;
        private long consecutiveEffectiveCount;
        private long recurringCount;
        private Long firstSeenSubmissionId;
        private Long lastSeenSubmissionId;
        private boolean active;
        private boolean recovered;
        private final Set<Long> problemIds = new LinkedHashSet<>();
        private final Set<Long> evidenceSubmissionIds = new LinkedHashSet<>();

        private StudentIssueAccumulator(String normalizedPointKey, String label, String factType) {
            this.normalizedPointKey = normalizedPointKey;
            this.label = label;
            this.factType = factType;
        }

        private void observe(Submission submission, boolean effectiveAttempt, long consecutive, boolean recurred) {
            rawOccurrenceCount++;
            if (effectiveAttempt) {
                effectiveOccurrenceCount++;
            }
            consecutiveEffectiveCount = Math.max(consecutiveEffectiveCount, consecutive);
            if (recurred) {
                recurringCount++;
            }
            if (firstSeenSubmissionId == null) {
                firstSeenSubmissionId = submission.getId();
            }
            lastSeenSubmissionId = submission.getId();
            evidenceSubmissionIds.add(submission.getId());
            if (submission.getProblemId() != null) {
                problemIds.add(submission.getProblemId());
            }
        }

        private AssignmentOverviewResponse.StudentIssueTrajectory toResponse() {
            List<String> labels = new ArrayList<>();
            if (recurringCount > 0) {
                labels.add("RECURRING_ERROR");
            }
            if (active && consecutiveEffectiveCount >= properties.getPersistentDifficultyThreshold()) {
                labels.add("PERSISTENT_DIFFICULTY");
            }
            if (problemIds.size() >= properties.getCrossProblemWeaknessThreshold()) {
                labels.add("CROSS_PROBLEM_WEAKNESS");
            }
            if (!active && recovered) {
                labels.add("RECOVERED");
            }
            if (labels.isEmpty()) {
                labels.add(rawOccurrenceCount <= 1 ? "SINGLE_OBSERVATION" : "OBSERVING");
            }
            return AssignmentOverviewResponse.StudentIssueTrajectory.builder()
                    .normalizedPointKey(normalizedPointKey)
                    .label(label)
                    .factType(factType)
                    .currentStatus(active ? "UNRESOLVED" : recovered ? "RECOVERED" : "NOT_OBSERVED")
                    .personalLabels(labels)
                    .rawOccurrenceCount(rawOccurrenceCount)
                    .effectiveOccurrenceCount(effectiveOccurrenceCount)
                    .consecutiveEffectiveCount(consecutiveEffectiveCount)
                    .affectedProblemCount(problemIds.size())
                    .recurringCount(recurringCount)
                    .firstSeenSubmissionId(firstSeenSubmissionId)
                    .lastSeenSubmissionId(lastSeenSubmissionId)
                    .affectedProblemIds(problemIds.stream().toList())
                    .evidenceSubmissionIds(evidenceSubmissionIds.stream().toList())
                    .build();
        }
    }

    private static final class PathAccumulator {
        private final EffectiveFact fact;
        private final int nodeIndex;
        private final BucketLifecycle lifecycle;
        private final long submittedStudentCount;
        private final SubmissionEvidenceProperties properties;
        private long occurrenceCount;
        private final Set<Long> students = new LinkedHashSet<>();
        private final Set<Long> problems = new LinkedHashSet<>();
        private final Set<Long> submissions = new LinkedHashSet<>();
        private final Map<Long, Submission> submissionSamples = new LinkedHashMap<>();
        private final Map<Long, Set<Long>> submissionIdsByStudent = new LinkedHashMap<>();

        private PathAccumulator(
                EffectiveFact fact,
                int nodeIndex,
                BucketLifecycle lifecycle,
                long submittedStudentCount,
                SubmissionEvidenceProperties properties
        ) {
            this.fact = fact;
            this.nodeIndex = nodeIndex;
            this.lifecycle = lifecycle;
            this.submittedStudentCount = submittedStudentCount;
            this.properties = properties;
        }

        private void add(Submission submission) {
            occurrenceCount++;
            submissions.add(submission.getId());
            submissionSamples.putIfAbsent(submission.getId(), submission);
            if (submission.getProblemId() != null) {
                problems.add(submission.getProblemId());
            }
            if (submission.getStudentProfileId() != null) {
                students.add(submission.getStudentProfileId());
                submissionIdsByStudent.computeIfAbsent(submission.getStudentProfileId(), ignored -> new LinkedHashSet<>())
                        .add(submission.getId());
            }
        }

        private AssignmentOverviewResponse.KnowledgePathStat toResponse() {
            List<Long> repeatedStudentIds = lifecycle.repeatedStudentIds.stream().toList();
            List<AssignmentOverviewResponse.KnowledgePathNode> scopedPath = fact.path().subList(0, nodeIndex + 1);
            String pathKey = scopedPath.stream().map(AssignmentOverviewResponse.KnowledgePathNode::getLabel)
                    .collect(Collectors.joining("/"));
            AssignmentOverviewResponse.KnowledgePathNode node = fact.path().get(nodeIndex);
            long recoveryNumerator = lifecycle.recoveredStudentIds.size();
            long recoveryDenominator = recoveryNumerator + lifecycle.unresolvedStudentIds.size();
            Double recoveryRate = recoveryDenominator == 0
                    ? null
                    : Math.round(recoveryNumerator * 10000.0 / recoveryDenominator) / 10000.0;
            long effectiveOccurrences = lifecycle.effectiveOccurrenceCount == 0 && occurrenceCount > 0
                    ? occurrenceCount
                    : lifecycle.effectiveOccurrenceCount;
            long manyThreshold = Math.max(2, (long) Math.ceil(submittedStudentCount * properties.getClassCoverageThreshold()));
            boolean manyStudents = students.size() >= manyThreshold;
            double repeatRatio = students.isEmpty() ? 0 : effectiveOccurrences * 1.0 / students.size();
            boolean highRepeat = repeatRatio >= properties.getClassRepeatThreshold();
            boolean lowRecovery = recoveryRate == null || recoveryRate < properties.getClassLowRecoveryThreshold();
            String classification = manyStudents && highRepeat && lowRecovery
                    ? "CLASS_DIFFICULTY"
                    : manyStudents
                    ? "COMMON_ERROR"
                    : highRepeat
                    ? "INDIVIDUAL_PERSISTENT"
                    : "OCCASIONAL_INDIVIDUAL";
            return AssignmentOverviewResponse.KnowledgePathStat.builder()
                    .id(node.getKind() + ":" + fact.pathStatus() + ":" + pathKey)
                    .label(node.getLabel())
                    .granularity(node.getKind())
                    .normalizedIssueId("mistakePoint".equals(node.getKind()) ? fact.normalizedPointKey() : null)
                    .path(scopedPath)
                    .pathStatus(fact.pathStatus())
                    .libraryFit(fact.libraryFit())
                    .source(fact.source())
                    .teacherCorrectionId(fact.teacherCorrectionId())
                    .errorOccurrenceCount(occurrenceCount)
                    .rawOccurrenceCount(occurrenceCount)
                    .effectiveWeightedOccurrenceCount(effectiveOccurrences)
                    .affectedStudentCount(students.size())
                    .repeatedStudentCount(repeatedStudentIds.size())
                    .unresolvedStudentCount(lifecycle.unresolvedStudentIds.size())
                    .recurringStudentCount(lifecycle.recurringStudentIds.size())
                    .recoveredStudentCount(lifecycle.recoveredStudentIds.size())
                    .recoveryNumerator(recoveryNumerator)
                    .recoveryDenominator(recoveryDenominator)
                    .recoveryRate(recoveryRate)
                    .difficultyClassification(classification)
                    .affectedProblemCount(problems.size())
                    .affectedStudentIds(students.stream().toList())
                    .repeatedStudentIds(repeatedStudentIds)
                    .resolvedStudentIds(lifecycle.recoveredStudentIds.stream().toList())
                    .affectedProblemIds(problems.stream().toList())
                    .evidenceSubmissionIds(submissions.stream().limit(20).toList())
                    .evidenceSamples(submissionSamples.values().stream()
                            .limit(20)
                            .map(submission -> AssignmentOverviewResponse.SubmissionEvidenceRef.builder()
                                    .submissionId(submission.getId())
                                    .studentProfileId(submission.getStudentProfileId())
                                    .problemId(submission.getProblemId())
                                    .verdict(submission.getVerdict() == null ? null : submission.getVerdict().name())
                                    .submittedAt(submission.getSubmittedAt())
                                    .build())
                            .toList())
                    .build();
        }
    }
}

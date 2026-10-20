package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultStatsProjection;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionIssueTransitionRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
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
public class SubmissionGrowthSummaryService {

    public static final String RULE_VERSION = "single-problem-growth-v1";

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository analysisRepository;
    private final SubmissionCaseResultRepository caseResultRepository;
    private final SubmissionDiagnosisFactRepository factRepository;
    private final SubmissionIssueTransitionRepository transitionRepository;
    private final IssuePointKeyFactory pointKeyFactory;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<Long, SubmissionGrowthSummaryResponse> summarizeByIds(Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return Map.of();
        }
        return summarize(submissionRepository.findAllById(submissionIds));
    }

    @Transactional(readOnly = true)
    public SubmissionGrowthSummaryResponse summarizeSubmission(Submission target) {
        if (target == null || target.getId() == null) {
            return null;
        }
        return summarize(scopeSubmissions(target)).get(target.getId());
    }

    @Transactional(readOnly = true)
    public Map<Long, SubmissionGrowthSummaryResponse> summarize(Collection<Submission> input) {
        List<Submission> submissions = safe(input).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .toList();
        if (submissions.isEmpty()) {
            return Map.of();
        }
        Map<ScopeKey, List<Submission>> byScope = submissions.stream()
                .collect(Collectors.groupingBy(
                        item -> new ScopeKey(item.getStudentProfileId(), item.getProblemId(), item.getAssignmentId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, SubmissionGrowthSummaryResponse> result = new LinkedHashMap<>();
        byScope.values().forEach(scope -> result.putAll(summarizeScope(scope)));
        return result;
    }

    private Map<Long, SubmissionGrowthSummaryResponse> summarizeScope(Collection<Submission> input) {
        List<Submission> submissions = safe(input).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Submission::getId))
                .toList();
        if (submissions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = submissions.stream().map(Submission::getId).toList();
        Set<Long> analyzedIds = analysisRepository.findBySubmissionIdIn(ids).stream()
                .map(SubmissionAnalysis::getSubmissionId)
                .collect(Collectors.toSet());
        Map<Long, TestStats> statsBySubmission = caseResultRepository.summarizeBySubmissionIdIn(ids).stream()
                .collect(Collectors.toMap(
                        SubmissionCaseResultStatsProjection::getSubmissionId,
                        item -> new TestStats(value(item.getPassedTestCases()), value(item.getTotalTestCases()))
                ));
        List<SubmissionDiagnosisFact> facts = factRepository.findBySubmissionIdIn(ids);
        Map<Long, Map<String, SubmissionDiagnosisFact>> factsBySubmission = facts.stream()
                .filter(item -> hasText(item.getNormalizedPointKey()))
                .collect(Collectors.groupingBy(
                        SubmissionDiagnosisFact::getSubmissionId,
                        LinkedHashMap::new,
                        Collectors.toMap(
                                SubmissionDiagnosisFact::getNormalizedPointKey,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new
                        )
                ));
        Map<String, FactDescriptor> descriptors = facts.stream()
                .filter(item -> hasText(item.getNormalizedPointKey()))
                .collect(Collectors.toMap(
                        SubmissionDiagnosisFact::getNormalizedPointKey,
                        this::descriptor,
                        (left, right) -> richer(left, right),
                        LinkedHashMap::new
                ));
        Map<Long, List<SubmissionIssueTransition>> transitionsBySubmission = transitionRepository
                .findByCurrentSubmissionIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        SubmissionIssueTransition::getCurrentSubmissionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Long, SubmissionGrowthSummaryResponse> result = new LinkedHashMap<>();
        Submission previous = null;
        Map<String, SubmissionDiagnosisFact> previousFacts = Map.of();
        TestStats previousStats = null;
        Set<String> seenKeys = new LinkedHashSet<>();

        for (Submission current : submissions) {
            Map<String, SubmissionDiagnosisFact> currentFacts = factsBySubmission.getOrDefault(current.getId(), Map.of());
            TestStats currentStats = statsBySubmission.getOrDefault(current.getId(), TestStats.EMPTY);
            boolean comparable = current.getStudentProfileId() != null && analyzedIds.contains(current.getId());
            boolean effective = comparable && effective(previous, current, previousFacts.keySet(), currentFacts.keySet());
            List<SubmissionGrowthSummaryResponse.IssueSignal> signals = comparable
                    ? issueSignals(current, previous, currentFacts, previousFacts,
                    transitionsBySubmission.getOrDefault(current.getId(), List.of()), descriptors, seenKeys, effective)
                    : List.of();
            Counts counts = counts(signals);
            Integer passedDelta = comparable && previous != null && currentStats.comparableWith(previousStats)
                    ? currentStats.passed() - previousStats.passed()
                    : null;
            Priority priority = priority(signals);
            String state = growthState(current, previous, comparable, effective, passedDelta, counts);
            result.put(current.getId(), SubmissionGrowthSummaryResponse.builder()
                    .submissionId(current.getId())
                    .growthState(state)
                    .ruleVersion(RULE_VERSION)
                    .effectiveAttempt(effective)
                    .comparable(comparable)
                    .comparisonSubmissionId(previous == null ? null : previous.getId())
                    .duplicateOfSubmissionId(!effective && comparable && previous != null ? previous.getId() : null)
                    .passedTestCases(currentStats.passed())
                    .totalTestCases(currentStats.total())
                    .previousPassedTestCases(previousStats == null ? null : previousStats.passed())
                    .previousTotalTestCases(previousStats == null ? null : previousStats.total())
                    .passedTestCaseDelta(passedDelta)
                    .persistedCount(counts.persisted())
                    .newCount(counts.added())
                    .recurringCount(counts.recurring())
                    .notObservedCount(counts.notObserved())
                    .recoveredCount(counts.recovered())
                    .uncomparableCount(counts.uncomparable())
                    .improvementCount(counts.improvements())
                    .unresolvedCount(counts.persisted() + counts.added() + counts.recurring())
                    .priorityIssueTitle(priority.title())
                    .priorityIssueStatus(priority.status())
                    .dataCompletenessStatus(completeness(current, comparable))
                    .issueSignals(signals)
                    .build());

            if (comparable) {
                seenKeys.addAll(currentFacts.keySet());
            }
            if (effective) {
                previous = current;
                previousFacts = currentFacts;
                previousStats = currentStats;
            }
        }
        return result;
    }

    private List<Submission> scopeSubmissions(Submission target) {
        if (target.getStudentProfileId() == null || target.getProblemId() == null) {
            return List.of(target);
        }
        if (target.getAssignmentId() == null) {
            return submissionRepository.findByProblemIdOrderBySubmittedAtAsc(target.getProblemId()).stream()
                    .filter(item -> item.getAssignmentId() == null)
                    .filter(item -> Objects.equals(item.getStudentProfileId(), target.getStudentProfileId()))
                    .toList();
        }
        return submissionRepository.findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(
                target.getAssignmentId(), target.getProblemId(), target.getStudentProfileId());
    }

    private List<SubmissionGrowthSummaryResponse.IssueSignal> issueSignals(
            Submission current,
            Submission previous,
            Map<String, SubmissionDiagnosisFact> currentFacts,
            Map<String, SubmissionDiagnosisFact> previousFacts,
            List<SubmissionIssueTransition> persisted,
            Map<String, FactDescriptor> descriptors,
            Set<String> seenKeys,
            boolean effective
    ) {
        if (!persisted.isEmpty()) {
            return persisted.stream()
                    .filter(item -> !"scope:uncomparable".equals(item.getNormalizedPointKey()))
                    .map(item -> signal(item, descriptors.get(item.getNormalizedPointKey())))
                    .toList();
        }
        List<SubmissionGrowthSummaryResponse.IssueSignal> signals = new ArrayList<>();
        for (Map.Entry<String, SubmissionDiagnosisFact> entry : currentFacts.entrySet()) {
            String key = entry.getKey();
            String status = previous == null ? "NEW"
                    : previousFacts.containsKey(key) ? "PERSISTED"
                    : seenKeys.contains(key) ? "RECURRED" : "NEW";
            signals.add(signal(entry.getValue(), status, effective));
        }
        if (previous != null) {
            for (Map.Entry<String, SubmissionDiagnosisFact> entry : previousFacts.entrySet()) {
                if (!currentFacts.containsKey(entry.getKey())) {
                    signals.add(signal(entry.getValue(),
                            current.getVerdict() == Submission.Verdict.ACCEPTED ? "RECOVERED" : "NOT_OBSERVED",
                            effective));
                }
            }
        }
        return signals;
    }

    private SubmissionGrowthSummaryResponse.IssueSignal signal(
            SubmissionIssueTransition item,
            FactDescriptor descriptor
    ) {
        return SubmissionGrowthSummaryResponse.IssueSignal.builder()
                .normalizedPointKey(item.getNormalizedPointKey())
                .title(firstText(item.getTitle(), descriptor == null ? null : descriptor.title()))
                .displayCategory(firstText(item.getDisplayCategory(), descriptor == null ? null : descriptor.displayCategory()))
                .changeStatus(item.getTransitionType())
                .pointKeySource(firstText(item.getPointKeySource(), descriptor == null ? null : descriptor.pointKeySource()))
                .knowledgePathStatus(descriptor == null ? null : descriptor.knowledgePathStatus())
                .knowledgePath(descriptor == null ? List.of() : descriptor.knowledgePath())
                .rawOccurrenceCount(item.getRawOccurrenceCount())
                .effectiveOccurrenceCount(item.getEffectiveOccurrenceCount())
                .evidenceSubmissionIds(parseIds(item.getEvidenceSubmissionIdsJson()))
                .build();
    }

    private SubmissionGrowthSummaryResponse.IssueSignal signal(
            SubmissionDiagnosisFact fact,
            String status,
            boolean effective
    ) {
        return SubmissionGrowthSummaryResponse.IssueSignal.builder()
                .normalizedPointKey(fact.getNormalizedPointKey())
                .title(fact.getTitle())
                .displayCategory(fact.getDisplayCategory())
                .changeStatus(status)
                .pointKeySource(fact.getPointKeySource())
                .knowledgePathStatus(fact.getKnowledgePathStatus())
                .knowledgePath(parseStrings(fact.getKnowledgePathJson()))
                .rawOccurrenceCount(1)
                .effectiveOccurrenceCount(effective ? 1 : 0)
                .evidenceSubmissionIds(List.of(fact.getSubmissionId()))
                .build();
    }

    private Counts counts(List<SubmissionGrowthSummaryResponse.IssueSignal> signals) {
        long persisted = 0;
        long added = 0;
        long recurring = 0;
        long notObserved = 0;
        long recovered = 0;
        long uncomparable = 0;
        long improvements = 0;
        for (SubmissionGrowthSummaryResponse.IssueSignal signal : signals) {
            if ("IMPROVEMENT".equalsIgnoreCase(signal.getDisplayCategory())) {
                if (!List.of("NOT_OBSERVED", "RECOVERED").contains(signal.getChangeStatus())) {
                    improvements++;
                }
                continue;
            }
            switch (String.valueOf(signal.getChangeStatus())) {
                case "PERSISTED" -> persisted++;
                case "NEW" -> added++;
                case "RECURRED" -> recurring++;
                case "NOT_OBSERVED" -> notObserved++;
                case "RECOVERED" -> recovered++;
                case "UNCOMPARABLE" -> uncomparable++;
                default -> {
                }
            }
        }
        return new Counts(persisted, added, recurring, notObserved, recovered, uncomparable, improvements);
    }

    private String growthState(
            Submission current,
            Submission previous,
            boolean comparable,
            boolean effective,
            Integer passedDelta,
            Counts counts
    ) {
        if (!comparable) {
            return "UNCOMPARABLE";
        }
        if (previous != null && !effective) {
            return "DUPLICATE_NO_CHANGE";
        }
        if (current.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "COMPLETED";
        }
        if (previous == null) {
            return "FIRST_RECORD";
        }
        int stageDelta = verdictStage(current.getVerdict()) - verdictStage(previous.getVerdict());
        long positive = counts.notObserved() + counts.recovered();
        long risk = counts.added() + counts.recurring();
        boolean judgeImproved = passedDelta != null && passedDelta > 0 || stageDelta > 0;
        boolean judgeRegressed = passedDelta != null && passedDelta < 0 || stageDelta < 0;
        if (positive > 0 && risk > 0) {
            return judgeImproved || positive > risk ? "MIXED_PROGRESS" : "ISSUE_SHIFTED";
        }
        if (judgeRegressed || risk > 0 && positive == 0) {
            return "REGRESSED";
        }
        if (judgeImproved || positive > 0) {
            return "CLEAR_PROGRESS";
        }
        return "STALLED";
    }

    private int verdictStage(Submission.Verdict verdict) {
        if (verdict == null) {
            return 0;
        }
        return switch (verdict) {
            case ACCEPTED -> 4;
            case WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED -> 3;
            case RUNTIME_ERROR -> 2;
            case COMPILATION_ERROR -> 1;
            case PENDING, INTERNAL_ERROR -> 0;
        };
    }

    private boolean effective(Submission previous, Submission current, Set<String> previousKeys, Set<String> currentKeys) {
        if (previous == null) {
            return true;
        }
        return !Objects.equals(pointKeyFactory.sourceFingerprint(previous.getSourceCode()), pointKeyFactory.sourceFingerprint(current.getSourceCode()))
                || !Objects.equals(previous.getVerdict(), current.getVerdict())
                || !Objects.equals(previousKeys, currentKeys);
    }

    private Priority priority(List<SubmissionGrowthSummaryResponse.IssueSignal> signals) {
        return signals.stream()
                .filter(item -> !"IMPROVEMENT".equalsIgnoreCase(item.getDisplayCategory()))
                .filter(item -> List.of("RECURRED", "NEW", "PERSISTED").contains(item.getChangeStatus()))
                .sorted(Comparator.comparingInt(item -> priorityRank(item.getChangeStatus())))
                .map(item -> new Priority(item.getTitle(), item.getChangeStatus()))
                .findFirst()
                .orElse(new Priority(null, null));
    }

    private int priorityRank(String status) {
        return switch (String.valueOf(status)) {
            case "RECURRED" -> 0;
            case "NEW" -> 1;
            case "PERSISTED" -> 2;
            default -> 3;
        };
    }

    private FactDescriptor descriptor(SubmissionDiagnosisFact fact) {
        return new FactDescriptor(
                fact.getTitle(),
                fact.getDisplayCategory(),
                fact.getPointKeySource(),
                fact.getKnowledgePathStatus(),
                parseStrings(fact.getKnowledgePathJson())
        );
    }

    private FactDescriptor richer(FactDescriptor left, FactDescriptor right) {
        return right.knowledgePath().size() > left.knowledgePath().size() ? right : left;
    }

    private String completeness(Submission submission, boolean comparable) {
        if (submission.getStudentProfileId() == null) {
            return "IDENTITY_MISSING";
        }
        return comparable ? "COMPLETE" : "ANALYSIS_MISSING";
    }

    private List<String> parseStrings(String json) {
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

    private List<Long> parseIds(String json) {
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

    private int value(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safe(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private record TestStats(int passed, int total) {
        private static final TestStats EMPTY = new TestStats(0, 0);

        private boolean comparableWith(TestStats other) {
            return other != null && total > 0 && total == other.total;
        }
    }

    private record FactDescriptor(
            String title,
            String displayCategory,
            String pointKeySource,
            String knowledgePathStatus,
            List<String> knowledgePath
    ) {
    }

    private record Counts(
            long persisted,
            long added,
            long recurring,
            long notObserved,
            long recovered,
            long uncomparable,
            long improvements
    ) {
    }

    private record Priority(String title, String status) {
    }

    private record ScopeKey(Long studentProfileId, Long problemId, Long assignmentId) {
    }
}

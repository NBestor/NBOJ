package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.persistence.SubmissionIssueTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentIssueLifecycleReadService {

    private final SubmissionIssueTransitionRepository transitionRepository;
    private final IssuePointKeyFactory pointKeyFactory;
    private final SubmissionEvidenceProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void hydrate(Long submissionId, StudentAiFeedbackResponse feedback) {
        if (!properties.isLifecycleReadEnabled() || submissionId == null || feedback == null) {
            return;
        }
        List<SubmissionIssueTransition> transitions = transitionRepository
                .findByCurrentSubmissionIdOrderByDisplayCategoryAscTitleAsc(submissionId);
        if (transitions.isEmpty()) {
            feedback.setIssueChanges(List.of());
            return;
        }
        Map<String, SubmissionIssueTransition> byPoint = transitions.stream()
                .filter(item -> item.getCurrentFactId() != null)
                .collect(Collectors.toMap(
                        SubmissionIssueTransition::getNormalizedPointKey,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Long> problemCounts = affectedProblemCounts(transitions);
        hydrateItems(feedback.getRepairItems(), "REPAIR", byPoint, problemCounts);
        hydrateItems(feedback.getImprovementItems(), "IMPROVEMENT", byPoint, problemCounts);
        List<StudentAiFeedbackResponse.IssueLifecycleItem> issueChanges = transitions.stream()
                .map(item -> toResponse(item, problemCounts.getOrDefault(item.getNormalizedPointKey(), item.getAffectedProblemCount())))
                .toList();
        feedback.setIssueChanges(issueChanges);
        feedback.setIssueChangeSummary(summary(issueChanges));
    }

    private void hydrateItems(
            List<StudentAiFeedbackResponse.FeedbackItem> items,
            String factType,
            Map<String, SubmissionIssueTransition> transitions,
            Map<String, Long> problemCounts
    ) {
        for (StudentAiFeedbackResponse.FeedbackItem item : safe(items)) {
            if (item == null) {
                continue;
            }
            IssuePointKeyFactory.Identity identity = pointKeyFactory.identity(
                    factType,
                    item.getMistakePointId(),
                    item.getImprovementPointId(),
                    item.getSkillUnitId(),
                    item.getKnowledgePath(),
                    item.getTitle()
            );
            item.setNormalizedPointKey(identity.key());
            item.setPointKeySource(identity.source());
            SubmissionIssueTransition transition = transitions.get(identity.key());
            if (transition == null) {
                item.setChangeStatus("UNCOMPARABLE");
                item.setPersonalLabels(List.of("EVIDENCE_INSUFFICIENT"));
                continue;
            }
            long affectedProblems = problemCounts.getOrDefault(identity.key(), transition.getAffectedProblemCount());
            item.setChangeStatus(transition.getTransitionType());
            item.setPersonalLabels(labels(transition, affectedProblems));
            item.setRawOccurrenceCount(transition.getRawOccurrenceCount());
            item.setEffectiveOccurrenceCount(transition.getEffectiveOccurrenceCount());
            item.setConsecutiveEffectiveCount(transition.getConsecutiveEffectiveCount());
            item.setAffectedProblemCount(affectedProblems);
            item.setPreviousSubmissionId(transition.getPreviousSubmissionId());
            item.setLifecycleEvidenceSubmissionIds(parseIds(transition.getEvidenceSubmissionIdsJson()));
        }
    }

    private Map<String, Long> affectedProblemCounts(List<SubmissionIssueTransition> current) {
        Long studentProfileId = current.stream()
                .map(SubmissionIssueTransition::getStudentProfileId)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        if (studentProfileId == null) {
            return Map.of();
        }
        Map<String, Set<Long>> problems = new LinkedHashMap<>();
        for (SubmissionIssueTransition item : transitionRepository.findByStudentProfileIdOrderByCurrentSubmissionIdAsc(studentProfileId)) {
            if (item.getCurrentFactId() == null || item.getProblemId() == null) {
                continue;
            }
            problems.computeIfAbsent(item.getNormalizedPointKey(), ignored -> new LinkedHashSet<>()).add(item.getProblemId());
        }
        return problems.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> (long) entry.getValue().size(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private StudentAiFeedbackResponse.IssueLifecycleItem toResponse(SubmissionIssueTransition item, long affectedProblems) {
        return StudentAiFeedbackResponse.IssueLifecycleItem.builder()
                .normalizedPointKey(item.getNormalizedPointKey())
                .pointKeySource(item.getPointKeySource())
                .title(item.getTitle())
                .factType(item.getFactType())
                .displayCategory(item.getDisplayCategory())
                .changeStatus(item.getTransitionType())
                .personalLabels(labels(item, affectedProblems))
                .rawOccurrenceCount(item.getRawOccurrenceCount())
                .effectiveOccurrenceCount(item.getEffectiveOccurrenceCount())
                .consecutiveEffectiveCount(item.getConsecutiveEffectiveCount())
                .affectedProblemCount(affectedProblems)
                .effectiveAttempt(item.isEffectiveAttempt())
                .previousSubmissionId(item.getPreviousSubmissionId())
                .currentSubmissionId(item.getCurrentSubmissionId())
                .firstSeenSubmissionId(item.getFirstSeenSubmissionId())
                .lastSeenSubmissionId(item.getLastSeenSubmissionId())
                .evidenceSubmissionIds(parseIds(item.getEvidenceSubmissionIdsJson()))
                .build();
    }

    private List<String> labels(SubmissionIssueTransition item, long affectedProblems) {
        List<String> labels = new ArrayList<>();
        if (item.getPersonalLabel() != null && !item.getPersonalLabel().isBlank()) {
            labels.add(item.getPersonalLabel());
        }
        if (affectedProblems >= properties.getCrossProblemWeaknessThreshold()) {
            labels.add("CROSS_PROBLEM_WEAKNESS");
        }
        return labels.stream().distinct().toList();
    }

    private StudentAiFeedbackResponse.IssueChangeSummary summary(List<StudentAiFeedbackResponse.IssueLifecycleItem> items) {
        return StudentAiFeedbackResponse.IssueChangeSummary.builder()
                .persistedCount(count(items, "PERSISTED"))
                .newCount(count(items, "NEW"))
                .recurringCount(count(items, "RECURRED"))
                .notObservedCount(count(items, "NOT_OBSERVED"))
                .recoveredCount(count(items, "RECOVERED"))
                .uncomparableCount(count(items, "UNCOMPARABLE"))
                .improvementCount(items.stream().filter(item -> "IMPROVEMENT".equals(item.getDisplayCategory())).count())
                .build();
    }

    private long count(List<StudentAiFeedbackResponse.IssueLifecycleItem> items, String status) {
        return items.stream().filter(item -> status.equals(item.getChangeStatus())).count();
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}

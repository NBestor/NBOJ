package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.StudentRecommendationResponse;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentRecommendationEventService {

    public static final String EVENT_EXPOSED = "EXPOSED";
    public static final String EVENT_CLICKED = "CLICKED";
    public static final String EVENT_ENTERED_PROBLEM = "ENTERED_PROBLEM";
    public static final String EVENT_SUBMITTED = "SUBMITTED";

    private final StudentRecommendationEventRepository eventRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionDiagnosisFactRepository diagnosisFactRepository;
    private final SubmissionCaseResultRepository caseResultRepository;
    private final DiagnosisReportReader diagnosisReportReader;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordExposure(Long studentProfileId,
                               StudentRecommendationResponse.RecommendationItem item) {
        if (studentProfileId == null || item == null || item.getRecommendationToken() == null || item.getRecommendationToken().isBlank()) {
            return;
        }
        if (eventRepository.findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(item.getRecommendationToken(), EVENT_EXPOSED).isPresent()) {
            return;
        }
        eventRepository.save(baseEvent(studentProfileId, item, EVENT_EXPOSED)
                .build());
    }

    @Transactional
    public void recordClick(Long studentProfileId, String recommendationToken) {
        recordSimpleEvent(studentProfileId, recommendationToken, EVENT_CLICKED);
    }

    @Transactional
    public void recordEnteredProblem(Long studentProfileId, String recommendationToken) {
        recordSimpleEvent(studentProfileId, recommendationToken, EVENT_ENTERED_PROBLEM);
    }

    private void recordSimpleEvent(Long studentProfileId, String recommendationToken, String eventType) {
        StudentRecommendationEvent exposure = findExposureOrNull(recommendationToken);
        if (exposure == null) {
            return;
        }
        eventRepository.save(StudentRecommendationEvent.builder()
                .recommendationToken(recommendationToken)
                .studentProfileId(studentProfileId == null ? exposure.getStudentProfileId() : studentProfileId)
                .type(exposure.getType())
                .assignmentId(exposure.getAssignmentId())
                .problemId(exposure.getProblemId())
                .focusAbility(exposure.getFocusAbility())
                .focusTags(exposure.getFocusTags())
                .sourceSubmissionId(exposure.getSourceSubmissionId())
                .focusIssueIds(exposure.getFocusIssueIds())
                .focusPointKeys(exposure.getFocusPointKeys())
                .focusKnowledgeNodeCodes(exposure.getFocusKnowledgeNodeCodes())
                .focusSkillUnitCodes(exposure.getFocusSkillUnitCodes())
                .focusMistakePointCodes(exposure.getFocusMistakePointCodes())
                .focusTestSemanticCodes(exposure.getFocusTestSemanticCodes())
                .strategy(exposure.getStrategy())
                .learningHypothesis(exposure.getLearningHypothesis())
                .expectedCompletionSignal(exposure.getExpectedCompletionSignal())
                .riskLevel(exposure.getRiskLevel())
                .fallbackAction(exposure.getFallbackAction())
                .eventType(eventType)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void recordSubmission(Submission submission, String recommendationToken) {
        if (submission == null || recommendationToken == null || recommendationToken.isBlank()) {
            return;
        }
        StudentRecommendationEvent exposure = findExposureOrNull(recommendationToken);
        if (exposure == null) {
            return;
        }
        SubmissionAnalysis analysis = submissionAnalysisRepository.findBySubmissionId(submission.getId()).orElse(null);
        EvidenceSnapshot followup = evidenceFor(submission.getId());
        eventRepository.save(StudentRecommendationEvent.builder()
                .recommendationToken(recommendationToken)
                .studentProfileId(submission.getStudentProfileId() == null ? exposure.getStudentProfileId() : submission.getStudentProfileId())
                .type(exposure.getType())
                .assignmentId(submission.getAssignmentId() == null ? exposure.getAssignmentId() : submission.getAssignmentId())
                .problemId(exposure.getProblemId() == null ? submission.getProblemId() : exposure.getProblemId())
                .focusAbility(exposure.getFocusAbility())
                .focusTags(exposure.getFocusTags())
                .sourceSubmissionId(exposure.getSourceSubmissionId())
                .focusIssueIds(exposure.getFocusIssueIds())
                .focusPointKeys(exposure.getFocusPointKeys())
                .focusKnowledgeNodeCodes(exposure.getFocusKnowledgeNodeCodes())
                .focusSkillUnitCodes(exposure.getFocusSkillUnitCodes())
                .focusMistakePointCodes(exposure.getFocusMistakePointCodes())
                .focusTestSemanticCodes(exposure.getFocusTestSemanticCodes())
                .strategy(exposure.getStrategy())
                .learningHypothesis(exposure.getLearningHypothesis())
                .expectedCompletionSignal(exposure.getExpectedCompletionSignal())
                .riskLevel(exposure.getRiskLevel())
                .fallbackAction(exposure.getFallbackAction())
                .eventType(EVENT_SUBMITTED)
                .followupSubmissionId(submission.getId())
                .followupVerdict(submission.getVerdict() == null ? null : submission.getVerdict().name())
                .followupIssueTag(first(diagnosisReportReader.issueTags(analysis)))
                .followupFineGrainedTag(first(diagnosisReportReader.fineGrainedTags(analysis)))
                .followupIssueIds(toJson(followup.issueIds()))
                .followupPointKeys(toJson(followup.pointKeys()))
                .followupKnowledgeNodeCodes(toJson(followup.knowledgeNodeCodes()))
                .followupSkillUnitCodes(toJson(followup.skillUnitCodes()))
                .followupMistakePointCodes(toJson(followup.mistakePointCodes()))
                .followupFailedTestSemanticCodes(toJson(followup.failedTestSemanticCodes()))
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void backfillSubmissionAnalysis(Submission submission, SubmissionAnalysis analysis) {
        if (submission == null || submission.getId() == null || analysis == null) {
            return;
        }
        List<StudentRecommendationEvent> events = eventRepository
                .findByFollowupSubmissionIdAndEventTypeOrderByCreatedAtDesc(submission.getId(), EVENT_SUBMITTED);
        if (events.isEmpty()) {
            return;
        }
        String verdict = submission.getVerdict() == null ? null : submission.getVerdict().name();
        String issueTag = first(diagnosisReportReader.issueTags(analysis));
        String fineGrainedTag = first(diagnosisReportReader.fineGrainedTags(analysis));
        EvidenceSnapshot followup = evidenceFor(submission.getId());
        events.forEach(event -> {
            boolean changed = false;
            if (!Objects.equals(event.getFollowupVerdict(), verdict)) {
                event.setFollowupVerdict(verdict);
                changed = true;
            }
            if (!Objects.equals(event.getFollowupIssueTag(), issueTag)) {
                event.setFollowupIssueTag(issueTag);
                changed = true;
            }
            if (!Objects.equals(event.getFollowupFineGrainedTag(), fineGrainedTag)) {
                event.setFollowupFineGrainedTag(fineGrainedTag);
                changed = true;
            }
            changed |= setIfChanged(event.getFollowupIssueIds(), toJson(followup.issueIds()), event::setFollowupIssueIds);
            changed |= setIfChanged(event.getFollowupPointKeys(), toJson(followup.pointKeys()), event::setFollowupPointKeys);
            changed |= setIfChanged(event.getFollowupKnowledgeNodeCodes(), toJson(followup.knowledgeNodeCodes()), event::setFollowupKnowledgeNodeCodes);
            changed |= setIfChanged(event.getFollowupSkillUnitCodes(), toJson(followup.skillUnitCodes()), event::setFollowupSkillUnitCodes);
            changed |= setIfChanged(event.getFollowupMistakePointCodes(), toJson(followup.mistakePointCodes()), event::setFollowupMistakePointCodes);
            changed |= setIfChanged(event.getFollowupFailedTestSemanticCodes(), toJson(followup.failedTestSemanticCodes()), event::setFollowupFailedTestSemanticCodes);
            if (changed) {
                eventRepository.save(event);
            }
        });
    }

    public List<StudentRecommendationEvent> findEvents(Long studentProfileId) {
        if (studentProfileId == null) {
            return List.of();
        }
        return eventRepository.findByStudentProfileIdOrderByCreatedAtDesc(studentProfileId);
    }

    public String tokenFor(Long studentProfileId,
                           StudentRecommendationResponse.RecommendationItem item) {
        if (item == null) {
            return "";
        }
        Submission sourceSubmission = sourceSubmission(studentProfileId, item);
        return String.join(":",
                "rec",
                String.valueOf(studentProfileId == null ? 0 : studentProfileId),
                nullToBlank(item.getType()),
                String.valueOf(item.getProblemId() == null ? 0 : item.getProblemId()),
                Integer.toHexString(Objects.hash(
                        nullToBlank(item.getFocusAbility()),
                        item.getFocusTags() == null ? List.of() : item.getFocusTags(),
                        item.getEvidenceProblemIds() == null ? List.of() : item.getEvidenceProblemIds(),
                        item.getAssignmentId() == null ? 0 : item.getAssignmentId(),
                        sourceSubmission == null || sourceSubmission.getId() == null ? 0 : sourceSubmission.getId(),
                        nullToBlank(item.getStrategy()),
                        nullToBlank(item.getRiskLevel()),
                        nullToBlank(item.getExpectedCompletionSignal())
                ))
        );
    }

    private StudentRecommendationEvent findExposureOrNull(String recommendationToken) {
        if (recommendationToken == null || recommendationToken.isBlank()) {
            return null;
        }
        return eventRepository.findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(recommendationToken, EVENT_EXPOSED)
                .orElse(null);
    }

    private StudentRecommendationEvent.StudentRecommendationEventBuilder baseEvent(Long studentProfileId,
                                                                                   StudentRecommendationResponse.RecommendationItem item,
                                                                                   String eventType) {
        Submission sourceSubmission = sourceSubmission(studentProfileId, item);
        EvidenceSnapshot focus = sourceSubmission == null
                ? EvidenceSnapshot.empty()
                : evidenceFor(sourceSubmission.getId());
        return StudentRecommendationEvent.builder()
                .recommendationToken(item.getRecommendationToken())
                .studentProfileId(studentProfileId)
                .type(item.getType())
                .assignmentId(item.getAssignmentId())
                .problemId(item.getProblemId())
                .focusAbility(item.getFocusAbility())
                .focusTags(toJson(item.getFocusTags()))
                .sourceSubmissionId(sourceSubmission == null ? null : sourceSubmission.getId())
                .focusIssueIds(toJson(focus.issueIds()))
                .focusPointKeys(toJson(focus.pointKeys()))
                .focusKnowledgeNodeCodes(toJson(focus.knowledgeNodeCodes()))
                .focusSkillUnitCodes(toJson(focus.skillUnitCodes()))
                .focusMistakePointCodes(toJson(focus.mistakePointCodes()))
                .focusTestSemanticCodes(toJson(focus.failedTestSemanticCodes()))
                .strategy(item.getStrategy())
                .learningHypothesis(item.getLearningHypothesis())
                .expectedCompletionSignal(item.getExpectedCompletionSignal())
                .riskLevel(item.getRiskLevel())
                .fallbackAction(item.getFallbackAction())
                .eventType(eventType)
                .createdAt(LocalDateTime.now());
    }

    private Submission sourceSubmission(Long studentProfileId,
                                        StudentRecommendationResponse.RecommendationItem item) {
        if (studentProfileId == null || item == null) {
            return null;
        }
        Set<Long> targetProblems = new LinkedHashSet<>();
        if (item.getProblemId() != null) {
            targetProblems.add(item.getProblemId());
        }
        if (item.getEvidenceProblemIds() != null) {
            item.getEvidenceProblemIds().stream().filter(Objects::nonNull).forEach(targetProblems::add);
        }
        return submissionRepository.findByStudentProfileIdInOrderBySubmittedAtDesc(List.of(studentProfileId))
                .stream()
                .filter(submission -> targetProblems.isEmpty() || targetProblems.contains(submission.getProblemId()))
                .findFirst()
                .orElse(null);
    }

    private EvidenceSnapshot evidenceFor(Long submissionId) {
        if (submissionId == null) {
            return EvidenceSnapshot.empty();
        }
        List<SubmissionDiagnosisFact> facts = java.util.Optional
                .ofNullable(diagnosisFactRepository.findBySubmissionId(submissionId))
                .orElse(List.of());
        List<SubmissionCaseResult> failedCases = java.util.Optional
                .ofNullable(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(submissionId))
                .orElse(List.of())
                .stream()
                .filter(result -> !Boolean.TRUE.equals(result.getPassed()))
                .toList();
        LinkedHashSet<String> issueIds = new LinkedHashSet<>();
        LinkedHashSet<String> pointKeys = new LinkedHashSet<>();
        LinkedHashSet<String> knowledgeCodes = new LinkedHashSet<>();
        LinkedHashSet<String> skillCodes = new LinkedHashSet<>();
        LinkedHashSet<String> mistakeCodes = new LinkedHashSet<>();
        for (SubmissionDiagnosisFact fact : facts) {
            add(issueIds, fact.getIssueId());
            add(pointKeys, fact.getNormalizedPointKey());
            add(knowledgeCodes, fact.getProvisionalNodeCode());
            parseStringList(fact.getKnowledgePathJson()).forEach(knowledgeCodes::add);
            add(skillCodes, fact.getSkillUnitId());
            add(mistakeCodes, fact.getMistakePointId());
        }
        LinkedHashSet<String> testCodes = new LinkedHashSet<>();
        failedCases.forEach(result -> add(testCodes, result.getTestSemanticCode()));
        return new EvidenceSnapshot(
                issueIds.stream().toList(),
                pointKeys.stream().toList(),
                knowledgeCodes.stream().toList(),
                skillCodes.stream().toList(),
                mistakeCodes.stream().toList(),
                testCodes.stream().toList()
        );
    }

    private List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    raw,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void add(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private boolean setIfChanged(String current,
                                 String next,
                                 java.util.function.Consumer<String> setter) {
        if (Objects.equals(current, next)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private String toJson(List<String> values) {
        try {
            List<String> normalized = values == null ? List.of() : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                    .stream()
                    .toList();
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record EvidenceSnapshot(
            List<String> issueIds,
            List<String> pointKeys,
            List<String> knowledgeNodeCodes,
            List<String> skillUnitCodes,
            List<String> mistakePointCodes,
            List<String> failedTestSemanticCodes
    ) {
        private static EvidenceSnapshot empty() {
            return new EvidenceSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}

package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.HintSafetyCheck;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.dto.AiQualityTrendResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassReviewFeedbackRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.HintSafetyCheckRepository;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.onlinejudge.identity.application.CurrentTeacherContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiQualityTrendService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository;
    private final ClassReviewFeedbackRepository classReviewFeedbackRepository;
    private final HintSafetyCheckRepository hintSafetyCheckRepository;
    private final CoachPromptRepository coachPromptRepository;
    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    @Autowired(required = false)
    private CurrentTeacherContext currentTeacherContext;

    public AiQualityTrendResponse buildTrend() {
        List<Assignment> assignments = currentTeacherContext == null
                ? assignmentRepository.findAllByOrderByCreatedAtDesc()
                : assignmentRepository.findByOwnerTeacherIdOrderByCreatedAtDesc(currentTeacherContext.requireTeacherId());
        List<Long> assignmentIds = assignments.stream()
                .map(Assignment::getId)
                .filter(Objects::nonNull)
                .toList();
        if (assignmentIds.isEmpty()) {
            return emptyTrend();
        }

        Map<Long, Assignment> assignmentById = assignments.stream()
                .filter(assignment -> assignment.getId() != null)
                .collect(Collectors.toMap(Assignment::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<Submission> submissions = submissionRepository.findByAssignmentIdIn(assignmentIds);
        Map<Long, List<Submission>> submissionsByAssignment = submissions.stream()
                .filter(submission -> submission.getAssignmentId() != null)
                .collect(Collectors.groupingBy(Submission::getAssignmentId, LinkedHashMap::new, Collectors.toList()));
        List<Long> submissionIds = submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SubmissionAnalysis> analysesBySubmissionId = submissionIds.isEmpty()
                ? Map.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(submissionIds)
                .stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, Function.identity(), (left, right) -> left));
        List<HintSafetyCheck> safetyChecks = submissionIds.isEmpty()
                ? List.of()
                : hintSafetyCheckRepository.findBySubmissionIdIn(submissionIds);
        Map<Long, List<HintSafetyCheck>> safetyChecksBySubmissionId = safetyChecks.stream()
                .filter(check -> check.getSubmissionId() != null)
                .collect(Collectors.groupingBy(HintSafetyCheck::getSubmissionId, LinkedHashMap::new, Collectors.toList()));
        List<CoachPrompt> coachPrompts = submissionIds.isEmpty()
                ? List.of()
                : coachPromptRepository.findBySubmissionIdIn(submissionIds);
        Map<Long, List<CoachPrompt>> coachPromptsBySubmissionId = coachPrompts.stream()
                .filter(prompt -> prompt.getSubmissionId() != null)
                .collect(Collectors.groupingBy(CoachPrompt::getSubmissionId, LinkedHashMap::new, Collectors.toList()));
        List<TeacherDiagnosisCorrection> corrections = teacherDiagnosisCorrectionRepository.findByAssignmentIdIn(assignmentIds);
        Map<Long, List<TeacherDiagnosisCorrection>> correctionsByAssignment = corrections.stream()
                .filter(correction -> correction.getAssignmentId() != null)
                .collect(Collectors.groupingBy(TeacherDiagnosisCorrection::getAssignmentId, LinkedHashMap::new, Collectors.toList()));
        List<ClassReviewFeedback> feedbacks = classReviewFeedbackRepository.findByAssignmentIdIn(assignmentIds);
        Map<Long, List<ClassReviewFeedback>> feedbacksByAssignment = feedbacks.stream()
                .filter(feedback -> feedback.getAssignmentId() != null)
                .collect(Collectors.groupingBy(ClassReviewFeedback::getAssignmentId, LinkedHashMap::new, Collectors.toList()));

        List<AiQualityTrendResponse.AssignmentQualityPoint> points = assignmentIds.stream()
                .map(assignmentId -> buildPoint(
                        assignmentById.get(assignmentId),
                        submissionsByAssignment.getOrDefault(assignmentId, List.of()),
                        analysesFor(submissionsByAssignment.get(assignmentId), analysesBySubmissionId),
                        safetyChecksFor(submissionsByAssignment.get(assignmentId), safetyChecksBySubmissionId),
                        coachPromptsFor(submissionsByAssignment.get(assignmentId), coachPromptsBySubmissionId),
                        analysesBySubmissionId,
                        correctionsByAssignment.getOrDefault(assignmentId, List.of()),
                        feedbacksByAssignment.getOrDefault(assignmentId, List.of())
                ))
                .toList();

        long analyzed = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getAnalyzedSubmissionCount).sum();
        long correctionCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getCorrectionCount).sum();
        long evalCandidateCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getEvalCandidateCount).sum();
        long interventionEvalCandidateCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getInterventionEvalCandidateCount).sum();
        long interventionWaitingFollowupCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getInterventionWaitingFollowupCount).sum();
        long interventionImprovedCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getInterventionImprovedCount).sum();
        long interventionShiftedCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getInterventionShiftedCount).sum();
        long interventionStillStuckCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getInterventionStillStuckCount).sum();
        long lowConfidenceCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getLowConfidenceCount).sum();
        long highLeakRiskCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getHighLeakRiskCount).sum();
        long promptSafetyIncidentCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getPromptSafetyIncidentCount).sum();
        long promptSafetyDowngradeCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getPromptSafetyDowngradeCount).sum();
        long promptSafetyHighRiskDowngradeCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getPromptSafetyHighRiskDowngradeCount).sum();
        long coachSafetyRejectionCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getCoachSafetyRejectionCount).sum();
        long modelCompletedCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getModelCompletedCount).sum();
        long modelPartialCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getModelPartialCount).sum();
        long modelRuntimeFailureCount = points.stream().mapToLong(AiQualityTrendResponse.AssignmentQualityPoint::getModelRuntimeFailureCount).sum();

        return AiQualityTrendResponse.builder()
                .assignmentCount(points.size())
                .analyzedSubmissionCount(analyzed)
                .correctionCount(correctionCount)
                .evalCandidateCount(evalCandidateCount)
                .interventionEvalCandidateCount(interventionEvalCandidateCount)
                .interventionWaitingFollowupCount(interventionWaitingFollowupCount)
                .interventionImprovedCount(interventionImprovedCount)
                .interventionShiftedCount(interventionShiftedCount)
                .interventionStillStuckCount(interventionStillStuckCount)
                .lowConfidenceCount(lowConfidenceCount)
                .highLeakRiskCount(highLeakRiskCount)
                .promptSafetyIncidentCount(promptSafetyIncidentCount)
                .promptSafetyDowngradeCount(promptSafetyDowngradeCount)
                .promptSafetyHighRiskDowngradeCount(promptSafetyHighRiskDowngradeCount)
                .coachSafetyRejectionCount(coachSafetyRejectionCount)
                .modelCompletedCount(modelCompletedCount)
                .modelPartialCount(modelPartialCount)
                .modelRuntimeFailureCount(modelRuntimeFailureCount)
                .correctionRate(AiQualityMetrics.rate(correctionCount, analyzed))
                .lowConfidenceRate(AiQualityMetrics.rate(lowConfidenceCount, analyzed))
                .highLeakRiskRate(AiQualityMetrics.rate(highLeakRiskCount, analyzed))
                .promptSafetyIncidentRate(AiQualityMetrics.rate(promptSafetyIncidentCount, analyzed))
                .modelRuntimeFailureRate(AiQualityMetrics.rate(modelRuntimeFailureCount, analyzed))
                .summary(buildSummary(analyzed, correctionCount, lowConfidenceCount, highLeakRiskCount,
                        promptSafetyDowngradeCount, coachSafetyRejectionCount, modelRuntimeFailureCount,
                        interventionEvalCandidateCount, interventionStillStuckCount, interventionWaitingFollowupCount))
                .assignments(points)
                .correctedTags(buildCorrectedTags(corrections))
                .evalNeededTags(buildEvalNeededTags(corrections))
                .sourceSegments(buildSourceSegments(analysesBySubmissionId, corrections, safetyChecks, coachPrompts))
                .build();
    }

    private AiQualityTrendResponse.AssignmentQualityPoint buildPoint(Assignment assignment,
                                                                     List<Submission> submissions,
                                                                     List<SubmissionAnalysis> analyses,
                                                                     List<HintSafetyCheck> safetyChecks,
                                                                     List<CoachPrompt> coachPrompts,
                                                                     Map<Long, SubmissionAnalysis> analysesBySubmissionId,
                                                                     List<TeacherDiagnosisCorrection> corrections,
                                                                     List<ClassReviewFeedback> feedbacks) {
        AiQualityMetrics metrics = AiQualityMetrics.from(analyses, corrections, diagnosisReportReader);
        InterventionTrendCounts interventionCounts = interventionTrendCounts(submissions, analysesBySubmissionId, feedbacks);
        PromptSafetyTrendCounts promptSafetyCounts = promptSafetyTrendCounts(metrics.highLeakRiskCount(), safetyChecks, coachPrompts);
        return AiQualityTrendResponse.AssignmentQualityPoint.builder()
                .assignmentId(assignment == null ? null : assignment.getId())
                .assignmentTitle(assignment == null ? "" : assignment.getTitle())
                .analyzedSubmissionCount(metrics.analyzedSubmissionCount())
                .correctionCount(metrics.correctionCount())
                .evalCandidateCount(metrics.evalCandidateCount())
                .interventionEvalCandidateCount(interventionCounts.candidateCount())
                .interventionWaitingFollowupCount(interventionCounts.waitingFollowupCount())
                .interventionImprovedCount(interventionCounts.improvedCount())
                .interventionShiftedCount(interventionCounts.shiftedCount())
                .interventionStillStuckCount(interventionCounts.stillStuckCount())
                .lowConfidenceCount(metrics.lowConfidenceCount())
                .highLeakRiskCount(metrics.highLeakRiskCount())
                .promptSafetyIncidentCount(promptSafetyCounts.incidentCount())
                .promptSafetyDowngradeCount(promptSafetyCounts.downgradeCount())
                .promptSafetyHighRiskDowngradeCount(promptSafetyCounts.highRiskDowngradeCount())
                .coachSafetyRejectionCount(promptSafetyCounts.coachSafetyRejectionCount())
                .modelCompletedCount(metrics.modelCompletedCount())
                .modelPartialCount(metrics.modelPartialCount())
                .modelRuntimeFailureCount(metrics.modelRuntimeFailureCount())
                .correctionRate(metrics.correctionRate())
                .lowConfidenceRate(metrics.lowConfidenceRate())
                .highLeakRiskRate(metrics.highLeakRiskRate())
                .promptSafetyIncidentRate(AiQualityMetrics.rate(promptSafetyCounts.incidentCount(), metrics.analyzedSubmissionCount()))
                .modelRuntimeFailureRate(metrics.modelRuntimeFailureRate())
                .summary(buildSummary(metrics.analyzedSubmissionCount(), metrics.correctionCount(), metrics.lowConfidenceCount(), metrics.highLeakRiskCount(),
                        promptSafetyCounts.downgradeCount(), promptSafetyCounts.coachSafetyRejectionCount(), metrics.modelRuntimeFailureCount(),
                        interventionCounts.candidateCount(), interventionCounts.stillStuckCount(), interventionCounts.waitingFollowupCount()))
                .build();
    }

    private List<SubmissionAnalysis> analysesFor(List<Submission> submissions,
                                                 Map<Long, SubmissionAnalysis> analysesBySubmissionId) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        return submissions.stream()
                .map(Submission::getId)
                .map(analysesBySubmissionId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<HintSafetyCheck> safetyChecksFor(List<Submission> submissions,
                                                  Map<Long, List<HintSafetyCheck>> safetyChecksBySubmissionId) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<HintSafetyCheck>> safeChecks = safetyChecksBySubmissionId == null ? Map.of() : safetyChecksBySubmissionId;
        return submissions.stream()
                .map(Submission::getId)
                .map(safeChecks::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private List<CoachPrompt> coachPromptsFor(List<Submission> submissions,
                                              Map<Long, List<CoachPrompt>> coachPromptsBySubmissionId) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CoachPrompt>> safePrompts = coachPromptsBySubmissionId == null ? Map.of() : coachPromptsBySubmissionId;
        return submissions.stream()
                .map(Submission::getId)
                .map(safePrompts::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    private InterventionTrendCounts interventionTrendCounts(List<Submission> submissions,
                                                            Map<Long, SubmissionAnalysis> analysesBySubmissionId,
                                                            List<ClassReviewFeedback> feedbacks) {
        long waiting = 0;
        long improved = 0;
        long shifted = 0;
        long stillStuck = 0;
        List<Submission> safeSubmissions = submissions == null ? List.of() : submissions;
        Map<Long, SubmissionAnalysis> safeAnalyses = analysesBySubmissionId == null ? Map.of() : analysesBySubmissionId;
        for (ClassReviewFeedback feedback : feedbacks == null ? List.<ClassReviewFeedback>of() : feedbacks) {
            if (!ClassReviewFeedbackService.ACTION_ACCEPTED.equals(feedback.getActionType())
                    && !ClassReviewFeedbackService.ACTION_MODIFIED.equals(feedback.getActionType())) {
                continue;
            }
            String status = interventionStatus(feedback, safeSubmissions, safeAnalyses);
            switch (status) {
                case TeacherInterventionImpactAnalyzer.STATUS_WAITING_FOLLOWUP -> waiting++;
                case TeacherInterventionImpactAnalyzer.STATUS_IMPROVED -> improved++;
                case TeacherInterventionImpactAnalyzer.STATUS_SHIFTED -> shifted++;
                case TeacherInterventionImpactAnalyzer.STATUS_STILL_STUCK -> stillStuck++;
                default -> {
                }
            }
        }
        return new InterventionTrendCounts(improved + shifted + stillStuck, waiting, improved, shifted, stillStuck);
    }

    private PromptSafetyTrendCounts promptSafetyTrendCounts(long highLeakRiskCount,
                                                            List<HintSafetyCheck> safetyChecks,
                                                            List<CoachPrompt> coachPrompts) {
        long downgradeCount = 0;
        long highRiskDowngradeCount = 0;
        for (HintSafetyCheck check : safetyChecks == null ? List.<HintSafetyCheck>of() : safetyChecks) {
            int weight = riskWeight(check == null ? null : check.getRiskLevel());
            if (weight >= 2) {
                downgradeCount++;
            }
            if (weight >= 3) {
                highRiskDowngradeCount++;
            }
        }
        long coachSafetyRejectionCount = coachSafetyRejectionCount(coachPrompts);
        return new PromptSafetyTrendCounts(
                highLeakRiskCount + downgradeCount + coachSafetyRejectionCount,
                downgradeCount,
                highRiskDowngradeCount,
                coachSafetyRejectionCount
        );
    }

    private long coachSafetyRejectionCount(List<CoachPrompt> coachPrompts) {
        return (coachPrompts == null ? List.<CoachPrompt>of() : coachPrompts)
                .stream()
                .filter(prompt -> prompt != null && "SAFETY_REJECTED".equalsIgnoreCase(prompt.getModelFailureReason()))
                .count();
    }

    private String interventionStatus(ClassReviewFeedback feedback,
                                      List<Submission> submissions,
                                      Map<Long, SubmissionAnalysis> analysesBySubmissionId) {
        List<Submission> followups = submissions.stream()
                .filter(Objects::nonNull)
                .filter(submission -> feedback.getExampleProblemId() == null
                        || Objects.equals(submission.getProblemId(), feedback.getExampleProblemId()))
                .filter(submission -> feedback.getCreatedAt() == null || isAfter(submission.getSubmittedAt(), feedback.getCreatedAt()))
                .sorted(Comparator
                        .comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(Submission::getId, Comparator.nullsLast(Long::compareTo))
                        .reversed())
                .toList();
        if (followups.isEmpty()) {
            return TeacherInterventionImpactAnalyzer.STATUS_WAITING_FOLLOWUP;
        }
        Submission latest = followups.get(0);
        if (latest.getVerdict() == Submission.Verdict.ACCEPTED) {
            return TeacherInterventionImpactAnalyzer.STATUS_IMPROVED;
        }
        SubmissionAnalysis analysis = latest.getId() == null ? null : analysesBySubmissionId.get(latest.getId());
        if (analysis == null) {
            return TeacherInterventionImpactAnalyzer.STATUS_WAITING_FOLLOWUP;
        }
        List<String> expectedTags = parseEvidenceTags(feedback.getEvidenceTags());
        if (!expectedTags.isEmpty() && mergedTags(analysis).stream().anyMatch(expectedTags::contains)) {
            return TeacherInterventionImpactAnalyzer.STATUS_STILL_STUCK;
        }
        return TeacherInterventionImpactAnalyzer.STATUS_SHIFTED;
    }

    private boolean isAfter(LocalDateTime value, LocalDateTime anchor) {
        return value != null && value.isAfter(anchor);
    }

    private List<String> parseEvidenceTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of(trimmed);
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(body.split(","))
                .map(value -> value.replace("\"", "").trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> mergedTags(SubmissionAnalysis analysis) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        diagnosisReportReader.fineGrainedTags(analysis).forEach(tags::add);
        diagnosisReportReader.issueTags(analysis).forEach(tags::add);
        return List.copyOf(tags);
    }

    private List<AiQualityTrendResponse.TagTrendStat> buildCorrectedTags(List<TeacherDiagnosisCorrection> corrections) {
        Map<String, TagAccumulator> accumulators = new LinkedHashMap<>();
        for (TeacherDiagnosisCorrection correction : corrections) {
            String tag = firstNonBlank(correction.getCorrectedFineGrainedTag(), correction.getCorrectedIssueTag(), "UNKNOWN");
            TagAccumulator accumulator = accumulators.computeIfAbsent(tag, TagAccumulator::new);
            accumulator.count++;
            if (correction.isEvalCandidate()) {
                accumulator.evalCandidateCount++;
            }
        }
        return toTagStats(accumulators.values());
    }

    private List<AiQualityTrendResponse.TagTrendStat> buildEvalNeededTags(List<TeacherDiagnosisCorrection> corrections) {
        Map<String, TagAccumulator> accumulators = new LinkedHashMap<>();
        for (TeacherDiagnosisCorrection correction : corrections) {
            if (correction.isEvalCandidate()) {
                continue;
            }
            String tag = firstNonBlank(correction.getCorrectedFineGrainedTag(), correction.getCorrectedIssueTag(), "UNKNOWN");
            TagAccumulator accumulator = accumulators.computeIfAbsent(tag, TagAccumulator::new);
            accumulator.count++;
        }
        return toTagStats(accumulators.values());
    }

    private List<AiQualityTrendResponse.TagTrendStat> toTagStats(Collection<TagAccumulator> values) {
        return values.stream()
                .sorted(Comparator.comparing(TagAccumulator::getCount).reversed().thenComparing(TagAccumulator::getTag))
                .limit(8)
                .map(accumulator -> AiQualityTrendResponse.TagTrendStat.builder()
                        .tag(accumulator.tag)
                        .label(diagnosisTaxonomy.label(accumulator.tag))
                        .count(accumulator.count)
                        .evalCandidateCount(accumulator.evalCandidateCount)
                        .build())
                .toList();
    }

    private List<AiQualityTrendResponse.SourceQualitySegment> buildSourceSegments(Map<Long, SubmissionAnalysis> analysesBySubmissionId,
                                                                                  List<TeacherDiagnosisCorrection> corrections,
                                                                                  List<HintSafetyCheck> safetyChecks,
                                                                                  List<CoachPrompt> coachPrompts) {
        Map<String, SourceAccumulator> accumulators = new LinkedHashMap<>();
        Map<String, RecoveryAccumulator> recoveryAccumulators = new LinkedHashMap<>();
        analysesBySubmissionId.values().stream()
                .filter(Objects::nonNull)
                .forEach(analysis -> {
                    String key = sourceSegmentKey(analysis);
                    SourceAccumulator accumulator = accumulators.computeIfAbsent(
                            key,
                            ignored -> new SourceAccumulator(analysis, diagnosisReportReader)
                    );
                    accumulator.recordInvocation(analysis, diagnosisReportReader);
                    recoveryAccumulators.computeIfAbsent(
                            sourceRecoveryKey(analysis),
                            ignored -> new RecoveryAccumulator()
                    ).record(analysis, diagnosisReportReader);
                    accumulator.analyzedSubmissionCount++;
                    Double confidence = diagnosisReportReader.confidence(analysis);
                    if (confidence == null || confidence < AiQualityMetrics.LOW_CONFIDENCE_THRESHOLD) {
                        accumulator.lowConfidenceCount++;
                    }
                    if ("HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis))) {
                        accumulator.highLeakRiskCount++;
                        accumulator.promptSafetyIncidentCount++;
                    }
                });

        Map<Long, SubmissionAnalysis> safeAnalyses = analysesBySubmissionId == null ? Map.of() : analysesBySubmissionId;
        for (TeacherDiagnosisCorrection correction : corrections == null ? List.<TeacherDiagnosisCorrection>of() : corrections) {
            SubmissionAnalysis analysis = safeAnalyses.get(correction.getSubmissionId());
            String key = analysis == null ? "UNKNOWN|unknown" : sourceSegmentKey(analysis);
            SourceAccumulator accumulator = accumulators.computeIfAbsent(key, ignored -> analysis == null
                    ? new SourceAccumulator("UNKNOWN", "unknown")
                    : new SourceAccumulator(analysis, diagnosisReportReader));
            accumulator.correctionCount++;
        }

        for (HintSafetyCheck safetyCheck : safetyChecks == null ? List.<HintSafetyCheck>of() : safetyChecks) {
            int riskWeight = riskWeight(safetyCheck == null ? null : safetyCheck.getRiskLevel());
            if (riskWeight < 2) {
                continue;
            }
            SubmissionAnalysis analysis = safetyCheck.getSubmissionId() == null ? null : safeAnalyses.get(safetyCheck.getSubmissionId());
            String key = analysis == null ? "UNKNOWN|unknown" : sourceSegmentKey(analysis);
            SourceAccumulator accumulator = accumulators.computeIfAbsent(key, ignored -> analysis == null
                    ? new SourceAccumulator("UNKNOWN", "unknown")
                    : new SourceAccumulator(analysis, diagnosisReportReader));
            accumulator.promptSafetyIncidentCount++;
            accumulator.promptSafetyDowngradeCount++;
            if (riskWeight >= 3) {
                accumulator.promptSafetyHighRiskDowngradeCount++;
            }
        }

        for (CoachPrompt prompt : coachPrompts == null ? List.<CoachPrompt>of() : coachPrompts) {
            if (prompt == null || !"SAFETY_REJECTED".equalsIgnoreCase(prompt.getModelFailureReason())) {
                continue;
            }
            SubmissionAnalysis analysis = prompt.getSubmissionId() == null ? null : safeAnalyses.get(prompt.getSubmissionId());
            String key = analysis == null ? "UNKNOWN|unknown" : sourceSegmentKey(analysis);
            SourceAccumulator accumulator = accumulators.computeIfAbsent(key, ignored -> analysis == null
                    ? new SourceAccumulator("UNKNOWN", "unknown")
                    : new SourceAccumulator(analysis, diagnosisReportReader));
            accumulator.promptSafetyIncidentCount++;
            accumulator.coachSafetyRejectionCount++;
        }

        return accumulators.values().stream()
                .sorted(Comparator
                        .comparing(SourceAccumulator::getAnalyzedSubmissionCount)
                        .thenComparing(SourceAccumulator::getCorrectionCount)
                        .reversed()
                        .thenComparing(SourceAccumulator::getSourceType)
                        .thenComparing(SourceAccumulator::getVersionLabel))
                .limit(8)
                .map(accumulator -> {
                    RecoveryAccumulator recovery = recoveryAccumulators.getOrDefault(accumulator.recoveryKey,
                            RecoveryAccumulator.notApplicable());
                    QualityComparabilitySummary comparability = buildQualityComparabilitySummary(accumulator, recovery);
                    return AiQualityTrendResponse.SourceQualitySegment.builder()
                            .sourceType(accumulator.sourceType)
                            .versionLabel(accumulator.versionLabel)
                            .provider(accumulator.provider)
                            .model(accumulator.model)
                            .modelVersion(accumulator.modelVersion)
                            .promptVersion(accumulator.promptVersion)
                            .agentVersion(accumulator.agentVersion)
                            .status(accumulator.status)
                            .runtimeMode(accumulator.runtimeMode)
                            .failureStage(accumulator.failureStage)
                            .failureReason(accumulator.failureReason)
                            .transportMode(accumulator.transportMode)
                            .fallbackCount(accumulator.fallbackCount)
                            .modelCompletedCount(accumulator.modelCompletedCount)
                            .modelPartialCount(accumulator.modelPartialCount)
                            .modelRuntimeFailureCount(accumulator.modelRuntimeFailureCount)
                            .streamNoContentCount(accumulator.streamNoContentCount)
                            .streamInvalidChunkCount(accumulator.streamInvalidChunkCount)
                            .streamFallbackRetryCount(accumulator.streamFallbackRetryCount)
                            .recoveryStatus(recovery.status())
                            .recoveryCheckCount(recovery.checkCount())
                            .recoveryPassedCheckCount(recovery.passedCheckCount())
                            .recoveryBlockedReasonCount(recovery.blockedReasonCount())
                            .recoveryBlockedReasons(recovery.blockedReasons())
                            .recoverySmokeRequiredChecks(recovery.requiredChecks())
                            .qualityComparabilityStatus(comparability.status())
                            .qualityComparabilitySummary(comparability.summary())
                            .qualityComparabilityReasonCount(comparability.reasons().size())
                            .qualityComparabilityReasons(comparability.reasons())
                            .analyzedSubmissionCount(accumulator.analyzedSubmissionCount)
                            .correctionCount(accumulator.correctionCount)
                            .lowConfidenceCount(accumulator.lowConfidenceCount)
                            .highLeakRiskCount(accumulator.highLeakRiskCount)
                            .promptSafetyIncidentCount(accumulator.promptSafetyIncidentCount)
                            .promptSafetyDowngradeCount(accumulator.promptSafetyDowngradeCount)
                            .promptSafetyHighRiskDowngradeCount(accumulator.promptSafetyHighRiskDowngradeCount)
                            .coachSafetyRejectionCount(accumulator.coachSafetyRejectionCount)
                            .correctionRate(AiQualityMetrics.rate(accumulator.correctionCount, accumulator.analyzedSubmissionCount))
                            .lowConfidenceRate(AiQualityMetrics.rate(accumulator.lowConfidenceCount, accumulator.analyzedSubmissionCount))
                            .highLeakRiskRate(AiQualityMetrics.rate(accumulator.highLeakRiskCount, accumulator.analyzedSubmissionCount))
                            .promptSafetyIncidentRate(AiQualityMetrics.rate(accumulator.promptSafetyIncidentCount, accumulator.analyzedSubmissionCount))
                            .modelRuntimeFailureRate(AiQualityMetrics.rate(accumulator.modelRuntimeFailureCount, accumulator.analyzedSubmissionCount))
                            .build();
                })
                .toList();
    }

    private QualityComparabilitySummary buildQualityComparabilitySummary(SourceAccumulator accumulator,
                                                                         RecoveryAccumulator recovery) {
        SourceAccumulator source = accumulator == null ? new SourceAccumulator("UNKNOWN", "unknown") : accumulator;
        RecoveryAccumulator recoverySummary = recovery == null ? RecoveryAccumulator.notApplicable() : recovery;
        List<String> reasons = new ArrayList<>();
        String recoveryStatus = recoverySummary.status();
        if ("BLOCKED".equals(recoveryStatus)) {
            reasons.add("current recovery blocked");
            reasons.addAll(recoverySummary.blockedReasons().stream()
                    .filter(reason -> reason != null && !reason.isBlank())
                    .limit(4)
                    .toList());
        }
        if (source.modelCompletedCount <= 0 && source.fallbackCount > 0) {
            reasons.add("model hits missing; fallback hits present");
        }
        if (source.modelPartialCount > 0) {
            reasons.add("partial model outputs present");
        }
        if (!"RECOVERED".equals(recoveryStatus) && source.modelRuntimeFailureCount > 0) {
            reasons.add("runtime failures still present");
        }
        List<String> distinctReasons = reasons.stream()
                .filter(reason -> reason != null && !reason.isBlank())
                .distinct()
                .limit(8)
                .toList();
        if ("BLOCKED".equals(recoveryStatus) || (source.modelCompletedCount <= 0 && source.fallbackCount > 0)) {
            return new QualityComparabilitySummary(
                    "NOT_COMPARABLE",
                    "该来源片段不能代表真实外部模型质量；需要先恢复 provider 或确认无 fallback 的模型命中后再做长期对比。",
                    distinctReasons
            );
        }
        if (source.modelPartialCount > 0) {
            return new QualityComparabilitySummary(
                    "PARTIAL",
                    "该来源已有部分真实模型证据，但仍混有 partial 样本，只适合做小范围质量观察。",
                    distinctReasons
            );
        }
        if ("RECOVERED".equals(recoveryStatus) && source.modelCompletedCount > 0) {
            return new QualityComparabilitySummary(
                    "COMPARABLE",
                    "该来源已有通过恢复检查的真实外部模型完成样本，可支持小批量模型质量对比。",
                    List.of()
            );
        }
        if (source.modelRuntimeFailureCount > 0) {
            return new QualityComparabilitySummary(
                    "PARTIAL",
                    "该来源已有外部模型运行证据，但仍混有运行失败样本，只适合做小范围质量观察。",
                    distinctReasons
            );
        }
        return new QualityComparabilitySummary(
                "NOT_APPLICABLE",
                "该来源片段没有需要解释的外部模型质量对比上下文。",
                List.of()
        );
    }

    private String sourceSegmentKey(SubmissionAnalysis analysis) {
        DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
        if (invocation != null) {
            return String.join("|",
                    firstNonBlank(analysis.getAnalysisSource(), "UNKNOWN"),
                    firstNonBlank(invocation.provider(), "UNKNOWN"),
                    firstNonBlank(invocation.modelVersion(), invocation.model(), "unknown-model"),
                    firstNonBlank(invocation.promptVersion(), "unknown-prompt"),
                    firstNonBlank(invocation.agentVersion(), "unknown-agent"),
                    firstNonBlank(invocation.status(), "unknown-status"),
                    firstNonBlank(invocation.runtimeMode(), "unknown-runtime"),
                    firstNonBlank(invocation.failureStage(), "none"),
                    firstNonBlank(invocation.failureReason(), "none")
            );
        }
        return firstNonBlank(analysis.getAnalysisSource(), "UNKNOWN") + "|" + versionLabel(analysis);
    }

    private String sourceRecoveryKey(SubmissionAnalysis analysis) {
        DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
        if (invocation != null) {
            return String.join("|",
                    firstNonBlank(analysis.getAnalysisSource(), "UNKNOWN"),
                    firstNonBlank(invocation.provider(), "UNKNOWN"),
                    firstNonBlank(invocation.modelVersion(), invocation.model(), "unknown-model"),
                    firstNonBlank(invocation.promptVersion(), "unknown-prompt"),
                    firstNonBlank(invocation.agentVersion(), "unknown-agent"),
                    firstNonBlank(invocation.runtimeMode(), "unknown-runtime")
            );
        }
        return firstNonBlank(analysis.getAnalysisSource(), "UNKNOWN") + "|" + versionLabel(analysis);
    }

    private String versionLabel(SubmissionAnalysis analysis) {
        DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
        if (invocation != null) {
            return String.join(" / ",
                    firstNonBlank(invocation.analysisSchemaVersion(), diagnosisReportReader.analysisSchemaVersion(analysis)),
                    firstNonBlank(invocation.agentVersion(), "unknown-agent"),
                    firstNonBlank(invocation.promptVersion(), "unknown-prompt"),
                    firstNonBlank(invocation.runtimeMode(), "unknown-runtime"),
                    firstNonBlank(invocation.modelVersion(), invocation.model(), "unknown-model")
            );
        }
        String schemaVersion = diagnosisReportReader.analysisSchemaVersion(analysis);
        String trace = diagnosisReportReader.diagnosticTrace(analysis);
        String agentVersion = extractTraceToken(trace, "diagnostic-agent:");
        if (agentVersion.isBlank()) {
            agentVersion = extractTraceToken(trace, "diagnostic-agent-");
        }
        if (!agentVersion.isBlank()) {
            return schemaVersion + " / agent-" + agentVersion;
        }
        return schemaVersion;
    }

    private String extractTraceToken(String trace, String prefix) {
        if (trace == null || trace.isBlank()) {
            return "";
        }
        int start = trace.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        int valueStart = start + prefix.length();
        int end = trace.indexOf(' ', valueStart);
        if (end < 0) {
            end = trace.length();
        }
        return trace.substring(valueStart, end).trim();
    }

    private String buildSummary(long analyzedCount,
                                long correctionCount,
                                long lowConfidenceCount,
                                long highLeakRiskCount,
                                long promptSafetyDowngradeCount,
                                long coachSafetyRejectionCount,
                                long modelRuntimeFailureCount,
                                long interventionEvalCandidateCount,
                                long interventionStillStuckCount,
                                long interventionWaitingFollowupCount) {
        if (analyzedCount == 0) {
            return "还没有跨作业 AI 诊断样本。";
        }
        if (interventionStillStuckCount > 0) {
            return "跨作业存在教师介入后仍卡同类问题，优先沉淀课堂介入 eval 并复盘策略颗粒度。";
        }
        if (highLeakRiskCount > 0) {
            return "跨作业范围内出现高泄题风险样本，建议优先复核。";
        }
        if (modelRuntimeFailureCount > 0) {
            return "跨作业存在外部模型运行失败或规则兜底样本，建议按模型来源、运行模式和失败原因复核。";
        }
        if (promptSafetyDowngradeCount > 0 || coachSafetyRejectionCount > 0) {
            if (promptSafetyDowngradeCount > 0 && coachSafetyRejectionCount > 0) {
                return "跨作业已有提示安全降级和 Coach 安全回退，建议按模型来源和提示版本复核安全网触发原因。";
            }
            if (promptSafetyDowngradeCount > 0) {
                return "跨作业已有提示安全降级事件，建议按模型来源和提示版本复核安全网触发原因。";
            }
            return "跨作业已有 Coach 模型追问被安全门拒绝，建议复核 Coach 提示词和安全评测样本。";
        }
        if (correctionCount > 0) {
            return "跨作业已有教师校正样本，优先补充高频校正标签的 eval。";
        }
        if (interventionEvalCandidateCount > 0) {
            return "跨作业已有课堂介入成效候选，建议沉淀教学建议和班级策略 eval fixture。";
        }
        if (interventionWaitingFollowupCount > 0) {
            return "跨作业已有课堂介入反馈，但仍在等待后续提交证据。";
        }
        if (lowConfidenceCount > 0) {
            return "跨作业存在低置信度诊断，建议抽查证据包和提示词。";
        }
        return "跨作业 AI 质量暂无明显告警，继续观察校正率和低置信度趋势。";
    }

    private AiQualityTrendResponse emptyTrend() {
        return AiQualityTrendResponse.builder()
                .assignmentCount(0)
                .analyzedSubmissionCount(0)
                .correctionCount(0)
                .evalCandidateCount(0)
                .interventionEvalCandidateCount(0)
                .interventionWaitingFollowupCount(0)
                .interventionImprovedCount(0)
                .interventionShiftedCount(0)
                .interventionStillStuckCount(0)
                .lowConfidenceCount(0)
                .highLeakRiskCount(0)
                .promptSafetyIncidentCount(0)
                .promptSafetyDowngradeCount(0)
                .promptSafetyHighRiskDowngradeCount(0)
                .coachSafetyRejectionCount(0)
                .modelCompletedCount(0)
                .modelPartialCount(0)
                .modelRuntimeFailureCount(0)
                .correctionRate(0)
                .lowConfidenceRate(0)
                .highLeakRiskRate(0)
                .promptSafetyIncidentRate(0)
                .modelRuntimeFailureRate(0)
                .summary("还没有作业，暂时无法形成跨作业 AI 质量趋势。")
                .assignments(List.of())
                .correctedTags(List.of())
                .evalNeededTags(List.of())
                .sourceSegments(List.of())
                .build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static class TagAccumulator {
        private final String tag;
        private long count;
        private long evalCandidateCount;

        private TagAccumulator(String tag) {
            this.tag = tag;
        }

        private long getCount() {
            return count;
        }

        private String getTag() {
            return tag;
        }
    }

    private record InterventionTrendCounts(long candidateCount,
                                           long waitingFollowupCount,
                                           long improvedCount,
                                           long shiftedCount,
                                           long stillStuckCount) {
    }

    private record PromptSafetyTrendCounts(long incidentCount,
                                           long downgradeCount,
                                           long highRiskDowngradeCount,
                                           long coachSafetyRejectionCount) {
    }

    private record QualityComparabilitySummary(String status,
                                               String summary,
                                               List<String> reasons) {
        private QualityComparabilitySummary {
            status = status == null || status.isBlank() ? "NOT_APPLICABLE" : status;
            summary = summary == null ? "" : summary;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private static class RecoveryAccumulator {
        private static final List<String> BASE_REQUIRED_CHECKS = List.of(
                "status=MODEL_COMPLETED",
                "fallbackUsed=false",
                "modelHit=true",
                "evidenceRefs present",
                "answerLeakRisk!=HIGH"
        );
        private boolean hasRecoveryContext;
        private boolean streamRequired;
        private boolean recovered;
        private long passedCheckCount;
        private final List<String> blockedReasons = new ArrayList<>();

        private void record(SubmissionAnalysis analysis,
                            DiagnosisReportReader diagnosisReportReader) {
            DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
            if (invocation == null) {
                return;
            }
            boolean context = isRuntimeFailure(invocation)
                    || "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(invocation.status())
                    || invocation.fallbackUsed();
            if (context) {
                hasRecoveryContext = true;
            }
            if (context && "stream".equalsIgnoreCase(invocation.transportMode())) {
                streamRequired = true;
            }
            if (!context && !hasRecoveryContext) {
                return;
            }
            if (isRecoveredSample(analysis, invocation, diagnosisReportReader)) {
                recovered = true;
                passedCheckCount = requiredChecks().size();
                return;
            }
            if (context) {
                blockedReasons.addAll(blockedReasons(analysis, invocation, diagnosisReportReader));
            }
        }

        private String status() {
            if (!hasRecoveryContext) {
                return "NOT_APPLICABLE";
            }
            return recovered ? "RECOVERED" : "BLOCKED";
        }

        private long checkCount() {
            return hasRecoveryContext ? requiredChecks().size() : 0;
        }

        private long passedCheckCount() {
            return recovered ? Math.max(1, passedCheckCount) : 0;
        }

        private long blockedReasonCount() {
            return blockedReasons().size();
        }

        private List<String> blockedReasons() {
            if (!hasRecoveryContext || recovered) {
                return List.of();
            }
            return blockedReasons.stream()
                    .filter(reason -> reason != null && !reason.isBlank())
                    .distinct()
                    .limit(8)
                    .toList();
        }

        private List<String> requiredChecks() {
            if (!hasRecoveryContext) {
                return List.of();
            }
            List<String> checks = new ArrayList<>(BASE_REQUIRED_CHECKS);
            if (streamRequired) {
                checks.add("streamContentChunkCount>0");
            }
            return checks;
        }

        private static RecoveryAccumulator notApplicable() {
            return new RecoveryAccumulator();
        }

        private boolean isRecoveredSample(SubmissionAnalysis analysis,
                                          DiagnosisReportReader.AiInvocationSnapshot invocation,
                                          DiagnosisReportReader diagnosisReportReader) {
            if (!"MODEL_COMPLETED".equalsIgnoreCase(invocation.status()) || invocation.fallbackUsed()) {
                return false;
            }
            if (!hasModelHit(analysis, diagnosisReportReader) || diagnosisReportReader.evidenceRefs(analysis).isEmpty()) {
                return false;
            }
            if ("HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis))) {
                return false;
            }
            if (!streamRequired) {
                return true;
            }
            return "stream".equalsIgnoreCase(invocation.transportMode())
                    && invocation.streamContentChunkCount() > 0;
        }

        private boolean hasModelHit(SubmissionAnalysis analysis,
                                    DiagnosisReportReader diagnosisReportReader) {
            return !diagnosisReportReader.issueTags(analysis).isEmpty()
                    || !diagnosisReportReader.fineGrainedTags(analysis).isEmpty();
        }

        private List<String> blockedReasons(SubmissionAnalysis analysis,
                                            DiagnosisReportReader.AiInvocationSnapshot invocation,
                                            DiagnosisReportReader diagnosisReportReader) {
            List<String> reasons = new ArrayList<>();
            String prefix = "submission:" + analysis.getSubmissionId() + ": ";
            if (isRuntimeFailure(invocation)) {
                reasons.add(prefix + "runtime failure");
            }
            if (!"MODEL_COMPLETED".equalsIgnoreCase(invocation.status())) {
                reasons.add(prefix + "model not completed");
            }
            if (!hasModelHit(analysis, diagnosisReportReader)) {
                reasons.add(prefix + "missing model hit");
            }
            if (diagnosisReportReader.evidenceRefs(analysis).isEmpty()) {
                reasons.add(prefix + "missing evidence");
            }
            if ("HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis))) {
                reasons.add(prefix + "safety failed");
            }
            if ("stream".equalsIgnoreCase(invocation.transportMode()) && invocation.streamContentChunkCount() <= 0) {
                reasons.add(prefix + "stream content chunk missing");
            }
            String failureReason = staticFirstNonBlank(invocation.failureReason(), "");
            if (!failureReason.isBlank()) {
                reasons.add(prefix + sanitizeRecoveryReason(failureReason));
            }
            return reasons;
        }

        private String sanitizeRecoveryReason(String reason) {
            String normalized = reason == null ? "" : reason.trim();
            return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
        }
    }

    private int riskWeight(String risk) {
        return switch (risk == null ? "" : risk.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static boolean isRuntimeFailure(DiagnosisReportReader.AiInvocationSnapshot invocation) {
        return invocation != null
                && ("MODEL_RUNTIME_FALLBACK".equalsIgnoreCase(invocation.status())
                || "MODEL_FAILED".equalsIgnoreCase(invocation.status())
                || invocation.fallbackUsed());
    }

    private static class SourceAccumulator {
        private final String sourceType;
        private final String versionLabel;
        private final String provider;
        private final String model;
        private final String modelVersion;
        private final String promptVersion;
        private final String agentVersion;
        private final String status;
        private final String runtimeMode;
        private final String failureStage;
        private final String failureReason;
        private final String transportMode;
        private final String recoveryKey;
        private long analyzedSubmissionCount;
        private long correctionCount;
        private long lowConfidenceCount;
        private long highLeakRiskCount;
        private long promptSafetyIncidentCount;
        private long promptSafetyDowngradeCount;
        private long promptSafetyHighRiskDowngradeCount;
        private long coachSafetyRejectionCount;
        private long fallbackCount;
        private long modelCompletedCount;
        private long modelPartialCount;
        private long modelRuntimeFailureCount;
        private long streamNoContentCount;
        private long streamInvalidChunkCount;
        private long streamFallbackRetryCount;

        private SourceAccumulator(String sourceType, String versionLabel) {
            this.sourceType = sourceType;
            this.versionLabel = versionLabel;
            this.provider = "";
            this.model = "";
            this.modelVersion = "";
            this.promptVersion = "";
            this.agentVersion = "";
            this.status = "";
            this.runtimeMode = "";
            this.failureStage = "";
            this.failureReason = "";
            this.transportMode = "";
            this.recoveryKey = sourceType + "|" + versionLabel;
        }

        private SourceAccumulator(SubmissionAnalysis analysis,
                                  DiagnosisReportReader diagnosisReportReader) {
            DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
            this.sourceType = analysis == null ? "UNKNOWN" : staticFirstNonBlank(analysis.getAnalysisSource(), "UNKNOWN");
            this.versionLabel = analysis == null ? "unknown" : buildVersionLabel(analysis, invocation, diagnosisReportReader);
            this.provider = invocation == null ? "" : invocation.provider();
            this.model = invocation == null ? "" : invocation.model();
            this.modelVersion = invocation == null ? "" : invocation.modelVersion();
            this.promptVersion = invocation == null ? "" : invocation.promptVersion();
            this.agentVersion = invocation == null ? "" : invocation.agentVersion();
            this.status = invocation == null ? "" : invocation.status();
            this.runtimeMode = invocation == null ? "" : invocation.runtimeMode();
            this.failureStage = invocation == null ? "" : invocation.failureStage();
            this.failureReason = invocation == null ? "" : invocation.failureReason();
            this.transportMode = invocation == null ? "" : invocation.transportMode();
            this.recoveryKey = buildRecoveryKey(analysis, invocation);
            this.fallbackCount = 0;
        }

        private void recordInvocation(SubmissionAnalysis analysis,
                                      DiagnosisReportReader diagnosisReportReader) {
            DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
            if (invocation == null) {
                return;
            }
            if (invocation.fallbackUsed()) {
                fallbackCount++;
            }
            if ("MODEL_COMPLETED".equalsIgnoreCase(invocation.status())) {
                modelCompletedCount++;
            }
            if ("MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(invocation.status())) {
                modelPartialCount++;
            }
            if (isRuntimeFailure(invocation)) {
                modelRuntimeFailureCount++;
            }
            if ("stream".equalsIgnoreCase(invocation.transportMode()) && invocation.streamContentChunkCount() <= 0) {
                streamNoContentCount++;
            }
            streamInvalidChunkCount += Math.max(0, invocation.streamInvalidChunkCount());
            if (invocation.streamFallbackRetryUsed()) {
                streamFallbackRetryCount++;
            }
        }

        private boolean isRuntimeFailure(DiagnosisReportReader.AiInvocationSnapshot invocation) {
            return invocation != null
                    && ("MODEL_RUNTIME_FALLBACK".equalsIgnoreCase(invocation.status())
                    || "MODEL_FAILED".equalsIgnoreCase(invocation.status())
                    || invocation.fallbackUsed());
        }

        private long getAnalyzedSubmissionCount() {
            return analyzedSubmissionCount;
        }

        private long getCorrectionCount() {
            return correctionCount;
        }

        private String getSourceType() {
            return sourceType;
        }

        private String getVersionLabel() {
            return versionLabel;
        }
    }

    private static String buildVersionLabel(SubmissionAnalysis analysis,
                                            DiagnosisReportReader.AiInvocationSnapshot invocation,
                                            DiagnosisReportReader diagnosisReportReader) {
        if (invocation != null) {
            return String.join(" / ",
                    staticFirstNonBlank(invocation.analysisSchemaVersion(), diagnosisReportReader.analysisSchemaVersion(analysis)),
                    staticFirstNonBlank(invocation.agentVersion(), "unknown-agent"),
                    staticFirstNonBlank(invocation.promptVersion(), "unknown-prompt"),
                    staticFirstNonBlank(invocation.runtimeMode(), "unknown-runtime"),
                    staticFirstNonBlank(invocation.modelVersion(), invocation.model(), "unknown-model")
            );
        }
        String schemaVersion = diagnosisReportReader.analysisSchemaVersion(analysis);
        String trace = diagnosisReportReader.diagnosticTrace(analysis);
        String agentVersion = staticExtractTraceToken(trace, "diagnostic-agent:");
        if (agentVersion.isBlank()) {
            agentVersion = staticExtractTraceToken(trace, "diagnostic-agent-");
        }
        return agentVersion.isBlank() ? schemaVersion : schemaVersion + " / agent-" + agentVersion;
    }

    private static String buildRecoveryKey(SubmissionAnalysis analysis,
                                           DiagnosisReportReader.AiInvocationSnapshot invocation) {
        if (invocation != null) {
            return String.join("|",
                    staticFirstNonBlank(analysis == null ? "" : analysis.getAnalysisSource(), "UNKNOWN"),
                    staticFirstNonBlank(invocation.provider(), "UNKNOWN"),
                    staticFirstNonBlank(invocation.modelVersion(), invocation.model(), "unknown-model"),
                    staticFirstNonBlank(invocation.promptVersion(), "unknown-prompt"),
                    staticFirstNonBlank(invocation.agentVersion(), "unknown-agent"),
                    staticFirstNonBlank(invocation.runtimeMode(), "unknown-runtime")
            );
        }
        return staticFirstNonBlank(analysis == null ? "" : analysis.getAnalysisSource(), "UNKNOWN")
                + "|"
                + staticFirstNonBlank(analysis == null ? "" : analysis.getAnalysisSource(), "unknown");
    }

    private static String staticFirstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String staticExtractTraceToken(String trace, String prefix) {
        if (trace == null || trace.isBlank()) {
            return "";
        }
        int start = trace.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        int valueStart = start + prefix.length();
        int end = trace.indexOf(' ', valueStart);
        if (end < 0) {
            end = trace.length();
        }
        return trace.substring(valueStart, end).trim();
    }
}

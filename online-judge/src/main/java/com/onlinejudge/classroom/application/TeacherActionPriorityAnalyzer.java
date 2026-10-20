package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TeacherActionPriorityAnalyzer {

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public Map<String, PrioritySignal> summarize(List<Submission> submissions,
                                                 Map<Long, SubmissionAnalysis> analyses,
                                                 Map<Long, StudentTrajectoryResponse.LearningInterventionImpact> impacts,
                                                 Map<Long, StudentTrajectoryResponse.LearningActionEvidence> actionEvidence) {
        return summarize(submissions, analyses, impacts, actionEvidence, Map.of());
    }

    public Map<String, PrioritySignal> summarize(List<Submission> submissions,
                                                 Map<Long, SubmissionAnalysis> analyses,
                                                 Map<Long, StudentTrajectoryResponse.LearningInterventionImpact> impacts,
                                                 Map<Long, StudentTrajectoryResponse.LearningActionEvidence> actionEvidence,
                                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions) {
        if (submissions == null || submissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, MutablePriority> signals = new LinkedHashMap<>();
        for (Submission submission : submissions) {
            if (submission == null || submission.getId() == null) {
                continue;
            }
            SubmissionAnalysis analysis = analyses.get(submission.getId());
            if (analysis == null) {
                continue;
            }
            List<String> tags = diagnosisReportReader.fineGrainedTags(analysis);
            if (tags.isEmpty()) {
                tags = diagnosisReportReader.issueTags(analysis);
            }
            StudentTrajectoryResponse.LearningInterventionImpact impact = impacts == null ? null : impacts.get(submission.getId());
            StudentTrajectoryResponse.LearningActionEvidence evidence = actionEvidence == null ? null : actionEvidence.get(submission.getId());
            CoachInteractionSummaryResponse coachInteraction = coachInteractions == null ? null : coachInteractions.get(submission.getId());
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                MutablePriority signal = signals.computeIfAbsent(tag, ignored -> new MutablePriority());
                signal.count++;
                if (submission.getStudentProfileId() != null) {
                    signal.students.put(submission.getStudentProfileId(), true);
                }
                Double confidence = diagnosisReportReader.confidence(analysis);
                if (confidence == null || confidence < AiQualityMetrics.LOW_CONFIDENCE_THRESHOLD) {
                    signal.lowConfidenceCount++;
                }
                if (isAttentionTrajectory(analysis)) {
                    signal.repeatedOrEscalatedCount++;
                }
                if (evidence != null && isUnexecuted(evidence.getExecutionStatus())) {
                    signal.unexecutedActionCount++;
                }
                if (impact != null && isUnresolvedImpact(impact.getStatus())) {
                    signal.unresolvedAfterInterventionCount++;
                }
                if (hasLowQualityCoachAnswer(coachInteraction)) {
                    signal.lowQualityCoachAnswerCount++;
                }
            }
        }
        LinkedHashMap<String, PrioritySignal> result = new LinkedHashMap<>();
        signals.forEach((tag, signal) -> result.put(tag, signal.toSignal(tag, diagnosisTaxonomy)));
        return result;
    }

    public AssignmentOverviewResponse.IssueStat enrich(AssignmentOverviewResponse.IssueStat issue,
                                                       PrioritySignal signal) {
        if (issue == null || signal == null) {
            return issue;
        }
        return AssignmentOverviewResponse.IssueStat.builder()
                .label(issue.getLabel())
                .count(issue.getCount())
                .explanation(issue.getExplanation())
                .abilityPoint(issue.getAbilityPoint())
                .recommendedHintPolicy(issue.getRecommendedHintPolicy())
                .interventionSuggestion(issue.getInterventionSuggestion())
                .actionPriorityScore(signal.getScore())
                .actionPriorityLabel(signal.getLabel())
                .actionPriorityReason(signal.getReason())
                .affectedStudentCount(signal.getAffectedStudentCount())
                .repeatedStudentCount(signal.getRepeatedOrEscalatedCount())
                .unexecutedActionCount(signal.getUnexecutedActionCount())
                .unresolvedAfterInterventionCount(signal.getUnresolvedAfterInterventionCount())
                .build();
    }

    private boolean isAttentionTrajectory(SubmissionAnalysis analysis) {
        DiagnosisReportReader.LearningTrajectorySignalSnapshot signal = diagnosisReportReader.learningTrajectorySignal(analysis);
        if (signal == null) {
            return false;
        }
        return signal.needsTeacherAttention()
                || "REPEATED_STUCK".equals(signal.phase())
                || "REGRESSION".equals(signal.phase());
    }

    private boolean isUnexecuted(String status) {
        return "CONTRADICTED".equals(status) || "NOT_OBSERVED".equals(status);
    }

    private boolean isUnresolvedImpact(String status) {
        return "SAME_ISSUE".equals(status) || "NO_CLEAR_CHANGE".equals(status) || "AWAITING_FOLLOWUP".equals(status);
    }

    private boolean hasLowQualityCoachAnswer(CoachInteractionSummaryResponse coachInteraction) {
        if (coachInteraction == null || coachInteraction.getAnswerQualitySignal() == null) {
            return false;
        }
        String level = coachInteraction.getAnswerQualitySignal().getQualityLevel();
        return "VAGUE_ACK".equals(level) || "DIRECTION_ONLY".equals(level);
    }

    @Data
    @Builder
    public static class PrioritySignal {
        private String tag;
        private String label;
        private long count;
        private long affectedStudentCount;
        private long repeatedOrEscalatedCount;
        private long unexecutedActionCount;
        private long unresolvedAfterInterventionCount;
        private long lowConfidenceCount;
        private long lowQualityCoachAnswerCount;
        private double score;
        private String reason;
    }

    private static class MutablePriority {
        private long count;
        private final LinkedHashMap<Long, Boolean> students = new LinkedHashMap<>();
        private long repeatedOrEscalatedCount;
        private long unexecutedActionCount;
        private long unresolvedAfterInterventionCount;
        private long lowConfidenceCount;
        private long lowQualityCoachAnswerCount;

        private PrioritySignal toSignal(String tag, DiagnosisTaxonomy taxonomy) {
            long affectedStudents = students.size();
            double score = count
                    + affectedStudents * 1.4
                    + repeatedOrEscalatedCount * 1.6
                    + unexecutedActionCount * 1.8
                    + unresolvedAfterInterventionCount * 2.0
                    + lowQualityCoachAnswerCount * 1.3
                    + lowConfidenceCount * 0.7;
            score = Math.round(score * 10.0) / 10.0;
            return PrioritySignal.builder()
                    .tag(tag)
                    .label(priorityLabel(score, affectedStudents, unresolvedAfterInterventionCount, unexecutedActionCount, lowQualityCoachAnswerCount))
                    .count(count)
                    .affectedStudentCount(affectedStudents)
                    .repeatedOrEscalatedCount(repeatedOrEscalatedCount)
                    .unexecutedActionCount(unexecutedActionCount)
                    .unresolvedAfterInterventionCount(unresolvedAfterInterventionCount)
                    .lowConfidenceCount(lowConfidenceCount)
                    .lowQualityCoachAnswerCount(lowQualityCoachAnswerCount)
                    .score(score)
                    .reason(reason(taxonomy.label(tag), affectedStudents, repeatedOrEscalatedCount,
                            unexecutedActionCount, unresolvedAfterInterventionCount, lowConfidenceCount,
                            lowQualityCoachAnswerCount))
                    .build();
        }

        private static String priorityLabel(double score,
                                            long affectedStudents,
                                            long unresolvedAfterInterventionCount,
                                            long unexecutedActionCount,
                                            long lowQualityCoachAnswerCount) {
            if (unresolvedAfterInterventionCount > 0 || unexecutedActionCount > 1 || score >= 9) {
                return "优先课堂干预";
            }
            if (lowQualityCoachAnswerCount > 0 || affectedStudents >= 2 || score >= 5) {
                return "适合小组讲评";
            }
            return "继续观察";
        }

        private static String reason(String tagLabel,
                                     long affectedStudents,
                                     long repeatedOrEscalatedCount,
                                     long unexecutedActionCount,
                                     long unresolvedAfterInterventionCount,
                                     long lowConfidenceCount,
                                     long lowQualityCoachAnswerCount) {
            StringBuilder reason = new StringBuilder();
            reason.append(tagLabel).append("影响 ").append(affectedStudents).append(" 名学生");
            if (repeatedOrEscalatedCount > 0) {
                reason.append("，其中 ").append(repeatedOrEscalatedCount).append(" 次存在反复卡住或回退");
            }
            if (unexecutedActionCount > 0) {
                reason.append("，").append(unexecutedActionCount).append(" 次学习动作未被观察到有效执行");
            }
            if (unresolvedAfterInterventionCount > 0) {
                reason.append("，").append(unresolvedAfterInterventionCount).append(" 次干预后仍未解决");
            }
            if (lowConfidenceCount > 0) {
                reason.append("，").append(lowConfidenceCount).append(" 次诊断置信度偏低");
            }
            if (lowQualityCoachAnswerCount > 0) {
                reason.append("，").append(lowQualityCoachAnswerCount).append(" 次 coach 回答缺少可验证证据");
            }
            reason.append("。");
            return reason.toString();
        }
    }
}

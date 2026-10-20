package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiagnosisEvidencePackageReader {

    private final ObjectMapper objectMapper;

    public String serialize(DiagnosisEvidencePackage evidencePackage) {
        if (evidencePackage == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidencePackage);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize diagnosis evidence package. submissionId={}",
                    evidencePackage.getSubmission() == null ? null : evidencePackage.getSubmission().getId(),
                    exception);
            return null;
        }
    }

    public Optional<DiagnosisEvidencePackage> read(SubmissionAnalysis analysis) {
        if (analysis == null || analysis.getEvidenceJson() == null || analysis.getEvidenceJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(analysis.getEvidenceJson(), DiagnosisEvidencePackage.class));
        } catch (Exception exception) {
            log.warn("Failed to read diagnosis evidence package. submissionId={}, reason={}",
                    analysis.getSubmissionId(),
                    exception.getMessage());
            return Optional.empty();
        }
    }

    public EvidenceSummary summarize(SubmissionAnalysis analysis, Submission submission) {
        return read(analysis)
                .map(evidencePackage -> summarizePersisted(evidencePackage))
                .orElseGet(() -> summarizeLegacy(analysis, submission));
    }

    private EvidenceSummary summarizePersisted(DiagnosisEvidencePackage evidencePackage) {
        List<String> refs = new ArrayList<>();
        List<String> details = new ArrayList<>();

        String schemaVersion = firstNonBlank(evidencePackage.getSchemaVersion(), DiagnosisEvidencePackage.SCHEMA_VERSION);
        refs.add("evidence:" + schemaVersion);

        DiagnosisEvidencePackage.SubmissionEvidence submission = evidencePackage.getSubmission();
        if (submission != null) {
            if (submission.getId() != null) {
                refs.add("submission:" + submission.getId());
            }
            if (hasText(submission.getVerdict())) {
                refs.add("verdict:" + submission.getVerdict().toLowerCase());
                details.add("评测结果为 " + readableVerdict(submission.getVerdict()));
            }
        }

        DiagnosisEvidencePackage.ProblemEvidence problem = evidencePackage.getProblem();
        if (problem != null && problem.getId() != null) {
            refs.add("problem:" + problem.getId());
            if (problem.getKnowledgePoints() != null && !problem.getKnowledgePoints().isEmpty()) {
                refs.add("problem:knowledge_points");
                details.add("题目知识点：" + String.join("、", problem.getKnowledgePoints().stream().limit(3).toList()));
            }
            if (problem.getCommonMistakes() != null && !problem.getCommonMistakes().isEmpty()) {
                refs.add("problem:common_mistakes");
                details.add("题目常见误区：" + String.join("、", problem.getCommonMistakes().stream().limit(2).toList()));
            }
        }

        DiagnosisEvidencePackage.JudgeFacts judgeFacts = evidencePackage.getJudgeFacts();
        if (judgeFacts != null) {
            if (judgeFacts.getPassedCount() != null && judgeFacts.getTotalCount() != null) {
                refs.add("judge:cases:" + judgeFacts.getPassedCount() + "/" + judgeFacts.getTotalCount());
                details.add("已通过 " + judgeFacts.getPassedCount() + "/" + judgeFacts.getTotalCount() + " 个测试点");
            }
            if (judgeFacts.getFirstFailedCase() != null) {
                refs.add("judge:first_failed_case:" + judgeFacts.getFirstFailedCase().getTestCaseNumber());
                if (judgeFacts.getFirstFailedCase().isHidden()) {
                    details.add("首个失败点是隐藏测试点，不能展示隐藏输入输出");
                } else {
                    details.add("首个失败点为 #" + judgeFacts.getFirstFailedCase().getTestCaseNumber());
                }
            }
            if (Boolean.TRUE.equals(judgeFacts.getHiddenFailureObserved())) {
                refs.add("judge:hidden_failure");
                details.add("存在隐藏测试点失败，追问需要围绕边界类型而不是具体隐藏数据");
            }
            if (hasText(judgeFacts.getCompileOutput())) {
                refs.add("judge:compile_output");
                details.add("包含编译输出证据");
            }
            if (hasText(judgeFacts.getRuntimeErrorMessage())) {
                refs.add("judge:runtime_error");
                details.add("包含运行错误信息");
            }
        }
        if (submission != null && submission.getSourceCodeLineCount() != null) {
            refs.add("source:lines:" + submission.getSourceCodeLineCount());
            details.add("代码约 " + submission.getSourceCodeLineCount() + " 行");
        }

        DiagnosisEvidencePackage.HistoryEvidence history = evidencePackage.getHistory();
        if (history != null) {
            if (hasText(history.getRepeatedFineGrainedTag())) {
                refs.add("history:repeated_fine_tag:" + history.getRepeatedFineGrainedTag());
                details.add("最近重复出现细分卡点 " + history.getRepeatedFineGrainedTag());
            } else if (hasText(history.getRepeatedIssueTag())) {
                refs.add("history:repeated_issue_tag:" + history.getRepeatedIssueTag());
                details.add("最近重复出现同类问题 " + history.getRepeatedIssueTag());
            }
            if (hasText(history.getTransitionSignal())) {
                refs.add("history:transition");
                details.add(history.getTransitionSignal());
            }
        }

        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory = evidencePackage.getLearningMemory();
        if (memory != null) {
            if (memory.getEvidenceRefs() != null) {
                refs.addAll(memory.getEvidenceRefs());
            }
            if (memory.getRecurringFineGrainedTags() != null && !memory.getRecurringFineGrainedTags().isEmpty()) {
                DiagnosisEvidencePackage.MemoryTagStat stat = memory.getRecurringFineGrainedTags().get(0);
                refs.add("memory:recurring_fine:" + stat.getTag());
                details.add("学生长期记忆显示重复细分卡点 " + stat.getTag() + " 出现 " + stat.getCount() + " 次");
            } else if (memory.getRecurringIssueTags() != null && !memory.getRecurringIssueTags().isEmpty()) {
                DiagnosisEvidencePackage.MemoryTagStat stat = memory.getRecurringIssueTags().get(0);
                refs.add("memory:recurring_issue:" + stat.getTag());
                details.add("学生长期记忆显示重复错因 " + stat.getTag() + " 出现 " + stat.getCount() + " 次");
            }
            if (hasText(memory.getRecentTrend())) {
                refs.add("memory:recent_trend");
                details.add(memory.getRecentTrend());
            }
            if (hasText(memory.getTeacherCorrectionSummary())) {
                refs.add("memory:teacher_correction");
                details.add(memory.getTeacherCorrectionSummary());
            }
        }

        DiagnosisEvidencePackage.PolicyEvidence policy = evidencePackage.getPolicy();
        if (policy != null && hasText(policy.getHintPolicy())) {
            refs.add("policy:" + policy.getHintPolicy());
            details.add("提示层级受 " + policy.getHintPolicy() + " 限制");
        }

        return new EvidenceSummary("persisted", deduplicate(refs), deduplicate(details));
    }

    private EvidenceSummary summarizeLegacy(SubmissionAnalysis analysis, Submission submission) {
        List<String> refs = new ArrayList<>();
        List<String> details = new ArrayList<>();
        if (submission != null && submission.getId() != null) {
            refs.add("submission:" + submission.getId());
        }
        if (analysis != null) {
            refs.add("analysis:legacy_summary");
            if (hasText(analysis.getScenario())) {
                refs.add("analysis:" + analysis.getScenario());
                details.add("旧诊断场景为 " + analysis.getScenario());
            }
            if (hasText(analysis.getHeadline())) {
                details.add("旧诊断结论：" + analysis.getHeadline());
            }
        }
        if (submission != null && submission.getVerdict() != null) {
            refs.add("verdict:" + submission.getVerdict().name().toLowerCase());
            details.add("评测结果为 " + readableVerdict(submission.getVerdict().name()));
        }
        if (details.isEmpty()) {
            details.add("旧诊断缺少完整证据包，只能使用标签和评测结果生成追问");
        } else {
            details.add("旧诊断缺少完整证据包，追问会自动降级为摘要证据");
        }
        return new EvidenceSummary("legacy-summary", deduplicate(refs), deduplicate(details));
    }

    private List<String> deduplicate(List<String> values) {
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String readableVerdict(String verdict) {
        if (verdict == null) {
            return "待观察";
        }
        return switch (verdict) {
            case "ACCEPTED", "AC" -> "已通过";
            case "WRONG_ANSWER", "WA" -> "答案需修正";
            case "TIME_LIMIT_EXCEEDED", "TLE" -> "时间超限";
            case "MEMORY_LIMIT_EXCEEDED", "MLE" -> "内存超限";
            case "RUNTIME_ERROR", "RE" -> "运行错误";
            case "COMPILATION_ERROR", "CE" -> "编译错误";
            default -> verdict;
        };
    }

    private String firstNonBlank(String primary, String fallback) {
        return hasText(primary) ? primary : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EvidenceSummary(String source, List<String> evidenceRefs, List<String> detailLines) {
    }
}

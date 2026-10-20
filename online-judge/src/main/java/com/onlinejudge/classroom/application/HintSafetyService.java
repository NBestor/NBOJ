package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.HintSafetyCheck;
import com.onlinejudge.classroom.persistence.HintSafetyCheckRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HintSafetyService {

    private final HintSafetyCheckRepository hintSafetyCheckRepository;
    private final ObjectMapper objectMapper;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    @Transactional
    public SubmissionAnalysisResponse verifyAndRecord(SubmissionAnalysisResponse analysis) {
        return verifyAndRecord(analysis, Assignment.HintPolicy.L2);
    }

    @Transactional
    public SubmissionAnalysisResponse verifyAndRecord(SubmissionAnalysisResponse analysis, Assignment.HintPolicy hintPolicy) {
        if (analysis == null || analysis.getSubmissionId() == null) {
            return analysis;
        }

        analysis.setIssueTags(diagnosisTaxonomy.normalizeIssueTags(analysis.getIssueTags()));
        SafetyResult result = verify(
                analysis.getStudentHint(),
                analysis.getStudentHintPlan(),
                analysis.getLearningInterventionPlan(),
                analysis.getReportMarkdown(),
                hintPolicy
        );
        String riskLevel = maxRisk(result.riskLevel(), analysis.getAnswerLeakRisk());
        analysis.setStudentHint(result.safeHint());
        analysis.setAnswerLeakRisk(riskLevel);
        analysis.setStudentHintPlan(resolveSafeHintPlan(analysis.getStudentHintPlan(), result.safeHint(), riskLevel, hintPolicy));
        analysis.setLearningInterventionPlan(resolveSafeInterventionPlan(
                analysis.getLearningInterventionPlan(),
                result.safeHint(),
                riskLevel
        ));
        if (riskWeight(riskLevel) >= 2) {
            analysis.setReportMarkdown("""
                    ## 提示已安全降级

                    系统检测到原始反馈可能过于直接，已仅保留下一步定位方向。

                    - %s

                    请先用自己的语言复述题意，再手推一个最小边界样例。
                    """.formatted(result.safeHint()));
        }

        if (hintSafetyCheckRepository != null) {
            hintSafetyCheckRepository.save(HintSafetyCheck.builder()
                    .submissionId(analysis.getSubmissionId())
                    .riskLevel(riskLevel)
                    .blockedReasonsJson(toJson(result.reasons()))
                    .originalHint(Optional.ofNullable(result.originalHint()).orElse(""))
                    .safeHint(result.safeHint())
                    .build());
        }
        return analysis;
    }

    private SafetyResult verify(String studentHint,
                                SubmissionAnalysisResponse.StudentHintPlan hintPlan,
                                SubmissionAnalysisResponse.LearningInterventionPlan interventionPlan,
                                String reportMarkdown,
                                Assignment.HintPolicy hintPolicy) {
        String originalHint = Optional.ofNullable(studentHint).orElse("");
        String visibleReportMarkdown = stripStudentCodeLocationSections(Optional.ofNullable(reportMarkdown).orElse(""));
        String combined = (originalHint + "\n"
                + hintPlanText(hintPlan) + "\n"
                + interventionPlanText(interventionPlan) + "\n"
                + visibleReportMarkdown).toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        boolean completeCodeLeak = combined.contains("```") || combined.contains("#include") || combined.contains("int main")
                || combined.contains("def ") || combined.contains("class solution");
        boolean hiddenTestLeak = (combined.contains("隐藏测试") || combined.contains("hidden test"))
                && (combined.contains("输入") || combined.contains("输出")
                || combined.contains("input") || combined.contains("output"));
        if (completeCodeLeak) {
            reasons.add("疑似包含完整代码片段");
        }
        if (combined.contains("答案如下") || combined.contains("完整代码") || combined.contains("直接改成")
                || combined.contains("最终答案") || combined.contains("complete answer")
                || combined.contains("directly change") || combined.contains("direct fix")
                || combined.contains("exact fix") || combined.contains("final answer")) {
            reasons.add("疑似直接给出答案或完整改法");
        }
        if (hiddenTestLeak) {
            reasons.add("疑似暴露隐藏测试点信息");
        }
        if (diagnosisTaxonomy.isBeyondPolicy(combined, hintPolicy)) {
            reasons.add("疑似超过当前作业提示层级");
        }

        if (reasons.isEmpty()) {
            return new SafetyResult("LOW", originalHint, originalHint, reasons);
        }

        String safeHint = switch (hintPolicy == null ? Assignment.HintPolicy.L2 : hintPolicy) {
            case L1 -> "我先只指出问题类型。请查看本次诊断标签，从中选择一个最可能的错因。";
            case L2 -> "我先不给出改法。请根据本次诊断标签，从边界条件、关键分支、复杂度或运行稳定性中选一个方向，手推一个最小反例后再修改。";
            case L3 -> "我先保留定位方向。请找到最可疑的局部逻辑，用一个最小样例验证它是否符合题意。";
            case L4 -> "原始提示可能过于直接。请先按题意复述算法思路，再决定是否需要参考改法。";
        };
        String riskLevel = reasons.size() >= 2 || hiddenTestLeak ? "HIGH" : "MEDIUM";
        return new SafetyResult(riskLevel, originalHint, safeHint, reasons);
    }

    private String stripStudentCodeLocationSections(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (cursor < normalized.length()) {
            int sectionStart = normalized.indexOf("## 代码定位", cursor);
            if (sectionStart < 0) {
                result.append(normalized.substring(cursor));
                break;
            }
            result.append(normalized, cursor, sectionStart);
            int nextSection = normalized.indexOf("\n## ", sectionStart + "## 代码定位".length());
            if (nextSection < 0) {
                break;
            }
            cursor = nextSection + 1;
        }
        return result.toString();
    }

    private SubmissionAnalysisResponse.StudentHintPlan resolveSafeHintPlan(SubmissionAnalysisResponse.StudentHintPlan original,
                                                                           String safeHint,
                                                                           String riskLevel,
                                                                           Assignment.HintPolicy hintPolicy) {
        Assignment.HintPolicy policy = hintPolicy == null ? Assignment.HintPolicy.L2 : hintPolicy;
        if (riskWeight(riskLevel) < 2 && original != null) {
            original.setAnswerLeakRisk(riskLevel);
            return original;
        }
        return SubmissionAnalysisResponse.StudentHintPlan.builder()
                .hintLevel(policy.name())
                .problemType(original == null || original.getProblemType() == null || original.getProblemType().isBlank()
                        ? "提示安全降级"
                        : original.getProblemType())
                .evidenceAnchor("原始提示可能过于直接，系统已仅保留可验证的定位方向。")
                .nextAction(safeHint)
                .coachQuestion("你能先构造一个最小样例，说明自己准备验证什么吗？")
                .teachingAction("COLLECT_EVIDENCE")
                .evidenceRefs(original == null || original.getEvidenceRefs() == null ? List.of() : original.getEvidenceRefs())
                .answerLeakRisk(riskLevel)
                .build();
    }

    private SubmissionAnalysisResponse.LearningInterventionPlan resolveSafeInterventionPlan(
            SubmissionAnalysisResponse.LearningInterventionPlan original,
            String safeHint,
            String riskLevel) {
        if (riskWeight(riskLevel) < 2 && original != null) {
            original.setAnswerLeakRisk(riskLevel);
            return original;
        }
        if (original == null && riskWeight(riskLevel) < 2) {
            return null;
        }
        return SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                .interventionType("COLLECT_EVIDENCE")
                .goal("把当前提示降级成一个安全、可验证的学习动作。")
                .studentTask(safeHint)
                .checkQuestion("你能先补充一个最小样例或一条运行证据，再说明自己要验证什么吗？")
                .completionSignal("学生能提供最小样例、输出对比或错误信息，而不是直接复述答案。")
                .evidenceRefs(original == null || original.getEvidenceRefs() == null ? List.of() : original.getEvidenceRefs())
                .estimatedMinutes(original == null || original.getEstimatedMinutes() == null ? 5 : original.getEstimatedMinutes())
                .answerLeakRisk(riskLevel)
                .build();
    }

    private String hintPlanText(SubmissionAnalysisResponse.StudentHintPlan hintPlan) {
        if (hintPlan == null) {
            return "";
        }
        return String.join("\n", List.of(
                Optional.ofNullable(hintPlan.getProblemType()).orElse(""),
                Optional.ofNullable(hintPlan.getEvidenceAnchor()).orElse(""),
                Optional.ofNullable(hintPlan.getNextAction()).orElse(""),
                Optional.ofNullable(hintPlan.getCoachQuestion()).orElse("")
        ));
    }

    private String interventionPlanText(SubmissionAnalysisResponse.LearningInterventionPlan interventionPlan) {
        if (interventionPlan == null) {
            return "";
        }
        return String.join("\n", List.of(
                Optional.ofNullable(interventionPlan.getGoal()).orElse(""),
                Optional.ofNullable(interventionPlan.getStudentTask()).orElse(""),
                Optional.ofNullable(interventionPlan.getCheckQuestion()).orElse(""),
                Optional.ofNullable(interventionPlan.getCompletionSignal()).orElse("")
        ));
    }

    private String toJson(List<String> reasons) {
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String maxRisk(String left, String right) {
        return riskWeight(left) >= riskWeight(right) ? normalizeRisk(left) : normalizeRisk(right);
    }

    private int riskWeight(String risk) {
        return switch (normalizeRisk(risk)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String normalizeRisk(String risk) {
        String normalized = Optional.ofNullable(risk).orElse("UNKNOWN").trim().toUpperCase(Locale.ROOT);
        if ("LOW".equals(normalized) || "MEDIUM".equals(normalized) || "HIGH".equals(normalized)) {
            return normalized;
        }
        return "UNKNOWN";
    }

    private record SafetyResult(String riskLevel, String originalHint, String safeHint, List<String> reasons) {
    }
}

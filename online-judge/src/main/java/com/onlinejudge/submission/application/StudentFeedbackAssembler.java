package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class StudentFeedbackAssembler {

    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public StudentFeedbackAssembler(DiagnosisTaxonomy diagnosisTaxonomy) {
        this.diagnosisTaxonomy = diagnosisTaxonomy;
    }

    public SubmissionAnalysisResponse.StudentFeedback assemble(SubmissionAnalysisResponse analysis,
                                                               DiagnosisEvidencePackage evidencePackage,
                                                               boolean fallbackUsed) {
        if (analysis == null) {
            return null;
        }
        SubmissionAnalysisResponse.StudentFeedback existing = analysis.getStudentFeedback();
        if (isUsable(existing)) {
            return normalize(existing, analysis, evidencePackage, fallbackUsed);
        }
        String primaryIssueTag = primaryIssueTag(analysis);
        String fineGrainedTag = primaryFineTag(analysis);
        String teachingTag = fineGrainedTag.isBlank() ? primaryIssueTag : fineGrainedTag;
        List<String> evidenceRefs = deduplicate(analysis.getEvidenceRefs());
        SubmissionAnalysisResponse.FeedbackIssue blockingIssue = buildBlockingIssue(
                analysis,
                evidencePackage,
                primaryIssueTag,
                fineGrainedTag,
                teachingTag,
                evidenceRefs
        );
        List<SubmissionAnalysisResponse.SecondaryIssue> secondaryIssues = buildSecondaryIssues(
                analysis,
                evidencePackage,
                primaryIssueTag,
                fineGrainedTag,
                evidenceRefs
        );
        List<SubmissionAnalysisResponse.ImprovementOpportunity> improvements = buildImprovements(
                analysis,
                evidencePackage,
                primaryIssueTag,
                fineGrainedTag,
                evidenceRefs
        );
        return SubmissionAnalysisResponse.StudentFeedback.builder()
                .summary(summary(analysis, blockingIssue, fallbackUsed))
                .blockingIssues(List.of(blockingIssue))
                .secondaryIssues(secondaryIssues)
                .improvementOpportunities(improvements)
                .nextLearningAction(nextLearningAction(analysis, blockingIssue, teachingTag, evidenceRefs))
                .build();
    }

    private boolean isUsable(SubmissionAnalysisResponse.StudentFeedback feedback) {
        return feedback != null
                && feedback.getBlockingIssues() != null
                && !feedback.getBlockingIssues().isEmpty()
                && feedback.getNextLearningAction() != null;
    }

    private SubmissionAnalysisResponse.StudentFeedback normalize(SubmissionAnalysisResponse.StudentFeedback feedback,
                                                                 SubmissionAnalysisResponse analysis,
                                                                 DiagnosisEvidencePackage evidencePackage,
                                                                 boolean fallbackUsed) {
        List<SubmissionAnalysisResponse.FeedbackIssue> blockingIssues =
                feedback.getBlockingIssues() == null ? List.of() : feedback.getBlockingIssues();
        List<SubmissionAnalysisResponse.SecondaryIssue> secondaryIssues =
                feedback.getSecondaryIssues() == null ? List.of() : feedback.getSecondaryIssues();
        List<SubmissionAnalysisResponse.ImprovementOpportunity> improvements =
                filterDuplicateImprovements(feedback.getImprovementOpportunities(), primaryIssueTag(analysis), primaryFineTag(analysis));
        return SubmissionAnalysisResponse.StudentFeedback.builder()
                .summary(defaultIfBlank(feedback.getSummary(), summary(analysis, blockingIssues.get(0), fallbackUsed)))
                .blockingIssues(blockingIssues)
                .secondaryIssues(secondaryIssues)
                .improvementOpportunities(improvements)
                .nextLearningAction(feedback.getNextLearningAction())
                .build();
    }

    private SubmissionAnalysisResponse.FeedbackIssue buildBlockingIssue(SubmissionAnalysisResponse analysis,
                                                                        DiagnosisEvidencePackage evidencePackage,
                                                                        String primaryIssueTag,
                                                                        String fineGrainedTag,
                                                                        String teachingTag,
                                                                        List<String> evidenceRefs) {
        SubmissionAnalysisResponse.StudentHintPlan hintPlan = analysis.getStudentHintPlan();
        String label = label(teachingTag);
        String evidence = evidenceText(analysis, evidencePackage, teachingTag);
        String nextAction = hintPlan == null
                ? defaultNextAction(teachingTag)
                : defaultIfBlank(hintPlan.getNextAction(), defaultNextAction(teachingTag));
        return SubmissionAnalysisResponse.FeedbackIssue.builder()
                .priority(1)
                .title("当前最需要先处理的问题")
                .studentMessage(blockingMessage(analysis, evidencePackage, teachingTag, label))
                .evidence(evidence)
                .nextAction(nextAction)
                .issueTag(primaryIssueTag)
                .fineGrainedTag(fineGrainedTag.isBlank() ? null : fineGrainedTag)
                .evidenceRefs(evidenceRefs)
                .build();
    }

    private String blockingMessage(SubmissionAnalysisResponse analysis,
                                   DiagnosisEvidencePackage evidencePackage,
                                   String teachingTag,
                                   String label) {
        String lowerSummary = (analysis.getSummary() == null ? "" : analysis.getSummary()).toLowerCase(Locale.ROOT);
        String failedEvidence = firstFailedCaseText(analysis, evidencePackage);
        if ("INPUT_PARSING".equals(teachingTag) || "IO_FORMAT".equals(teachingTag)) {
            if (failedEvidence.contains("实际只输出") || failedEvidence.contains("少输出")) {
                return "题目要求处理的输入结构和当前代码实际处理的次数不一致，先把读取次数和输出行数对齐。";
            }
            return "当前最像是输入或输出结构没有和题面要求一一对应，先检查每一段输入是否都被处理到。";
        }
        if ("OUTPUT_FORMAT_DETAIL".equals(teachingTag)) {
            return "当前失败优先表现为输出格式不一致，先做逐字符对比，再考虑算法改动。";
        }
        if ("TIME_COMPLEXITY".equals(teachingTag) || "BRUTE_FORCE_LIMIT".equals(teachingTag)
                || "OVER_SIMULATION".equals(teachingTag)) {
            return "当前提交的主要风险在最大规模下操作次数过多，先估算核心循环在上限输入下会运行多少次。";
        }
        if ("DP_STATE_DESIGN".equals(teachingTag) || "STATE_TRANSITION".equals(teachingTag)) {
            return "这次更像是动态规划的状态含义没有先定清楚，先用一句话写出每个状态表示什么，再检查它依赖的信息是否已经算好。";
        }
        if ("GREEDY_ASSUMPTION".equals(teachingTag)) {
            return "当前风险在贪心选择的依据还不够稳，先找一个小反例验证：每一步看起来最优，是否一定能推出整体最优。";
        }
        if ("SORT_KEY".equals(teachingTag)) {
            return "这次重点不是排序函数本身，而是排序依据是否和题目真正要求一致，先拿两个容易混淆的元素手动比较。";
        }
        if ("UNINITIALIZED_VARIABLE".equals(teachingTag)) {
            return "当前代码里有变量可能在赋值前就参与判断或输出，先确认每个计数器、答案变量和标志变量都有明确初始值。";
        }
        if ("RUNTIME_STABILITY".equals(teachingTag) || "EMPTY_INPUT".equals(teachingTag)) {
            return "当前代码存在运行稳定性风险，先定位最小触发场景，再检查下标、空输入或除零。";
        }
        if ("SYNTAX_ERROR".equals(teachingTag)) {
            return "当前代码还没有进入运行阶段，先只处理第一条编译或语法报错。";
        }
        if ("AC".equalsIgnoreCase(safe(analysis.getScenario())) || lowerSummary.contains("已经通过")) {
            return "这次已经通过，当前重点从修错转为复盘：确认代码为什么能覆盖更多边界。";
        }
        return "当前最需要先处理的是“" + label + "”，先用已有失败证据验证它是否解释了当前结果。";
    }

    private String evidenceText(SubmissionAnalysisResponse analysis,
                                DiagnosisEvidencePackage evidencePackage,
                                String teachingTag) {
        String failedCaseText = firstFailedCaseText(analysis, evidencePackage);
        if (!failedCaseText.isBlank()) {
            return failedCaseText;
        }
        if (analysis.getStudentHintPlan() != null && !blank(analysis.getStudentHintPlan().getEvidenceAnchor())) {
            return analysis.getStudentHintPlan().getEvidenceAnchor();
        }
        List<String> refs = analysis.getEvidenceRefs();
        if (refs != null && !refs.isEmpty()) {
            return "当前判断绑定证据：" + refs.get(0);
        }
        return "当前判断来自评测结果和代码结构信号。";
    }

    private String firstFailedCaseText(SubmissionAnalysisResponse analysis,
                                       DiagnosisEvidencePackage evidencePackage) {
        SubmissionAnalysisResponse.FailedCaseSnapshot failedCase = analysis.getFirstFailedCase();
        if (failedCase == null && evidencePackage != null && evidencePackage.getJudgeFacts() != null) {
            failedCase = evidencePackage.getJudgeFacts().getFirstFailedCase();
        }
        if (failedCase == null || failedCase.isHidden()) {
            return "";
        }
        String actual = safe(failedCase.getActualOutput());
        String expected = safe(failedCase.getExpectedOutput());
        if (!expected.isBlank() && !actual.isBlank()) {
            int expectedLines = lineCount(expected);
            int actualLines = lineCount(actual);
            if (expectedLines > actualLines) {
                return "第一个失败用例期望输出 " + expectedLines + " 行，实际只输出 " + actualLines + " 行。";
            }
            return "第一个失败用例的期望输出是“" + compact(expected) + "”，实际输出是“" + compact(actual) + "”。";
        }
        return "";
    }

    private List<SubmissionAnalysisResponse.SecondaryIssue> buildSecondaryIssues(SubmissionAnalysisResponse analysis,
                                                                                 DiagnosisEvidencePackage evidencePackage,
                                                                                 String primaryIssueTag,
                                                                                 String fineGrainedTag,
                                                                                 List<String> evidenceRefs) {
        List<SubmissionAnalysisResponse.SecondaryIssue> issues = new ArrayList<>();
        List<String> tags = merge(analysis.getIssueTags(), analysis.getFineGrainedTags());
        for (String tag : tags) {
            String normalized = safe(tag).trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank() || normalized.equals(primaryIssueTag) || normalized.equals(fineGrainedTag)) {
                continue;
            }
            if (issues.size() >= 2) {
                break;
            }
            issues.add(SubmissionAnalysisResponse.SecondaryIssue.builder()
                    .title("可能的次要问题")
                    .studentMessage("也可以留意“" + label(normalized) + "”，但它不应压过当前第一失败证据。")
                    .whyNotPrimary("当前优先级由 first failed case、运行报错或最高置信规则信号决定。")
                    .issueTag(normalized)
                    .evidenceRefs(evidenceRefs)
                    .build());
        }
        return issues;
    }

    private List<SubmissionAnalysisResponse.ImprovementOpportunity> buildImprovements(
            SubmissionAnalysisResponse analysis,
            DiagnosisEvidencePackage evidencePackage,
            String primaryIssueTag,
            String fineGrainedTag,
            List<String> evidenceRefs) {
        List<SubmissionAnalysisResponse.ImprovementOpportunity> opportunities = new ArrayList<>();
        List<String> improvementEvidenceRefs = List.of();
        String scenario = safe(analysis.getScenario()).toUpperCase(Locale.ROOT);
        String source = evidencePackage == null || evidencePackage.getSubmission() == null
                ? ""
                : safe(evidencePackage.getSubmission().getSourceCode());
        addFocusedImprovement(opportunities, primaryIssueTag, fineGrainedTag, improvementEvidenceRefs);
        boolean hasFocusedImprovement = !opportunities.isEmpty();
        if (!hasFocusedImprovement
                && ("TIME_COMPLEXITY".equals(primaryIssueTag) || "ALGORITHM_STRATEGY".equals(primaryIssueTag) || "TLE".equals(scenario))) {
            opportunities.add(improvement("COMPLEXITY",
                    "修正当前问题后，可以用最大输入规模估算一次核心操作次数。",
                    "这能帮助你判断算法是否只是样例能过，还是在上限下也站得住。",
                    improvementEvidenceRefs));
        }
        if (!hasFocusedImprovement && (source.contains("for ") || source.contains("while ") || "WA".equals(scenario))) {
            opportunities.add(improvement("TESTING_HABIT",
                    "给自己补一个和样例结构不同的小测试，尤其覆盖多组输入、边界值或重复元素。",
                    "自测习惯能提前暴露只适配样例或漏处理输入结构的问题。",
                    improvementEvidenceRefs));
        }
        if (!hasFocusedImprovement
                && (source.length() > 900 || source.contains("debug") || source.contains("print(") || source.contains("System.out"))) {
            opportunities.add(improvement("CODE_CLARITY",
                    "通过当前错误后，可以把读取输入、核心计算和输出整理成更清楚的几个步骤。",
                    "结构清楚后，漏读、漏输出和调试残留会更容易被自己发现。",
                    improvementEvidenceRefs));
        }
        if ("BOUNDARY_CONDITION".equals(primaryIssueTag) || "LOOP_BOUNDARY".equals(fineGrainedTag)
                || "RUNTIME_STABILITY".equals(primaryIssueTag) || "RE".equals(scenario)) {
            opportunities.add(improvement("BOUNDARY_AWARENESS",
                    "把最小输入、单元素、最大值和空结果这几类边界列成检查清单。",
                    "边界清单能减少同类错误在下一题重复出现。",
                    improvementEvidenceRefs));
        }
        if (source.contains("stderr") || source.toLowerCase(Locale.ROOT).contains("debug")) {
            opportunities.add(improvement("DEBUG_CLEANUP",
                    "通过后清理调试输出和临时分支，只保留真正服务解题逻辑的代码。",
                    "减少干扰代码能让你和老师更快看清主流程。",
                    improvementEvidenceRefs));
        }
        if (opportunities.isEmpty()) {
            opportunities.add(improvement("ROBUSTNESS",
                    "修正当前问题后，再构造一个极端小样例和一个接近上限的样例复查。",
                    "这能帮助你确认代码不是只修好了眼前这一组数据。",
                    improvementEvidenceRefs));
        }
        return filterDuplicateImprovements(opportunities, primaryIssueTag, fineGrainedTag).stream()
                .limit(3)
                .toList();
    }

    private void addFocusedImprovement(List<SubmissionAnalysisResponse.ImprovementOpportunity> opportunities,
                                       String primaryIssueTag,
                                       String fineGrainedTag,
                                       List<String> evidenceRefs) {
        String tag = blank(fineGrainedTag) ? primaryIssueTag : fineGrainedTag;
        switch (tag) {
            case "DP_STATE_DESIGN", "STATE_TRANSITION" -> opportunities.add(improvement("DP_MODELING",
                    "修正后，把状态含义、初始状态和依赖信息分别写成一句话，再用最小样例逐格核对。",
                    "这样能避免只会写转移式，却说不清每个状态到底代表什么。",
                    evidenceRefs));
            case "GREEDY_ASSUMPTION" -> opportunities.add(improvement("GREEDY_PROOF",
                    "修正后，给自己的贪心规则补一个反例检查或交换理由，不要只用样例判断它对不对。",
                    "贪心题最容易错在局部最优看起来合理，但整体并不保证最优。",
                    evidenceRefs));
            case "SORT_KEY" -> opportunities.add(improvement("SORTING_MODEL",
                    "修正后，专门补测相同前缀、不同位数或排序依据相近的数据。",
                    "这些样例能逼你确认比较规则服务的是题意，而不是默认字典序或数值直觉。",
                    evidenceRefs));
            case "UNINITIALIZED_VARIABLE" -> opportunities.add(improvement("INITIALIZATION_HABIT",
                    "通过后回看所有变量，把“定义时就有合理初值”当作固定检查项。",
                    "初始化习惯能减少本地偶尔能跑、评测机结果却不稳定的问题。",
                    evidenceRefs));
            default -> {
            }
        }
    }

    private SubmissionAnalysisResponse.ImprovementOpportunity improvement(String category,
                                                                          String message,
                                                                          String benefit,
                                                                          List<String> evidenceRefs) {
        return SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                .category(category)
                .studentMessage(message)
                .benefit(benefit)
                .evidenceRefs(evidenceRefs)
                .build();
    }

    private List<SubmissionAnalysisResponse.ImprovementOpportunity> filterDuplicateImprovements(
            List<SubmissionAnalysisResponse.ImprovementOpportunity> source,
            String primaryIssueTag,
            String fineGrainedTag) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<SubmissionAnalysisResponse.ImprovementOpportunity> filtered = new ArrayList<>();
        for (SubmissionAnalysisResponse.ImprovementOpportunity item : source) {
            if (item == null || blank(item.getCategory())) {
                continue;
            }
            String category = item.getCategory().trim().toUpperCase(Locale.ROOT);
            if (("COMPLEXITY".equals(category) && "TIME_COMPLEXITY".equals(primaryIssueTag))
                    || ("BOUNDARY_AWARENESS".equals(category) && "BOUNDARY_CONDITION".equals(primaryIssueTag))) {
                // Keep the suggestion as improvement only when it adds learning value, not a duplicate cause.
                if (safe(item.getStudentMessage()).contains("当前")) {
                    continue;
                }
            }
            if (seen.add(category)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private SubmissionAnalysisResponse.NextLearningAction nextLearningAction(SubmissionAnalysisResponse analysis,
                                                                             SubmissionAnalysisResponse.FeedbackIssue issue,
                                                                             String teachingTag,
                                                                             List<String> evidenceRefs) {
        SubmissionAnalysisResponse.StudentHintPlan hintPlan = analysis.getStudentHintPlan();
        return SubmissionAnalysisResponse.NextLearningAction.builder()
                .hintLevel(hintPlan == null ? "L2" : defaultIfBlank(hintPlan.getHintLevel(), "L2"))
                .action(hintPlan == null ? teachingAction(teachingTag) : defaultIfBlank(hintPlan.getTeachingAction(), teachingAction(teachingTag)))
                .task(defaultIfBlank(issue.getNextAction(), defaultNextAction(teachingTag)))
                .checkQuestion(hintPlan == null ? defaultCoachQuestion(teachingTag)
                        : defaultIfBlank(hintPlan.getCoachQuestion(), defaultCoachQuestion(teachingTag)))
                .evidenceRefs(evidenceRefs)
                .answerLeakRisk("LOW")
                .build();
    }

    private String summary(SubmissionAnalysisResponse analysis,
                           SubmissionAnalysisResponse.FeedbackIssue issue,
                           boolean fallbackUsed) {
        String base = issue == null ? "" : issue.getStudentMessage();
        if (base.isBlank()) {
            base = defaultIfBlank(analysis.getSummary(), "系统已经整理出当前提交的主要问题和下一步检查动作。");
        }
        if (fallbackUsed) {
            return "本地可验证反馈：" + base + " AI 暂不可用，先看本地反馈。";
        }
        return base;
    }

    private String primaryIssueTag(SubmissionAnalysisResponse analysis) {
        if (analysis == null || analysis.getIssueTags() == null || analysis.getIssueTags().isEmpty()) {
            return "NEEDS_MORE_EVIDENCE";
        }
        return safe(analysis.getIssueTags().get(0)).trim().toUpperCase(Locale.ROOT);
    }

    private String primaryFineTag(SubmissionAnalysisResponse analysis) {
        if (analysis == null || analysis.getFineGrainedTags() == null || analysis.getFineGrainedTags().isEmpty()) {
            return "";
        }
        return safe(analysis.getFineGrainedTags().get(0)).trim().toUpperCase(Locale.ROOT);
    }

    private String label(String tag) {
        String normalized = safe(tag).trim().toUpperCase(Locale.ROOT);
        String label = diagnosisTaxonomy.label(normalized);
        return defaultIfBlank(label, normalized.isBlank() ? "问题定位" : normalized);
    }

    private String teachingAction(String tag) {
        String action = diagnosisTaxonomy.teachingAction(safe(tag).trim().toUpperCase(Locale.ROOT));
        return defaultIfBlank(action, "COLLECT_EVIDENCE");
    }

    private String defaultNextAction(String teachingTag) {
        String action = teachingAction(teachingTag);
        return switch (action) {
            case "COMPARE_INPUT_SPEC" -> "把题面输入格式和代码里的读取操作一一对应，确认每一组数据都被处理。";
            case "COMPARE_OUTPUT" -> "把实际输出和期望输出逐字符对比，先定位多了、少了或换行不一致的位置。";
            case "COUNT_COMPLEXITY" -> "估算最大输入规模下核心循环或递归会执行多少次。";
            case "ASK_MIN_CASE" -> "构造一个最小边界样例，手推当前代码的关键变量变化。";
            case "TRACE_VARIABLES" -> "选第一个失败样例，追踪关键变量在第一轮和最后一轮的值。";
            case "CHECK_RUNTIME_GUARDS" -> "找一个最小输入，验证是否会触发越界、空值或除零。";
            default -> "先选一个最小证据，验证当前判断是否能解释这次提交结果。";
        };
    }

    private String defaultCoachQuestion(String teachingTag) {
        String action = teachingAction(teachingTag);
        return switch (action) {
            case "COMPARE_INPUT_SPEC" -> "题面要求读入几组数据？你的代码实际处理了几组？";
            case "COMPARE_OUTPUT" -> "第一处输出差异是字符、空格、换行，还是输出行数？";
            case "COUNT_COMPLEXITY" -> "输入达到上限时，核心操作大约会执行多少次？";
            case "ASK_MIN_CASE" -> "这个最小样例中，哪一步开始和你的预期不一样？";
            case "TRACE_VARIABLES" -> "关键变量第一次变化和最后一次变化是否都符合你的定义？";
            case "CHECK_RUNTIME_GUARDS" -> "哪个最小输入最可能触发当前运行风险？";
            default -> "你准备用哪一个证据确认这个判断？";
        };
    }

    private List<String> merge(List<String> left, List<String> right) {
        List<String> values = new ArrayList<>();
        if (left != null) {
            values.addAll(left);
        }
        if (right != null) {
            values.addAll(right);
        }
        return values;
    }

    private List<String> deduplicate(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (!blank(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private int lineCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return value.split("\\R", -1).length;
    }

    private String compact(String value) {
        String compacted = safe(value).replaceAll("\\s+", " ").trim();
        if (compacted.length() <= 80) {
            return compacted;
        }
        return compacted.substring(0, 77) + "...";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return blank(value) ? safe(fallback) : value;
    }
}

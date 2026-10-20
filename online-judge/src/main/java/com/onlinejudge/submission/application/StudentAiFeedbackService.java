package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import com.onlinejudge.submission.domain.AiDiagnosisRun;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.StudentAiFeedbackLookupResponse;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRevisionRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAiFeedbackService {

    private static final String SOURCE_MODEL = "MODEL";
    private static final String SOURCE_AI_UNAVAILABLE = "AI_UNAVAILABLE";
    private static final Pattern CODE_LINE_REF = Pattern.compile("^code:line:(\\d+)$");
    private static final Pattern CODE_RANGE_REF = Pattern.compile("^code:range:(\\d+)-(\\d+)$");
    private static final Pattern CODE_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{2,}");
    private static final Duration GENERATING_EXPIRES_AFTER = Duration.ofMinutes(5);

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;
    private final StudentAiFeedbackRepository studentAiFeedbackRepository;
    private final StudentAiFeedbackEventRepository studentAiFeedbackEventRepository;
    private final StudentAiFeedbackRevisionRepository studentAiFeedbackRevisionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final AiStandardSkillUnitRepository skillUnitRepository;
    private final AiStandardMistakePointRepository mistakePointRepository;
    private final AiStandardImprovementPointRepository improvementPointRepository;
    private final InformaticsKnowledgeNodeRepository knowledgeRepository;
    private final ObjectMapper objectMapper;
    private StudentIssueLifecycleReadService issueLifecycleReadService;
    private AiDiagnosisWorkflowService diagnosisWorkflowService;
    private SubmissionGrowthSummaryService growthSummaryService;

    @Autowired(required = false)
    void setIssueLifecycleReadService(StudentIssueLifecycleReadService issueLifecycleReadService) {
        this.issueLifecycleReadService = issueLifecycleReadService;
    }

    @Autowired(required = false)
    void setDiagnosisWorkflowService(AiDiagnosisWorkflowService diagnosisWorkflowService) {
        this.diagnosisWorkflowService = diagnosisWorkflowService;
    }

    @Autowired(required = false)
    void setGrowthSummaryService(SubmissionGrowthSummaryService growthSummaryService) {
        this.growthSummaryService = growthSummaryService;
    }

    @Transactional(readOnly = true)
    public StudentAiFeedbackLookupResponse getLookup(Long submissionId) {
        ensureSubmissionExists(submissionId);
        StudentAiFeedbackLookupResponse lookup = studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .map(this::toLookup)
                .orElse(StudentAiFeedbackLookupResponse.builder()
                        .status("NOT_REQUESTED")
                        .feedback(notRequested(submissionId))
                        .build());
        hydrateGrowth(submissionId, lookup.getFeedback());
        return lookup;
    }

    @Transactional
    public StudentAiFeedbackLookupResponse markGenerating(Long submissionId) {
        return markGenerating(submissionId, false);
    }

    @Transactional
    public StudentAiFeedbackLookupResponse startNewGeneration(Long submissionId) {
        return markGenerating(submissionId, true);
    }

    private StudentAiFeedbackLookupResponse markGenerating(Long submissionId, boolean forceNewVersion) {
        ensureSubmissionExists(submissionId);
        StudentAiFeedback entity = studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .orElse(StudentAiFeedback.builder()
                        .submissionId(submissionId)
                        .source(SOURCE_MODEL)
                        .build());
        if (!forceNewVersion && "READY".equals(entity.getStatus()) && !needsContractRefresh(entity)) {
            return toLookup(entity);
        }
        if (!forceNewVersion && "GENERATING".equals(entity.getStatus()) && !isGeneratingExpired(entity)) {
            return toLookup(entity);
        }
        entity.setGenerationKey(UUID.randomUUID().toString());
        StudentAiFeedbackResponse feedback = StudentAiFeedbackResponse.builder()
                .submissionId(submissionId)
                .status("GENERATING")
                .source(SOURCE_AI_UNAVAILABLE)
                .generatedAt(LocalDateTime.now())
                .repairItems(List.of())
                .improvementItems(List.of())
                .safety(StudentAiFeedbackResponse.Safety.builder()
                        .answerLeakRisk("LOW")
                        .blockedReasons(List.of())
                        .build())
                .evidenceRefs(List.of())
                .build();
        entity.setStatus("GENERATING");
        entity.setSource(SOURCE_AI_UNAVAILABLE);
        entity.setFailureReason(null);
        entity.setFeedbackJson(serialize(feedback));
        StudentAiFeedback saved = studentAiFeedbackRepository.save(entity);
        if (diagnosisWorkflowService != null) {
            diagnosisWorkflowService.beginRun(submissionId, saved.getGenerationKey());
        }
        return toLookup(saved);
    }

    @Transactional(readOnly = true)
    public boolean isGeneratingExpired(Long submissionId) {
        if (submissionId == null) {
            return false;
        }
        return studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .filter(entity -> "GENERATING".equals(entity.getStatus()))
                .map(this::isGeneratingExpired)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean needsContractRefresh(Long submissionId) {
        if (submissionId == null) {
            return false;
        }
        return studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .map(this::needsContractRefresh)
                .orElse(false);
    }

    private boolean isGeneratingExpired(StudentAiFeedback entity) {
        if (entity == null || entity.getGeneratedAt() == null) {
            return true;
        }
        return entity.getGeneratedAt().isBefore(LocalDateTime.now().minus(GENERATING_EXPIRES_AFTER));
    }

    private boolean needsContractRefresh(StudentAiFeedback entity) {
        if (entity == null || !"READY".equals(entity.getStatus()) || !SOURCE_MODEL.equals(entity.getSource())) {
            return false;
        }
        StudentAiFeedbackResponse feedback = deserialize(entity);
        if (feedback == null) {
            return true;
        }
        List<StudentAiFeedbackResponse.FeedbackItem> items = new ArrayList<>();
        items.addAll(safe(feedback.getRepairItems()));
        items.addAll(safe(feedback.getImprovementItems()));
        return items.stream()
                .noneMatch(item -> item != null && (!clean(item.getTitle()).isBlank() || !clean(item.getBody()).isBlank()));
    }

    @Transactional
    public StudentAiFeedbackResponse markFailed(Long submissionId, String reason) {
        ensureSubmissionExists(submissionId);
        StudentAiFeedbackResponse feedback = StudentAiFeedbackResponse.builder()
                .submissionId(submissionId)
                .status("FAILED")
                .source(SOURCE_AI_UNAVAILABLE)
                .generatedAt(LocalDateTime.now())
                .repairItems(List.of())
                .improvementItems(List.of())
                .safety(StudentAiFeedbackResponse.Safety.builder()
                        .answerLeakRisk("LOW")
                        .blockedReasons(reason == null || reason.isBlank() ? List.of("GENERATION_FAILED") : List.of(reason))
                        .build())
                .evidenceRefs(List.of())
                .build();
        StudentAiFeedback entity = studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .orElse(StudentAiFeedback.builder()
                        .submissionId(submissionId)
                        .source(SOURCE_MODEL)
                        .build());
        entity.setStatus("FAILED");
        entity.setSource(SOURCE_AI_UNAVAILABLE);
        entity.setFailureReason(failureReason(feedback));
        entity.setFeedbackJson(serialize(feedback));
        ensureGenerationKey(entity);
        StudentAiFeedback saved = studentAiFeedbackRepository.save(entity);
        StudentAiFeedbackRevision revision = saveRevision(saved, feedback, null);
        saved.setLatestRevisionId(revision.getId());
        saved = studentAiFeedbackRepository.save(saved);
        recordGenerationEvent(
                submissionRepository.findById(submissionId).orElse(null),
                saved,
                feedback
        );
        feedback.setGeneratedAt(saved.getGeneratedAt());
        if (diagnosisWorkflowService != null) {
            diagnosisWorkflowService.latestRun(submissionId)
                    .ifPresent(run -> diagnosisWorkflowService.failRun(run.getId(), new IllegalStateException(reason)));
        }
        return feedback;
    }

    public StudentAiFeedbackResponse generateAndStore(Long submissionId) {
        long startedAt = System.nanoTime();
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + submission.getProblemId()));
        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(submissionId);
        AiDiagnosisRun run = activeRun(submissionId);
        try (AiDiagnosisWorkflowContext.Scope ignored = AiDiagnosisWorkflowContext.activate(run == null ? null : run.getId())) {
            SubmissionAnalysisResponse analysis = submissionAnalysisService.generateAndStoreAnalysis(problem, submission, caseResults);
            StudentAiFeedbackResponse feedback = toStudentAiFeedback(submission, analysis);
            feedback.setLatencyMs(elapsedMs(startedAt));
            StudentAiFeedbackResponse saved = saveFeedback(submission, feedback, analysis);
            if (diagnosisWorkflowService != null && run != null) {
                if ("READY".equals(saved.getStatus())) {
                    diagnosisWorkflowService.markResultReady(run.getId());
                    diagnosisWorkflowService.completeRun(run.getId());
                } else {
                    diagnosisWorkflowService.failRun(run.getId(), new IllegalStateException(failureReason(saved)));
                }
            }
            return saved;
        } catch (RuntimeException exception) {
            if (diagnosisWorkflowService != null && run != null) {
                diagnosisWorkflowService.failRun(run.getId(), exception);
            }
            throw exception;
        }
    }

    private AiDiagnosisRun activeRun(Long submissionId) {
        if (diagnosisWorkflowService == null) {
            return null;
        }
        StudentAiFeedback entity = studentAiFeedbackRepository.findBySubmissionId(submissionId).orElse(null);
        if (entity == null) {
            return null;
        }
        ensureGenerationKey(entity);
        return diagnosisWorkflowService.beginRun(submissionId, entity.getGenerationKey());
    }

    private StudentAiFeedbackResponse toStudentAiFeedback(Submission submission, SubmissionAnalysisResponse analysis) {
        if (analysis == null || modelFailed(analysis)) {
            return failedFeedback(submission.getId(), modelFailureReason(analysis));
        }
        List<StudentAiFeedbackResponse.FeedbackItem> repairItems = repairItems(submission, analysis);
        List<StudentAiFeedbackResponse.FeedbackItem> improvementItems = improvementItems(submission, analysis);
        StudentAiFeedbackResponse.StudentReport report = studentReport(analysis, repairItems, improvementItems);
        if (repairItems.isEmpty() && improvementItems.isEmpty()) {
            return failedFeedback(submission.getId(), "FULL_CHAIN_FEEDBACK_EMPTY");
        }
        return StudentAiFeedbackResponse.builder()
                .submissionId(submission.getId())
                .status("READY")
                .source(SOURCE_MODEL)
                .generatedAt(LocalDateTime.now())
                .repairItems(repairItems)
                .improvementItems(improvementItems)
                .studentReport(report)
                .nextQuestion(nextQuestion(analysis))
                .safety(StudentAiFeedbackResponse.Safety.builder()
                        .answerLeakRisk(firstNonBlank(analysis.getAnswerLeakRisk(), nextActionRisk(analysis), "LOW"))
                        .blockedReasons(List.of())
                        .build())
                .evidenceRefs(evidenceRefs(analysis, repairItems, improvementItems))
                .build();
    }

    private boolean modelFailed(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.AiInvocation invocation = analysis.getAiInvocation();
        return invocation != null && "MODEL_FAILED".equalsIgnoreCase(invocation.getStatus());
    }

    private String modelFailureReason(SubmissionAnalysisResponse analysis) {
        if (analysis == null || analysis.getAiInvocation() == null) {
            return "FULL_CHAIN_FAILED";
        }
        String reason = analysis.getAiInvocation().getFailureReason();
        return reason == null || reason.isBlank() ? "FULL_CHAIN_FAILED" : reason;
    }

    private List<StudentAiFeedbackResponse.FeedbackItem> repairItems(Submission submission, SubmissionAnalysisResponse analysis) {
        List<StudentAiFeedbackResponse.FeedbackItem> items = new ArrayList<>();
        for (SubmissionAnalysisResponse.BasicLayerAdvice advice : safe(analysis.getBasicLayerAdvice())) {
            if (advice == null) {
                continue;
            }
            String title = clean(advice.getTitle());
            String body = joinNonBlank(advice.getWhatHappened(), advice.getWhyItMatters(), advice.getStudentAction());
            List<String> refs = evidenceRefsForItem(safe(advice.getEvidenceRefs()), title, body, submission, analysis);
            addItem(items, StudentAiFeedbackResponse.FeedbackItem.builder()
                    .title(title)
                    .body(body)
                    .kind("REPAIR")
                    .skillUnitId(cleanId(advice.getSkillUnitId()))
                    .mistakePointId(cleanId(advice.getMistakePointId()))
                    .libraryFit(libraryFit(analysis))
                    .knowledgePath(repairKnowledgePath(advice, title, body, analysis))
                    .knowledgePathStatus(pathStatus(
                            advice.getKnowledgePathStatus(),
                            advice.getSkillUnitId(),
                            advice.getMistakePointId()))
                    .provisionalNodeCode(cleanId(advice.getProvisionalNodeCode()))
                    .evidenceRefs(refs)
                    .evidenceSnippets(evidenceSnippets(refs, submission))
                    .qualitySignals(List.of("evidence_grounded", "actionable", "no_answer_leak"))
                    .build());
        }
        if (!items.isEmpty() || analysis.getStudentFeedback() == null) {
            return items;
        }
        for (SubmissionAnalysisResponse.FeedbackIssue issue : safe(analysis.getStudentFeedback().getBlockingIssues())) {
            if (issue == null) {
                continue;
            }
            String title = clean(issue.getTitle());
            String body = joinNonBlank(issue.getStudentMessage(), issue.getNextAction());
            List<String> refs = evidenceRefsForItem(safe(issue.getEvidenceRefs()), title, body, submission, analysis);
            addItem(items, StudentAiFeedbackResponse.FeedbackItem.builder()
                    .title(title)
                    .body(body)
                    .kind("REPAIR")
                    .skillUnitId(cleanId(issue.getIssueTag()))
                    .mistakePointId(cleanId(issue.getFineGrainedTag()))
                    .libraryFit(libraryFit(analysis))
                    .knowledgePath(knowledgePathFallback(issue.getIssueTag(), issue.getFineGrainedTag(), title))
                    .knowledgePathStatus("INFERRED")
                    .evidenceRefs(refs)
                    .evidenceSnippets(evidenceSnippets(refs, submission))
                    .qualitySignals(List.of("evidence_grounded", "actionable", "no_answer_leak"))
                    .build());
        }
        return items;
    }

    private List<StudentAiFeedbackResponse.FeedbackItem> improvementItems(Submission submission, SubmissionAnalysisResponse analysis) {
        List<StudentAiFeedbackResponse.FeedbackItem> items = new ArrayList<>();
        for (SubmissionAnalysisResponse.ImprovementLayerAdvice advice : safe(analysis.getImprovementLayerAdvice())) {
            if (advice == null) {
                continue;
            }
            String title = clean(advice.getTitle());
            String body = joinNonBlank(advice.getCurrentLimit(), advice.getSuggestion(), advice.getStudentBenefit());
            List<String> refs = evidenceRefsForItem(safe(advice.getEvidenceRefs()), title, body, submission, analysis);
            addItem(items, StudentAiFeedbackResponse.FeedbackItem.builder()
                    .title(title)
                    .body(body)
                    .kind("IMPROVEMENT")
                    .skillUnitId(cleanId(advice.getSkillUnitId()))
                    .improvementPointId(cleanId(advice.getImprovementPointId()))
                    .libraryFit(libraryFit(analysis))
                    .knowledgePath(improvementKnowledgePath(advice, title, body, analysis))
                    .knowledgePathStatus(pathStatus(
                            advice.getKnowledgePathStatus(),
                            advice.getSkillUnitId(),
                            advice.getImprovementPointId()))
                    .provisionalNodeCode(cleanId(advice.getProvisionalNodeCode()))
                    .evidenceRefs(refs)
                    .evidenceSnippets(evidenceSnippets(refs, submission))
                    .qualitySignals(List.of("transfer"))
                    .build());
        }
        if (!items.isEmpty() || analysis.getStudentFeedback() == null) {
            return items;
        }
        for (SubmissionAnalysisResponse.ImprovementOpportunity item : safe(analysis.getStudentFeedback().getImprovementOpportunities())) {
            if (item == null) {
                continue;
            }
            String title = clean(item.getTitle());
            String body = joinNonBlank(item.getStudentMessage(), item.getBenefit());
            List<String> refs = evidenceRefsForItem(safe(item.getEvidenceRefs()), title, body, submission, analysis);
            addItem(items, StudentAiFeedbackResponse.FeedbackItem.builder()
                    .title(title)
                    .body(body)
                    .kind("IMPROVEMENT")
                    .improvementPointId(cleanId(item.getCategory()))
                    .libraryFit(libraryFit(analysis))
                    .knowledgePath(knowledgePathFallback(item.getCategory(), title))
                    .knowledgePathStatus("INFERRED")
                    .evidenceRefs(refs)
                    .evidenceSnippets(evidenceSnippets(refs, submission))
                    .qualitySignals(List.of("transfer"))
                    .build());
        }
        return items;
    }

    private StudentAiFeedbackResponse.StudentReport studentReport(
            SubmissionAnalysisResponse analysis,
            List<StudentAiFeedbackResponse.FeedbackItem> repairItems,
            List<StudentAiFeedbackResponse.FeedbackItem> improvementItems
    ) {
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis.getStudentFeedback();
        String basic = firstNonBlank(
                feedback == null ? "" : feedback.getSummary(),
                analysis.getSummary(),
                repairItems.isEmpty() ? "" : "下面是本次完整诊断给出的修正重点。"
        );
        String improvement = improvementItems.isEmpty() ? "" : "修完基础问题后，再按下面的提升建议做迁移检查。";
        String nextAction = firstNonBlank(nextActionTask(analysis), nextQuestion(analysis), analysis.getStudentHint());
        if (basic.isBlank() && improvement.isBlank() && nextAction.isBlank()) {
            return null;
        }
        return StudentAiFeedbackResponse.StudentReport.builder()
                .basicLayerText(blankToNull(basic))
                .improvementLayerText(blankToNull(improvement))
                .nextActionText(blankToNull(nextAction))
                .build();
    }

    private String nextQuestion(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.NextLearningAction action = nextAction(analysis);
        return firstNonBlank(action == null ? "" : action.getCheckQuestion(), action == null ? "" : action.getTask());
    }

    private String nextActionTask(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.NextLearningAction action = nextAction(analysis);
        return action == null ? "" : clean(action.getTask());
    }

    private String nextActionRisk(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.NextLearningAction action = nextAction(analysis);
        return action == null ? "" : clean(action.getAnswerLeakRisk());
    }

    private SubmissionAnalysisResponse.NextLearningAction nextAction(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis == null ? null : analysis.getStudentFeedback();
        return feedback == null ? null : feedback.getNextLearningAction();
    }

    private String libraryFit(SubmissionAnalysisResponse analysis) {
        if (analysis == null || analysis.getAiInvocation() == null) {
            return null;
        }
        String value = firstNonBlank(
                analysis.getAiInvocation().getDiagnosisLibraryFit(),
                analysis.getAiInvocation().getLibraryFit()
        ).toUpperCase();
        return value.isBlank() ? null : value;
    }

    private List<String> evidenceRefs(SubmissionAnalysisResponse analysis,
                                      List<StudentAiFeedbackResponse.FeedbackItem> repairItems,
                                      List<StudentAiFeedbackResponse.FeedbackItem> improvementItems) {
        Set<String> refs = new LinkedHashSet<>(safe(analysis.getEvidenceRefs()));
        for (StudentAiFeedbackResponse.FeedbackItem item : safe(repairItems)) {
            refs.addAll(safe(item.getEvidenceRefs()));
        }
        for (StudentAiFeedbackResponse.FeedbackItem item : safe(improvementItems)) {
            refs.addAll(safe(item.getEvidenceRefs()));
        }
        return refs.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> knowledgePathForRepair(String skillUnitId,
                                                String mistakePointId,
                                                String title,
                                                String body,
                                                SubmissionAnalysisResponse analysis) {
        String skillCode = clean(skillUnitId);
        String mistakeCode = clean(mistakePointId);
        LinkedHashSet<String> path = new LinkedHashSet<>();
        Optional<AiStandardMistakePoint> mistake = findMistake(mistakeCode);
        if (mistake.isPresent()) {
            AiStandardMistakePoint item = mistake.get();
            addKnowledgeNodePath(path, item.getPrimaryKnowledgeNodeCode());
            addSkillName(path, firstNonBlank(skillCode, item.getSkillUnitCode()));
            addIfNotBlank(path, item.getName());
            return path.isEmpty() ? knowledgePathFallback(skillCode, mistakeCode) : List.copyOf(path);
        }
        Optional<AiStandardSkillUnit> skill = findSkill(skillCode);
        if (skill.isPresent()) {
            AiStandardSkillUnit item = skill.get();
            addKnowledgeNodePath(path, item.getPrimaryKnowledgeNodeCode());
            addIfNotBlank(path, item.getName());
            return path.isEmpty() ? knowledgePathFallback(skillCode, mistakeCode) : List.copyOf(path);
        }
        return knowledgePathFallback(skillCode, mistakeCode, inferredKnowledgeTitle(title, body, analysis));
    }

    private List<String> repairKnowledgePath(SubmissionAnalysisResponse.BasicLayerAdvice advice,
                                             String title,
                                             String body,
                                             SubmissionAnalysisResponse analysis) {
        String status = clean(advice == null ? null : advice.getKnowledgePathStatus()).toUpperCase();
        if ("PROVISIONAL".equals(status)) {
            return cleanPath(advice.getKnowledgePath());
        }
        if ("UNCLASSIFIED".equals(status)) {
            return List.of();
        }
        return knowledgePathForRepair(
                advice == null ? null : advice.getSkillUnitId(),
                advice == null ? null : advice.getMistakePointId(),
                title,
                body,
                analysis);
    }

    private List<String> knowledgePathForImprovement(String skillUnitId,
                                                     String improvementPointId,
                                                     String title,
                                                     String body,
                                                     SubmissionAnalysisResponse analysis) {
        String skillCode = clean(skillUnitId);
        String improvementCode = clean(improvementPointId);
        LinkedHashSet<String> path = new LinkedHashSet<>();
        Optional<AiStandardImprovementPoint> improvement = findImprovement(improvementCode);
        if (improvement.isPresent()) {
            AiStandardImprovementPoint item = improvement.get();
            addKnowledgeNodePath(path, item.getPrimaryKnowledgeNodeCode());
            addSkillName(path, firstNonBlank(skillCode, item.getSkillUnitCode()));
            addIfNotBlank(path, item.getName());
            return path.isEmpty() ? knowledgePathFallback(skillCode, improvementCode) : List.copyOf(path);
        }
        Optional<AiStandardSkillUnit> skill = findSkill(skillCode);
        if (skill.isPresent()) {
            AiStandardSkillUnit item = skill.get();
            addKnowledgeNodePath(path, item.getPrimaryKnowledgeNodeCode());
            addIfNotBlank(path, item.getName());
            return path.isEmpty() ? knowledgePathFallback(skillCode, improvementCode) : List.copyOf(path);
        }
        return knowledgePathFallback(skillCode, improvementCode, inferredKnowledgeTitle(title, body, analysis));
    }

    private List<String> improvementKnowledgePath(SubmissionAnalysisResponse.ImprovementLayerAdvice advice,
                                                  String title,
                                                  String body,
                                                  SubmissionAnalysisResponse analysis) {
        String status = clean(advice == null ? null : advice.getKnowledgePathStatus()).toUpperCase();
        if ("PROVISIONAL".equals(status)) {
            return cleanPath(advice.getKnowledgePath());
        }
        if ("UNCLASSIFIED".equals(status)) {
            return List.of();
        }
        return knowledgePathForImprovement(
                advice == null ? null : advice.getSkillUnitId(),
                advice == null ? null : advice.getImprovementPointId(),
                title,
                body,
                analysis);
    }

    private String pathStatus(String explicitStatus, String... formalIds) {
        String normalized = clean(explicitStatus).toUpperCase();
        if (Set.of("FORMAL", "PROVISIONAL", "INFERRED", "UNCLASSIFIED").contains(normalized)) {
            return normalized;
        }
        return Arrays.stream(formalIds == null ? new String[0] : formalIds)
                .map(this::clean)
                .anyMatch(value -> !value.isBlank())
                ? "FORMAL"
                : "INFERRED";
    }

    private List<String> cleanPath(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private void addSkillName(LinkedHashSet<String> path, String skillCode) {
        findSkill(skillCode).ifPresent(skill -> addIfNotBlank(path, skill.getName()));
    }

    private void addKnowledgeNodePath(LinkedHashSet<String> path, String knowledgeNodeCode) {
        String code = clean(knowledgeNodeCode);
        if (code.isBlank()) {
            return;
        }
        Optional<InformaticsKnowledgeNode> node = findKnowledgeNode(code);
        if (node.isPresent()) {
            for (String segment : splitKnowledgePath(firstNonBlank(node.get().getPath(), node.get().getName()))) {
                addIfNotBlank(path, segment);
            }
            return;
        }
        addIfNotBlank(path, code);
    }

    private List<String> splitKnowledgePath(String path) {
        String cleaned = clean(path);
        if (cleaned.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split("\\s*(?:/|>|›|→)\\s*"))
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> knowledgePathFallback(String... values) {
        LinkedHashSet<String> path = new LinkedHashSet<>();
        for (String value : values) {
            List<String> segments = splitKnowledgePath(value);
            if (segments.isEmpty()) {
                addIfNotBlank(path, value);
            } else {
                segments.forEach(segment -> addIfNotBlank(path, segment));
            }
        }
        if (path.isEmpty()) {
            path.add("未归类");
        }
        return List.copyOf(path);
    }

    private String inferredKnowledgeTitle(String title, String body, SubmissionAnalysisResponse analysis) {
        String text = joinNonBlank(
                title,
                body,
                analysis == null ? "" : analysis.getSummary(),
                analysis == null ? "" : analysis.getStudentHint()
        );
        Optional<InformaticsKnowledgeNode> node = bestKnowledgeNodeForText(text);
        if (node.isPresent()) {
            return firstNonBlank(node.get().getPath(), node.get().getName());
        }
        return firstNonBlank(title, body, "未归类");
    }

    private Optional<InformaticsKnowledgeNode> bestKnowledgeNodeForText(String text) {
        String haystack = compactText(text);
        if (haystack.isBlank()) {
            return Optional.empty();
        }
        InformaticsKnowledgeNode best = null;
        int bestScore = 0;
        List<InformaticsKnowledgeNode> nodes = knowledgeRepository.findByEnabledTrueOrderByPathAscSortOrderAscCodeAsc();
        for (InformaticsKnowledgeNode node : nodes == null ? List.<InformaticsKnowledgeNode>of() : nodes) {
            int score = knowledgeMatchScore(node, haystack);
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best);
    }

    private int knowledgeMatchScore(InformaticsKnowledgeNode node, String haystack) {
        if (node == null) {
            return 0;
        }
        int score = 0;
        String name = compactText(node.getName());
        if (!name.isBlank() && haystack.contains(name)) {
            score = Math.max(score, 100 + name.length());
        }
        for (String alias : splitLooseList(node.getAliases())) {
            String compact = compactText(alias);
            if (!compact.isBlank() && haystack.contains(compact)) {
                score = Math.max(score, 80 + compact.length());
            }
        }
        return score;
    }

    private List<String> splitLooseList(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split("[,，;；\\n]+"))
                .map(this::clean)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String compactText(String value) {
        return clean(value).replaceAll("\\s+", "").toLowerCase();
    }

    private void addIfNotBlank(LinkedHashSet<String> target, String value) {
        String cleaned = clean(value);
        if (!cleaned.isBlank()) {
            target.add(cleaned);
        }
    }

    private Optional<AiStandardSkillUnit> findSkill(String code) {
        String cleaned = clean(code);
        if (cleaned.isBlank()) {
            return Optional.empty();
        }
        Optional<AiStandardSkillUnit> result = skillUnitRepository.findByCode(cleaned);
        return result == null ? Optional.empty() : result;
    }

    private Optional<AiStandardMistakePoint> findMistake(String code) {
        String cleaned = clean(code);
        if (cleaned.isBlank()) {
            return Optional.empty();
        }
        Optional<AiStandardMistakePoint> result = mistakePointRepository.findByCode(cleaned);
        return result == null ? Optional.empty() : result;
    }

    private Optional<AiStandardImprovementPoint> findImprovement(String code) {
        String cleaned = clean(code);
        if (cleaned.isBlank()) {
            return Optional.empty();
        }
        Optional<AiStandardImprovementPoint> result = improvementPointRepository.findByCode(cleaned);
        return result == null ? Optional.empty() : result;
    }

    private Optional<InformaticsKnowledgeNode> findKnowledgeNode(String code) {
        String cleaned = clean(code);
        if (cleaned.isBlank()) {
            return Optional.empty();
        }
        Optional<InformaticsKnowledgeNode> result = knowledgeRepository.findByCode(cleaned);
        return result == null ? Optional.empty() : result;
    }

    private List<StudentAiFeedbackResponse.EvidenceSnippet> evidenceSnippets(List<String> refs, Submission submission) {
        if (submission == null || submission.getSourceCode() == null || refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<StudentAiFeedbackResponse.EvidenceSnippet> snippets = new ArrayList<>();
        for (String ref : refs) {
            if (snippets.size() >= 3) {
                break;
            }
            StudentAiFeedbackResponse.EvidenceSnippet snippet = evidenceSnippet(ref, submission.getSourceCode());
            if (snippet != null) {
                snippets.add(snippet);
            }
        }
        return snippets;
    }

    private List<String> evidenceRefsForItem(List<String> refs,
                                             String title,
                                             String body,
                                             Submission submission,
                                             SubmissionAnalysisResponse analysis) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(safe(refs));
        if (hasCodeEvidence(merged)) {
            return cleanedRefs(merged);
        }
        for (String ref : analysisCodeEvidenceRefs(analysis)) {
            merged.add(ref);
            if (hasCodeEvidence(merged)) {
                return cleanedRefs(merged);
            }
        }
        String inferred = inferCodeEvidenceRefFromText(title + " " + body, submission);
        if (!inferred.isBlank()) {
            merged.add(inferred);
        }
        return cleanedRefs(merged);
    }

    private List<String> analysisCodeEvidenceRefs(SubmissionAnalysisResponse analysis) {
        if (analysis == null) {
            return List.of();
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (analysis.getCaseUnderstanding() != null) {
            addCodeRef(refs, analysis.getCaseUnderstanding().getPrimaryEvidenceRef());
        }
        addCodeRefs(refs, analysis.getEvidenceRefs());
        if (analysis.getStudentFeedback() != null && analysis.getStudentFeedback().getNextLearningAction() != null) {
            addCodeRefs(refs, analysis.getStudentFeedback().getNextLearningAction().getEvidenceRefs());
        }
        if (analysis.getModelEducationTrace() != null) {
            addCodeRefs(refs, analysis.getModelEducationTrace().getEvidenceRefs());
            addCodeRefs(refs, analysis.getModelEducationTrace().getNextLearningActionEvidenceRefs());
        }
        if (analysis.getLearningInterventionPlan() != null) {
            addCodeRefs(refs, analysis.getLearningInterventionPlan().getEvidenceRefs());
        }
        for (SubmissionAnalysisResponse.LineIssue issue : safe(analysis.getLineIssues())) {
            if (issue != null && issue.getLineNumber() != null && issue.getLineNumber() > 0) {
                refs.add("code:line:" + issue.getLineNumber());
            }
        }
        return List.copyOf(refs);
    }

    private String inferCodeEvidenceRefFromText(String text, Submission submission) {
        if (submission == null || submission.getSourceCode() == null || clean(text).isBlank()) {
            return "";
        }
        List<String> tokens = codeTokens(text);
        if (tokens.isEmpty()) {
            return "";
        }
        String[] lines = submission.getSourceCode().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int bestLine = -1;
        int bestScore = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int score = 0;
            for (String token : tokens) {
                if (line.contains(token)) {
                    score += Math.max(1, token.length());
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestLine = i + 1;
            }
        }
        return bestLine > 0 ? "code:line:" + bestLine : "";
    }

    private List<String> codeTokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = CODE_TOKEN.matcher(clean(text));
        while (matcher.find() && tokens.size() < 12) {
            String token = matcher.group();
            if (!isIgnoredToken(token) && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isIgnoredToken(String token) {
        String normalized = token == null ? "" : token.toLowerCase();
        return Set.of("the", "and", "for", "while", "return", "true", "false", "none",
                "null", "int", "long", "double", "float", "string", "list", "dict").contains(normalized);
    }

    private boolean hasCodeEvidence(Set<String> refs) {
        return refs != null && refs.stream().anyMatch(this::isCodeEvidenceRef);
    }

    private void addCodeRefs(Set<String> target, List<String> refs) {
        for (String ref : safe(refs)) {
            addCodeRef(target, ref);
        }
    }

    private void addCodeRef(Set<String> target, String ref) {
        String cleaned = clean(ref);
        if (isCodeEvidenceRef(cleaned)) {
            target.add(cleaned);
        }
    }

    private boolean isCodeEvidenceRef(String ref) {
        String cleaned = clean(ref);
        return CODE_LINE_REF.matcher(cleaned).matches() || CODE_RANGE_REF.matcher(cleaned).matches();
    }

    private List<String> cleanedRefs(Set<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        return refs.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private StudentAiFeedbackResponse.EvidenceSnippet evidenceSnippet(String evidenceRef, String sourceCode) {
        String ref = clean(evidenceRef);
        Matcher lineMatcher = CODE_LINE_REF.matcher(ref);
        if (lineMatcher.find()) {
            int line = parsePositiveInt(lineMatcher.group(1));
            String code = sourceLine(sourceCode, line);
            return code.isBlank() ? null : StudentAiFeedbackResponse.EvidenceSnippet.builder()
                    .evidenceRef(ref)
                    .lineNumber(line)
                    .lineEnd(line)
                    .code(code)
                    .build();
        }
        Matcher rangeMatcher = CODE_RANGE_REF.matcher(ref);
        if (rangeMatcher.find()) {
            int start = parsePositiveInt(rangeMatcher.group(1));
            int end = Math.min(start + 4, parsePositiveInt(rangeMatcher.group(2)));
            String code = sourceLines(sourceCode, start, end);
            return code.isBlank() ? null : StudentAiFeedbackResponse.EvidenceSnippet.builder()
                    .evidenceRef(ref)
                    .lineNumber(start)
                    .lineEnd(end)
                    .code(code)
                    .build();
        }
        return null;
    }

    private String sourceLine(String sourceCode, int lineNumber) {
        return sourceLines(sourceCode, lineNumber, lineNumber).replaceFirst("^\\d+:\\s*", "");
    }

    private String sourceLines(String sourceCode, int startLine, int endLine) {
        if (sourceCode == null || startLine <= 0 || endLine < startLine) {
            return "";
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (startLine > lines.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int line = startLine; line <= Math.min(endLine, lines.length); line++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line).append(": ").append(lines[line - 1]);
        }
        return clean(builder.toString());
    }

    private int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void addItem(List<StudentAiFeedbackResponse.FeedbackItem> items, StudentAiFeedbackResponse.FeedbackItem item) {
        if (item == null || (clean(item.getTitle()).isBlank() && clean(item.getBody()).isBlank())) {
            return;
        }
        String key = (clean(item.getTitle()) + "|" + clean(item.getBody())).replaceAll("\\s+", "");
        boolean duplicate = items.stream()
                .anyMatch(existing -> (clean(existing.getTitle()) + "|" + clean(existing.getBody()))
                        .replaceAll("\\s+", "")
                        .equals(key));
        if (!duplicate) {
            items.add(item);
        }
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) {
                parts.add(cleaned);
            }
        }
        return String.join(" ", parts);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String cleanId(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String blankToNull(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private StudentAiFeedbackResponse saveFeedback(Submission submission,
                                                   StudentAiFeedbackResponse feedback,
                                                   SubmissionAnalysisResponse analysisResponse) {
        Long submissionId = submission.getId();
        if (feedback.getGeneratedAt() == null) {
            feedback.setGeneratedAt(LocalDateTime.now());
        }
        StudentAiFeedback entity = studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .orElse(StudentAiFeedback.builder()
                        .submissionId(submissionId)
                        .source(SOURCE_MODEL)
                        .build());
        entity.setStatus(feedback.getStatus());
        entity.setSource(hasText(feedback.getSource()) ? feedback.getSource() : SOURCE_AI_UNAVAILABLE);
        entity.setFeedbackJson(serialize(feedback));
        entity.setFailureReason(failureReason(feedback));
        ensureGenerationKey(entity);
        StudentAiFeedback saved = studentAiFeedbackRepository.save(entity);
        StudentAiFeedbackRevision revision = saveRevision(saved, feedback, analysisResponse);
        saved.setLatestRevisionId(revision.getId());
        saved = studentAiFeedbackRepository.save(saved);
        recordGenerationEvent(submission, saved, feedback);
        feedback.setGeneratedAt(saved.getGeneratedAt());
        return feedback;
    }

    private StudentAiFeedbackRevision saveRevision(StudentAiFeedback entity,
                                                    StudentAiFeedbackResponse feedback,
                                                    SubmissionAnalysisResponse analysisResponse) {
        ensureGenerationKey(entity);
        return studentAiFeedbackRevisionRepository
                .findBySubmissionIdAndGenerationKey(entity.getSubmissionId(), entity.getGenerationKey())
                .orElseGet(() -> {
                    int version = studentAiFeedbackRevisionRepository
                            .findTopBySubmissionIdOrderByVersionNumberDesc(entity.getSubmissionId())
                            .map(current -> current.getVersionNumber() + 1)
                            .orElse(1);
                    SubmissionAnalysis analysis = submissionAnalysisRepository
                            .findBySubmissionId(entity.getSubmissionId())
                            .orElse(null);
                    SubmissionAnalysisResponse.AiInvocation invocation = analysisResponse == null
                            ? null
                            : analysisResponse.getAiInvocation();
                    AiDiagnosisRun diagnosisRun = diagnosisWorkflowService == null
                            ? null
                            : diagnosisWorkflowService.latestRun(entity.getSubmissionId()).orElse(null);
                    return studentAiFeedbackRevisionRepository.save(StudentAiFeedbackRevision.builder()
                            .submissionId(entity.getSubmissionId())
                            .feedbackId(entity.getId())
                            .analysisId(analysis == null ? null : analysis.getId())
                            .diagnosisRunId(diagnosisRun == null ? null : diagnosisRun.getId())
                            .diagnosisRunVersion(diagnosisRun == null ? null : diagnosisRun.getVersionNumber())
                            .versionNumber(version)
                            .generationKey(entity.getGenerationKey())
                            .status(statusOrFailed(entity.getStatus()))
                            .source(hasText(entity.getSource()) ? entity.getSource() : SOURCE_AI_UNAVAILABLE)
                            .feedbackJson(entity.getFeedbackJson())
                            .analysisJson(analysisResponse == null || analysis == null ? null : analysis.getReportJson())
                            .evidenceJson(analysisResponse == null || analysis == null ? null : analysis.getEvidenceJson())
                            .failureReason(entity.getFailureReason())
                            .provider(invocation == null ? null : invocation.getProvider())
                            .model(invocation == null ? null : invocation.getModel())
                            .promptVersion(invocation == null ? null : firstNonBlank(
                                    invocation.getAdvicePromptVersion(),
                                    invocation.getPromptVersion()))
                            .schemaVersion(analysisResponse == null ? null : analysisResponse.getAnalysisSchemaVersion())
                            .generatedAt(feedback == null ? LocalDateTime.now() : feedback.getGeneratedAt())
                            .build());
                });
    }

    private void ensureGenerationKey(StudentAiFeedback entity) {
        if (entity.getGenerationKey() == null || entity.getGenerationKey().isBlank()) {
            entity.setGenerationKey(UUID.randomUUID().toString());
        }
    }

    private StudentAiFeedbackResponse failedFeedback(Long submissionId, String reason) {
        return StudentAiFeedbackResponse.builder()
                .submissionId(submissionId)
                .status("FAILED")
                .source(SOURCE_AI_UNAVAILABLE)
                .generatedAt(LocalDateTime.now())
                .repairItems(List.of())
                .improvementItems(List.of())
                .safety(StudentAiFeedbackResponse.Safety.builder()
                        .answerLeakRisk("LOW")
                        .blockedReasons(List.of(reason))
                        .build())
                .evidenceRefs(List.of())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional
    public void recordViewed(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
        StudentAiFeedback feedback = studentAiFeedbackRepository.findBySubmissionId(submissionId)
                .orElse(null);
        studentAiFeedbackEventRepository
                .findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(submissionId, StudentAiFeedbackEvent.EVENT_VIEWED)
                .ifPresentOrElse(
                        ignored -> {
                        },
                        () -> studentAiFeedbackEventRepository.save(eventFor(
                                submission,
                                feedback,
                                feedback == null ? null : deserialize(feedback),
                                StudentAiFeedbackEvent.EVENT_VIEWED
                        ))
                );
    }

    private StudentAiFeedbackLookupResponse toLookup(StudentAiFeedback entity) {
        StudentAiFeedbackResponse feedback = deserialize(entity);
        if (feedback == null) {
            feedback = StudentAiFeedbackResponse.builder()
                    .submissionId(entity.getSubmissionId())
                    .status(statusOrFailed(entity.getStatus()))
                    .source(SOURCE_AI_UNAVAILABLE)
                    .generatedAt(entity.getGeneratedAt())
                    .repairItems(List.of())
                    .improvementItems(List.of())
                    .safety(StudentAiFeedbackResponse.Safety.builder()
                            .answerLeakRisk("LOW")
                            .blockedReasons(entity.getFailureReason() == null || entity.getFailureReason().isBlank()
                                    ? List.of("STRUCTURED_OUTPUT_INVALID")
                                    : List.of(entity.getFailureReason()))
                            .build())
                    .evidenceRefs(List.of())
                    .build();
        }
        if (feedback.getGeneratedAt() == null) {
            feedback.setGeneratedAt(entity.getGeneratedAt());
        }
        hydrateDisplayContract(feedback);
        if (issueLifecycleReadService != null) {
            issueLifecycleReadService.hydrate(entity.getSubmissionId(), feedback);
        }
        hydrateGrowth(entity.getSubmissionId(), feedback);
        return StudentAiFeedbackLookupResponse.builder()
                .status(statusOrFailed(entity.getStatus()))
                .failureReason(entity.getFailureReason())
                .feedback(feedback)
                .diagnosisProgress(diagnosisWorkflowService == null
                        ? null
                        : diagnosisWorkflowService.progressForSubmission(entity.getSubmissionId()))
                .build();
    }

    private void hydrateGrowth(Long submissionId, StudentAiFeedbackResponse feedback) {
        if (growthSummaryService == null || submissionId == null || feedback == null || feedback.getGrowthSummary() != null) {
            return;
        }
        submissionRepository.findById(submissionId)
                .map(growthSummaryService::summarizeSubmission)
                .ifPresent(feedback::setGrowthSummary);
    }

    private void hydrateDisplayContract(StudentAiFeedbackResponse feedback) {
        if (feedback == null) {
            return;
        }
        hydrateKnowledgePaths(feedback.getRepairItems(), true);
        hydrateKnowledgePaths(feedback.getImprovementItems(), false);
    }

    private void hydrateKnowledgePaths(List<StudentAiFeedbackResponse.FeedbackItem> items, boolean repairLayer) {
        for (StudentAiFeedbackResponse.FeedbackItem item : safe(items)) {
            if (item == null) {
                continue;
            }
            if (!clean(item.getKnowledgePathStatus()).isBlank()) {
                continue;
            }
            if (!safe(item.getKnowledgePath()).isEmpty()) {
                item.setKnowledgePathStatus(pathStatus(
                        null,
                        item.getSkillUnitId(),
                        repairLayer ? item.getMistakePointId() : item.getImprovementPointId()));
                continue;
            }
            String title = clean(item.getTitle());
            String body = clean(item.getBody());
            if (title.isBlank() && body.isBlank()) {
                continue;
            }
            boolean improvement = !repairLayer
                    || "IMPROVEMENT".equalsIgnoreCase(clean(item.getKind()))
                    || !clean(item.getImprovementPointId()).isBlank();
            List<String> path = improvement
                    ? knowledgePathForImprovement(item.getSkillUnitId(), item.getImprovementPointId(), title, body, null)
                    : knowledgePathForRepair(item.getSkillUnitId(), item.getMistakePointId(), title, body, null);
            item.setKnowledgePath(path);
            item.setKnowledgePathStatus(pathStatus(
                    null,
                    item.getSkillUnitId(),
                    improvement ? item.getImprovementPointId() : item.getMistakePointId()));
        }
    }

    private StudentAiFeedbackResponse deserialize(StudentAiFeedback entity) {
        if (entity.getFeedbackJson() == null || entity.getFeedbackJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(entity.getFeedbackJson(), StudentAiFeedbackResponse.class);
        } catch (JsonProcessingException exception) {
            log.warn("Student AI feedback JSON parse failed. submissionId={}, error={}",
                    entity.getSubmissionId(),
                    exception.getMessage());
            return null;
        }
    }

    private String serialize(StudentAiFeedbackResponse feedback) {
        try {
            return objectMapper.writeValueAsString(feedback);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学生 AI 反馈序列化失败", exception);
        }
    }

    private StudentAiFeedbackResponse notRequested(Long submissionId) {
        return StudentAiFeedbackResponse.builder()
                .submissionId(submissionId)
                .status("NOT_REQUESTED")
                .source(SOURCE_MODEL)
                .repairItems(List.of())
                .improvementItems(List.of())
                .safety(StudentAiFeedbackResponse.Safety.builder()
                        .answerLeakRisk("LOW")
                        .blockedReasons(List.of())
                        .build())
                .evidenceRefs(List.of())
                .build();
    }

    private void ensureSubmissionExists(Long submissionId) {
        submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
    }

    private String statusOrFailed(String status) {
        return status == null || status.isBlank() ? "FAILED" : status;
    }

    private String failureReason(StudentAiFeedbackResponse feedback) {
        if (feedback == null || "READY".equals(feedback.getStatus())) {
            return null;
        }
        if (feedback.getSafety() == null || feedback.getSafety().getBlockedReasons() == null) {
            return feedback == null ? "UNKNOWN" : feedback.getStatus();
        }
        return String.join(",", feedback.getSafety().getBlockedReasons());
    }

    private void recordGenerationEvent(Submission submission,
                                       StudentAiFeedback entity,
                                       StudentAiFeedbackResponse feedback) {
        if (submission == null || entity == null || feedback == null || feedback.getStatus() == null) {
            return;
        }
        String eventType = "READY".equals(feedback.getStatus())
                ? StudentAiFeedbackEvent.EVENT_READY
                : StudentAiFeedbackEvent.EVENT_FAILED;
        if (!StudentAiFeedbackEvent.EVENT_READY.equals(eventType) && "GENERATING".equals(feedback.getStatus())) {
            return;
        }
        if (studentAiFeedbackEventRepository
                .findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(submission.getId(), eventType)
                .isPresent()) {
            return;
        }
        studentAiFeedbackEventRepository.save(eventFor(submission, entity, feedback, eventType));
    }

    private StudentAiFeedbackEvent eventFor(Submission submission,
                                            StudentAiFeedback entity,
                                            StudentAiFeedbackResponse feedback,
                                            String eventType) {
        return StudentAiFeedbackEvent.builder()
                .submissionId(submission.getId())
                .feedbackRevisionId(entity == null ? null : entity.getLatestRevisionId())
                .studentProfileId(submission.getStudentProfileId())
                .assignmentId(submission.getAssignmentId())
                .problemId(submission.getProblemId())
                .eventType(eventType)
                .feedbackStatus(feedback == null ? entityStatus(entity) : feedback.getStatus())
                .feedbackSource(feedback == null ? entitySource(entity) : feedback.getSource())
                .answerLeakRisk(feedback == null || feedback.getSafety() == null
                        ? null
                        : feedback.getSafety().getAnswerLeakRisk())
                .failureReason(entity == null ? failureReason(feedback) : entity.getFailureReason())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String entityStatus(StudentAiFeedback entity) {
        return entity == null ? null : entity.getStatus();
    }

    private String entitySource(StudentAiFeedback entity) {
        return entity == null ? SOURCE_MODEL : entity.getSource();
    }
}

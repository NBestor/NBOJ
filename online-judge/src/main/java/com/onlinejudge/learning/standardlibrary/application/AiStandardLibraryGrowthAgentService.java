package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryGrowthCandidate;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryGrowthCandidateStatus;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryGrowthGovernanceSummaryResponse;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryGrowthCandidateRepository;
import com.onlinejudge.submission.application.AdviceGenerationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiStandardLibraryGrowthAgentService {

    private static final double MIN_CONFIDENCE = 0.6;

    private final AiStandardLibraryGrowthCandidateRepository candidateRepository;
    private final AiStandardLibraryService standardLibraryService;
    private final InformaticsKnowledgeNodeRepository knowledgeNodeRepository;
    private final AiStandardLibraryGrowthProperties properties;

    @Transactional
    public AiStandardLibraryGrowthCandidate propose(StandardLibraryGrowthProposal proposal) {
        String code = normalizeCode(proposal == null ? "" : proposal.getSuggestedCode());
        AiStandardLibraryLayer layer = proposal == null ? null : proposal.getLayer();
        String evidenceStatus = normalizeEvidenceStatus(
                proposal == null ? null : proposal.getEvidenceStatus(),
                proposal == null ? List.of() : proposal.getEvidenceRefs()
        );
        Optional<AiStandardLibraryGrowthCandidate> duplicateCandidate = layer == null || code.isBlank()
                ? Optional.empty()
                : findDuplicate(layer, parentKnowledgeNodeCode(proposal), code);
        if (duplicateCandidate.isPresent()) {
            return maybeAutoPromote(aggregateDuplicate(duplicateCandidate.get(), proposal));
        }
        List<String> precheckErrors = precheck(proposal, layer, code);
        AiStandardLibraryGrowthCandidateStatus status = precheckErrors.isEmpty()
                ? AiStandardLibraryGrowthCandidateStatus.PROPOSED
                : AiStandardLibraryGrowthCandidateStatus.BLOCKED;

        AiStandardLibraryGrowthCandidate candidate = AiStandardLibraryGrowthCandidate.builder()
                .layer(layer == null ? AiStandardLibraryLayer.MISTAKE_POINT : layer)
                .suggestedCode(code.isBlank() ? "UNSPECIFIED_GROWTH_CANDIDATE" : code)
                .suggestedName(requiredText(proposal == null ? "" : proposal.getSuggestedName(), "未命名标准库候选"))
                .suggestedPath(joinPath(proposal == null ? null : proposal.getSuggestedPath()))
                .parentKnowledgeNodeCode(parentKnowledgeNodeCode(proposal))
                .sourceProblemId(proposal == null ? null : proposal.getSourceProblemId())
                .sourceSubmissionId(proposal == null ? null : proposal.getSourceSubmissionId())
                .observedSubmissionIds(observedSubmissionIds(proposal == null ? null : proposal.getSourceSubmissionId()))
                .similarExistingItems(joinLines(proposal == null ? null : proposal.getSimilarExistingItemCodes()))
                .changeReason(requiredText(proposal == null ? "" : proposal.getChangeReason(), "缺少变更理由"))
                .evidenceRefs(joinLines(proposal == null ? null : proposal.getEvidenceRefs()))
                .evidenceStatus(evidenceStatus)
                .confidence(proposal == null ? null : proposal.getConfidence())
                .status(status)
                .precheckMessage(String.join("；", precheckErrors))
                .occurrenceCount(1)
                .lastObservedAt(LocalDateTime.now())
                .beforeSnapshot(beforeSnapshot(layer, code))
                .diffSummary(diffSummary(proposal, status))
                .rollbackInfo(rollbackInfo(status))
                .build();
        return maybeAutoPromote(candidateRepository.save(candidate));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<AiStandardLibraryGrowthCandidate> proposeFromDiagnosisOutput(AdviceGenerationOutput output) {
        return proposeFromDiagnosisOutput(output, null, null, diagnosisEvidenceRefs(output));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<AiStandardLibraryGrowthCandidate> proposeFromDiagnosisOutput(AdviceGenerationOutput output,
                                                                             Long sourceProblemId,
                                                                             Long sourceSubmissionId,
                                                                             List<String> diagnosisEvidenceRefs) {
        if (output == null || output.getLibraryGrowth() == null || output.getLibraryGrowth().getCandidates() == null) {
            return List.of();
        }
        if (!diagnosisAllowsGrowth(output)) {
            return List.of();
        }
        List<String> fallbackEvidenceRefs = diagnosisEvidenceRefs == null
                ? diagnosisEvidenceRefs(output)
                : diagnosisEvidenceRefs;
        return output.getLibraryGrowth().getCandidates().stream()
                .filter(candidate -> candidate != null)
                .map(candidate -> proposeDiagnosisGrowthCandidate(
                        candidate,
                        sourceProblemId,
                        sourceSubmissionId,
                        fallbackEvidenceRefs
                ))
                .toList();
    }

    private AiStandardLibraryGrowthCandidate proposeDiagnosisGrowthCandidate(
            AdviceGenerationOutput.LibraryGrowthCandidate candidate,
            Long sourceProblemId,
            Long sourceSubmissionId,
            List<String> diagnosisEvidenceRefs) {
        List<String> evidenceRefs = growthEvidenceRefs(candidate, diagnosisEvidenceRefs);
        AiStandardLibraryGrowthCandidate saved = propose(StandardLibraryGrowthProposal.builder()
                .suggestedCode(suggestedCode(candidate))
                .suggestedName(candidate.getName())
                .layer(growthLayer(candidate.getLayer()))
                .suggestedPath(candidate.getSuggestedPath())
                .parentKnowledgeNodeCode(candidate.getParentKnowledgeNodeCode())
                .sourceProblemId(sourceProblemId == null ? candidate.getSourceProblemId() : sourceProblemId)
                .sourceSubmissionId(sourceSubmissionId == null ? candidate.getSourceSubmissionId() : sourceSubmissionId)
                .similarExistingItemCodes(candidate.getSimilarExistingItems())
                .changeReason(growthChangeReason(candidate))
                .evidenceRefs(evidenceRefs)
                .evidenceStatus(growthEvidenceStatus(candidate, evidenceRefs))
                .confidence(candidate.getConfidence())
                .build());
        if ("NEEDS_REVIEW".equalsIgnoreCase(candidate.getStatus())
                && saved.getStatus() == AiStandardLibraryGrowthCandidateStatus.PROPOSED) {
            return markNeedsReview(saved, "AI 诊断提出的待审核标准库候选。");
        }
        return saved;
    }

    private boolean diagnosisAllowsGrowth(AdviceGenerationOutput output) {
        if (output != null && output.getLibraryGrowth() != null
                && output.getLibraryGrowth().getCandidates() != null
                && output.getLibraryGrowth().getCandidates().stream()
                .anyMatch(candidate -> candidate != null
                        && !normalizeText(candidate.getParentKnowledgeNodeCode()).isBlank())) {
            return true;
        }
        AdviceGenerationOutput.DiagnosisDecision decision = output.getDiagnosisDecision();
        if (decision == null || decision.getLibraryFit() == null) {
            return true;
        }
        String fit = decision.getLibraryFit().trim().toUpperCase(Locale.ROOT);
        if ("PARTIAL".equals(fit) || "MISS".equals(fit)) {
            return true;
        }
        if ("HIT".equals(fit)) {
            return false;
        }
        return decision.getAnchors() != null && decision.getAnchors().stream()
                .anyMatch(anchor -> anchor != null && "OUT_OF_LIBRARY".equalsIgnoreCase(anchor.getType()));
    }

    @Transactional(readOnly = true)
    public List<AiStandardLibraryGrowthCandidate> listCandidates() {
        return candidateRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public AiStandardLibraryGrowthCandidate getCandidate(Long id) {
        return findCandidate(id);
    }

    @Transactional(readOnly = true)
    public AiStandardLibraryGrowthGovernanceSummaryResponse governanceSummary() {
        List<AiStandardLibraryGrowthCandidate> candidates = candidateRepository.findAllByOrderByCreatedAtDesc();
        Map<AiStandardLibraryGrowthCandidateStatus, Long> counts = new EnumMap<>(AiStandardLibraryGrowthCandidateStatus.class);
        for (AiStandardLibraryGrowthCandidateStatus status : AiStandardLibraryGrowthCandidateStatus.values()) {
            counts.put(status, candidates.stream()
                    .filter(candidate -> candidate.getStatus() == status)
                    .count());
        }
        List<AiStandardLibraryGrowthGovernanceSummaryResponse.PathStat> pathStats = pathStats(candidates);
        return AiStandardLibraryGrowthGovernanceSummaryResponse.builder()
                .totalCount(candidates.size())
                .reviewPendingCount((int) candidates.stream().filter(this::pendingReview).count())
                .proposedCount(count(counts, AiStandardLibraryGrowthCandidateStatus.PROPOSED))
                .needsReviewCount(count(counts, AiStandardLibraryGrowthCandidateStatus.NEEDS_REVIEW))
                .blockedCount(count(counts, AiStandardLibraryGrowthCandidateStatus.BLOCKED))
                .mergedSimilarCount(count(counts, AiStandardLibraryGrowthCandidateStatus.MERGED_SIMILAR))
                .teacherApprovedCount(count(counts, AiStandardLibraryGrowthCandidateStatus.TEACHER_APPROVED))
                .mergedCount(count(counts, AiStandardLibraryGrowthCandidateStatus.MERGED))
                .rejectedCount(count(counts, AiStandardLibraryGrowthCandidateStatus.REJECTED))
                .ignoredCount(count(counts, AiStandardLibraryGrowthCandidateStatus.IGNORED))
                .duplicateAggregateCount((int) candidates.stream()
                        .filter(candidate -> candidate.getStatus() == AiStandardLibraryGrowthCandidateStatus.MERGED_SIMILAR
                                || candidate.getOccurrenceCount() != null && candidate.getOccurrenceCount() > 1)
                        .count())
                .statusStats(statusStats(counts))
                .highFrequencyPaths(pathStats.stream().limit(6).toList())
                .weakPaths(pathStats.stream()
                        .filter(stat -> stat.getPendingCount() > 0 && (stat.getCandidateCount() > 1 || stat.getOccurrenceCount() > 1))
                        .limit(6)
                        .toList())
                .build();
    }

    @Transactional
    public AiStandardLibraryGrowthCandidate updateCandidate(Long id, StandardLibraryGrowthProposal proposal) {
        AiStandardLibraryGrowthCandidate candidate = findCandidate(id);
        String code = normalizeCode(proposal == null ? candidate.getSuggestedCode() : proposal.getSuggestedCode());
        AiStandardLibraryLayer layer = proposal == null ? candidate.getLayer() : proposal.getLayer();
        List<String> precheckErrors = precheckForCandidate(proposal, layer, code, id);
        candidate.setLayer(layer == null ? candidate.getLayer() : layer);
        candidate.setSuggestedCode(code.isBlank() ? candidate.getSuggestedCode() : code);
        candidate.setSuggestedName(requiredText(proposal == null ? candidate.getSuggestedName() : proposal.getSuggestedName(), candidate.getSuggestedName()));
        candidate.setSuggestedPath(joinPath(proposal == null ? splitPath(candidate.getSuggestedPath()) : proposal.getSuggestedPath()));
        String resolvedParentCode = proposal == null ? "" : parentKnowledgeNodeCode(proposal);
        candidate.setParentKnowledgeNodeCode(resolvedParentCode.isBlank()
                ? candidate.getParentKnowledgeNodeCode()
                : resolvedParentCode);
        candidate.setSourceProblemId(proposal == null ? candidate.getSourceProblemId() : proposal.getSourceProblemId());
        candidate.setSourceSubmissionId(proposal == null ? candidate.getSourceSubmissionId() : proposal.getSourceSubmissionId());
        if (proposal != null && proposal.getSourceSubmissionId() != null && proposal.getSourceSubmissionId() > 0) {
            LinkedHashSet<Long> observed = new LinkedHashSet<>(safeObservedSubmissionIds(candidate));
            observed.add(proposal.getSourceSubmissionId());
            candidate.setObservedSubmissionIds(List.copyOf(observed));
            candidate.setOccurrenceCount(observed.size());
        }
        candidate.setSimilarExistingItems(joinLines(proposal == null ? lines(candidate.getSimilarExistingItems()) : proposal.getSimilarExistingItemCodes()));
        candidate.setChangeReason(requiredText(proposal == null ? candidate.getChangeReason() : proposal.getChangeReason(), candidate.getChangeReason()));
        candidate.setEvidenceRefs(joinLines(proposal == null ? lines(candidate.getEvidenceRefs()) : proposal.getEvidenceRefs()));
        candidate.setEvidenceStatus(normalizeEvidenceStatus(
                proposal == null ? candidate.getEvidenceStatus() : proposal.getEvidenceStatus(),
                lines(candidate.getEvidenceRefs())
        ));
        candidate.setConfidence(proposal == null ? candidate.getConfidence() : proposal.getConfidence());
        candidate.setStatus(precheckErrors.isEmpty()
                ? AiStandardLibraryGrowthCandidateStatus.PROPOSED
                : AiStandardLibraryGrowthCandidateStatus.BLOCKED);
        candidate.setPrecheckMessage(String.join("；", precheckErrors));
        candidate.setBeforeSnapshot(beforeSnapshot(candidate.getLayer(), candidate.getSuggestedCode()));
        candidate.setDiffSummary(diffSummary(candidate, candidate.getStatus()));
        candidate.setRollbackInfo(rollbackInfo(candidate.getStatus()));
        return candidateRepository.save(candidate);
    }

    @Transactional
    public AiStandardLibraryGrowthCandidate ignore(Long id, String teacherNote) {
        AiStandardLibraryGrowthCandidate candidate = findCandidate(id);
        candidate.setStatus(AiStandardLibraryGrowthCandidateStatus.IGNORED);
        candidate.setTeacherNote(requiredText(teacherNote, "教师已忽略该候选。"));
        candidate.setPrecheckMessage(requiredText(teacherNote, "教师已忽略该候选。"));
        candidate.setRollbackInfo("未写入正式标准库；恢复时将状态改回 PROPOSED 后重新处理。");
        candidate.setDiffSummary(candidate.getDiffSummary() + "\nignoredReason=" + candidate.getPrecheckMessage());
        return candidateRepository.save(candidate);
    }

    @Transactional
    public AiStandardLibraryGrowthCandidate reject(Long id, String teacherNote) {
        AiStandardLibraryGrowthCandidate candidate = findCandidate(id);
        candidate.setStatus(AiStandardLibraryGrowthCandidateStatus.REJECTED);
        candidate.setTeacherNote(requiredText(teacherNote, "教师拒绝该候选。"));
        candidate.setPrecheckMessage(candidate.getTeacherNote());
        candidate.setRollbackInfo("未写入正式标准库；同类低质候选会优先聚合到本记录，避免反复推荐。");
        candidate.setDiffSummary(candidate.getDiffSummary() + "\nrejectedReason=" + candidate.getPrecheckMessage());
        return candidateRepository.save(candidate);
    }

    @Transactional
    public AiStandardLibraryGrowthCandidate approve(Long id, StandardLibraryGrowthProposal overrideProposal) {
        AiStandardLibraryGrowthCandidate candidate = mergeToFormalLibrary(id, overrideProposal);
        if (candidate.getStatus() == AiStandardLibraryGrowthCandidateStatus.MERGED) {
            candidate.setStatus(AiStandardLibraryGrowthCandidateStatus.TEACHER_APPROVED);
            candidate.setPrecheckMessage(candidate.getPrecheckMessage() + "；教师已批准。");
            candidate.setRollbackInfo(candidate.getRollbackInfo() + " 本状态表示由教师审核批准入库。");
            return candidateRepository.save(candidate);
        }
        return candidate;
    }

    @Transactional
    public AiStandardLibraryGrowthCandidate autoMergeToFormalLibrary(Long id, StandardLibraryGrowthProposal overrideProposal) {
        AiStandardLibraryGrowthCandidate candidate = findCandidate(id);
        if (!properties.isEnabled()) {
            return markNeedsReview(candidate, "标准库成长 Agent 已关闭。");
        }
        if (!properties.isAutoMergeEnabled()) {
            return markNeedsReview(candidate, "自动入库未开启；候选保留给教师确认。");
        }
        if (candidate.getConfidence() == null || candidate.getConfidence() < properties.getAutoMergeMinConfidence()) {
            return markNeedsReview(candidate, "自动入库置信度不足。");
        }
        if (!"SUPPORTED".equalsIgnoreCase(candidate.getEvidenceStatus())) {
            return markNeedsReview(candidate, "自动入库缺少直接代码证据。");
        }
        if (candidate.getOccurrenceCount() == null
                || candidate.getOccurrenceCount() < properties.getAutoMergeMinOccurrences()) {
            return markNeedsReview(candidate, "自动入库出现次数不足。");
        }
        if (independentSubmissionCount(candidate) < properties.getAutoMergeMinOccurrences()) {
            return markNeedsReview(candidate, "自动入库缺少足够的独立提交来源。");
        }
        if (!hasValidKnowledgePointParent(candidate.getParentKnowledgeNodeCode())) {
            return markNeedsReview(candidate, "自动入库缺少有效的正式知识点父节点。");
        }
        return mergeToFormalLibrary(id, overrideProposal);
    }

    @Transactional
    public AiStandardLibraryGrowthCandidate mergeToFormalLibrary(Long id, StandardLibraryGrowthProposal overrideProposal) {
        AiStandardLibraryGrowthCandidate candidate = findCandidate(id);
        if (candidate.getStatus() == AiStandardLibraryGrowthCandidateStatus.MERGED
                || candidate.getStatus() == AiStandardLibraryGrowthCandidateStatus.TEACHER_APPROVED) {
            return candidate;
        }
        StandardLibraryGrowthProposal proposal = overrideProposal == null ? proposalFrom(candidate) : overrideProposal;
        List<String> precheckErrors = precheckForCandidate(
                proposal,
                proposal.getLayer(),
                normalizeCode(proposal.getSuggestedCode()),
                candidate.getId()
        );
        if (!precheckErrors.isEmpty()) {
            candidate.setStatus(AiStandardLibraryGrowthCandidateStatus.NEEDS_REVIEW);
            candidate.setPrecheckMessage(String.join("；", precheckErrors));
            candidate.setRollbackInfo("未写入正式标准库；请编辑候选或手动合并相似条目。");
            return candidateRepository.save(candidate);
        }

        candidate.setBeforeSnapshot(beforeSnapshot(proposal.getLayer(), normalizeCode(proposal.getSuggestedCode())));
        var created = standardLibraryService.create(toItemRequest(proposal));
        candidate.setStatus(AiStandardLibraryGrowthCandidateStatus.MERGED);
        candidate.setPrecheckMessage("已写入正式标准库: " + created.getLayer() + "/" + created.getCode());
        candidate.setDiffSummary(diffSummary(candidate, AiStandardLibraryGrowthCandidateStatus.MERGED)
                + "\nformalItemId=" + created.getId());
        candidate.setRollbackInfo("已写入正式标准库；如需回滚，请在 AI 标准库中停用或删除条目 "
                + created.getLayer() + "/" + created.getCode()
                + "，并保留本候选审计记录。");
        return candidateRepository.save(candidate);
    }

    private List<String> precheck(StandardLibraryGrowthProposal proposal, AiStandardLibraryLayer layer, String code) {
        return precheckForCandidate(proposal, layer, code, null);
    }

    private AiStandardLibraryGrowthCandidate aggregateDuplicate(AiStandardLibraryGrowthCandidate existing,
                                                                StandardLibraryGrowthProposal proposal) {
        LinkedHashSet<Long> observedSubmissionIds = new LinkedHashSet<>(safeObservedSubmissionIds(existing));
        Long sourceSubmissionId = proposal == null ? null : proposal.getSourceSubmissionId();
        boolean independentSourceAdded = sourceSubmissionId != null && sourceSubmissionId > 0
                && observedSubmissionIds.add(sourceSubmissionId);
        existing.setObservedSubmissionIds(List.copyOf(observedSubmissionIds));
        if (!observedSubmissionIds.isEmpty()) {
            existing.setOccurrenceCount(observedSubmissionIds.size());
        } else if (existing.getOccurrenceCount() == null || existing.getOccurrenceCount() < 1) {
            existing.setOccurrenceCount(1);
        }
        existing.setLastObservedAt(LocalDateTime.now());
        if (proposal != null) {
            if (proposal.getSourceProblemId() != null) {
                existing.setSourceProblemId(proposal.getSourceProblemId());
            }
            if (proposal.getSourceSubmissionId() != null) {
                existing.setSourceSubmissionId(proposal.getSourceSubmissionId());
            }
            if (!normalizeText(proposal.getParentKnowledgeNodeCode()).isBlank()) {
                existing.setParentKnowledgeNodeCode(normalizeText(proposal.getParentKnowledgeNodeCode()));
            }
        }
        existing.setEvidenceRefs(joinLines(mergeLines(lines(existing.getEvidenceRefs()),
                proposal == null ? List.of() : proposal.getEvidenceRefs())));
        existing.setEvidenceStatus(aggregateEvidenceStatus(existing.getEvidenceStatus(),
                proposal == null ? null : proposal.getEvidenceStatus(),
                lines(existing.getEvidenceRefs())));
        existing.setSimilarExistingItems(joinLines(mergeLines(lines(existing.getSimilarExistingItems()),
                proposal == null ? List.of() : proposal.getSimilarExistingItemCodes())));
        String reason = requiredText(proposal == null ? "" : proposal.getChangeReason(), "");
        String existingReason = requiredText(existing.getChangeReason(), "");
        if (!reason.isBlank() && !existingReason.contains(reason)) {
            existing.setChangeReason(existingReason.isBlank() ? reason : existingReason + "\n再次出现：" + reason);
        }
        if (independentSourceAdded && List.of(
                AiStandardLibraryGrowthCandidateStatus.PROPOSED,
                AiStandardLibraryGrowthCandidateStatus.NEEDS_REVIEW,
                AiStandardLibraryGrowthCandidateStatus.BLOCKED,
                AiStandardLibraryGrowthCandidateStatus.MERGED_SIMILAR
        ).contains(existing.getStatus())) {
            existing.setStatus(AiStandardLibraryGrowthCandidateStatus.MERGED_SIMILAR);
        }
        existing.setPrecheckMessage(independentSourceAdded
                ? "相似库外发现已按独立提交聚合，独立出现次数=" + existing.getOccurrenceCount()
                : "同一提交的重复生成仅合并证据，独立出现次数=" + existing.getOccurrenceCount());
        existing.setDiffSummary(diffSummary(existing, existing.getStatus())
                + "\noccurrenceCount=" + existing.getOccurrenceCount()
                + "\nobservedSubmissionIds=" + existing.getObservedSubmissionIds());
        existing.setRollbackInfo("未自动写入正式标准库；该记录用于教师按频次审核。");
        return candidateRepository.save(existing);
    }

    private List<AiStandardLibraryGrowthGovernanceSummaryResponse.StatusStat> statusStats(
            Map<AiStandardLibraryGrowthCandidateStatus, Long> counts) {
        return List.of(AiStandardLibraryGrowthCandidateStatus.values()).stream()
                .map(status -> AiStandardLibraryGrowthGovernanceSummaryResponse.StatusStat.builder()
                        .status(status.name())
                        .count(count(counts, status))
                        .build())
                .toList();
    }

    private List<AiStandardLibraryGrowthGovernanceSummaryResponse.PathStat> pathStats(
            List<AiStandardLibraryGrowthCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate.getSuggestedPath() != null && !candidate.getSuggestedPath().isBlank())
                .collect(Collectors.groupingBy(AiStandardLibraryGrowthCandidate::getSuggestedPath))
                .entrySet()
                .stream()
                .map(entry -> pathStat(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingInt(AiStandardLibraryGrowthGovernanceSummaryResponse.PathStat::getOccurrenceCount)
                        .thenComparingInt(AiStandardLibraryGrowthGovernanceSummaryResponse.PathStat::getCandidateCount)
                        .reversed())
                .toList();
    }

    private AiStandardLibraryGrowthGovernanceSummaryResponse.PathStat pathStat(
            String path,
            List<AiStandardLibraryGrowthCandidate> candidates) {
        int occurrenceCount = candidates.stream()
                .map(AiStandardLibraryGrowthCandidate::getOccurrenceCount)
                .mapToInt(value -> value == null || value < 1 ? 1 : value)
                .sum();
        int pendingCount = (int) candidates.stream().filter(this::pendingReview).count();
        return AiStandardLibraryGrowthGovernanceSummaryResponse.PathStat.builder()
                .path(splitPath(path))
                .candidateCount(candidates.size())
                .pendingCount(pendingCount)
                .occurrenceCount(occurrenceCount)
                .recommendedAction(pathRecommendedAction(candidates.size(), pendingCount, occurrenceCount))
                .build();
    }

    private String pathRecommendedAction(int candidateCount, int pendingCount, int occurrenceCount) {
        if (pendingCount > 0 && (candidateCount > 1 || occurrenceCount > 1)) {
            return "优先审核：同一路径多次出现库外候选。";
        }
        if (pendingCount > 0) {
            return "等待教师确认后再入库。";
        }
        return "已处理，保留审计记录。";
    }

    private boolean pendingReview(AiStandardLibraryGrowthCandidate candidate) {
        return candidate != null && List.of(
                AiStandardLibraryGrowthCandidateStatus.PROPOSED,
                AiStandardLibraryGrowthCandidateStatus.NEEDS_REVIEW,
                AiStandardLibraryGrowthCandidateStatus.BLOCKED,
                AiStandardLibraryGrowthCandidateStatus.MERGED_SIMILAR
        ).contains(candidate.getStatus());
    }

    private int count(Map<AiStandardLibraryGrowthCandidateStatus, Long> counts,
                      AiStandardLibraryGrowthCandidateStatus status) {
        return Math.toIntExact(counts.getOrDefault(status, 0L));
    }

    private List<String> precheckForCandidate(StandardLibraryGrowthProposal proposal,
                                              AiStandardLibraryLayer layer,
                                              String code,
                                              Long currentCandidateId) {
        List<String> errors = new ArrayList<>();
        if (layer == null) {
            errors.add("层级缺失");
        }
        if (code.isBlank()) {
            errors.add("建议 ID 缺失");
        }
        if (proposal == null || proposal.getSourceProblemId() == null || proposal.getSourceSubmissionId() == null) {
            errors.add("来源题目或提交缺失");
        }
        if (proposal == null || proposal.getSuggestedPath() == null || proposal.getSuggestedPath().isEmpty()) {
            errors.add("归属路径缺失");
        }
        if (proposal == null || proposal.getConfidence() == null || proposal.getConfidence() < MIN_CONFIDENCE) {
            errors.add("置信度不足");
        }
        if (layer != null && !code.isBlank() && standardLibraryService.formalItemExists(layer, code)) {
            errors.add("正式标准库已存在: " + layer + "/" + code);
        }
        Optional<AiStandardLibraryGrowthCandidate> duplicateCandidate = layer == null || code.isBlank()
                ? Optional.empty()
                : findDuplicate(layer, parentKnowledgeNodeCode(proposal), code);
        if (duplicateCandidate.isPresent()
                && (currentCandidateId == null || !duplicateCandidate.get().getId().equals(currentCandidateId))) {
            errors.add("候选池已存在: " + layer + "/" + code);
        }
        return errors;
    }

    private String beforeSnapshot(AiStandardLibraryLayer layer, String code) {
        if (layer == null || code == null || code.isBlank()) {
            return "NO_EXISTING_ITEM";
        }
        Optional<AiStandardLibraryItem> item = standardLibraryService.findFormalItemAsLegacy(layer, code);
        if (item.isEmpty()) {
            return "NO_EXISTING_ITEM";
        }
        AiStandardLibraryItem existing = item.get();
        return "layer=" + existing.getLayer()
                + "\ncode=" + existing.getCode()
                + "\nname=" + existing.getName()
                + "\ncategory=" + existing.getCategory()
                + "\ndescription=" + existing.getDescription();
    }

    private String diffSummary(StandardLibraryGrowthProposal proposal, AiStandardLibraryGrowthCandidateStatus status) {
        return "status=" + status
                + "\nname=" + requiredText(proposal == null ? "" : proposal.getSuggestedName(), "未命名标准库候选")
                + "\nreason=" + requiredText(proposal == null ? "" : proposal.getChangeReason(), "缺少变更理由");
    }

    private String diffSummary(AiStandardLibraryGrowthCandidate candidate, AiStandardLibraryGrowthCandidateStatus status) {
        return "status=" + status
                + "\nname=" + requiredText(candidate == null ? "" : candidate.getSuggestedName(), "未命名标准库候选")
                + "\ncode=" + requiredText(candidate == null ? "" : candidate.getSuggestedCode(), "UNKNOWN")
                + "\nreason=" + requiredText(candidate == null ? "" : candidate.getChangeReason(), "缺少变更理由");
    }

    private String rollbackInfo(AiStandardLibraryGrowthCandidateStatus status) {
        if (status == AiStandardLibraryGrowthCandidateStatus.PROPOSED) {
            return "候选尚未写入正式标准库；删除或忽略候选即可回滚。";
        }
        return "未写入正式标准库；修正预检问题后重新生成候选。";
    }

    private AiStandardLibraryGrowthCandidate markNeedsReview(AiStandardLibraryGrowthCandidate candidate, String message) {
        candidate.setStatus(AiStandardLibraryGrowthCandidateStatus.NEEDS_REVIEW);
        candidate.setPrecheckMessage(message);
        candidate.setRollbackInfo("未写入正式标准库；候选保留在待处理列表中。");
        return candidateRepository.save(candidate);
    }

    private AiStandardLibraryGrowthCandidate findCandidate(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("标准库成长候选不存在: " + id));
    }

    private StandardLibraryGrowthProposal proposalFrom(AiStandardLibraryGrowthCandidate candidate) {
        return StandardLibraryGrowthProposal.builder()
                .suggestedCode(candidate.getSuggestedCode())
                .suggestedName(candidate.getSuggestedName())
                .layer(candidate.getLayer())
                .suggestedPath(splitPath(candidate.getSuggestedPath()))
                .parentKnowledgeNodeCode(candidate.getParentKnowledgeNodeCode())
                .sourceProblemId(candidate.getSourceProblemId())
                .sourceSubmissionId(candidate.getSourceSubmissionId())
                .similarExistingItemCodes(lines(candidate.getSimilarExistingItems()))
                .changeReason(candidate.getChangeReason())
                .evidenceRefs(lines(candidate.getEvidenceRefs()))
                .evidenceStatus(candidate.getEvidenceStatus())
                .confidence(candidate.getConfidence())
                .build();
    }

    private com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemRequest toItemRequest(StandardLibraryGrowthProposal proposal) {
        String code = normalizeCode(proposal.getSuggestedCode());
        String path = joinPath(proposal.getSuggestedPath());
        List<String> evidence = proposal.getEvidenceRefs() == null ? List.of() : proposal.getEvidenceRefs();
        GrowthDraft draft = growthDraft(proposal.getChangeReason());
        List<String> knowledgeCodes = knowledgeNodeCodes(
                proposal.getParentKnowledgeNodeCode(),
                proposal.getSuggestedPath(),
                proposal.getSimilarExistingItemCodes());
        String primaryKnowledgeCode = knowledgeCodes.stream().findFirst().orElse("");
        String description = requiredText(draft.errorSymptom(),
                requiredText(proposal.getChangeReason(), "由 AI 诊断发现并经门禁写入的细颗粒标准库条目。"));
        String studentExplanation = requiredText(draft.studentExplanation(),
                "先把当前代码表现、判题证据和标准库路径对齐，再判断是否属于这个易错点。");
        String commonMisconception = requiredText(draft.errorSymptom(),
                requiredText(proposal.getChangeReason(), "学生可能没有把代码行为、判题结果和知识点边界对齐。"));
        com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemRequest request =
                new com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemRequest();
        request.setLayer(proposal.getLayer().name());
        request.setCode(code);
        request.setCategory(categoryFromPath(path));
        request.setName(requiredText(proposal.getSuggestedName(), code));
        request.setDescription(description);
        request.setStudentExplanation(studentExplanation);
        request.setTeacherExplanation(proposal.getChangeReason());
        request.setSkillUnitCode(skillUnitFromSimilarItems(proposal.getSimilarExistingItemCodes()));
        request.setMistakeType("DEBUGGING");
        request.setCommonMisconception(commonMisconception);
        request.setEvidenceSignals(evidence);
        request.setCommonCodePatterns(draft.typicalCodePattern().isBlank()
                ? List.of()
                : List.of(draft.typicalCodePattern()));
        request.setJudgeSignals(evidence.stream().filter(ref -> ref.startsWith("judge:")).toList());
        request.setRequiredEvidence(evidence);
        request.setApplicableLanguages(List.of("PYTHON", "CPP17"));
        request.setRelatedItems(proposal.getSimilarExistingItemCodes());
        request.setPrimaryKnowledgeNodeCode(primaryKnowledgeCode);
        request.setKnowledgeNodeCodes(knowledgeCodes);
        request.setRelatedKnowledgeNodeCodes(knowledgeCodes.stream().skip(1).toList());
        request.setPrerequisiteKnowledgeCodes(List.of());
        request.setSeverity("MEDIUM");
        request.setEnabled(true);
        request.setLibraryVersion("standard-library-growth-v2");
        return request;
    }

    private String categoryFromPath(String path) {
        if (path == null || path.isBlank()) {
            return "AI 扩库";
        }
        String first = path.split("\\.")[0];
        return first.isBlank() ? "AI 扩库" : first;
    }

    private String skillUnitFromSimilarItems(List<String> similarItems) {
        if (similarItems == null || similarItems.isEmpty()) {
            return "SK_AI_GROWTH_REVIEW";
        }
        Optional<String> explicitSkill = similarItems.stream()
                .map(this::normalizeCode)
                .filter(value -> value.startsWith("SK_"))
                .findFirst()
                .filter(value -> !value.isBlank());
        if (explicitSkill.isPresent()) {
            return explicitSkill.get();
        }
        Optional<AiStandardLibraryItem> similar = findSimilarFormalItem(similarItems);
        if (similar.isPresent()) {
            AiStandardLibraryItem item = similar.get();
            if (item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT) {
                return item.getCode();
            }
            String skill = normalizeText(item.getSkillUnitCode());
            if (!skill.isBlank()) {
                return skill;
            }
        }
        return "SK_AI_GROWTH_REVIEW";
    }

    private List<String> knowledgeNodeCodes(String parentKnowledgeNodeCode,
                                            List<String> path,
                                            List<String> similarItems) {
        String parentCode = normalizeText(parentKnowledgeNodeCode);
        if (hasValidKnowledgePointParent(parentCode)) {
            return List.of(parentCode);
        }
        Optional<AiStandardLibraryItem> similar = findSimilarFormalItem(similarItems);
        if (similar.isPresent()) {
            List<String> inherited = lines(similar.get().getKnowledgeNodeCodes());
            if (!inherited.isEmpty()) {
                return inherited;
            }
            String primary = normalizeText(similar.get().getPrimaryKnowledgeNodeCode());
            if (!primary.isBlank()) {
                return List.of(primary);
            }
        }
        List<String> normalizedPath = path == null ? List.of() : path.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_"))
                .toList();
        if (normalizedPath.isEmpty()) {
            return List.of("AI_GROWTH_REVIEW");
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (normalizedPath.size() >= 2) {
            for (int end = normalizedPath.size(); end >= 2; end--) {
                codes.add(String.join(".", normalizedPath.subList(0, end)));
            }
        }
        codes.add(normalizedPath.get(0));
        return codes.stream().toList();
    }

    private Optional<AiStandardLibraryItem> findSimilarFormalItem(List<String> similarItems) {
        if (similarItems == null || similarItems.isEmpty()) {
            return Optional.empty();
        }
        for (String value : similarItems) {
            String code = normalizeCode(value);
            if (code.isBlank()) {
                continue;
            }
            for (AiStandardLibraryLayer layer : likelyLayers(code)) {
                Optional<AiStandardLibraryItem> item = standardLibraryService.findFormalItemAsLegacy(layer, code);
                if (item.isPresent()) {
                    return item;
                }
            }
        }
        return Optional.empty();
    }

    private List<AiStandardLibraryLayer> likelyLayers(String code) {
        if (code.startsWith("SK_")) {
            return List.of(AiStandardLibraryLayer.SKILL_UNIT);
        }
        if (code.startsWith("MP_")) {
            return List.of(AiStandardLibraryLayer.MISTAKE_POINT, AiStandardLibraryLayer.BASIC_CAUSE);
        }
        if (code.startsWith("IP_")) {
            return List.of(AiStandardLibraryLayer.IMPROVEMENT_POINT);
        }
        return List.of(AiStandardLibraryLayer.MISTAKE_POINT, AiStandardLibraryLayer.SKILL_UNIT,
                AiStandardLibraryLayer.IMPROVEMENT_POINT, AiStandardLibraryLayer.BASIC_CAUSE);
    }

    private List<String> splitPath(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\.")).stream()
                .map(this::normalizeText)
                .filter(path -> !path.isBlank())
                .toList();
    }

    private List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(this::normalizeText)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private List<String> mergeLines(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            second.stream().map(this::normalizeText).filter(value -> !value.isBlank()).forEach(merged::add);
        }
        return merged.stream().toList();
    }

    private String joinPath(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(".", values.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String joinLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private List<String> diagnosisEvidenceRefs(AdviceGenerationOutput output) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        AdviceGenerationOutput.DiagnosisDecision decision = output == null ? null : output.getDiagnosisDecision();
        if (decision != null) {
            if (decision.getAnchors() != null) {
                decision.getAnchors().stream()
                        .filter(anchor -> anchor != null && anchor.getEvidenceRefs() != null)
                        .flatMap(anchor -> anchor.getEvidenceRefs().stream())
                        .map(this::normalizeText)
                        .filter(ref -> !ref.isBlank())
                        .forEach(refs::add);
            }
            if (decision.getOutOfLibraryFindings() != null) {
                decision.getOutOfLibraryFindings().stream()
                        .filter(finding -> finding != null && finding.getEvidenceRefs() != null)
                        .flatMap(finding -> finding.getEvidenceRefs().stream())
                        .map(this::normalizeText)
                        .filter(ref -> !ref.isBlank())
                        .forEach(refs::add);
            }
        }
        if (output != null && output.getDiagnosisCandidates() != null) {
            output.getDiagnosisCandidates().stream()
                    .filter(candidate -> candidate != null && candidate.getEvidenceRefs() != null)
                    .flatMap(candidate -> candidate.getEvidenceRefs().stream())
                    .map(this::normalizeText)
                    .filter(ref -> !ref.isBlank())
                    .forEach(refs::add);
        }
        return refs.stream().toList();
    }

    private List<String> growthEvidenceRefs(AdviceGenerationOutput.LibraryGrowthCandidate candidate,
                                            List<String> diagnosisEvidenceRefs) {
        if (candidate == null) {
            return List.of();
        }
        if ("UNSUPPORTED".equalsIgnoreCase(normalizeText(candidate.getEvidenceStatus()))) {
            return List.of();
        }
        if (candidate.getEvidenceRefs() != null && !candidate.getEvidenceRefs().isEmpty()) {
            return mergeLines(candidate.getEvidenceRefs(), List.of());
        }
        return mergeLines(diagnosisEvidenceRefs, List.of());
    }

    private String growthEvidenceStatus(AdviceGenerationOutput.LibraryGrowthCandidate candidate, List<String> evidenceRefs) {
        String modelStatus = candidate == null ? "" : normalizeText(candidate.getEvidenceStatus()).toUpperCase(Locale.ROOT);
        if ("UNSUPPORTED".equals(modelStatus)) {
            return "UNSUPPORTED";
        }
        if (evidenceRefs == null || evidenceRefs.isEmpty()) {
            return "NO_DIRECT_CODE_EVIDENCE";
        }
        return "SUPPORTED";
    }

    private String normalizeEvidenceStatus(String status, List<String> evidenceRefs) {
        String normalized = normalizeText(status).toUpperCase(Locale.ROOT);
        if (List.of("SUPPORTED", "NO_DIRECT_CODE_EVIDENCE", "UNSUPPORTED").contains(normalized)) {
            if ("SUPPORTED".equals(normalized) && (evidenceRefs == null || evidenceRefs.isEmpty())) {
                return "NO_DIRECT_CODE_EVIDENCE";
            }
            return normalized;
        }
        return evidenceRefs == null || evidenceRefs.isEmpty() ? "NO_DIRECT_CODE_EVIDENCE" : "SUPPORTED";
    }

    private String aggregateEvidenceStatus(String existingStatus, String proposalStatus, List<String> mergedEvidenceRefs) {
        String normalizedProposal = normalizeEvidenceStatus(proposalStatus, mergedEvidenceRefs);
        String normalizedExisting = normalizeEvidenceStatus(existingStatus, mergedEvidenceRefs);
        if ("SUPPORTED".equals(normalizedProposal) || "SUPPORTED".equals(normalizedExisting)) {
            return "SUPPORTED";
        }
        if ("UNSUPPORTED".equals(normalizedProposal) || "UNSUPPORTED".equals(normalizedExisting)) {
            return "UNSUPPORTED";
        }
        return "NO_DIRECT_CODE_EVIDENCE";
    }

    private String normalizeCode(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private String suggestedCode(AdviceGenerationOutput.LibraryGrowthCandidate candidate) {
        AiStandardLibraryLayer layer = growthLayer(candidate.getLayer());
        String prefix = layer == AiStandardLibraryLayer.IMPROVEMENT_POINT ? "IP_" : "MP_";
        String readablePath = candidate.getSuggestedPath() == null
                ? ""
                : String.join("_", candidate.getSuggestedPath()).toUpperCase(Locale.ROOT);
        if (readablePath.matches("[A-Z0-9_]+") && readablePath.length() <= 108) {
            return readablePath.startsWith(prefix) ? readablePath : prefix + readablePath;
        }
        String stableSource = normalizeText(candidate.getParentKnowledgeNodeCode())
                + "|" + normalizeText(candidate.getName())
                + "|" + joinPath(candidate.getSuggestedPath());
        return prefix + "AI_"
                + Integer.toUnsignedString(stableSource.hashCode(), 36).toUpperCase(Locale.ROOT);
    }

    private AiStandardLibraryLayer growthLayer(String value) {
        String normalized = normalizeText(value).toUpperCase(Locale.ROOT);
        if ("IMPROVEMENT_POINT".equals(normalized)) {
            return AiStandardLibraryLayer.IMPROVEMENT_POINT;
        }
        if ("SKILL_UNIT".equals(normalized)) {
            return AiStandardLibraryLayer.SKILL_UNIT;
        }
        return AiStandardLibraryLayer.MISTAKE_POINT;
    }

    private String parentKnowledgeNodeCode(StandardLibraryGrowthProposal proposal) {
        String explicit = normalizeText(proposal == null ? null : proposal.getParentKnowledgeNodeCode());
        if (!explicit.isBlank()) {
            return explicit;
        }
        List<String> path = proposal == null || proposal.getSuggestedPath() == null
                ? List.of()
                : proposal.getSuggestedPath().stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .toList();
        for (int end = path.size(); end > 0; end--) {
            String candidateCode = String.join(".", path.subList(0, end)).toUpperCase(Locale.ROOT);
            if (hasValidKnowledgePointParent(candidateCode)) {
                return candidateCode;
            }
        }
        return "";
    }

    private Optional<AiStandardLibraryGrowthCandidate> findDuplicate(AiStandardLibraryLayer layer,
                                                                      String parentKnowledgeNodeCode,
                                                                      String code) {
        String parentCode = normalizeText(parentKnowledgeNodeCode);
        Optional<AiStandardLibraryGrowthCandidate> scoped = candidateRepository
                .findByLayerAndParentKnowledgeNodeCodeAndSuggestedCode(
                layer,
                parentCode,
                code);
        if (scoped.isPresent() || !parentCode.isBlank()) {
            return scoped;
        }
        return candidateRepository.findByLayerAndSuggestedCode(layer, code);
    }

    private boolean hasValidKnowledgePointParent(String code) {
        String normalized = normalizeText(code);
        if (normalized.isBlank()) {
            return false;
        }
        return knowledgeNodeRepository.findByCode(normalized)
                .filter(node -> node.isEnabled() && node.getType() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .isPresent();
    }

    private AiStandardLibraryGrowthCandidate maybeAutoPromote(AiStandardLibraryGrowthCandidate candidate) {
        if (candidate == null
                || !properties.isEnabled()
                || !properties.isAutoMergeEnabled()
                || candidate.getStatus() == AiStandardLibraryGrowthCandidateStatus.MERGED
                || candidate.getStatus() == AiStandardLibraryGrowthCandidateStatus.TEACHER_APPROVED
                || candidate.getConfidence() == null
                || candidate.getConfidence() < properties.getAutoMergeMinConfidence()
                || candidate.getOccurrenceCount() == null
                || candidate.getOccurrenceCount() < properties.getAutoMergeMinOccurrences()
                || independentSubmissionCount(candidate) < properties.getAutoMergeMinOccurrences()
                || !"SUPPORTED".equalsIgnoreCase(candidate.getEvidenceStatus())
                || !hasValidKnowledgePointParent(candidate.getParentKnowledgeNodeCode())) {
            return candidate;
        }
        return mergeToFormalLibrary(candidate.getId(), null);
    }

    private List<Long> observedSubmissionIds(Long sourceSubmissionId) {
        return sourceSubmissionId == null || sourceSubmissionId <= 0
                ? List.of()
                : List.of(sourceSubmissionId);
    }

    private List<Long> safeObservedSubmissionIds(AiStandardLibraryGrowthCandidate candidate) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (candidate != null && candidate.getObservedSubmissionIds() != null) {
            candidate.getObservedSubmissionIds().stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(ids::add);
        }
        if (ids.isEmpty() && candidate != null
                && candidate.getSourceSubmissionId() != null
                && candidate.getSourceSubmissionId() > 0) {
            ids.add(candidate.getSourceSubmissionId());
        }
        return List.copyOf(ids);
    }

    private int independentSubmissionCount(AiStandardLibraryGrowthCandidate candidate) {
        return safeObservedSubmissionIds(candidate).size();
    }

    private String growthChangeReason(AdviceGenerationOutput.LibraryGrowthCandidate candidate) {
        return joinLines(List.of(
                candidate.getReason(),
                "错误表现：" + requiredText(candidate.getErrorSymptom(), "未提供"),
                "典型代码特征：" + requiredText(candidate.getTypicalCodePattern(), "未提供"),
                "学生解释话术：" + requiredText(candidate.getStudentExplanation(), "未提供")
        ));
    }

    private GrowthDraft growthDraft(String changeReason) {
        String errorSymptom = "";
        String typicalCodePattern = "";
        String studentExplanation = "";
        for (String line : lines(changeReason)) {
            if (line.startsWith("错误表现：")) {
                errorSymptom = normalizeText(line.substring("错误表现：".length()));
            } else if (line.startsWith("典型代码特征：")) {
                typicalCodePattern = normalizeText(line.substring("典型代码特征：".length()));
            } else if (line.startsWith("学生解释话术：")) {
                studentExplanation = normalizeText(line.substring("学生解释话术：".length()));
            }
        }
        return new GrowthDraft(errorSymptom, typicalCodePattern, studentExplanation);
    }

    private String requiredText(String value, String fallback) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record GrowthDraft(String errorSymptom, String typicalCodePattern, String studentExplanation) {
    }
}

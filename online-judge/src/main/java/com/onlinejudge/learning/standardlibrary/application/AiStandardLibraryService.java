package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardApplicationScenario;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryGrowthCandidateStatus;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDiagnosticLayerResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemRequest;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationExpansionResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationNodeResponse;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardApplicationScenarioRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryGrowthCandidateRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryItemRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.submission.application.StandardLibraryPack;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiStandardLibraryService {

    private final AiStandardLibraryItemRepository repository;
    private final AiStandardSkillUnitRepository skillUnitRepository;
    private final AiStandardMistakePointRepository mistakePointRepository;
    private final AiStandardImprovementPointRepository improvementPointRepository;
    private final AiStandardApplicationScenarioRepository applicationScenarioRepository;
    private final InformaticsKnowledgeNodeRepository knowledgeRepository;
    private final AiStandardLibraryGrowthCandidateRepository growthCandidateRepository;

    @Transactional(readOnly = true)
    public List<AiStandardLibraryItemResponse> list(String layer, String category, String enabled, String query) {
        AiStandardLibraryLayer layerFilter = parseLayerOrNull(layer);
        Boolean enabledFilter = parseBooleanOrNull(enabled);
        String categoryFilter = normalizeText(category).toLowerCase(Locale.ROOT);
        String queryFilter = normalizeText(query).toLowerCase(Locale.ROOT);
        return repository.findAllByOrderByLayerAscCategoryAscCodeAsc().stream()
                .filter(item -> layerFilter == null || item.getLayer() == layerFilter)
                .filter(item -> enabledFilter == null || item.isEnabled() == enabledFilter)
                .filter(item -> categoryFilter.isBlank() || item.getCategory().toLowerCase(Locale.ROOT).contains(categoryFilter))
                .filter(item -> matchesQuery(item, queryFilter))
                .map(AiStandardLibraryItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiStandardLibraryItemResponse get(Long id) {
        return AiStandardLibraryItemResponse.from(find(id));
    }

    @Transactional
    public AiStandardLibraryItemResponse create(AiStandardLibraryItemRequest request) {
        AiStandardLibraryLayer layer = parseLayer(request.getLayer());
        String code = normalizeCode(request.getCode());
        if (formalItemExistsForOther(layer, code, null, "", null)) {
            throw new IllegalArgumentException("标准库条目已存在: " + layer + "/" + code);
        }
        AiStandardLibraryItem item = new AiStandardLibraryItem();
        item.setLayer(layer);
        item.setCode(code);
        apply(item, request);
        AiStandardLibraryItem saved = repository.save(item);
        syncCanonicalFromSnapshot(saved, null, "");
        return AiStandardLibraryItemResponse.from(saved);
    }

    @Transactional
    public AiStandardLibraryItemResponse update(Long id, AiStandardLibraryItemRequest request) {
        AiStandardLibraryItem item = find(id);
        AiStandardLibraryLayer previousLayer = item.getLayer();
        String previousCode = item.getCode();
        AiStandardLibraryLayer requestedLayer = parseLayer(request.getLayer());
        String requestedCode = normalizeCode(request.getCode());
        if (item.getLayer() != requestedLayer || !item.getCode().equals(requestedCode)) {
            if (formalItemExistsForOther(requestedLayer, requestedCode, previousLayer, previousCode, item.getId())) {
                throw new IllegalArgumentException("标准库条目已存在: " + requestedLayer + "/" + requestedCode);
            }
            item.setLayer(requestedLayer);
            item.setCode(requestedCode);
        }
        apply(item, request);
        AiStandardLibraryItem saved = repository.save(item);
        syncCanonicalFromSnapshot(saved, previousLayer, previousCode);
        return AiStandardLibraryItemResponse.from(saved);
    }

    @Transactional
    public AiStandardLibraryItemResponse setEnabled(Long id, boolean enabled) {
        AiStandardLibraryItem item = find(id);
        AiStandardLibraryLayer previousLayer = item.getLayer();
        String previousCode = item.getCode();
        item.setEnabled(enabled);
        AiStandardLibraryItem saved = repository.save(item);
        syncCanonicalFromSnapshot(saved, previousLayer, previousCode);
        return AiStandardLibraryItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StandardLibraryPack.BasicCauseOption> enabledBasicCauses() {
        List<AiStandardMistakePoint> normalizedMistakes = mistakePointRepository.findByEnabledTrueOrderByCategoryAscCodeAsc();
        if (!normalizedMistakes.isEmpty()) {
            return preferIntelligentItems(normalizedMistakes, AiStandardMistakePoint::getCode).stream()
                    .sorted(Comparator.comparing(AiStandardMistakePoint::getCategory)
                            .thenComparing(AiStandardMistakePoint::getCode))
                    .map(this::toBasicCause)
                    .toList();
        }
        List<AiStandardLibraryItem> legacyItems = repository.findByEnabledTrueOrderByLayerAscCategoryAscCodeAsc().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT
                        || item.getLayer() == AiStandardLibraryLayer.BASIC_CAUSE)
                .toList();
        return preferIntelligentItems(legacyItems, AiStandardLibraryItem::getCode).stream()
                .sorted(Comparator.comparing(AiStandardLibraryItem::getCategory).thenComparing(AiStandardLibraryItem::getCode))
                .map(this::toBasicCause)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StandardLibraryPack.ImprovementPointOption> enabledImprovementPoints() {
        List<AiStandardImprovementPoint> normalizedImprovements =
                improvementPointRepository.findByEnabledTrueOrderByCategoryAscCodeAsc();
        if (!normalizedImprovements.isEmpty()) {
            return normalizedImprovements.stream()
                    .sorted(Comparator.comparing(AiStandardImprovementPoint::getCategory)
                            .thenComparing(AiStandardImprovementPoint::getCode))
                    .map(this::toImprovementPoint)
                    .toList();
        }
        return repository.findByEnabledTrueOrderByLayerAscCategoryAscCodeAsc().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.IMPROVEMENT_POINT)
                .sorted(Comparator.comparing(AiStandardLibraryItem::getCategory).thenComparing(AiStandardLibraryItem::getCode))
                .map(this::toImprovementPoint)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasEnabledKnowledge() {
        if (!normalizedNavigationItems().isEmpty()) {
            return true;
        }
        return repository.findByEnabledTrueOrderByLayerAscCategoryAscCodeAsc().stream()
                .anyMatch(item -> !ArchivedFallbackCodePolicy.isArchivedFallback(item.getLayer(), item.getCode()));
    }

    @Transactional(readOnly = true)
    public List<AiStandardLibraryItem> enabledNavigationItems() {
        List<AiStandardLibraryItem> normalizedItems = normalizedNavigationItems();
        List<AiStandardLibraryItem> legacyItems = repository.findByEnabledTrueOrderByLayerAscCategoryAscCodeAsc().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT
                        || item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT
                        || item.getLayer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                        || item.getLayer() == AiStandardLibraryLayer.BASIC_CAUSE)
                .filter(item -> !ArchivedFallbackCodePolicy.isArchivedFallback(item.getLayer(), item.getCode()))
                .toList();
        if (normalizedItems.isEmpty()) {
            return legacyItems;
        }
        LinkedHashMap<String, AiStandardLibraryItem> merged = new LinkedHashMap<>();
        normalizedItems.forEach(item -> merged.put(navigationItemKey(item), item));
        legacyItems.forEach(item -> merged.putIfAbsent(navigationItemKey(item), item));
        return merged.values().stream().toList();
    }

    @Transactional(readOnly = true)
    public List<AiStandardLibraryNavigationNodeResponse> listRootKnowledgeAreas() {
        return knowledgeRepository.findByEnabledTrueAndParentCodeIsNullOrderBySortOrderAscCodeAsc().stream()
                .map(this::toNavigationNode)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiStandardLibraryNavigationExpansionResponse expandKnowledgeNode(String code) {
        return expandKnowledgeNode(code, 0, 50);
    }

    @Transactional(readOnly = true)
    public AiStandardLibraryNavigationExpansionResponse expandKnowledgeNode(String code, int page, int size) {
        InformaticsKnowledgeNode node = findKnowledgeNode(code);
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<InformaticsKnowledgeNode> allNodes = knowledgeRepository.findByEnabledTrueOrderByPathAscSortOrderAscCodeAsc();
        Page<InformaticsKnowledgeNode> childPage = knowledgeRepository
                .findByEnabledTrueAndParentCodeOrderBySortOrderAscCodeAsc(
                        node.getCode(),
                        PageRequest.of(normalizedPage, normalizedSize));
        List<AiStandardLibraryNavigationNodeResponse> children = childPage.stream()
                .map(this::toNavigationNode)
                .toList();
        return AiStandardLibraryNavigationExpansionResponse.builder()
                .node(toNavigationNode(node))
                .ancestors(knowledgeAncestors(node, allNodes))
                .children(children)
                .childPage(normalizedPage)
                .childSize(normalizedSize)
                .childTotal(childPage.getTotalElements())
                .childHasMore(childPage.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public AiStandardLibraryDiagnosticLayerResponse expandDiagnosticLayer(String knowledgePointCode) {
        InformaticsKnowledgeNode knowledgePoint = findKnowledgeNode(knowledgePointCode);
        if (knowledgePoint.getType() != InformaticsKnowledgeNodeType.KNOWLEDGE_POINT) {
            throw new IllegalArgumentException("诊断层只能展开到知识点: " + knowledgePointCode);
        }
        String code = knowledgePoint.getCode();
        List<AiStandardSkillUnit> directSkills =
                skillUnitRepository.findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(code);
        List<AiStandardMistakePoint> mistakes =
                mistakePointRepository.findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(code);
        List<AiStandardImprovementPoint> improvements =
                improvementPointRepository.findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(code);
        List<AiStandardApplicationScenario> applicationScenarios =
                applicationScenarioRepository
                        .findByEnabledTrueAndKnowledgePointCodeOrderBySortOrderAscCodeAsc(code);

        Set<String> referencedSkillCodes = new LinkedHashSet<>();
        directSkills.stream()
                .map(AiStandardSkillUnit::getCode)
                .map(this::normalizeText)
                .filter(skillCode -> !skillCode.isBlank())
                .forEach(referencedSkillCodes::add);
        mistakes.stream()
                .map(AiStandardMistakePoint::getSkillUnitCode)
                .map(this::normalizeText)
                .filter(skillCode -> !skillCode.isBlank())
                .forEach(referencedSkillCodes::add);
        improvements.stream()
                .map(AiStandardImprovementPoint::getSkillUnitCode)
                .map(this::normalizeText)
                .filter(skillCode -> !skillCode.isBlank())
                .forEach(referencedSkillCodes::add);
        List<AiStandardSkillUnit> skills = referencedSkillCodes.isEmpty()
                ? List.of()
                : skillUnitRepository.findByEnabledTrueAndCodeInOrderByCategoryAscCodeAsc(referencedSkillCodes);

        Map<String, List<AiStandardLibraryDiagnosticLayerResponse.MistakePoint>> mistakesBySkill = mistakes.stream()
                .map(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::from)
                .collect(Collectors.groupingBy(
                        item -> normalizeText(item.getSkillUnitCode()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint>> improvementsBySkill =
                improvements.stream()
                        .filter(item -> !normalizeText(item.getSkillUnitCode()).isBlank())
                        .map(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint::from)
                        .collect(Collectors.groupingBy(
                                item -> normalizeText(item.getSkillUnitCode()),
                                LinkedHashMap::new,
                                Collectors.toList()));
        Map<String, List<AiStandardLibraryDiagnosticLayerResponse.ApplicationScenario>> scenariosBySkill =
                applicationScenarios.stream()
                        .map(AiStandardLibraryDiagnosticLayerResponse.ApplicationScenario::from)
                        .collect(Collectors.groupingBy(
                                item -> normalizeText(item.getSkillUnitCode()),
                                LinkedHashMap::new,
                                Collectors.toList()));
        List<AiStandardLibraryDiagnosticLayerResponse.SkillUnit> skillUnits = skills.stream()
                .map(skill -> AiStandardLibraryDiagnosticLayerResponse.SkillUnit.from(
                        skill,
                        mistakesBySkill.getOrDefault(skill.getCode(), List.of()),
                        improvementsBySkill.getOrDefault(skill.getCode(), List.of()),
                        scenariosBySkill.getOrDefault(skill.getCode(), List.of())))
                .toList();
        Set<String> skillCodes = skills.stream()
                .map(AiStandardSkillUnit::getCode)
                .map(this::normalizeText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint> directImprovements = improvements.stream()
                .filter(item -> normalizeText(item.getSkillUnitCode()).isBlank()
                        || !skillCodes.contains(normalizeText(item.getSkillUnitCode())))
                .map(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint::from)
                .toList();
        List<AiStandardLibraryDiagnosticLayerResponse.ProvisionalCandidate> provisionalCandidates =
                growthCandidateRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(candidate -> code.equals(normalizeText(candidate.getParentKnowledgeNodeCode())))
                        .filter(candidate -> List.of(
                                AiStandardLibraryGrowthCandidateStatus.PROPOSED,
                                AiStandardLibraryGrowthCandidateStatus.NEEDS_REVIEW,
                                AiStandardLibraryGrowthCandidateStatus.MERGED_SIMILAR
                        ).contains(candidate.getStatus()))
                        .limit(8)
                        .map(candidate -> AiStandardLibraryDiagnosticLayerResponse.ProvisionalCandidate.builder()
                                .code(candidate.getSuggestedCode())
                                .name(candidate.getSuggestedName())
                                .layer(candidate.getLayer().name())
                                .parentKnowledgeNodeCode(candidate.getParentKnowledgeNodeCode())
                                .suggestedPath(splitDotPath(candidate.getSuggestedPath()))
                                .description(candidate.getChangeReason())
                                .confidence(candidate.getConfidence())
                                .occurrenceCount(candidate.getOccurrenceCount())
                                .provisional(true)
                                .build())
                        .toList();
        return AiStandardLibraryDiagnosticLayerResponse.builder()
                .knowledgePoint(toNavigationNode(knowledgePoint))
                .skillUnits(skillUnits)
                .directImprovementPoints(directImprovements)
                .provisionalCandidates(provisionalCandidates)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AiStandardApplicationScenario> findRelevantApplicationScenarios(
            Set<String> skillUnitCodes,
            Set<String> knowledgePointCodes,
            int limit) {
        LinkedHashMap<String, AiStandardApplicationScenario> scenarios = new LinkedHashMap<>();
        Set<String> normalizedSkills = safeCodes(skillUnitCodes);
        Set<String> normalizedKnowledge = safeCodes(knowledgePointCodes);
        if (!normalizedSkills.isEmpty()) {
            applicationScenarioRepository
                    .findByEnabledTrueAndSkillUnitCodeInOrderBySortOrderAscCodeAsc(normalizedSkills)
                    .forEach(item -> scenarios.putIfAbsent(item.getCode(), item));
        } else if (!normalizedKnowledge.isEmpty()) {
            applicationScenarioRepository
                    .findByEnabledTrueAndKnowledgePointCodeInOrderBySortOrderAscCodeAsc(normalizedKnowledge)
                    .forEach(item -> scenarios.putIfAbsent(item.getCode(), item));
        }
        int boundedLimit = Math.max(0, Math.min(limit, 12));
        if (boundedLimit == 0) {
            return List.of();
        }
        return scenarios.values().stream()
                .sorted(Comparator
                        .comparing(AiStandardApplicationScenario::getSkillUnitCode)
                        .thenComparing(AiStandardApplicationScenario::getTransferPairCode)
                        .thenComparingInt(AiStandardApplicationScenario::getSortOrder)
                        .thenComparing(AiStandardApplicationScenario::getCode))
                .limit(boundedLimit)
                .toList();
    }

    private Set<String> safeCodes(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return codes.stream()
                .map(this::normalizeText)
                .filter(code -> !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> splitDotPath(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("\\."))
                .map(this::normalizeText)
                .filter(item -> !item.isBlank())
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean formalItemExists(AiStandardLibraryLayer layer, String code) {
        String normalizedCode = normalizeCode(code);
        return canonicalRecordExists(layer, normalizedCode)
                || repository.existsByLayerAndCode(layer, normalizedCode);
    }

    @Transactional(readOnly = true)
    public Optional<AiStandardLibraryItem> findFormalItemAsLegacy(AiStandardLibraryLayer layer, String code) {
        if (layer == null || code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalizedCode = normalizeCode(code);
        Optional<AiStandardLibraryItem> canonical = canonicalAsLegacy(layer, normalizedCode);
        if (canonical.isPresent()) {
            return canonical;
        }
        return repository.findByLayerAndCode(layer, normalizedCode);
    }

    StandardLibraryPack.BasicCauseOption toBasicCause(AiStandardLibraryItem item) {
        if (item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT) {
            return StandardLibraryPack.BasicCauseOption.builder()
                    .id(item.getCode())
                    .category(item.getCategory())
                    .name(item.getName())
                    .description(item.getDescription())
                    .studentExplanation(item.getCommonMisconception())
                    .teacherExplanation(item.getTeacherExplanation())
                    .evidenceSignals(List.of())
                    .commonCodePatterns(List.of())
                    .judgeSignals(List.of())
                    .hintL1("")
                    .hintL2("")
                    .hintL3("")
                    .abilityPoint(item.getSkillUnitCode())
                    .severity(item.getSeverity())
                    .applicableLanguages(lines(item.getApplicableLanguages()))
                    .relatedFineTags(lines(item.getKnowledgeNodeCodes()))
                    .teachingAction("")
                    .build();
        }
        return StandardLibraryPack.BasicCauseOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation(item.getStudentExplanation())
                .teacherExplanation(item.getTeacherExplanation())
                .evidenceSignals(lines(item.getEvidenceSignals()))
                .commonCodePatterns(lines(item.getCommonCodePatterns()))
                .judgeSignals(lines(item.getJudgeSignals()))
                .hintL1(item.getHintL1())
                .hintL2(item.getHintL2())
                .hintL3(item.getHintL3())
                .abilityPoint(item.getAbilityPoint())
                .severity(item.getSeverity())
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .relatedFineTags(lines(item.getRelatedItems()))
                .teachingAction(item.getTeachingAction())
                .build();
    }

    StandardLibraryPack.BasicCauseOption toBasicCause(AiStandardMistakePoint item) {
        return StandardLibraryPack.BasicCauseOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation(item.getMisconception())
                .teacherExplanation(item.getRepairStrategy())
                .evidenceSignals(List.of())
                .commonCodePatterns(lines(item.getSymptom()))
                .judgeSignals(List.of())
                .hintL1("")
                .hintL2("")
                .hintL3("")
                .abilityPoint(item.getSkillUnitCode())
                .severity(item.getSeverity())
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .relatedFineTags(lines(item.getKnowledgeNodeCodes()))
                .teachingAction("")
                .build();
    }

    StandardLibraryPack.ImprovementPointOption toImprovementPoint(AiStandardLibraryItem item) {
        return StandardLibraryPack.ImprovementPointOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .whenToUse(item.getWhenToUse())
                .studentBenefit(item.getStudentBenefit())
                .teacherExplanation(item.getTeacherExplanation())
                .requiredEvidence(lines(item.getRequiredEvidence()))
                .hintL1(item.getHintL1())
                .hintL2(item.getHintL2())
                .hintL3(item.getHintL3())
                .abilityPoint(item.getAbilityPoint())
                .relatedBasicCauses(lines(item.getRelatedItems()))
                .build();
    }

    StandardLibraryPack.ImprovementPointOption toImprovementPoint(AiStandardImprovementPoint item) {
        return StandardLibraryPack.ImprovementPointOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .whenToUse(item.getImprovementGoal())
                .studentBenefit(item.getStudentBenefit())
                .teacherExplanation(item.getTeacherExplanation())
                .requiredEvidence(List.of())
                .hintL1("")
                .hintL2("")
                .hintL3("")
                .abilityPoint(item.getSkillUnitCode())
                .relatedBasicCauses(lines(item.getRelatedMistakeCodes()))
                .build();
    }

    private boolean formalItemExistsForOther(AiStandardLibraryLayer layer,
                                             String code,
                                             AiStandardLibraryLayer previousLayer,
                                             String previousCode,
                                             Long currentLegacyId) {
        if (canonicalRecordExists(layer, code) && !sameCanonicalTarget(previousLayer, previousCode, layer, code)) {
            return true;
        }
        Optional<AiStandardLibraryItem> duplicate = repository.findByLayerAndCode(layer, code);
        return duplicate.isPresent()
                && (currentLegacyId == null || !duplicate.get().getId().equals(currentLegacyId));
    }

    private boolean canonicalRecordExists(AiStandardLibraryLayer layer, String code) {
        if (layer == null || code == null || code.isBlank()) {
            return false;
        }
        AiStandardLibraryLayer canonicalLayer = canonicalLayer(layer);
        if (canonicalLayer == AiStandardLibraryLayer.SKILL_UNIT) {
            return skillUnitRepository.existsByCode(code);
        }
        if (canonicalLayer == AiStandardLibraryLayer.MISTAKE_POINT) {
            return mistakePointRepository.existsByCode(code);
        }
        if (canonicalLayer == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
            return improvementPointRepository.existsByCode(code);
        }
        return false;
    }

    private Optional<AiStandardLibraryItem> canonicalAsLegacy(AiStandardLibraryLayer layer, String code) {
        AiStandardLibraryLayer canonicalLayer = canonicalLayer(layer);
        if (canonicalLayer == AiStandardLibraryLayer.SKILL_UNIT) {
            return skillUnitRepository.findByCode(code).map(this::toLegacyNavigationItem);
        }
        if (canonicalLayer == AiStandardLibraryLayer.MISTAKE_POINT) {
            return mistakePointRepository.findByCode(code).map(this::toLegacyNavigationItem);
        }
        if (canonicalLayer == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
            return improvementPointRepository.findByCode(code).map(this::toLegacyNavigationItem);
        }
        return Optional.empty();
    }

    private void syncCanonicalFromSnapshot(AiStandardLibraryItem item,
                                           AiStandardLibraryLayer previousLayer,
                                           String previousCode) {
        if (item == null || !canSyncCanonical(item)) {
            return;
        }
        AiStandardLibraryLayer currentCanonicalLayer = canonicalLayer(item.getLayer());
        AiStandardLibraryLayer previousCanonicalLayer = canonicalLayer(previousLayer);
        if (previousLayer != null && previousCanonicalLayer != currentCanonicalLayer) {
            disableCanonical(previousLayer, previousCode);
        }
        if (currentCanonicalLayer == AiStandardLibraryLayer.SKILL_UNIT) {
            syncSkillUnit(item, previousCanonicalLayer, previousCode);
        } else if (currentCanonicalLayer == AiStandardLibraryLayer.MISTAKE_POINT) {
            syncMistakePoint(item, previousCanonicalLayer, previousCode);
        } else if (currentCanonicalLayer == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
            syncImprovementPoint(item, previousCanonicalLayer, previousCode);
        }
    }

    private void syncSkillUnit(AiStandardLibraryItem item,
                               AiStandardLibraryLayer previousCanonicalLayer,
                               String previousCode) {
        Optional<AiStandardSkillUnit> existing = Optional.empty();
        if (previousCanonicalLayer == AiStandardLibraryLayer.SKILL_UNIT && !normalizeText(previousCode).isBlank()) {
            existing = skillUnitRepository.findByCode(previousCode);
        }
        AiStandardSkillUnit unit = existing.or(() -> skillUnitRepository.findByCode(item.getCode()))
                .orElseGet(AiStandardSkillUnit::new);
        unit.setCode(item.getCode());
        unit.setCategory(item.getCategory());
        unit.setName(item.getName());
        unit.setDescription(item.getDescription());
        unit.setLearningGoal(firstNonBlank(item.getStudentExplanation(), item.getDescription()));
        unit.setPrimaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode());
        unit.setKnowledgeNodeCodes(item.getKnowledgeNodeCodes());
        unit.setPrerequisiteKnowledgeCodes(item.getPrerequisiteKnowledgeCodes());
        unit.setMasteryLevel(item.getSeverity());
        unit.setApplicableLanguages(item.getApplicableLanguages());
        unit.setEnabled(item.isEnabled());
        unit.setLibraryVersion(item.getLibraryVersion());
        skillUnitRepository.save(unit);
    }

    private void syncMistakePoint(AiStandardLibraryItem item,
                                  AiStandardLibraryLayer previousCanonicalLayer,
                                  String previousCode) {
        Optional<AiStandardMistakePoint> existing = Optional.empty();
        if (previousCanonicalLayer == AiStandardLibraryLayer.MISTAKE_POINT && !normalizeText(previousCode).isBlank()) {
            existing = mistakePointRepository.findByCode(previousCode);
        }
        AiStandardMistakePoint mistake = existing.or(() -> mistakePointRepository.findByCode(item.getCode()))
                .orElseGet(AiStandardMistakePoint::new);
        mistake.setCode(item.getCode());
        mistake.setCategory(item.getCategory());
        mistake.setName(item.getName());
        mistake.setDescription(item.getDescription());
        mistake.setSkillUnitCode(item.getSkillUnitCode());
        mistake.setMistakeType(item.getMistakeType());
        mistake.setMisconception(item.getCommonMisconception());
        mistake.setSymptom(firstNonBlank(item.getCommonCodePatterns(), item.getDescription()));
        mistake.setRepairStrategy(firstNonBlank(item.getTeacherExplanation(), item.getTeachingAction()));
        mistake.setSeverity(item.getSeverity());
        mistake.setPrimaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode());
        mistake.setKnowledgeNodeCodes(item.getKnowledgeNodeCodes());
        mistake.setPrerequisiteKnowledgeCodes(item.getPrerequisiteKnowledgeCodes());
        mistake.setApplicableLanguages(item.getApplicableLanguages());
        mistake.setEnabled(item.isEnabled());
        mistake.setLibraryVersion(item.getLibraryVersion());
        mistakePointRepository.save(mistake);
    }

    private void syncImprovementPoint(AiStandardLibraryItem item,
                                      AiStandardLibraryLayer previousCanonicalLayer,
                                      String previousCode) {
        Optional<AiStandardImprovementPoint> existing = Optional.empty();
        if (previousCanonicalLayer == AiStandardLibraryLayer.IMPROVEMENT_POINT && !normalizeText(previousCode).isBlank()) {
            existing = improvementPointRepository.findByCode(previousCode);
        }
        AiStandardImprovementPoint improvement = existing.or(() -> improvementPointRepository.findByCode(item.getCode()))
                .orElseGet(AiStandardImprovementPoint::new);
        improvement.setCode(item.getCode());
        improvement.setCategory(item.getCategory());
        improvement.setName(item.getName());
        improvement.setDescription(item.getDescription());
        improvement.setSkillUnitCode(item.getSkillUnitCode());
        improvement.setPrimaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode());
        improvement.setKnowledgeNodeCodes(item.getKnowledgeNodeCodes());
        improvement.setImprovementGoal(firstNonBlank(item.getWhenToUse(), item.getDescription()));
        improvement.setPracticeStrategy(firstNonBlank(item.getTeachingAction(), item.getTeacherExplanation()));
        improvement.setStudentBenefit(item.getStudentBenefit());
        improvement.setTeacherExplanation(item.getTeacherExplanation());
        improvement.setRelatedMistakeCodes(item.getRelatedItems());
        improvement.setApplicableLanguages(item.getApplicableLanguages());
        improvement.setEnabled(item.isEnabled());
        improvement.setLibraryVersion(item.getLibraryVersion());
        improvementPointRepository.save(improvement);
    }

    private void disableCanonical(AiStandardLibraryLayer layer, String code) {
        if (layer == null || code == null || code.isBlank()) {
            return;
        }
        AiStandardLibraryLayer canonicalLayer = canonicalLayer(layer);
        if (canonicalLayer == AiStandardLibraryLayer.SKILL_UNIT) {
            skillUnitRepository.findByCode(code).ifPresent(item -> {
                item.setEnabled(false);
                skillUnitRepository.save(item);
            });
        } else if (canonicalLayer == AiStandardLibraryLayer.MISTAKE_POINT) {
            mistakePointRepository.findByCode(code).ifPresent(item -> {
                item.setEnabled(false);
                mistakePointRepository.save(item);
            });
        } else if (canonicalLayer == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
            improvementPointRepository.findByCode(code).ifPresent(item -> {
                item.setEnabled(false);
                improvementPointRepository.save(item);
            });
        }
    }

    private boolean canSyncCanonical(AiStandardLibraryItem item) {
        AiStandardLibraryLayer canonicalLayer = canonicalLayer(item.getLayer());
        if (canonicalLayer == AiStandardLibraryLayer.SKILL_UNIT) {
            return !normalizeText(item.getDescription()).isBlank()
                    && !normalizeText(item.getStudentExplanation()).isBlank()
                    && !normalizeText(item.getPrimaryKnowledgeNodeCode()).isBlank()
                    && !lines(item.getKnowledgeNodeCodes()).isEmpty();
        }
        if (canonicalLayer == AiStandardLibraryLayer.MISTAKE_POINT) {
            return !normalizeText(item.getDescription()).isBlank()
                    && !normalizeText(item.getSkillUnitCode()).isBlank()
                    && !normalizeText(item.getPrimaryKnowledgeNodeCode()).isBlank()
                    && !normalizeText(item.getMistakeType()).isBlank()
                    && !normalizeText(item.getCommonMisconception()).isBlank()
                    && !lines(item.getKnowledgeNodeCodes()).isEmpty();
        }
        return canonicalLayer == AiStandardLibraryLayer.IMPROVEMENT_POINT;
    }

    private boolean sameCanonicalTarget(AiStandardLibraryLayer previousLayer,
                                        String previousCode,
                                        AiStandardLibraryLayer currentLayer,
                                        String currentCode) {
        if (previousLayer == null || previousCode == null || previousCode.isBlank()) {
            return false;
        }
        return canonicalLayer(previousLayer) == canonicalLayer(currentLayer)
                && normalizeText(previousCode).equals(normalizeText(currentCode));
    }

    private AiStandardLibraryLayer canonicalLayer(AiStandardLibraryLayer layer) {
        if (layer == AiStandardLibraryLayer.BASIC_CAUSE) {
            return AiStandardLibraryLayer.MISTAKE_POINT;
        }
        return layer;
    }

    private List<AiStandardLibraryItem> normalizedNavigationItems() {
        List<AiStandardSkillUnit> skills = skillUnitRepository.findByEnabledTrueOrderByCategoryAscCodeAsc();
        List<AiStandardMistakePoint> mistakes = mistakePointRepository.findByEnabledTrueOrderByCategoryAscCodeAsc();
        List<AiStandardImprovementPoint> improvements =
                improvementPointRepository.findByEnabledTrueOrderByCategoryAscCodeAsc();
        if (skills.isEmpty() && mistakes.isEmpty() && improvements.isEmpty()) {
            return List.of();
        }
        List<AiStandardLibraryItem> items = new ArrayList<>();
        skills.stream().map(this::toLegacyNavigationItem).forEach(items::add);
        mistakes.stream().map(this::toLegacyNavigationItem).forEach(items::add);
        improvements.stream().map(this::toLegacyNavigationItem).forEach(items::add);
        return preferIntelligentItems(items, AiStandardLibraryItem::getCode);
    }

    private <T> List<T> preferIntelligentItems(List<T> items, Function<T, String> codeAccessor) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> !ArchivedFallbackCodePolicy.isArchivedFallback(codeAccessor.apply(item)))
                .toList();
    }

    private String navigationItemKey(AiStandardLibraryItem item) {
        String layer = item.getLayer() == null ? "" : item.getLayer().name();
        String code = item.getCode() == null ? "" : item.getCode();
        return layer + "/" + code;
    }

    private AiStandardLibraryItem toLegacyNavigationItem(AiStandardSkillUnit item) {
        return AiStandardLibraryItem.builder()
                .id(item.getId())
                .layer(AiStandardLibraryLayer.SKILL_UNIT)
                .code(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation(item.getLearningGoal())
                .teacherExplanation("")
                .skillUnitCode("")
                .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                .mistakeType("")
                .commonMisconception("")
                .evidenceSignals("")
                .commonCodePatterns("")
                .judgeSignals("")
                .requiredEvidence("")
                .whenToUse("")
                .studentBenefit("")
                .hintL1("")
                .hintL2("")
                .hintL3("")
                .abilityPoint(item.getName())
                .severity(item.getMasteryLevel())
                .applicableLanguages(item.getApplicableLanguages())
                .relatedItems("")
                .knowledgeNodeCodes(item.getKnowledgeNodeCodes())
                .relatedKnowledgeNodeCodes(relatedKnowledgeNodeCodes(item.getPrimaryKnowledgeNodeCode(), item.getKnowledgeNodeCodes()))
                .prerequisiteKnowledgeCodes(item.getPrerequisiteKnowledgeCodes())
                .teachingAction("")
                .enabled(item.isEnabled())
                .libraryVersion(item.getLibraryVersion())
                .createdAt(orNow(item.getCreatedAt()))
                .updatedAt(orNow(item.getUpdatedAt()))
                .build();
    }

    private AiStandardLibraryItem toLegacyNavigationItem(AiStandardMistakePoint item) {
        return AiStandardLibraryItem.builder()
                .id(item.getId())
                .layer(AiStandardLibraryLayer.MISTAKE_POINT)
                .code(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation("")
                .teacherExplanation(item.getRepairStrategy())
                .skillUnitCode(item.getSkillUnitCode())
                .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                .mistakeType(item.getMistakeType())
                .commonMisconception(item.getMisconception())
                .evidenceSignals("")
                .commonCodePatterns(item.getSymptom())
                .judgeSignals("")
                .requiredEvidence("")
                .whenToUse("")
                .studentBenefit("")
                .hintL1("")
                .hintL2("")
                .hintL3("")
                .abilityPoint(item.getSkillUnitCode())
                .severity(item.getSeverity())
                .applicableLanguages(item.getApplicableLanguages())
                .relatedItems(item.getSkillUnitCode())
                .knowledgeNodeCodes(item.getKnowledgeNodeCodes())
                .relatedKnowledgeNodeCodes(relatedKnowledgeNodeCodes(item.getPrimaryKnowledgeNodeCode(), item.getKnowledgeNodeCodes()))
                .prerequisiteKnowledgeCodes(item.getPrerequisiteKnowledgeCodes())
                .teachingAction("")
                .enabled(item.isEnabled())
                .libraryVersion(item.getLibraryVersion())
                .createdAt(orNow(item.getCreatedAt()))
                .updatedAt(orNow(item.getUpdatedAt()))
                .build();
    }

    private AiStandardLibraryItem toLegacyNavigationItem(AiStandardImprovementPoint item) {
        return AiStandardLibraryItem.builder()
                .id(item.getId())
                .layer(AiStandardLibraryLayer.IMPROVEMENT_POINT)
                .code(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation("")
                .teacherExplanation(item.getTeacherExplanation())
                .skillUnitCode(item.getSkillUnitCode())
                .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                .mistakeType("")
                .commonMisconception("")
                .evidenceSignals("")
                .commonCodePatterns("")
                .judgeSignals("")
                .requiredEvidence("")
                .whenToUse(item.getImprovementGoal())
                .studentBenefit(item.getStudentBenefit())
                .hintL1("")
                .hintL2("")
                .hintL3("")
                .abilityPoint(item.getSkillUnitCode())
                .severity("")
                .applicableLanguages(item.getApplicableLanguages())
                .relatedItems(item.getRelatedMistakeCodes())
                .knowledgeNodeCodes(item.getKnowledgeNodeCodes())
                .relatedKnowledgeNodeCodes(relatedKnowledgeNodeCodes(item.getPrimaryKnowledgeNodeCode(), item.getKnowledgeNodeCodes()))
                .prerequisiteKnowledgeCodes("")
                .teachingAction("")
                .enabled(item.isEnabled())
                .libraryVersion(item.getLibraryVersion())
                .createdAt(orNow(item.getCreatedAt()))
                .updatedAt(orNow(item.getUpdatedAt()))
                .build();
    }

    private LocalDateTime orNow(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }

    private AiStandardLibraryItem find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("标准库条目不存在: " + id));
    }

    private InformaticsKnowledgeNode findKnowledgeNode(String code) {
        String normalized = normalizeText(code);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("知识节点编码不能为空");
        }
        return knowledgeRepository.findByCode(normalized)
                .filter(InformaticsKnowledgeNode::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("知识节点不存在或已停用: " + code));
    }

    private AiStandardLibraryNavigationNodeResponse toNavigationNode(InformaticsKnowledgeNode node) {
        return AiStandardLibraryNavigationNodeResponse.from(
                node,
                knowledgeRepository.existsByEnabledTrueAndParentCode(node.getCode()),
                hasDiagnosticLayer(node.getCode()));
    }

    private boolean hasDiagnosticLayer(String knowledgePointCode) {
        return !skillUnitRepository
                .findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(knowledgePointCode).isEmpty()
                || !mistakePointRepository
                .findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(knowledgePointCode).isEmpty()
                || !improvementPointRepository
                .findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(knowledgePointCode).isEmpty();
    }

    private List<AiStandardLibraryNavigationNodeResponse> knowledgeAncestors(InformaticsKnowledgeNode node,
                                                                             List<InformaticsKnowledgeNode> allNodes) {
        Map<String, InformaticsKnowledgeNode> byCode = new LinkedHashMap<>();
        for (InformaticsKnowledgeNode candidate : allNodes) {
            byCode.put(candidate.getCode(), candidate);
        }
        List<AiStandardLibraryNavigationNodeResponse> result = new ArrayList<>();
        InformaticsKnowledgeNode current = node;
        while (current.getParentCode() != null && byCode.containsKey(current.getParentCode())) {
            current = byCode.get(current.getParentCode());
            result.add(0, toNavigationNode(current));
        }
        return result;
    }

    private void apply(AiStandardLibraryItem item, AiStandardLibraryItemRequest request) {
        String knowledgeNodeCodes = join(request.getKnowledgeNodeCodes());
        String primaryKnowledgeNodeCode = firstNonBlank(request.getPrimaryKnowledgeNodeCode(), firstLine(knowledgeNodeCodes));
        item.setCategory(required(request.getCategory(), "分类不能为空"));
        item.setName(required(request.getName(), "名称不能为空"));
        item.setDescription(normalizeText(request.getDescription()));
        item.setStudentExplanation(normalizeText(request.getStudentExplanation()));
        item.setTeacherExplanation(normalizeText(request.getTeacherExplanation()));
        item.setSkillUnitCode(normalizeCodeOrBlank(request.getSkillUnitCode()));
        item.setPrimaryKnowledgeNodeCode(primaryKnowledgeNodeCode);
        item.setMistakeType(normalizeText(request.getMistakeType()));
        item.setCommonMisconception(normalizeText(request.getCommonMisconception()));
        item.setEvidenceSignals(join(request.getEvidenceSignals()));
        item.setCommonCodePatterns(join(request.getCommonCodePatterns()));
        item.setJudgeSignals(join(request.getJudgeSignals()));
        item.setRequiredEvidence(join(request.getRequiredEvidence()));
        item.setWhenToUse(normalizeText(request.getWhenToUse()));
        item.setStudentBenefit(normalizeText(request.getStudentBenefit()));
        item.setHintL1(normalizeText(request.getHintL1()));
        item.setHintL2(normalizeText(request.getHintL2()));
        item.setHintL3(normalizeText(request.getHintL3()));
        item.setAbilityPoint(normalizeText(request.getAbilityPoint()));
        item.setSeverity(normalizeText(request.getSeverity()));
        item.setApplicableLanguages(join(request.getApplicableLanguages()));
        item.setRelatedItems(join(request.getRelatedItems()));
        item.setKnowledgeNodeCodes(knowledgeNodeCodes.isBlank() && !primaryKnowledgeNodeCode.isBlank()
                ? primaryKnowledgeNodeCode
                : knowledgeNodeCodes);
        item.setRelatedKnowledgeNodeCodes(join(request.getRelatedKnowledgeNodeCodes()).isBlank()
                ? relatedKnowledgeNodeCodes(primaryKnowledgeNodeCode, item.getKnowledgeNodeCodes())
                : join(request.getRelatedKnowledgeNodeCodes()));
        item.setPrerequisiteKnowledgeCodes(join(request.getPrerequisiteKnowledgeCodes()));
        item.setTeachingAction(normalizeText(request.getTeachingAction()));
        item.setLibraryVersion(normalizeText(request.getLibraryVersion()).isBlank()
                ? "standard-library-db-v3"
                : normalizeText(request.getLibraryVersion()));
        item.setEnabled(request.getEnabled() == null || request.getEnabled());
        validateKnowledgeItem(item);
    }

    private void validateKnowledgeItem(AiStandardLibraryItem item) {
        if (item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT) {
            requireText(item.getDescription(), "能力点定义不能为空");
            requireText(item.getStudentExplanation(), "能力点学习目标不能为空");
            requireText(item.getPrimaryKnowledgeNodeCode(), "能力点必须关联至少一个知识节点，并指定一个主知识节点");
            requireLines(item.getKnowledgeNodeCodes(), "能力点必须关联至少一个知识节点");
            return;
        }
        if (item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT) {
            requireText(item.getDescription(), "易错点定义不能为空");
            requireText(item.getSkillUnitCode(), "易错点必须关联能力点");
            requireText(item.getPrimaryKnowledgeNodeCode(), "易错点必须关联至少一个知识节点，并能落到主知识节点");
            requireText(item.getMistakeType(), "易错点类型不能为空");
            requireText(item.getCommonMisconception(), "学生常见误解不能为空");
            requireLines(item.getKnowledgeNodeCodes(), "易错点必须关联至少一个知识节点");
        }
    }

    private void requireText(String value, String message) {
        if (normalizeText(value).isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireLines(String value, String message) {
        if (lines(value).isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean matchesQuery(AiStandardLibraryItem item, String query) {
        if (query.isBlank()) {
            return true;
        }
        return contains(item.getCode(), query)
                || contains(item.getName(), query)
                || contains(item.getCategory(), query)
                || contains(item.getDescription(), query)
                || contains(item.getTeacherExplanation(), query)
                || contains(item.getSkillUnitCode(), query)
                || contains(item.getPrimaryKnowledgeNodeCode(), query)
                || contains(item.getMistakeType(), query)
                || contains(item.getCommonMisconception(), query)
                || contains(item.getKnowledgeNodeCodes(), query)
                || contains(item.getRelatedKnowledgeNodeCodes(), query)
                || contains(item.getEvidenceSignals(), query)
                || contains(item.getCommonCodePatterns(), query);
    }

    private boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    private AiStandardLibraryLayer parseLayer(String value) {
        try {
            return AiStandardLibraryLayer.valueOf(required(value, "层级不能为空").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("标准库层级无效: " + value);
        }
    }

    private AiStandardLibraryLayer parseLayerOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseLayer(value);
    }

    private Boolean parseBooleanOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    private String normalizeCode(String value) {
        return required(value, "条目 ID 不能为空").trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCodeOrBlank(String value) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    private String required(String value, String message) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeText(first);
        return normalizedFirst.isBlank() ? normalizeText(second) : normalizedFirst;
    }

    private String firstLine(String value) {
        return lines(value).stream().findFirst().orElse("");
    }

    private String relatedKnowledgeNodeCodes(String primaryKnowledgeNodeCode, String knowledgeNodeCodes) {
        String primary = normalizeText(primaryKnowledgeNodeCode);
        if (primary.isBlank()) {
            return "";
        }
        return join(lines(knowledgeNodeCodes).stream()
                .filter(code -> !code.equals(primary))
                .distinct()
                .toList());
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}

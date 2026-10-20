package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardApplicationScenario;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class StandardLibraryNavigationPackBuilder {

    private final AiStandardLibraryService standardLibraryService;
    private final InformaticsKnowledgeNodeRepository knowledgeRepository;

    public StandardLibraryPack build(StandardLibraryNavigationOutput output) {
        LinkedHashSet<String> skillIds = new LinkedHashSet<>();
        LinkedHashSet<String> mistakeIds = new LinkedHashSet<>();
        LinkedHashSet<String> improvementIds = new LinkedHashSet<>();
        LinkedHashSet<String> knowledgeIds = new LinkedHashSet<>();
        for (StandardLibraryNavigationOutput.SelectedPath path : safe(output == null ? null : output.getSelectedPaths())) {
            add(knowledgeIds, path.getKnowledgeNodeCode());
            add(skillIds, path.getSkillUnitCode());
            add(mistakeIds, path.getMistakePointCode());
            add(improvementIds, path.getImprovementPointCode());
        }

        LinkedHashMap<String, AiStandardLibraryItem> selectedItems = new LinkedHashMap<>();
        skillIds.forEach(id -> addItem(selectedItems, AiStandardLibraryLayer.SKILL_UNIT, id));
        mistakeIds.forEach(id -> addItem(selectedItems, AiStandardLibraryLayer.MISTAKE_POINT, id));
        improvementIds.forEach(id -> addItem(selectedItems, AiStandardLibraryLayer.IMPROVEMENT_POINT, id));
        return buildFromSelectedItems(selectedItems, knowledgeIds, "AI_NAVIGATION",
                output == null ? "" : output.getUncertainty());
    }

    public StandardLibraryPack buildFromItems(List<AiStandardLibraryItem> seedItems,
                                              String status,
                                              String uncertainty) {
        LinkedHashMap<String, AiStandardLibraryItem> selectedItems = new LinkedHashMap<>();
        for (AiStandardLibraryItem item : safe(seedItems)) {
            if (item == null || item.getLayer() == null || normalize(item.getCode()).isBlank()) {
                continue;
            }
            selectedItems.putIfAbsent(item.getLayer().name() + "/" + item.getCode(), item);
            if (item.getLayer() != AiStandardLibraryLayer.SKILL_UNIT) {
                addItem(selectedItems, AiStandardLibraryLayer.SKILL_UNIT, item.getSkillUnitCode());
            }
        }
        return buildFromSelectedItems(selectedItems, new LinkedHashSet<>(), status, uncertainty);
    }

    private StandardLibraryPack buildFromSelectedItems(LinkedHashMap<String, AiStandardLibraryItem> selectedItems,
                                                       LinkedHashSet<String> knowledgeIds,
                                                       String status,
                                                       String uncertainty) {
        for (AiStandardLibraryItem item : selectedItems.values()) {
            add(knowledgeIds, primaryKnowledgeCode(item));
            lines(item.getKnowledgeNodeCodes()).forEach(code -> add(knowledgeIds, code));
        }

        List<AiStandardLibraryItem> items = selectedItems.values().stream().toList();
        List<StandardLibraryPack.SkillUnitOption> skillUnits = items.stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(this::toSkillUnit)
                .toList();
        List<StandardLibraryPack.MistakePointOption> mistakePoints = items.stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT
                        || item.getLayer() == AiStandardLibraryLayer.BASIC_CAUSE)
                .map(this::toMistakePoint)
                .toList();
        List<StandardLibraryPack.BasicCauseOption> basicCauses = items.stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT
                        || item.getLayer() == AiStandardLibraryLayer.BASIC_CAUSE)
                .map(this::toBasicCause)
                .toList();
        List<StandardLibraryPack.ImprovementPointOption> improvementPoints = items.stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.IMPROVEMENT_POINT)
                .map(this::toImprovementPoint)
                .toList();
        List<StandardLibraryPack.KnowledgeAnchorOption> anchors = knowledgeIds.stream()
                .map(this::toKnowledgeAnchor)
                .filter(anchor -> anchor != null)
                .limit(12)
                .toList();
        LinkedHashSet<String> selectedSkillCodes = new LinkedHashSet<>();
        items.stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibraryItem::getCode)
                .forEach(code -> add(selectedSkillCodes, code));
        items.stream()
                .map(AiStandardLibraryItem::getSkillUnitCode)
                .forEach(code -> add(selectedSkillCodes, code));
        List<StandardLibraryPack.ApplicationScenarioOption> applicationScenarios =
                relevantApplicationScenarios(selectedSkillCodes, knowledgeIds).stream()
                        .map(this::toApplicationScenario)
                        .toList();

        return StandardLibraryPack.builder()
                .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .structureVersion(StandardLibraryPack.STRUCTURE_VERSION)
                .knowledgeGroups(buildKnowledgeGroups(
                        items, skillUnits, mistakePoints, improvementPoints, applicationScenarios))
                .basicCauses(basicCauses)
                .improvementPoints(improvementPoints)
                .knowledgeAnchors(anchors)
                .skillUnits(skillUnits)
                .mistakePoints(mistakePoints)
                .applicationScenarios(applicationScenarios)
                .standardLibraryNavigationSummary(StandardLibraryPack.StandardLibraryNavigationSummary.builder()
                        .status(firstNonBlank(status, "LOCAL_RECALL"))
                        .failureReason("")
                        .selectedCount(items.size() + anchors.size())
                        .uncertainty(firstNonBlank(uncertainty, ""))
                        .build())
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .improvementTags(List.of())
                .teachingActions(List.of())
                .build();
    }

    private void addItem(Map<String, AiStandardLibraryItem> items, AiStandardLibraryLayer layer, String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return;
        }
        standardLibraryService.findFormalItemAsLegacy(layer, normalized)
                .ifPresent(item -> items.putIfAbsent(layer.name() + "/" + item.getCode(), item));
    }

    private List<StandardLibraryPack.KnowledgeGroupOption> buildKnowledgeGroups(
            List<AiStandardLibraryItem> selectedItems,
            List<StandardLibraryPack.SkillUnitOption> skillUnits,
            List<StandardLibraryPack.MistakePointOption> mistakePoints,
            List<StandardLibraryPack.ImprovementPointOption> improvementPoints,
            List<StandardLibraryPack.ApplicationScenarioOption> applicationScenarios) {
        LinkedHashMap<String, List<AiStandardLibraryItem>> byKnowledge = new LinkedHashMap<>();
        for (AiStandardLibraryItem item : selectedItems) {
            String code = firstNonBlank(primaryKnowledgeCode(item), firstNonBlank(item.getCategory(), "UNMAPPED"));
            byKnowledge.computeIfAbsent(code, ignored -> new java.util.ArrayList<>()).add(item);
        }
        return byKnowledge.entrySet().stream()
                .limit(10)
                .map(entry -> toKnowledgeGroup(
                        entry.getKey(), entry.getValue(), skillUnits, mistakePoints,
                        improvementPoints, applicationScenarios))
                .toList();
    }

    private StandardLibraryPack.KnowledgeGroupOption toKnowledgeGroup(
            String knowledgeCode,
            List<AiStandardLibraryItem> items,
            List<StandardLibraryPack.SkillUnitOption> allSkills,
            List<StandardLibraryPack.MistakePointOption> allMistakes,
            List<StandardLibraryPack.ImprovementPointOption> allImprovements,
            List<StandardLibraryPack.ApplicationScenarioOption> allScenarios) {
        LinkedHashSet<String> skillIds = new LinkedHashSet<>();
        for (AiStandardLibraryItem item : items) {
            if (item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT) {
                add(skillIds, item.getCode());
            }
            add(skillIds, item.getSkillUnitCode());
        }
        List<StandardLibraryPack.SkillUnitGroupOption> skillGroups = skillIds.stream()
                .map(skillId -> toSkillGroup(
                        skillId, allSkills, allMistakes, allImprovements, allScenarios))
                .filter(group -> group.getSkillUnit() != null
                        || !safe(group.getMistakePoints()).isEmpty()
                        || !safe(group.getImprovementPoints()).isEmpty())
                .limit(8)
                .toList();
        KnowledgeNodeDisplay display = knowledgeNodeDisplay(knowledgeCode);
        return StandardLibraryPack.KnowledgeGroupOption.builder()
                .id(knowledgeCode)
                .name(display.name())
                .path(display.path())
                .description(firstNonBlank(display.description(), "AI 导航选中的知识点。"))
                .skillUnits(skillGroups)
                .improvementPoints(List.of())
                .build();
    }

    private StandardLibraryPack.SkillUnitGroupOption toSkillGroup(
            String skillId,
            List<StandardLibraryPack.SkillUnitOption> allSkills,
            List<StandardLibraryPack.MistakePointOption> allMistakes,
            List<StandardLibraryPack.ImprovementPointOption> allImprovements,
            List<StandardLibraryPack.ApplicationScenarioOption> allScenarios) {
        StandardLibraryPack.SkillUnitOption skill = allSkills.stream()
                .filter(item -> skillId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        List<StandardLibraryPack.MistakePointOption> mistakes = allMistakes.stream()
                .filter(item -> skillId.equals(item.getSkillUnitCode()))
                .limit(8)
                .toList();
        List<StandardLibraryPack.ImprovementPointOption> improvements = allImprovements.stream()
                .filter(item -> skillId.equals(item.getAbilityPoint()))
                .limit(5)
                .toList();
        List<StandardLibraryPack.ApplicationScenarioOption> scenarios = allScenarios.stream()
                .filter(item -> skillId.equals(item.getSkillUnitCode()))
                .limit(2)
                .toList();
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        if (skill != null) {
            candidateIds.add(skill.getId());
        }
        mistakes.stream().map(StandardLibraryPack.MistakePointOption::getId).forEach(candidateIds::add);
        improvements.stream().map(StandardLibraryPack.ImprovementPointOption::getId).forEach(candidateIds::add);
        return StandardLibraryPack.SkillUnitGroupOption.builder()
                .skillUnit(skill)
                .mistakePoints(mistakes)
                .improvementPoints(improvements)
                .applicationScenarios(scenarios)
                .candidateIds(candidateIds.stream().toList())
                .build();
    }

    private List<AiStandardApplicationScenario> relevantApplicationScenarios(
            Set<String> skillCodes,
            Set<String> knowledgeCodes) {
        try {
            return standardLibraryService.findRelevantApplicationScenarios(
                    skillCodes, knowledgeCodes, 12).stream()
                    .limit(12)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Application scenarios unavailable; continuing with the selected standard library items: {}",
                    exception.getMessage());
            return List.of();
        }
    }

    private StandardLibraryPack.ApplicationScenarioOption toApplicationScenario(
            AiStandardApplicationScenario item) {
        return StandardLibraryPack.ApplicationScenarioOption.builder()
                .id(item.getCode())
                .transferPairCode(item.getTransferPairCode())
                .contextType(item.getContextType())
                .learningPhase(item.getLearningPhase())
                .title(item.getTitle())
                .knowledgePointCode(item.getKnowledgePointCode())
                .skillUnitCode(item.getSkillUnitCode())
                .linkedMistakeCodes(lines(item.getLinkedMistakeCodes()))
                .linkedImprovementCodes(lines(item.getLinkedImprovementCodes()))
                .taskContext(item.getTaskContext())
                .studentTask(item.getStudentTask())
                .observableEvidence(item.getObservableEvidence())
                .commonFailure(item.getCommonFailure())
                .teacherMove(item.getTeacherMove())
                .studentCheck(item.getStudentCheck())
                .constraintProfile(item.getConstraintProfile())
                .successCriteria(item.getSuccessCriteria())
                .transferNote(item.getTransferNote())
                .difficultyLevel(item.getDifficultyLevel())
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .sourceFramework(item.getSourceFramework())
                .reviewStatus(item.getReviewStatus())
                .build();
    }

    private StandardLibraryPack.BasicCauseOption toBasicCause(AiStandardLibraryItem item) {
        return StandardLibraryPack.BasicCauseOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation(item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT
                        ? item.getCommonMisconception()
                        : item.getStudentExplanation())
                .teacherExplanation(item.getTeacherExplanation())
                .evidenceSignals(lines(item.getEvidenceSignals()))
                .commonCodePatterns(lines(item.getCommonCodePatterns()))
                .judgeSignals(lines(item.getJudgeSignals()))
                .hintL1(item.getHintL1())
                .hintL2(item.getHintL2())
                .hintL3(item.getHintL3())
                .abilityPoint(firstNonBlank(item.getSkillUnitCode(), item.getAbilityPoint()))
                .severity(item.getSeverity())
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .relatedFineTags(lines(item.getKnowledgeNodeCodes()))
                .teachingAction(item.getTeachingAction())
                .build();
    }

    private StandardLibraryPack.ImprovementPointOption toImprovementPoint(AiStandardLibraryItem item) {
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
                .abilityPoint(firstNonBlank(item.getSkillUnitCode(), item.getAbilityPoint()))
                .relatedBasicCauses(lines(item.getRelatedItems()))
                .build();
    }

    private StandardLibraryPack.SkillUnitOption toSkillUnit(AiStandardLibraryItem item) {
        return StandardLibraryPack.SkillUnitOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .primaryKnowledgeNodeCode(primaryKnowledgeCode(item))
                .knowledgeNodeCodes(lines(item.getKnowledgeNodeCodes()))
                .relatedKnowledgeNodeCodes(relatedKnowledgeCodes(item))
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .build();
    }

    private StandardLibraryPack.MistakePointOption toMistakePoint(AiStandardLibraryItem item) {
        return StandardLibraryPack.MistakePointOption.builder()
                .id(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .skillUnitCode(item.getSkillUnitCode())
                .primaryKnowledgeNodeCode(primaryKnowledgeCode(item))
                .mistakeType(item.getMistakeType())
                .commonMisconception(item.getCommonMisconception())
                .knowledgeNodeCodes(lines(item.getKnowledgeNodeCodes()))
                .relatedKnowledgeNodeCodes(relatedKnowledgeCodes(item))
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .build();
    }

    private StandardLibraryPack.KnowledgeAnchorOption toKnowledgeAnchor(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return null;
        }
        KnowledgeNodeDisplay display = knowledgeNodeDisplay(normalized);
        return StandardLibraryPack.KnowledgeAnchorOption.builder()
                .id(normalized)
                .name(display.name())
                .path(display.path())
                .description(firstNonBlank(display.description(), "AI 导航选中的知识树节点。"))
                .build();
    }

    private KnowledgeNodeDisplay knowledgeNodeDisplay(String code) {
        Optional<InformaticsKnowledgeNode> node = knowledgeRepository.findByCode(code);
        return node.map(value -> new KnowledgeNodeDisplay(
                        firstNonBlank(value.getName(), code),
                        firstNonBlank(value.getPath(), code),
                        text(value.getDescription())))
                .orElseGet(() -> new KnowledgeNodeDisplay(code, code.replace(".", " > "), ""));
    }

    private String primaryKnowledgeCode(AiStandardLibraryItem item) {
        String primary = text(item.getPrimaryKnowledgeNodeCode());
        if (!primary.isBlank()) {
            return primary;
        }
        return lines(item.getKnowledgeNodeCodes()).stream().findFirst().orElse("");
    }

    private List<String> relatedKnowledgeCodes(AiStandardLibraryItem item) {
        List<String> explicit = lines(item.getRelatedKnowledgeNodeCodes());
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String primary = primaryKnowledgeCode(item);
        return lines(item.getKnowledgeNodeCodes()).stream()
                .filter(code -> !code.equals(primary))
                .distinct()
                .toList();
    }

    private void add(LinkedHashSet<String> ids, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            ids.add(normalized);
        }
    }

    private <T> List<T> safe(List<T> source) {
        return source == null ? List.of() : source;
    }

    private List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String left = text(first);
        return left.isBlank() ? text(second) : left;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record KnowledgeNodeDisplay(String name, String path, String description) {
    }
}

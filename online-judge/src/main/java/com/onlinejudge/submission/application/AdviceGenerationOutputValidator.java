package com.onlinejudge.submission.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AdviceGenerationOutputValidator {

    private static final int MAX_BASIC_REPORT_LENGTH = 360;
    private static final int MAX_IMPROVEMENT_REPORT_LENGTH = 300;
    private static final int MAX_NEXT_ACTION_LENGTH = 220;
    private static final Pattern NUMERIC_ARRAY = Pattern.compile("\\[[0-9]+(?:\\s*,\\s*[0-9]+)+\\]");

    public ExternalModelStagePayloads.StageValidationResult validate(AdviceGenerationOutput output,
                                                                     ModelDiagnosisBrief brief,
                                                                     StandardLibraryPack standardLibraryPack) {
        if (output == null) {
            return invalid(ModelStageFailureReason.EMPTY_RESPONSE, "Advice generation output is empty.");
        }
        if (hasStudentReport(output)) {
            return validateDiagnosisReportV2(output, brief, standardLibraryPack);
        }
        if (output.getCaseUnderstanding() == null) {
            return invalid(ModelStageFailureReason.INVALID_JSON, "caseUnderstanding is missing.");
        }
        Set<String> evidenceRefs = evidenceRefs(brief);
        List<String> orderedEvidenceRefs = EvidenceRefSupport.orderedEvidenceRefs(brief);
        if (!isAccepted(brief) && safe(output.getBasicLayerAdvice()).isEmpty()) {
            return invalid(ModelStageFailureReason.INVALID_JSON, "Non-accepted submission requires basicLayerAdvice.");
        }
        if (safe(output.getNextStepPlan()).isEmpty()) {
            return invalid(ModelStageFailureReason.INVALID_JSON, "nextStepPlan is empty.");
        }
        String primaryRef = output.getCaseUnderstanding().getPrimaryEvidenceRef();
        String normalizedPrimaryRef = EvidenceRefSupport.normalizeEvidenceRef(primaryRef, brief);
        if (normalizedPrimaryRef.isBlank() || !EvidenceRefSupport.isValidEvidenceRef(normalizedPrimaryRef, brief)) {
            return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF,
                    "caseUnderstanding.primaryEvidenceRef is invalid: " + primaryRef);
        }
        if (!normalizedPrimaryRef.equals(primaryRef)) {
            output.getCaseUnderstanding().setPrimaryEvidenceRef(normalizedPrimaryRef);
        }
        Set<String> mistakeIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getMistakePoints());
        mistakeIds.addAll(ids(standardLibraryPack == null ? null : standardLibraryPack.getBasicCauses()));
        Set<String> skillIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getSkillUnits());
        Set<String> improvementIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getImprovementPoints());
        List<String> softFixes = new java.util.ArrayList<>();

        for (AdviceGenerationOutput.BasicLayerAdvice item : safe(output.getBasicLayerAdvice())) {
            if (item == null) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "basicLayerAdvice contains null item.");
            }
            if (blank(item.getTitle()) || blank(item.getStudentAction()) || blank(item.getCheckQuestion())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "basicLayerAdvice is missing title/action/question.");
            }
            clearUnknownBasicAdviceIds(item, mistakeIds, skillIds, softFixes);
            item.setEvidenceRefs(normalizeEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, orderedEvidenceRefs,
                    brief, softFixes));
            String invalidEvidence = invalidEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, brief,
                    "basicLayerAdvice.evidenceRefs");
            if (!invalidEvidence.isBlank()) {
                return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidEvidence);
            }
            if (invalidConfidence(item.getConfidence())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "basicLayerAdvice confidence is invalid.");
            }
            recordAnswerLeakRisk(softFixes, "basicLayerAdvice", item.getTitle(), item.getWhatHappened(), item.getWhyItMatters(),
                    item.getStudentAction(), item.getCheckQuestion());
        }

        for (AdviceGenerationOutput.ImprovementLayerAdvice item : safe(output.getImprovementLayerAdvice())) {
            if (item == null) {
                continue;
            }
            if (blank(item.getTitle()) || blank(item.getSuggestion()) || blank(item.getStudentBenefit())) {
                return invalid(ModelStageFailureReason.INVALID_JSON,
                        "improvementLayerAdvice is missing title/suggestion/benefit.");
            }
            clearUnknownImprovementAdviceIds(item, improvementIds, skillIds, softFixes);
            item.setEvidenceRefs(normalizeEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, orderedEvidenceRefs,
                    brief, softFixes));
            String invalidEvidence = invalidEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, brief,
                    "improvementLayerAdvice.evidenceRefs", false);
            if (!invalidEvidence.isBlank()) {
                return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidEvidence);
            }
            if (invalidConfidence(item.getConfidence())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "improvementLayerAdvice confidence is invalid.");
            }
            recordAnswerLeakRisk(softFixes, "improvementLayerAdvice", item.getTitle(), item.getCurrentLimit(),
                    item.getSuggestion(), item.getStudentBenefit());
        }

        for (AdviceGenerationOutput.NextStepAdvice item : safe(output.getNextStepPlan())) {
            if (item == null || blank(item.getTarget()) || blank(item.getReason())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "nextStepPlan item is incomplete.");
            }
            String normalizedEvidenceRef = EvidenceRefSupport.normalizeEvidenceRef(item.getEvidenceRef(), brief);
            if (!blank(item.getEvidenceRef()) && !EvidenceRefSupport.isValidEvidenceRef(normalizedEvidenceRef, brief)) {
                return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF,
                        "nextStepPlan evidenceRef is invalid: " + item.getEvidenceRef());
            }
            if (!blank(normalizedEvidenceRef)) {
                item.setEvidenceRef(normalizedEvidenceRef);
            }
            recordAnswerLeakRisk(softFixes, "nextStepPlan", item.getTarget(), item.getReason());
        }
        recordAnswerLeakRisk(softFixes, "caseUnderstanding/studentSummary", output.getStudentSummary(),
                output.getCaseUnderstanding().getProblemGoal(),
                output.getCaseUnderstanding().getCodeIntent(),
                output.getCaseUnderstanding().getBehaviorGap());
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(true)
                .stage("DIAGNOSIS_AND_ADVICE")
                .failureReason(ModelStageFailureReason.NONE)
                .message("")
                .softFixes(softFixes)
                .hardFailures(List.of())
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult validateDiagnosisReportV2(
            AdviceGenerationOutput output,
            ModelDiagnosisBrief brief,
            StandardLibraryPack standardLibraryPack
    ) {
        List<String> softFixes = new java.util.ArrayList<>();
        AdviceGenerationOutput.StudentReport report = output.getStudentReport();
        if (!isAccepted(brief) && blank(report.getBasicLayerText())) {
            return invalid(ModelStageFailureReason.INVALID_JSON,
                    "Non-accepted submission requires studentReport.basicLayerText.");
        }
        if (blank(report.getNextActionText())) {
            return invalid(ModelStageFailureReason.INVALID_JSON, "studentReport.nextActionText is empty.");
        }
        String hintLevel = report.getHintLevel();
        if (!blank(hintLevel) && !List.of("L1", "L2", "L3", "L4").contains(hintLevel.trim().toUpperCase(Locale.ROOT))) {
            return invalid(ModelStageFailureReason.INVALID_JSON, "studentReport.hintLevel is invalid.");
        }
        recordAnswerLeakRisk(softFixes, "studentReport", report.getBasicLayerText(),
                report.getImprovementLayerText(), report.getNextActionText(), output.getStudentSummary());
        if (containsUnsupportedNumericArray(brief,
                report.getBasicLayerText(),
                report.getImprovementLayerText(),
                report.getNextActionText(),
                output.getStudentSummary())) {
            return invalid(ModelStageFailureReason.INVALID_JSON,
                    "studentReport contains numeric example not present in visible input.");
        }
        trimStudentReport(report, softFixes);

        Set<String> evidenceRefs = evidenceRefs(brief);
        List<String> orderedEvidenceRefs = EvidenceRefSupport.orderedEvidenceRefs(brief);
        Set<String> allowedIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getMistakePoints());
        allowedIds.addAll(ids(standardLibraryPack == null ? null : standardLibraryPack.getBasicCauses()));
        allowedIds.addAll(ids(standardLibraryPack == null ? null : standardLibraryPack.getSkillUnits()));
        allowedIds.addAll(ids(standardLibraryPack == null ? null : standardLibraryPack.getImprovementPoints()));
        allowedIds.addAll(ids(standardLibraryPack == null ? null : standardLibraryPack.getKnowledgeAnchors()));
        Set<String> mistakeIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getMistakePoints());
        mistakeIds.addAll(ids(standardLibraryPack == null ? null : standardLibraryPack.getBasicCauses()));
        Set<String> skillIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getSkillUnits());
        Set<String> improvementIds = ids(standardLibraryPack == null ? null : standardLibraryPack.getImprovementPoints());
        Map<String, String> standardLibraryAliases = standardLibraryAliases(standardLibraryPack);

        sanitizeDiagnosisCandidates(output, allowedIds, evidenceRefs, orderedEvidenceRefs, brief, softFixes);
        sanitizeLibraryGrowth(output, evidenceRefs, orderedEvidenceRefs, brief, softFixes);
        ExternalModelStagePayloads.StageValidationResult adviceItemsFailure = validateReportAdviceItems(
                output,
                evidenceRefs,
                orderedEvidenceRefs,
                brief,
                mistakeIds,
                skillIds,
                improvementIds,
                softFixes
        );
        if (adviceItemsFailure != null) {
            return adviceItemsFailure;
        }

        AdviceGenerationOutput.DiagnosisDecision decision = output.getDiagnosisDecision();
        if (decision != null) {
            String fit = normalizeLibraryFit(decision.getLibraryFit());
            if (!fit.equals(normalize(decision.getLibraryFit()))) {
                softFixes.add("diagnosisDecision.libraryFit normalized: " + decision.getLibraryFit() + " -> " + fit);
                decision.setLibraryFit(fit);
            }
            if (!fit.isBlank() && !List.of("HIT", "PARTIAL", "MISS").contains(fit)) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "diagnosisDecision.libraryFit is invalid.");
            }
            boolean hasKnownAnchor = false;
            for (AdviceGenerationOutput.DiagnosisAnchor anchor : safe(decision.getAnchors())) {
                if (anchor == null) {
                    return invalid(ModelStageFailureReason.INVALID_JSON, "diagnosisDecision.anchors contains null item.");
                }
                String type = normalize(anchor.getType());
                boolean outOfLibrary = "OUT_OF_LIBRARY".equals(type);
                String anchorId = normalize(anchor.getId());
                if (outOfLibrary && !anchorId.isBlank()) {
                    anchor.setId(null);
                    softFixes.add("diagnosisDecision anchor id cleared for OUT_OF_LIBRARY: " + anchorId);
                    anchorId = "";
                }
                if (!outOfLibrary && !anchorId.isBlank() && !allowedIds.contains(anchorId)) {
                    String resolvedId = resolveStandardLibraryId(anchor.getId(), anchor.getType(),
                            standardLibraryAliases, allowedIds);
                    if (!resolvedId.isBlank()) {
                        anchor.setId(resolvedId);
                        softFixes.add("diagnosisDecision anchor id normalized: " + anchorId + " -> " + resolvedId);
                        anchorId = resolvedId;
                    } else {
                        anchor.setId(null);
                        anchor.setType("OUT_OF_LIBRARY");
                        outOfLibrary = true;
                        softFixes.add("diagnosisDecision anchor converted to OUT_OF_LIBRARY: " + anchorId);
                        anchorId = "";
                    }
                }
                if (!outOfLibrary && !anchorId.isBlank()) {
                    hasKnownAnchor = true;
                }
                if ("MISS".equals(fit) && !outOfLibrary && !anchorId.isBlank()) {
                    fit = "PARTIAL";
                    decision.setLibraryFit(fit);
                    softFixes.add("diagnosisDecision.libraryFit normalized: MISS -> PARTIAL because known anchor exists");
                }
                if (safe(anchor.getEvidenceRefs()).isEmpty()) {
                    List<String> fallbackEvidenceRefs = fallbackEvidenceRefs(output, evidenceRefs, orderedEvidenceRefs, brief, softFixes);
                    if (!fallbackEvidenceRefs.isEmpty()) {
                        anchor.setEvidenceRefs(fallbackEvidenceRefs);
                        softFixes.add("diagnosisDecision anchor evidenceRefs filled from primary evidence");
                    }
                }
                anchor.setEvidenceRefs(normalizeEvidenceRefs(anchor.getEvidenceRefs(), evidenceRefs, orderedEvidenceRefs, brief, softFixes));
                String invalidEvidence = invalidEvidenceRefs(anchor.getEvidenceRefs(), evidenceRefs, brief,
                        "diagnosisDecision.anchors.evidenceRefs");
                if (!invalidEvidence.isBlank()) {
                    return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidEvidence);
                }
                if (invalidConfidence(anchor.getConfidence())) {
                    return invalid(ModelStageFailureReason.INVALID_JSON, "diagnosisDecision anchor confidence is invalid.");
                }
                recordAnswerLeakRisk(softFixes, "diagnosisDecision anchor", anchor.getReason());
            }
            if ("HIT".equals(fit) && !hasKnownAnchor) {
                fit = "PARTIAL";
                decision.setLibraryFit(fit);
                softFixes.add("diagnosisDecision.libraryFit downgraded HIT -> PARTIAL because no known anchor remained");
            }
            List<AdviceGenerationOutput.OutOfLibraryFinding> validFindings = new java.util.ArrayList<>();
            for (AdviceGenerationOutput.OutOfLibraryFinding finding : safe(decision.getOutOfLibraryFindings())) {
                if (finding == null || blank(finding.getName()) || blank(finding.getReason())) {
                    softFixes.add("diagnosisDecision.outOfLibraryFinding dropped: incomplete item");
                    continue;
                }
                finding.setEvidenceRefs(normalizeEvidenceRefs(finding.getEvidenceRefs(), evidenceRefs, orderedEvidenceRefs, brief, softFixes));
                String invalidEvidence = invalidEvidenceRefs(finding.getEvidenceRefs(), evidenceRefs, brief,
                        "diagnosisDecision.outOfLibraryFindings.evidenceRefs");
                if (!invalidEvidence.isBlank()) {
                    softFixes.add("diagnosisDecision.outOfLibraryFinding dropped: " + invalidEvidence);
                    continue;
                }
                if (invalidConfidence(finding.getConfidence())) {
                    softFixes.add("diagnosisDecision.outOfLibraryFinding dropped: invalid confidence");
                    continue;
                }
                recordAnswerLeakRisk(softFixes, "diagnosisDecision.outOfLibraryFinding", finding.getName(), finding.getReason());
                validFindings.add(finding);
            }
            if (decision.getOutOfLibraryFindings() != null) {
                decision.setOutOfLibraryFindings(validFindings);
            }
        }
        if (!isAccepted(brief) && safe(output.getBasicLayerAdvice()).isEmpty()) {
            return invalid(ModelStageFailureReason.INVALID_JSON,
                    "Non-accepted submission requires basicLayerAdvice; studentReport is summary only.");
        }
        attachValidationTrace(output, softFixes, List.of());
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(true)
                .stage("DIAGNOSIS_REPORT")
                .failureReason(ModelStageFailureReason.NONE)
                .message("")
                .softFixes(softFixes)
                .hardFailures(List.of())
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult validateReportAdviceItems(
            AdviceGenerationOutput output,
            Set<String> evidenceRefs,
            List<String> orderedEvidenceRefs,
            ModelDiagnosisBrief brief,
            Set<String> mistakeIds,
            Set<String> skillIds,
            Set<String> improvementIds,
            List<String> softFixes
    ) {
        for (AdviceGenerationOutput.BasicLayerAdvice item : safe(output.getBasicLayerAdvice())) {
            if (item == null) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "basicLayerAdvice contains null item.");
            }
            if (blank(item.getTitle()) || blank(item.getStudentAction()) || blank(item.getCheckQuestion())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "basicLayerAdvice is missing title/action/question.");
            }
            clearUnknownBasicAdviceIds(item, mistakeIds, skillIds, softFixes);
            item.setEvidenceRefs(normalizeEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, orderedEvidenceRefs,
                    brief, softFixes));
            String invalidEvidence = invalidEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, brief,
                    "basicLayerAdvice.evidenceRefs");
            if (!invalidEvidence.isBlank()) {
                return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidEvidence);
            }
            if (invalidConfidence(item.getConfidence())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "basicLayerAdvice confidence is invalid.");
            }
            recordAnswerLeakRisk(softFixes, "basicLayerAdvice", item.getTitle(), item.getWhatHappened(), item.getWhyItMatters(),
                    item.getStudentAction(), item.getCheckQuestion());
        }
        for (AdviceGenerationOutput.ImprovementLayerAdvice item : safe(output.getImprovementLayerAdvice())) {
            if (item == null) {
                continue;
            }
            if (blank(item.getTitle()) || blank(item.getSuggestion()) || blank(item.getStudentBenefit())) {
                return invalid(ModelStageFailureReason.INVALID_JSON,
                        "improvementLayerAdvice is missing title/suggestion/benefit.");
            }
            clearUnknownImprovementAdviceIds(item, improvementIds, skillIds, softFixes);
            item.setEvidenceRefs(normalizeEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, orderedEvidenceRefs,
                    brief, softFixes));
            String invalidEvidence = invalidEvidenceRefs(item.getEvidenceRefs(), evidenceRefs, brief,
                    "improvementLayerAdvice.evidenceRefs", false);
            if (!invalidEvidence.isBlank()) {
                return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidEvidence);
            }
            if (invalidConfidence(item.getConfidence())) {
                return invalid(ModelStageFailureReason.INVALID_JSON, "improvementLayerAdvice confidence is invalid.");
            }
            recordAnswerLeakRisk(softFixes, "improvementLayerAdvice", item.getTitle(), item.getCurrentLimit(),
                    item.getSuggestion(), item.getStudentBenefit());
        }
        return null;
    }

    private void clearUnknownBasicAdviceIds(AdviceGenerationOutput.BasicLayerAdvice item,
                                            Set<String> mistakeIds,
                                            Set<String> skillIds,
                                            List<String> softFixes) {
        String mistakeId = normalize(item.getMistakePointId());
        if (!mistakeId.isBlank() && !mistakeIds.contains(mistakeId)) {
            item.setMistakePointId(null);
            softFixes.add("basicLayerAdvice.mistakePointId cleared: " + mistakeId);
        }
        String skillId = normalize(item.getSkillUnitId());
        if (!skillId.isBlank() && !skillIds.contains(skillId)) {
            item.setSkillUnitId(null);
            softFixes.add("basicLayerAdvice.skillUnitId cleared: " + skillId);
        }
    }

    private void clearUnknownImprovementAdviceIds(AdviceGenerationOutput.ImprovementLayerAdvice item,
                                                  Set<String> improvementIds,
                                                  Set<String> skillIds,
                                                  List<String> softFixes) {
        String improvementId = normalize(item.getImprovementPointId());
        if (!improvementId.isBlank() && !improvementIds.contains(improvementId)) {
            item.setImprovementPointId(null);
            softFixes.add("improvementLayerAdvice.improvementPointId cleared: " + improvementId);
        }
        String skillId = normalize(item.getSkillUnitId());
        if (!skillId.isBlank() && !skillIds.contains(skillId)) {
            item.setSkillUnitId(null);
            softFixes.add("improvementLayerAdvice.skillUnitId cleared: " + skillId);
        }
    }

    private void sanitizeDiagnosisCandidates(
            AdviceGenerationOutput output,
            Set<String> allowedIds,
            Set<String> evidenceRefs,
            List<String> orderedEvidenceRefs,
            ModelDiagnosisBrief brief,
            List<String> softFixes
    ) {
        List<AdviceGenerationOutput.DiagnosisCandidate> validCandidates = new java.util.ArrayList<>();
        for (AdviceGenerationOutput.DiagnosisCandidate candidate : safe(output.getDiagnosisCandidates())) {
            if (candidate == null) {
                softFixes.add("diagnosisCandidate dropped: null item");
                continue;
            }
            if (blank(candidate.getName()) || blank(candidate.getReason())) {
                softFixes.add("diagnosisCandidate dropped: incomplete item");
                continue;
            }
            String fit = normalize(candidate.getLibraryFit());
            if (fit.isBlank() || !List.of("HIT", "PARTIAL", "MISS", "OUT_OF_LIBRARY").contains(fit)) {
                softFixes.add("diagnosisCandidate dropped: invalid libraryFit " + candidate.getLibraryFit());
                continue;
            }
            if (blankPath(candidate.getLibraryPath())) {
                softFixes.add("diagnosisCandidate dropped: missing libraryPath");
                continue;
            }
            String anchorType = normalize(candidate.getAnchorType());
            String anchorId = normalize(candidate.getAnchorId());
            boolean outOfLibrary = "OUT_OF_LIBRARY".equals(fit) || "OUT_OF_LIBRARY".equals(anchorType);
            if (outOfLibrary && !anchorId.isBlank()) {
                candidate.setAnchorId(null);
                softFixes.add("diagnosisCandidate anchorId cleared for OUT_OF_LIBRARY: " + anchorId);
                anchorId = "";
            }
            if ("HIT".equals(fit) && anchorId.isBlank()) {
                softFixes.add("diagnosisCandidate dropped: HIT without anchorId");
                continue;
            }
            if ("MISS".equals(fit) && !anchorId.isBlank()) {
                candidate.setAnchorId(null);
                softFixes.add("diagnosisCandidate anchorId cleared for MISS: " + anchorId);
                anchorId = "";
            }
            if (!outOfLibrary && !anchorId.isBlank() && !allowedIds.contains(anchorId)) {
                candidate.setAnchorId(null);
                candidate.setAnchorType("OUT_OF_LIBRARY");
                candidate.setLibraryFit("OUT_OF_LIBRARY");
                softFixes.add("diagnosisCandidate converted to OUT_OF_LIBRARY: " + anchorId);
            }
            candidate.setEvidenceRefs(normalizeEvidenceRefs(candidate.getEvidenceRefs(), evidenceRefs,
                    orderedEvidenceRefs, brief, softFixes));
            String invalidEvidence = invalidEvidenceRefs(candidate.getEvidenceRefs(), evidenceRefs, brief,
                    "diagnosisCandidates.evidenceRefs");
            if (!invalidEvidence.isBlank()) {
                softFixes.add("diagnosisCandidate dropped: " + invalidEvidence);
                continue;
            }
            if (invalidConfidence(candidate.getConfidence())) {
                softFixes.add("diagnosisCandidate dropped: invalid confidence");
                continue;
            }
            recordAnswerLeakRisk(softFixes, "diagnosisCandidate",
                    candidate.getName(), candidate.getReason());
            validCandidates.add(candidate);
        }
        if (output.getDiagnosisCandidates() != null) {
            output.setDiagnosisCandidates(validCandidates);
        }
    }

    private void sanitizeLibraryGrowth(
            AdviceGenerationOutput output,
            Set<String> evidenceRefs,
            List<String> orderedEvidenceRefs,
            ModelDiagnosisBrief brief,
            List<String> softFixes
    ) {
        AdviceGenerationOutput.LibraryGrowth growth = output.getLibraryGrowth();
        if (growth == null || growth.getCandidates() == null) {
            return;
        }
        List<AdviceGenerationOutput.LibraryGrowthCandidate> validCandidates = new java.util.ArrayList<>();
        for (AdviceGenerationOutput.LibraryGrowthCandidate candidate : safe(growth.getCandidates())) {
            if (candidate == null) {
                softFixes.add("libraryGrowth candidate dropped: null item");
                continue;
            }
            if (blank(candidate.getName()) || blank(candidate.getReason()) || blankPath(candidate.getSuggestedPath())) {
                softFixes.add("libraryGrowth candidate dropped: incomplete item");
                continue;
            }
            if (blank(candidate.getErrorSymptom())
                    || blank(candidate.getTypicalCodePattern())
                    || blank(candidate.getStudentExplanation())) {
                softFixes.add("libraryGrowth candidate dropped: missing review fields");
                continue;
            }
            if (invalidConfidence(candidate.getConfidence())) {
                softFixes.add("libraryGrowth candidate dropped: invalid confidence");
                continue;
            }
            recordAnswerLeakRisk(softFixes, "libraryGrowth candidate",
                    candidate.getName(), candidate.getReason(), candidate.getErrorSymptom(),
                    candidate.getTypicalCodePattern(), candidate.getStudentExplanation());
            candidate.setEvidenceRefs(normalizeEvidenceRefs(candidate.getEvidenceRefs(), evidenceRefs,
                    orderedEvidenceRefs, brief, softFixes));
            String invalidEvidence = invalidEvidenceRefs(candidate.getEvidenceRefs(), evidenceRefs, brief,
                    "libraryGrowth.candidates.evidenceRefs", false);
            if (!invalidEvidence.isBlank()) {
                candidate.setEvidenceRefs(List.of());
                candidate.setEvidenceStatus("UNSUPPORTED");
                softFixes.add("libraryGrowth candidate evidence cleared: " + invalidEvidence);
            } else if (candidate.getEvidenceRefs() == null || candidate.getEvidenceRefs().isEmpty()) {
                candidate.setEvidenceStatus("NO_DIRECT_CODE_EVIDENCE");
            } else {
                candidate.setEvidenceStatus("SUPPORTED");
            }
            String originalStatus = candidate.getStatus();
            String status = normalize(originalStatus);
            if (!"NEEDS_REVIEW".equals(status)) {
                candidate.setStatus("NEEDS_REVIEW");
                softFixes.add("libraryGrowth candidate status normalized: " + originalStatus + " -> NEEDS_REVIEW");
            }
            validCandidates.add(candidate);
        }
        growth.setCandidates(validCandidates);
    }

    private void attachValidationTrace(AdviceGenerationOutput output, List<String> softFixes, List<String> hardFailures) {
        if (output == null) {
            return;
        }
        AdviceGenerationOutput.TeacherTrace trace = output.getTeacherTrace();
        if (trace == null) {
            trace = AdviceGenerationOutput.TeacherTrace.builder().build();
            output.setTeacherTrace(trace);
        }
        trace.setSoftFixes(softFixes == null ? List.of() : softFixes);
        trace.setHardFailures(hardFailures == null ? List.of() : hardFailures);
    }

    private boolean hasStudentReport(AdviceGenerationOutput output) {
        AdviceGenerationOutput.StudentReport report = output.getStudentReport();
        return report != null && (!blank(report.getBasicLayerText())
                || !blank(report.getImprovementLayerText())
                || !blank(report.getNextActionText()));
    }

    private Set<String> evidenceRefs(ModelDiagnosisBrief brief) {
        return EvidenceRefSupport.validEvidenceRefs(brief);
    }

    private Set<String> ids(List<?> values) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (values == null) {
            return ids;
        }
        for (Object value : values) {
            if (value instanceof StandardLibraryPack.MistakePointOption option) {
                ids.add(normalize(option.getId()));
            } else if (value instanceof StandardLibraryPack.BasicCauseOption option) {
                ids.add(normalize(option.getId()));
            } else if (value instanceof StandardLibraryPack.SkillUnitOption option) {
                ids.add(normalize(option.getId()));
            } else if (value instanceof StandardLibraryPack.ImprovementPointOption option) {
                ids.add(normalize(option.getId()));
            } else if (value instanceof StandardLibraryPack.KnowledgeAnchorOption option) {
                ids.add(normalize(option.getId()));
            }
        }
        ids.remove("");
        return ids;
    }

    private Map<String, String> standardLibraryAliases(StandardLibraryPack pack) {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        if (pack == null) {
            return aliases;
        }
        addAliases(aliases, pack.getMistakePoints());
        addAliases(aliases, pack.getBasicCauses());
        addAliases(aliases, pack.getSkillUnits());
        addAliases(aliases, pack.getImprovementPoints());
        addAliases(aliases, pack.getKnowledgeAnchors());
        return aliases;
    }

    private void addAliases(Map<String, String> aliases, List<?> values) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (value instanceof StandardLibraryPack.MistakePointOption option) {
                addAlias(aliases, option.getId(), option.getId());
                addAlias(aliases, option.getName(), option.getId());
                addAlias(aliases, option.getCategory(), option.getId());
                addAlias(aliases, option.getDescription(), option.getId());
                addAlias(aliases, option.getMistakeType(), option.getId());
                addAlias(aliases, option.getCommonMisconception(), option.getId());
            } else if (value instanceof StandardLibraryPack.BasicCauseOption option) {
                addAlias(aliases, option.getId(), option.getId());
                addAlias(aliases, option.getName(), option.getId());
                addAlias(aliases, option.getCategory(), option.getId());
                addAlias(aliases, option.getDescription(), option.getId());
                addAlias(aliases, option.getAbilityPoint(), option.getId());
            } else if (value instanceof StandardLibraryPack.SkillUnitOption option) {
                addAlias(aliases, option.getId(), option.getId());
                addAlias(aliases, option.getName(), option.getId());
                addAlias(aliases, option.getCategory(), option.getId());
                addAlias(aliases, option.getDescription(), option.getId());
            } else if (value instanceof StandardLibraryPack.ImprovementPointOption option) {
                addAlias(aliases, option.getId(), option.getId());
                addAlias(aliases, option.getName(), option.getId());
                addAlias(aliases, option.getCategory(), option.getId());
                addAlias(aliases, option.getDescription(), option.getId());
                addAlias(aliases, option.getAbilityPoint(), option.getId());
            } else if (value instanceof StandardLibraryPack.KnowledgeAnchorOption option) {
                addAlias(aliases, option.getId(), option.getId());
                addAlias(aliases, option.getName(), option.getId());
                addAlias(aliases, option.getPath(), option.getId());
                addAlias(aliases, option.getDescription(), option.getId());
            }
        }
    }

    private void addAlias(Map<String, String> aliases, String alias, String id) {
        String aliasKey = normalizeAlias(alias);
        String normalizedId = normalize(id);
        if (!aliasKey.isBlank() && !normalizedId.isBlank()) {
            aliases.putIfAbsent(aliasKey, normalizedId);
        }
    }

    private String resolveStandardLibraryId(String rawId,
                                            String rawType,
                                            Map<String, String> aliases,
                                            Set<String> allowedIds) {
        String normalizedId = normalize(rawId);
        List<String> preferredPrefixes = preferredAnchorPrefixes(rawType);
        if (normalizedId.isBlank()) {
            return "";
        }
        if (allowedIds.contains(normalizedId)) {
            return normalizedId;
        }
        List<String> candidates = new java.util.ArrayList<>();
        String exactAlias = aliases.get(normalizeAlias(rawId));
        if (exactAlias != null && allowedIds.contains(exactAlias)) {
            candidates.add(exactAlias);
        }
        for (String hint : anchorAliasHints(rawId)) {
            String resolved = aliases.get(normalizeAlias(hint));
            if (resolved != null && allowedIds.contains(resolved)) {
                candidates.add(resolved);
            }
        }
        for (String hint : anchorAliasHints(rawId)) {
            String hintKey = normalizeAlias(hint);
            if (hintKey.length() < 4) {
                continue;
            }
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (allowedIds.contains(entry.getValue())
                        && (entry.getKey().contains(hintKey) || hintKey.contains(entry.getKey()))) {
                    candidates.add(entry.getValue());
                }
            }
        }
        return choosePreferredCandidate(candidates, preferredPrefixes);
    }

    private List<String> preferredAnchorPrefixes(String rawType) {
        String type = normalize(rawType);
        if ("MISTAKE_POINT".equals(type) || "BASIC_CAUSE".equals(type)) {
            return List.of("MP_", "BC_");
        }
        if ("SKILL_UNIT".equals(type)) {
            return List.of("SK_");
        }
        if ("IMPROVEMENT_POINT".equals(type)) {
            return List.of("IP_");
        }
        if ("KNOWLEDGE_NODE".equals(type) || "KNOWLEDGE_ANCHOR".equals(type)) {
            return List.of("KN_", "KA_");
        }
        return List.of();
    }

    private String choosePreferredCandidate(List<String> candidates, List<String> preferredPrefixes) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>(candidates);
        for (String prefix : preferredPrefixes) {
            for (String candidate : deduped) {
                if (candidate.startsWith(prefix)) {
                    return candidate;
                }
            }
        }
        return deduped.iterator().next();
    }

    private List<String> anchorAliasHints(String rawId) {
        String key = normalizeAlias(rawId);
        if (key.contains("DPSTATE") || key.contains("DYNAMICPROGRAMMINGSTATE")) {
            return List.of("动态规划状态设计", "DP状态设计", "状态定义", "状态含义", "DP_STATE_DESIGN", "DP_STATE_DEFINITION");
        }
        if (key.contains("RECURSIONBASE") || key.contains("RECURSIONEXIT") || key.contains("BASECASE")) {
            return List.of("递归出口", "递归终止条件", "递归边界", "RECURSION_BASE_CASE", "RECURSION_EXIT");
        }
        if (key.contains("INTEGEROVERFLOW") || key.contains("OVERFLOW")) {
            return List.of("整数溢出", "取模前溢出", "数据范围溢出", "INTEGER_OVERFLOW");
        }
        if (key.contains("BINARYSEARCH") || key.contains("DICHOTOMY")) {
            return List.of("二分边界", "二分查找边界", "左右边界更新", "BINARY_SEARCH_BOUNDARY");
        }
        if (key.contains("GAMESTATE") || key.contains("GAMEDP")) {
            return List.of("博弈状态定义", "游戏状态定义", "博弈DP状态", "GAME_STATE_DEFINITION");
        }
        if (key.contains("MATCHINGVISITED") || key.contains("AUGMENTVISITED")) {
            return List.of("匹配访问标记", "增广路访问标记", "visited重置", "MATCHING_VISITED_SCOPE");
        }
        if (key.contains("STALEQUEUE") || key.contains("STALEHEAP") || key.contains("DIJKSTRA")) {
            return List.of("堆中旧状态", "优先队列旧状态", "Dijkstra旧状态", "STALE_QUEUE_ENTRY");
        }
        if (key.contains("BOUNDARY") || key.contains("OFFBYONE")) {
            return List.of("边界条件", "边界处理", "循环边界", "数组边界", "BOUNDARY_CONDITION");
        }
        if (key.contains("GRAPHMODEL")) {
            return List.of("图建模", "建图", "边权建模", "GRAPH_MODELING");
        }
        if (key.contains("TOPOLOGICAL") || key.contains("TOPO")) {
            return List.of("拓扑排序", "拓扑顺序", "TOPOLOGICAL_ORDER");
        }
        if (key.contains("MONOTONICQUEUE") || key.contains("SLIDINGWINDOW")) {
            return List.of("单调队列窗口", "滑动窗口边界", "MONOTONIC_QUEUE_WINDOW");
        }
        return List.of(rawId);
    }

    private String normalizeLibraryFit(String value) {
        String normalized = normalize(value);
        if (List.of("HIT", "PARTIAL", "MISS").contains(normalized)) {
            return normalized;
        }
        if (List.of("OUT_OF_LIBRARY", "OUTOFLIBRARY", "NO_MATCH", "NOMATCH", "NOT_FOUND",
                "NOTFOUND", "UNKNOWN", "LIBRARY_GAP", "LIBRARYGAP").contains(normalized)) {
            return "MISS";
        }
        if (List.of("PARTIAL_MATCH", "PARTIALMATCH", "PARTIALLY_MATCHED", "PARTIALLYMATCHED").contains(normalized)) {
            return "PARTIAL";
        }
        if (List.of("MATCH", "MATCHED", "FOUND", "IN_LIBRARY", "INLIBRARY").contains(normalized)) {
            return "HIT";
        }
        return normalized;
    }

    private String invalidEvidenceRefs(List<String> refs, Set<String> validRefs, ModelDiagnosisBrief brief, String field) {
        return invalidEvidenceRefs(refs, validRefs, brief, field, true);
    }

    private String invalidEvidenceRefs(List<String> refs,
                                       Set<String> validRefs,
                                       ModelDiagnosisBrief brief,
                                       String field,
                                       boolean required) {
        return EvidenceRefSupport.invalidEvidenceRefs(refs, validRefs, brief, field, required);
    }

    private List<String> normalizeEvidenceRefs(List<String> refs,
                                               Set<String> validRefs,
                                               List<String> orderedValidRefs,
                                               ModelDiagnosisBrief brief,
                                               List<String> softFixes) {
        return EvidenceRefSupport.normalizeEvidenceRefs(refs, validRefs, orderedValidRefs, brief, softFixes);
    }

    private List<String> fallbackEvidenceRefs(AdviceGenerationOutput output,
                                              Set<String> validRefs,
                                              List<String> orderedValidRefs,
                                              ModelDiagnosisBrief brief,
                                              List<String> softFixes) {
        String primaryRef = output == null || output.getCaseUnderstanding() == null
                ? ""
                : output.getCaseUnderstanding().getPrimaryEvidenceRef();
        String normalizedPrimaryRef = EvidenceRefSupport.normalizeEvidenceRef(primaryRef, validRefs, orderedValidRefs, brief);
        if (!blank(normalizedPrimaryRef) && EvidenceRefSupport.isValidEvidenceRef(normalizedPrimaryRef, brief)) {
            if (!normalizedPrimaryRef.equals(primaryRef) && softFixes != null) {
                softFixes.add("caseUnderstanding.primaryEvidenceRef alias " + primaryRef + " -> " + normalizedPrimaryRef);
            }
            return List.of(normalizedPrimaryRef);
        }
        for (AdviceGenerationOutput.DiagnosisCandidate candidate : safe(output == null ? null : output.getDiagnosisCandidates())) {
            List<String> refs = normalizeEvidenceRefs(candidate == null ? null : candidate.getEvidenceRefs(),
                    validRefs, orderedValidRefs, brief, softFixes);
            if (invalidEvidenceRefs(refs, validRefs, brief, "diagnosisCandidates.evidenceRefs").isBlank()) {
                return refs;
            }
        }
        return List.of();
    }

    private boolean invalidConfidence(Double confidence) {
        return confidence == null || confidence < 0 || confidence > 1;
    }

    private void trimStudentReport(AdviceGenerationOutput.StudentReport report, List<String> softFixes) {
        report.setBasicLayerText(trimToLength(report.getBasicLayerText(), MAX_BASIC_REPORT_LENGTH,
                "studentReport.basicLayerText", softFixes));
        report.setImprovementLayerText(trimToLength(report.getImprovementLayerText(), MAX_IMPROVEMENT_REPORT_LENGTH,
                "studentReport.improvementLayerText", softFixes));
        report.setNextActionText(trimToLength(report.getNextActionText(), MAX_NEXT_ACTION_LENGTH,
                "studentReport.nextActionText", softFixes));
    }

    private String trimToLength(String value, int maxLength, String field, List<String> softFixes) {
        if (length(value) <= maxLength) {
            return value;
        }
        softFixes.add(field + " trimmed to " + maxLength + " chars");
        return value.trim().substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }

    private boolean isAccepted(ModelDiagnosisBrief brief) {
        String verdict = brief == null || brief.getVerdict() == null ? "" : brief.getVerdict().trim().toUpperCase(Locale.ROOT);
        return "AC".equals(verdict) || "ACCEPTED".equals(verdict);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean blankPath(List<String> values) {
        return values == null || values.stream().noneMatch(value -> value != null && !value.isBlank());
    }

    private void recordAnswerLeakRisk(List<String> softFixes, String field, String... values) {
        String trigger = firstUnsafeTrigger(values);
        if (!trigger.isBlank() && softFixes != null) {
            softFixes.add(field + " answer leak allowed: " + trigger);
        }
    }

    private String firstUnsafeTrigger(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trigger = ModelOutputSafetyPolicy.unsafeLeakTrigger(value);
            if (!trigger.isBlank()) {
                return trigger;
            }
        }
        return "";
    }

    private boolean containsUnsupportedNumericArray(ModelDiagnosisBrief brief, String... values) {
        List<String> visibleInputs = visibleInputPreviews(brief);
        if (visibleInputs.isEmpty()) {
            return false;
        }
        for (String value : values) {
            Matcher matcher = NUMERIC_ARRAY.matcher(value == null ? "" : value);
            while (matcher.find()) {
                if (!visibleInputContainsArray(matcher.group(), visibleInputs)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> visibleInputPreviews(ModelDiagnosisBrief brief) {
        if (brief == null || brief.getVisibleCaseFacts() == null) {
            return List.of();
        }
        return brief.getVisibleCaseFacts().stream()
                .filter(item -> item != null && !Boolean.TRUE.equals(item.getHidden()))
                .map(ModelDiagnosisBrief.VisibleCaseFact::getInputPreview)
                .filter(input -> input != null && !input.isBlank())
                .toList();
    }

    private boolean visibleInputContainsArray(String arrayText, List<String> visibleInputs) {
        String normalizedArray = normalizeNumbers(arrayText);
        if (normalizedArray.isBlank()) {
            return false;
        }
        return visibleInputs.stream()
                .map(this::normalizeNumbers)
                .anyMatch(input -> !input.isBlank() && input.contains(normalizedArray));
    }

    private String normalizeNumbers(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAlias(String value) {
        return value == null ? "" : value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private ExternalModelStagePayloads.StageValidationResult invalid(ModelStageFailureReason reason, String message) {
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(false)
                .stage("DIAGNOSIS_AND_ADVICE")
                .failureReason(reason)
                .message(message == null ? "" : message)
                .softFixes(List.of())
                .hardFailures(List.of(message == null ? "" : message))
                .build();
    }
}

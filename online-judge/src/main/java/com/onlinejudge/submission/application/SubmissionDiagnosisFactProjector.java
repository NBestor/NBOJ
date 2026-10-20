package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SubmissionDiagnosisFactProjector {

    private final SubmissionDiagnosisFactRepository factRepository;
    private final ObjectMapper objectMapper;
    private final IssuePointKeyFactory pointKeyFactory;
    private final SubmissionIssueLifecycleService lifecycleService;
    private final AiStandardSkillUnitRepository skillUnitRepository;
    private final AiStandardMistakePointRepository mistakePointRepository;
    private final AiStandardImprovementPointRepository improvementPointRepository;

    @Autowired
    public SubmissionDiagnosisFactProjector(
            SubmissionDiagnosisFactRepository factRepository,
            ObjectMapper objectMapper,
            IssuePointKeyFactory pointKeyFactory,
            SubmissionIssueLifecycleService lifecycleService,
            AiStandardSkillUnitRepository skillUnitRepository,
            AiStandardMistakePointRepository mistakePointRepository,
            AiStandardImprovementPointRepository improvementPointRepository
    ) {
        this.factRepository = factRepository;
        this.objectMapper = objectMapper;
        this.pointKeyFactory = pointKeyFactory;
        this.lifecycleService = lifecycleService;
        this.skillUnitRepository = skillUnitRepository;
        this.mistakePointRepository = mistakePointRepository;
        this.improvementPointRepository = improvementPointRepository;
    }

    public SubmissionDiagnosisFactProjector(SubmissionDiagnosisFactRepository factRepository, ObjectMapper objectMapper) {
        this(factRepository, objectMapper, new IssuePointKeyFactory(), null, null, null, null);
    }

    @Transactional
    public ProjectionResult project(SubmissionAnalysis analysis, SubmissionAnalysisResponse response) {
        if (analysis == null || analysis.getId() == null || analysis.getSubmissionId() == null || response == null) {
            return new ProjectionResult(0, 0);
        }
        List<SubmissionDiagnosisFact> candidates = new ArrayList<>();
        String libraryFit = libraryFit(response);
        int index = 0;
        for (SubmissionAnalysisResponse.BasicLayerAdvice advice : safe(response.getBasicLayerAdvice())) {
            if (advice == null) {
                continue;
            }
            candidates.add(fact(
                    analysis,
                    "REPAIR",
                    advice.getIssueId(),
                    index == 0,
                    advice.getTitle(),
                    advice.getSkillUnitId(),
                    advice.getMistakePointId(),
                    null,
                    advice.getProvisionalNodeCode(),
                    advice.getKnowledgePath(),
                    advice.getKnowledgePathStatus(),
                    libraryFit,
                    advice.getEvidenceRefs(),
                    advice.getConfidence(),
                    index
            ));
            index++;
        }
        int improvementIndex = 0;
        for (SubmissionAnalysisResponse.ImprovementLayerAdvice advice : safe(response.getImprovementLayerAdvice())) {
            if (advice == null) {
                continue;
            }
            candidates.add(fact(
                    analysis,
                    "IMPROVEMENT",
                    advice.getIssueId(),
                    false,
                    advice.getTitle(),
                    advice.getSkillUnitId(),
                    null,
                    advice.getImprovementPointId(),
                    advice.getProvisionalNodeCode(),
                    advice.getKnowledgePath(),
                    advice.getKnowledgePathStatus(),
                    libraryFit,
                    advice.getEvidenceRefs(),
                    advice.getConfidence(),
                    improvementIndex
            ));
            improvementIndex++;
        }
        if (candidates.isEmpty()) {
            int fallbackIndex = 0;
            for (String tag : safe(response.getFineGrainedTags()).isEmpty()
                    ? safe(response.getIssueTags())
                    : safe(response.getFineGrainedTags())) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                candidates.add(fact(
                        analysis,
                        "DIAGNOSIS",
                        tag,
                        fallbackIndex == 0,
                        tag,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        "UNCLASSIFIED",
                        libraryFit,
                        response.getEvidenceRefs(),
                        response.getConfidence(),
                        fallbackIndex
                ));
                fallbackIndex++;
            }
        }
        int inserted = 0;
        for (SubmissionDiagnosisFact candidate : candidates) {
            if (!factRepository.existsByFactKey(candidate.getFactKey())) {
                factRepository.save(candidate);
                inserted++;
            }
        }
        log.info("Projected submission diagnosis facts. submissionId={}, analysisId={}, inserted={}, total={}",
                analysis.getSubmissionId(), analysis.getId(), inserted, candidates.size());
        if (lifecycleService != null) {
            lifecycleService.rebuildForSubmission(analysis.getSubmissionId());
        }
        return new ProjectionResult(inserted, candidates.size() - inserted);
    }

    private SubmissionDiagnosisFact fact(
            SubmissionAnalysis analysis,
            String factType,
            String issueId,
            boolean primary,
            String title,
            String skillUnitId,
            String mistakePointId,
            String improvementPointId,
            String provisionalNodeCode,
            List<String> knowledgePath,
            String pathStatus,
            String libraryFit,
            List<String> evidenceRefs,
            Double confidence,
            int index
    ) {
        FormalAnchors anchors = resolveFormalAnchors(
                factType,
                skillUnitId,
                mistakePointId,
                improvementPointId
        );
        String normalizedPathStatus = normalizePathStatus(
                pathStatus,
                knowledgePath,
                anchors.skillUnitId(),
                anchors.mistakePointId(),
                anchors.improvementPointId(),
                provisionalNodeCode
        );
        String normalizedProvisionalCode = "PROVISIONAL".equals(normalizedPathStatus)
                ? clean(provisionalNodeCode)
                : null;
        String normalizedSkillUnitId = "PROVISIONAL".equals(normalizedPathStatus) ? null : anchors.skillUnitId();
        String normalizedMistakePointId = "PROVISIONAL".equals(normalizedPathStatus) ? null : anchors.mistakePointId();
        String normalizedImprovementPointId = "PROVISIONAL".equals(normalizedPathStatus) ? null : anchors.improvementPointId();
        String stableIssue = firstNonBlank(issueId, mistakePointId, improvementPointId, skillUnitId, title, "item-" + index);
        String factKey = analysis.getId() + ":" + factType + ":" + stableIssue + ":" + index;
        IssuePointKeyFactory.Identity identity = pointKeyFactory.identity(
                factType,
                normalizedMistakePointId,
                normalizedImprovementPointId,
                normalizedProvisionalCode,
                normalizedSkillUnitId,
                safe(knowledgePath),
                title
        );
        return SubmissionDiagnosisFact.builder()
                .submissionId(analysis.getSubmissionId())
                .analysisId(analysis.getId())
                .factKey(limit(factKey, 180))
                .issueId(limit(clean(issueId), 120))
                .factType(factType)
                .displayCategory("IMPROVEMENT".equals(factType) ? "IMPROVEMENT" : "REPAIR")
                .normalizedPointKey(limit(identity.key(), 220))
                .pointKeySource(identity.source())
                .pointKeyVersion(identity.version())
                .primaryIssue(primary)
                .title(limit(clean(title), 500))
                .skillUnitId(limit(normalizedSkillUnitId, 160))
                .mistakePointId(limit(normalizedMistakePointId, 160))
                .improvementPointId(limit(normalizedImprovementPointId, 160))
                .provisionalNodeCode(limit(normalizedProvisionalCode, 160))
                .knowledgePathJson(json(safe(knowledgePath)))
                .knowledgePathStatus(normalizedPathStatus)
                .libraryFit(normalizeLibraryFit(libraryFit, normalizedPathStatus))
                .evidenceRefsJson(json(safe(evidenceRefs)))
                .confidence(confidence)
                .projectionStatus("READY")
                .build();
    }

    private FormalAnchors resolveFormalAnchors(
            String factType,
            String skillUnitId,
            String mistakePointId,
            String improvementPointId
    ) {
        String skill = clean(skillUnitId);
        String mistake = clean(mistakePointId);
        String improvement = clean(improvementPointId);
        if (skillUnitRepository == null || mistakePointRepository == null || improvementPointRepository == null) {
            return new FormalAnchors(skill, mistake, improvement);
        }
        if ("REPAIR".equals(factType) && mistake != null && !mistake.isBlank()) {
            var point = mistakePointRepository.findByCode(mistake)
                    .filter(item -> item.isEnabled() && enabledSkill(item.getSkillUnitCode()) != null)
                    .orElse(null);
            if (point != null) {
                return new FormalAnchors(enabledSkill(point.getSkillUnitCode()), point.getCode(), null);
            }
        }
        if ("IMPROVEMENT".equals(factType) && improvement != null && !improvement.isBlank()) {
            var point = improvementPointRepository.findByCode(improvement)
                    .filter(item -> item.isEnabled()
                            && (clean(item.getSkillUnitCode()) == null
                            || clean(item.getSkillUnitCode()).isBlank()
                            || enabledSkill(item.getSkillUnitCode()) != null))
                    .orElse(null);
            if (point != null) {
                return new FormalAnchors(enabledSkill(point.getSkillUnitCode()), null, point.getCode());
            }
        }
        return new FormalAnchors(enabledSkill(skill), null, null);
    }

    private String enabledSkill(String code) {
        String normalized = clean(code);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return skillUnitRepository.findByCode(normalized)
                .filter(item -> item.isEnabled())
                .map(item -> clean(item.getCode()))
                .orElse(null);
    }

    private String libraryFit(SubmissionAnalysisResponse response) {
        SubmissionAnalysisResponse.AiInvocation invocation = response.getAiInvocation();
        if (invocation == null) {
            return "UNKNOWN";
        }
        return firstNonBlank(invocation.getDiagnosisLibraryFit(), invocation.getLibraryFit(), "UNKNOWN");
    }

    private String normalizePathStatus(
            String value,
            List<String> path,
            String skillUnitId,
            String mistakePointId,
            String improvementPointId,
            String provisionalNodeCode
    ) {
        String normalized = clean(value) == null ? "" : clean(value).toUpperCase();
        boolean formalIdentity = !firstNonBlank(mistakePointId, improvementPointId, skillUnitId).isBlank();
        boolean provisionalIdentity = !firstNonBlank(provisionalNodeCode).isBlank();
        if ("FORMAL".equals(normalized)) {
            return formalIdentity ? "FORMAL" : "UNCLASSIFIED";
        }
        if ("PROVISIONAL".equals(normalized)) {
            return provisionalIdentity ? "PROVISIONAL" : "UNCLASSIFIED";
        }
        if (List.of("INFERRED", "UNCLASSIFIED").contains(normalized)) {
            return normalized;
        }
        if (formalIdentity) {
            return "FORMAL";
        }
        if (provisionalIdentity) {
            return "PROVISIONAL";
        }
        return safe(path).isEmpty() ? "UNCLASSIFIED" : "INFERRED";
    }

    private String normalizeLibraryFit(String value, String pathStatus) {
        String normalized = clean(value) == null ? "" : clean(value).toUpperCase();
        if (List.of("HIT", "PARTIAL", "MISS").contains(normalized)) {
            return normalized;
        }
        return switch (pathStatus) {
            case "FORMAL" -> "HIT";
            case "PROVISIONAL", "INFERRED" -> "PARTIAL";
            default -> "MISS";
        };
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String limit(String value, int length) {
        if (value == null || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

    public record ProjectionResult(int inserted, int skipped) {
    }

    private record FormalAnchors(String skillUnitId, String mistakePointId, String improvementPointId) {
    }
}

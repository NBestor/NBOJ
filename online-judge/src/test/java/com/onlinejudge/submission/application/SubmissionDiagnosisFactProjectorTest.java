package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionDiagnosisFactProjectorTest {

    @Test
    void projectsMultiplePathStatusesAndReplayIsIdempotent() {
        SubmissionDiagnosisFactRepository repository = mock(SubmissionDiagnosisFactRepository.class);
        when(repository.existsByFactKey(any())).thenReturn(false, false, true, true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubmissionDiagnosisFactProjector projector = new SubmissionDiagnosisFactProjector(repository, new ObjectMapper());
        SubmissionAnalysis analysis = SubmissionAnalysis.builder().id(8L).submissionId(7L).build();
        SubmissionAnalysisResponse response = SubmissionAnalysisResponse.builder()
                .basicLayerAdvice(List.of(
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .issueId("I1")
                                .title("边界错误")
                                .mistakePointId("MP_BOUNDARY")
                                .knowledgePath(List.of("基础", "循环", "边界", "边界错误"))
                                .knowledgePathStatus("FORMAL")
                                .evidenceRefs(List.of("code:line:3"))
                                .build(),
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .issueId("I2")
                                .title("临时细分问题")
                                .knowledgePath(List.of("基础", "循环", "临时路径"))
                                .knowledgePathStatus("PROVISIONAL")
                                .provisionalNodeCode("MP_AI_LOOP_BOUNDARY")
                                .build(),
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .issueId("I3")
                                .title("旧记录缺少路径状态")
                                .knowledgePath(List.of())
                                .build()
                ))
                .build();

        when(repository.existsByFactKey(any())).thenReturn(false, false, false, true, true, true);
        assertThat(projector.project(analysis, response).inserted()).isEqualTo(3);
        assertThat(projector.project(analysis, response).inserted()).isZero();

        ArgumentCaptor<SubmissionDiagnosisFact> captor = ArgumentCaptor.forClass(SubmissionDiagnosisFact.class);
        verify(repository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SubmissionDiagnosisFact::getKnowledgePathStatus)
                .containsExactly("FORMAL", "PROVISIONAL", "UNCLASSIFIED");
        assertThat(captor.getAllValues().get(1).getProvisionalNodeCode()).isEqualTo("MP_AI_LOOP_BOUNDARY");
        assertThat(captor.getAllValues().get(1).getPointKeySource()).isEqualTo("PROVISIONAL_ID");
        assertThat(captor.getAllValues()).extracting(SubmissionDiagnosisFact::getLibraryFit)
                .containsExactly("HIT", "PARTIAL", "MISS");
        assertThat(captor.getAllValues()).allSatisfy(fact -> {
            assertThat(fact.getSubmissionId()).isEqualTo(7L);
            assertThat(fact.getAnalysisId()).isEqualTo(8L);
            assertThat(fact.getFactKey()).isNotBlank();
            assertThat(fact.getNormalizedPointKey()).isNotBlank();
            assertThat(fact.getPointKeyVersion()).isEqualTo(IssuePointKeyFactory.VERSION);
            assertThat(fact.getDisplayCategory()).isEqualTo("REPAIR");
        });
    }

    @Test
    void refusesProvisionalStatusWithoutStableCode() {
        SubmissionDiagnosisFactRepository repository = mock(SubmissionDiagnosisFactRepository.class);
        when(repository.existsByFactKey(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubmissionDiagnosisFactProjector projector = new SubmissionDiagnosisFactProjector(repository, new ObjectMapper());
        SubmissionAnalysis analysis = SubmissionAnalysis.builder().id(10L).submissionId(9L).build();
        SubmissionAnalysisResponse response = SubmissionAnalysisResponse.builder()
                .basicLayerAdvice(List.of(SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                        .issueId("I1")
                        .title("缺少稳定 code")
                        .knowledgePath(List.of("算法", "临时问题"))
                        .knowledgePathStatus("PROVISIONAL")
                        .evidenceRefs(List.of("code:line:2"))
                        .build()))
                .build();

        assertThat(projector.project(analysis, response).inserted()).isEqualTo(1);

        ArgumentCaptor<SubmissionDiagnosisFact> captor = ArgumentCaptor.forClass(SubmissionDiagnosisFact.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKnowledgePathStatus()).isEqualTo("UNCLASSIFIED");
        assertThat(captor.getValue().getProvisionalNodeCode()).isNull();
        assertThat(captor.getValue().getPointKeySource()).isEqualTo("TEXT_FINGERPRINT");
        assertThat(captor.getValue().getLibraryFit()).isEqualTo("MISS");
    }

    @Test
    void canonicalizesFormalSkillFromMistakeAnchor() {
        SubmissionDiagnosisFactRepository repository = mock(SubmissionDiagnosisFactRepository.class);
        AiStandardSkillUnitRepository skillRepository = mock(AiStandardSkillUnitRepository.class);
        AiStandardMistakePointRepository mistakeRepository = mock(AiStandardMistakePointRepository.class);
        AiStandardImprovementPointRepository improvementRepository = mock(AiStandardImprovementPointRepository.class);
        when(repository.existsByFactKey(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(skillRepository.findByCode("SK_CANONICAL")).thenReturn(Optional.of(AiStandardSkillUnit.builder()
                .code("SK_CANONICAL")
                .enabled(true)
                .build()));
        when(mistakeRepository.findByCode("MP_CANONICAL")).thenReturn(Optional.of(AiStandardMistakePoint.builder()
                .code("MP_CANONICAL")
                .skillUnitCode("SK_CANONICAL")
                .enabled(true)
                .build()));
        SubmissionDiagnosisFactProjector projector = new SubmissionDiagnosisFactProjector(
                repository,
                new ObjectMapper(),
                new IssuePointKeyFactory(),
                null,
                skillRepository,
                mistakeRepository,
                improvementRepository
        );
        SubmissionAnalysis analysis = SubmissionAnalysis.builder().id(12L).submissionId(11L).build();
        SubmissionAnalysisResponse response = SubmissionAnalysisResponse.builder()
                .basicLayerAdvice(List.of(SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                        .issueId("I1")
                        .title("正式易错点能力归属错配")
                        .skillUnitId("SK_LEGACY_CROSS_LINK")
                        .mistakePointId("MP_CANONICAL")
                        .knowledgePath(List.of("算法", "图论", "并查集"))
                        .knowledgePathStatus("FORMAL")
                        .build()))
                .build();

        assertThat(projector.project(analysis, response).inserted()).isEqualTo(1);

        ArgumentCaptor<SubmissionDiagnosisFact> captor = ArgumentCaptor.forClass(SubmissionDiagnosisFact.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSkillUnitId()).isEqualTo("SK_CANONICAL");
        assertThat(captor.getValue().getMistakePointId()).isEqualTo("MP_CANONICAL");
        assertThat(captor.getValue().getKnowledgePathStatus()).isEqualTo("FORMAL");
        assertThat(captor.getValue().getPointKeySource()).isEqualTo("FORMAL_ID");
    }
}

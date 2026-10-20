package com.onlinejudge.learning.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisReportReaderTest {

    private final DiagnosisReportReader reader = new DiagnosisReportReader(new ObjectMapper(), new DiagnosisTaxonomy());

    @Test
    void readsNewReportWithFineGrainedTags() {
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .headline("边界问题")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE", "UNKNOWN_FINE"],
                          "studentHint": "检查循环边界",
                          "progressSignal": "正在定位边界",
                          "learningTrajectorySignal": {
                            "phase": "REPEATED_STUCK",
                            "label": "同类问题反复卡住",
                            "evidenceRef": "history:repeated_stuck",
                            "summary": "连续多次停留在同一类失败上。",
                            "nextFocus": "只验证一个最小样例。",
                            "needsTeacherAttention": true
                          },
                          "confidence": 0.82,
                          "uncertainty": "隐藏测试点只提供脱敏摘要",
                          "answerLeakRisk": "LOW",
                          "studentHintPlan": {
                            "hintLevel": "L2",
                            "problemType": "循环边界",
                            "evidenceAnchor": "核对 code:plus_minus_one",
                            "nextAction": "手推 n=1",
                            "coachQuestion": "循环最后一次取什么值？",
                            "teachingAction": "TRACE_VARIABLES",
                            "evidenceRefs": ["code:plus_minus_one"],
                            "answerLeakRisk": "LOW"
                          }
                        }
                        """)
                .build();

        assertThat(reader.issueTags(analysis)).containsExactly("BOUNDARY_CONDITION");
        assertThat(reader.fineGrainedTags(analysis)).containsExactly("OFF_BY_ONE");
        assertThat(reader.studentHint(analysis)).isEqualTo("检查循环边界");
        assertThat(reader.progressSignal(analysis)).isEqualTo("正在定位边界");
        var trajectorySignal = reader.learningTrajectorySignal(analysis);
        assertThat(trajectorySignal).isNotNull();
        assertThat(trajectorySignal.phase()).isEqualTo("REPEATED_STUCK");
        assertThat(trajectorySignal.evidenceRef()).isEqualTo("history:repeated_stuck");
        assertThat(trajectorySignal.nextFocus()).isEqualTo("只验证一个最小样例。");
        assertThat(trajectorySignal.needsTeacherAttention()).isTrue();
        assertThat(reader.confidence(analysis)).isEqualTo(0.82);
        assertThat(reader.uncertainty(analysis)).isEqualTo("隐藏测试点只提供脱敏摘要");
        assertThat(reader.answerLeakRisk(analysis)).isEqualTo("LOW");
        var hintPlan = reader.studentHintPlan(analysis);
        assertThat(hintPlan).isNotNull();
        assertThat(hintPlan.hintLevel()).isEqualTo("L2");
        assertThat(hintPlan.problemType()).isEqualTo("循环边界");
        assertThat(hintPlan.teachingAction()).isEqualTo("TRACE_VARIABLES");
        assertThat(hintPlan.evidenceRefs()).containsExactly("code:plus_minus_one");
    }

    @Test
    void keepsOldReportWithoutFineGrainedTagsReadable() {
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .headline("旧版诊断标题")
                .reportJson("""
                        {
                          "issueTags": ["TIME_COMPLEXITY"]
                        }
                        """)
                .build();

        assertThat(reader.issueTags(analysis)).containsExactly("TIME_COMPLEXITY");
        assertThat(reader.fineGrainedTags(analysis)).isEmpty();
        assertThat(reader.studentHint(analysis)).isEmpty();
        assertThat(reader.studentHintPlan(analysis)).isNull();
        assertThat(reader.confidence(analysis)).isNull();
        assertThat(reader.uncertainty(analysis)).isEmpty();
        assertThat(reader.answerLeakRisk(analysis)).isEqualTo("UNKNOWN");
    }

    @Test
    void readsAiInvocationSnapshotWhenPresent() {
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .headline("模型诊断")
                .reportJson("""
                        {
                          "issueTags": ["TIME_COMPLEXITY"],
                          "aiInvocation": {
                            "provider": "ModelScope",
                            "model": "MiniMax/MiniMax-M2.7",
                            "modelVersion": "MiniMax/MiniMax-M2.7",
                            "promptVersion": "submission-diagnosis-prompt-v2",
                            "agentVersion": "diagnostic-agent-v2",
                            "analysisSchemaVersion": "diagnosis-v1",
                            "evidenceSchemaVersion": "diagnosis-evidence-v1",
                            "taxonomyVersion": "diagnosis-taxonomy-v1",
                            "status": "MODEL_COMPLETED",
                            "fallbackUsed": false,
                            "runtimeMode": "single-call",
                            "failureStage": "",
                            "failureReason": "",
                            "transportMode": "stream",
                            "streamChunkCount": 4,
                            "streamContentChunkCount": 3,
                            "streamReasoningChunkCount": 1,
                            "streamInvalidChunkCount": 2,
                            "streamFinishReason": "stop",
                            "streamFallbackRetryUsed": true
                          }
                        }
                        """)
                .build();

        var snapshot = reader.aiInvocation(analysis);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.provider()).isEqualTo("ModelScope");
        assertThat(snapshot.modelVersion()).isEqualTo("MiniMax/MiniMax-M2.7");
        assertThat(snapshot.promptVersion()).isEqualTo("submission-diagnosis-prompt-v2");
        assertThat(snapshot.agentVersion()).isEqualTo("diagnostic-agent-v2");
        assertThat(snapshot.status()).isEqualTo("MODEL_COMPLETED");
        assertThat(snapshot.fallbackUsed()).isFalse();
        assertThat(snapshot.runtimeMode()).isEqualTo("single-call");
        assertThat(snapshot.failureStage()).isEmpty();
        assertThat(snapshot.failureReason()).isEmpty();
        assertThat(snapshot.transportMode()).isEqualTo("stream");
        assertThat(snapshot.streamChunkCount()).isEqualTo(4);
        assertThat(snapshot.streamContentChunkCount()).isEqualTo(3);
        assertThat(snapshot.streamReasoningChunkCount()).isEqualTo(1);
        assertThat(snapshot.streamInvalidChunkCount()).isEqualTo(2);
        assertThat(snapshot.streamFinishReason()).isEqualTo("stop");
        assertThat(snapshot.streamFallbackRetryUsed()).isTrue();
    }

    @Test
    void fallsBackToHeadlineWhenReportJsonIsInvalid() {
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .headline("旧版轻量标题")
                .reportJson("{broken")
                .build();

        assertThat(reader.issueTags(analysis)).containsExactly("旧版轻量标题");
        assertThat(reader.fineGrainedTags(analysis)).isEmpty();
    }
}

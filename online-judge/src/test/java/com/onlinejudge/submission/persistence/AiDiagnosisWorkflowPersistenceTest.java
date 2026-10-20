package com.onlinejudge.submission.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.application.AiDiagnosisWorkflowService;
import com.onlinejudge.submission.domain.AiDiagnosisRun;
import com.onlinejudge.submission.domain.AiDiagnosisStageRun;
import com.onlinejudge.submission.dto.AiDiagnosisProgressResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AiDiagnosisWorkflowPersistenceTest {

    @Autowired
    AiDiagnosisRunRepository runRepository;

    @Autowired
    AiDiagnosisStageRunRepository stageRepository;

    AiDiagnosisWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new AiDiagnosisWorkflowService(runRepository, stageRepository, new ObjectMapper());
    }

    @Test
    void appendsOfficialVersionsAndKeepsUniqueStageKeys() throws Exception {
        AiDiagnosisRun first = workflowService.beginRun(7L, "generation-1");
        AiDiagnosisRun reused = workflowService.beginRun(7L, "generation-1");
        AiDiagnosisRun second = workflowService.beginRun(7L, "generation-2");

        assertThat(reused.getId()).isEqualTo(first.getId());
        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(second.getVersionNumber()).isEqualTo(2);

        AtomicInteger calls = new AtomicInteger();
        StageOutput firstOutput = workflowService.executeStage(
                first.getId(), "CORE_DIAGNOSIS", "CORE_DIAGNOSIS", null,
                List.of("evidence"), StageOutput.class, "provider", "model", "prompt-v1",
                () -> new StageOutput("I1", calls.incrementAndGet())
        );
        StageOutput cachedOutput = workflowService.executeStage(
                first.getId(), "CORE_DIAGNOSIS", "CORE_DIAGNOSIS", null,
                List.of("evidence"), StageOutput.class, "provider", "model", "prompt-v1",
                () -> new StageOutput("I2", calls.incrementAndGet())
        );

        assertThat(firstOutput.issueId()).isEqualTo("I1");
        assertThat(cachedOutput).isEqualTo(firstOutput);
        assertThat(calls).hasValue(1);
        assertThat(stageRepository.findByRunIdOrderByIdAsc(first.getId())).hasSize(1);

        workflowService.completeRun(first.getId());
        workflowService.completeRun(second.getId());
        assertThat(runRepository.findById(first.getId()).orElseThrow().isOfficialVersion()).isFalse();
        assertThat(runRepository.findById(second.getId()).orElseThrow().isOfficialVersion()).isTrue();
    }

    @Test
    void recoversInterruptedStageWithoutRemovingSucceededOutput() throws Exception {
        AiDiagnosisRun run = workflowService.beginRun(9L, "generation-recovery");
        workflowService.executeStage(
                run.getId(), "CORE_DIAGNOSIS", "CORE_DIAGNOSIS", null,
                "input", StageOutput.class, "provider", "model", "prompt-v1",
                () -> new StageOutput("I1", 1)
        );
        workflowService.prepareStage(
                run.getId(), "ISSUE_ATTACHMENT:I1", "ISSUE_ATTACHMENT", "I1",
                "input-2", "provider", "model", "prompt-v1"
        );

        workflowService.recoverInterruptedRuns();

        List<AiDiagnosisStageRun> stages = stageRepository.findByRunIdOrderByIdAsc(run.getId());
        assertThat(stages).extracting(AiDiagnosisStageRun::getStatus)
                .containsExactly(AiDiagnosisWorkflowService.STAGE_SUCCEEDED,
                        AiDiagnosisWorkflowService.STAGE_RETRYABLE_FAILED);
        assertThat(stages.get(0).getOutputJson()).contains("I1");
        assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus())
                .isEqualTo(AiDiagnosisWorkflowService.RUN_RETRYING);

        AiDiagnosisProgressResponse progress = workflowService.progressForSubmission(9L);
        assertThat(progress.isRetrying()).isTrue();
        assertThat(progress.getStage()).isEqualTo("UNDERSTANDING_EVIDENCE");
        assertThat(progress.getCompletedStages()).isEqualTo(1);
        assertThat(progress.getTotalStages()).isEqualTo(2);
    }

    @Test
    void requiredFailureKeepsResultAvailableForRetryWhileProjectionFailureDoesNotRollbackCompletion() throws Exception {
        AiDiagnosisRun retrying = workflowService.beginRun(12L, "generation-required-failure");
        workflowService.executeStage(
                retrying.getId(), "CORE_DIAGNOSIS", "CORE_DIAGNOSIS", null,
                "input", StageOutput.class, "provider", "model", "prompt",
                () -> new StageOutput("I1", 1));
        assertThatThrownBy(() -> workflowService.executeStage(
                retrying.getId(), "ISSUE_ATTACHMENT:I1", "ISSUE_ATTACHMENT", "I1",
                "input", StageOutput.class, "provider", "model", "prompt",
                () -> { throw new IllegalStateException("temporary failure"); }))
                .isInstanceOf(IllegalStateException.class);
        workflowService.markResultReady(retrying.getId());
        workflowService.completeRun(retrying.getId());
        AiDiagnosisRun retryingSaved = runRepository.findById(retrying.getId()).orElseThrow();
        assertThat(retryingSaved.getStatus()).isEqualTo(AiDiagnosisWorkflowService.RUN_RETRYING);
        assertThat(retryingSaved.isResultSaved()).isTrue();

        AiDiagnosisRun completed = workflowService.beginRun(13L, "generation-projection-failure");
        for (String stageType : List.of("CORE_DIAGNOSIS", "ISSUE_ATTACHMENT", "STUDENT_OUTPUT", "TEACHER_OUTPUT")) {
            workflowService.executeStage(
                    completed.getId(), stageType, stageType, null,
                    "input", StageOutput.class, "provider", "model", "prompt",
                    () -> new StageOutput(stageType, 1));
        }
        assertThatThrownBy(() -> workflowService.executeStage(
                completed.getId(), "RUNTIME_QUALITY_PROJECTION", "RUNTIME_QUALITY_PROJECTION", null,
                "input", StageOutput.class, "provider", "model", "prompt",
                () -> { throw new IllegalStateException("projection failure"); }))
                .isInstanceOf(IllegalStateException.class);
        workflowService.markResultReady(completed.getId());
        workflowService.completeRun(completed.getId());
        assertThat(runRepository.findById(completed.getId()).orElseThrow().getStatus())
                .isEqualTo(AiDiagnosisWorkflowService.RUN_COMPLETED);
    }

    private record StageOutput(String issueId, int sequence) {
    }
}

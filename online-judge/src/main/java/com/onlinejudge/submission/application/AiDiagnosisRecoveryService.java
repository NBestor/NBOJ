package com.onlinejudge.submission.application;

import com.onlinejudge.submission.domain.AiDiagnosisRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDiagnosisRecoveryService {

    private final AiDiagnosisWorkflowService workflowService;
    private final StudentAiFeedbackAsyncService feedbackAsyncService;

    @Value("${ai.workflow.recovery-enabled:true}")
    private boolean recoveryEnabled;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        if (!recoveryEnabled) {
            return;
        }
        List<AiDiagnosisRun> recovered = workflowService.recoverInterruptedRuns();
        for (AiDiagnosisRun run : recovered) {
            log.info("Re-enqueueing interrupted AI diagnosis. runId={}, submissionId={}, version={}",
                    run.getId(), run.getSubmissionId(), run.getVersionNumber());
            feedbackAsyncService.enqueueRecovered(run.getSubmissionId());
        }
    }
}

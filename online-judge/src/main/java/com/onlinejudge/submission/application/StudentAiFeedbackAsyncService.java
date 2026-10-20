package com.onlinejudge.submission.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAiFeedbackAsyncService {

    private final StudentAiFeedbackService studentAiFeedbackService;
    private final ExternalModelFailureClassifier failureClassifier;
    private final ObjectProvider<StudentAiFeedbackAsyncService> selfProvider;
    private final Set<Long> runningSubmissionIds = ConcurrentHashMap.newKeySet();

    public void enqueue(Long submissionId) {
        enqueue(submissionId, false);
    }

    public void enqueueRecovered(Long submissionId) {
        enqueueRegeneration(submissionId);
    }

    public void enqueueRegeneration(Long submissionId) {
        if (submissionId == null || !runningSubmissionIds.add(submissionId)) {
            return;
        }
        try {
            studentAiFeedbackService.startNewGeneration(submissionId);
            selfProvider.getObject().generate(submissionId);
        } catch (RuntimeException exception) {
            runningSubmissionIds.remove(submissionId);
            throw exception;
        }
    }

    private void enqueue(Long submissionId, boolean recovery) {
        if (submissionId == null) {
            return;
        }
        if (!recovery) {
            String status = studentAiFeedbackService.markGenerating(submissionId).getStatus();
            if ("READY".equals(status)) {
                return;
            }
        }
        if (!runningSubmissionIds.add(submissionId)) {
            return;
        }
        selfProvider.getObject().generate(submissionId);
    }

    @Async
    public void generate(Long submissionId) {
        try {
            studentAiFeedbackService.generateAndStore(submissionId);
        } catch (Exception exception) {
            log.error("Student AI feedback generation failed. submissionId={}", submissionId, exception);
            studentAiFeedbackService.markFailed(submissionId, failureClassifier.classify(exception).name());
        } finally {
            runningSubmissionIds.remove(submissionId);
        }
    }
}

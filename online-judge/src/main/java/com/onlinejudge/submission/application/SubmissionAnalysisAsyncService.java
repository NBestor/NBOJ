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
public class SubmissionAnalysisAsyncService {

    private final StudentAiFeedbackAsyncService studentAiFeedbackAsyncService;
    private final ObjectProvider<SubmissionAnalysisAsyncService> selfProvider;
    private final Set<Long> runningSubmissionIds = ConcurrentHashMap.newKeySet();

    public void enqueue(Long submissionId) {
        if (submissionId == null) {
            log.warn("Skipped async submission analysis enqueue because submissionId is null.");
            return;
        }
        if (!runningSubmissionIds.add(submissionId)) {
            log.info("Skipped async submission analysis enqueue because it is already running. submissionId={}", submissionId);
            return;
        }
        log.info("Queued async submission analysis. submissionId={}", submissionId);
        selfProvider.getObject().generate(submissionId);
    }

    @Async
    public void generate(Long submissionId) {
        try {
            log.info("Routing legacy analysis request into the complete diagnosis workflow. submissionId={}", submissionId);
            studentAiFeedbackAsyncService.enqueue(submissionId);
        } catch (Exception exception) {
            log.error("Complete diagnosis workflow enqueue failed. submissionId={}", submissionId, exception);
        } finally {
            runningSubmissionIds.remove(submissionId);
        }
    }
}

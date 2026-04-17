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

    private final SubmissionAnalysisService submissionAnalysisService;
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
            log.info("Async submission analysis started. submissionId={}", submissionId);
            submissionAnalysisService.generateAndStoreAnalysisForSubmission(submissionId);
            log.info("Async submission analysis finished. submissionId={}", submissionId);
        } catch (Exception exception) {
            log.error("Async submission analysis generation failed. submissionId={}", submissionId, exception);
        } finally {
            runningSubmissionIds.remove(submissionId);
        }
    }
}

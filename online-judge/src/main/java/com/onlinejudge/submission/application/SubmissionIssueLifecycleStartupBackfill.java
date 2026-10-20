package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionEvidenceBackfillResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionIssueLifecycleStartupBackfill implements ApplicationRunner {

    private final SubmissionEvidenceProperties properties;
    private final SubmissionEvidenceBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isLifecycleStartupBackfillEnabled()) {
            return;
        }
        Long cursor = 0L;
        boolean hasMore;
        do {
            SubmissionEvidenceBackfillResponse result = properties.isLifecycleStartupBackfillDryRun()
                    ? backfillService.preview(cursor, properties.getLifecycleBackfillBatchSize())
                    : backfillService.backfill(cursor, properties.getLifecycleBackfillBatchSize());
            log.info("Submission issue lifecycle startup backfill. dryRun={}, cursorStart={}, nextCursor={}, processed={}, failed={}",
                    result.isDryRun(), result.getCursorStart(), result.getNextCursor(), result.getProcessedCount(), result.getFailedCount());
            cursor = result.getNextCursor();
            hasMore = result.isHasMore();
        } while (hasMore);
    }
}

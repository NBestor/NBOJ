package com.onlinejudge.submission.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.submission-evidence")
public class SubmissionEvidenceProperties {
    private boolean strictClassroomContextEnabled = true;
    private boolean analyticsReadEnabled = true;
    private boolean lifecycleProjectionEnabled = true;
    private boolean lifecycleReadEnabled = true;
    private boolean lifecycleStartupBackfillEnabled = false;
    private boolean lifecycleStartupBackfillDryRun = true;
    private int lifecycleBackfillBatchSize = 200;
    private int persistentDifficultyThreshold = 3;
    private int crossProblemWeaknessThreshold = 2;
    private double classCoverageThreshold = 0.4;
    private double classRepeatThreshold = 1.5;
    private double classLowRecoveryThreshold = 0.6;
}

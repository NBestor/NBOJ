package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOutputStageResult {
    private AdviceGenerationOutput adviceOutput;
    private SubmissionAnalysisResponse.StudentFeedback studentFeedback;
}

package com.onlinejudge.submission.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherInsightOutput {
    private String summary;
    private List<IssueObservation> issueObservations;
    private String uncertainty;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueObservation {
        private String issueId;
        private String teachingObservation;
        private List<String> evidenceRefs;
        private Integer priority;
    }
}

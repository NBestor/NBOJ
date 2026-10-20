package com.onlinejudge.submission.application;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeDiagnosisOutput {
    private String problemUnderstanding;
    private String codeIntent;
    private String behaviorGap;
    @JsonAlias({"diagnosisIssues", "findings", "rootCauses", "problems"})
    private List<Issue> issues;
    private List<Hypothesis> hypotheses;
    private NavigationIntent navigationIntent;
    private String uncertainty;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Issue {
        private String issueId;
        @JsonAlias({"name", "issue", "problem", "rootCause"})
        private String title;
        @JsonAlias({"description", "currentIssue", "behaviorGap", "reason"})
        private String whatHappened;
        @JsonAlias({"impact", "why", "whyImportant"})
        private String whyItMatters;
        private List<String> evidenceRefs;
        private String severity;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hypothesis {
        @JsonAlias({"title", "issue", "rootCause"})
        private String name;
        @JsonAlias({"description", "whatHappened", "behaviorGap"})
        private String reason;
        private List<String> evidenceRefs;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NavigationIntent {
        private List<String> preferredDirections;
        private String reason;
        private List<String> avoidDirections;
    }
}

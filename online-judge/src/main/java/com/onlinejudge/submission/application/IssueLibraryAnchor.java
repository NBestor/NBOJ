package com.onlinejudge.submission.application;

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
public class IssueLibraryAnchor {
    private String issueId;
    private String anchorStatus;
    private List<PathNode> path;
    private String skillUnitCode;
    private String mistakePointCode;
    private String improvementPointCode;
    private String provisionalCandidateCode;
    private String provisionalCandidateName;
    private String provisionalCandidateLayer;
    private String reason;
    private Double confidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PathNode {
        private String code;
        private String name;
        private String type;
    }
}

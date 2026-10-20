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
public class StandardLibraryNavigationOutput {
    private String status;
    private List<SelectedBranch> selectedBranches;
    private List<SelectedPath> selectedPaths;
    private List<UnresolvedGap> unresolvedGaps;
    private String uncertainty;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectedBranch {
        private String knowledgeNodeCode;
        private String reason;
        private List<String> evidenceRefs;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectedPath {
        private String knowledgeNodeCode;
        private String skillUnitCode;
        private String mistakePointCode;
        private String improvementPointCode;
        private String libraryFit;
        private String reason;
        private List<String> evidenceRefs;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UnresolvedGap {
        private String name;
        private List<String> suggestedPath;
        private String reason;
        private List<String> evidenceRefs;
        private Double confidence;
    }
}

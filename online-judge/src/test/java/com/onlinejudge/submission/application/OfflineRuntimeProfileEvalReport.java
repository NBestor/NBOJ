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
public class OfflineRuntimeProfileEvalReport {

    private String reportType;
    private Integer totalCount;
    private Integer reducedCount;
    private Integer autoReducedCount;
    private Integer autoCompactCount;
    private Integer qualityPreservedCount;
    private Integer autoQualityPreservedCount;
    private Double averageCompressionRatio;
    private Double averageAutoCompressionRatio;
    private List<Entry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String caseId;
        private String promptVersion;
        private Integer standardRequestBytes;
        private Integer lowLatencyRequestBytes;
        private Integer autoRequestBytes;
        private Boolean requestBytesReduced;
        private Boolean autoRequestBytesReduced;
        private Double compressionRatio;
        private Double autoCompressionRatio;
        private String lowLatencyRuntimeProfile;
        private Boolean lowLatencyRequestCompact;
        private String autoRuntimeProfile;
        private Boolean autoRequestCompact;
        private Integer evidenceRefCount;
        private Integer issueTagCount;
        private Integer fineTagCount;
        private Integer teachingActionCount;
        private Boolean hiddenBoundaryPresent;
        private Integer autoEvidenceRefCount;
        private Integer autoIssueTagCount;
        private Integer autoFineTagCount;
        private Integer autoTeachingActionCount;
        private Boolean autoHiddenBoundaryPresent;
        private Boolean qualityPreserved;
        private Boolean autoQualityPreserved;
        private List<String> failureReasons;
        private List<String> autoFailureReasons;
    }
}

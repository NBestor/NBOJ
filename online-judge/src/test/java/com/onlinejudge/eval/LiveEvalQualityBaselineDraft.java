package com.onlinejudge.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveEvalQualityBaselineDraft {

    private String name;
    private String sourceReportType;
    private String caseId;
    private String assistantType;
    private String stage;
    private String model;
    private String promptVersion;
    private String status;
    private String baselineType;
    private List<String> expectedSignals;
    private List<String> evidenceRefs;
    private List<String> mustKeep;
    private List<String> mustNotMention;
    private String teachingAction;
    private String teacherExpectation;
    private String outputSummary;
    private String regressionPurpose;
}

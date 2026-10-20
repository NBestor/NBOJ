package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportPreviewResponse {
    private String importType;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int duplicateRows;
    private String message;
    private List<RowIssue> issues;
    private List<StudentImportRow> students;
    private List<ProblemImportRow> problems;

    @Data
    @Builder
    public static class RowIssue {
        private int rowNumber;
        private String severity;
        private String message;
    }

    @Data
    @Builder
    public static class StudentImportRow {
        private int rowNumber;
        private String className;
        private Long classGroupId;
        private String displayName;
        private String studentNo;
        private String note;
        private boolean valid;
        private boolean duplicate;
        private String message;
    }

    @Data
    @Builder
    public static class ProblemImportRow {
        private int rowNumber;
        private String title;
        private String description;
        private String difficulty;
        private Integer timeLimit;
        private Integer memoryLimit;
        private String aiPromptDirection;
        private int testCaseCount;
        private int visibleTestCaseCount;
        private boolean valid;
        private boolean duplicate;
        private String message;
        private String payloadJson;
    }
}

package com.onlinejudge.classroom.dto;

import lombok.Data;

@Data
public class ImportRequest {
    private String format;
    private String content;
    private String fileName;
    private Long classGroupId;
    private String className;
}

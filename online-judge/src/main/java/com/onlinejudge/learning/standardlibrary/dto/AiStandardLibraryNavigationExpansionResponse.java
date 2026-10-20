package com.onlinejudge.learning.standardlibrary.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiStandardLibraryNavigationExpansionResponse {
    private AiStandardLibraryNavigationNodeResponse node;
    private List<AiStandardLibraryNavigationNodeResponse> ancestors;
    private List<AiStandardLibraryNavigationNodeResponse> children;
    private int childPage;
    private int childSize;
    private long childTotal;
    private boolean childHasMore;
}

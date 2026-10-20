package com.onlinejudge.learning.standardlibrary.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.standard-library-growth")
public class AiStandardLibraryGrowthProperties {
    private boolean enabled = true;
    private boolean autoMergeEnabled = true;
    private double autoMergeMinConfidence = 0.9;
    private int autoMergeMinOccurrences = 2;
}

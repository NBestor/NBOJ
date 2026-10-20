package com.onlinejudge.execution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.code-run")
@Data
public class CodeRunProperties {

    private boolean enabled = true;
    private int maxSourceBytes = 100 * 1024;
    private int maxStdinBytes = 64 * 1024;
    private int maxOutputBytes = 64 * 1024;
    private int maxRunsPerMinute = 10;
    private int maxConcurrentPerKey = 1;
    private int maxConcurrentGlobal = 4;
}

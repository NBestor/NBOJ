package com.onlinejudge.submission.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiDiagnosisExecutionConfiguration {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("application-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "aiDiagnosisBranchExecutor")
    public Executor aiDiagnosisBranchExecutor(
            @Value("${ai.workflow.branch-concurrency:4}") int concurrency,
            @Value("${ai.workflow.branch-queue-capacity:200}") int queueCapacity) {
        int poolSize = Math.max(2, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Math.max(20, queueCapacity));
        executor.setThreadNamePrefix("ai-diagnosis-branch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

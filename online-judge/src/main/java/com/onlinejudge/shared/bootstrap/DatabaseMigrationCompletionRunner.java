package com.onlinejudge.shared.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database-migration.exit-on-complete", havingValue = "true")
@Slf4j
public class DatabaseMigrationCompletionRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Database migration and Hibernate schema validation completed; exiting one-shot process.");
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}

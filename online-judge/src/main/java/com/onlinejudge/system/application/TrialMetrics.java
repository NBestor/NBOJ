package com.onlinejudge.system.application;

import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TrialMetrics {
    private final Counter loginFailures;
    private final Counter forbiddenRequests;
    private final Counter rosterMismatches;
    private final Counter submissionSuccesses;
    private final Counter quotaRejections;
    private final Counter aiSuccesses;
    private final Counter aiFailures;
    private final Timer aiLatency;

    public TrialMetrics(MeterRegistry registry, TeacherAccountRepository accounts) {
        Gauge.builder("trial.teacher.applications.pending", accounts,
                        repository -> repository.countByStatus(TeacherAccount.Status.PENDING))
                .description("Teacher applications waiting for review")
                .register(registry);
        loginFailures = Counter.builder("trial.teacher.login.failures").register(registry);
        forbiddenRequests = Counter.builder("trial.tenant.access.denied").register(registry);
        rosterMismatches = Counter.builder("trial.roster.match.failures").register(registry);
        submissionSuccesses = Counter.builder("trial.assignment.submissions.success").register(registry);
        quotaRejections = Counter.builder("trial.ai.quota.rejections").register(registry);
        aiSuccesses = Counter.builder("trial.ai.requests").tag("result", "success").register(registry);
        aiFailures = Counter.builder("trial.ai.requests").tag("result", "failure").register(registry);
        aiLatency = Timer.builder("trial.ai.latency")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofMinutes(2))
                .register(registry);
    }

    public void loginFailed() { loginFailures.increment(); }
    public void accessDenied() { forbiddenRequests.increment(); }
    public void rosterMismatch() { rosterMismatches.increment(); }
    public void submissionSucceeded() { submissionSuccesses.increment(); }
    public void quotaRejected() { quotaRejections.increment(); }

    public void aiCompleted(boolean success, long elapsedNanos) {
        (success ? aiSuccesses : aiFailures).increment();
        aiLatency.record(Duration.ofNanos(Math.max(0, elapsedNanos)));
    }
}

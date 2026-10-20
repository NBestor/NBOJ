package com.onlinejudge.problem.application;

import com.onlinejudge.problem.domain.Problem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemAccessPolicyTest {

    private final ProblemAccessPolicy policy = new ProblemAccessPolicy();

    @Test
    void onlyOwnerCanEditPrivateDraft() {
        UUID ownerId = UUID.randomUUID();
        Problem draft = Problem.builder()
                .ownerTeacherId(ownerId)
                .scope(Problem.Scope.PRIVATE)
                .versionState(Problem.VersionState.DRAFT)
                .build();

        assertThat(policy.canEdit(ownerId, draft)).isTrue();
        assertThat(policy.canEdit(UUID.randomUUID(), draft)).isFalse();

        draft.setVersionState(Problem.VersionState.PUBLISHED);
        assertThat(policy.canEdit(ownerId, draft)).isFalse();
    }

    @Test
    void anonymousCatalogOnlyContainsPublicPublishedProblems() {
        Problem publicProblem = Problem.builder()
                .scope(Problem.Scope.PUBLIC)
                .versionState(Problem.VersionState.PUBLISHED)
                .build();
        Problem sharedProblem = Problem.builder()
                .scope(Problem.Scope.SHARED)
                .versionState(Problem.VersionState.PUBLISHED)
                .build();

        assertThat(policy.isAnonymousCatalogVisible(publicProblem)).isTrue();
        assertThat(policy.isAnonymousCatalogVisible(sharedProblem)).isFalse();
    }
}

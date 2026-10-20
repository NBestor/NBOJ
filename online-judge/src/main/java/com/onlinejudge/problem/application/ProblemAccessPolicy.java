package com.onlinejudge.problem.application;

import com.onlinejudge.problem.domain.Problem;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class ProblemAccessPolicy {

    public boolean canEdit(UUID teacherId, Problem problem) {
        return problem != null
                && Objects.equals(teacherId, problem.getOwnerTeacherId())
                && problem.getScope() == Problem.Scope.PRIVATE
                && problem.getVersionState() == Problem.VersionState.DRAFT;
    }

    public boolean isAnonymousCatalogVisible(Problem problem) {
        return problem != null
                && problem.getScope() == Problem.Scope.PUBLIC
                && problem.getVersionState() == Problem.VersionState.PUBLISHED
                && problem.getArchivedAt() == null;
    }

    public boolean isTeacherVisible(UUID teacherId, Problem problem) {
        if (problem == null || problem.getArchivedAt() != null) return false;
        if (problem.getScope() == Problem.Scope.PUBLIC && problem.getVersionState() == Problem.VersionState.PUBLISHED) return true;
        if (problem.getScope() == Problem.Scope.SHARED && problem.getVersionState() == Problem.VersionState.PUBLISHED) return true;
        return Objects.equals(teacherId, problem.getOwnerTeacherId());
    }
}

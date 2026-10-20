package com.onlinejudge.problem.application;

import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.application.AuditService;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.dto.ProblemManageResponse;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemGovernanceService {
    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final ProblemAccessPolicy accessPolicy;
    private final CurrentTeacherContext currentTeacher;
    @Autowired(required = false)
    private AuditService audit;

    public List<ProblemManageResponse> teacherCatalog() {
        UUID teacherId = currentTeacher.requireTeacherId();
        return problems.findAllByOrderByIdAsc().stream()
                .filter(problem -> accessPolicy.isTeacherVisible(teacherId, problem))
                .map(this::response).toList();
    }

    public List<ProblemManageResponse> pendingReviews() {
        currentTeacher.requireAdmin();
        return problems.findByVersionStateOrderByCreatedAtAsc(Problem.VersionState.REVIEW_PENDING)
                .stream().map(this::response).toList();
    }

    @Transactional
    public ProblemManageResponse submitReview(Long problemId) {
        UUID teacherId = currentTeacher.requireTeacherId();
        Problem draft = require(problemId);
        if (!accessPolicy.canEdit(teacherId, draft)) throw forbidden("只能提交自己的私有草稿");
        Problem review = cloneVersion(draft, draft.getOwnerTeacherId(), Problem.Scope.SHARED,
                Problem.VersionState.REVIEW_PENDING);
        record(teacherId, "PROBLEM_REVIEW_SUBMITTED", review.getId(), "source=" + draft.getId());
        return response(review);
    }

    @Transactional
    public ProblemManageResponse revise(Long problemId) {
        UUID teacherId = currentTeacher.requireTeacherId();
        Problem source = require(problemId);
        if (!Objects.equals(source.getOwnerTeacherId(), teacherId)
                || source.getVersionState() != Problem.VersionState.PUBLISHED) {
            throw forbidden("只能修订自己已发布的题目");
        }
        Problem revision = cloneVersion(source, teacherId, Problem.Scope.PRIVATE, Problem.VersionState.DRAFT);
        record(teacherId, "PROBLEM_REVISION_CREATED", revision.getId(), "source=" + source.getId());
        return response(revision);
    }

    @Transactional
    public ProblemManageResponse approve(Long problemId) {
        TeacherPrincipal admin = currentTeacher.requireAdmin();
        Problem review = requireReview(problemId);
        review.setScope(Problem.Scope.SHARED);
        review.setVersionState(Problem.VersionState.PUBLISHED);
        review.setReviewedBy(admin.id());
        review.setReviewedAt(LocalDateTime.now());
        review.setReviewReason(null);
        Problem saved = problems.save(review);
        record(admin.id(), "PROBLEM_REVIEW_APPROVED", saved.getId(), null);
        return response(saved);
    }

    @Transactional
    public ProblemManageResponse reject(Long problemId, String reason) {
        TeacherPrincipal admin = currentTeacher.requireAdmin();
        Problem review = requireReview(problemId);
        review.setVersionState(Problem.VersionState.REJECTED);
        review.setReviewedBy(admin.id());
        review.setReviewedAt(LocalDateTime.now());
        review.setReviewReason(limit(reason, 500));
        Problem saved = problems.save(review);
        record(admin.id(), "PROBLEM_REVIEW_REJECTED", saved.getId(), "reason=" + limit(reason, 500));
        return response(saved);
    }

    @Transactional
    public ProblemManageResponse publishPublic(Long problemId) {
        TeacherPrincipal admin = currentTeacher.requireAdmin();
        Problem source = require(problemId);
        if (source.getScope() != Problem.Scope.SHARED || source.getVersionState() != Problem.VersionState.PUBLISHED) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_PROBLEM_STATE", "只有已发布共建题可以提升为公共题");
        }
        Problem publicVersion = cloneVersion(source, TeacherAccount.BOOTSTRAP_ADMIN_ID,
                Problem.Scope.PUBLIC, Problem.VersionState.PUBLISHED);
        publicVersion.setReviewedBy(admin.id());
        publicVersion.setReviewedAt(LocalDateTime.now());
        Problem saved = problems.save(publicVersion);
        record(admin.id(), "PROBLEM_PUBLISHED_PUBLIC", saved.getId(), "source=" + source.getId());
        return response(saved);
    }

    @Transactional
    public Problem freezeForAssignment(Long problemId, UUID teacherId) {
        Problem source = require(problemId);
        if (!accessPolicy.isTeacherVisible(teacherId, source)) throw forbidden("题目不在当前教师可见题库中");
        if (source.getScope() == Problem.Scope.PRIVATE && source.getVersionState() == Problem.VersionState.DRAFT) {
            if (!Objects.equals(source.getOwnerTeacherId(), teacherId)) throw forbidden("不能布置其他教师私有题");
            return cloneVersion(source, teacherId, Problem.Scope.PRIVATE, Problem.VersionState.FROZEN);
        }
        if (source.getVersionState() != Problem.VersionState.PUBLISHED
                && source.getVersionState() != Problem.VersionState.FROZEN) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_PROBLEM_STATE", "题目当前状态不可布置");
        }
        return source;
    }

    private Problem cloneVersion(Problem source, UUID ownerId, Problem.Scope scope, Problem.VersionState state) {
        int nextVersion = problems.findTopBySeriesIdOrderByVersionNoDesc(source.getSeriesId())
                .map(Problem::getVersionNo).orElse(source.getVersionNo()) + 1;
        Problem copy = problems.save(Problem.builder()
                .ownerTeacherId(ownerId).scope(scope).versionState(state)
                .seriesId(source.getSeriesId()).versionNo(nextVersion).sourceProblemId(source.getId())
                .title(source.getTitle()).description(source.getDescription()).difficulty(source.getDifficulty())
                .timeLimit(source.getTimeLimit()).memoryLimit(source.getMemoryLimit())
                .aiPromptDirection(source.getAiPromptDirection()).starterCode(source.getStarterCode())
                .knowledgePoints(copyList(source.getKnowledgePoints())).algorithmStrategies(copyList(source.getAlgorithmStrategies()))
                .commonMistakes(copyList(source.getCommonMistakes())).boundaryTypes(copyList(source.getBoundaryTypes())).build());
        for (TestCase testCase : testCases.findByProblemIdOrderByOrderIndexAsc(source.getId())) {
            testCases.save(TestCase.builder().problemId(copy.getId()).input(testCase.getInput())
                    .expectedOutput(testCase.getExpectedOutput()).isHidden(testCase.getIsHidden())
                    .orderIndex(testCase.getOrderIndex()).build());
        }
        return copy;
    }

    private Problem require(Long id) {
        return problems.findById(id).orElseThrow(() ->
                new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "题目不存在"));
    }

    private Problem requireReview(Long id) {
        Problem problem = require(id);
        if (problem.getVersionState() != Problem.VersionState.REVIEW_PENDING) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_PROBLEM_STATE", "题目不在待审核状态");
        }
        return problem;
    }

    private ProblemManageResponse response(Problem problem) {
        return ProblemManageResponse.from(problem, testCases.findByProblemIdOrderByOrderIndexAsc(problem.getId()));
    }

    private List<String> copyList(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
    private String limit(String value, int max) { String text = value == null ? "" : value.trim(); return text.length() > max ? text.substring(0, max) : text; }
    private PlatformApiException forbidden(String message) { return new PlatformApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message); }
    private void record(UUID actor, String event, Long target, String detail) {
        if (audit != null) audit.record(actor, event, "PROBLEM", target, detail, null);
    }
}

import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, BookOpen, CircleCheck, ClipboardList, LogIn, Play } from "lucide-react";
import { api, ApiError } from "../../shared/api/client";
import type { Assignment, ProblemCatalogItem, StudentProfile, StudentRecommendation, StudentRecommendationItem } from "../../shared/api/types";
import { useTranslation } from "../../shared/i18n";
import { clearActiveStudent, loadStudent, onActiveStudentChange } from "../../shared/storage";

function visibleAssignmentTitle(assignment: Assignment) {
  return assignment.title.includes("试点任务") ? "课堂编程作业" : assignment.title;
}

function latestTeacherAssignments(assignments: Assignment[]) {
  return assignments.filter(item => item.status !== "DRAFT");
}

type DifficultyKey = "easy" | "medium" | "hard" | "unknown";

function problemDifficultyKey(value?: string | null): DifficultyKey {
  switch (String(value || "").toUpperCase()) {
    case "EASY":
      return "easy";
    case "MEDIUM":
      return "medium";
    case "HARD":
      return "hard";
    default:
      return "unknown";
  }
}

function pickStarterProblems(problems: ProblemCatalogItem[]) {
  const selected: ProblemCatalogItem[] = [];
  const hasProblem = (problem: ProblemCatalogItem) => selected.some(item => item.id === problem.id);
  const takeDifficulty = (difficulty: string) => {
    const problem = problems.find(item => !hasProblem(item) && String(item.difficulty || "").toUpperCase() === difficulty);
    if (problem) {
      selected.push(problem);
    }
  };

  ["EASY", "EASY", "MEDIUM", "HARD"].forEach(takeDifficulty);
  problems.forEach(problem => {
    if (selected.length < 4 && !hasProblem(problem)) {
      selected.push(problem);
    }
  });
  return selected.slice(0, 4);
}

interface AssignmentProgress {
  completedTasks: number;
  totalTasks: number;
}

export default function StudentPage() {
  const { t } = useTranslation();
  const [student, setStudent] = useState<StudentProfile | null>(() => loadStudent());
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [progressByAssignmentId, setProgressByAssignmentId] = useState<Record<number, AssignmentProgress | null>>({});
  const [publicProblems, setPublicProblems] = useState<ProblemCatalogItem[] | null>(null);
  const [assignmentLoading, setAssignmentLoading] = useState(false);
  const [recommendation, setRecommendation] = useState<StudentRecommendation | null>(null);
  const [recommendationLoading, setRecommendationLoading] = useState(false);
  const [recommendationFailed, setRecommendationFailed] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);

  useEffect(() => onActiveStudentChange(() => setStudent(loadStudent())), []);

  useEffect(() => {
    let ignore = false;
    api.problemCatalog()
      .then(result => {
        if (!ignore) setPublicProblems(result);
      })
      .catch(() => {
        if (!ignore) {
          setPublicProblems([]);
          setFailed(t("studentHome.errors.publicBank"));
        }
      });
    return () => {
      ignore = true;
    };
  }, [t]);

  useEffect(() => {
    if (!student) {
      setAssignments([]);
      setProgressByAssignmentId({});
      setAssignmentLoading(false);
      return;
    }
    let ignore = false;
    setAssignmentLoading(true);
    api.studentAssignments(student.id)
      .then(async result => {
        if (ignore) return;
        setAssignments(result);
        const progressEntries = await Promise.all(
          latestTeacherAssignments(result).map(async assignment => {
            try {
              const trajectory = await api.studentTrajectory(assignment.id, student.id);
              return [assignment.id, {
                completedTasks: trajectory.completedTasks,
                totalTasks: trajectory.totalTasks
              }] as const;
            } catch {
              return [assignment.id, null] as const;
            }
          })
        );
        if (!ignore) {
          setProgressByAssignmentId(Object.fromEntries(progressEntries));
        }
      })
      .catch(error => {
        if (!ignore && error instanceof ApiError && error.status === 401) {
          clearActiveStudent();
          setStudent(null);
          return;
        }
        if (!ignore) setFailed(t("studentHome.errors.assignments"));
      })
      .finally(() => {
        if (!ignore) setAssignmentLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [student, t]);

  useEffect(() => {
    if (!student) {
      setRecommendation(null);
      setRecommendationLoading(false);
      setRecommendationFailed(false);
      return;
    }
    let ignore = false;
    setRecommendationLoading(true);
    setRecommendationFailed(false);
    api.studentRecommendations(student.id)
      .then(result => {
        if (!ignore) setRecommendation(result);
      })
      .catch(() => {
        if (!ignore) {
          setRecommendation(null);
          setRecommendationFailed(true);
        }
      })
      .finally(() => {
        if (!ignore) setRecommendationLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [student]);

  const visibleAssignments = useMemo(() => latestTeacherAssignments(assignments), [assignments]);
  const progressFor = (assignment: Assignment) => progressByAssignmentId[assignment.id] ?? null;
  const problemCount = publicProblems?.length ?? null;
  const publicDifficultyCounts = useMemo(() => {
    const counts = { EASY: 0, MEDIUM: 0, HARD: 0 };
    (publicProblems || []).forEach(problem => {
      const difficulty = String(problem.difficulty || "").toUpperCase();
      if (difficulty === "EASY" || difficulty === "MEDIUM" || difficulty === "HARD") {
        counts[difficulty] += 1;
      }
    });
    return counts;
  }, [publicProblems]);
  const starterProblems = useMemo(() => pickStarterProblems(publicProblems || []), [publicProblems]);
  const publicStartPath = starterProblems[0]
    ? `/student/assignments/public/problems/${starterProblems[0].id}`
    : "/student/assignments/public";

  function recommendationPath(item: StudentRecommendationItem) {
    if (!student) return null;
    const query = new URLSearchParams({ studentProfileId: String(student.id) });
    if (item.recommendationToken) query.set("recommendationToken", item.recommendationToken);
    if (item.problemId && item.assignmentId) return `/student/assignments/${item.assignmentId}/problems/${item.problemId}?${query}`;
    if (item.problemId) return `/student/assignments/public/problems/${item.problemId}?${query}`;
    if (item.assignmentId) return `/student/assignments/${item.assignmentId}`;
    return null;
  }

  function recommendationAction(item: StudentRecommendationItem) {
    const path = recommendationPath(item);
    if (!path) return null;
    return (
      <Link
        className="student-next-learning__cta"
        to={path}
        onClick={() => {
          if (student && item.recommendationToken) void api.recordRecommendationEvent(student.id, item.recommendationToken).catch(() => undefined);
        }}
        aria-label={t("studentHome.nextLearning.openAria", { title: item.title })}
      >
        {item.actionLabel || t("studentHome.nextLearning.open")}<ArrowRight size={16} aria-hidden="true" />
      </Link>
    );
  }

  function recommendationOutcome(item: StudentRecommendationItem) {
    switch (item.actionOutcome) {
      case "CONTRACT_FULFILLED":
        return t("studentHome.nextLearning.outcome.fulfilled");
      case "UNRESOLVED_SAME_FOCUS":
      case "TEACHER_INTERVENTION_NEEDED":
        return t("studentHome.nextLearning.outcome.unresolved");
      case "WAITING_DIAGNOSIS":
      case "NO_FOLLOWUP_SUBMISSION":
        return t("studentHome.nextLearning.outcome.waiting");
      default:
        return null;
    }
  }

  function renderNextLearning() {
    const items = recommendation?.recommendations || [];
    const primary = items[0];
    const fallbackAssignment = visibleAssignments[0];
    const fallbackPath = fallbackAssignment ? `/student/assignments/${fallbackAssignment.id}` : "/student/assignments/public";
    return (
      <section className="student-home-zone student-next-learning" data-home-zone="continue" aria-labelledby="student-next-learning-heading">
        <header className="student-next-learning__head">
          <h2 id="student-next-learning-heading">{t("studentHome.nextLearning.title")}</h2>
        </header>
        {recommendationLoading ? (
          <div className="student-next-learning__state" role="status" aria-live="polite">
            <div><strong>{t("studentHome.nextLearning.loading")}</strong><p>{t("studentHome.nextLearning.loadingHint")}</p></div>
          </div>
        ) : recommendationFailed || !primary ? (
          <div className="student-next-learning__state">
            <div>
              <strong>{t(recommendationFailed ? "studentHome.nextLearning.fallbackTitle" : "studentHome.nextLearning.empty")}</strong>
              <p>{t(recommendationFailed ? "studentHome.nextLearning.fallbackHint" : "studentHome.nextLearning.emptyHint")}</p>
            </div>
            <Link to={fallbackPath}>{t("studentHome.nextLearning.keepLearning")}<ArrowRight size={16} aria-hidden="true" /></Link>
          </div>
        ) : (
          <article className="student-next-learning__primary">
            <div className="student-next-learning__summary">
              <h3>{primary.title}</h3>
              {primary.reason && <p>{primary.reason}</p>}
              {recommendationOutcome(primary) && (
                <span className="student-next-learning__status">{recommendationOutcome(primary)}</span>
              )}
              {primary.expectedCompletionSignal && <small>{t("studentHome.nextLearning.goal", { goal: primary.expectedCompletionSignal })}</small>}
            </div>
            {recommendationAction(primary)}
          </article>
        )}
      </section>
    );
  }

  function renderSelfPractice() {
    return (
      <section className="student-home-zone student-self-practice" data-home-zone="practice" aria-labelledby="student-self-practice-heading">
        <header className="student-home-zone__head">
          <h2 id="student-self-practice-heading">{t("studentHome.selfPractice.title")}</h2>
        </header>
        <Link className="student-self-practice-row" to="/student/assignments/public">
          <span className="student-self-practice-row__icon" aria-hidden="true"><BookOpen size={19} /></span>
          <span className="student-self-practice-row__main">
            <strong>{t("studentHome.public.title")}</strong>
            <small>{problemCount !== null ? t("studentHome.selfPractice.meta", { count: problemCount }) : t("studentHome.loading.publicBank")}</small>
          </span>
          <span className="student-self-practice-row__action">{t("studentHome.selfPractice.action")}</span>
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
      </section>
    );
  }

  function assignmentState(assignment: Assignment) {
    const progress = progressFor(assignment);
    if (progress && progress.totalTasks > 0 && progress.completedTasks >= progress.totalTasks) {
      return { key: "completed", label: t("studentHome.dashboard.completed") };
    }
    if (assignment.status === "CLOSED") {
      return { key: "closed", label: t("studentHome.dashboard.closed") };
    }
    if (progress?.completedTasks === 0) {
      return { key: "not-started", label: t("studentHome.dashboard.notStarted") };
    }
    return { key: "active", label: t("studentHome.dashboard.active") };
  }

  function renderPublicPractice(headingId: string, sectionId?: string) {
    return (
      <section id={sectionId} className="student-guest-practice" aria-labelledby={headingId}>
        <header className="student-guest-practice__head">
          <span className="student-assignment-board__icon" aria-hidden="true"><BookOpen size={20} /></span>
          <div>
            <h2 id={headingId}>{t("studentHome.guestPreview.today")}</h2>
          </div>
        </header>
        <div className="student-guest-practice__panel">
          <div className="student-guest-practice__summary">
            <span className="student-guest-practice__icon" aria-hidden="true"><BookOpen size={21} /></span>
            <span className="student-guest-practice__main">
              <strong>{t("studentHome.public.title")}</strong>
              <small>{problemCount !== null ? t("studentHome.public.meta", { count: problemCount }) : t("studentHome.loading.publicBank")}</small>
              <span>{t("studentHome.public.description")}</span>
            </span>
            <span className="student-guest-practice__difficulty" aria-label={t("studentHome.guestPreview.difficultyAria")}>
              <small>{t("studentHome.guestPreview.difficulty")}</small>
              <strong>{t("studentHome.guestPreview.easy", { count: publicDifficultyCounts.EASY })}</strong>
              <strong>{t("studentHome.guestPreview.medium", { count: publicDifficultyCounts.MEDIUM })}</strong>
              <strong>{t("studentHome.guestPreview.hard", { count: publicDifficultyCounts.HARD })}</strong>
            </span>
            <span className="student-guest-practice__actions">
              <Link className="student-guest-practice__action student-guest-practice__action--primary" to={publicStartPath}>
                <Play size={15} fill="currentColor" aria-hidden="true" />
                {t("studentHome.public.cta")}
              </Link>
              <Link className="student-guest-practice__action student-guest-practice__action--secondary" to="/student/assignments/public">
                {t("studentHome.guestPreview.viewAll")}
              </Link>
            </span>
          </div>

          {starterProblems.length ? (
            <div className="student-guest-starters">
              <h3>{t("studentHome.guestPreview.starterTitle")}</h3>
              <nav className="student-guest-starters__grid" aria-label={t("studentHome.guestPreview.starterAria")}>
                {starterProblems.map(problem => {
                  const difficultyKey = problemDifficultyKey(problem.difficulty);
                  return (
                    <Link
                      className={`student-guest-starter-card is-${difficultyKey}`}
                      to={`/student/assignments/public/problems/${problem.id}`}
                      key={problem.id}
                    >
                      <span className="student-guest-starter-card__badge">{t(`studentPublic.difficulty.${difficultyKey}`)}</span>
                      <strong>{problem.title}</strong>
                      <small>{t(`studentHome.guestPreview.starterHint.${difficultyKey}`)}</small>
                      <ArrowRight size={16} aria-hidden="true" />
                    </Link>
                  );
                })}
              </nav>
            </div>
          ) : null}
        </div>
      </section>
    );
  }

  return (
    <div className="student-page student-home student-home--assignments student-task-home">
      {failed && <div className="alert alert--error">{failed}</div>}

      {!student ? (
        <>
          {renderPublicPractice("student-guest-practice-heading", "assignments")}

          <section className="student-guest-login-card" aria-labelledby="student-guest-login-heading">
            <span className="student-assignment-board__icon" aria-hidden="true"><LogIn size={19} /></span>
            <div>
              <h2 id="student-guest-login-heading">{t("studentHome.login.title")}</h2>
              <p>{t("studentHome.login.description")}</p>
              <small>{t("studentHome.login.meta")}</small>
            </div>
            <Link className="student-guest-login-action" to="/student/login">
              <LogIn size={16} aria-hidden="true" />
              {t("studentHome.login.cta")}
            </Link>
          </section>
        </>
      ) : (
        <div className="student-home-sections">
          {renderNextLearning()}
          <section
            id="assignments"
            className="student-home-zone student-assignment-board student-learning-task-board student-classroom-section"
            data-home-zone="classroom"
            aria-labelledby="student-assignment-heading"
          >
          <header className="student-assignment-board__head">
            <span className="student-assignment-board__icon" aria-hidden="true"><ClipboardList size={20} /></span>
            <h2 id="student-assignment-heading">{t("studentHome.dashboard.classroom")}</h2>
          </header>

          <nav className="student-assignment-table student-assignment-table--direct student-learning-task-list" aria-label={t("studentHome.dashboard.classroom")}>
            {assignmentLoading ? (
              <div className="student-assignment-board__empty" role="status" aria-live="polite">
                {t("studentHome.loading.assignments")}
              </div>
            ) : visibleAssignments.length ? (
              <>
                {visibleAssignments.map(assignment => {
                  const progress = progressFor(assignment);
                  const state = assignmentState(assignment);
                  const totalTasks = progress?.totalTasks ?? assignment.tasks?.length ?? 0;
                  const completedTasks = progress?.completedTasks ?? 0;
                  return (
                    <Link
                      className="student-entry-link student-assignment-row student-assignment-row--direct"
                      to={`/student/assignments/${assignment.id}`}
                      key={assignment.id}
                      aria-label={visibleAssignmentTitle(assignment)}
                    >
                      <span className="student-assignment-row__icon" aria-hidden="true">
                        {state.key === "completed" ? <CircleCheck size={19} /> : <ClipboardList size={19} />}
                      </span>
                      <span className="student-assignment-row__main">
                        <strong>{visibleAssignmentTitle(assignment)}</strong>
                      </span>
                      <span className="student-assignment-row__status">
                        <span><i className={`student-assignment-row__dot student-assignment-row__dot--${state.key}`} />{state.label}</span>
                      </span>
                      <span className="student-assignment-row__progress" aria-label={t("studentHome.dashboard.progressAria", { completed: completedTasks, total: totalTasks })}>
                        <progress value={completedTasks} max={Math.max(totalTasks, 1)} />
                        <strong>{progress ? `${completedTasks}/${totalTasks}` : `-/${totalTasks}`}</strong>
                      </span>
                      <ArrowRight className="student-assignment-row__chevron" size={18} aria-hidden="true" />
                    </Link>
                  );
                })}
              </>
            ) : (
              <div className="student-assignment-board__empty">
                <strong>{t("studentHome.emptyAssignments.title")}</strong>
              </div>
            )}
          </nav>
          </section>
          {renderSelfPractice()}
        </div>
      )}

    </div>
  );
}

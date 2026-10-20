import { lazy, ReactNode, Suspense, useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { LucideIcon } from "lucide-react";
import {
  ArrowRight,
  BarChart3,
  BookOpenCheck,
  Box,
  CheckCircle2,
  Code2,
  Gauge,
  Image as ImageIcon,
  LogIn,
  Menu,
  Moon,
  RotateCcw,
  ShieldCheck,
  Sun,
  UserRound,
  UsersRound,
  X
} from "lucide-react";
import routeHubCodePreview from "./assets/route-hub-code-preview.png";
import TeacherAuthGate from "./features/teacher/TeacherAuthGate";
import PortalAuthGate from "./features/admin/PortalAuthGate";
import { TeacherShell } from "./features/teacher/TeacherShell";
import { api } from "./shared/api/client";
import { AccountSessionProvider } from "./shared/auth/AccountSessionContext";
import { useTranslation } from "./shared/i18n";
import { clearActiveStudent, loadStudent, onActiveStudentChange } from "./shared/storage";
import { Button } from "./shared/ui/Button";
import { EmptyState } from "./shared/ui/EmptyState";
import "./features/teacher-analytics/TeacherAnalytics.css";

const StudentPage = lazy(() => import("./features/student/StudentPage"));
const StudentLoginPage = lazy(() => import("./features/student/StudentLoginPage"));
const StudentAssignmentPage = lazy(() => import("./features/student/StudentAssignmentPage"));
const StudentAssignmentRankingPage = lazy(() => import("./features/student/StudentAssignmentRankingPage"));
const StudentAssignmentSubmissionsPage = lazy(() => import("./features/student/StudentAssignmentSubmissionsPage"));
const ProblemPage = lazy(() => import("./features/problem/ProblemPage"));
const AssignmentCreatePage = lazy(() => import("./features/teacher/AssignmentCreatePage"));
const TeacherManagementPage = lazy(() => import("./features/teacher/TeacherManagementPage"));
const TaskEditorPage = lazy(() => import("./features/task-editor/TaskEditorPage"));
const TeacherAnalyticsLandingPage = lazy(() => import("./features/teacher-analytics/pages/TeacherAnalyticsLandingPage"));
const ClassAnalyticsPage = lazy(() => import("./features/teacher-analytics/pages/ClassAnalyticsPage"));
const AssignmentAnalyticsPage = lazy(() => import("./features/teacher-analytics/pages/AssignmentAnalyticsPage"));
const ProblemAnalyticsPage = lazy(() => import("./features/teacher-analytics/pages/ProblemAnalyticsPage"));
const PlatformAdminPage = lazy(() => import("./features/admin/PlatformAdminPage"));
const SchoolAdminPage = lazy(() => import("./features/admin/SchoolAdminPage"));
const StudentProblemAnalyticsPage = lazy(() => import("./features/teacher-analytics/pages/StudentProblemAnalyticsPage"));

type Theme = "light" | "dark";
type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  activeWhen?: (pathname: string) => boolean;
  noActive?: boolean;
};

function loadTheme(): Theme {
  try {
    return localStorage.getItem("wzai:theme") === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
}

function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem("wzai:theme", theme);
  } catch {
    // Theme still applies for the current page even when storage is blocked.
  }
}

function useTheme() {
  const [theme, setTheme] = useState<Theme>(loadTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    saveTheme(theme);
  }, [theme]);

  return { theme, setTheme };
}

function RouteHubPage() {
  const { t } = useTranslation();

  return (
    <div className="role-entry-page route-hub-page route-hub-page--experience">
      <section className="route-hub-experience" aria-labelledby="route-hub-title">
        <div className="route-hub-hero-copy">
          <p className="eyebrow">{t("routeHub.welcome")}</p>
          <h1 id="route-hub-title">
            {t("routeHub.headlineStart")}<br />
            {t("routeHub.headlineEnd")}
          </h1>
          <p className="route-hub-summary">{t("routeHub.summary")}</p>

          <nav className="route-hub-role-actions" aria-label={t("routeHub.roleAria")}>
            <NavLink to="/student" className="route-hub-role-action route-hub-role-action--primary">
              <BookOpenCheck size={19} aria-hidden="true" />
              <span>{t("routeHub.studentCta")}</span>
            </NavLink>
            <NavLink to="/teacher" className="route-hub-role-action">
              <UsersRound size={19} aria-hidden="true" />
              <span>{t("routeHub.teacherCta")}</span>
            </NavLink>
            <NavLink to="/school-admin/login" className="route-hub-role-action">
              <ShieldCheck size={19} aria-hidden="true" />
              <span>{t("routeHub.schoolAdminCta")}</span>
            </NavLink>
          </nav>

          <div className="route-hub-feature-list">
            <div>
              <Box size={18} aria-hidden="true" />
              <span><strong>{t("routeHub.features.languagesTitle")}</strong><small>{t("routeHub.features.languagesDetail")}</small></span>
            </div>
            <div>
              <Gauge size={18} aria-hidden="true" />
              <span><strong>{t("routeHub.features.feedbackTitle")}</strong><small>{t("routeHub.features.feedbackDetail")}</small></span>
            </div>
            <div>
              <BarChart3 size={18} aria-hidden="true" />
              <span><strong>{t("routeHub.features.dataTitle")}</strong><small>{t("routeHub.features.dataDetail")}</small></span>
            </div>
          </div>

        </div>

        <figure className="route-hub-preview-frame" aria-label={t("routeHub.preview.alt")}>
          <figcaption>
            <span className="route-hub-preview-frame__badge"><ImageIcon size={14} aria-hidden="true" />{t("routeHub.preview.badge")}</span>
          </figcaption>
          <div className="route-hub-preview-frame__image">
            <img
              src={routeHubCodePreview}
              alt={t("routeHub.preview.alt")}
              width={937}
              height={619}
              draggable={false}
            />
          </div>
        </figure>
      </section>

      <section className="route-hub-learning-loop" aria-labelledby="route-hub-loop-title">
        <h2 id="route-hub-loop-title">{t("routeHub.loop.title")}</h2>
        <div className="route-hub-loop-grid">
          <article className="route-hub-loop-step">
            <span className="route-hub-loop-step__number">1</span>
            <Code2 size={20} aria-hidden="true" />
            <div><strong>{t("routeHub.loop.practice")}</strong><small>{t("routeHub.loop.practiceDetail")}</small></div>
            <span className="route-hub-loop-step__evidence"><code>def two_sum(nums, target)</code></span>
          </article>
          <ArrowRight className="route-hub-loop-arrow" size={22} aria-hidden="true" />
          <article className="route-hub-loop-step">
            <span className="route-hub-loop-step__number">2</span>
            <CheckCircle2 size={20} aria-hidden="true" />
            <div><strong>{t("routeHub.loop.judge")}</strong><small>{t("routeHub.loop.judgeDetail")}</small></div>
            <span className="route-hub-loop-step__evidence"><CheckCircle2 size={14} aria-hidden="true" />{t("routeHub.loop.judgeEvidence")}<b>32 ms</b></span>
          </article>
          <ArrowRight className="route-hub-loop-arrow" size={22} aria-hidden="true" />
          <article className="route-hub-loop-step">
            <span className="route-hub-loop-step__number">3</span>
            <RotateCcw size={20} aria-hidden="true" />
            <div><strong>{t("routeHub.loop.review")}</strong><small>{t("routeHub.loop.reviewDetail")}</small></div>
            <span className="route-hub-loop-step__evidence"><strong>{t("routeHub.loop.reviewEvidence")}</strong><code>O(n)</code></span>
          </article>
        </div>
      </section>
    </div>
  );
}

function TeacherRoute({ children }: { children: ReactNode }) {
  return (
    <TeacherAuthGate>
      <TeacherShell>{children}</TeacherShell>
    </TeacherAuthGate>
  );
}

function AdminRoute({ portal, children }: { portal: "PLATFORM_ADMIN" | "SCHOOL_ADMIN"; children: ReactNode }) {
  return <PortalAuthGate portal={portal}>{children}</PortalAuthGate>;
}

function LegacyAssignmentRedirect({ level }: { level: "assignment" | "problem" | "student" }) {
  const location = useLocation();
  const match = location.pathname.match(/\/teacher\/assignment\/([^/]+)(?:\/problems\/([^/]+))?(?:\/students\/([^/]+))?/);
  const assignmentId = match?.[1];
  const problemId = match?.[2];
  const studentProfileId = match?.[3];
  const search = location.search || "";
  if (!assignmentId) {
    return <Navigate to="/teacher/classes" replace />;
  }
  if (level !== "assignment" && problemId) {
    if (level === "student" && studentProfileId) {
      return <Navigate to={`/teacher/classes/0/assignments/${assignmentId}/problems/${problemId}/students/${studentProfileId}${search}`} replace />;
    }
    return <Navigate to={`/teacher/classes/0/assignments/${assignmentId}/problems/${problemId}${search}`} replace />;
  }
  return <Navigate to={`/teacher/classes/0/assignments/${assignmentId}${search}`} replace />;
}

function Header() {
  const { theme, setTheme } = useTheme();
  const { locale, setLocale, t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [student, setStudent] = useState(() => loadStudent());
  const location = useLocation();
  const isProblemPage =
    /^\/student\/assignments\/[^/]+\/problems\//.test(location.pathname);
  const isStudentContext = location.pathname.startsWith("/student") || isProblemPage;
  const isTeacherContext = location.pathname.startsWith("/teacher") || location.pathname.startsWith("/task-editor");
  const navItems: NavItem[] = [
    {
      to: "/student",
      label: t("common.studentSide"),
      icon: BookOpenCheck,
      activeWhen: (pathname: string) => pathname.startsWith("/student")
    },
    {
      to: "/teacher",
      label: t("common.teacherSide"),
      icon: UsersRound,
      activeWhen: (pathname: string) => pathname.startsWith("/teacher") || pathname.startsWith("/task-editor")
    }
  ];

  useEffect(() => {
    setOpen(false);
    setStudent(loadStudent());
  }, [location.pathname]);

  useEffect(() => onActiveStudentChange(() => setStudent(loadStudent())), []);

  useEffect(() => {
    if (!isStudentContext || !student) return;
    void api.studentSession()
      .then(current => setStudent(current))
      .catch(() => {
        clearActiveStudent();
        setStudent(null);
      });
  }, [isStudentContext, location.pathname, student?.id]);

  function signOut() {
    void api.studentLogout().catch(() => undefined);
    clearActiveStudent();
    setStudent(null);
  }

  const hasNav = navItems.length > 0;
  const headerClassName = [
    "app-header",
    open && hasNav ? "is-open" : "",
    isProblemPage ? "app-header--practice" : "",
    isTeacherContext ? "app-header--teacher-context" : "",
    hasNav ? "" : "app-header--no-nav"
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <header className={headerClassName}>
      <NavLink to="/" className="brand" aria-label={t("common.appName")}>
        <span className="brand__mark">
          <BookOpenCheck size={24} />
        </span>
        <span>
          <strong>{t("common.appName")}</strong>
        </span>
      </NavLink>
      {hasNav ? (
        <>
          <button
            type="button"
            className="nav-toggle"
            aria-label={open ? t("common.closeNavigation") : t("common.openNavigation")}
            aria-expanded={open}
            aria-controls="main-navigation"
            onClick={() => setOpen(value => !value)}
          >
            {open ? <X size={19} /> : <Menu size={19} />}
          </button>
          <nav className="top-nav" id="main-navigation" aria-label={t("common.mainNavigation")}>
            {navItems.map(item => (
              <NavLink
                key={`${item.label}-${item.to}`}
                to={item.to}
                className={({ isActive }) =>
                  !item.noActive && (isActive || item.activeWhen?.(location.pathname)) ? "top-nav__link is-active" : "top-nav__link"
                }
              >
                <item.icon size={17} />
                <span>{item.label}</span>
              </NavLink>
            ))}
          </nav>
        </>
      ) : null}
      <div className="header-actions">
        {isStudentContext ? (
          student ? (
            <div className="header-student-menu" aria-label="学生身份">
              <NavLink to="/student/login" className="header-student-chip">
                <UserRound size={16} />
                <span>{student.displayName}</span>
              </NavLink>
              <Button type="button" variant="ghost" className="header-signout-button" onClick={signOut}>
                {t("common.logout")}
              </Button>
            </div>
          ) : (
            <NavLink to="/student/login" className="header-login-link">
              <LogIn size={17} />
              <span>{t("common.login")}</span>
            </NavLink>
          )
        ) : null}
        <button
          type="button"
          className="language-toggle"
          aria-label={t("common.language")}
          title={t("common.language")}
          onClick={() => setLocale(locale === "zh" ? "en" : "zh")}
        >
          <span>{locale === "zh" ? "中" : "EN"}</span>
        </button>
        <button
          type="button"
          className="theme-toggle"
          aria-label={t("common.theme.toggle")}
          title={theme === "dark" ? t("common.theme.toLight") : t("common.theme.toDark")}
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
        >
          {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          <span>{theme === "dark" ? t("common.theme.light") : t("common.theme.dark")}</span>
        </button>
      </div>
    </header>
  );
}

export default function App() {
  const { t } = useTranslation();

  return (
    <AccountSessionProvider>
      <div className="app-shell">
      <a className="skip-link" href="#main-content">
        {t("common.skipToMain")}
      </a>
      <Header />
      <main className="main-shell" id="main-content">
        <Suspense fallback={<EmptyState title={t("common.loadingPage")} live />}>
          <Routes>
            <Route path="/" element={<RouteHubPage />} />
            <Route path="/student" element={<StudentPage />} />
            <Route path="/student/login" element={<StudentLoginPage />} />
            <Route path="/student/assignments/:assignmentId" element={<StudentAssignmentPage />} />
            <Route path="/student/assignments/:assignmentId/ranking" element={<StudentAssignmentRankingPage />} />
            <Route path="/student/assignments/:assignmentId/submissions" element={<StudentAssignmentSubmissionsPage />} />
            <Route path="/student/assignments/:assignmentId/problems/:problemId" element={<ProblemPage />} />
            <Route path="/teacher" element={<TeacherRoute><Navigate to="/teacher/classes" replace /></TeacherRoute>} />
            <Route path="/teacher/login" element={<TeacherRoute><Navigate to="/teacher/classes" replace /></TeacherRoute>} />
            <Route path="/teacher/classes" element={<TeacherRoute><TeacherAnalyticsLandingPage /></TeacherRoute>} />
            <Route path="/teacher/classes/:classId" element={<TeacherRoute><ClassAnalyticsPage /></TeacherRoute>} />
            <Route path="/teacher/classes/:classId/assignments/:assignmentId" element={<TeacherRoute><AssignmentAnalyticsPage /></TeacherRoute>} />
            <Route path="/teacher/classes/:classId/assignments/:assignmentId/problems/:problemId" element={<TeacherRoute><ProblemAnalyticsPage /></TeacherRoute>} />
            <Route path="/teacher/classes/:classId/assignments/:assignmentId/problems/:problemId/students/:studentProfileId" element={<TeacherRoute><StudentProblemAnalyticsPage /></TeacherRoute>} />
            <Route path="/teacher/manage" element={<TeacherRoute><Navigate to="/teacher/manage/classes" replace /></TeacherRoute>} />
            <Route path="/teacher/manage/classes" element={<TeacherRoute><TeacherManagementPage section="classes" /></TeacherRoute>} />
            <Route path="/teacher/manage/problems" element={<TeacherRoute><TeacherManagementPage section="problems" /></TeacherRoute>} />
            <Route path="/teacher/manage/ai-library" element={<TeacherRoute><TeacherManagementPage section="ai-library" /></TeacherRoute>} />
            <Route path="/teacher/manage/system" element={<TeacherRoute><TeacherManagementPage section="system" /></TeacherRoute>} />
            <Route path="/platform-admin/login" element={<AdminRoute portal="PLATFORM_ADMIN"><Navigate to="/platform-admin" replace /></AdminRoute>} />
            <Route path="/platform-admin/*" element={<AdminRoute portal="PLATFORM_ADMIN"><PlatformAdminPage /></AdminRoute>} />
            <Route path="/school-admin/login" element={<AdminRoute portal="SCHOOL_ADMIN"><Navigate to="/school-admin" replace /></AdminRoute>} />
            <Route path="/school-admin/*" element={<AdminRoute portal="SCHOOL_ADMIN"><SchoolAdminPage /></AdminRoute>} />
            <Route path="/teacher-management" element={<Navigate to="/teacher/manage/classes" replace />} />
            <Route path="/class-overview" element={<Navigate to="/teacher/classes" replace />} />
            <Route path="/task-editor" element={<TeacherRoute><TaskEditorPage /></TeacherRoute>} />
            <Route path="/teacher/assignment/new" element={<TeacherRoute><AssignmentCreatePage /></TeacherRoute>} />
            <Route path="/teacher/assignment/:assignmentId/problems/:problemId/students/:studentProfileId" element={<TeacherRoute><LegacyAssignmentRedirect level="student" /></TeacherRoute>} />
            <Route path="/teacher/assignment/:assignmentId/problems/:problemId" element={<TeacherRoute><LegacyAssignmentRedirect level="problem" /></TeacherRoute>} />
            <Route path="/teacher/assignment/:assignmentId" element={<TeacherRoute><LegacyAssignmentRedirect level="assignment" /></TeacherRoute>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </main>
      </div>
    </AccountSessionProvider>
  );
}

import type { StudentProfile } from "./api/types";

const GLOBAL_STUDENT_KEY = "wzai:student";
const ASSIGNMENT_STUDENT_PREFIX = "wzai:student:";
const LAST_INVITE_CODE_KEY = "wzai:lastInviteCode";
const LAST_PUBLIC_PROBLEM_KEY = "wzai:lastPublicProblem";
const DRAFT_PREFIX = "wzai:draft:";
const STUDENT_CHANGE_EVENT = "wzai:student-change";

function storageArea(kind: "local" | "session"): Storage | null {
  if (typeof window === "undefined") {
    return null;
  }
  return kind === "local" ? window.localStorage : window.sessionStorage;
}

function storageGet(kind: "local" | "session", key: string): string | null {
  try {
    return storageArea(kind)?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function storageSet(kind: "local" | "session", key: string, value: string): void {
  try {
    storageArea(kind)?.setItem(key, value);
  } catch {
    // Storage can be blocked in private or embedded browser contexts.
  }
}

function storageRemove(kind: "local" | "session", key: string): void {
  try {
    storageArea(kind)?.removeItem(key);
  } catch {
    // Storage can be blocked in private or embedded browser contexts.
  }
}

function storageKeys(kind: "local" | "session"): string[] {
  try {
    const area = storageArea(kind);
    if (!area) {
      return [];
    }
    return Array.from({ length: area.length }, (_, index) => area.key(index)).filter((key): key is string => Boolean(key));
  } catch {
    return [];
  }
}

function emitStudentChange(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(STUDENT_CHANGE_EVENT));
  }
}

export function saveActiveStudent(student: StudentProfile): void {
  storageSet("session", GLOBAL_STUDENT_KEY, JSON.stringify(student));
  emitStudentChange();
}

export function clearActiveStudent(): void {
  storageRemove("session", GLOBAL_STUDENT_KEY);
  for (const key of storageKeys("session")) {
    if (key.startsWith(ASSIGNMENT_STUDENT_PREFIX)) {
      storageRemove("session", key);
    }
  }
  emitStudentChange();
}

export function saveStudent(assignmentId: number, student: StudentProfile): void {
  saveActiveStudent(student);
  storageSet("session", `${ASSIGNMENT_STUDENT_PREFIX}${assignmentId}`, JSON.stringify(student));
}

export function loadStudent(assignmentId?: number | null): StudentProfile | null {
  const keys = assignmentId ? [`${ASSIGNMENT_STUDENT_PREFIX}${assignmentId}`, GLOBAL_STUDENT_KEY] : [GLOBAL_STUDENT_KEY];
  for (const key of keys) {
    const raw = storageGet("session", key);
    if (!raw) {
      continue;
    }
    try {
      const parsed = JSON.parse(raw) as StudentProfile;
      if (parsed?.id) {
        return parsed;
      }
    } catch {
      storageRemove("session", key);
    }
  }
  return null;
}

export function onActiveStudentChange(listener: () => void): () => void {
  if (typeof window === "undefined") {
    return () => undefined;
  }
  window.addEventListener(STUDENT_CHANGE_EVENT, listener);
  return () => window.removeEventListener(STUDENT_CHANGE_EVENT, listener);
}

export function saveInviteCode(inviteCode: string): void {
  const normalized = inviteCode.trim().toUpperCase();
  if (normalized) {
    storageSet("local", LAST_INVITE_CODE_KEY, normalized);
  }
}

export function loadInviteCode(): string | null {
  const saved = storageGet("local", LAST_INVITE_CODE_KEY);
  return saved?.trim().toUpperCase() || null;
}

export function saveLastPublicProblem(problemId: number): void {
  if (Number.isFinite(problemId)) {
    storageSet("local", LAST_PUBLIC_PROBLEM_KEY, String(problemId));
  }
}

export function loadLastPublicProblem(): number | null {
  const saved = Number(storageGet("local", LAST_PUBLIC_PROBLEM_KEY));
  return Number.isFinite(saved) && saved > 0 ? saved : null;
}

export function saveDraft(problemId: number, languageId: number, sourceCode: string): void {
  storageSet("local", `${DRAFT_PREFIX}${problemId}:${languageId}`, sourceCode);
}

export function loadDraft(problemId: number, languageId: number): string | null {
  return storageGet("local", `${DRAFT_PREFIX}${problemId}:${languageId}`);
}

export function clearDraft(problemId: number, languageId: number): void {
  storageRemove("local", `${DRAFT_PREFIX}${problemId}:${languageId}`);
}

export function hasDraft(problemId: number): boolean {
  const prefix = `${DRAFT_PREFIX}${problemId}:`;
  return storageKeys("local").some(key => key.startsWith(prefix) && Boolean(storageGet("local", key)?.trim()));
}

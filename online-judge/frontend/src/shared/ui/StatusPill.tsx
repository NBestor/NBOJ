import { difficultyLabel, verdictLabel } from "../format";

type Tone = "neutral" | "success" | "warning" | "danger" | "info";

export function StatusPill({ children, tone = "neutral" }: { children: React.ReactNode; tone?: Tone }) {
  return <span className={`status-pill status-pill--${tone}`}>{children}</span>;
}

export function VerdictPill({ verdict }: { verdict?: string | null }) {
  const key = (verdict || "").toUpperCase();
  const tone =
    key === "ACCEPTED"
      ? "success"
      : key.includes("TIME") || key.includes("MEMORY")
        ? "warning"
        : key.includes("ERROR") || key.includes("WRONG")
          ? "danger"
          : "neutral";
  return <StatusPill tone={tone}>{verdictLabel(verdict)}</StatusPill>;
}

export function DifficultyPill({ difficulty }: { difficulty?: string | null }) {
  const key = (difficulty || "").toUpperCase();
  const tone = key === "EASY" ? "success" : key === "MEDIUM" ? "warning" : key === "HARD" ? "danger" : "neutral";
  return <StatusPill tone={tone}>{difficultyLabel(difficulty)}</StatusPill>;
}

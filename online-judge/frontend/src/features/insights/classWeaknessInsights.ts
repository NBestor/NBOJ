import type { AssignmentOverview } from "../../shared/api/types";
import { displayText } from "../../shared/format";

export type ClassWeaknessAssignment = {
  id: number;
  title: string;
};

export type ClassWeaknessInsight = {
  label: string;
  count: number;
  kind: "issue" | "ability";
  assignmentTitles: string[];
  action?: string | null;
};

export function buildClassWeaknessInsights(
  assignments: ClassWeaknessAssignment[],
  overviewByAssignment: Record<number, AssignmentOverview | null>
): ClassWeaknessInsight[] {
  const insightMap = new Map<string, ClassWeaknessInsight>();
  assignments.forEach(assignment => {
    const overview = overviewByAssignment[assignment.id];
    overview?.topIssues?.forEach(issue => {
      addInsight(
        insightMap,
        issue.label,
        issue.affectedStudentCount || issue.count,
        "issue",
        assignment.title,
        issue.interventionSuggestion || issue.actionPriorityReason
      );
    });
    overview?.classAbilityWeaknesses?.forEach(ability => {
      addInsight(
        insightMap,
        ability.abilityPoint,
        ability.submissionCount || ability.taskCount,
        "ability",
        assignment.title,
        ability.evidenceTags?.length ? `证据标签：${ability.evidenceTags.slice(0, 3).join("、")}` : null
      );
    });
  });
  return [...insightMap.values()]
    .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label, "zh-Hans-CN"))
    .slice(0, 3);
}

function addInsight(
  target: Map<string, ClassWeaknessInsight>,
  label: string | null | undefined,
  count: number,
  kind: ClassWeaknessInsight["kind"],
  assignmentTitle: string,
  action?: string | null
) {
  const keyLabel = displayText(label, "");
  if (!keyLabel) {
    return;
  }
  const key = `${kind}:${keyLabel}`;
  const existing =
    target.get(key) ||
    {
      label: keyLabel,
      count: 0,
      kind,
      assignmentTitles: [],
      action
    };
  existing.count += Math.max(1, count || 0);
  if (!existing.assignmentTitles.includes(assignmentTitle)) {
    existing.assignmentTitles.push(assignmentTitle);
  }
  if (!existing.action && action) {
    existing.action = action;
  }
  target.set(key, existing);
}

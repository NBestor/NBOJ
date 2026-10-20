import type { StudentAiFeedbackItem } from "../../shared/api/types";

export type FeedbackCodeReferenceTone = "amber" | "teal" | "blue" | "violet";

export type FeedbackCodeRange = {
  startLine: number;
  endLine: number;
  code: string;
};

export type FeedbackCodeReference = {
  issueIndex: number;
  issueNumber: number;
  tone: FeedbackCodeReferenceTone;
  title: string;
  ranges: FeedbackCodeRange[];
};

const FEEDBACK_REFERENCE_TONES: FeedbackCodeReferenceTone[] = ["amber", "teal", "blue", "violet"];

export function feedbackIssueTone(issueIndex: number): FeedbackCodeReferenceTone {
  return FEEDBACK_REFERENCE_TONES[issueIndex % FEEDBACK_REFERENCE_TONES.length];
}

function clampLine(value: number, lineCount: number) {
  return Math.min(Math.max(Math.trunc(value), 1), lineCount);
}

function rangesFromItem(item: StudentAiFeedbackItem, lineCount: number): FeedbackCodeRange[] {
  const snippetRanges = (item.evidenceSnippets || [])
    .filter(snippet => Number.isFinite(snippet.lineNumber))
    .map(snippet => {
      const startLine = clampLine(Number(snippet.lineNumber), lineCount);
      const endLine = clampLine(Number(snippet.lineEnd || snippet.lineNumber), lineCount);
      return {
        startLine: Math.min(startLine, endLine),
        endLine: Math.max(startLine, endLine),
        code: snippet.code?.trim() || ""
      };
    });

  if (snippetRanges.length) {
    return snippetRanges;
  }

  const fallbackLine = item.evidenceRefs
    ?.map(reference => reference.match(/^code:line:(\d+)$/)?.[1])
    .find(Boolean);
  if (!fallbackLine) {
    return [];
  }

  const line = clampLine(Number(fallbackLine), lineCount);
  return [{ startLine: line, endLine: line, code: "" }];
}

export function buildFeedbackCodeReferences(
  items: StudentAiFeedbackItem[],
  lineCount: number
): FeedbackCodeReference[] {
  if (lineCount <= 0) {
    return [];
  }

  return items.flatMap((item, issueIndex) => {
    const ranges = rangesFromItem(item, lineCount);
    if (!ranges.length) {
      return [];
    }

    return [{
      issueIndex,
      issueNumber: issueIndex + 1,
      tone: feedbackIssueTone(issueIndex),
      title: item.title?.trim() || `问题 ${issueIndex + 1}`,
      ranges
    }];
  });
}

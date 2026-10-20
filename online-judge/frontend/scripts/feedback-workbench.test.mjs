import assert from "node:assert/strict";
import test from "node:test";
import { buildFeedbackCodeReferences } from "../src/features/problem/feedbackCodeReferences.ts";

test("maps issue order to stable semantic colors and exact code ranges", () => {
  const references = buildFeedbackCodeReferences([
    {
      title: "误用贪心策略替代区间 DP",
      evidenceSnippets: [
        { lineNumber: 10, lineEnd: 19, code: "def solve(n, nums):" }
      ]
    },
    {
      title: "过度复杂的结构维护",
      evidenceSnippets: [
        { lineNumber: 2, lineEnd: 2, code: "import heapq" },
        { lineNumber: 16, lineEnd: 16, code: "heapq.heappush(heap, x)" },
        { lineNumber: 18, lineEnd: 18, code: "heapq.heappush(heap, x)" }
      ]
    }
  ], 23);

  assert.deepEqual(references, [
    {
      issueIndex: 0,
      issueNumber: 1,
      tone: "amber",
      title: "误用贪心策略替代区间 DP",
      ranges: [{ startLine: 10, endLine: 19, code: "def solve(n, nums):" }]
    },
    {
      issueIndex: 1,
      issueNumber: 2,
      tone: "teal",
      title: "过度复杂的结构维护",
      ranges: [
        { startLine: 2, endLine: 2, code: "import heapq" },
        { startLine: 16, endLine: 16, code: "heapq.heappush(heap, x)" },
        { startLine: 18, endLine: 18, code: "heapq.heappush(heap, x)" }
      ]
    }
  ]);
});

test("uses code-line evidence refs, clamps ranges, and omits ungrounded issues", () => {
  const references = buildFeedbackCodeReferences([
    {
      title: "输入读取位置",
      evidenceSnippets: [{ lineNumber: -4, lineEnd: 99, code: "input()" }]
    },
    {
      title: "输出时机",
      evidenceRefs: ["judge:first_failed_case:1", "code:line:7"]
    },
    {
      title: "只有评测证据",
      evidenceRefs: ["judge:first_failed_case:1"]
    }
  ], 12);

  assert.deepEqual(references, [
    {
      issueIndex: 0,
      issueNumber: 1,
      tone: "amber",
      title: "输入读取位置",
      ranges: [{ startLine: 1, endLine: 12, code: "input()" }]
    },
    {
      issueIndex: 1,
      issueNumber: 2,
      tone: "teal",
      title: "输出时机",
      ranges: [{ startLine: 7, endLine: 7, code: "" }]
    }
  ]);
});

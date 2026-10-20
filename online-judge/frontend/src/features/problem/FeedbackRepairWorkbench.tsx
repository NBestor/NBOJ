import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronRight, Code2 } from "lucide-react";
import type { StudentAiFeedbackItem, SubmissionResult } from "../../shared/api/types";
import { useTranslation } from "../../shared/i18n";
import {
  buildFeedbackCodeReferences,
  feedbackIssueTone,
  type FeedbackCodeReference
} from "./feedbackCodeReferences";

type FeedbackTestCase = NonNullable<SubmissionResult["testCaseResults"]>[number];
type FeedbackSelection = { kind: "repair" | "improvement"; index: number };

type FeedbackRepairWorkbenchProps = {
  sourceCode: string;
  sourceFileName: string;
  languageName: string;
  repairItems: StudentAiFeedbackItem[];
  improvementItems: StudentAiFeedbackItem[];
  summary: string;
  verificationPrompt: string;
  passed: number;
  total: number;
  firstFailedCase: FeedbackTestCase | null;
  onReturnToCode: () => void;
};

function lifecycleStatusKey(status?: string | null) {
  const normalized = String(status || "UNCOMPARABLE").toLowerCase();
  return ["new", "persisted", "not_observed", "recovered", "recurred", "uncomparable"].includes(normalized)
    ? normalized
    : "uncomparable";
}

function knowledgePathStatusKey(status?: string | null) {
  const normalized = String(status || "UNCLASSIFIED").toLowerCase();
  return ["formal", "provisional", "inferred", "unclassified"].includes(normalized)
    ? normalized
    : "unclassified";
}

function referenceForIssue(references: FeedbackCodeReference[], issueIndex: number) {
  return references.find(reference => reference.issueIndex === issueIndex) || null;
}

function rangeLabel(
  reference: FeedbackCodeReference | null,
  emptyLabel: string,
  lineLabelText: (line: number) => string,
  rangeLabelText: (start: number, end: number) => string
) {
  if (!reference?.ranges.length) {
    return emptyLabel;
  }
  return reference.ranges
    .map(range => range.startLine === range.endLine ? lineLabelText(range.startLine) : rangeLabelText(range.startLine, range.endLine))
    .join(" · ");
}

function issueReferenceAtLine(references: FeedbackCodeReference[], lineNumber: number, activeIssueIndex: number) {
  const matches = references.filter(reference => reference.ranges.some(range => lineNumber >= range.startLine && lineNumber <= range.endLine));
  return matches.find(reference => reference.issueIndex === activeIssueIndex) || matches[0] || null;
}

function selectionFromReference(reference: FeedbackCodeReference, repairCount: number): FeedbackSelection {
  return reference.issueIndex < repairCount
    ? { kind: "repair", index: reference.issueIndex }
    : { kind: "improvement", index: reference.issueIndex - repairCount };
}

export function FeedbackRepairWorkbench({
  sourceCode,
  sourceFileName,
  languageName,
  repairItems,
  improvementItems,
  summary,
  verificationPrompt,
  passed,
  total,
  firstFailedCase,
  onReturnToCode
}: FeedbackRepairWorkbenchProps) {
  const { t } = useTranslation();
  const [selection, setSelection] = useState<FeedbackSelection>(() => repairItems.length
    ? { kind: "repair", index: 0 }
    : { kind: "improvement", index: 0 });
  const codePaneRef = useRef<HTMLDivElement>(null);
  const codeLines = useMemo(() => (sourceCode || "").replace(/\r\n/g, "\n").split("\n"), [sourceCode]);
  const references = useMemo(
    () => buildFeedbackCodeReferences([...repairItems, ...improvementItems], Math.max(codeLines.length, 1)),
    [codeLines.length, improvementItems, repairItems]
  );
  const activeGlobalIndex = selection.kind === "repair" ? selection.index : repairItems.length + selection.index;
  const activeItem = selection.kind === "repair"
    ? repairItems[selection.index] || repairItems[0] || null
    : improvementItems[selection.index] || improvementItems[0] || null;
  const activeReference = referenceForIssue(references, activeGlobalIndex);
  const activeTone = feedbackIssueTone(activeGlobalIndex);
  const activeCategoryItems = selection.kind === "repair" ? repairItems : improvementItems;
  const activeCategoryOffset = selection.kind === "repair" ? 0 : repairItems.length;
  const passRate = total ? Math.round((passed / total) * 100) : 0;

  useEffect(() => {
    const items = selection.kind === "repair" ? repairItems : improvementItems;
    if (selection.index < items.length) {
      return;
    }
    if (repairItems.length) {
      setSelection({ kind: "repair", index: 0 });
    } else if (improvementItems.length) {
      setSelection({ kind: "improvement", index: 0 });
    }
  }, [improvementItems.length, repairItems.length, selection.index, selection.kind]);

  function selectFeedback(nextSelection: FeedbackSelection) {
    setSelection(nextSelection);
    const globalIndex = nextSelection.kind === "repair" ? nextSelection.index : repairItems.length + nextSelection.index;
    const targetLine = referenceForIssue(references, globalIndex)?.ranges[0]?.startLine;
    if (!targetLine) {
      return;
    }
    window.requestAnimationFrame(() => {
      const codePane = codePaneRef.current;
      const lineElement = codePane?.querySelector<HTMLElement>(`[data-line-number="${targetLine}"]`);
      if (!codePane || !lineElement) {
        return;
      }
      codePane.scrollTo({
        top: Math.max(0, lineElement.offsetTop - (codePane.clientHeight / 2) + (lineElement.clientHeight / 2)),
        behavior: "smooth"
      });
    });
  }

  function selectReference(reference: FeedbackCodeReference) {
    selectFeedback(selectionFromReference(reference, repairItems.length));
  }

  return (
    <div className="feedback-code-workbench">
      <aside className="feedback-code-workbench__rail" aria-label={t("problemFeedbackWorkbench.issueNavigation")}>
        <div className="feedback-code-workbench__overview">
          <strong>{t("problemFeedbackWorkbench.statusOverview")}</strong>
          <dl>
            <div>
              <dt>{t("problemFeedbackWorkbench.judgeResult")}</dt>
              <dd>{passed}/{total || "-"}</dd>
            </div>
            <div>
              <dt>{t("problemFeedbackWorkbench.passRate")}</dt>
              <dd>{passRate}%</dd>
            </div>
          </dl>
        </div>

        <div className="feedback-code-workbench__issue-list">
          <div className="feedback-code-workbench__section-title">
            <strong>{t("problemFeedbackWorkbench.issueList")}</strong>
            <span>{t("problemFeedbackWorkbench.itemCount", { count: repairItems.length })}</span>
          </div>
          {repairItems.map((item, issueIndex) => {
            const tone = feedbackIssueTone(issueIndex);
            const reference = referenceForIssue(references, issueIndex);
            return (
              <button
                type="button"
                className={`feedback-code-workbench__issue feedback-code-workbench__issue--repair${selection.kind === "repair" && selection.index === issueIndex ? " is-active" : ""}`}
                data-tone={tone}
                aria-pressed={selection.kind === "repair" && selection.index === issueIndex}
                onClick={() => selectFeedback({ kind: "repair", index: issueIndex })}
                key={`${item.normalizedPointKey || item.title || "issue"}-${issueIndex}`}
              >
                <span className="feedback-code-workbench__issue-number">{issueIndex + 1}</span>
                <span className="feedback-code-workbench__issue-copy">
                  <strong>{item.title || t("issueLifecycle.unnamedIssue")}</strong>
                  <small>{reference
                    ? rangeLabel(
                        reference,
                        t("problemFeedbackWorkbench.codeLocated"),
                        line => t("feedbackMeta.line", { line }),
                        (start, end) => t("feedbackMeta.lineRange", { start, end })
                      )
                    : t("problemFeedbackWorkbench.noCodeLocation")}</small>
                </span>
              </button>
            );
          })}
        </div>

        <section className="feedback-code-workbench__growth">
          <div className="feedback-code-workbench__section-title">
            <strong>{t("problemFeedbackWorkbench.growthSuggestions")}</strong>
            <span>{t("problemFeedbackWorkbench.itemCount", { count: improvementItems.length })}</span>
          </div>
          {improvementItems.map((item, improvementIndex) => {
            const globalIndex = repairItems.length + improvementIndex;
            const tone = feedbackIssueTone(globalIndex);
            const reference = referenceForIssue(references, globalIndex);
            return (
              <button
                type="button"
                className={`feedback-code-workbench__issue feedback-code-workbench__issue--growth${selection.kind === "improvement" && selection.index === improvementIndex ? " is-active" : ""}`}
                data-tone={tone}
                aria-pressed={selection.kind === "improvement" && selection.index === improvementIndex}
                onClick={() => selectFeedback({ kind: "improvement", index: improvementIndex })}
                key={`${item.normalizedPointKey || item.title || "growth"}-${improvementIndex}`}
              >
                <span className="feedback-code-workbench__issue-number">{improvementIndex + 1}</span>
                <span className="feedback-code-workbench__issue-copy">
                  <strong>{item.title || t("issueLifecycle.unnamedIssue")}</strong>
                  <small>{reference
                    ? rangeLabel(
                        reference,
                        t("problemFeedbackWorkbench.codeLocated"),
                        line => t("feedbackMeta.line", { line }),
                        (start, end) => t("feedbackMeta.lineRange", { start, end })
                      )
                    : t("problemFeedbackWorkbench.noCodeLocation")}</small>
                </span>
              </button>
            );
          })}
        </section>
      </aside>

      <main className="feedback-code-workbench__main">
        <div className="feedback-code-workbench__legend" aria-label={t("problemFeedbackWorkbench.codeLegend")}>
          {activeCategoryItems.map((item, categoryIndex) => {
            const globalIndex = activeCategoryOffset + categoryIndex;
            return (
              <button
                type="button"
                data-tone={feedbackIssueTone(globalIndex)}
                className={selection.index === categoryIndex ? "is-active" : ""}
                onClick={() => selectFeedback({ kind: selection.kind, index: categoryIndex })}
                key={`${selection.kind}-${item.title || "legend"}-${categoryIndex}`}
              >
                <span>{categoryIndex + 1}</span>
                {selection.index === categoryIndex
                  ? t(selection.kind === "repair" ? "problemFeedbackWorkbench.currentIssue" : "problemFeedbackWorkbench.currentSuggestion")
                  : t(selection.kind === "repair" ? "problemFeedbackWorkbench.relatedIssue" : "problemFeedbackWorkbench.relatedSuggestion")}
              </button>
            );
          })}
        </div>

        <section className="feedback-code-workbench__editor" aria-label={t("problemFeedbackWorkbench.codeEvidence")}>
          <header>
            <span>{sourceFileName}</span>
            <div>
              <span>{languageName}</span>
            </div>
          </header>
          <div className="feedback-code-workbench__code" ref={codePaneRef}>
            {codeLines.map((line, lineIndex) => {
              const lineNumber = lineIndex + 1;
              const reference = issueReferenceAtLine(references, lineNumber, activeGlobalIndex);
              const startsRange = reference?.ranges.some(range => range.startLine === lineNumber);
              const isActive = reference?.issueIndex === activeGlobalIndex;
              const referenceSelection = reference ? selectionFromReference(reference, repairItems.length) : null;
              const referenceNumber = referenceSelection ? referenceSelection.index + 1 : 0;
              return (
                <div
                  className={`feedback-code-workbench__line${reference ? " is-referenced" : ""}${isActive ? " is-active" : ""}`}
                  data-tone={reference?.tone}
                  data-line-number={lineNumber}
                  key={lineNumber}
                >
                  <span className="feedback-code-workbench__gutter">
                    {startsRange ? (
                      <button type="button" onClick={() => reference && selectReference(reference)} aria-label={t(referenceSelection?.kind === "improvement" ? "problemFeedbackWorkbench.selectSuggestion" : "problemFeedbackWorkbench.selectIssue", { number: referenceNumber })}>
                        {referenceNumber}
                      </button>
                    ) : null}
                    <em>{lineNumber}</em>
                  </span>
                  <code>{line || " "}</code>
                  {startsRange && reference ? (
                    <button type="button" className="feedback-code-workbench__inline-label" onClick={() => selectReference(reference)}>
                      {t(referenceSelection?.kind === "improvement" ? "problemFeedbackWorkbench.inlineSuggestion" : "problemFeedbackWorkbench.inlineIssue", { number: referenceNumber })}
                    </button>
                  ) : null}
                </div>
              );
            })}
          </div>
        </section>

        <section className="feedback-code-workbench__failed-case" aria-label={t("problemFeedbackWorkbench.failedCase")}>
          <header>
            <strong>{t("problemFeedbackWorkbench.currentFailedCase")}</strong>
            {firstFailedCase ? <span>{t("problemFeedbackWorkbench.caseNumber", { number: firstFailedCase.testCaseNumber })}</span> : null}
          </header>
          {firstFailedCase ? (
            <dl>
              <div><dt>{t("problemFeedbackWorkbench.input")}</dt><dd>{t("problemFeedbackWorkbench.publicCase")}</dd></div>
              <div><dt>{t("problemFeedbackWorkbench.expected")}</dt><dd>{firstFailedCase.expectedOutput?.trim() || "-"}</dd></div>
              <div><dt>{t("problemFeedbackWorkbench.actual")}</dt><dd>{firstFailedCase.actualOutput?.trim() || "-"}</dd></div>
              <div><dt>{t("problemFeedbackWorkbench.result")}</dt><dd>{t("problemFeedbackWorkbench.failed")}</dd></div>
            </dl>
          ) : <p>{t("problemFeedbackWorkbench.noPublicFailedCase")}</p>}
        </section>

      </main>

      <aside className="feedback-code-workbench__inspector" data-tone={activeTone} aria-live="polite">
        <header>
          <span>{t(selection.kind === "repair" ? "problemFeedbackWorkbench.issuePosition" : "problemFeedbackWorkbench.suggestionPosition", { current: selection.index + 1, total: activeCategoryItems.length })}</span>
          <strong>{activeItem?.title || t("issueLifecycle.unnamedIssue")}</strong>
          <small>{t(`issueLifecycle.status.${lifecycleStatusKey(activeItem?.changeStatus)}`)}</small>
        </header>

        {selection.kind === "repair" ? (
          <>
            <section>
              <h3>{t("problemFeedbackWorkbench.reason")}</h3>
              <p>{summary || activeItem?.body || t("problemFeedbackWorkbench.noSummary")}</p>
            </section>
            <section>
              <h3>{t("problemFeedbackWorkbench.correction")}</h3>
              <p>{activeItem?.body || t("problemFeedbackWorkbench.noCorrection")}</p>
            </section>
            <section>
              <h3>{t("problemFeedbackWorkbench.verification")}</h3>
              <p>{verificationPrompt || t("problemFeedbackWorkbench.defaultVerification")}</p>
            </section>
          </>
        ) : (
          <section>
            <h3>{t("problemFeedbackWorkbench.suggestionDetails")}</h3>
            <p>{activeItem?.body || t("problemFeedbackWorkbench.noSuggestionDetails")}</p>
          </section>
        )}

        <div className="feedback-code-workbench__evidence-location">
          <strong>{t("problemFeedbackWorkbench.evidenceLocation")}</strong>
          <span>{activeReference
            ? rangeLabel(
                activeReference,
                t("problemFeedbackWorkbench.codeLocated"),
                line => t("feedbackMeta.line", { line }),
                (start, end) => t("feedbackMeta.lineRange", { start, end })
              )
            : t("problemFeedbackWorkbench.noCodeLocation")}</span>
        </div>

        {activeItem?.knowledgePath?.length ? (
          <div className="feedback-code-workbench__knowledge">
            <strong>{t("feedbackMeta.knowledgePath")}</strong>
            <ol className="feedback-code-workbench__knowledge-list" aria-label={t("feedbackMeta.knowledgePathAria")}>
              {activeItem.knowledgePath.map((knowledgePoint, index) => (
                <li key={`${knowledgePoint}-${index}`}>
                  {index > 0 ? <ChevronRight className="feedback-code-workbench__knowledge-arrow" size={14} aria-hidden="true" /> : null}
                  <span className="feedback-code-workbench__knowledge-tag">{knowledgePoint}</span>
                </li>
              ))}
            </ol>
            <em>{t(`feedbackMeta.pathStatus.${knowledgePathStatusKey(activeItem.knowledgePathStatus)}`)}</em>
          </div>
        ) : null}

        <button type="button" className="feedback-code-workbench__return" onClick={onReturnToCode}>
          <Code2 size={16} aria-hidden="true" />
          {t("problemFeedbackWorkbench.returnToCode")}
        </button>
      </aside>
    </div>
  );
}

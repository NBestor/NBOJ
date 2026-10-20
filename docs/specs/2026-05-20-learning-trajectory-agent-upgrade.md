# Learning Trajectory Agent Upgrade

## Goal

Upgrade the diagnosis agent from single-submission error analysis to learning-trajectory-aware coaching.

The agent should not only answer "what is wrong in this code", but also identify whether the student is progressing, stuck, regressing, or ready to review after acceptance.

## Current Change

- Added `learningTrajectorySignal` to submission analysis output.
- The signal includes `phase`, `label`, `evidenceRef`, `summary`, `nextFocus`, and `needsTeacherAttention`.
- The agent now derives trajectory phases from previous verdict, current verdict, repeated issue counts, and transition evidence.
- The diagnostic trace now records `trajectory=<phase>` for auditability.
- Public CodeStream attempt-chain fixtures now evaluate multi-attempt learning signals.

## Follow-Up Upgrade: Business Decision Integration

The next iteration moved the trajectory signal out of the diagnosis-only layer and into the student trajectory business interface.

- `DiagnosisReportReader` can now parse `learningTrajectorySignal` from stored report JSON.
- `StudentTrajectoryResponse` exposes the signal at three levels:
  - overall latest student trajectory
  - latest task trajectory
  - individual submission points
- `LearningTrajectoryPolicy` maps trajectory phases into:
  - next student action
  - teacher attention reason
  - latest improvement signal
- Frontend API types now include these trajectory fields, so UI work can use them safely.

This matters because a structured AI signal is not truly useful until it changes downstream decisions. The system now uses the agent's learning-trajectory judgment to decide what the student should do next and whether a teacher should pay attention.

## Follow-Up Upgrade: Verifiable Learning Intervention

The next research upgrade moves from "diagnose and suggest" to "diagnose and assign one observable learning action".

New field: `learningInterventionPlan`.

It contains:

- `interventionType`: stable action category, such as `VARIABLE_TRACE`, `MIN_CASE_TRACE`, `COMPARE_SUBMISSIONS`, `COUNTEREXAMPLE`, `COMPLEXITY_ESTIMATE`, or `EXPLAIN_GENERALITY`.
- `goal`: what learning problem this action is meant to resolve.
- `studentTask`: the concrete next action for the student.
- `checkQuestion`: one Socratic check question.
- `completionSignal`: what observable student work counts as done.
- `evidenceRefs`: evidence anchors reused from diagnosis and trajectory signals.
- `estimatedMinutes`: small time box for the intervention.
- `answerLeakRisk`: safety classification for the intervention itself.

This is an education-agent upgrade because the target is no longer just "classify the bug".
The agent must now convert a diagnosis into a small teaching move that a teacher or system can later check.

Implementation notes:

- Rule fallback now generates `learningInterventionPlan`, so the feature works without a live model key.
- The model prompt asks for the same structured field, but model output is normalized and can be replaced by the rule plan if incomplete.
- Safety checks inspect the intervention plan in addition to the student hint and report markdown.
- Student trajectory API exposes the latest intervention plan at overall, task, and submission-point levels.
- Frontend API types are updated so UI can render the plan safely later.

Acceptance gates:

- Existing positive, blind, negative, public replay, and attempt-chain eval gates must remain passing.
- Every eval case must produce a non-empty `learningInterventionPlan`.
- Intervention plans must include a task, check question, completion signal, evidence refs where available, and safe answer-leak risk.
- Safety downgrade must also downgrade unsafe intervention plans, not only unsafe hint text.

Professional lesson from this round:

An AI field only becomes product capability when it passes through five layers:

```text
output protocol -> rule/model generation -> safety guard -> persisted reader/API -> eval gate
```

If any layer is missing, the field is just decorative JSON.

## Follow-Up Upgrade: Intervention Impact Signal

The next optimization closes the first feedback loop around `learningInterventionPlan`.

New concept: `learningInterventionImpact`.

It does not claim causality. It is an observational signal based on what happens after the intervention plan is generated:

- `FOLLOWUP_ACCEPTED`: the next same-problem submission passed.
- `ISSUE_SHIFTED`: the next same-problem submission changed diagnosis category.
- `SAME_ISSUE`: the next same-problem submission still hits the same issue or fine-grained issue.
- `VERDICT_CHANGED`: verdict stage changed, but the diagnosis signal is not enough to call it clear improvement.
- `NO_CLEAR_CHANGE`: there was a follow-up submission, but no clear issue or verdict movement.
- `AWAITING_FOLLOWUP`: no later same-problem submission exists yet.

Why this matters:

The agent now has a primitive feedback loop:

```text
diagnose -> assign intervention -> observe next same-problem submission -> label impact
```

This is closer to an education agent's objective function. The system can begin to notice which intervention types are often followed by acceptance, which ones are followed by same-issue persistence, and where teacher intervention may be needed.

Implementation notes:

- `LearningInterventionImpactAnalyzer` reuses persisted diagnosis JSON and submission order.
- It attaches impact signals to the student trajectory response at overall, task, and submission-point levels.
- It deliberately avoids causal wording because the system does not yet know whether the student actually completed the intervention task.

Acceptance gates:

- Analyzer tests must cover accepted follow-up, same issue, issue shift, and awaiting follow-up.
- Student trajectory response and frontend types must expose intervention impact without changing database schema.
- Existing diagnosis, trajectory, intervention, and safety eval gates must remain passing.

## Follow-Up Upgrade: Learning Action Evidence

The next optimization adds process supervision.

`learningInterventionImpact` can observe what happened after an intervention, but it still cannot tell whether the student actually performed the learning action. That makes teacher analytics and AI evaluation easy to overclaim: a later AC does not always mean the planned action worked, and a later failure does not always mean the action was bad.

New concept: `learningActionEvidence`.

It contains:

- `expectedActionType`: the action the agent asked the student to perform.
- `executionStatus`: `NOT_OBSERVED`, `PARTIALLY_OBSERVED`, `OBSERVED`, or `CONTRADICTED`.
- `observedEvidence`: what the system saw after the action was assigned.
- `confidence`: how strong this process-level inference is.
- `evidenceRefs`: action, follow-up, and impact evidence anchors.
- `nextAdjustment`: how the next teaching move should change.

The important distinction:

```text
learningInterventionImpact = outcome changed after the intervention
learningActionEvidence = there is evidence the student actually executed the assigned learning action
```

Implementation notes:

- Diagnosis output initializes `learningActionEvidence` as `NOT_OBSERVED` when it creates a `learningInterventionPlan`.
- `LearningActionEvidenceAnalyzer` upgrades that status from ordered same-problem follow-up submissions and intervention impact signals.
- Student trajectory API exposes the latest action evidence at overall, task, and submission-point levels.
- `LearningTrajectoryPolicy` can lower hint granularity when action evidence is `CONTRADICTED`.

Acceptance gates:

- Reader tests must parse stored `learningActionEvidence`.
- Analyzer tests must cover observed execution, contradicted execution, and no-follow-up waiting state.
- Existing diagnosis and trajectory eval gates must still pass.

## Follow-Up Upgrade: Teacher Action Priority Strategy

Teacher-side statistics should not be a raw frequency leaderboard.

The new teacher strategy ranks issues by an evidence-weighted action priority score. The score is not model-generated; it is a deterministic, auditable heuristic that combines:

- issue count
- affected student count
- repeated-stuck or regression trajectory evidence
- unobserved or contradicted learning actions
- unresolved same-issue evidence after intervention
- low-confidence diagnosis count

This makes the teacher dashboard answer a better question:

```text
Which issue should I intervene on first, given both frequency and process evidence?
```

New fields on teacher overview `topIssues`:

- `actionPriorityScore`
- `actionPriorityLabel`
- `actionPriorityReason`
- `affectedStudentCount`
- `repeatedStudentCount`
- `unexecutedActionCount`
- `unresolvedAfterInterventionCount`

Acceptance gates:

- Teacher priority analyzer tests must verify that unresolved intervention and contradicted action evidence raise priority.
- Teacher overview should still include the old count, taxonomy explanation, ability point, hint policy, and intervention suggestion.
- The UI may display the action priority reason, but the source of truth stays in backend DTOs.

## Supported Phases

- `FIRST_ATTEMPT`: no reliable previous attempt, diagnose from current evidence.
- `FIXED_COMPILATION`: compilation error moved to a runtime/correctness verdict.
- `RUNTIME_FIXED_CORRECTNESS_REMAINS`: runtime error moved to WA/TLE/MLE.
- `STILL_STUCK`: same failing verdict remains, but not enough repetitions for escalation.
- `REPEATED_STUCK`: same failing verdict repeats several times; reduce task size and consider teacher intervention.
- `PROGRESSING`: verdict stage improved but not accepted yet.
- `REGRESSION`: verdict stage worsened or accepted solution failed after a new edit.
- `ACCEPTED_AFTER_FIX`: failed attempt moved to accepted; shift to reflection.
- `ACCEPTED_REVIEW`: accepted without useful history; shift to generalization review.

## Evaluation

The public attempt-chain fixture set covers:

- compile fixed but correctness remains
- runtime crash fixed but correctness remains
- repeated wrong-answer stuck state
- accepted after a fix
- regression after a better verdict

Acceptance gates:

- 100% structured trajectory signal rate on attempt-chain fixtures.
- 100% phase match on current fixture set.
- 100% teacher-attention match on escalation/regression examples.
- 100% required trajectory evidence refs.
- Existing positive, negative, and public replay gates must continue passing.
- Student trajectory policy tests must verify that `REPEATED_STUCK`, `REGRESSION`, and accepted-review phases change downstream next-step or attention decisions.

## Why This Matters

This is an agent-level improvement, not model fine-tuning.

The target function becomes more educational:

```text
diagnosis quality =
  current error evidence
  + learning trajectory classification
  + safe next coaching move
  + measurable regression/stuck detection
```

This gives later iterations a stable place to attach richer coaching policies, teacher dashboards, and personalized learning summaries.

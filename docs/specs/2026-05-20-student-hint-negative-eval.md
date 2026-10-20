# Spec: Student Hint Negative Eval

## Why

The positive 100-case eval checks whether the backend agent can find expected causes. It does not fully protect against false positives.

For student learning, false positives are especially risky: a noisy tag can push the student toward the wrong reflection path and pollute long-term ability analytics. This eval adds negative controls that look similar to known bug patterns but should not receive those labels.

## Scope

The first negative set contains 30 cases, grouped into 6 patterns:

- accepted input parsing code should not become `INPUT_PARSING`
- accepted DP previous-state transitions should not become `OFF_BY_ONE`
- accepted zero accumulators should not become `INITIAL_STATE`
- accepted hidden pass cases should not become `SAMPLE_OVERFIT`
- TLE with hidden failure should not default to `SAMPLE_OVERFIT`
- intentional `set()` usage should not become `DUPLICATE_CASE`

## Files

```text
online-judge/scripts/generate-student-hint-negative-fixtures.mjs
online-judge/src/test/resources/diagnosis-eval-fixtures/student-hint-negative-cases.json
online-judge/src/test/java/com/onlinejudge/submission/application/StudentHintEvalFixtureLoader.java
online-judge/src/test/java/com/onlinejudge/submission/application/StudentHintEvalFixtureTest.java
```

## Current Result

```text
positive blind eval:
issueTags=100/100
fineTags=100/100
teachingActions=100/100
evidenceRefs=100/100

negative eval:
issueFalsePositives=0/30
fineFalsePositives=0/30
requiredIssueHits=30/30
requiredFineHits=30/30
evidenceRefs=30/30
```

## Acceptance

- Positive blind eval must keep its existing thresholds and current expected behavior.
- Negative eval must keep false positives at zero for forbidden issue and fine-grained tags.
- Every negative fixture must define at least one forbidden tag.
- AC negative cases should normally resolve to `GENERALIZATION_CHECK`.
- TLE negative controls may resolve to coarse `TIME_COMPLEXITY`, but should not invent unsupported fine-grained causes.

## Next Expansion

The next useful expansion is real student replay:

- collect accepted submissions that look like common bugs but are correct
- collect teacher-corrected AI false positives
- collect multiple submissions from the same student to test whether historical signals improve or pollute diagnosis

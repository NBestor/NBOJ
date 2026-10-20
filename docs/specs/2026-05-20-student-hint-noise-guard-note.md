# Note: Student Hint Noise Guard

## Why

The previous blind eval proved that the backend agent can recover the expected diagnosis on the 100 synthetic student-hint fixtures. This round focuses on a subtler education risk: tag pollution.

For a learning product, a noisy secondary label can be harmful even when the primary diagnosis is right. It may push the student toward the wrong reflection path and pollute the long-term ability profile.

## Changes

- `ACCEPTED` submissions now stop at `GENERALIZATION_CHECK` and do not accumulate error-like tags.
- Ordinary `input()` / `split()` code is evidence context only; it is promoted to `INPUT_PARSING` only when stronger problem-aware evidence exists.
- Hidden-case failure is promoted to `SAMPLE_OVERFIT` only for `WRONG_ANSWER`, not for TLE/MLE by default.
- `range(1, n)` is treated as off-by-one only when the problem statement implies inclusive `1..n`; normal DP transitions like `dp[i - 1]` are no longer over-tagged.
- TLE no longer defaults to `BRUTE_FORCE_LIMIT`; repeated scans inside a loop and nested loops provide stronger evidence for that fine tag.

## Current Result

Blind eval remains:

```text
issueTags=100/100
fineTags=100/100
teachingActions=100/100
evidenceRefs=100/100
```

The target is now both recall and precision: hit the expected teaching path, while avoiding noisy secondary tags that could mislead learning analytics.

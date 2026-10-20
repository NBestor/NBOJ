# Spec: Public Student Data Eval Import

## Why

The synthetic 100-case set is useful for high-granularity educational diagnosis, but it is too clean. We need public replay data from real programming submissions to expose noisy code shapes, verdict distributions, and false-positive diagnosis signals.

## Source

- Dataset: CodeStream: A Dataset of Iterative Programming Submissions with Sequential Verdict Traces and Attempt Histories
- DOI: 10.17632/n77t7z9zcr.1
- Page: https://data.mendeley.com/datasets/n77t7z9zcr/1
- Published: 21 April 2026
- License on Mendeley page: CC BY 4.0
- Files used locally as temporary cache only:
  - `details.md`
  - `Problem Data.csv`
  - `Submission Data.csv`

## Import Policy

- Do import: problem text, public submitted source code, final verdict, verdict trace, selected test-case input/output.
- Do not import: raw `user_id`, full raw CSV files, private student identity, school identity, or account-level timeline.
- Keep public data in a separate replay fixture file, not mixed into the curated 100-case gold set.
- Treat imported cases as verdict-level replay tests unless manually annotated by a teacher.

## Current Implementation

- Added generator: `online-judge/scripts/generate-student-hint-public-replay-fixtures.mjs`
- Added fixture: `online-judge/src/test/resources/diagnosis-eval-fixtures/student-hint-public-replay-cases.json`
- Added loader entry: `StudentHintEvalFixtureLoader.loadPublicReplay()`
- Added tests:
  - attribution and privacy guard
  - verdict-level issue/evidence hit rate
  - forbidden fine-tag guard against `STATE_RESET` false positives

## Dataset Shape

- Imported 25 replay cases.
- Verdict coverage:
  - `COMPILATION_ERROR`: 4
  - `RUNTIME_ERROR`: 4
  - `TIME_LIMIT_EXCEEDED`: 4
  - `MEMORY_LIMIT_EXCEEDED`: 1
  - `WRONG_ANSWER`: 7
  - `ACCEPTED`: 5

## Finding From First Replay Run

The first public replay run exposed a real false-positive risk: the previous state-reset regex promoted ordinary loop assignments such as `int local = 0` to `STATE_RESET`. That was too eager for real Java/C++ submissions.

Fix:

- `STATE_RESET` is now promoted mainly by stronger problem-aware evidence: multi-case problem text plus pre-loop state.
- Ordinary reset-like code shape is kept as low-confidence observation only.
- Public replay fixtures forbid `STATE_RESET`, so this regression is now tested.

## Acceptance

- Generator creates 25 fixtures and rejects raw `user_` leakage.
- `StudentHintEvalFixtureTest` passes with public replay:
  - issue hits: 25/25
  - evidence hits: 25/25
  - forbidden fine false positives: 0/25
  - safety: 25/25
  - structured plan: 25/25
- Existing synthetic positive/negative eval remains at objective 1.0.

## Limits

This is not yet a teacher-grade fine-grained diagnosis dataset. CodeStream gives verdicts and traces, not verified pedagogical root-cause labels. It should therefore be used for robustness, privacy, source attribution, coarse verdict signal stability, and false-positive discovery.

Fine-grained educational labels still need either teacher annotation or a stricter adjudication workflow.

## Next

- Expand replay size from 25 to 50-100 with balanced per-verdict sampling.
- Add attempt-chain fixtures where the same anonymous user-problem pair moves from CE/WA/TLE to AC.
- Add a manual annotation queue for 30-50 high-value wrong-answer cases.
- Separate eval dashboards into gold accuracy, public replay stability, negative false-positive rate, and longitudinal coaching quality.

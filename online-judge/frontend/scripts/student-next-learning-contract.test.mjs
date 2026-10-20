import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const pageSource = readFileSync(new URL("../src/features/student/StudentPage.tsx", import.meta.url), "utf8");
const i18nSource = readFileSync(new URL("../src/shared/i18n.tsx", import.meta.url), "utf8");
const styleSource = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

test("student home consumes the canonical recommendation API", () => {
  assert.match(pageSource, /api\.studentRecommendations\(student\.id\)/);
  assert.doesNotMatch(pageSource, /reviewCards\?\.\[0\]/);
});

test("next learning keeps a single primary action and completion signal", () => {
  assert.match(pageSource, /const primary = items\[0\]/);
  assert.match(pageSource, /primary\.expectedCompletionSignal/);
  assert.doesNotMatch(pageSource, /items\.slice\(1\)/);
  assert.doesNotMatch(pageSource, /primary\.learningHypothesis/);
  assert.doesNotMatch(pageSource, /primary\.fallbackAction/);
});

test("recommendation route carries identity and tracking token", () => {
  assert.match(pageSource, /studentProfileId/);
  assert.match(pageSource, /recommendationToken/);
  assert.match(pageSource, /recordRecommendationEvent/);
});

test("loading empty and failure states all degrade to a usable task", () => {
  assert.match(pageSource, /recommendationLoading/);
  assert.match(pageSource, /recommendationFailed/);
  assert.match(pageSource, /!primary/);
  assert.match(pageSource, /fallbackPath/);
  assert.match(pageSource, /studentHome\.nextLearning\.keepLearning/);
  assert.doesNotMatch(pageSource, /setRecommendationReload/);
});

test("next learning has Chinese English and responsive contracts", () => {
  assert.equal((i18nSource.match(/nextLearning:/g) || []).length, 2);
  assert.match(i18nSource, /继续学习/);
  assert.match(i18nSource, /Continue Learning/);
  assert.match(styleSource, /\.student-next-learning/);
  assert.match(styleSource, /@media \(max-width: 520px\)/);
});

test("signed-in home declares three task-owned zones", () => {
  assert.match(pageSource, /data-home-zone="continue"/);
  assert.match(pageSource, /data-home-zone="classroom"/);
  assert.match(pageSource, /data-home-zone="practice"/);
  assert.match(pageSource, /student-self-practice-row/);
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const stylesheet = readFileSync(join(frontendRoot, "src", "styles.css"), "utf8");

test("assignment workbench width does not shift when a vertical scrollbar appears", () => {
  const rule = stylesheet.match(/\.main-shell > \.problem-page\.problem-workbench\s*\{([^}]+)\}/);
  assert.ok(rule, "problem workbench full-width rule should exist");
  assert.match(rule[1], /width:\s*100%;/);
  assert.match(rule[1], /margin:\s*0;/);
  assert.doesNotMatch(rule[1], /100vw|50vw/);
});

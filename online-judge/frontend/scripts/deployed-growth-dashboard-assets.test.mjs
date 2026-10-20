import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import test from "node:test";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const deployedAppRoot = resolve(frontendRoot, "..", "src", "main", "resources", "static", "code");
const deployedAssetsRoot = join(deployedAppRoot, "assets");

test("deployed app contains the redesigned growth dashboard assets", () => {
  assert.ok(existsSync(join(deployedAppRoot, "index.html")), "deployed app index should exist");
  assert.ok(existsSync(deployedAssetsRoot), "deployed app assets should exist");

  const deployedStyles = readdirSync(deployedAssetsRoot)
    .filter(fileName => fileName.endsWith(".css"))
    .map(fileName => readFileSync(join(deployedAssetsRoot, fileName), "utf8"))
    .join("\n");

  assert.match(deployedStyles, /growth-dashboard__metric-icon/);
  assert.match(deployedStyles, /growth-dashboard__trend-insight/);
  assert.match(deployedStyles, /growth-dashboard__analytics-grid/);
  assert.match(deployedStyles, /growth-dashboard__knowledge-list/);
  assert.match(deployedStyles, /growth-dashboard__donut/);
  assert.match(deployedStyles, /growth-dashboard__evolution-context/);
  assert.doesNotMatch(deployedStyles, /growth-dashboard__donut-submission/);
});

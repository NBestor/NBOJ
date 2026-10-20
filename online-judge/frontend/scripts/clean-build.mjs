import { readFileSync } from "node:fs";
import { rm } from "node:fs/promises";
import { resolve } from "node:path";

const routeContract = JSON.parse(
  readFileSync(new URL("../../config/route-ownership.json", import.meta.url), "utf8")
);
const publicPath = routeContract.onlineJudge.publicPath;
const legacyPath = routeContract.compatibility.legacyOnlineJudgePath;

function staticOutputPath(routePath) {
  if (!/^\/[a-z0-9][a-z0-9/_-]*\/$/.test(routePath)) {
    throw new Error(`Unsafe route path in route ownership contract: ${routePath}`);
  }
  return resolve(import.meta.dirname, "../../src/main/resources/static", routePath.slice(1));
}

const outDir = staticOutputPath(publicPath);
const legacyOutDir = staticOutputPath(legacyPath);
try {
  await Promise.all([
    rm(outDir, { recursive: true, force: true }),
    rm(legacyOutDir, { recursive: true, force: true })
  ]);
} catch (error) {
  if (error && typeof error === "object" && "code" in error && error.code === "EPERM") {
    console.warn(`[clean-build] ${outDir} is locked; continuing so Vite can overwrite current assets.`);
  } else {
    throw error;
  }
}

import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { readFileSync } from "node:fs";

const yingqiBanner =
  "/*! owner=yingqi; signature=00f40662ae433dacddf0157fca60a279bf71a54fbf04ee7d50d3190752554b5d; claim=yingqi|wenzhong-ai-learning-platform|nboj|2026-05-19 */";
const backendTarget = process.env.VITE_API_TARGET || process.env.API_TARGET || "http://localhost:8081";
const routeContract = JSON.parse(
  readFileSync(new URL("../config/route-ownership.json", import.meta.url), "utf8")
);
const publicPath = routeContract.onlineJudge.publicPath;
const publicApiPath = routeContract.onlineJudge.apiPath;
const publicAssetsPath = routeContract.onlineJudge.assetsPath;

if (!/^\/[a-z0-9][a-z0-9/_-]*\/$/.test(publicPath)
  || !publicApiPath.startsWith(publicPath)
  || !publicAssetsPath.startsWith(publicPath)) {
  throw new Error("config/route-ownership.json contains an invalid Online Judge path contract");
}

const publicPrefix = publicPath.slice(0, -1);
const publicApiPrefix = publicApiPath.slice(0, -1);

export default defineConfig({
  base: publicPath,
  plugins: [
    {
      name: "app-root-redirect",
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          const requestUrl = req.url || "";
          const [pathname, suffix = ""] = requestUrl.split(/(?=[?#])/);
          const redirectMap = new Map([
            ["/", publicPath],
            [publicPrefix, publicPath],
            ["/student", `${publicPrefix}/student`],
            ["/teacher", `${publicPrefix}/teacher`],
            ["/teacher-management", `${publicPrefix}/teacher/manage`],
            ["/task-editor", `${publicPrefix}/task-editor`],
            ["/class-overview", `${publicPrefix}/teacher/classes`],
            ["/problems", `${publicPrefix}/student/assignments/public`]
          ]);
          let target = redirectMap.get(pathname);
          target ||= pathname.startsWith("/teacher/assignment/") ? `${publicPrefix}${pathname}` : undefined;
          target ||= pathname.startsWith("/problem/") ? `${publicPrefix}${pathname}` : undefined;

          if (target) {
            res.statusCode = 302;
            res.setHeader("Location", `${target}${suffix}`);
            res.end();
            return;
          }
          next();
        });
      }
    },
    react()
  ],
  resolve: {
    preserveSymlinks: true
  },
  server: {
    proxy: {
      [publicApiPrefix]: {
        target: backendTarget,
        rewrite: path => path.slice(publicPrefix.length)
      },
      "/api": backendTarget,
      "/h2-console": backendTarget
    }
  },
  build: {
    outDir: `../src/main/resources/static${publicPrefix}`,
    emptyOutDir: false,
    sourcemap: false,
    rollupOptions: {
      output: {
        banner: yingqiBanner,
        manualChunks: {
          react: ["react", "react-dom", "react-router-dom"]
        }
      }
    }
  }
});

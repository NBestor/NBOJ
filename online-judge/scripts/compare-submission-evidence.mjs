#!/usr/bin/env node

const baseUrl = (process.argv[2] || process.env.OJ_BASE_URL || "http://127.0.0.1:8081").replace(/\/$/, "");
const requestedIds = process.argv.slice(3).map(Number).filter(Number.isFinite);

async function get(path) {
  const response = await fetch(`${baseUrl}${path}`);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${path}`);
  }
  return response.json();
}

function percent(value) {
  return typeof value === "number" ? `${Math.round(value * 100)}%` : "样本不足";
}

const assignments = await get("/api/teacher/assignments");
const selected = requestedIds.length
  ? assignments.filter(item => requestedIds.includes(item.id))
  : assignments;

console.log("# 提交证据新旧口径对比\n");
console.log("旧 `participantCount` 只代表有具名提交的学生；新口径同时给出在册、合法提交、尝试和完整性。匿名课堂历史只出现在缺失统计中。\n");
console.log("| 作业 | 旧参与人数 | 新在册 | 新提交 | 未提交 | 合法尝试 | 学生通过率 | 尝试通过率 | 身份缺失 | 未诊断 | 完整率 |");
console.log("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");

for (const assignment of selected) {
  const overview = await get(`/api/teacher/assignments/${assignment.id}/overview`);
  const completeness = overview.dataCompleteness || {};
  console.log([
    `| ${assignment.title}`,
    overview.participantCount ?? 0,
    overview.rosterStudentCount ?? "-",
    overview.submittedStudentCount ?? "-",
    overview.unsubmittedStudentCount ?? "-",
    overview.attemptCount ?? 0,
    percent(overview.studentPassRate),
    percent(overview.attemptPassRate),
    completeness.identityMissingCount ?? "-",
    completeness.analysisMissingCount ?? "-",
    `${percent(completeness.completeRate)} |`
  ].join(" | "));
}

console.log("\n核验时应从路径桶的 `evidenceSubmissionIds` 下钻到提交；恢复率分母只包含存在可比较同题后续提交的样本。");

#!/usr/bin/env python3
"""Generate deterministic complex diagnosis fixtures.

The fixture truth comes from bug recipes plus executable evidence. This script
does not crawl or use external student code.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import textwrap
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parent
OUTPUT = Path(os.environ.get("COMPLEX_FIXTURE_OUTPUT", str(ROOT / "complex-student-submission-cases.json")))
TOTAL_CASES = 210
LIVE_CANDIDATES = 42
MIN_SOURCE_LINES = 50
LARGE_SOURCE_LINES = 100
MIN_LARGE_CASES = 80

MUST_NOT_MENTION = ["完整代码", "参考答案", "隐藏测试点", "直接改成", "for _ in range", "def solve"]
QUALITY_METRICS = [
    "primaryRootCauseHit",
    "teachingPriorityCorrect",
    "secondaryIssuesNotOverweighted",
    "distractingSignalsIgnored",
    "evidenceGrounded",
    "noFullSolutionLeak",
]


@dataclass(frozen=True)
class TestCase:
    input_data: str
    expected_output: str


@dataclass(frozen=True)
class Template:
    slug: str
    title: str
    description: str
    knowledge_points: list[str]
    algorithm_strategies: list[str]
    common_mistakes: list[str]
    boundary_types: list[str]
    tests: list[TestCase]
    correct_code: str
    bug_code: str
    primary_issue: str
    primary_fine: str
    secondary_issues: list[str]
    distracting_signals: list[str]
    teaching_priority: str
    must_mention: list[str]
    misconception: str
    expected_student_move: str


def normalize_code(code: str) -> str:
    return textwrap.dedent(code).strip() + "\n"


def run_python(code: str, input_data: str, timeout_seconds: float = 1.0) -> tuple[str, str, str]:
    with tempfile.NamedTemporaryFile("w", suffix=".py", encoding="utf-8", delete=False) as handle:
        handle.write(code)
        script_path = Path(handle.name)
    try:
        completed = subprocess.run(
            [sys.executable, str(script_path)],
            input=input_data,
            text=True,
            capture_output=True,
            timeout=timeout_seconds,
            check=False,
        )
        stdout = completed.stdout.strip().replace(str(script_path), "<student.py>")
        stderr = completed.stderr.strip().replace(str(script_path), "<student.py>")
        if completed.returncode != 0:
            return "RUNTIME_ERROR", stdout, stderr
        return "OK", stdout, stderr
    except subprocess.TimeoutExpired as exc:
        return "TIME_LIMIT_EXCEEDED", (exc.stdout or "").strip(), (exc.stderr or "").strip()
    finally:
        try:
            script_path.unlink()
        except FileNotFoundError:
            pass


def verify_correct(template: Template) -> None:
    for index, test in enumerate(template.tests, start=1):
        status, actual, stderr = run_python(template.correct_code, test.input_data)
        if status != "OK" or actual != test.expected_output.strip():
            raise AssertionError(
                f"{template.slug} correct solution failed case {index}: "
                f"status={status}, actual={actual!r}, expected={test.expected_output!r}, stderr={stderr!r}"
            )


def first_failed_case(template: Template) -> tuple[int, str, str, str, str]:
    for index, test in enumerate(template.tests, start=1):
        status, actual, stderr = run_python(template.bug_code, test.input_data)
        expected = test.expected_output.strip()
        if status == "TIME_LIMIT_EXCEEDED":
            return index, "TIME_LIMIT_EXCEEDED", actual, expected, stderr
        if status == "RUNTIME_ERROR":
            return index, "RUNTIME_ERROR", actual or stderr, expected, stderr
        if actual != expected:
            return index, "WRONG_ANSWER", actual, expected, stderr
    raise AssertionError(f"{template.slug} buggy submission unexpectedly passed all tests")


def code_lines(code: str) -> int:
    return len(code.strip("\n").splitlines())


def base_templates() -> list[Template]:
    return [
        Template(
            slug="multi-query-prefix",
            title="多次区间统计",
            description="第一行输入 n 和 q，第二行输入 n 个整数。接下来 q 行每行给出 l r，需要逐行输出区间 [l,r] 的元素和。",
            knowledge_points=["前缀和", "输入读取", "循环"],
            algorithm_strategies=["前缀和", "多次查询"],
            common_mistakes=["只读取一次查询", "调试输出污染结果", "把 q 当作可选信息"],
            boundary_types=["q=1", "q>1", "单元素区间"],
            tests=[
                TestCase("5 2\n1 2 3 4 5\n1 3\n2 5\n", "6\n14"),
                TestCase("4 3\n2 2 2 2\n1 1\n1 4\n3 4\n", "2\n8\n4"),
                TestCase("3 1\n5 6 7\n2 2\n", "6"),
            ],
            correct_code=normalize_code(
                """
                import sys

                def read_numbers():
                    return list(map(int, sys.stdin.readline().split()))

                def build_prefix(values):
                    prefix = [0]
                    running = 0
                    for value in values:
                        running += value
                        prefix.append(running)
                    return prefix

                def answer_query(prefix, left, right):
                    return prefix[right] - prefix[left - 1]

                def solve():
                    n, q = read_numbers()
                    values = read_numbers()
                    prefix = build_prefix(values)
                    answers = []
                    for _ in range(q):
                        left, right = read_numbers()
                        answers.append(str(answer_query(prefix, left, right)))
                    print("\\n".join(answers))

                if __name__ == "__main__":
                    solve()
                """
            ),
            bug_code=normalize_code(
                """
                import sys

                def read_numbers():
                    line = sys.stdin.readline().strip()
                    if not line:
                        return []
                    return list(map(int, line.split()))

                def normalize_bounds(left, right, size):
                    if left < 1:
                        left = 1
                    if right > size:
                        right = size
                    return left, right

                def build_prefix(values):
                    prefix = [0]
                    total = 0
                    for index, value in enumerate(values):
                        total += value
                        prefix.append(total)
                    return prefix

                def answer_query(prefix, left, right):
                    if left > right:
                        return 0
                    return prefix[right] - prefix[left - 1]

                def format_answer(value, debug_enabled=False):
                    if debug_enabled:
                        return "sum=" + str(value)
                    return str(value)

                def solve():
                    header = read_numbers()
                    if not header:
                        return
                    n, q = header
                    values = read_numbers()
                    prefix = build_prefix(values)
                    first_query = read_numbers()
                    if not first_query:
                        return
                    left, right = normalize_bounds(first_query[0], first_query[1], n)
                    result = answer_query(prefix, left, right)
                    if q > 2:
                        sys.stderr.write("debug: multiple queries detected\\n")
                    print(format_answer(result, False))

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="IO_FORMAT",
            primary_fine="INPUT_PARSING",
            secondary_issues=["OUTPUT_FORMAT_DETAIL", "BOUNDARY_CONDITION"],
            distracting_signals=["normalize_bounds helper makes boundary handling look central", "stderr debug branch is not the first failed behavior"],
            teaching_priority="先对照题面 q 行查询和代码实际读取次数，因为 first failed case 少输出了第二次查询。",
            must_mention=["输入结构", "多次查询", "读取次数"],
            misconception="把 q 次查询当成只需要处理第一组。",
            expected_student_move="逐行对照题面输入和代码读取次数，找第一处数量不一致。",
        ),
        Template(
            slug="multi-case-run-reset",
            title="多组连续段统计",
            description="第一行输入 T。每组输入一个 01 字符串，分别输出该组字符串中最长连续 1 的长度，每组数据必须独立计算。",
            knowledge_points=["字符串", "多组数据", "状态变量"],
            algorithm_strategies=["线性扫描"],
            common_mistakes=["组间状态不重置", "上一组 best 污染下一组", "把全局最大值当作单组答案"],
            boundary_types=["全 0", "全 1", "连续多组"],
            tests=[
                TestCase("2\n111\n0\n", "3\n0"),
                TestCase("3\n1011\n00\n11110\n", "2\n0\n4"),
                TestCase("1\n01010\n", "1"),
            ],
            correct_code=normalize_code(
                """
                def update_run(ch, current, best):
                    if ch == "1":
                        current += 1
                        best = max(best, current)
                    else:
                        current = 0
                    return current, best

                def solve_case(text):
                    current = 0
                    best = 0
                    for ch in text.strip():
                        current, best = update_run(ch, current, best)
                    return best

                def solve():
                    t = int(input())
                    answers = []
                    for _ in range(t):
                        answers.append(str(solve_case(input().strip())))
                    print("\\n".join(answers))

                if __name__ == "__main__":
                    solve()
                """
            ),
            bug_code=normalize_code(
                """
                def update_run(ch, current, best):
                    if ch == "1":
                        current += 1
                        if current > best:
                            best = current
                    else:
                        current = 0
                    return current, best

                def sanitize(text):
                    return "".join(ch for ch in text.strip() if ch in "01")

                def describe_case(index, text):
                    if len(text) == 0:
                        return "empty"
                    if text.count("1") == len(text):
                        return "all-one"
                    return "mixed"

                def solve_case(text, current, best):
                    cleaned = sanitize(text)
                    for ch in cleaned:
                        current, best = update_run(ch, current, best)
                    return current, best

                def solve():
                    t = int(input())
                    current = 0
                    best = 0
                    answers = []
                    for case_index in range(t):
                        text = input().strip()
                        label = describe_case(case_index, text)
                        if label == "empty":
                            answers.append("0")
                            continue
                        current, best = solve_case(text, current, best)
                        answers.append(str(best))
                    print("\\n".join(answers))

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="VARIABLE_INITIALIZATION",
            primary_fine="STATE_RESET",
            secondary_issues=["BOUNDARY_CONDITION", "CONDITION_BRANCH"],
            distracting_signals=["sanitize helper suggests input cleaning", "empty branch is not responsible for first failed output"],
            teaching_priority="先检查每组开始前 current 和 best 是否重置，因为第二组全 0 仍输出上一组 best。",
            must_mention=["多组", "状态", "重置"],
            misconception="认为多组数据可以共享上一组扫描状态。",
            expected_student_move="写出第二组开始前 current 和 best 的值。",
        ),
        Template(
            slug="house-dp-state",
            title="不相邻选择最大和",
            description="给定 n 个非负整数，选择若干位置使任意两个被选位置不相邻，输出可获得的最大和。",
            knowledge_points=["动态规划", "状态设计"],
            algorithm_strategies=["DP"],
            common_mistakes=["状态来源不足", "只比较当前值和上一答案", "忽略隔一个位置的转移"],
            boundary_types=["n=1", "相邻大数", "多个小数累积"],
            tests=[
                TestCase("5\n2 7 9 3 1\n", "12"),
                TestCase("4\n5 1 1 5\n", "10"),
                TestCase("1\n8\n", "8"),
            ],
            correct_code=normalize_code(
                """
                def solve_values(values):
                    if not values:
                        return 0
                    if len(values) == 1:
                        return values[0]
                    dp = [0] * len(values)
                    dp[0] = values[0]
                    dp[1] = max(values[0], values[1])
                    for i in range(2, len(values)):
                        dp[i] = max(dp[i - 1], dp[i - 2] + values[i])
                    return dp[-1]

                n = int(input())
                values = list(map(int, input().split()))
                print(solve_values(values))
                """
            ),
            bug_code=normalize_code(
                """
                def choose_best(previous, current):
                    if previous >= current:
                        return previous
                    return current

                def parse_values():
                    n = int(input())
                    values = list(map(int, input().split()))
                    while len(values) < n:
                        values.append(0)
                    return values[:n]

                def explain_state(index, value, best):
                    if index < 0:
                        return "init"
                    if value == best:
                        return "take-current"
                    return "carry"

                def solve_values(values):
                    n = len(values)
                    if n == 0:
                        return 0
                    dp = [0] * n
                    dp[0] = values[0]
                    for i in range(1, n):
                        skip_current = dp[i - 1]
                        take_current = values[i]
                        dp[i] = choose_best(skip_current, take_current)
                        explain_state(i, values[i], dp[i])
                    return dp[-1]

                def solve():
                    values = parse_values()
                    answer = solve_values(values)
                    print(answer)

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="ALGORITHM_STRATEGY",
            primary_fine="DP_STATE_DESIGN",
            secondary_issues=["STATE_TRANSITION", "BOUNDARY_CONDITION"],
            distracting_signals=["parse_values pads missing inputs", "explain_state function looks suspicious but has no effect"],
            teaching_priority="先让学生用自然语言定义 dp[i] 的含义，再检查选择当前元素时信息来源是否足够。",
            must_mention=["DP", "状态", "不相邻"],
            misconception="认为当前元素只能和上一轮最大值或自己比较。",
            expected_student_move="写出 dp[i] 应该表示什么，以及选择第 i 个位置时还需要哪个历史状态。",
        ),
        Template(
            slug="coin-greedy-trap",
            title="任意硬币最少数量",
            description="给定 n 种硬币面值和目标金额 amount，每种硬币可无限使用，输出凑出金额所需的最少硬币数，无法凑出输出 -1。",
            knowledge_points=["贪心", "反例", "动态规划"],
            algorithm_strategies=["最短路径思想", "DP"],
            common_mistakes=["默认大面值优先总是最优", "缺少贪心证明", "只用样例验证"],
            boundary_types=["无法凑出", "非规范硬币系统", "amount=0"],
            tests=[
                TestCase("3 6\n1 3 4\n", "2"),
                TestCase("2 7\n2 4\n", "-1"),
                TestCase("3 11\n1 5 6\n", "2"),
            ],
            correct_code=normalize_code(
                """
                n, amount = map(int, input().split())
                coins = list(map(int, input().split()))
                inf = 10 ** 9
                dp = [inf] * (amount + 1)
                dp[0] = 0
                for total in range(1, amount + 1):
                    for coin in coins:
                        if total >= coin:
                            dp[total] = min(dp[total], dp[total - coin] + 1)
                print(-1 if dp[amount] == inf else dp[amount])
                """
            ),
            bug_code=normalize_code(
                """
                def read_input():
                    n, amount = map(int, input().split())
                    coins = list(map(int, input().split()))
                    return n, amount, coins

                def sort_coins(coins):
                    unique = []
                    seen = set()
                    for coin in coins:
                        if coin not in seen:
                            unique.append(coin)
                            seen.add(coin)
                    unique.sort(reverse=True)
                    return unique

                def greedy_count(amount, coins):
                    count = 0
                    remain = amount
                    trace = []
                    for coin in coins:
                        if coin <= 0:
                            continue
                        take = remain // coin
                        if take:
                            trace.append((coin, take))
                        count += take
                        remain -= take * coin
                    if remain != 0:
                        return -1
                    return count

                def solve():
                    n, amount, coins = read_input()
                    coins = sort_coins(coins[:n])
                    if amount == 0:
                        print(0)
                        return
                    print(greedy_count(amount, coins))

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="ALGORITHM_STRATEGY",
            primary_fine="GREEDY_ASSUMPTION",
            secondary_issues=["SAMPLE_ONLY", "BOUNDARY_CONDITION"],
            distracting_signals=["deduplicate coins makes data structure choice look relevant", "amount zero branch is correct but not related"],
            teaching_priority="先构造反例检验大面值优先是否总能得到最少数量。",
            must_mention=["贪心", "反例", "局部选择"],
            misconception="把大面值优先当成无需证明的最优策略。",
            expected_student_move="用 1,3,4 凑 6 这样的反例手算局部选择和全局最优的差异。",
        ),
        Template(
            slug="large-range-simulation",
            title="超大区间累加",
            description="给定 l 和 r，1 <= l <= r <= 10^9，输出 l 到 r 的整数和。需要关注最大规模输入。",
            knowledge_points=["复杂度", "数学公式", "边界"],
            algorithm_strategies=["公式推导"],
            common_mistakes=["逐个模拟超大区间", "只按小样例估计性能", "忽略最大边界"],
            boundary_types=["单点区间", "最大 r", "大跨度"],
            tests=[
                TestCase("1 5\n", "15"),
                TestCase("100000000 100000010\n", "1100000055"),
                TestCase("1 1000000000\n", "500000000500000000"),
            ],
            correct_code=normalize_code(
                """
                l, r = map(int, input().split())
                count = r - l + 1
                print((l + r) * count // 2)
                """
            ),
            bug_code=normalize_code(
                """
                import sys

                def read_bounds():
                    raw = sys.stdin.readline().split()
                    if len(raw) < 2:
                        return 0, -1
                    left, right = map(int, raw[:2])
                    if left > right:
                        left, right = right, left
                    return left, right

                def add_with_checkpoint(total, value, checkpoint):
                    total += value
                    if value == checkpoint:
                        pass
                    return total

                def simulate_sum(left, right):
                    total = 0
                    checkpoint = left + (right - left) // 2
                    current = left
                    while current <= right:
                        total = add_with_checkpoint(total, current, checkpoint)
                        current += 1
                    return total

                def solve():
                    left, right = read_bounds()
                    if right < left:
                        print(0)
                        return
                    if right - left <= 2:
                        print(simulate_sum(left, right))
                        return
                    answer = simulate_sum(left, right)
                    print(answer)

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="TIME_COMPLEXITY",
            primary_fine="MAX_BOUNDARY",
            secondary_issues=["OVER_SIMULATION", "BOUNDARY_CONDITION"],
            distracting_signals=["read_bounds swaps invalid range", "checkpoint helper makes state update look important"],
            teaching_priority="先估算最大区间跨度下 while 循环次数，再讨论是否能用公式或更低复杂度。",
            must_mention=["最大规模", "复杂度", "循环次数"],
            misconception="小样例能跑过就认为逐步模拟可以接受。",
            expected_student_move="把 r=10^9 时的循环次数写出来并和时间限制比较。",
        ),
        Template(
            slug="output-format-extra",
            title="按行输出平方",
            description="输入 n 和 n 个整数，按原顺序每行输出一个数的平方，不能输出多余提示、编号或空格。",
            knowledge_points=["输出格式", "循环", "字符串"],
            algorithm_strategies=["直接模拟"],
            common_mistakes=["输出多余编号", "空格换行不符合题面", "把调试信息留在标准输出"],
            boundary_types=["n=1", "负数", "零"],
            tests=[
                TestCase("3\n2 -1 0\n", "4\n1\n0"),
                TestCase("1\n5\n", "25"),
                TestCase("2\n-3 4\n", "9\n16"),
            ],
            correct_code=normalize_code(
                """
                n = int(input())
                values = list(map(int, input().split()))
                print("\\n".join(str(value * value) for value in values[:n]))
                """
            ),
            bug_code=normalize_code(
                """
                def parse_input():
                    n = int(input())
                    values = list(map(int, input().split()))
                    return n, values

                def square(value):
                    return value * value

                def decorate(index, value, verbose=False):
                    if verbose:
                        return "case " + str(index) + ": " + str(value)
                    return str(index) + ": " + str(value)

                def build_lines(n, values):
                    lines = []
                    for index in range(n):
                        if index >= len(values):
                            lines.append("0")
                            continue
                        value = square(values[index])
                        lines.append(decorate(index + 1, value, False))
                    return lines

                def solve():
                    n, values = parse_input()
                    lines = build_lines(n, values)
                    print("\\n".join(lines))

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="IO_FORMAT",
            primary_fine="OUTPUT_FORMAT_DETAIL",
            secondary_issues=["BOUNDARY_CONDITION", "CODE_READABILITY"],
            distracting_signals=["missing value branch appends zero", "decorate helper suggests presentation feature"],
            teaching_priority="先逐字符比较实际输出和期望输出，确认多余编号来自格式而不是计算错误。",
            must_mention=["输出格式", "多余字符", "逐字对比"],
            misconception="认为输出带编号更清楚但忽略了判题要求严格匹配。",
            expected_student_move="把第一行实际输出和期望输出逐字符对齐。",
        ),
        Template(
            slug="zero-division-guard",
            title="平均通过率",
            description="输入 n 和 n 行 a b，表示通过人数和总人数。输出每行通过率 a/b，保留两位小数；当 b 为 0 时输出 0.00。",
            knowledge_points=["运行时稳定性", "除零", "输入处理"],
            algorithm_strategies=["直接模拟"],
            common_mistakes=["没有处理除零", "多组数据状态混杂", "格式化输出遗漏"],
            boundary_types=["b=0", "n=1", "a=0"],
            tests=[
                TestCase("3\n1 2\n0 0\n3 4\n", "0.50\n0.00\n0.75"),
                TestCase("1\n0 5\n", "0.00"),
                TestCase("2\n2 2\n0 0\n", "1.00\n0.00"),
            ],
            correct_code=normalize_code(
                """
                n = int(input())
                answers = []
                for _ in range(n):
                    passed, total = map(int, input().split())
                    rate = 0.0 if total == 0 else passed / total
                    answers.append(f"{rate:.2f}")
                print("\\n".join(answers))
                """
            ),
            bug_code=normalize_code(
                """
                def parse_pair(text):
                    parts = text.split()
                    if len(parts) < 2:
                        return 0, 0
                    return int(parts[0]), int(parts[1])

                def clamp_passed(passed, total):
                    if passed < 0:
                        return 0
                    if total >= 0 and passed > total:
                        return total
                    return passed

                def format_rate(passed, total):
                    passed = clamp_passed(passed, total)
                    rate = passed / total
                    return f"{rate:.2f}"

                def solve():
                    n = int(input())
                    answers = []
                    for _ in range(n):
                        passed, total = parse_pair(input())
                        answers.append(format_rate(passed, total))
                    print("\\n".join(answers))

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="RUNTIME_STABILITY",
            primary_fine="EMPTY_INPUT",
            secondary_issues=["BOUNDARY_CONDITION", "OUTPUT_FORMAT_DETAIL"],
            distracting_signals=["clamp_passed handles invalid counts", "formatting is correct when total is nonzero"],
            teaching_priority="先根据异常和 b=0 用例检查除法前的运行时保护。",
            must_mention=["除零", "运行时", "b 为 0"],
            misconception="只检查人数范围，没有检查分母为 0 的运行稳定性。",
            expected_student_move="手推 total=0 进入 format_rate 时下一步执行什么。",
        ),
        Template(
            slug="off-by-one-window",
            title="固定长度窗口最大和",
            description="输入 n、k 和 n 个整数，输出所有长度恰好为 k 的连续子数组中的最大和。窗口必须包含 k 个元素。",
            knowledge_points=["滑动窗口", "循环边界", "数组"],
            algorithm_strategies=["滑动窗口"],
            common_mistakes=["少枚举最后一个窗口", "窗口长度少一个元素", "边界循环写错"],
            boundary_types=["k=1", "k=n", "最后窗口最优"],
            tests=[
                TestCase("5 3\n1 2 3 10 1\n", "15"),
                TestCase("4 4\n5 1 1 1\n", "8"),
                TestCase("3 1\n7 2 9\n", "9"),
            ],
            correct_code=normalize_code(
                """
                n, k = map(int, input().split())
                values = list(map(int, input().split()))
                current = sum(values[:k])
                best = current
                for right in range(k, n):
                    current += values[right]
                    current -= values[right - k]
                    best = max(best, current)
                print(best)
                """
            ),
            bug_code=normalize_code(
                """
                def read_case():
                    n, k = map(int, input().split())
                    values = list(map(int, input().split()))
                    return n, k, values

                def initial_window(values, k):
                    total = 0
                    for index in range(max(0, k - 1)):
                        total += values[index]
                    return total

                def update_window(total, values, right, k):
                    total += values[right]
                    left = right - k
                    if left >= 0:
                        total -= values[left]
                    return total

                def choose_best(old, new):
                    if new > old:
                        return new
                    return old

                def solve():
                    n, k, values = read_case()
                    if k <= 0 or not values:
                        print(0)
                        return
                    current = initial_window(values, k)
                    best = current
                    for right in range(k - 1, n - 1):
                        current = update_window(current, values, right, k)
                        best = choose_best(best, current)
                    print(best)

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="LOOP_BOUNDARY",
            primary_fine="OFF_BY_ONE",
            secondary_issues=["BOUNDARY_CONDITION", "STATE_TRANSITION"],
            distracting_signals=["initial_window uses k - 1", "empty guard looks like main boundary branch"],
            teaching_priority="先列出 right 的取值，确认最后一个长度为 k 的窗口是否被枚举。",
            must_mention=["循环边界", "最后一个窗口", "k 个元素"],
            misconception="把窗口右端点范围少写一位，导致最后一个候选没被比较。",
            expected_student_move="用 n=5,k=3 写出所有窗口右端点和代码实际访问的 right。",
        ),
        Template(
            slug="duplicate-count-set",
            title="出现次数不少于两次的数字个数",
            description="输入 n 和 n 个整数，输出有多少个不同数字出现次数不少于两次。重复次数需要被统计，不能只看集合大小。",
            knowledge_points=["哈希表", "重复元素", "计数"],
            algorithm_strategies=["频次统计"],
            common_mistakes=["用 set 丢失次数", "把不同元素数量当作重复元素数量", "重复场景缺少测试"],
            boundary_types=["无重复", "全部相同", "多个重复值"],
            tests=[
                TestCase("6\n1 2 2 3 3 3\n", "2"),
                TestCase("4\n1 2 3 4\n", "0"),
                TestCase("5\n7 7 7 7 7\n", "1"),
            ],
            correct_code=normalize_code(
                """
                n = int(input())
                values = list(map(int, input().split()))
                counts = {}
                for value in values[:n]:
                    counts[value] = counts.get(value, 0) + 1
                print(sum(1 for count in counts.values() if count >= 2))
                """
            ),
            bug_code=normalize_code(
                """
                def read_values():
                    n = int(input())
                    values = list(map(int, input().split()))
                    return n, values[:n]

                def collect_unique(values):
                    seen = set()
                    order = []
                    for value in values:
                        if value not in seen:
                            seen.add(value)
                            order.append(value)
                    return seen, order

                def maybe_duplicate_score(values):
                    seen, order = collect_unique(values)
                    missing = len(values) - len(seen)
                    if missing <= 0:
                        return 0
                    return missing

                def solve():
                    n, values = read_values()
                    score = maybe_duplicate_score(values)
                    print(score)

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="BOUNDARY_CONDITION",
            primary_fine="DUPLICATE_CASE",
            secondary_issues=["DATA_STRUCTURE_CHOICE", "ALGORITHM_STRATEGY"],
            distracting_signals=["order list preserves first occurrence", "missing count can match some simple cases"],
            teaching_priority="先构造多个值都重复的样例，比较缺失个数和重复数字种类数。",
            must_mention=["重复元素", "次数", "不同数字"],
            misconception="把重复出现的总次数差当成重复数字种类数。",
            expected_student_move="对 1 2 2 3 3 3 手写频次表，再和 set 后的信息比较。",
        ),
        Template(
            slug="initial-best-negative",
            title="最大连续子段和",
            description="输入 n 和 n 个整数，输出非空连续子数组的最大和。数组可能全为负数，必须选择至少一个元素。",
            knowledge_points=["状态初始化", "动态规划", "数组"],
            algorithm_strategies=["Kadane"],
            common_mistakes=["best 初始化为 0", "把空子数组当成合法答案", "全负数边界漏测"],
            boundary_types=["全负数", "单元素", "正负混合"],
            tests=[
                TestCase("3\n-5 -2 -7\n", "-2"),
                TestCase("5\n-2 3 -1 4 -5\n", "6"),
                TestCase("1\n-8\n", "-8"),
            ],
            correct_code=normalize_code(
                """
                n = int(input())
                values = list(map(int, input().split()))
                best = values[0]
                current = values[0]
                for value in values[1:n]:
                    current = max(value, current + value)
                    best = max(best, current)
                print(best)
                """
            ),
            bug_code=normalize_code(
                """
                def read_values():
                    n = int(input())
                    values = list(map(int, input().split()))
                    return values[:n]

                def update_state(current, best, value):
                    current = max(0, current + value)
                    if current > best:
                        best = current
                    return current, best

                def classify(value):
                    if value > 0:
                        return "positive"
                    if value == 0:
                        return "zero"
                    return "negative"

                def solve():
                    values = read_values()
                    current = 0
                    best = 0
                    for value in values:
                        classify(value)
                        current, best = update_state(current, best, value)
                    print(best)

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="VARIABLE_INITIALIZATION",
            primary_fine="INITIAL_STATE",
            secondary_issues=["BOUNDARY_CONDITION", "STATE_TRANSITION"],
            distracting_signals=["classify helper mentions negative values", "update_state looks like common Kadane pattern"],
            teaching_priority="先确认题目要求非空子数组，再检查 best 的初值是否允许空答案。",
            must_mention=["初始状态", "全负数", "非空"],
            misconception="默认最大和至少为 0，忽略了必须选择一个元素。",
            expected_student_move="用全负数样例写出循环前 current 和 best 的含义。",
        ),
        Template(
            slug="pair-sum-bruteforce",
            title="两数和存在性",
            description="输入 n、target 和 n 个整数，判断是否存在两个不同位置的数相加等于 target，存在输出 YES，否则输出 NO。n 可达到 200000。",
            knowledge_points=["哈希表", "复杂度", "查找"],
            algorithm_strategies=["哈希集合"],
            common_mistakes=["双重循环超时", "只看小样例", "没有估算 n^2"],
            boundary_types=["无解", "重复值", "最大规模"],
            tests=[
                TestCase("5 9\n2 7 11 15 1\n", "YES"),
                TestCase("4 8\n1 2 3 4\n", "NO"),
                TestCase("200000 3\n" + " ".join(["1"] * 200000) + "\n", "NO"),
            ],
            correct_code=normalize_code(
                """
                n, target = map(int, input().split())
                values = list(map(int, input().split()))
                seen = set()
                ok = False
                for value in values[:n]:
                    if target - value in seen:
                        ok = True
                        break
                    seen.add(value)
                print("YES" if ok else "NO")
                """
            ),
            bug_code=normalize_code(
                """
                def read_case():
                    n, target = map(int, input().split())
                    values = list(map(int, input().split()))
                    return n, target, values[:n]

                def same_position_guard(i, j):
                    return i != j

                def has_pair(values, target):
                    for i in range(len(values)):
                        for j in range(len(values)):
                            if same_position_guard(i, j) and values[i] + values[j] == target:
                                return True
                    return False

                def solve():
                    n, target, values = read_case()
                    if n < 2:
                        print("NO")
                        return
                    print("YES" if has_pair(values, target) else "NO")

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="TIME_COMPLEXITY",
            primary_fine="BRUTE_FORCE_LIMIT",
            secondary_issues=["DATA_STRUCTURE_CHOICE", "DUPLICATE_CASE"],
            distracting_signals=["same_position_guard is correct", "nested loops pass small visible cases"],
            teaching_priority="先估算 n=200000 时双重循环次数，再考虑需要保存哪些已见信息。",
            must_mention=["暴力", "规模", "循环次数"],
            misconception="用小样例通过来证明双重循环足够快。",
            expected_student_move="计算最大 n 下内层判断次数数量级。",
        ),
        Template(
            slug="sample-overfit-special",
            title="奇偶分组计数",
            description="输入 n 和 n 个整数，输出偶数个数和奇数个数。测试数据不保证和样例相同，隐藏数据会覆盖全偶数、全奇数与混合场景。",
            knowledge_points=["泛化", "条件分支", "计数"],
            algorithm_strategies=["直接统计"],
            common_mistakes=["硬编码样例", "只处理见过的输入", "缺少自造反例"],
            boundary_types=["全偶数", "全奇数", "混合"],
            tests=[
                TestCase("5\n1 2 3 4 5\n", "2 3"),
                TestCase("4\n2 4 6 8\n", "4 0"),
                TestCase("3\n7 9 10\n", "1 2"),
            ],
            correct_code=normalize_code(
                """
                n = int(input())
                values = list(map(int, input().split()))
                even = sum(1 for value in values[:n] if value % 2 == 0)
                odd = n - even
                print(even, odd)
                """
            ),
            bug_code=normalize_code(
                """
                def read_case():
                    n = int(input())
                    values = list(map(int, input().split()))
                    return n, values

                def looks_like_sample(n, values):
                    return n == 5 and values[:5] == [1, 2, 3, 4, 5]

                def count_by_memory(n, values):
                    if looks_like_sample(n, values):
                        return 2, 3
                    if n == 3:
                        return 1, 2
                    return 0, n

                def solve():
                    n, values = read_case()
                    even, odd = count_by_memory(n, values)
                    print(even, odd)

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="SAMPLE_ONLY",
            primary_fine="SAMPLE_OVERFIT",
            secondary_issues=["CONDITION_BRANCH", "GENERALIZATION_CHECK"],
            distracting_signals=["n == 3 branch accidentally passes one extra case", "function names look intentional"],
            teaching_priority="先用不同于样例的全偶数输入验证代码是否真的统计，而不是记住样例。",
            must_mention=["样例", "泛化", "自造反例"],
            misconception="把样例输出模式当成题目规律。",
            expected_student_move="构造一个全偶数小样例，看代码是否仍按题意统计。",
        ),
        Template(
            slug="partial-fix-regression",
            title="括号余额检查",
            description="输入一个只含括号的字符串，若括号序列合法输出 YES，否则输出 NO。任何前缀右括号都不能多于左括号。",
            knowledge_points=["栈", "前缀状态", "回归"],
            algorithm_strategies=["状态扫描"],
            common_mistakes=["只检查最终数量", "修复一个样例后破坏前缀约束", "没有比较前后提交差异"],
            boundary_types=["空前缀", "右括号开头", "最终数量相等但非法"],
            tests=[
                TestCase("())(\n", "NO"),
                TestCase("()()\n", "YES"),
                TestCase(")(\n", "NO"),
            ],
            correct_code=normalize_code(
                """
                s = input().strip()
                balance = 0
                ok = True
                for ch in s:
                    if ch == "(":
                        balance += 1
                    else:
                        balance -= 1
                    if balance < 0:
                        ok = False
                        break
                if balance != 0:
                    ok = False
                print("YES" if ok else "NO")
                """
            ),
            bug_code=normalize_code(
                """
                def normalize(text):
                    return "".join(ch for ch in text if ch in "()")

                def final_balance(text):
                    balance = 0
                    minimum = 0
                    for ch in text:
                        if ch == "(":
                            balance += 1
                        else:
                            balance -= 1
                        minimum = min(minimum, balance)
                    return balance, minimum

                def solve():
                    text = normalize(input().strip())
                    balance, minimum = final_balance(text)
                    if balance == 0:
                        print("YES")
                    else:
                        print("NO")

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="NEEDS_MORE_EVIDENCE",
            primary_fine="PARTIAL_FIX_REGRESSION",
            secondary_issues=["CONDITION_BRANCH", "STATE_TRANSITION"],
            distracting_signals=["minimum is computed but ignored", "final balance passes many balanced examples"],
            teaching_priority="先比较最终 balance 和扫描过程中的 minimum，定位局部修复遗漏的前缀约束。",
            must_mention=["回退", "前缀", "比较"],
            misconception="修到最终左右数量相等后，就误以为括号序列一定合法。",
            expected_student_move="对 '())(' 写出每一步 balance 和 minimum。",
        ),
        Template(
            slug="in-place-swap-progress",
            title="把 0 移到数组末尾",
            description="输入 n 和 n 个 0/1 数字，保持 1 的相对顺序，把所有 0 移到末尾并输出结果。",
            knowledge_points=["原地修改", "循环不变量", "数组"],
            algorithm_strategies=["双指针"],
            common_mistakes=["交换后指针推进过快", "当前位置新值未继续检查", "原地状态未稳定"],
            boundary_types=["连续 0", "全 0", "交替 0/1"],
            tests=[
                TestCase("5\n0 0 1 1 0\n", "1 1 0 0 0"),
                TestCase("4\n1 0 1 0\n", "1 1 0 0"),
                TestCase("3\n0 0 0\n", "0 0 0"),
            ],
            correct_code=normalize_code(
                """
                n = int(input())
                values = list(map(int, input().split()))
                ones = [value for value in values[:n] if value == 1]
                zeros = [0] * (n - len(ones))
                print(" ".join(map(str, ones + zeros)))
                """
            ),
            bug_code=normalize_code(
                """
                def read_values():
                    n = int(input())
                    values = list(map(int, input().split()))
                    return n, values[:n]

                def find_next_one(values, start):
                    index = start
                    while index < len(values) and values[index] == 0:
                        index += 1
                    return index

                def move_zeroes(values):
                    i = 0
                    while i < len(values):
                        if values[i] == 0:
                            j = find_next_one(values, i + 1)
                            if j < len(values):
                                values[i], values[j] = values[j], values[i]
                                i += 1
                            else:
                                break
                        i += 1
                    return values

                def solve():
                    n, values = read_values()
                    result = move_zeroes(values)
                    print(" ".join(map(str, result)))

                if __name__ == "__main__":
                    solve()
                """
            ),
            primary_issue="STATE_TRANSITION",
            primary_fine="IN_PLACE_STATE_PROGRESS",
            secondary_issues=["LOOP_BOUNDARY", "DUPLICATE_CASE"],
            distracting_signals=["find_next_one seems to handle runs of zero", "double increment is easy to miss"],
            teaching_priority="先跟踪一次交换后 i 指向的位置，检查新换来的 0 是否还需要继续处理。",
            must_mention=["原地", "交换后", "状态推进"],
            misconception="交换后直接推进指针，忽略当前位置的新状态还没稳定。",
            expected_student_move="用 0 0 1 1 0 跟踪 i、j 和数组变化。",
        ),
    ]


def variant_code(template: Template, variant: int) -> str:
    code = template.bug_code
    extra = f"""

def audit_variant_{variant}(values):
    checksum = 0
    for item in str(values):
        checksum += ord(item) % 7
    return checksum

def keep_complex_shape_{variant}(flag=False):
    if flag:
        return audit_variant_{variant}(flag)
    return 0

def describe_variant_shape_{variant}(size=0):
    label = "wide" if size > 10 else "small"
    return label

def variant_guard_{variant}(items=()):
    total = 0
    for item in str(items):
        total += len(item)
    if total < 0:
        return -1
    return total

def variant_shadow_signal_{variant}(values=()):
    shadows = []
    for index, item in enumerate(str(values)):
        if index % 2 == 0:
            shadows.append(item)
    return "".join(shadows)

def variant_boundary_probe_{variant}(left=0, right=0):
    if left > right:
        left, right = right, left
    width = right - left
    if width == 0:
        return "single"
    if width < 3:
        return "small"
    return "wide"
"""
    if variant % 2 == 1:
        extra += f"""

def variant_note_{variant}():
    return "complex-eval-variant-{variant}"
"""
    if variant >= 10:
        extra += f"""

def long_audit_path_{variant}_a(values=()):
    total = 0
    for index, item in enumerate(str(values)):
        total += (index + ord(item)) % 11
    return total

def long_audit_path_{variant}_b(values=()):
    bucket = {{}}
    for item in str(values):
        bucket[item] = bucket.get(item, 0) + 1
    return sorted(bucket.items())

def long_audit_path_{variant}_c(values=()):
    flags = []
    for index, item in enumerate(str(values)):
        if index % 3 == 0:
            flags.append(item)
        elif index % 3 == 1:
            flags.append(str(len(item)))
        else:
            flags.append("-")
    return "".join(flags)

def long_audit_path_{variant}_d(values=()):
    score = long_audit_path_{variant}_a(values)
    if score < 0:
        return "impossible"
    if score % 2 == 0:
        return "even-shadow"
    return "odd-shadow"

def long_audit_path_{variant}_e(values=()):
    pairs = long_audit_path_{variant}_b(values)
    text = []
    for key, count in pairs:
        if count > 0:
            text.append(str(key) + ":" + str(count))
    return "|".join(text)

def long_audit_path_{variant}_f(values=()):
    marker = long_audit_path_{variant}_d(values)
    digest = long_audit_path_{variant}_e(values)
    if not digest:
        return marker
    return marker + "/" + digest
"""
    return code.replace("\nif __name__ == \"__main__\":\n", extra + "\nif __name__ == \"__main__\":\n")


def semantic_variant(variant: int) -> str:
    parts = ["base-audit"]
    if variant % 2 == 1:
        parts.append("note-branch")
    if variant >= 10:
        parts.append("long-audit")
    return "+".join(parts)


def build_case(template: Template, index: int, variant: int, live_index: int | None) -> dict:
    bug_code = variant_code(template, variant)
    line_count = code_lines(bug_code)
    if line_count < MIN_SOURCE_LINES:
        raise AssertionError(f"{template.slug} variant {variant} has only {line_count} lines")
    first_index, verdict, actual, expected, stderr = first_failed_case(
        Template(**{**template.__dict__, "bug_code": bug_code})
    )
    case_id = f"complex-live-{live_index:02d}-{template.slug}" if live_index is not None else f"complex-static-{index:03d}-{template.slug}"
    problem_id = 900000 + index
    submission_id = 910000 + index
    evidence_root = f"generator:{template.slug}:{template.primary_fine.lower()}"
    first_ref = f"judge:first_failed_case:{first_index}"
    return {
        "caseId": case_id,
        "generatorSpecId": f"complex-generator-v1::{template.slug}::variant-{variant:02d}",
        "teacherExpectation": template.teaching_priority,
        "problem": {
            "id": problem_id,
            "title": f"{template.title} #{variant}",
            "description": template.description,
            "difficulty": "MEDIUM",
            "timeLimit": 1000,
            "memoryLimit": 65536,
            "knowledgePoints": template.knowledge_points,
            "algorithmStrategies": template.algorithm_strategies,
            "commonMistakes": template.common_mistakes,
            "boundaryTypes": template.boundary_types,
        },
        "submission": {
            "id": submission_id,
            "problemId": problem_id,
            "languageId": 71,
            "languageName": "Python 3",
            "verdict": verdict,
            "sourceCode": bug_code.rstrip(),
            "compileOutput": "",
            "errorMessage": stderr,
        },
        "caseResults": [
            {
                "testCaseNumber": first_index,
                "passed": False,
                "hidden": False,
                "inputSnapshot": template.tests[first_index - 1].input_data.rstrip("\n"),
                "actualOutput": actual,
                "expectedOutput": expected,
                "executionTime": 0.03 if verdict != "TIME_LIMIT_EXCEEDED" else 1.2,
                "memoryUsed": 2048 + variant,
            }
        ],
        "baseline": {
            "scenario": verdict,
            "headline": f"{template.primary_fine} 是 first failed case 的主错因",
            "summary": template.teaching_priority,
            "issueTags": [template.primary_issue] + [issue for issue in template.secondary_issues if issue != template.primary_issue][:2],
            "fineGrainedTags": [template.primary_fine],
            "evidenceRefs": [evidence_root, first_ref, "verdict:" + verdict.lower()],
            "studentHint": template.expected_student_move,
            "answerLeakRisk": "LOW",
        },
        "expectedIssueTags": [template.primary_issue] + [issue for issue in template.secondary_issues if issue != template.primary_issue][:2],
        "expectedFineTags": [template.primary_fine],
        "primaryRootCause": {
            "issueTag": template.primary_issue,
            "fineGrainedTag": template.primary_fine,
            "evidenceRef": evidence_root,
            "whyPrimary": template.teaching_priority,
        },
        "secondaryIssues": [
            {"issueTag": issue, "role": "secondary", "whySecondary": "该信号存在，但不能优先解释 first failed case。"}
            for issue in template.secondary_issues
        ],
        "distractingSignals": template.distracting_signals,
        "expectedTeachingPriority": template.teaching_priority,
        "requiredEvidenceRefs": [evidence_root, first_ref],
        "mustMention": template.must_mention,
        "mustNotMention": MUST_NOT_MENTION,
        "quality": {
            "bugPattern": f"complex-{template.slug}",
            "misconception": template.misconception,
            "expectedStudentMove": template.expected_student_move,
            "evalPurpose": "验证模型能在多错因复杂学生提交中优先定位最该先教的根因。",
            "lineCount": line_count,
            "injectedBugCount": 1 + len(template.secondary_issues),
            "semanticVariant": semantic_variant(variant),
            "verifiedByExecution": True,
            "correctSolutionVerified": True,
            "expectedMetrics": QUALITY_METRICS,
            "liveCandidate": live_index is not None,
        },
    }


def main() -> None:
    templates = base_templates()
    for template in templates:
        verify_correct(template)

    cases = []
    seen_sources = set()
    for index in range(1, TOTAL_CASES + 1):
        template = templates[(index - 1) % len(templates)]
        variant = ((index - 1) // len(templates)) + 1
        live_index = index if index <= LIVE_CANDIDATES else None
        case = build_case(template, index, variant, live_index)
        source = case["submission"]["sourceCode"]
        if source in seen_sources:
            raise AssertionError(f"duplicate generated source at {case['caseId']}")
        seen_sources.add(source)
        cases.append(case)

    large_count = sum(1 for case in cases if case["quality"]["lineCount"] >= LARGE_SOURCE_LINES)
    if large_count < MIN_LARGE_CASES:
        raise AssertionError(f"only {large_count} generated cases have at least {LARGE_SOURCE_LINES} lines")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(cases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(cases)} complex fixtures to {OUTPUT}")


if __name__ == "__main__":
    main()

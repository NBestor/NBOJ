package com.onlinejudge.problem.application;

import com.onlinejudge.problem.domain.Problem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PublicProblemSeedCatalog {

    private static final List<PublicProblemSeed> SEEDS = buildSeeds();

    private PublicProblemSeedCatalog() {
    }

    public static List<PublicProblemSeed> seeds() {
        return SEEDS;
    }

    private static List<PublicProblemSeed> buildSeeds() {
        return List.of(
                tidalRoads(),
                tidalDiscountPath(),
                adjacentStoneMerge(),
                treeCoursePlan(),
                dynamicConnectivity(),
                longestRepeatedRoute(),
                slidingWarehouseCost(),
                layeredDiscountPath(),
                parallelAssembly(),
                energyField(),
                subarrayMinimumContribution()
        );
    }

    private static PublicProblemSeed tidalRoads() {
        return problem(
                "潮汐道路最早到达",
                """
                        ## 题目描述

                        有 `n` 个岛屿和 `m` 条单向潮汐道路。第 `i` 条道路从 `u` 到 `v`，行驶时间为 `w`。
                        这条道路只有在出发时刻 `t` 满足 `t mod p = r` 时才能进入；如果到达路口时不满足条件，可以原地等待到下一次可进入时刻。

                        你从 1 号岛屿的时刻 0 出发，求到达 `n` 号岛屿的最早时刻。若不可达，输出 `-1`。

                        ## 输入格式

                        第一行两个整数 `n m`。
                        接下来 `m` 行，每行五个整数 `u v w p r`。

                        ## 输出格式

                        输出一个整数，表示最早到达时刻。

                        ## 约束

                        `1 <= n <= 2 * 10^5`，`1 <= m <= 3 * 10^5`，`1 <= w,p <= 10^9`，`0 <= r < p`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "关注等待时间公式、Dijkstra 的状态弹出条件，以及大权值下的 INF 边界。",
                """
                        import sys
                        import heapq
                        from collections import defaultdict

                        INF = 10 ** 30

                        def parse_input():
                            data = sys.stdin.buffer.read().split()
                            if not data:
                                return 0, []
                            it = iter(data)
                            n = int(next(it))
                            m = int(next(it))
                            graph = defaultdict(list)
                            for _ in range(m):
                                u = int(next(it))
                                v = int(next(it))
                                w = int(next(it))
                                p = int(next(it))
                                r = int(next(it))
                                graph[u].append((v, w, p, r))
                            for node in graph:
                                graph[node].sort(key=lambda item: (item[2], item[3], item[0]))
                            return n, graph

                        def wait_time(current, period, residue):
                            remainder = current % period
                            if remainder <= residue:
                                return residue - remainder
                            return period - remainder + residue

                        def relax_edges(node, current_time, graph, dist, heap):
                            for to, cost, period, residue in graph.get(node, []):
                                wait = wait_time(current_time + cost, period, residue)
                                candidate = current_time + wait + cost
                                if candidate <= dist[to]:
                                    dist[to] = candidate
                                    heapq.heappush(heap, (candidate, to))

                        def earliest_arrival(n, graph):
                            dist = [INF] * (n + 1)
                            used = [False] * (n + 1)
                            heap = [(0, 1)]
                            dist[1] = 0
                            while heap:
                                current_time, node = heapq.heappop(heap)
                                if used[node]:
                                    continue
                                used[node] = True
                                if node == n:
                                    return current_time
                                relax_edges(node, current_time, graph, dist, heap)
                            return -1 if dist[n] == INF else dist[n]

                        def main():
                            n, graph = parse_input()
                            if n == 0:
                                return
                            answer = earliest_arrival(n, graph)
                            print(answer)

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("最短路", "周期等待", "大权值图"),
                List.of("Dijkstra", "时间依赖边"),
                List.of("把到达时间而不是出发时间代入周期", "过早标记 visited", "等候公式边界错误"),
                List.of("同余边界", "不可达图", "大整数 INF"),
                List.of(
                        sample("""
                                4 5
                                1 2 3 5 0
                                2 4 4 3 1
                                1 3 2 4 2
                                3 4 6 5 0
                                2 3 1 2 0
                                """, "8\n"),
                        hidden("""
                                3 1
                                1 2 5 7 3
                                """, "-1\n")
                )
        );
    }

    private static PublicProblemSeed tidalDiscountPath() {
        return problem(
                "潮汐折扣最短路",
                """
                        ## 题目描述

                        有 `n` 个城市和 `m` 条单向潮汐道路。第 `i` 条道路从 `u` 到 `v`，行驶时间为 `w`。
                        这条道路只有在出发时刻 `t` 满足 `t mod p = r` 时才能进入；如果到达路口时不满足条件，可以原地等待到下一次可进入时刻。

                        你从 1 号城市的时刻 0 出发，目标是尽早到达 `n` 号城市。
                        你还有最多 `k` 张折扣券。每张折扣券只能在通过一条道路时使用一次，使这条道路的行驶时间变为 `ceil(w / 2)`；等待时间不能打折。

                        这是一道时间依赖最短路题：同一个城市在不同已用折扣券数量下，可能对应不同的最优状态。

                        求到达 `n` 号城市的最早时刻。若无法到达，输出 `-1`。

                        ## 输入格式

                        第一行三个整数 `n m k`。
                        接下来 `m` 行，每行五个整数 `u v w p r`，表示一条从 `u` 到 `v` 的单向道路。

                        ## 输出格式

                        输出一个整数，表示最早到达时刻；不可达时输出 `-1`。

                        ## 约束

                        `1 <= n <= 2 * 10^5`，`1 <= m <= 3 * 10^5`，`0 <= k <= 20`。
                        `1 <= w,p <= 10^9`，`0 <= r < p`。
                        """,
                Problem.Difficulty.HARD,
                3000,
                262144,
                "这是时间依赖最短路 + 分层状态 Dijkstra。重点检查等待时间公式、单向边、优惠券状态维度、状态支配关系和奇数边权的向上取整。",
                resourceText("/public-problem-seeds/tidal-discount-path-wrong.py"),
                List.of("图论", "最短路", "时间依赖边", "状态分层"),
                List.of("Dijkstra", "分层图", "等待时间取模", "状态支配"),
                List.of(
                        "把单向边误建成双向边",
                        "等待时间取模公式在 rem > r 时多等一轮",
                        "折扣券对奇数边权使用 floor 而不是 ceil",
                        "用单个节点最优时间错误剪掉不同优惠券状态"
                ),
                List.of("不可达", "奇数边权", "rem > r", "较晚到达但保留折扣券更优"),
                List.of(
                        sample("""
                                4 5 1
                                1 2 5 1 0
                                2 4 5 3 1
                                1 3 2 1 0
                                3 4 100 1 0
                                2 3 1 1 0
                                """, "9\n"),
                        hidden("""
                                3 2 0
                                2 1 1 1 0
                                2 3 1 1 0
                                """, "-1\n"),
                        hidden("""
                                4 4 1
                                1 2 1 1 0
                                2 3 1 1 0
                                1 3 5 1 0
                                3 4 100 1 0
                                """, "52\n")
                )
        );
    }

    private static PublicProblemSeed adjacentStoneMerge() {
        return problem(
                "相邻石子合并最小代价",
                """
                        ## 题目描述

                        一排有 `n` 堆石子，第 `i` 堆重量为 `a_i`。每次只能选择相邻的两堆合并，代价为这两堆重量之和，合并后的新堆重量也是两堆重量之和。

                        求把所有石子合并成一堆的最小总代价。

                        ## 输入格式

                        第一行一个整数 `n`。
                        第二行 `n` 个整数 `a_i`。

                        ## 输出格式

                        输出最小总代价。

                        ## 约束

                        `1 <= n <= 500`，`1 <= a_i <= 10^6`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "重点看学生是否误用 Huffman 贪心；本题只能合并相邻区间，需要区间 DP。",
                """
                        import sys
                        import heapq

                        def read_numbers():
                            raw = sys.stdin.buffer.read().split()
                            if not raw:
                                return []
                            return [int(x) for x in raw]

                        def build_active_list(weights):
                            active = []
                            for index, value in enumerate(weights):
                                active.append({
                                    "id": index,
                                    "weight": value,
                                    "left": index - 1,
                                    "right": index + 1,
                                    "alive": True,
                                })
                            if active:
                                active[0]["left"] = -1
                                active[-1]["right"] = -1
                            return active

                        def push_neighbor_pair(heap, active, left):
                            if left == -1:
                                return
                            right = active[left]["right"]
                            if right == -1:
                                return
                            if not active[left]["alive"] or not active[right]["alive"]:
                                return
                            total = active[left]["weight"] + active[right]["weight"]
                            heapq.heappush(heap, (total, left, right))

                        def greedy_adjacent_merge(weights):
                            if len(weights) <= 1:
                                return 0
                            active = build_active_list(weights)
                            heap = []
                            for i in range(len(weights) - 1):
                                push_neighbor_pair(heap, active, i)
                            answer = 0
                            remaining = len(weights)
                            while remaining > 1 and heap:
                                cost, left, right = heapq.heappop(heap)
                                if right != active[left]["right"]:
                                    continue
                                if not active[left]["alive"] or not active[right]["alive"]:
                                    continue
                                answer += cost
                                active[left]["weight"] = cost
                                active[right]["alive"] = False
                                next_node = active[right]["right"]
                                active[left]["right"] = next_node
                                if next_node != -1:
                                    active[next_node]["left"] = left
                                push_neighbor_pair(heap, active, active[left]["left"])
                                push_neighbor_pair(heap, active, left)
                                remaining -= 1
                            return answer

                        def main():
                            data = read_numbers()
                            if not data:
                                return
                            n = data[0]
                            weights = data[1:1 + n]
                            print(greedy_adjacent_merge(weights))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("区间 DP", "相邻合并", "最优子结构"),
                List.of("区间动态规划", "前缀和"),
                List.of("误把相邻合并当 Huffman", "局部最小合并不保证全局最优", "区间枚举顺序错误"),
                List.of("单堆", "权值悬殊", "重复权值"),
                List.of(
                        sample("""
                                4
                                4 1 3 2
                                """, "20\n"),
                        hidden("""
                                5
                                7 2 9 4 1
                                """, "52\n")
                )
        );
    }

    private static PublicProblemSeed treeCoursePlan() {
        return problem(
                "课程树选课收益",
                """
                        ## 题目描述

                        有 `n` 门课程构成一棵以 1 为根的先修树。选择一门课程前，必须选择它到根路径上的所有先修课程。
                        第 `i` 门课的收益是 `value_i`。你必须恰好选择 `k` 门课，求最大总收益。

                        ## 输入格式

                        第一行两个整数 `n k`。
                        第二行 `n` 个整数表示课程收益。
                        接下来 `n-1` 行，每行两个整数 `parent child`。

                        ## 输出格式

                        输出最大总收益。保证至少存在一种选择方案。

                        ## 约束

                        `1 <= k <= n <= 3000`，`-10^6 <= value_i <= 10^6`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "关注树形 DP 的容量合并，必须保留父节点被选中的闭包约束。",
                """
                        import sys
                        from collections import defaultdict

                        NEG = -10 ** 30

                        def parse():
                            data = list(map(int, sys.stdin.buffer.read().split()))
                            if not data:
                                return 0, 0, [], {}
                            n, k = data[0], data[1]
                            values = [0] + data[2:2 + n]
                            children = defaultdict(list)
                            pos = 2 + n
                            for _ in range(n - 1):
                                parent = data[pos]
                                child = data[pos + 1]
                                pos += 2
                                children[parent].append(child)
                            return n, k, values, children

                        def merge(left, right, limit):
                            merged = [NEG] * (min(limit, len(left) + len(right) - 2) + 1)
                            for i in range(len(left)):
                                if left[i] <= NEG // 2:
                                    continue
                                for j in range(len(right)):
                                    if right[j] <= NEG // 2:
                                        continue
                                    if i + j > limit:
                                        break
                                    if i + j >= len(merged):
                                        continue
                                    merged[i + j] = max(merged[i + j], left[i] + right[j])
                            return merged

                        def solve_tree(n, k, values, children):
                            sys.setrecursionlimit(max(1000000, n * 4 + 10))
                            size = [0] * (n + 1)

                            def dfs(node):
                                current = [NEG, values[node]]
                                size[node] = 1
                                for child in children.get(node, []):
                                    child_dp = dfs(child)
                                    size[node] += size[child]
                                    current = merge(current, child_dp, k)
                                if len(current) <= k:
                                    current.extend([NEG] * (k + 1 - len(current)))
                                current[0] = 0
                                return current[:k + 1]

                            dp = dfs(1)
                            return dp[k]

                        def main():
                            n, k, values, children = parse()
                            if n == 0:
                                return
                            print(solve_tree(n, k, values, children))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("树形 DP", "依赖闭包", "背包合并"),
                List.of("DFS 后序", "分组背包"),
                List.of("允许选子节点但没选父节点", "把 dp[0] 重置破坏闭包", "负收益初始化错误"),
                List.of("负收益课程", "k 等于 1", "链式依赖"),
                List.of(
                        sample("""
                                5 3
                                3 4 2 10 1
                                1 2
                                1 3
                                2 4
                                2 5
                                """, "17\n"),
                        hidden("""
                                3 2
                                -5 10 9
                                1 2
                                1 3
                                """, "5\n")
                )
        );
    }

    private static PublicProblemSeed dynamicConnectivity() {
        return problem(
                "可撤销道路连通性",
                """
                        ## 题目描述

                        有 `n` 个城市，初始没有道路。接下来有 `q` 个操作：

                        - `+ u v`：加入一条道路。
                        - `- u v`：删除一条当前存在的道路。
                        - `? u v`：询问当前 `u` 与 `v` 是否连通。

                        同一时刻同一条无向道路最多存在一条。对每个询问输出 `YES` 或 `NO`。

                        ## 输入格式

                        第一行两个整数 `n q`。
                        接下来 `q` 行，每行一个操作。

                        ## 输出格式

                        对每个 `?` 操作输出一行。

                        ## 约束

                        `1 <= n <= 2 * 10^5`，`1 <= q <= 2 * 10^5`。
                        """,
                Problem.Difficulty.HARD,
                3000,
                262144,
                "这是动态连通性问题，删除边不能用普通并查集在线处理；关注离线区间、线段树分治和 rollback。",
                """
                        import sys

                        class DSU:
                            def __init__(self, n):
                                self.parent = list(range(n + 1))
                                self.size = [1] * (n + 1)
                                self.components = n

                            def find(self, x):
                                while self.parent[x] != x:
                                    self.parent[x] = self.parent[self.parent[x]]
                                    x = self.parent[x]
                                return x

                            def union(self, a, b):
                                ra = self.find(a)
                                rb = self.find(b)
                                if ra == rb:
                                    return False
                                if self.size[ra] < self.size[rb]:
                                    ra, rb = rb, ra
                                self.parent[rb] = ra
                                self.size[ra] += self.size[rb]
                                self.components -= 1
                                return True

                            def connected(self, a, b):
                                return self.find(a) == self.find(b)

                        def normalize_edge(a, b):
                            if a > b:
                                a, b = b, a
                            return a, b

                        def parse():
                            lines = sys.stdin.buffer.read().splitlines()
                            if not lines:
                                return 0, []
                            n, q = map(int, lines[0].split())
                            operations = []
                            for line in lines[1:1 + q]:
                                parts = line.split()
                                op = parts[0].decode()
                                u = int(parts[1])
                                v = int(parts[2])
                                operations.append((op, u, v))
                            return n, operations

                        def answer_queries(n, operations):
                            dsu = DSU(n)
                            active = set()
                            output = []
                            for op, u, v in operations:
                                edge = normalize_edge(u, v)
                                if op == "+":
                                    active.add(edge)
                                    dsu.union(u, v)
                                elif op == "-":
                                    active.discard(edge)
                                else:
                                    output.append("YES" if dsu.connected(u, v) else "NO")
                            return output

                        def main():
                            n, operations = parse()
                            if n == 0:
                                return
                            print("\\n".join(answer_queries(n, operations)))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("动态连通性", "并查集", "离线算法"),
                List.of("线段树分治", "Rollback DSU"),
                List.of("普通并查集无法删除边", "路径压缩破坏回滚", "边存在区间闭开端点错误"),
                List.of("重复加删", "删除后再询问", "孤立点"),
                List.of(
                        sample("""
                                4 8
                                + 1 2
                                + 2 3
                                ? 1 3
                                - 2 3
                                ? 1 3
                                + 3 4
                                + 2 4
                                ? 1 3
                                """, """
                                YES
                                NO
                                YES
                                """),
                        hidden("""
                                3 5
                                + 1 2
                                ? 1 3
                                - 1 2
                                + 2 3
                                ? 1 3
                                """, """
                                NO
                                NO
                                """)
                )
        );
    }

    private static PublicProblemSeed longestRepeatedRoute() {
        return problem(
                "最长重复路线片段",
                """
                        ## 题目描述

                        给定一个只包含小写字母的字符串 `s`，表示一条路线的节点类型序列。求出现至少两次的最长连续片段长度。
                        两次出现可以重叠。

                        ## 输入格式

                        第一行一个字符串 `s`。

                        ## 输出格式

                        输出一个整数，表示最长重复子串长度。

                        ## 约束

                        `1 <= |s| <= 2 * 10^5`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "可用后缀数组/后缀自动机/二分哈希；关注双哈希或确定性结构，以及窗口下标。",
                """
                        import sys

                        MOD = 1_000_000_007
                        BASE = 911382323

                        def read_string():
                            return sys.stdin.readline().strip()

                        def build_prefix(values):
                            n = len(values)
                            prefix = [0] * (n + 1)
                            power = [1] * (n + 1)
                            for i, value in enumerate(values, 1):
                                prefix[i] = (prefix[i - 1] * BASE + value) % MOD
                                power[i] = (power[i - 1] * BASE) % MOD
                            return prefix, power

                        def get_hash(prefix, power, left, right):
                            value = prefix[right] - prefix[left] * power[right - left]
                            value %= MOD
                            return value

                        def has_duplicate(length, prefix, power, n):
                            if length == 0:
                                return True
                            seen = {}
                            for start in range(0, n - length):
                                end = start + length
                                token = get_hash(prefix, power, start, end)
                                if token in seen:
                                    return True
                                seen[token] = start
                            return False

                        def longest_repeated(s):
                            values = [ord(ch) - 96 for ch in s]
                            prefix, power = build_prefix(values)
                            n = len(values)
                            low, high = 0, n
                            best = 0
                            while low <= high:
                                mid = (low + high) // 2
                                if has_duplicate(mid, prefix, power, n):
                                    best = mid
                                    low = mid + 1
                                else:
                                    high = mid - 1
                            return best

                        def main():
                            s = read_string()
                            if not s:
                                return
                            print(longest_repeated(s))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("字符串算法", "滚动哈希", "后缀结构"),
                List.of("二分答案", "后缀数组", "后缀自动机"),
                List.of("枚举窗口少一个", "单哈希碰撞风险", "二分边界更新错误"),
                List.of("全相同字符", "无重复字符", "重叠重复"),
                List.of(
                        sample("banana\n", "3\n"),
                        hidden("abcdef\n", "0\n")
                )
        );
    }

    private static PublicProblemSeed slidingWarehouseCost() {
        return problem(
                "仓库滑窗调平代价",
                """
                        ## 题目描述

                        有 `n` 个连续仓库，第 `i` 个仓库有 `a_i` 件货物。对每个长度为 `k` 的连续窗口，求把窗口内所有仓库调整到同一数量所需的最小搬运代价。

                        一次搬运可以让某个仓库数量增加或减少 1，代价为 1。对每个窗口输出最小代价。

                        ## 输入格式

                        第一行两个整数 `n k`。
                        第二行 `n` 个整数 `a_i`。

                        ## 输出格式

                        输出 `n-k+1` 个整数。

                        ## 约束

                        `1 <= k <= n <= 2 * 10^5`，`0 <= a_i <= 10^9`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "最优目标值是中位数；关注双堆/有序集合维护、延迟删除和重复值。",
                """
                        import sys
                        import heapq

                        def parse():
                            data = list(map(int, sys.stdin.buffer.read().split()))
                            if not data:
                                return 0, 0, []
                            n, k = data[0], data[1]
                            arr = data[2:2 + n]
                            return n, k, arr

                        def rebalance(left, right):
                            while len(left) > len(right) + 1:
                                value = -heapq.heappop(left)
                                heapq.heappush(right, value)
                            while len(right) > len(left):
                                value = heapq.heappop(right)
                                heapq.heappush(left, -value)

                        def add_value(left, right, value):
                            if not left or value <= -left[0]:
                                heapq.heappush(left, -value)
                            else:
                                heapq.heappush(right, value)
                            rebalance(left, right)

                        def remove_value(left, right, value):
                            try:
                                if left and value <= -left[0]:
                                    left.remove(-value)
                                    heapq.heapify(left)
                                else:
                                    right.remove(value)
                                    heapq.heapify(right)
                            except ValueError:
                                return
                            rebalance(left, right)

                        def current_cost(left, right):
                            if not left:
                                return 0
                            merged = [-x for x in left] + list(right)
                            average = sum(merged) // len(merged)
                            return sum(abs(x - average) for x in merged)

                        def solve(n, k, arr):
                            left, right = [], []
                            answer = []
                            for i, value in enumerate(arr):
                                add_value(left, right, value)
                                if i >= k:
                                    remove_value(left, right, arr[i - k])
                                if i + 1 >= k:
                                    answer.append(str(current_cost(left, right)))
                            return answer

                        def main():
                            n, k, arr = parse()
                            if n == 0:
                                return
                            print(" ".join(solve(n, k, arr)))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("滑动窗口", "中位数", "堆"),
                List.of("双堆", "延迟删除", "有序多重集合"),
                List.of("用平均数代替中位数", "直接 remove 导致复杂度退化", "重复值删除错堆"),
                List.of("偶数窗口", "重复值", "极大数"),
                List.of(
                        sample("""
                                5 3
                                1 3 2 6 4
                                """, "2 4 4\n"),
                        hidden("""
                                4 2
                                10 1 10 1
                                """, "9 9 9\n")
                )
        );
    }

    private static PublicProblemSeed layeredDiscountPath() {
        return problem(
                "分层优惠最短路",
                """
                        ## 题目描述

                        有一张无向带权图。你从 1 号点到 `n` 号点，最多可以使用 `k` 张优惠券。
                        每张优惠券可以让某一条边的费用变为 `floor(w / 2)`，每条边最多使用一张优惠券。

                        求最小总费用。

                        ## 输入格式

                        第一行三个整数 `n m k`。
                        接下来 `m` 行，每行三个整数 `u v w`。

                        ## 输出格式

                        输出一个整数。

                        ## 约束

                        `1 <= n <= 10^5`，`1 <= m <= 2 * 10^5`，`0 <= k <= 20`，`1 <= w <= 10^9`。
                        """,
                Problem.Difficulty.HARD,
                2500,
                262144,
                "需要把优惠券数量作为状态层；普通单点 visited 会丢掉未来可用券数不同的路径。",
                """
                        import sys
                        import heapq
                        from collections import defaultdict

                        INF = 10 ** 30

                        def parse():
                            data = list(map(int, sys.stdin.buffer.read().split()))
                            if not data:
                                return 0, 0, 0, {}
                            n, m, k = data[0], data[1], data[2]
                            graph = defaultdict(list)
                            pos = 3
                            for _ in range(m):
                                u, v, w = data[pos], data[pos + 1], data[pos + 2]
                                pos += 3
                                graph[u].append((v, w))
                                graph[v].append((u, w))
                            return n, m, k, graph

                        def shortest(n, k, graph):
                            dist = [[INF] * (k + 1) for _ in range(n + 1)]
                            best_node = [INF] * (n + 1)
                            heap = [(0, 1, 0)]
                            dist[1][0] = 0
                            best_node[1] = 0
                            visited = [False] * (n + 1)
                            while heap:
                                cost, node, used = heapq.heappop(heap)
                                if visited[node]:
                                    continue
                                visited[node] = True
                                if node == n:
                                    return cost
                                for to, weight in graph.get(node, []):
                                    direct = cost + weight
                                    if direct < dist[to][used]:
                                        dist[to][used] = direct
                                        if direct < best_node[to]:
                                            best_node[to] = direct
                                        heapq.heappush(heap, (direct, to, used))
                                    if used < k:
                                        discounted = cost + weight // 2
                                        if discounted < dist[to][used + 1]:
                                            dist[to][used + 1] = discounted
                                            if discounted < best_node[to]:
                                                best_node[to] = discounted
                                            heapq.heappush(heap, (discounted, to, used + 1))
                            return min(dist[n])

                        def main():
                            n, _, k, graph = parse()
                            if n == 0:
                                return
                            print(shortest(n, k, graph))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("分层图", "最短路", "资源约束"),
                List.of("Dijkstra", "状态扩展"),
                List.of("按节点 visited 而不是按状态 visited", "提前返回非最优层", "优惠券状态转移漏边"),
                List.of("k 为 0", "大权边优惠", "不同券数到达同一点"),
                List.of(
                        sample("""
                                3 3 1
                                1 2 5
                                2 3 5
                                1 3 100
                                """, "7\n"),
                        hidden("""
                                2 1 0
                                1 2 9
                                """, "9\n")
                )
        );
    }

    private static PublicProblemSeed parallelAssembly() {
        return problem(
                "双工位装配最短完成时间",
                """
                        ## 题目描述

                        有 `n` 个装配任务，每个任务耗时 `t_i`，并且有若干先后依赖关系。每一时刻最多同时进行两个已经满足依赖的任务。

                        求完成所有任务的最短时间。任务一旦开始不能中断。

                        ## 输入格式

                        第一行两个整数 `n m`。
                        第二行 `n` 个整数 `t_i`。
                        接下来 `m` 行，每行两个整数 `a b`，表示任务 `a` 必须在任务 `b` 前完成。

                        ## 输出格式

                        输出最短完成时间。

                        ## 约束

                        `1 <= n <= 16`，`0 <= m <= n(n-1)/2`，`1 <= t_i <= 100`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "这是小规模状态压缩调度，不是普通拓扑贪心；关注并行机、依赖闭包和状态转移。",
                """
                        import sys
                        import heapq

                        def parse():
                            data = list(map(int, sys.stdin.buffer.read().split()))
                            if not data:
                                return 0, 0, [], []
                            n, m = data[0], data[1]
                            durations = data[2:2 + n]
                            edges = []
                            pos = 2 + n
                            for _ in range(m):
                                a, b = data[pos] - 1, data[pos + 1] - 1
                                pos += 2
                                edges.append((a, b))
                            return n, m, durations, edges

                        def greedy_schedule(n, durations, edges):
                            prereq = [0] * n
                            children = [[] for _ in range(n)]
                            for a, b in edges:
                                prereq[b] |= 1 << a
                                children[a].append(b)
                            done = 0
                            time = 0
                            running = []
                            available = []
                            for task in range(n):
                                if prereq[task] == 0:
                                    heapq.heappush(available, (-durations[task], task))
                            while done != (1 << n) - 1:
                                while available and len(running) < 2:
                                    _, task = heapq.heappop(available)
                                    if done & (1 << task):
                                        continue
                                    heapq.heappush(running, (time + durations[task], task))
                                if not running:
                                    break
                                finish, task = heapq.heappop(running)
                                time = finish
                                done |= 1 << task
                                for child in children[task]:
                                    if prereq[child] & ~done == 0:
                                        heapq.heappush(available, (-durations[child], child))
                            return time

                        def main():
                            n, _, durations, edges = parse()
                            if n == 0:
                                return
                            print(greedy_schedule(n, durations, edges))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("状态压缩 DP", "拓扑依赖", "调度"),
                List.of("最短路状态搜索", "子集 DP"),
                List.of("用最长任务优先贪心替代全局搜索", "未处理同时完成释放多个任务", "状态缺少机器占用时间"),
                List.of("无依赖", "链式依赖", "同时完成"),
                List.of(
                        sample("""
                                3 1
                                3 2 4
                                1 3
                                """, "7\n"),
                        hidden("""
                                2 0
                                5 8
                                """, "8\n")
                )
        );
    }

    private static PublicProblemSeed energyField() {
        return problem(
                "矩形能量场统计",
                """
                        ## 题目描述

                        有一个 `n * m` 的网格，初始能量全为 0。给定 `q` 次矩形加法操作，每次把左上角 `(x1,y1)` 到右下角 `(x2,y2)` 的所有格子加上 `v`。

                        所有操作完成后，统计能量值大于等于 `T` 的格子数量。

                        ## 输入格式

                        第一行四个整数 `n m q T`。
                        接下来 `q` 行，每行五个整数 `x1 y1 x2 y2 v`。

                        ## 输出格式

                        输出一个整数。

                        ## 约束

                        `1 <= n,m <= 2000`，`1 <= q <= 2 * 10^5`，`0 <= T,v <= 10^9`。
                        """,
                Problem.Difficulty.HARD,
                2500,
                262144,
                "标准二维差分要特别注意 x2+1/y2+1 边界；高 q 下不能逐格更新矩形。",
                """
                        import sys

                        def parse():
                            data = list(map(int, sys.stdin.buffer.read().split()))
                            if not data:
                                return 0, 0, 0, 0, []
                            n, m, q, threshold = data[:4]
                            ops = []
                            pos = 4
                            for _ in range(q):
                                x1, y1, x2, y2, value = data[pos:pos + 5]
                                pos += 5
                                ops.append((x1, y1, x2, y2, value))
                            return n, m, q, threshold, ops

                        def apply_operations(n, m, ops):
                            diff = [[0] * (m + 2) for _ in range(n + 2)]
                            for x1, y1, x2, y2, value in ops:
                                diff[x1][y1] += value
                                if x2 < n:
                                    diff[x2][y1] -= value
                                if y2 < m:
                                    diff[x1][y2] -= value
                                if x2 < n and y2 < m:
                                    diff[x2][y2] += value
                            return diff

                        def count_cells(n, m, threshold, diff):
                            answer = 0
                            for i in range(1, n + 1):
                                row_prefix = 0
                                for j in range(1, m + 1):
                                    row_prefix += diff[i][j]
                                    diff[i][j] = diff[i - 1][j] + row_prefix
                                    if diff[i][j] >= threshold:
                                        answer += 1
                            return answer

                        def debug_projection(n, m, diff):
                            top = []
                            for i in range(1, min(n, 3) + 1):
                                top.append(diff[i][1:min(m, 3) + 1])
                            return top

                        def solve(n, m, threshold, ops):
                            diff = apply_operations(n, m, ops)
                            _ = debug_projection(n, m, diff)
                            return count_cells(n, m, threshold, diff)

                        def main():
                            n, m, _, threshold, ops = parse()
                            if n == 0:
                                return
                            print(solve(n, m, threshold, ops))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("二维差分", "前缀和", "网格统计"),
                List.of("差分矩阵", "离线批量更新"),
                List.of("把闭区间边界写成 x2/y2 而非 x2+1/y2+1", "二维前缀顺序错误", "阈值为 0 时漏计"),
                List.of("贴边矩形", "单行单列", "阈值 0"),
                List.of(
                        sample("""
                                3 4 3 2
                                1 1 2 2 1
                                2 2 3 4 2
                                1 4 3 4 1
                                """, "7\n"),
                        hidden("""
                                1 3 1 0
                                1 1 1 2 5
                                """, "3\n")
                )
        );
    }

    private static PublicProblemSeed subarrayMinimumContribution() {
        return problem(
                "子数组最小值贡献和",
                """
                        ## 题目描述

                        给定一个长度为 `n` 的数组 `a`，求所有非空连续子数组的最小值之和。答案对 `1_000_000_007` 取模。

                        ## 输入格式

                        第一行一个整数 `n`。
                        第二行 `n` 个整数 `a_i`。

                        ## 输出格式

                        输出一个整数。

                        ## 约束

                        `1 <= n <= 2 * 10^5`，`0 <= a_i <= 10^9`。
                        """,
                Problem.Difficulty.HARD,
                2000,
                262144,
                "贡献法要用单调栈计算左右控制范围；重复值一侧严格一侧非严格，避免重复或漏算。",
                """
                        import sys

                        MOD = 1_000_000_007

                        def parse():
                            data = list(map(int, sys.stdin.buffer.read().split()))
                            if not data:
                                return 0, []
                            n = data[0]
                            arr = data[1:1 + n]
                            return n, arr

                        def previous_less(arr):
                            n = len(arr)
                            prev = [-1] * n
                            stack = []
                            for i, value in enumerate(arr):
                                while stack and arr[stack[-1]] >= value:
                                    stack.pop()
                                if stack:
                                    prev[i] = stack[-1]
                                stack.append(i)
                            return prev

                        def next_less(arr):
                            n = len(arr)
                            nxt = [n] * n
                            stack = []
                            for i in range(n - 1, -1, -1):
                                value = arr[i]
                                while stack and arr[stack[-1]] >= value:
                                    stack.pop()
                                if stack:
                                    nxt[i] = stack[-1]
                                stack.append(i)
                            return nxt

                        def contribution_sum(arr):
                            prev = previous_less(arr)
                            nxt = next_less(arr)
                            total = 0
                            for i, value in enumerate(arr):
                                left = i - prev[i]
                                right = nxt[i] - i
                                add = (value % MOD) * left * right
                                total = (total + add) % MOD
                            return total

                        def slow_check(arr):
                            total = 0
                            for i in range(min(len(arr), 20)):
                                current = 10 ** 30
                                for j in range(i, min(len(arr), 20)):
                                    current = min(current, arr[j])
                                    total += current
                            return total

                        def main():
                            n, arr = parse()
                            if n == 0:
                                return
                            _ = slow_check(arr[:8])
                            print(contribution_sum(arr))

                        if __name__ == "__main__":
                            main()
                        """,
                List.of("单调栈", "贡献法", "重复值处理"),
                List.of("左右边界", "取模"),
                List.of("重复值两侧都用严格比较", "贡献范围重复计算", "大数取模时机错误"),
                List.of("全相等", "含 0", "严格递增"),
                List.of(
                        sample("""
                                4
                                3 1 2 4
                                """, "17\n"),
                        hidden("""
                                2
                                2 2
                                """, "6\n")
                )
        );
    }

    private static PublicProblemSeed problem(String title,
                                             String description,
                                             Problem.Difficulty difficulty,
                                             int timeLimit,
                                             int memoryLimit,
                                             String aiPromptDirection,
                                             String starterCode,
                                             List<String> knowledgePoints,
                                             List<String> algorithmStrategies,
                                             List<String> commonMistakes,
                                             List<String> boundaryTypes,
                                             List<PublicProblemSeed.TestCaseSeed> testCases) {
        return new PublicProblemSeed(
                title,
                description.stripTrailing() + "\n",
                difficulty,
                timeLimit,
                memoryLimit,
                aiPromptDirection,
                starterCode.stripTrailing() + "\n",
                knowledgePoints,
                algorithmStrategies,
                commonMistakes,
                boundaryTypes,
                testCases
        );
    }

    private static PublicProblemSeed.TestCaseSeed sample(String input, String expectedOutput) {
        return new PublicProblemSeed.TestCaseSeed(input.stripIndent(), expectedOutput.stripIndent(), false);
    }

    private static PublicProblemSeed.TestCaseSeed hidden(String input, String expectedOutput) {
        return new PublicProblemSeed.TestCaseSeed(input.stripIndent(), expectedOutput.stripIndent(), true);
    }

    private static String resourceText(String path) {
        try (InputStream input = PublicProblemSeedCatalog.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing public problem seed resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

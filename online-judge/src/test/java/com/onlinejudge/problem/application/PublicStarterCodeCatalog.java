package com.onlinejudge.problem.application;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PublicStarterCodeCatalog {

    private static final String COURSE_TREE_DIAGNOSTIC_STARTER = """
            import sys


            def solve():
                data = list(map(int, sys.stdin.read().split()))
                if not data:
                    return
                n, k = data[0], data[1]
                values = [0] + data[2:2 + n]
                edges = data[2 + n:]

                children = [[] for _ in range(n + 1)]
                for i in range(0, len(edges), 2):
                    parent, child = edges[i], edges[i + 1]
                    children[parent].append(child)

                # 这里想先挑收益最高的课程，但没有把“选择子课程必须先选父课程”这个闭包条件带进去。
                candidates = sorted(values[1:], reverse=True)
                print(sum(candidates[:k]))


            if __name__ == "__main__":
                solve()
            """;

    private static final String WAREHOUSE_WINDOW_DIAGNOSTIC_STARTER = """
            import sys


            def solve():
                data = list(map(int, sys.stdin.read().split()))
                if not data:
                    return
                n, k = data[0], data[1]
                heights = data[2:2 + n]

                best = None
                for left in range(0, n - k + 1):
                    window = heights[left:left + k]
                    target = sum(window) // k
                    cost = sum(abs(value - target) for value in window)
                    if best is None or cost < best:
                        best = cost

                print(best if best is not None else 0)


            if __name__ == "__main__":
                solve()
            """;

    private static final String LAYERED_DISCOUNT_PATH_DIAGNOSTIC_STARTER = """
            import heapq
            import sys


            def solve():
                data = list(map(int, sys.stdin.read().split()))
                if not data:
                    return
                n, m, k = data[0], data[1], data[2]
                graph = [[] for _ in range(n + 1)]
                index = 3
                for _ in range(m):
                    a, b, w = data[index], data[index + 1], data[index + 2]
                    index += 3
                    graph[a].append((b, w))
                    graph[b].append((a, w))

                dist = [[10 ** 18] * (k + 1) for _ in range(n + 1)]
                dist[1][0] = 0
                heap = [(0, 1, 0)]

                while heap:
                    cost, node, used = heapq.heappop(heap)
                    if cost != dist[node][used]:
                        continue
                    for nxt, weight in graph[node]:
                        normal = cost + weight
                        if normal < dist[nxt][used]:
                            dist[nxt][used] = normal
                            heapq.heappush(heap, (normal, nxt, used))

                        if used < k:
                            # 状态层已经建出来了，但这里忘了真正把边权打折。
                            discounted = cost + weight
                            if discounted < dist[nxt][used + 1]:
                                dist[nxt][used + 1] = discounted
                                heapq.heappush(heap, (discounted, nxt, used + 1))

                answer = min(dist[n])
                print(-1 if answer >= 10 ** 18 else answer)


            if __name__ == "__main__":
                solve()
            """;

    private static final String ASSEMBLY_TWO_STATION_DIAGNOSTIC_STARTER = """
            import heapq
            import sys


            def solve():
                data = list(map(int, sys.stdin.read().split()))
                if not data:
                    return
                n, m = data[0], data[1]
                durations = [0] + data[2:2 + n]
                index = 2 + n

                children = [[] for _ in range(n + 1)]
                indegree = [0] * (n + 1)
                for _ in range(m):
                    a, b = data[index], data[index + 1]
                    index += 2
                    children[a].append(b)
                    indegree[b] += 1

                available = []
                for task in range(1, n + 1):
                    if indegree[task] == 0:
                        heapq.heappush(available, task)

                running = []
                time = 0
                finished = 0

                while available or running:
                    while available and len(running) < 1:
                        task = heapq.heappop(available)
                        heapq.heappush(running, (time + durations[task], task))

                    time, task = heapq.heappop(running)
                    finished += 1
                    for nxt in children[task]:
                        indegree[nxt] -= 1
                        if indegree[nxt] == 0:
                            heapq.heappush(available, nxt)

                print(time if finished == n else -1)


            if __name__ == "__main__":
                solve()
            """;

    private static final String ONE_TO_N_SUM_STARTER = """
            def sum_to_n(n):
                total = 0
                for i in range(1, n):
                    total += i
                return total

            def main():
                n = int(input())
                print(sum_to_n(n))

            if __name__ == "__main__":
                main()
            """;

    private PublicStarterCodeCatalog() {
    }

    public static String findByTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String starterCode = starterCodesByTitle().get(title);
        if (starterCode != null) {
            return starterCode;
        }
        if (title.contains("1 到 n 求和")) {
            return ONE_TO_N_SUM_STARTER;
        }
        return null;
    }

    public static Map<String, String> starterCodesByTitle() {
        Map<String, String> starterCodes = new LinkedHashMap<>();
        PublicProblemSeedCatalog.seeds().forEach(seed -> starterCodes.put(seed.title(), seed.starterCode()));
        starterCodes.put("两数求和", """
                a, b = map(int, input().split())
                answer = a
                print(answer)
                """);
        starterCodes.put("回文判断", """
                s = input().strip()

                reversed_s = ""
                for i in range(len(s) - 1, 0, -1):
                    reversed_s += s[i]

                if s == reversed_s:
                    print("YES")
                else:
                    print("NO")
                """);
        starterCodes.put("FizzBuzz", """
                n = int(input())

                if n % 3 == 0:
                    print("Fizz")
                elif n % 5 == 0:
                    print("Buzz")
                elif n % 15 == 0:
                    print("FizzBuzz")
                else:
                    print(n)
                """);
        starterCodes.put("阶乘计算", """
                n = int(input())

                ans = 1
                for i in range(1, n):
                    ans *= i

                print(ans)
                """);
        starterCodes.put("质数判断", """
                n = int(input())

                is_prime = True
                for i in range(2, n):
                    if n % i == 0:
                        is_prime = False

                print("YES" if is_prime else "NO")
                """);
        starterCodes.put("AI闭环测试：1 到 n 求和", ONE_TO_N_SUM_STARTER);
        starterCodes.put("AI闭环复测：1 到 n 求和", ONE_TO_N_SUM_STARTER);
        starterCodes.put("AI闭环终测：1 到 n 求和", ONE_TO_N_SUM_STARTER);
        starterCodes.put("课程树选课收益", COURSE_TREE_DIAGNOSTIC_STARTER);
        starterCodes.put("仓库滑窗调平代价", WAREHOUSE_WINDOW_DIAGNOSTIC_STARTER);
        starterCodes.put("分层优惠最短路", LAYERED_DISCOUNT_PATH_DIAGNOSTIC_STARTER);
        starterCodes.put("双工位装配最短完成时间", ASSEMBLY_TWO_STATION_DIAGNOSTIC_STARTER);
        return Map.copyOf(starterCodes);
    }
}

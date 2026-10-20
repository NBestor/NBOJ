import sys
from dataclasses import dataclass
from typing import Iterable, List, Tuple


@dataclass
class TreeCase:
    n: int
    weights: List[int]
    edges: List[Tuple[int, int]]


@dataclass
class Contribution:
    node: int
    parent: int
    subtree_size: int
    subtree_weight: int
    distance_sum: int


class TokenReader:
    def __init__(self) -> None:
        self.data = list(map(int, sys.stdin.buffer.read().split()))
        self.pos = 0

    def has_data(self) -> bool:
        return self.pos < len(self.data)

    def next_int(self) -> int:
        if self.pos >= len(self.data):
            raise ValueError("unexpected end of input")
        value = self.data[self.pos]
        self.pos += 1
        return value


class Tree:
    def __init__(self, case: TreeCase) -> None:
        self.n = case.n
        self.weights = [0] + case.weights
        self.graph: List[List[int]] = [[] for _ in range(self.n + 1)]
        for a, b in case.edges:
            self.graph[a].append(b)
            self.graph[b].append(a)

    def neighbors(self, node: int) -> Iterable[int]:
        return self.graph[node]

    def degree(self, node: int) -> int:
        return len(self.graph[node])

    def total_weight(self) -> int:
        return sum(self.weights)


class RootedTreeStats:
    def __init__(self, tree: Tree) -> None:
        self.tree = tree
        self.parent = [0] * (tree.n + 1)
        self.depth = [0] * (tree.n + 1)
        self.subtree_size = [0] * (tree.n + 1)
        self.subtree_weight = [0] * (tree.n + 1)
        self.down_cost = [0] * (tree.n + 1)
        self.order: List[int] = []
        self.trace: List[Contribution] = []

    def build_order(self, root: int = 1) -> None:
        stack = [root]
        self.parent[root] = -1
        while stack:
            node = stack.pop()
            self.order.append(node)
            for nxt in self.tree.neighbors(node):
                if nxt == self.parent[node]:
                    continue
                self.parent[nxt] = node
                self.depth[nxt] = self.depth[node] + 1
                stack.append(nxt)

    def accumulate(self) -> None:
        for node in reversed(self.order):
            self.subtree_size[node] = 1
            self.subtree_weight[node] = self.tree.weights[node]
            self.down_cost[node] = 0
            for nxt in self.tree.neighbors(node):
                if nxt == self.parent[node]:
                    continue
                self.subtree_size[node] += self.subtree_size[nxt]
                self.subtree_weight[node] += self.subtree_weight[nxt]
                self.down_cost[node] += self.down_cost[nxt] + self.subtree_size[nxt]
            self.trace.append(Contribution(
                node,
                self.parent[node],
                self.subtree_size[node],
                self.subtree_weight[node],
                self.down_cost[node],
            ))

    def prepare(self) -> None:
        self.build_order(1)
        self.accumulate()


class RerootSolver:
    def __init__(self, tree: Tree) -> None:
        self.tree = tree
        self.stats = RootedTreeStats(tree)
        self.answer = [0] * (tree.n + 1)
        self.total_weight = tree.total_weight()

    def run(self) -> List[int]:
        self.stats.prepare()
        self.answer[1] = self.stats.down_cost[1]
        self.propagate(1, -1)
        return self.answer[1:]

    def propagate(self, node: int, parent: int) -> None:
        for nxt in self.tree.neighbors(node):
            if nxt == parent:
                continue
            child_weight = self.stats.subtree_weight[nxt]
            outside_weight = self.total_weight - child_weight
            self.answer[nxt] = (
                self.answer[node]
                - self.stats.subtree_size[nxt]
                - outside_weight
            )
            self.propagate(nxt, node)


class BruteChecker:
    def __init__(self, tree: Tree) -> None:
        self.tree = tree

    def distances_from(self, start: int) -> List[int]:
        dist = [-1] * (self.tree.n + 1)
        dist[start] = 0
        queue = [start]
        head = 0
        while head < len(queue):
            node = queue[head]
            head += 1
            for nxt in self.tree.neighbors(node):
                if dist[nxt] != -1:
                    continue
                dist[nxt] = dist[node] + 1
                queue.append(nxt)
        return dist

    def weighted_sum(self, start: int) -> int:
        dist = self.distances_from(start)
        total = 0
        for node in range(1, self.tree.n + 1):
            total += dist[node] * self.tree.weights[node]
        return total

    def small_case_answers(self) -> List[int]:
        return [self.weighted_sum(node) for node in range(1, self.tree.n + 1)]


class TreeValidator:
    def __init__(self, case: TreeCase) -> None:
        self.case = case

    def validate(self) -> None:
        if self.case.n <= 0:
            raise ValueError("n must be positive")
        if len(self.case.weights) != self.case.n:
            raise ValueError("weight count mismatch")
        if len(self.case.edges) != self.case.n - 1:
            raise ValueError("tree must have n-1 edges")
        for a, b in self.case.edges:
            if not (1 <= a <= self.case.n and 1 <= b <= self.case.n):
                raise ValueError("edge endpoint out of range")

    def connected(self) -> bool:
        graph = [[] for _ in range(self.case.n + 1)]
        for a, b in self.case.edges:
            graph[a].append(b)
            graph[b].append(a)
        seen = [False] * (self.case.n + 1)
        stack = [1]
        seen[1] = True
        while stack:
            node = stack.pop()
            for nxt in graph[node]:
                if not seen[nxt]:
                    seen[nxt] = True
                    stack.append(nxt)
        return all(seen[1:])


def read_case(reader: TokenReader) -> TreeCase:
    n = reader.next_int()
    weights = [reader.next_int() for _ in range(n)]
    edges = []
    for _ in range(n - 1):
        a = reader.next_int()
        b = reader.next_int()
        edges.append((a, b))
    return TreeCase(n, weights, edges)


def solve(case: TreeCase) -> List[int]:
    validator = TreeValidator(case)
    validator.validate()
    tree = Tree(case)
    if case.n <= 8 and not validator.connected():
        return BruteChecker(tree).small_case_answers()
    return RerootSolver(tree).run()


def format_answers(values: List[int]) -> str:
    return " ".join(map(str, values))


def main() -> None:
    reader = TokenReader()
    if not reader.has_data():
        return
    case = read_case(reader)
    print(format_answers(solve(case)))


if __name__ == "__main__":
    main()

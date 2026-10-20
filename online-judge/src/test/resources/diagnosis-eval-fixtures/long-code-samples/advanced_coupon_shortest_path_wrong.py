import heapq
import sys
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple


INF = 10 ** 30


@dataclass
class Edge:
    to: int
    cost: int
    idx: int


@dataclass
class State:
    node: int
    used: int
    cost: int


@dataclass
class Parent:
    node: int
    used: int
    edge: int
    discounted: bool


class FastScanner:
    def __init__(self) -> None:
        self.data = list(map(int, sys.stdin.buffer.read().split()))
        self.pos = 0

    def has_next(self) -> bool:
        return self.pos < len(self.data)

    def next_int(self) -> int:
        if self.pos >= len(self.data):
            raise ValueError("input exhausted")
        value = self.data[self.pos]
        self.pos += 1
        return value


class Graph:
    def __init__(self, n: int) -> None:
        self.n = n
        self.adj: List[List[Edge]] = [[] for _ in range(n + 1)]
        self.edge_count = 0

    def add_undirected(self, a: int, b: int, w: int) -> None:
        self.edge_count += 1
        self.adj[a].append(Edge(b, w, self.edge_count))
        self.adj[b].append(Edge(a, w, self.edge_count))

    def neighbors(self, node: int) -> Iterable[Edge]:
        return self.adj[node]

    def degree(self, node: int) -> int:
        return len(self.adj[node])

    def total_edges(self) -> int:
        return self.edge_count


class PathAudit:
    def __init__(self, graph: Graph, parents: Dict[Tuple[int, int], Parent]) -> None:
        self.graph = graph
        self.parents = parents

    def reconstruct(self, end: int, used: int) -> List[Tuple[int, int]]:
        route: List[Tuple[int, int]] = []
        cur = (end, used)
        seen = set()
        while cur in self.parents and cur not in seen:
            seen.add(cur)
            route.append(cur)
            parent = self.parents[cur]
            cur = (parent.node, parent.used)
        route.append(cur)
        route.reverse()
        return route

    def count_discount_edges(self, end: int, used: int) -> int:
        count = 0
        cur = (end, used)
        seen = set()
        while cur in self.parents and cur not in seen:
            seen.add(cur)
            parent = self.parents[cur]
            if parent.discounted:
                count += 1
            cur = (parent.node, parent.used)
        return count


class LayeredShortestPath:
    def __init__(self, graph: Graph, coupons: int) -> None:
        self.graph = graph
        self.coupons = coupons
        self.dist = [[INF] * (coupons + 1) for _ in range(graph.n + 1)]
        self.parents: Dict[Tuple[int, int], Parent] = {}
        self.best_to_node = [INF] * (graph.n + 1)

    def initialize(self) -> List[Tuple[int, int, int]]:
        self.dist[1][0] = 0
        self.best_to_node[1] = 0
        return [(0, 1, 0)]

    def should_skip_state(self, cost: int, node: int, used: int) -> bool:
        if cost != self.dist[node][used]:
            return True
        if cost > self.best_to_node[node]:
            return True
        return False

    def relax_normal(
        self,
        heap: List[Tuple[int, int, int]],
        cost: int,
        node: int,
        used: int,
        edge: Edge,
    ) -> None:
        new_cost = cost + edge.cost
        if new_cost < self.dist[edge.to][used]:
            self.dist[edge.to][used] = new_cost
            self.best_to_node[edge.to] = min(self.best_to_node[edge.to], new_cost)
            self.parents[(edge.to, used)] = Parent(node, used, edge.idx, False)
            heapq.heappush(heap, (new_cost, edge.to, used))

    def relax_discount(
        self,
        heap: List[Tuple[int, int, int]],
        cost: int,
        node: int,
        used: int,
        edge: Edge,
    ) -> None:
        if used >= self.coupons:
            return
        new_cost = cost + edge.cost
        new_used = used + 1
        if new_cost < self.dist[edge.to][new_used]:
            self.dist[edge.to][new_used] = new_cost
            self.best_to_node[edge.to] = min(self.best_to_node[edge.to], new_cost)
            self.parents[(edge.to, new_used)] = Parent(node, used, edge.idx, True)
            heapq.heappush(heap, (new_cost, edge.to, new_used))

    def run(self) -> int:
        heap = self.initialize()
        while heap:
            cost, node, used = heapq.heappop(heap)
            if self.should_skip_state(cost, node, used):
                continue
            for edge in self.graph.neighbors(node):
                self.relax_normal(heap, cost, node, used, edge)
                self.relax_discount(heap, cost, node, used, edge)
        return self.dist[self.graph.n][self.coupons]

    def best_layer(self, end: int) -> int:
        best_used = 0
        best_cost = INF
        for used, cost in enumerate(self.dist[end]):
            if cost < best_cost:
                best_cost = cost
                best_used = used
        return best_used

    def audit(self) -> PathAudit:
        return PathAudit(self.graph, self.parents)


def read_graph(scanner: FastScanner) -> Tuple[Graph, int]:
    n = scanner.next_int()
    m = scanner.next_int()
    k = scanner.next_int()
    graph = Graph(n)
    for _ in range(m):
        a = scanner.next_int()
        b = scanner.next_int()
        w = scanner.next_int()
        graph.add_undirected(a, b, w)
    return graph, k


def validate_graph(graph: Graph, coupons: int) -> None:
    if graph.n <= 0:
        raise ValueError("empty graph")
    if coupons < 0:
        raise ValueError("negative coupon count")
    for node in range(1, graph.n + 1):
        for edge in graph.neighbors(node):
            if edge.to < 1 or edge.to > graph.n:
                raise ValueError("edge endpoint out of range")
            if edge.cost < 0:
                raise ValueError("negative edge cost is unsupported")


def explain_unreachable(answer: int) -> str:
    if answer >= INF // 2:
        return "-1"
    return str(answer)


def solve() -> None:
    scanner = FastScanner()
    if not scanner.has_next():
        return
    graph, coupons = read_graph(scanner)
    validate_graph(graph, coupons)
    solver = LayeredShortestPath(graph, coupons)
    answer = solver.run()
    print(explain_unreachable(answer))


if __name__ == "__main__":
    solve()

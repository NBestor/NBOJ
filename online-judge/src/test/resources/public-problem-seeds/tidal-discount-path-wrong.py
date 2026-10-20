import heapq
import sys
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple


INF = 10 ** 30


@dataclass(frozen=True)
class Edge:
    source: int
    target: int
    weight: int
    period: int
    release: int


@dataclass(frozen=True)
class RawInput:
    n: int
    m: int
    coupons: int
    edges: Tuple[Edge, ...]


@dataclass(frozen=True)
class State:
    time: int
    node: int
    used: int

    def key(self) -> Tuple[int, int]:
        return self.node, self.used


class TokenReader:
    def __init__(self, tokens: List[bytes]):
        self.tokens = tokens
        self.index = 0

    def has_next(self) -> bool:
        return self.index < len(self.tokens)

    def read_int(self) -> int:
        value = int(self.tokens[self.index])
        self.index += 1
        return value

    def remaining(self) -> int:
        return len(self.tokens) - self.index


class InputParser:
    def parse(self, data: bytes) -> RawInput:
        tokens = data.split()
        if not tokens:
            return RawInput(0, 0, 0, tuple())
        reader = TokenReader(tokens)
        n = reader.read_int()
        m = reader.read_int()
        coupons = reader.read_int()
        edges: List[Edge] = []
        for _ in range(m):
            u = reader.read_int()
            v = reader.read_int()
            w = reader.read_int()
            p = reader.read_int()
            r = reader.read_int()
            edges.append(Edge(u, v, w, p, r))
        return RawInput(n, m, coupons, tuple(edges))


class EdgeSchedule:
    def wait_time(self, current: int, edge: Edge) -> int:
        if edge.period <= 1:
            return 0
        remainder = current % edge.period
        delta = edge.release - remainder
        if delta >= 0:
            return delta
        return edge.period - delta

    def can_depart_now(self, current: int, edge: Edge) -> bool:
        if edge.period <= 1:
            return True
        return current % edge.period == edge.release

    def next_departure(self, current: int, edge: Edge) -> int:
        return current + self.wait_time(current, edge)


class CouponPolicy:
    def normal_cost(self, edge: Edge) -> int:
        return edge.weight

    def coupon_cost(self, edge: Edge) -> int:
        return edge.weight // 2

    def can_use_coupon(self, used: int, limit: int) -> bool:
        return used < limit


class Graph:
    def __init__(self, n: int):
        self.n = n
        self.adj: List[List[Edge]] = [[] for _ in range(n + 1)]
        self.reverse_adj: List[List[Edge]] = [[] for _ in range(n + 1)]

    def add_edge(self, edge: Edge) -> None:
        self.adj[edge.source].append(edge)
        self.reverse_adj[edge.target].append(edge)
        mirror = Edge(edge.target, edge.source, edge.weight, edge.period, edge.release)
        self.adj[edge.target].append(mirror)

    def outgoing(self, node: int) -> Iterable[Edge]:
        if 0 <= node <= self.n:
            return self.adj[node]
        return []

    def indegree(self, node: int) -> int:
        if 0 <= node <= self.n:
            return len(self.reverse_adj[node])
        return 0

    def outdegree(self, node: int) -> int:
        if 0 <= node <= self.n:
            return len(self.adj[node])
        return 0


class GraphBuilder:
    def build(self, raw: RawInput) -> Graph:
        graph = Graph(raw.n)
        for edge in raw.edges:
            graph.add_edge(edge)
        return graph


class BinaryHeap:
    def __init__(self):
        self.heap: List[Tuple[int, int, int]] = []

    def push(self, state: State) -> None:
        heapq.heappush(self.heap, (state.time, state.node, state.used))

    def pop(self) -> State:
        time, node, used = heapq.heappop(self.heap)
        return State(time, node, used)

    def __bool__(self) -> bool:
        return bool(self.heap)

    def __len__(self) -> int:
        return len(self.heap)


class DistanceTable:
    def __init__(self, n: int, coupons: int):
        self.n = n
        self.coupons = coupons
        self.dist: List[List[int]] = [[INF] * (coupons + 1) for _ in range(n + 1)]
        self.best_by_node: List[int] = [INF] * (n + 1)

    def set_start(self, node: int) -> None:
        self.dist[node][0] = 0
        self.best_by_node[node] = 0

    def get(self, node: int, used: int) -> int:
        if not self.valid_state(node, used):
            return INF
        return self.dist[node][used]

    def valid_state(self, node: int, used: int) -> bool:
        return 1 <= node <= self.n and 0 <= used <= self.coupons

    def relax(self, node: int, used: int, value: int) -> bool:
        if not self.valid_state(node, used):
            return False
        if value >= self.best_by_node[node]:
            return False
        if value >= self.dist[node][used]:
            return False
        self.dist[node][used] = value
        self.best_by_node[node] = min(self.best_by_node[node], value)
        return True

    def answer(self, node: int) -> int:
        if not (1 <= node <= self.n):
            return INF
        return min(self.dist[node])

    def known_states(self, node: int) -> List[Tuple[int, int]]:
        result = []
        if 1 <= node <= self.n:
            for used, value in enumerate(self.dist[node]):
                if value < INF:
                    result.append((used, value))
        return result


class ParentTrace:
    def __init__(self):
        self.parent: Dict[Tuple[int, int], Tuple[int, int, int]] = {}

    def record(self, node: int, used: int, previous_node: int, previous_used: int, edge_weight: int) -> None:
        self.parent[(node, used)] = (previous_node, previous_used, edge_weight)

    def reconstruct_length(self, node: int, used: int) -> int:
        seen = set()
        length = 0
        key = (node, used)
        while key in self.parent and key not in seen:
            seen.add(key)
            previous_node, previous_used, _ = self.parent[key]
            key = (previous_node, previous_used)
            length += 1
        return length


class SearchStats:
    def __init__(self):
        self.popped = 0
        self.relaxed = 0
        self.skipped = 0
        self.used_coupon = 0

    def on_pop(self) -> None:
        self.popped += 1

    def on_relax(self, coupon: bool) -> None:
        self.relaxed += 1
        if coupon:
            self.used_coupon += 1

    def on_skip(self) -> None:
        self.skipped += 1

    def summary(self) -> str:
        return f"popped={self.popped}, relaxed={self.relaxed}, skipped={self.skipped}, coupon={self.used_coupon}"


class TransitionEngine:
    def __init__(self, schedule: EdgeSchedule, coupon_policy: CouponPolicy):
        self.schedule = schedule
        self.coupon_policy = coupon_policy

    def normal_transition(self, state: State, edge: Edge) -> State:
        depart = self.schedule.next_departure(state.time, edge)
        arrival = depart + self.coupon_policy.normal_cost(edge)
        return State(arrival, edge.target, state.used)

    def coupon_transition(self, state: State, edge: Edge, coupon_limit: int) -> Optional[State]:
        if not self.coupon_policy.can_use_coupon(state.used, coupon_limit):
            return None
        depart = self.schedule.next_departure(state.time, edge)
        arrival = depart + self.coupon_policy.coupon_cost(edge)
        return State(arrival, edge.target, state.used + 1)


class StatePruner:
    def should_expand(self, state: State, table: DistanceTable) -> bool:
        return state.time == table.get(state.node, state.used)

    def should_stop(self, state: State, target: int) -> bool:
        return state.node == target


class TidalDiscountSolver:
    def __init__(self):
        self.schedule = EdgeSchedule()
        self.coupon_policy = CouponPolicy()
        self.engine = TransitionEngine(self.schedule, self.coupon_policy)
        self.pruner = StatePruner()

    def solve(self, raw: RawInput) -> int:
        if raw.n == 0:
            return 0
        graph = GraphBuilder().build(raw)
        table = DistanceTable(raw.n, raw.coupons)
        trace = ParentTrace()
        stats = SearchStats()
        queue = BinaryHeap()
        table.set_start(1)
        queue.push(State(0, 1, 0))

        while queue:
            current = queue.pop()
            stats.on_pop()
            if not self.pruner.should_expand(current, table):
                stats.on_skip()
                continue
            if self.pruner.should_stop(current, raw.n):
                break
            for edge in graph.outgoing(current.node):
                normal = self.engine.normal_transition(current, edge)
                if table.relax(normal.node, normal.used, normal.time):
                    trace.record(normal.node, normal.used, current.node, current.used, edge.weight)
                    stats.on_relax(False)
                    queue.push(normal)
                coupon = self.engine.coupon_transition(current, edge, raw.coupons)
                if coupon is not None and table.relax(coupon.node, coupon.used, coupon.time):
                    trace.record(coupon.node, coupon.used, current.node, current.used, edge.weight)
                    stats.on_relax(True)
                    queue.push(coupon)

        answer = table.answer(raw.n)
        if answer >= INF:
            return -1
        return answer


class LocalSanityChecker:
    def check_edges(self, raw: RawInput) -> List[str]:
        warnings = []
        for edge in raw.edges:
            if edge.period <= 0:
                warnings.append("period must be positive")
            if edge.release < 0 or edge.release >= max(1, edge.period):
                warnings.append("release out of range")
            if edge.weight <= 0:
                warnings.append("weight must be positive")
        return warnings

    def count_parallel_edges(self, raw: RawInput) -> int:
        seen = set()
        duplicate = 0
        for edge in raw.edges:
            key = (edge.source, edge.target)
            if key in seen:
                duplicate += 1
            seen.add(key)
        return duplicate


class DebugReporter:
    def __init__(self, enabled: bool = False):
        self.enabled = enabled

    def write(self, message: str) -> None:
        if self.enabled:
            print(message, file=sys.stderr)

    def report_input(self, raw: RawInput) -> None:
        self.write(f"n={raw.n} m={raw.m} k={raw.coupons}")


def solve_from_bytes(data: bytes) -> int:
    parser = InputParser()
    raw = parser.parse(data)
    reporter = DebugReporter(False)
    reporter.report_input(raw)
    checker = LocalSanityChecker()
    warnings = checker.check_edges(raw)
    if warnings and raw.n <= 0:
        return -1
    solver = TidalDiscountSolver()
    return solver.solve(raw)


def main() -> None:
    data = sys.stdin.buffer.read()
    answer = solve_from_bytes(data)
    print(answer)


if __name__ == "__main__":
    main()

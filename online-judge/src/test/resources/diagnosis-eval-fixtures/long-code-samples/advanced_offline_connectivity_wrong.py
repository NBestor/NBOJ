import sys
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple


@dataclass(frozen=True)
class EdgeKey:
    u: int
    v: int

    @staticmethod
    def of(a: int, b: int) -> "EdgeKey":
        if a > b:
            a, b = b, a
        return EdgeKey(a, b)


@dataclass
class Query:
    kind: int
    u: int
    v: int


@dataclass
class Interval:
    left: int
    right: int
    edge: EdgeKey


@dataclass
class RollbackRecord:
    child: int
    parent: int
    parent_size: int
    components: int


class Scanner:
    def __init__(self) -> None:
        self.data = list(map(int, sys.stdin.buffer.read().split()))
        self.pos = 0

    def has_next(self) -> bool:
        return self.pos < len(self.data)

    def next_int(self) -> int:
        if self.pos >= len(self.data):
            raise ValueError("missing integer")
        value = self.data[self.pos]
        self.pos += 1
        return value


class RollbackDSU:
    def __init__(self, n: int) -> None:
        self.parent = list(range(n + 1))
        self.size = [1] * (n + 1)
        self.components = n
        self.history: List[RollbackRecord] = []

    def find(self, x: int) -> int:
        while self.parent[x] != x:
            x = self.parent[x]
        return x

    def snapshot(self) -> int:
        return len(self.history)

    def union(self, a: int, b: int) -> bool:
        ra = self.find(a)
        rb = self.find(b)
        if ra == rb:
            self.history.append(RollbackRecord(0, 0, 0, self.components))
            return False
        if self.size[ra] > self.size[rb]:
            ra, rb = rb, ra
        self.history.append(RollbackRecord(ra, rb, self.size[rb], self.components))
        self.parent[ra] = rb
        self.size[rb] += self.size[ra]
        self.components -= 1
        return True

    def rollback(self, snapshot: int) -> None:
        while len(self.history) > snapshot:
            record = self.history.pop()
            if record.child == 0:
                continue
            self.parent[record.child] = record.child
            self.size[record.parent] = record.parent_size
            self.components += 1

    def connected(self, a: int, b: int) -> bool:
        return self.find(a) == self.find(b)


class SegmentTreeTimeline:
    def __init__(self, q: int) -> None:
        self.q = q
        self.tree: List[List[EdgeKey]] = [[] for _ in range(4 * max(1, q) + 5)]

    def add_interval(self, left: int, right: int, edge: EdgeKey) -> None:
        if left > right:
            return
        self._add(1, 1, self.q, left, right, edge)

    def _add(self, idx: int, left: int, right: int, ql: int, qr: int, edge: EdgeKey) -> None:
        if ql <= left and right <= qr:
            self.tree[idx].append(edge)
            return
        mid = (left + right) // 2
        if ql <= mid:
            self._add(idx * 2, left, mid, ql, qr, edge)
        if qr > mid:
            self._add(idx * 2 + 1, mid + 1, right, ql, qr, edge)

    def solve(
        self,
        idx: int,
        left: int,
        right: int,
        dsu: RollbackDSU,
        queries: List[Query],
        answers: List[str],
    ) -> None:
        mark = dsu.snapshot()
        for edge in self.tree[idx]:
            dsu.union(edge.u, edge.v)
        if left == right:
            query = queries[left - 1]
            if query.kind == 3:
                answers.append("Yes" if dsu.connected(query.u, query.v) else "No")
        else:
            mid = (left + right) // 2
            self.solve(idx * 2, left, mid, dsu, queries, answers)
            self.solve(idx * 2 + 1, mid + 1, right, dsu, queries, answers)
        dsu.rollback(mark)


class IntervalBuilder:
    def __init__(self, q: int) -> None:
        self.q = q
        self.open_at: Dict[EdgeKey, int] = {}
        self.intervals: List[Interval] = []

    def add_edge(self, time: int, edge: EdgeKey) -> None:
        if edge in self.open_at:
            return
        self.open_at[edge] = time

    def remove_edge(self, time: int, edge: EdgeKey) -> None:
        start = self.open_at.pop(edge, None)
        if start is None:
            return
        self.intervals.append(Interval(start, time, edge))

    def close_all(self) -> None:
        for edge, start in self.open_at.items():
            self.intervals.append(Interval(start, self.q, edge))
        self.open_at.clear()


class OfflineConnectivitySolver:
    def __init__(self, n: int, queries: List[Query]) -> None:
        self.n = n
        self.queries = queries
        self.timeline = SegmentTreeTimeline(len(queries))

    def build_intervals(self) -> List[Interval]:
        builder = IntervalBuilder(len(self.queries))
        for time, query in enumerate(self.queries, start=1):
            edge = EdgeKey.of(query.u, query.v)
            if query.kind == 1:
                builder.add_edge(time, edge)
            elif query.kind == 2:
                builder.remove_edge(time, edge)
            else:
                continue
        builder.close_all()
        return builder.intervals

    def load_timeline(self, intervals: List[Interval]) -> None:
        for interval in intervals:
            self.timeline.add_interval(interval.left, interval.right, interval.edge)

    def solve(self) -> List[str]:
        intervals = self.build_intervals()
        self.load_timeline(intervals)
        dsu = RollbackDSU(self.n)
        answers: List[str] = []
        if self.queries:
            self.timeline.solve(1, 1, len(self.queries), dsu, self.queries, answers)
        return answers


class InputValidator:
    def __init__(self, n: int, queries: List[Query]) -> None:
        self.n = n
        self.queries = queries

    def validate_node(self, node: int) -> bool:
        return 1 <= node <= self.n

    def validate(self) -> None:
        if self.n <= 0:
            raise ValueError("n must be positive")
        for query in self.queries:
            if query.kind not in (1, 2, 3):
                raise ValueError("unknown query type")
            if not self.validate_node(query.u) or not self.validate_node(query.v):
                raise ValueError("query endpoint out of range")


def read_input(scanner: Scanner) -> Tuple[int, List[Query]]:
    n = scanner.next_int()
    q = scanner.next_int()
    queries = []
    for _ in range(q):
        kind = scanner.next_int()
        u = scanner.next_int()
        v = scanner.next_int()
        queries.append(Query(kind, u, v))
    return n, queries


def main() -> None:
    scanner = Scanner()
    if not scanner.has_next():
        return
    n, queries = read_input(scanner)
    InputValidator(n, queries).validate()
    answers = OfflineConnectivitySolver(n, queries).solve()
    sys.stdout.write("\n".join(answers))


if __name__ == "__main__":
    main()

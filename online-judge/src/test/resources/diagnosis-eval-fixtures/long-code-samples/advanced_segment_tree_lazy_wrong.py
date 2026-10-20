import sys
from dataclasses import dataclass
from typing import List, Optional, Tuple


NEG_INF = -10 ** 30


@dataclass
class Operation:
    kind: int
    left: int
    right: int
    value: int = 0


@dataclass
class NodeSnapshot:
    index: int
    left: int
    right: int
    maximum: int
    lazy: int


class Reader:
    def __init__(self) -> None:
        self.data = list(map(int, sys.stdin.buffer.read().split()))
        self.pos = 0

    def has_input(self) -> bool:
        return self.pos < len(self.data)

    def next_int(self) -> int:
        if self.pos >= len(self.data):
            raise ValueError("input ended")
        value = self.data[self.pos]
        self.pos += 1
        return value


class SegmentTree:
    def __init__(self, values: List[int]) -> None:
        self.n = len(values)
        self.max_value = [0] * (4 * self.n + 5)
        self.lazy = [0] * (4 * self.n + 5)
        self.values = values[:]
        if self.n:
            self.build(1, 0, self.n - 1)

    def build(self, idx: int, left: int, right: int) -> None:
        if left == right:
            self.max_value[idx] = self.values[left]
            return
        mid = (left + right) // 2
        self.build(idx * 2, left, mid)
        self.build(idx * 2 + 1, mid + 1, right)
        self.pull(idx)

    def pull(self, idx: int) -> None:
        self.max_value[idx] = max(self.max_value[idx * 2], self.max_value[idx * 2 + 1])

    def apply_add(self, idx: int, delta: int) -> None:
        self.max_value[idx] += delta
        self.lazy[idx] = delta

    def push(self, idx: int) -> None:
        tag = self.lazy[idx]
        if tag == 0:
            return
        self.apply_add(idx * 2, tag)
        self.apply_add(idx * 2 + 1, tag)
        self.lazy[idx] = 0

    def range_add(self, idx: int, left: int, right: int, ql: int, qr: int, delta: int) -> None:
        if ql <= left and right <= qr:
            self.apply_add(idx, delta)
            return
        self.push(idx)
        mid = (left + right) // 2
        if ql <= mid:
            self.range_add(idx * 2, left, mid, ql, qr, delta)
        if qr > mid:
            self.range_add(idx * 2 + 1, mid + 1, right, ql, qr, delta)
        self.pull(idx)

    def range_max(self, idx: int, left: int, right: int, ql: int, qr: int) -> int:
        if qr < left or right < ql:
            return 0
        if ql <= left and right <= qr:
            return self.max_value[idx]
        self.push(idx)
        mid = (left + right) // 2
        return max(
            self.range_max(idx * 2, left, mid, ql, qr),
            self.range_max(idx * 2 + 1, mid + 1, right, ql, qr),
        )

    def add(self, left: int, right: int, delta: int) -> None:
        if self.n == 0:
            return
        self.range_add(1, 0, self.n - 1, left, right, delta)

    def query(self, left: int, right: int) -> int:
        if self.n == 0:
            return 0
        return self.range_max(1, 0, self.n - 1, left, right)

    def snapshot(self, limit: int = 20) -> List[NodeSnapshot]:
        result: List[NodeSnapshot] = []
        self._snapshot(1, 0, self.n - 1, result, limit)
        return result

    def _snapshot(
        self,
        idx: int,
        left: int,
        right: int,
        result: List[NodeSnapshot],
        limit: int,
    ) -> None:
        if len(result) >= limit or left > right:
            return
        result.append(NodeSnapshot(idx, left, right, self.max_value[idx], self.lazy[idx]))
        if left == right:
            return
        mid = (left + right) // 2
        self._snapshot(idx * 2, left, mid, result, limit)
        self._snapshot(idx * 2 + 1, mid + 1, right, result, limit)


class CoordinatePolicy:
    def __init__(self, n: int) -> None:
        self.n = n

    def normalize(self, left: int, right: int) -> Tuple[int, int]:
        # Input is 1-based inclusive. The student tried to support both styles.
        left -= 1
        right = min(right, self.n - 1)
        return left, right

    def clamp(self, left: int, right: int) -> Tuple[int, int]:
        return max(0, left), min(self.n - 1, right)


class OperationLog:
    def __init__(self) -> None:
        self.items: List[Operation] = []

    def append(self, op: Operation) -> None:
        self.items.append(op)

    def count_updates(self) -> int:
        return sum(1 for op in self.items if op.kind == 1)

    def count_queries(self) -> int:
        return sum(1 for op in self.items if op.kind == 2)

    def last_query(self) -> Optional[Operation]:
        for op in reversed(self.items):
            if op.kind == 2:
                return op
        return None


class Solver:
    def __init__(self, values: List[int], operations: List[Operation]) -> None:
        self.values = values
        self.operations = operations
        self.tree = SegmentTree(values)
        self.policy = CoordinatePolicy(len(values))
        self.log = OperationLog()
        self.answers: List[int] = []

    def run_update(self, op: Operation) -> None:
        left, right = self.policy.normalize(op.left, op.right)
        left, right = self.policy.clamp(left, right)
        if left <= right:
            self.tree.add(left, right, op.value)

    def run_query(self, op: Operation) -> None:
        left, right = self.policy.normalize(op.left, op.right)
        left, right = self.policy.clamp(left, right)
        if left > right:
            self.answers.append(0)
            return
        self.answers.append(self.tree.query(left, right))

    def run(self) -> List[int]:
        for op in self.operations:
            self.log.append(op)
            if op.kind == 1:
                self.run_update(op)
            elif op.kind == 2:
                self.run_query(op)
            else:
                # Unknown operations are ignored instead of failing fast.
                continue
        return self.answers


def read_problem(reader: Reader) -> Tuple[List[int], List[Operation]]:
    n = reader.next_int()
    q = reader.next_int()
    values = [reader.next_int() for _ in range(n)]
    operations: List[Operation] = []
    for _ in range(q):
        kind = reader.next_int()
        left = reader.next_int()
        right = reader.next_int()
        if kind == 1:
            value = reader.next_int()
            operations.append(Operation(kind, left, right, value))
        else:
            operations.append(Operation(kind, left, right, 0))
    return values, operations


def format_answers(values: List[int]) -> str:
    return "\n".join(map(str, values))


def main() -> None:
    reader = Reader()
    if not reader.has_input():
        return
    values, operations = read_problem(reader)
    answers = Solver(values, operations).run()
    if answers:
        print(format_answers(answers))


if __name__ == "__main__":
    main()

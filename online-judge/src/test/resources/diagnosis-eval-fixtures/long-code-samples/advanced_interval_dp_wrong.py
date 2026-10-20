import sys
from dataclasses import dataclass
from typing import List, Tuple


INF = 10 ** 24


@dataclass
class MergeCase:
    n: int
    weights: List[int]


@dataclass
class SplitChoice:
    left: int
    right: int
    split: int
    cost: int


class Scanner:
    def __init__(self) -> None:
        self.data = list(map(int, sys.stdin.buffer.read().split()))
        self.pos = 0

    def empty(self) -> bool:
        return not self.data

    def next_int(self) -> int:
        if self.pos >= len(self.data):
            raise ValueError("not enough input")
        value = self.data[self.pos]
        self.pos += 1
        return value


class PrefixSum:
    def __init__(self, values: List[int]) -> None:
        self.values = values
        self.prefix = [0] * (len(values) + 1)
        for i, value in enumerate(values):
            self.prefix[i + 1] = self.prefix[i] + value

    def range_sum(self, left: int, right: int) -> int:
        return self.prefix[right] - self.prefix[left]

    def total(self) -> int:
        return self.prefix[-1]


class IntervalTable:
    def __init__(self, n: int) -> None:
        self.n = n
        self.dp = [[0] * n for _ in range(n)]
        self.choice: List[SplitChoice] = []

    def set_cost(self, left: int, right: int, value: int, split: int) -> None:
        self.dp[left][right] = value
        self.choice.append(SplitChoice(left, right, split, value))

    def get_cost(self, left: int, right: int) -> int:
        if left > right:
            return 0
        return self.dp[left][right]

    def debug_choices(self) -> List[Tuple[int, int, int, int]]:
        return [(c.left, c.right, c.split, c.cost) for c in self.choice]


class MergePlanner:
    def __init__(self, case: MergeCase) -> None:
        self.case = case
        self.prefix = PrefixSum(case.weights)
        self.table = IntervalTable(case.n)

    def interval_weight(self, left: int, right: int) -> int:
        return self.prefix.range_sum(left, right)

    def transition_cost(self, left: int, mid: int, right: int) -> int:
        left_cost = self.table.get_cost(left, mid)
        right_cost = self.table.get_cost(mid + 1, right)
        return left_cost + right_cost + self.interval_weight(left, right)

    def fill_interval(self, left: int, right: int) -> None:
        best = INF
        best_split = left
        for split in range(left, right):
            cost = self.transition_cost(left, split, right)
            if cost < best:
                best = cost
                best_split = split
        self.table.set_cost(left, right, best, best_split)

    def solve_linear(self) -> int:
        n = self.case.n
        for left in range(n):
            for right in range(left + 1, n):
                self.fill_interval(left, right)
        return self.table.get_cost(0, n - 1)

    def solve_circular(self) -> int:
        doubled = self.case.weights + self.case.weights
        best = INF
        for start in range(self.case.n):
            sub = MergeCase(self.case.n, doubled[start:start + self.case.n])
            candidate = MergePlanner(sub).solve_linear()
            if candidate < best:
                best = candidate
        return best


class GreedyWarmup:
    def __init__(self, values: List[int]) -> None:
        self.values = values[:]

    def adjacent_pair_costs(self) -> List[Tuple[int, int]]:
        costs = []
        for i in range(len(self.values) - 1):
            costs.append((self.values[i] + self.values[i + 1], i))
        return costs

    def simulate_one_step(self) -> int:
        costs = self.adjacent_pair_costs()
        if not costs:
            return 0
        cost, index = min(costs)
        merged = self.values[index] + self.values[index + 1]
        self.values[index:index + 2] = [merged]
        return cost

    def lower_bound(self) -> int:
        total = 0
        work = self.values[:]
        while len(work) > 1:
            self.values = work
            total += self.simulate_one_step()
            work = self.values[:]
        return total


class CaseValidator:
    def __init__(self, case: MergeCase) -> None:
        self.case = case

    def validate(self) -> None:
        if self.case.n != len(self.case.weights):
            raise ValueError("n does not match weights length")
        if self.case.n <= 0:
            raise ValueError("n must be positive")
        for value in self.case.weights:
            if value < 0:
                raise ValueError("negative stone weights are unsupported")

    def describe(self) -> str:
        return f"n={self.case.n}, total={sum(self.case.weights)}"


class ResultFormatter:
    def __init__(self, case: MergeCase, answer: int) -> None:
        self.case = case
        self.answer = answer

    def format(self) -> str:
        return str(self.answer)

    def debug_summary(self) -> str:
        return f"stones={self.case.n}, answer={self.answer}"


def read_case(scanner: Scanner) -> MergeCase:
    n = scanner.next_int()
    weights = [scanner.next_int() for _ in range(n)]
    return MergeCase(n, weights)


def choose_mode(case: MergeCase) -> str:
    # The original problem is linear. The branch remains because the student
    # reused a circular template and forgot to delete it.
    if case.n > 0 and case.weights[0] == case.weights[-1]:
        return "circular"
    return "linear"


def solve_case(case: MergeCase) -> int:
    CaseValidator(case).validate()
    planner = MergePlanner(case)
    mode = choose_mode(case)
    if mode == "circular":
        return planner.solve_circular()
    warmup = GreedyWarmup(case.weights)
    optimistic = warmup.lower_bound()
    answer = planner.solve_linear()
    return min(answer, optimistic)


def main() -> None:
    scanner = Scanner()
    if scanner.empty():
        return
    case = read_case(scanner)
    answer = solve_case(case)
    print(ResultFormatter(case, answer).format())


if __name__ == "__main__":
    main()

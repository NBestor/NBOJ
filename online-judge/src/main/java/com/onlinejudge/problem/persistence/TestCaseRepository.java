package com.onlinejudge.problem.persistence;

import com.onlinejudge.problem.domain.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);
    List<TestCase> findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(Long problemId);
    long deleteByProblemId(Long problemId);
}


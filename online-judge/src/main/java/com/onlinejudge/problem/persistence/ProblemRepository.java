package com.onlinejudge.problem.persistence;

import com.onlinejudge.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {
    List<Problem> findAllByOrderByIdAsc();

    @Query("""
            select p.id as id,
                   p.title as title,
                   p.description as description,
                   p.difficulty as difficulty,
                   p.timeLimit as timeLimit,
                   p.memoryLimit as memoryLimit,
                   p.createdAt as createdAt
            from Problem p
            order by p.id asc
            """)
    List<ProblemCatalogProjection> findCatalogItems();

    @Query("""
            select p.title
            from Problem p
            where p.id = :id
            """)
    Optional<String> findTitleById(@Param("id") Long id);
}


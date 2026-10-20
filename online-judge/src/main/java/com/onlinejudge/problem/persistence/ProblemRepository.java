package com.onlinejudge.problem.persistence;

import com.onlinejudge.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            where p.scope = PUBLIC
              and p.versionState = PUBLISHED
              and p.archivedAt is null
            order by p.id asc
            """)
    List<ProblemCatalogProjection> findCatalogItems();

    @Query("""
            select p.title
            from Problem p
            where p.id = :id
            """)
    Optional<String> findTitleById(@Param("id") Long id);

    default List<Problem> findByVersionStateOrderByCreatedAtAsc(Problem.VersionState versionState) {
        return findAllByOrderByIdAsc().stream()
                .filter(problem -> problem.getVersionState() == versionState)
                .toList();
    }
    default List<Problem> findByOwnerTeacherIdOrderByCreatedAtDesc(UUID ownerTeacherId) {
        return findAllByOrderByIdAsc().stream()
                .filter(problem -> ownerTeacherId.equals(problem.getOwnerTeacherId()))
                .sorted(java.util.Comparator.comparing(Problem::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }
    default Optional<Problem> findByIdAndOwnerTeacherId(Long id, UUID ownerTeacherId) {
        return findById(id).filter(problem -> ownerTeacherId.equals(problem.getOwnerTeacherId()));
    }
    default Optional<Problem> findTopBySeriesIdOrderByVersionNoDesc(UUID seriesId) {
        return findAllByOrderByIdAsc().stream()
                .filter(problem -> seriesId.equals(problem.getSeriesId()))
                .max(java.util.Comparator.comparingInt(Problem::getVersionNo));
    }
}

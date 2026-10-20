package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.StudentIdentityMergeRequest;
import com.onlinejudge.classroom.dto.StudentIdentitySplitRequest;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StudentIdentityAuditServiceTest {

    private final FakeClassGroupRepository classGroupRepository = new FakeClassGroupRepository();
    private final FakeStudentProfileRepository studentProfileRepository = new FakeStudentProfileRepository();
    private final StudentIdentityAuditService service = new StudentIdentityAuditService(
            classGroupRepository,
            studentProfileRepository,
            new StudentIdentityService()
    );
    private final StudentIdentityAdminService adminService = new StudentIdentityAdminService(
            classGroupRepository,
            studentProfileRepository,
            new StudentIdentityService(),
            service
    );

    @Test
    void reportsStableLegacyAndDuplicateIdentityGroups() {
        classGroupRepository.items.put(9L, ClassGroup.builder()
                .id(9L)
                .name("高一1班")
                .build());
        studentProfileRepository.items.put(1L, student(1L, "张三", "08", "class:9:08"));
        studentProfileRepository.items.put(2L, student(2L, "张三", "08", "7:9:张三:08"));
        studentProfileRepository.items.put(3L, student(3L, "李四", "", "class:9:李四"));

        var audit = service.auditClass(9L);

        assertThat(audit.getTotalProfiles()).isEqualTo(3);
        assertThat(audit.getStableIdentityCount()).isEqualTo(2);
        assertThat(audit.getManualIdentityCount()).isEqualTo(0);
        assertThat(audit.getLegacyIdentityCount()).isEqualTo(1);
        assertThat(audit.getMissingStudentNoCount()).isEqualTo(1);
        assertThat(audit.getDuplicateGroupCount()).isEqualTo(1);
        assertThat(audit.getDuplicateGroups()).first()
                .satisfies(group -> {
                    assertThat(group.getStableIdentityKey()).isEqualTo("class:9:08");
                    assertThat(group.getStudentProfileIds()).containsExactly(1L, 2L);
                    assertThat(group.getIdentityKeys()).contains("class:9:08", "7:9:张三:08");
                });
    }

    @Test
    void mergeProfilesRekeysSelectedProfilesWithoutDeletingHistory() {
        classGroupRepository.items.put(9L, ClassGroup.builder()
                .id(9L)
                .name("class-a")
                .build());
        studentProfileRepository.items.put(1L, student(1L, "student-a", "08", "class:9:08"));
        studentProfileRepository.items.put(2L, student(2L, "student-a", "08", "7:9:student-a:08"));

        StudentIdentityMergeRequest request = new StudentIdentityMergeRequest();
        request.setStudentProfileIds(List.of(1L, 2L));
        request.setTargetStudentProfileId(1L);

        var audit = adminService.mergeProfiles(9L, request);

        assertThat(studentProfileRepository.items.get(1L).getIdentityKey()).isEqualTo("manual-merge:9:1");
        assertThat(studentProfileRepository.items.get(2L).getIdentityKey()).isEqualTo("manual-merge:9:1");
        assertThat(audit.getManualIdentityCount()).isEqualTo(2);
        assertThat(audit.getDuplicateGroupCount()).isZero();
        assertThat(studentProfileRepository.items).containsKeys(1L, 2L);
    }

    @Test
    void splitProfileGivesProfileIndependentManualIdentity() {
        classGroupRepository.items.put(9L, ClassGroup.builder()
                .id(9L)
                .name("class-a")
                .build());
        studentProfileRepository.items.put(1L, student(1L, "student-a", "08", "class:9:08"));
        studentProfileRepository.items.put(2L, student(2L, "student-a", "08", "class:9:08"));

        StudentIdentitySplitRequest request = new StudentIdentitySplitRequest();
        request.setStudentProfileId(2L);

        var audit = adminService.splitProfile(9L, request);

        assertThat(studentProfileRepository.items.get(1L).getIdentityKey()).isEqualTo("class:9:08");
        assertThat(studentProfileRepository.items.get(2L).getIdentityKey()).isEqualTo("manual-split:9:2");
        assertThat(audit.getManualIdentityCount()).isEqualTo(1);
        assertThat(audit.getDuplicateGroupCount()).isZero();
    }

    private StudentProfile student(Long id, String displayName, String studentNo, String identityKey) {
        return StudentProfile.builder()
                .id(id)
                .classGroupId(9L)
                .displayName(displayName)
                .studentNo(studentNo)
                .identityKey(identityKey)
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .lastSeenAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .build();
    }

    private static class FakeClassGroupRepository extends UnsupportedJpaRepository<ClassGroup, Long> implements ClassGroupRepository {
        private final Map<Long, ClassGroup> items = new LinkedHashMap<>();

        @Override
        public Optional<ClassGroup> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<ClassGroup> findAllByOrderByCreatedAtDesc() {
            return List.copyOf(items.values());
        }

        @Override
        public Optional<ClassGroup> findByNameIgnoreCase(String name) {
            return items.values()
                    .stream()
                    .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(name))
                    .findFirst();
        }
    }

    private static class FakeStudentProfileRepository extends UnsupportedJpaRepository<StudentProfile, Long> implements StudentProfileRepository {
        private final Map<Long, StudentProfile> items = new LinkedHashMap<>();

        @Override
        public Optional<StudentProfile> findByIdentityKey(String identityKey) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getIdentityKey(), identityKey))
                    .findFirst();
        }

        @Override
        public Optional<StudentProfile> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<StudentProfile> findAllById(Iterable<Long> ids) {
            Set<Long> wanted = new LinkedHashSet<>();
            ids.forEach(wanted::add);
            return items.values()
                    .stream()
                    .filter(item -> wanted.contains(item.getId()))
                    .toList();
        }

        @Override
        public <S extends StudentProfile> S save(S entity) {
            items.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public <S extends StudentProfile> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = new ArrayList<>();
            entities.forEach(entity -> {
                items.put(entity.getId(), entity);
                saved.add(entity);
            });
            return saved;
        }

        @Override
        public List<StudentProfile> findByIdentityKeyOrderByCreatedAtDesc(String identityKey) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getIdentityKey(), identityKey))
                    .toList();
        }

        @Override
        public List<StudentProfile> findByIdentityKeyIn(Collection<String> identityKeys) {
            return items.values()
                    .stream()
                    .filter(item -> identityKeys.contains(item.getIdentityKey()))
                    .toList();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(Long classGroupId) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .sorted(Comparator.comparing(StudentProfile::getStudentNo, Comparator.nullsLast(String::compareTo))
                            .thenComparing(StudentProfile::getDisplayName, Comparator.nullsLast(String::compareTo)))
                    .toList();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdAndStudentNoIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String studentNo) {
            return List.of();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdAndDisplayNameIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String displayName) {
            return List.of();
        }
    }

    private abstract static class UnsupportedJpaRepository<T, ID> {
        public List<T> findAll() { throw unsupported(); }
        public List<T> findAllById(Iterable<ID> ids) { throw unsupported(); }
        public <S extends T> S save(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAll(Iterable<S> entities) { throw unsupported(); }
        public Optional<T> findById(ID id) { throw unsupported(); }
        public boolean existsById(ID id) { throw unsupported(); }
        public long count() { throw unsupported(); }
        public void deleteById(ID id) { throw unsupported(); }
        public void delete(T entity) { throw unsupported(); }
        public void deleteAllById(Iterable<? extends ID> ids) { throw unsupported(); }
        public void deleteAll(Iterable<? extends T> entities) { throw unsupported(); }
        public void deleteAll() { throw unsupported(); }
        public void flush() { throw unsupported(); }
        public <S extends T> S saveAndFlush(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) { throw unsupported(); }
        public void deleteAllInBatch(Iterable<T> entities) { throw unsupported(); }
        public void deleteAllByIdInBatch(Iterable<ID> ids) { throw unsupported(); }
        public void deleteAllInBatch() { throw unsupported(); }
        public T getOne(ID id) { throw unsupported(); }
        public T getById(ID id) { throw unsupported(); }
        public T getReferenceById(ID id) { throw unsupported(); }
        public List<T> findAll(Sort sort) { throw unsupported(); }
        public Page<T> findAll(Pageable pageable) { throw unsupported(); }
        public <S extends T> Optional<S> findOne(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example, Sort sort) { throw unsupported(); }
        public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) { throw unsupported(); }
        public <S extends T> long count(Example<S> example) { throw unsupported(); }
        public <S extends T> boolean exists(Example<S> example) { throw unsupported(); }
        public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw unsupported(); }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used in this test");
        }
    }
}

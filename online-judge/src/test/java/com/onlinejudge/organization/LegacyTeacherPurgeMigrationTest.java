package com.onlinejudge.organization;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyTeacherPurgeMigrationTest {
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @AfterEach
    void clearProperties() {
        System.clearProperty("legacy.teacher.purge.confirmation");
        System.clearProperty("legacy.teacher.backup.proof");
    }

    @Test
    void failsClosedWhenLegacyTeachersExistWithoutBackupProofAndConfirmation() throws Exception {
        DataSource dataSource = newDatabase("purge_blocked");
        migrateToSchoolExpansion(dataSource);
        insertLegacyTeacher(dataSource, UUID.randomUUID());
        assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).load().migrate())
                .isInstanceOf(FlywayException.class)
                .rootCause().hasMessageContaining("Legacy teacher purge blocked");
    }

    @Test
    void purgesTeacherGraphButTransfersPublishedSharedContentAfterExplicitGate() throws Exception {
        DataSource dataSource = newDatabase("purge_confirmed");
        migrateToSchoolExpansion(dataSource);
        UUID teacherId = UUID.randomUUID();
        insertLegacyTeacher(dataSource, teacherId);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO class_groups (owner_teacher_id,name,created_at) VALUES ('" + teacherId + "','旧班',CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO problems (id,owner_teacher_id,scope,version_state,series_id,version_no,title,description,difficulty,time_limit,memory_limit,created_at) VALUES " +
                    "(900001,'" + teacherId + "','PRIVATE','DRAFT','" + UUID.randomUUID() + "',1,'私有题','说明','EASY',1000,128000,CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO problems (id,owner_teacher_id,scope,version_state,series_id,version_no,source_problem_id,title,description,difficulty,time_limit,memory_limit,created_at) VALUES " +
                    "(900002,'" + teacherId + "','SHARED','PUBLISHED','" + UUID.randomUUID() + "',1,900001,'保留题','说明','EASY',1000,128000,CURRENT_TIMESTAMP)");
        }
        System.setProperty("legacy.teacher.purge.confirmation", "DELETE_ALL_LEGACY_TEACHERS");
        System.setProperty("legacy.teacher.backup.proof", "backup://verified/2026-08-15");
        Flyway.configure().dataSource(dataSource).load().migrate();

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT COUNT(*) FROM teacher_accounts WHERE id='" + teacherId + "'" )).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM class_groups WHERE owner_teacher_id='" + teacherId + "'" )).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM problems WHERE title='私有题'" )).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM problems WHERE title='保留题' AND owner_teacher_id='00000000-0000-0000-0000-000000000001'" )).isEqualTo(1);
            assertThat(count(statement, "SELECT COUNT(*) FROM problems WHERE title='保留题' AND source_problem_id IS NULL" )).isEqualTo(1);
            assertThat(count(statement, "SELECT COUNT(*) FROM legacy_teacher_purge_manifests WHERE teacher_count=1 AND preserved_problem_count=1" )).isEqualTo(1);
        }
    }

    private void migrateToSchoolExpansion(DataSource dataSource) {
        Flyway.configure().dataSource(dataSource).target("17").load().migrate();
    }
    private void insertLegacyTeacher(DataSource dataSource, UUID teacherId) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO teacher_accounts (id,username_normalized,password_hash,display_name,school_name,role,status,must_change_password,failed_login_count,created_at,updated_at) VALUES (?,?,?,?,?,'TEACHER','ACTIVE',FALSE,0,?,?)")) {
            statement.setObject(1, teacherId); statement.setString(2, "legacy-" + teacherId.toString().substring(0, 8));
            statement.setString(3, "hash"); statement.setString(4, "旧教师"); statement.setString(5, "旧学校");
            statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now())); statement.setTimestamp(7, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }
    private long count(java.sql.Statement statement, String sql) throws Exception {
        try (var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }

    private DataSource newDatabase(String prefix) throws Exception {
        String databaseName = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
            statement.execute("create database " + databaseName);
        }
        return POSTGRES.getDatabase("postgres", databaseName);
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }
}

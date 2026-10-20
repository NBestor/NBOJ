package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * One-time destructive cleanup required by the school-tenant rollout.
 * It fails closed whenever legacy TEACHER rows exist without both a backup proof and the exact confirmation value.
 */
public class V18__controlled_legacy_teacher_purge extends BaseJavaMigration {
    private static final String CONFIRMATION = "DELETE_ALL_LEGACY_TEACHERS";
    private static final String PLATFORM_ID = "00000000-0000-0000-0000-000000000001";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        relaxLegacyRoleConstraint(connection);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE teacher_accounts SET role = 'PLATFORM_ADMIN' WHERE role = 'ADMIN'");
        }
        Set<String> teacherIds = strings(connection, "SELECT id FROM teacher_accounts WHERE role = 'TEACHER'");
        if (teacherIds.isEmpty()) return;

        String confirmation = setting("legacy.teacher.purge.confirmation", "LEGACY_TEACHER_PURGE_CONFIRMATION");
        String backupProof = setting("legacy.teacher.backup.proof", "LEGACY_TEACHER_BACKUP_PROOF");
        if (!CONFIRMATION.equals(confirmation) || backupProof == null || backupProof.isBlank()) {
            throw new FlywayException("Legacy teacher purge blocked: provide backup proof and confirmation DELETE_ALL_LEGACY_TEACHERS");
        }

        Set<String> classIds = stringsByIds(connection, "class_groups", "id", "owner_teacher_id", teacherIds);
        Set<String> studentIds = stringsByIds(connection, "student_profiles", "id", "class_group_id", classIds);
        Set<String> assignmentIds = stringsByIds(connection, "assignments", "id", "owner_teacher_id", teacherIds);
        Set<String> submissionIds = union(
                stringsByIds(connection, "submissions", "id", "assignment_id", assignmentIds),
                stringsByIds(connection, "submissions", "id", "student_profile_id", studentIds));
        Set<String> preservedProblems = problemIds(connection, teacherIds, true);
        Set<String> privateProblems = problemIds(connection, teacherIds, false);

        insertManifest(connection, teacherIds.size(), classIds.size(), studentIds.size(), assignmentIds.size(),
                submissionIds.size(), privateProblems.size(), preservedProblems.size(), sha256(backupProof));

        if (!preservedProblems.isEmpty()) {
            updateByIds(connection, "problems", "owner_teacher_id", PLATFORM_ID, "id", preservedProblems);
            updateByIds(connection, "problems", "reviewed_by", PLATFORM_ID, "id", preservedProblems);
        }

        deleteReferences(connection, submissionIds, List.of("submission_id", "source_submission_id",
                "current_submission_id", "first_seen_submission_id", "last_seen_submission_id",
                "previous_submission_id", "followup_submission_id"), Set.of("submissions"));
        deleteByIds(connection, "submissions", "id", submissionIds);
        deleteReferences(connection, studentIds, List.of("student_profile_id"), Set.of("student_profiles", "submissions"));
        deleteByIds(connection, "student_sessions", "student_profile_id", studentIds);
        deleteByIds(connection, "student_profiles", "id", studentIds);
        deleteReferences(connection, assignmentIds, List.of("assignment_id"), Set.of("assignments", "submissions"));
        deleteByIds(connection, "assignments", "id", assignmentIds);
        deleteReferences(connection, classIds, List.of("class_group_id"), Set.of("class_groups", "student_profiles", "assignments"));
        deleteByIds(connection, "class_groups", "id", classIds);
        clearProblemSourceReferences(connection, privateProblems);
        deleteReferences(connection, privateProblems, List.of("problem_id", "source_problem_id", "example_problem_id"),
                Set.of("problems", "submissions"));
        deleteByIds(connection, "problems", "id", privateProblems);

        deleteByIds(connection, "teacher_sessions", "teacher_id", teacherIds);
        deleteByIds(connection, "teacher_ai_quotas", "teacher_id", teacherIds);
        deleteByIds(connection, "ai_usage_events", "teacher_id", teacherIds);
        deleteByIds(connection, "platform_audit_events", "actor_teacher_id", teacherIds);
        Set<String> auditTargets = new LinkedHashSet<>(teacherIds);
        auditTargets.addAll(classIds); auditTargets.addAll(studentIds); auditTargets.addAll(assignmentIds);
        auditTargets.addAll(submissionIds); auditTargets.addAll(privateProblems);
        deleteAuditTargets(connection, auditTargets);
        deleteByIds(connection, "teacher_accounts", "id", teacherIds);
    }

    private Set<String> problemIds(Connection c, Set<String> teachers, boolean preserve) throws SQLException {
        if (!table(c, "problems") || teachers.isEmpty()) return Set.of();
        String condition = preserve
                ? "((scope = 'PUBLIC' OR scope = 'SHARED') AND version_state = 'PUBLISHED')"
                : "NOT ((scope = 'PUBLIC' OR scope = 'SHARED') AND version_state = 'PUBLISHED')";
        return queryIds(c, "SELECT id FROM problems WHERE owner_teacher_id IN (" + placeholders(teachers.size()) + ") AND " + condition, teachers);
    }

    private void deleteReferences(Connection c, Set<String> ids, List<String> columns, Set<String> excluded) throws SQLException {
        if (ids.isEmpty()) return;
        DatabaseMetaData metadata = c.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if (table == null || excluded.contains(table.toLowerCase(Locale.ROOT)) || table.toLowerCase(Locale.ROOT).startsWith("flyway_")) continue;
                for (String column : columns) if (column(c, table, column)) deleteByIds(c, table, column, ids);
            }
        }
    }

    private Set<String> stringsByIds(Connection c, String table, String result, String filter, Set<String> ids) throws SQLException {
        if (ids.isEmpty() || !table(c, table) || !column(c, table, filter)) return Set.of();
        return queryIds(c, "SELECT " + result + " FROM " + table + " WHERE " + filter + " IN (" + placeholders(ids.size()) + ")", ids);
    }

    private Set<String> strings(Connection c, String sql) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    private Set<String> queryIds(Connection c, String sql, Set<String> ids) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            bind(statement, ids);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
        }
        return result;
    }

    private void deleteByIds(Connection c, String table, String column, Set<String> ids) throws SQLException {
        if (ids.isEmpty() || !table(c, table) || !column(c, table, column)) return;
        try (PreparedStatement statement = c.prepareStatement("DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders(ids.size()) + ")")) {
            bind(statement, ids); statement.executeUpdate();
        }
    }

    private void updateByIds(Connection c, String table, String targetColumn, String value, String idColumn, Set<String> ids) throws SQLException {
        if (ids.isEmpty() || !column(c, table, targetColumn)) return;
        try (PreparedStatement statement = c.prepareStatement("UPDATE " + table + " SET " + targetColumn + " = ? WHERE " + idColumn + " IN (" + placeholders(ids.size()) + ")")) {
            statement.setObject(1, UUID.fromString(value)); int index = 2;
            for (String id : ids) statement.setObject(index++, typed(id));
            statement.executeUpdate();
        }
    }

    private void deleteAuditTargets(Connection c, Set<String> ids) throws SQLException {
        if (!table(c, "platform_audit_events")) return;
        try (PreparedStatement statement = c.prepareStatement("DELETE FROM platform_audit_events WHERE target_id IN (" + placeholders(ids.size()) + ")")) {
            int index = 1; for (String id : ids) statement.setString(index++, id); statement.executeUpdate();
        }
    }

    private void clearProblemSourceReferences(Connection c, Set<String> deletedProblemIds) throws SQLException {
        if (deletedProblemIds.isEmpty() || !table(c, "problems") || !column(c, "problems", "source_problem_id")) return;
        try (PreparedStatement statement = c.prepareStatement(
                "UPDATE problems SET source_problem_id = NULL WHERE source_problem_id IN (" +
                        placeholders(deletedProblemIds.size()) + ")")) {
            bind(statement, deletedProblemIds);
            statement.executeUpdate();
        }
    }

    private void insertManifest(Connection c, long teachers, long classes, long students, long assignments,
                                long submissions, long privateProblems, long preservedProblems, String proofHash) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("INSERT INTO legacy_teacher_purge_manifests " +
                "(teacher_count,class_count,student_count,assignment_count,submission_count,private_problem_count,preserved_problem_count,backup_proof_hash,created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setLong(1, teachers); statement.setLong(2, classes); statement.setLong(3, students);
            statement.setLong(4, assignments); statement.setLong(5, submissions); statement.setLong(6, privateProblems);
            statement.setLong(7, preservedProblems); statement.setString(8, proofHash); statement.setTimestamp(9, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private boolean table(Connection c, String table) throws SQLException {
        try (ResultSet rows = c.getMetaData().getTables(null, null, table.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            if (rows.next()) return true;
        }
        try (ResultSet rows = c.getMetaData().getTables(null, null, table.toLowerCase(Locale.ROOT), new String[]{"TABLE"})) { return rows.next(); }
    }
    private void relaxLegacyRoleConstraint(Connection c) throws SQLException {
        String database = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        List<String> constraints = new ArrayList<>();
        if (database.contains("h2")) {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT constraint_name FROM information_schema.table_constraints " +
                            "WHERE table_schema = current_schema AND table_name = 'TEACHER_ACCOUNTS' AND constraint_type = 'CHECK'");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) constraints.add(rows.getString(1));
            }
        } else if (database.contains("postgresql")) {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT con.conname FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid " +
                            "WHERE rel.relname = 'teacher_accounts' AND con.contype = 'c' " +
                            "AND rel.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = current_schema()) " +
                            "AND pg_get_constraintdef(con.oid) LIKE '%role%'");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) constraints.add(rows.getString(1));
            }
        }
        for (String constraint : constraints) {
            try (Statement statement = c.createStatement()) {
                statement.execute("ALTER TABLE teacher_accounts DROP CONSTRAINT " + quote(c, constraint));
            }
        }
    }
    private String quote(Connection c, String identifier) throws SQLException {
        String quote = c.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) return identifier;
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
    private boolean column(Connection c, String table, String column) throws SQLException {
        try (ResultSet rows = c.getMetaData().getColumns(null, null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            if (rows.next()) return true;
        }
        try (ResultSet rows = c.getMetaData().getColumns(null, null, table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT))) { return rows.next(); }
    }
    private String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private void bind(PreparedStatement statement, Set<String> ids) throws SQLException {
        int index = 1; for (String id : ids) statement.setObject(index++, typed(id));
    }
    private Object typed(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return Long.valueOf(value); }
    }
    private Set<String> union(Set<String> left, Set<String> right) { Set<String> result = new LinkedHashSet<>(left); result.addAll(right); return result; }
    private String setting(String property, String environment) {
        String value = System.getProperty(property); return value == null || value.isBlank() ? System.getenv(environment) : value;
    }
    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

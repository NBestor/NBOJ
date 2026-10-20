package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class AiStandardLibraryH2SchemaCompatibility implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (!isH2()) {
            return;
        }

        List<LayerConstraint> constraints = jdbcTemplate.query("""
                        select tc.constraint_name, cc.check_clause
                        from information_schema.table_constraints tc
                        join information_schema.check_constraints cc
                          on tc.constraint_name = cc.constraint_name
                         and tc.constraint_schema = cc.constraint_schema
                        where tc.table_name = 'AI_STANDARD_LIBRARY_ITEMS'
                          and upper(cc.check_clause) like '%LAYER%'
                        """,
                (rs, rowNum) -> new LayerConstraint(rs.getString("constraint_name"), rs.getString("check_clause")));

        for (LayerConstraint constraint : constraints) {
            if (!isOutdated(constraint.checkClause())) {
                continue;
            }
            jdbcTemplate.execute("alter table AI_STANDARD_LIBRARY_ITEMS drop constraint " + quoteIdentifier(constraint.name()));
            log.info("Dropped outdated H2 constraint {} on ai_standard_library_items.layer", constraint.name());
        }
    }

    private boolean isH2() {
        String productName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName()
        );
        return productName != null && productName.equalsIgnoreCase("H2");
    }

    private boolean isOutdated(String checkClause) {
        if (checkClause == null || checkClause.isBlank()) {
            return true;
        }
        return Arrays.stream(AiStandardLibraryLayer.values())
                .map(Enum::name)
                .anyMatch(value -> !checkClause.contains("'" + value + "'"));
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record LayerConstraint(String name, String checkClause) {
    }
}

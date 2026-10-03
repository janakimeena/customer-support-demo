package com.example.support.ingest.load;

import com.example.support.ingest.ImportCustomer;
import java.sql.Statement;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Load strategy that inserts new customers and updates changed ones with a <b>batched upsert</b>, keyed by
 * email (the natural key every source shares; our numeric ids mean nothing to the sources).
 *
 * <p><b>Why plain JDBC here, when the rest of the app uses JPA?</b> Saving entities one by one costs a SELECT
 * and an INSERT/UPDATE per row. A JDBC batch sends one prepared statement with many parameter sets in a single
 * round trip, and the upsert decides insert vs. update inside the database. The price: Hibernate is bypassed,
 * so this SQL must maintain {@code updated_at} and {@code version} (optimistic locking) itself.
 *
 * <p><b>Idempotent:</b> {@code WHEN MATCHED AND (something changed)} means re-running the same import changes
 * nothing; rows aren't rewritten and versions aren't bumped. That is what makes it safe to simply run an import
 * again after a failure.
 *
 * <p>{@code MERGE} is standard SQL (PostgreSQL 15+, and the H2 used by the tests). PostgreSQL's own
 * {@code INSERT ... ON CONFLICT (email) DO UPDATE ... WHERE ...} would work just as well there.
 *
 * <p>Each chunk is its own transaction: a failure loses at most one chunk, and earlier chunks stay committed.
 */
@Component
public class BatchUpsertWriter implements CustomerWriter {

    static final String UPSERT = """
            MERGE INTO customers c
            USING (VALUES (CAST(? AS VARCHAR(254)), CAST(? AS VARCHAR(100)), CAST(? AS VARCHAR(20)),
                           CAST(? AS TIMESTAMP WITH TIME ZONE)))
                  AS s (email, name, tier, ts)
            ON c.email = s.email
            WHEN MATCHED AND (c.name <> s.name OR c.tier <> s.tier) THEN
                UPDATE SET name = s.name, tier = s.tier, updated_at = s.ts, version = c.version + 1
            WHEN NOT MATCHED THEN
                INSERT (name, email, tier, created_at, updated_at) VALUES (s.name, s.email, s.tier, s.ts, s.ts)
            """;

    private final ChunkPlanner planner;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public BatchUpsertWriter(ChunkPlanner planner, JdbcTemplate jdbcTemplate, Clock clock) {
        this.planner = planner;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChunkResult write(List<ImportCustomer> chunk) {
        ChunkPlanner.Plan plan = planner.plan(chunk);
        List<ImportCustomer> changes = new ArrayList<>(plan.inserts());
        changes.addAll(plan.updates());
        if (changes.isEmpty()) {
            return new ChunkResult(0, 0, plan.unchanged(), plan.rejected());
        }

        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int[][] counts = jdbcTemplate.batchUpdate(UPSERT, changes, changes.size(), (statement, customer) -> {
            statement.setString(1, customer.email());
            statement.setString(2, customer.name());
            statement.setString(3, customer.tier().name());
            statement.setObject(4, now);
        });

        // One count per row: 1 = written, 0 = matched but identical (someone made the same change meanwhile).
        // Some drivers answer SUCCESS_NO_INFO for batches; count those as written.
        int inserted = 0;
        int updated = 0;
        int unchanged = plan.unchanged();
        for (int i = 0; i < changes.size(); i++) {
            int count = counts[0][i];
            if (count == 0) {
                unchanged++;
            } else if (count > 0 || count == Statement.SUCCESS_NO_INFO) {
                if (i < plan.inserts().size()) {
                    inserted++;
                } else {
                    updated++;
                }
            }
        }
        return new ChunkResult(inserted, updated, unchanged, plan.rejected());
    }
}

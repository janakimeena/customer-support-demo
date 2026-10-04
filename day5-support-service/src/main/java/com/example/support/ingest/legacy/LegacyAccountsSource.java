package com.example.support.ingest.legacy;

import com.example.support.ingest.CustomerSource;
import com.example.support.ingest.RawCustomer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <b>Adapter</b> over the old support system's {@code legacy_accounts} table: upper-case names, one-letter
 * plan codes, and closed accounts that must not be imported.
 *
 * <p>For the demo the table lives in our own database (see {@code V3_2__legacy_accounts.sql}). In real life
 * it would be another system's database with its own {@code DataSource}; only the {@link JdbcTemplate} passed
 * in would change.
 *
 * <p>{@code queryForStream} maps rows as they are read instead of building a list first. The stream holds a
 * JDBC connection until it is closed. (The PostgreSQL driver only fetches in batches of {@code fetchSize} when
 * auto-commit is off; otherwise it reads the whole result. Fine for this table, worth knowing for big ones.)
 */
public class LegacyAccountsSource implements CustomerSource {

    private static final String SQL = """
            SELECT acct_no, acct_name, contact_email, plan_code
            FROM legacy_accounts
            WHERE status = 'A'
            ORDER BY acct_no
            """;

    private final JdbcTemplate jdbcTemplate;

    public LegacyAccountsSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Stream<RawCustomer> read() {
        return jdbcTemplate.queryForStream(SQL, LegacyAccountsSource::adapt);
    }

    private static RawCustomer adapt(ResultSet row, int rowNum) throws SQLException {
        return new RawCustomer("legacy account " + row.getString("acct_no"),
                titleCase(row.getString("acct_name")), row.getString("contact_email"),
                tierFor(row.getString("plan_code")));
    }

    /** {@code P} / {@code S} → our tiers; anything else is passed through for the normalizer to reject. */
    static String tierFor(String planCode) {
        if (planCode == null) {
            return null;
        }
        return switch (planCode.trim()) {
            case "P" -> "PREMIUM";
            case "S" -> "STANDARD";
            default -> "plan code " + planCode.trim();
        };
    }

    /** {@code "MARY-JANE O'BRIEN"} → {@code "Mary-Jane O'Brien"}. */
    static String titleCase(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(name.length());
        boolean startOfWord = true;
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            result.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ' || c == '-' || c == '\'';
        }
        return result.toString();
    }
}

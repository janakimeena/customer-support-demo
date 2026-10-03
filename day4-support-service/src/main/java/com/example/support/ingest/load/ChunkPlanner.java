package com.example.support.ingest.load;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import com.example.support.ingest.ImportCustomer;
import com.example.support.ingest.Rejection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Compares a chunk with what is already stored, using one {@code IN (...)} query per chunk rather than one
 * query per row. Shared by both writers, so a dry run predicts exactly what a real run would do.
 *
 * <p>Also enforces {@code support.customer.max-customers}: ingestion bypasses {@code CustomerService}, so it
 * must apply the same business rule itself.
 */
@Component
public class ChunkPlanner {

    private final JdbcClient jdbc;
    private final CustomerProperties properties;

    public ChunkPlanner(JdbcClient jdbc, CustomerProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public Plan plan(List<ImportCustomer> chunk) {
        Map<String, Stored> stored = findStored(chunk);
        long room = Math.max(0, properties.maxCustomers() - currentCount());

        List<ImportCustomer> inserts = new ArrayList<>();
        List<ImportCustomer> updates = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        int unchanged = 0;
        for (ImportCustomer customer : chunk) {
            Stored existing = stored.get(customer.email());
            if (existing == null) {
                if (inserts.size() < room) {
                    inserts.add(customer);
                } else {
                    rejected.add(new Rejection(customer.origin(),
                            "customer limit of " + properties.maxCustomers() + " reached"));
                }
            } else if (existing.differsFrom(customer)) {
                updates.add(customer);
            } else {
                unchanged++;
            }
        }
        return new Plan(inserts, updates, unchanged, rejected);
    }

    private Map<String, Stored> findStored(List<ImportCustomer> chunk) {
        Map<String, Stored> stored = new HashMap<>();
        jdbc.sql("SELECT email, name, tier FROM customers WHERE email IN (:emails)")
                .param("emails", chunk.stream().map(ImportCustomer::email).toList())
                .query((row, rowNum) -> stored.put(row.getString("email"),
                        new Stored(row.getString("name"), Customer.Tier.valueOf(row.getString("tier")))))
                .list();
        return stored;
    }

    private long currentCount() {
        return jdbc.sql("SELECT count(*) FROM customers").query(Long.class).single();
    }

    public record Plan(List<ImportCustomer> inserts, List<ImportCustomer> updates, int unchanged,
                       List<Rejection> rejected) {
    }

    private record Stored(String name, Customer.Tier tier) {

        boolean differsFrom(ImportCustomer customer) {
            return !name.equals(customer.name()) || tier != customer.tier();
        }
    }
}

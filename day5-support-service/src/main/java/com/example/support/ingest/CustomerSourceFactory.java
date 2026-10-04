package com.example.support.ingest;

import com.example.support.ingest.IngestionProperties.SourceDefinition;
import com.example.support.ingest.crm.CrmClient;
import com.example.support.ingest.crm.CrmCustomerSource;
import com.example.support.ingest.csv.CsvCustomerSource;
import com.example.support.ingest.legacy.LegacyAccountsSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * <b>Factory</b>: turns a source definition from configuration into a ready-to-use {@link CustomerSource}.
 *
 * <p>The ingestion service only ever asks "give me the source called X". Which class that is, and what it
 * needs to be constructed (a resource loader, an HTTP client with a base URL, a JdbcTemplate), is decided
 * here and nowhere else. Adding a new kind of source means one new enum constant, one new class and one new
 * {@code case}; the compiler flags this {@code switch} until the new constant is handled.
 */
@Component
public class CustomerSourceFactory {

    private final ResourceLoader resourceLoader;
    private final RestClient.Builder restClientBuilder;
    private final JdbcTemplate jdbcTemplate;

    public CustomerSourceFactory(
            ResourceLoader resourceLoader, RestClient.Builder restClientBuilder, JdbcTemplate jdbcTemplate) {
        this.resourceLoader = resourceLoader;
        this.restClientBuilder = restClientBuilder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomerSource create(SourceDefinition definition) {
        return switch (definition.type()) {
            case CSV -> new CsvCustomerSource(resourceLoader.getResource(requireLocation(definition)));
            // RestClient.Builder is a prototype-style bean: clone() it before customizing, so other users of
            // the shared builder don't inherit this base URL.
            case CRM_API -> new CrmCustomerSource(
                    new CrmClient(restClientBuilder.clone().baseUrl(requireLocation(definition)).build()));
            case LEGACY_DB -> new LegacyAccountsSource(jdbcTemplate);
        };
    }

    private static String requireLocation(SourceDefinition definition) {
        if (definition.location() == null || definition.location().isBlank()) {
            throw new IllegalArgumentException(definition.type() + " source needs a location");
        }
        return definition.location();
    }
}

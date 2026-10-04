package com.example.support.ingest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code support.ingest.*}. Sources are defined in configuration, not passed in by API callers: a client can
 * only pick a source by name, so it can't make the server read an arbitrary file or call an arbitrary URL.
 *
 * <pre>
 * support:
 *   ingest:
 *     chunk-size: 100
 *     sources:
 *       east-csv: { type: CSV, location: classpath:sample-data/customers-east.csv }
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "support.ingest")
public record IngestionProperties(
        @Min(1) @Max(1000) @DefaultValue("100") int chunkSize,
        @Min(0) @DefaultValue("50") int maxReportedRejections,
        @NotNull @Valid @DefaultValue Map<String, SourceDefinition> sources) {

    public record SourceDefinition(@NotNull SourceType type, String location) {
    }
}

package com.example.support.ingest;

import com.example.support.ingest.IngestionProperties.SourceDefinition;
import com.example.support.ingest.load.BatchUpsertWriter;
import com.example.support.ingest.load.CustomerWriter;
import com.example.support.ingest.load.DryRunWriter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The ETL pipeline: <b>Extract</b> from a {@link CustomerSource}, <b>Transform</b> with the
 * {@link CustomerNormalizer}, <b>Load</b> in chunks through a {@link CustomerWriter}.
 *
 * <p>Every pattern of the day meets here, and this class depends on none of the concrete classes behind them:
 * <ul>
 *   <li>the {@link CustomerSourceFactory} (Factory) builds the source from configuration;</li>
 *   <li>the source (Strategy, often an Adapter) could be a file, an API or a database;</li>
 *   <li>the writer (Strategy) is chosen per run: real upsert or dry run;</li>
 *   <li>the {@link IngestionReport.Builder} (Builder) collects the outcome as the run progresses.</li>
 * </ul>
 *
 * <p>Records stream through one at a time and are written in chunks of {@code support.ingest.chunk-size}, so
 * memory use doesn't depend on the size of the source. Bad records are reported and skipped, not fatal.
 * Not {@code @Transactional}: each chunk commits on its own (see {@link BatchUpsertWriter}).
 */
@Service
public class CustomerIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CustomerIngestionService.class);

    private final IngestionProperties properties;
    private final CustomerSourceFactory sourceFactory;
    private final CustomerNormalizer normalizer;
    private final CustomerWriter upsertWriter;
    private final CustomerWriter dryRunWriter;
    private final Clock clock;

    public CustomerIngestionService(IngestionProperties properties, CustomerSourceFactory sourceFactory,
            CustomerNormalizer normalizer, BatchUpsertWriter upsertWriter, DryRunWriter dryRunWriter, Clock clock) {
        this.properties = properties;
        this.sourceFactory = sourceFactory;
        this.normalizer = normalizer;
        this.upsertWriter = upsertWriter;
        this.dryRunWriter = dryRunWriter;
        this.clock = clock;
    }

    /** Configured sources, by name. */
    public Map<String, SourceType> sources() {
        Map<String, SourceType> sources = new TreeMap<>();
        properties.sources().forEach((name, definition) -> sources.put(name, definition.type()));
        return sources;
    }

    public IngestionReport run(String sourceName, boolean dryRun) {
        SourceDefinition definition = Optional.ofNullable(properties.sources().get(sourceName))
                .orElseThrow(() -> new UnknownSourceException(sourceName));
        CustomerWriter writer = dryRun ? dryRunWriter : upsertWriter;
        IngestionReport.Builder report = IngestionReport.builder(sourceName, definition.type(), clock)
                .dryRun(dryRun)
                .maxReportedRejections(properties.maxReportedRejections());

        // email -> origin of its first occurrence in this run
        Map<String, String> seen = new HashMap<>();
        List<ImportCustomer> chunk = new ArrayList<>(properties.chunkSize());

        try (Stream<RawCustomer> records = open(definition)) {
            Iterator<RawCustomer> iterator = records.iterator();
            RawCustomer raw;
            while ((raw = next(iterator)) != null) {
                report.recordRead();
                switch (normalizer.normalize(raw)) {
                    case Rejection rejection -> report.reject(rejection);
                    case ImportCustomer customer -> {
                        String first = seen.putIfAbsent(customer.email(), customer.origin());
                        if (first != null) {
                            report.reject(new Rejection(customer.origin(), "duplicate email, first seen at " + first));
                        } else {
                            chunk.add(customer);
                        }
                    }
                }
                if (chunk.size() == properties.chunkSize()) {
                    report.add(writer.write(chunk));
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                report.add(writer.write(chunk));
            }
        } catch (SourceUnavailableException ex) {
            IngestionReport failed = report.failed(ex.getMessage());
            log.warn("Ingestion from '{}' failed after {} records: {}", sourceName, failed.read(), failed.error());
            throw new IngestionFailedException(failed, ex);
        }

        IngestionReport completed = report.completed();
        log.info("Ingestion from '{}' {}: read={} inserted={} updated={} unchanged={} rejected={} ({} ms)",
                sourceName, dryRun ? "(dry run) completed" : "completed", completed.read(), completed.inserted(),
                completed.updated(), completed.unchanged(), completed.rejected(), completed.durationMs());
        return completed;
    }

    private Stream<RawCustomer> open(SourceDefinition definition) {
        CustomerSource source = sourceFactory.create(definition);
        try {
            return source.read();
        } catch (SourceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SourceUnavailableException("Cannot open source: " + ex.getMessage(), ex);
        }
    }

    /**
     * Next record, or {@code null} at the end. Any failure while <em>reading</em> (I/O, HTTP, JDBC) becomes a
     * {@link SourceUnavailableException}; failures while writing are not caught here and keep their own type.
     */
    private static RawCustomer next(Iterator<RawCustomer> iterator) {
        try {
            return iterator.hasNext() ? iterator.next() : null;
        } catch (SourceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SourceUnavailableException("Source failed while reading: " + ex.getMessage(), ex);
        }
    }
}

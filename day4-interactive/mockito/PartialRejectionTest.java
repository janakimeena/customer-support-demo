package com.example.support.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import com.example.support.ingest.IngestionProperties.SourceDefinition;
import com.example.support.ingest.load.BatchUpsertWriter;
import com.example.support.ingest.load.ChunkResult;
import com.example.support.ingest.load.DryRunWriter;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Checkpoint 9: Mockito exercise (session 1:05-1:30).
 *
 * Copy this file to day4-support-service/src/test/java/com/example/support/ingest/ and run:
 *     ./mvnw test -Dtest=PartialRejectionTest
 * Both tests fail until you finish them. The setup is already done (same as CustomerIngestionServiceTest).
 *
 * PREDICT  Run it BEFORE doing TODO 1. The first test fails with a NullPointerException, not "TODO". Why?
 * ASK      1. Why is the normalizer real while the source and the writers are mocks?
 *          2. MockitoExtension uses strict stubs. What happens if you stub dryRunWriter but never use it?
 */
@ExtendWith(MockitoExtension.class)
class PartialRejectionTest {

    private static final SourceDefinition CSV = new SourceDefinition(SourceType.CSV, "classpath:x.csv");

    @Mock
    CustomerSourceFactory factory;

    @Mock
    CustomerSource source;

    @Mock
    BatchUpsertWriter upsertWriter;

    @Mock
    DryRunWriter dryRunWriter;

    private CustomerIngestionService service;

    @BeforeEach
    void setUp() {
        // chunk size 2, list at most 1 rejection in the report
        var properties = new IngestionProperties(2, 1, Map.of("east-csv", CSV));
        var normalizer = new CustomerNormalizer(Validation.buildDefaultValidatorFactory().getValidator(),
                new CustomerProperties(100, Customer.Tier.STANDARD));
        service = new CustomerIngestionService(properties, factory, normalizer, upsertWriter, dryRunWriter,
                Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneOffset.UTC));
    }

    /**
     * The source delivers 2 valid customers (one chunk). The writer inserts one and rejects the other
     * because the customer limit is reached. The run must still COMPLETE and count both outcomes.
     */
    @Test
    void aChunkTheWriterPartlyRejectsStillCountsTheRest() {
        when(factory.create(CSV)).thenReturn(source);
        when(source.read()).thenReturn(Stream.of(raw(1, "a"), raw(2, "b")));
        // TODO 1: stub upsertWriter.write(anyList()) to return a ChunkResult with 1 inserted and
        //         one Rejection("f:2", "customer limit of 100 reached")

        IngestionReport report = service.run("east-csv", false);

        // TODO 2: assert status COMPLETED, read 2, inserted 1, rejected 1,
        //         and that the listed rejection's origin is "f:2"
        fail("TODO: finish this test");
    }

    /**
     * Stretch: the source throws while delivering its 3rd record. Chunk 1 (records 1 and 2) was already
     * written. Expect an IngestionFailedException whose report says FAILED, read 2, inserted 2.
     */
    @Test
    void sourceFailingMidwayReportsWhatWasAlreadyLoaded() {
        // TODO 3: build a Stream that returns raw(1, "a"), raw(2, "b") and then throws
        //         new java.io.UncheckedIOException(new java.io.IOException("connection reset"))
        //         Hint: Stream.of(1, 2, 3).map(i -> { if (i == 3) throw ...; return raw(i, "c" + i); })
        // TODO 4: stub the factory, the source and the writer; then use assertThatThrownBy(...)
        //         .isInstanceOfSatisfying(IngestionFailedException.class, ex -> ...)
        fail("TODO: finish this test");
    }

    private static RawCustomer raw(int line, String user) {
        return new RawCustomer("f:" + line, "Customer " + user, user + "@example.com", "STANDARD");
    }
}

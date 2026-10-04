package com.example.support.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import com.example.support.ingest.IngestionProperties.SourceDefinition;
import com.example.support.ingest.load.BatchUpsertWriter;
import com.example.support.ingest.load.ChunkResult;
import com.example.support.ingest.load.DryRunWriter;
import jakarta.validation.Validation;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The pipeline in isolation. Factory, source and both writers are Mockito mocks, so these tests check only
 * the orchestration: chunking, de-duplication, strategy choice, report contents and failure handling. The
 * normalizer is real: it's a pure function, and mocking it would just repeat its logic here.
 */
@ExtendWith(MockitoExtension.class)
class CustomerIngestionServiceTest {

    private static final SourceDefinition CSV = new SourceDefinition(SourceType.CSV, "classpath:x.csv");

    @Mock
    CustomerSourceFactory factory;

    @Mock
    CustomerSource source;

    @Mock
    BatchUpsertWriter upsertWriter;

    @Mock
    DryRunWriter dryRunWriter;

    /** Captures the argument of every write() call. */
    @Captor
    ArgumentCaptor<List<ImportCustomer>> chunks;

    private CustomerIngestionService service;

    @BeforeEach
    void setUp() {
        var properties = new IngestionProperties(2, 1, Map.of("east-csv", CSV));
        var normalizer = new CustomerNormalizer(Validation.buildDefaultValidatorFactory().getValidator(),
                new CustomerProperties(100, Customer.Tier.STANDARD));
        service = new CustomerIngestionService(properties, factory, normalizer, upsertWriter, dryRunWriter,
                Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void writesInChunksAndAddsUpTheReport() {
        givenSourceReturns(raw(1, "a"), raw(2, "b"), raw(3, "c"), raw(4, "d"), raw(5, "e"));
        // Copy the list on each call: the service reuses (clears) its chunk buffer after write() returns.
        List<List<ImportCustomer>> written = new ArrayList<>();
        when(upsertWriter.write(anyList())).thenAnswer(call -> {
            written.add(List.copyOf(call.getArgument(0)));
            return new ChunkResult(1, 1, 0, List.of());
        });

        IngestionReport report = service.run("east-csv", false);

        assertThat(written).extracting(List::size).containsExactly(2, 2, 1);
        assertThat(written.getFirst()).extracting(ImportCustomer::email).containsExactly("a@example.com", "b@example.com");
        assertThat(report.status()).isEqualTo(IngestionReport.Status.COMPLETED);
        assertThat(report.read()).isEqualTo(5);
        assertThat(report.inserted()).isEqualTo(3);
        assertThat(report.updated()).isEqualTo(3);
        verifyNoInteractions(dryRunWriter);
    }

    @Test
    void dryRunUsesTheDryRunStrategyOnly() {
        givenSourceReturns(raw(1, "a"));
        when(dryRunWriter.write(anyList())).thenReturn(new ChunkResult(1, 0, 0, List.of()));

        IngestionReport report = service.run("east-csv", true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.inserted()).isEqualTo(1);
        verify(upsertWriter, never()).write(any());
    }

    @Test
    void invalidAndDuplicateRecordsAreReportedNotWritten() {
        givenSourceReturns(raw(1, "a"), new RawCustomer("f:2", "", "bad", null), raw(3, "A"));
        when(upsertWriter.write(chunks.capture())).thenReturn(new ChunkResult(1, 0, 0, List.of()));

        IngestionReport report = service.run("east-csv", false);

        verify(upsertWriter, times(1)).write(any());
        assertThat(chunks.getValue()).extracting(ImportCustomer::origin).containsExactly("f:1");
        assertThat(report.rejected()).isEqualTo(2);
        // max-reported-rejections = 1: the count is complete, the list is capped.
        assertThat(report.rejections()).hasSize(1);
        assertThat(report.rejections().getFirst().origin()).isEqualTo("f:2");
    }

    @Test
    void sourceFailureKeepsWhatWasLoadedAndClosesTheSource() {
        AtomicBoolean closed = new AtomicBoolean();
        Stream<RawCustomer> failing = Stream.of(1, 2, 3)
                .map(i -> {
                    if (i == 3) {
                        throw new UncheckedIOException(new java.io.IOException("connection reset"));
                    }
                    return raw(i, "c" + i);
                })
                .onClose(() -> closed.set(true));
        when(factory.create(CSV)).thenReturn(source);
        when(source.read()).thenReturn(failing);
        when(upsertWriter.write(anyList())).thenReturn(new ChunkResult(2, 0, 0, List.of()));

        assertThatThrownBy(() -> service.run("east-csv", false))
                .isInstanceOfSatisfying(IngestionFailedException.class, ex -> {
                    assertThat(ex.getReport().status()).isEqualTo(IngestionReport.Status.FAILED);
                    assertThat(ex.getReport().inserted()).isEqualTo(2);
                    assertThat(ex.getReport().error()).contains("connection reset");
                });
        assertThat(closed).isTrue();
    }

    @Test
    void unknownSourceIsNotFound() {
        assertThatThrownBy(() -> service.run("nope", false)).isInstanceOf(UnknownSourceException.class);
        verifyNoInteractions(factory);
    }

    private void givenSourceReturns(RawCustomer... records) {
        when(factory.create(CSV)).thenReturn(source);
        when(source.read()).thenReturn(Stream.of(records));
    }

    private static RawCustomer raw(int line, String user) {
        return new RawCustomer("f:" + line, "Customer " + user, user + "@example.com", "STANDARD");
    }
}

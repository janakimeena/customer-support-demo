package com.example.support.ingest;

import com.example.support.ingest.load.ChunkResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of one ingestion run, returned by the API. Immutable; assembled step by step with a {@link Builder}.
 *
 * @param read       records the source delivered (accepted + rejected)
 * @param rejections the first {@code support.ingest.max-reported-rejections} rejections; {@code rejected} is
 *                   the full count
 */
public record IngestionReport(
        String source,
        SourceType type,
        boolean dryRun,
        Status status,
        String error,
        Instant startedAt,
        long durationMs,
        int read,
        int inserted,
        int updated,
        int unchanged,
        int rejected,
        List<Rejection> rejections) {

    public enum Status { COMPLETED, FAILED }

    public static Builder builder(String source, SourceType type, Clock clock) {
        return new Builder(source, type, clock);
    }

    /**
     * <b>Builder</b>. A report has thirteen fields, most of which are counters that grow while the run is in
     * progress. Instead of a thirteen-argument constructor call at the end (easy to mix up two {@code int}s),
     * the service feeds events into the builder as they happen and calls {@link #build} once. The builder also
     * owns the small rules: timing, capping the rejection list, and the status.
     *
     * <p>Not thread-safe: one builder belongs to one run.
     */
    public static final class Builder {

        private final String source;
        private final SourceType type;
        private final Clock clock;
        private final Instant startedAt;
        private boolean dryRun;
        private int maxReportedRejections = Integer.MAX_VALUE;
        private int read;
        private int inserted;
        private int updated;
        private int unchanged;
        private int rejected;
        private final List<Rejection> rejections = new ArrayList<>();

        private Builder(String source, SourceType type, Clock clock) {
            this.source = source;
            this.type = type;
            this.clock = clock;
            this.startedAt = clock.instant();
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder maxReportedRejections(int max) {
            this.maxReportedRejections = max;
            return this;
        }

        public Builder recordRead() {
            read++;
            return this;
        }

        public Builder reject(Rejection rejection) {
            rejected++;
            if (rejections.size() < maxReportedRejections) {
                rejections.add(rejection);
            }
            return this;
        }

        public Builder add(ChunkResult chunk) {
            inserted += chunk.inserted();
            updated += chunk.updated();
            unchanged += chunk.unchanged();
            chunk.rejected().forEach(this::reject);
            return this;
        }

        public IngestionReport completed() {
            return build(Status.COMPLETED, null);
        }

        public IngestionReport failed(String error) {
            return build(Status.FAILED, error);
        }

        private IngestionReport build(Status status, String error) {
            long durationMs = Duration.between(startedAt, clock.instant()).toMillis();
            return new IngestionReport(source, type, dryRun, status, error, startedAt, durationMs,
                    read, inserted, updated, unchanged, rejected, List.copyOf(rejections));
        }
    }
}

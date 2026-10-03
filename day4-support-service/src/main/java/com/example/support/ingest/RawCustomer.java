package com.example.support.ingest;

/**
 * One customer as a source delivered it: plain strings in our field names, but not yet cleaned or validated.
 * Every source, whatever its own format, is adapted into this shape, so the rest of the pipeline only ever
 * deals with one type.
 *
 * @param origin where the record came from, for error reports (e.g. {@code customers-east.csv:4},
 *               {@code crm contact 7001})
 * @param tier   our tier vocabulary ({@code STANDARD}/{@code PREMIUM}), any case; blank means "use the default"
 */
public record RawCustomer(String origin, String name, String email, String tier) {
}

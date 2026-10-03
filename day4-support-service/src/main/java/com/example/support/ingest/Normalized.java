package com.example.support.ingest;

/**
 * Result of normalizing one {@link RawCustomer}: either a clean {@link ImportCustomer} or a {@link Rejection}.
 * Sealed like Day 1's {@code TicketParseResult}, so a pattern-matching {@code switch} over it is exhaustive.
 */
public sealed interface Normalized permits ImportCustomer, Rejection {
}

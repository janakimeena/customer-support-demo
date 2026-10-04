package com.example.support.ingest;

/** The kinds of source the {@link CustomerSourceFactory} knows how to build. */
public enum SourceType {
    /** A CSV file; {@code location} is a Spring resource such as {@code classpath:...} or {@code file:...}. */
    CSV,
    /** The partner CRM's paged JSON API; {@code location} is its base URL. */
    CRM_API,
    /** The {@code legacy_accounts} table of the old support system; no {@code location}. */
    LEGACY_DB
}

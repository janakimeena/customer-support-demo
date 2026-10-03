package com.example.support.ingest;

import java.util.stream.Stream;

/**
 * <b>Strategy</b> for the Extract step. The pipeline doesn't know or care whether customers come from a file,
 * an HTTP API or another database; each source is one interchangeable implementation of this interface.
 *
 * <p>{@link #read()} returns a lazy {@link Stream}, so a large source is never loaded into memory at once.
 * The stream may hold a file handle, an HTTP connection or a JDBC connection, so callers must close it
 * (try-with-resources).
 */
@FunctionalInterface
public interface CustomerSource {

    /**
     * @throws SourceUnavailableException if the source can't be opened or fails while being read
     */
    Stream<RawCustomer> read();
}

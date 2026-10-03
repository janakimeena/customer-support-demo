package com.example.support.ingest.load;

import com.example.support.ingest.Rejection;
import java.util.List;

/** What happened to one chunk. {@code rejected} holds rows the writer refused, e.g. over the customer limit. */
public record ChunkResult(int inserted, int updated, int unchanged, List<Rejection> rejected) {

    public ChunkResult {
        rejected = List.copyOf(rejected);
    }
}

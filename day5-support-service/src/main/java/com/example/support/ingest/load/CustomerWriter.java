package com.example.support.ingest.load;

import com.example.support.ingest.ImportCustomer;
import java.util.List;

/**
 * <b>Strategy</b> for the Load step. The ingestion service picks one per run: {@link BatchUpsertWriter} writes
 * to the database, {@link DryRunWriter} only reports what would happen. The pipeline code is identical either
 * way; only the strategy object differs.
 */
public interface CustomerWriter {

    /** Loads one chunk. The emails in a chunk are unique (the service removes duplicates first). */
    ChunkResult write(List<ImportCustomer> chunk);
}

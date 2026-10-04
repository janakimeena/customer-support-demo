package com.example.support.ingest.load;

import com.example.support.ingest.ImportCustomer;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Load strategy that writes nothing: it reports what {@link BatchUpsertWriter} would do with the chunk. */
@Component
public class DryRunWriter implements CustomerWriter {

    private final ChunkPlanner planner;

    public DryRunWriter(ChunkPlanner planner) {
        this.planner = planner;
    }

    @Override
    @Transactional(readOnly = true)
    public ChunkResult write(List<ImportCustomer> chunk) {
        ChunkPlanner.Plan plan = planner.plan(chunk);
        return new ChunkResult(plan.inserts().size(), plan.updates().size(), plan.unchanged(), plan.rejected());
    }
}

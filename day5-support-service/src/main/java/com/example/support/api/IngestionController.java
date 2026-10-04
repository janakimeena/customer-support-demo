package com.example.support.api;

import com.example.support.ingest.CustomerIngestionService;
import com.example.support.ingest.IngestionReport;
import com.example.support.ingest.SourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts customer imports. Callers choose a configured source by name; they can't pass a file path or URL.
 * A run is synchronous, which is fine for demo-sized sources. A real system would start a background job
 * and return {@code 202 Accepted} with a link to poll.
 */
@Tag(name = "Ingestion", description = "Import customers from CSV files, the partner CRM API and the legacy database")
@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final CustomerIngestionService ingestionService;

    public IngestionController(CustomerIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/sources")
    public List<SourceSummary> sources() {
        return ingestionService.sources().entrySet().stream()
                .map(entry -> new SourceSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Operation(summary = "Import customers from one source",
            description = "Upserts by email: new customers are inserted, changed ones updated, identical ones left "
                    + "alone, so running the same import twice is safe. With dryRun=true nothing is written.")
    @PostMapping("/sources/{name}/runs")
    public IngestionReport run(@PathVariable String name, @RequestParam(defaultValue = "false") boolean dryRun) {
        return ingestionService.run(name, dryRun);
    }

    public record SourceSummary(String name, SourceType type) {
    }
}

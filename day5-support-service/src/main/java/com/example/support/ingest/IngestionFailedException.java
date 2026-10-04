package com.example.support.ingest;

/**
 * A run stopped part-way because its source failed. Carries the partial report: chunks loaded before the
 * failure stay committed, and since loading is idempotent the fix is simply to run the import again.
 */
public class IngestionFailedException extends RuntimeException {

    private final IngestionReport report;

    public IngestionFailedException(IngestionReport report, Throwable cause) {
        super(report.error(), cause);
        this.report = report;
    }

    public IngestionReport getReport() {
        return report;
    }
}

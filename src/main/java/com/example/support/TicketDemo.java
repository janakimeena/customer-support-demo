package com.example.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Day 1: process ticket files concurrently using platform or virtual threads. */
public final class TicketDemo {
    private TicketDemo() {
    }

    public static void main(String[] args) throws IOException {
        DemoOptions options = DemoOptions.from(args);
        List<Path> files;
        try (var paths = Files.list(options.inputDirectory())) {
            files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        if (files.isEmpty()) {
            System.out.println("No .csv ticket files found in " + options.inputDirectory());
            return;
        }

        System.out.printf("Processing %d ticket files with %s threads...%n",
                files.size(), options.executorMode());

        long startedAt = System.nanoTime();
        try (ExecutorService executor = createExecutor(options)) {
            List<CompletableFuture<TicketFileProcessor.FileReport>> tasks = files.stream()
                    .map(file -> CompletableFuture.supplyAsync(
                            () -> TicketFileProcessor.process(file), executor))
                    .toList();

            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            List<TicketFileProcessor.FileReport> reports = tasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
            printReports(reports, System.nanoTime() - startedAt);
        }
    }

    private static ExecutorService createExecutor(DemoOptions options) {
        return switch (options.executorMode()) {
            case FIXED -> Executors.newFixedThreadPool(
                    options.workers(),
                    Thread.ofPlatform().name("ticket-worker-", 0).factory());
            case VIRTUAL -> Executors.newVirtualThreadPerTaskExecutor();
        };
    }

    private static void printReports(
            List<TicketFileProcessor.FileReport> reports, long elapsedNanos) {
        int accepted = 0;
        int rejected = 0;

        for (TicketFileProcessor.FileReport report : reports) {
            System.out.printf("\n%s | thread=%s | file-time=%d ms%n",
                    report.file(), report.workerThread(), report.elapsed().toMillis());
            for (TicketParseResult result : report.results()) {
                switch (result) {
                    case TicketParseResult.Accepted item -> {
                        accepted++;
                        SupportTicket ticket = item.ticket();
                        System.out.printf("  OK   line %d | #%d | %-7s | %s%n",
                                item.lineNumber(), ticket.id(), ticket.status(), ticket.subject());
                    }
                    case TicketParseResult.Rejected item -> {
                        rejected++;
                        System.out.printf("  BAD  line %d | %s%n",
                                item.lineNumber(), item.reason());
                    }
                }
            }
        }

        System.out.printf("\nSummary: %d accepted, %d rejected, total %d ms%n",
                accepted, rejected, elapsedNanos / 1_000_000);
    }

    private record DemoOptions(Path inputDirectory, ExecutorMode executorMode, int workers) {
        private static DemoOptions from(String[] args) {
            Path inputDirectory = Path.of("data", "tickets");
            ExecutorMode mode = ExecutorMode.FIXED;
            int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));

            for (String arg : args) {
                if (arg.startsWith("--executor=")) {
                    mode = ExecutorMode.valueOf(
                            arg.substring("--executor=".length()).toUpperCase());
                } else if (arg.startsWith("--workers=")) {
                    workers = Integer.parseInt(arg.substring("--workers=".length()));
                    if (workers < 1) {
                        throw new IllegalArgumentException("--workers must be at least 1");
                    }
                } else if (!arg.startsWith("--")) {
                    inputDirectory = Path.of(arg);
                } else {
                    throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }

            return new DemoOptions(inputDirectory, mode, workers);
        }
    }

    private enum ExecutorMode {
        FIXED,
        VIRTUAL
    }
}

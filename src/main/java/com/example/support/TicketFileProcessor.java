package com.example.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Reads and validates all ticket rows in one CSV file. */
public final class TicketFileProcessor {
    private TicketFileProcessor() {
    }

    public static FileReport process(Path file) {
        long startedAt = System.nanoTime();
        List<TicketParseResult> results = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Ticket file is empty: " + file);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!line.isBlank()) {
                    results.add(parseRow(line, file, lineNumber));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not process " + file, exception);
        }

        return new FileReport(
                file,
                List.copyOf(results),
                Duration.ofNanos(System.nanoTime() - startedAt),
                Thread.currentThread().getName());
    }

    static TicketParseResult parseRow(String rawLine, Path sourceFile, int lineNumber) {
        try {
            List<String> fields = parseCsvRecord(rawLine);
            if (fields.size() != 5) {
                throw new IllegalArgumentException(
                        "Expected 5 columns but found " + fields.size());
            }

            SupportTicket ticket = new SupportTicket(
                    Long.parseLong(fields.get(0).trim()),
                    fields.get(1),
                    fields.get(2),
                    SupportTicket.Status.valueOf(fields.get(3).trim().toUpperCase()),
                    Instant.parse(fields.get(4).trim()));
            return new TicketParseResult.Accepted(ticket, sourceFile, lineNumber);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            return new TicketParseResult.Rejected(
                    rawLine, sourceFile, lineNumber, exception.getMessage());
        }
    }

    /** Parses a single RFC-4180-style record, including quoted commas and escaped quotes. */
    private static List<String> parseCsvRecord(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (current == ',' && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quoted field");
        }
        fields.add(field.toString());
        return fields;
    }

    public record FileReport(
            Path file,
            List<TicketParseResult> results,
            Duration elapsed,
            String workerThread) {
        public FileReport {
            results = List.copyOf(results);
        }
    }
}

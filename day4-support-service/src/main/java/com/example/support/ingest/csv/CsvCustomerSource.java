package com.example.support.ingest.csv;

import com.example.support.ingest.CustomerSource;
import com.example.support.ingest.RawCustomer;
import com.example.support.ingest.SourceUnavailableException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.Resource;

/**
 * Reads customers from a CSV file with a header row containing {@code name}, {@code email} and, optionally,
 * {@code tier} (any column order, any header case, extra columns ignored).
 *
 * <p>Day 1 parsed CSV by hand to show the mechanics; here Apache Commons CSV handles quoting, escaped quotes
 * and line breaks inside quoted fields. Rows are streamed: the file is never loaded into memory as a whole.
 */
public class CsvCustomerSource implements CustomerSource {

    private static final List<String> REQUIRED_COLUMNS = List.of("name", "email");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()                 // take column names from the first row ...
            .setSkipHeaderRecord(true)   // ... and don't return that row as data
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    private final Resource resource;

    public CsvCustomerSource(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Stream<RawCustomer> read() {
        CSVParser parser = open();
        String file = resource.getFilename();
        return parser.stream()
                .map(row -> new RawCustomer(
                        file + ":" + parser.getCurrentLineNumber(),
                        column(row, "name"), column(row, "email"), column(row, "tier")))
                .onClose(() -> close(parser));
    }

    private CSVParser open() {
        Reader reader = null;
        try {
            reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            CSVParser parser = CSVParser.parse(reader, FORMAT);
            List<String> missing = REQUIRED_COLUMNS.stream()
                    .filter(column -> !parser.getHeaderMap().containsKey(column))
                    .toList();
            if (!missing.isEmpty()) {
                parser.close();
                throw new SourceUnavailableException(
                        resource.getDescription() + " is missing required columns " + missing, null);
            }
            return parser;
        } catch (IOException | UncheckedIOException ex) {
            closeQuietly(reader);
            throw new SourceUnavailableException("Cannot read " + resource.getDescription(), ex);
        }
    }

    /** A short row (fewer fields than the header) yields {@code null} instead of an exception. */
    private static String column(CSVRecord row, String name) {
        return row.isMapped(name) && row.isSet(name) ? row.get(name) : null;
    }

    private static void close(CSVParser parser) {
        try {
            parser.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void closeQuietly(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // already failing; the original exception is the one worth reporting
            }
        }
    }
}

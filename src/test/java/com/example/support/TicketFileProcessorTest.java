package com.example.support;

import java.nio.file.Path;

/** Dependency-free smoke tests; run with java -ea. */
public final class TicketFileProcessorTest {
    private TicketFileProcessorTest() {
    }

    public static void main(String[] args) {
        parsesAValidTicketAndQuotedComma();
        rejectsAnInvalidStatus();
        rejectsAnUnterminatedQuotedField();
        System.out.println("All TicketFileProcessor tests passed.");
    }

    private static void parsesAValidTicketAndQuotedComma() {
        TicketParseResult result = TicketFileProcessor.parseRow(
                "101,C-100,\"Refund, please\",OPEN,2026-09-30T09:15:00Z",
                Path.of("tickets.csv"), 2);

        assert result instanceof TicketParseResult.Accepted;
        var accepted = (TicketParseResult.Accepted) result;
        assert accepted.ticket().subject().equals("Refund, please");
        assert accepted.ticket().id() == 101;
    }

    private static void rejectsAnInvalidStatus() {
        TicketParseResult result = TicketFileProcessor.parseRow(
                "102,C-101,Login issue,UNKNOWN,2026-09-30T09:15:00Z",
                Path.of("tickets.csv"), 3);

        assert result instanceof TicketParseResult.Rejected;
    }

    private static void rejectsAnUnterminatedQuotedField() {
        TicketParseResult result = TicketFileProcessor.parseRow(
                "103,C-102,\"Never closed,OPEN,2026-09-30T09:15:00Z",
                Path.of("tickets.csv"), 4);

        assert result instanceof TicketParseResult.Rejected;
    }
}

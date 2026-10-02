package com.example.support;

import java.nio.file.Path;

/** Sealed outcome for parsing one CSV row. */
public sealed interface TicketParseResult
        permits TicketParseResult.Accepted, TicketParseResult.Rejected {

    Path sourceFile();

    int lineNumber();

    record Accepted(SupportTicket ticket, Path sourceFile, int lineNumber)
            implements TicketParseResult {
    }

    record Rejected(String rawLine, Path sourceFile, int lineNumber, String reason)
            implements TicketParseResult {
    }
}

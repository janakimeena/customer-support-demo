package com.example.support;

import java.time.Instant;
import java.util.Objects;

/** Immutable representation of a support ticket. */
public record SupportTicket(
        long id,
        String customerId,
        String subject,
        Status status,
        Instant createdAt) {

    public SupportTicket {
        if (id <= 0) {
            throw new IllegalArgumentException("Ticket id must be positive");
        }
        customerId = requireText(customerId, "customerId");
        subject = requireText(subject, "subject");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public enum Status {
        OPEN,
        PENDING,
        CLOSED
    }
}

package com.example.support.customer.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable customer. Ids use the {@code C-<number>} format referenced by Day 1 support tickets.
 */
public record Customer(
        String id,
        String name,
        String email,
        Tier tier,
        Instant createdAt) {

    public Customer {
        id = requireText(id, "id");
        name = requireText(name, "name");
        email = normalizeEmail(email);
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Customer withDetails(String newName, String newEmail, Tier newTier) {
        return new Customer(id, newName, newEmail, newTier, createdAt);
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "email").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public enum Tier {
        STANDARD,
        PREMIUM
    }
}

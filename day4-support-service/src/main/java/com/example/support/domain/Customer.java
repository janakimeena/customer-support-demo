package com.example.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * JPA entity mapped to the {@code customers} table created by Flyway (V1).
 *
 * <p>Unlike the Day 2 record, an entity is a mutable class: JPA needs a no-arg constructor, tracks changes to
 * managed instances and writes them on commit ("dirty checking"), so updating a customer is just calling
 * {@link #update} inside a transaction. The entity never leaves the service layer; the API uses DTOs.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tier tier;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected Customer() {
    }

    public Customer(String name, String email, Tier tier, Instant now) {
        this.name = requireText(name, "name");
        this.email = normalizeEmail(email);
        this.tier = Objects.requireNonNull(tier, "tier");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void update(String newName, String newEmail, Tier newTier, Instant now) {
        this.name = requireText(newName, "name");
        this.email = normalizeEmail(newEmail);
        this.tier = Objects.requireNonNull(newTier, "tier");
        this.updatedAt = Objects.requireNonNull(now, "now");
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

    public Long getId() {
        return id;
    }

    /** The id as the API shows it, e.g. {@code C-100}. */
    public String getPublicId() {
        return CustomerIds.format(id);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Tier getTier() {
        return tier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** Entities are equal when they represent the same row; a new (unsaved) entity is only equal to itself. */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Customer that && id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Customer.class.hashCode();
    }

    public enum Tier {
        STANDARD,
        PREMIUM
    }
}

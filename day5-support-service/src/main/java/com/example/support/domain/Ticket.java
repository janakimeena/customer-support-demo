package com.example.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * A support ticket, mapped to {@code tickets} (Flyway V2).
 *
 * <p>The relationship is unidirectional, ticket → customer: {@code Customer} has no {@code List<Ticket>}, so
 * loading a customer can never drag in thousands of tickets. Queries that need both sides use a join.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY: loading a ticket does not load its customer until {@code getCustomer().getName()} is called.
     * (The JPA default for {@code @ManyToOne} is EAGER, which quietly adds a query or join everywhere.)
     * Repository methods that need the customer fetch it in the same query (join fetch / entity graph).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected Ticket() {
    }

    public Ticket(Customer customer, String subject, TicketPriority priority, Instant now) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        this.customer = Objects.requireNonNull(customer, "customer");
        this.subject = subject.trim();
        this.priority = Objects.requireNonNull(priority, "priority");
        this.status = TicketStatus.OPEN;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** Returns the previous status. */
    public TicketStatus changeStatus(TicketStatus newStatus, Instant now) {
        TicketStatus previous = status;
        this.status = Objects.requireNonNull(newStatus, "newStatus");
        this.updatedAt = Objects.requireNonNull(now, "now");
        return previous;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getSubject() {
        return subject;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
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

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Ticket that && id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Ticket.class.hashCode();
    }
}

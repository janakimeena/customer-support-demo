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
import java.time.Instant;
import java.util.Objects;

/** One row of a ticket's audit trail. Immutable once written. */
@Entity
@Table(name = "ticket_status_changes")
public class TicketStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    /** {@code null} for the row written when the ticket is created. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TicketStatus toStatus;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    /** For JPA only. */
    protected TicketStatusChange() {
    }

    public TicketStatusChange(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus, Instant changedAt) {
        this.ticket = Objects.requireNonNull(ticket, "ticket");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}

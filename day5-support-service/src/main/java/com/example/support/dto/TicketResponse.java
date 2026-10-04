package com.example.support.dto;

import com.example.support.domain.Ticket;
import com.example.support.domain.TicketPriority;
import com.example.support.domain.TicketStatus;
import java.time.Instant;

/**
 * Flattens ticket + customer into one JSON object. {@code customerName} comes from the joined customer, so
 * the repository must fetch it together with the ticket (see {@code TicketRepository}).
 */
public record TicketResponse(
        long id,
        String customerId,
        String customerName,
        String subject,
        TicketStatus status,
        TicketPriority priority,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getCustomer().getPublicId(),
                ticket.getCustomer().getName(), ticket.getSubject(), ticket.getStatus(), ticket.getPriority(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }
}

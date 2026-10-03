package com.example.support.dto;

import com.example.support.domain.TicketStatus;
import com.example.support.domain.TicketStatusChange;
import java.time.Instant;

public record TicketStatusChangeResponse(TicketStatus fromStatus, TicketStatus toStatus, Instant changedAt) {

    public static TicketStatusChangeResponse from(TicketStatusChange change) {
        return new TicketStatusChangeResponse(change.getFromStatus(), change.getToStatus(), change.getChangedAt());
    }
}

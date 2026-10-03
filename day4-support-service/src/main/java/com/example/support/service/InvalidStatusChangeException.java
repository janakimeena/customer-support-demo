package com.example.support.service;

import com.example.support.domain.TicketStatus;

public class InvalidStatusChangeException extends ConflictException {

    public InvalidStatusChangeException(long ticketId, TicketStatus status) {
        super("Ticket " + ticketId + " is already " + status);
    }
}

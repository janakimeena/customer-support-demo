package com.example.support.service;

public class TicketNotFoundException extends NotFoundException {

    public TicketNotFoundException(long id) {
        super("Ticket " + id + " was not found");
    }

    @Override
    public String getTitle() {
        return "Ticket not found";
    }
}

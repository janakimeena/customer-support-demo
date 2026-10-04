package com.example.support.service;

public class CustomerHasTicketsException extends ConflictException {

    public CustomerHasTicketsException(String id) {
        super("Customer " + id + " still has tickets and cannot be deleted");
    }
}

package com.example.support.customer.service;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(String id) {
        super("Customer " + id + " was not found");
    }
}

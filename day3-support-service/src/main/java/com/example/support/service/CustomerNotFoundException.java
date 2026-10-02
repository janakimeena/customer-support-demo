package com.example.support.service;

public class CustomerNotFoundException extends NotFoundException {

    public CustomerNotFoundException(String id) {
        super("Customer " + id + " was not found");
    }

    @Override
    public String getTitle() {
        return "Customer not found";
    }
}

package com.example.support.service;

public class CustomerLimitExceededException extends ConflictException {

    public CustomerLimitExceededException(int maxCustomers) {
        super("Customer limit of " + maxCustomers + " reached");
    }
}

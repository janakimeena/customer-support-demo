package com.example.support.customer.service;

public class CustomerLimitExceededException extends RuntimeException {

    public CustomerLimitExceededException(int maxCustomers) {
        super("Customer limit of " + maxCustomers + " reached");
    }
}

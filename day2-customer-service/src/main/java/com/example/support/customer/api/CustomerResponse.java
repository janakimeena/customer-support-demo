package com.example.support.customer.api;

import com.example.support.customer.domain.Customer;
import java.time.Instant;

/** API representation, kept separate so the domain model can change without breaking clients. */
public record CustomerResponse(
        String id,
        String name,
        String email,
        Customer.Tier tier,
        Instant createdAt) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id(), customer.name(), customer.email(), customer.tier(), customer.createdAt());
    }
}

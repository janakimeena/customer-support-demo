package com.example.support.dto;

import com.example.support.domain.Customer;
import java.time.Instant;

/** API view of a customer. The entity's {@code version} and numeric key stay internal. */
public record CustomerResponse(
        String id,
        String name,
        String email,
        Customer.Tier tier,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getPublicId(), customer.getName(), customer.getEmail(),
                customer.getTier(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}

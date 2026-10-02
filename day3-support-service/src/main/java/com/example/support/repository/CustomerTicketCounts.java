package com.example.support.repository;

import com.example.support.domain.Customer;

/** Row of {@link CustomerRepository#findTicketCounts}; filled by the JPQL constructor expression. */
public record CustomerTicketCounts(
        Long customerId,
        String name,
        Customer.Tier tier,
        Long totalTickets,
        Long openTickets) {
}

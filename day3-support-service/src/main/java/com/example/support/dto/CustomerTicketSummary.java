package com.example.support.dto;

import com.example.support.domain.Customer;
import com.example.support.domain.CustomerIds;
import com.example.support.repository.CustomerTicketCounts;

public record CustomerTicketSummary(
        String customerId,
        String name,
        Customer.Tier tier,
        long totalTickets,
        long openTickets) {

    public static CustomerTicketSummary from(CustomerTicketCounts row) {
        return new CustomerTicketSummary(CustomerIds.format(row.customerId()), row.name(), row.tier(),
                row.totalTickets(), row.openTickets());
    }
}

package com.example.support.dto;

import com.example.support.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for opening a ticket. {@code priority} is optional and defaults to NORMAL. */
public record TicketRequest(
        @NotNull @ValidCustomerId String customerId,
        @NotBlank @Size(max = 200) String subject,
        TicketPriority priority) {
}

package com.example.support.dto;

import com.example.support.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusRequest(@NotNull TicketStatus status) {
}

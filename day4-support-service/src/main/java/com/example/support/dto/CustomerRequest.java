package com.example.support.dto;

import com.example.support.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for create and update. {@code tier} is optional. The size limits match the column lengths in
 * V1__create_customers.sql, so bad input is rejected with a 400 before it can reach the database.
 */
public record CustomerRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        Customer.Tier tier) {
}

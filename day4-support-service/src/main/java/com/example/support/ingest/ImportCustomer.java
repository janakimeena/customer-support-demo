package com.example.support.ingest;

import com.example.support.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A cleaned, validated customer, ready to be loaded. The constraints match {@code CustomerRequest} and the
 * column sizes in V1, so an imported customer obeys exactly the same rules as one created through the API.
 */
public record ImportCustomer(
        String origin,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Customer.Tier tier) implements Normalized {
}

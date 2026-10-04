package com.example.support.config;

import com.example.support.domain.Customer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding of the {@code support.customer.*} properties (unchanged from Day 2).
 * Invalid values fail at startup because of {@code @Validated}.
 */
@Validated
@ConfigurationProperties(prefix = "support.customer")
public record CustomerProperties(
        @Min(1) @DefaultValue("1000") int maxCustomers,
        @NotNull @DefaultValue("STANDARD") Customer.Tier defaultTier) {
}

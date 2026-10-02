package com.example.support.customer.config;

import com.example.support.customer.domain.Customer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding of the {@code support.customer.*} properties. Values come from application.yml and
 * can be overridden per profile (application-dev.yml, application-prod.yml), by environment variables
 * (SUPPORT_CUSTOMER_MAX_CUSTOMERS) or by command-line arguments (--support.customer.max-customers=5).
 * Invalid values fail at startup because of {@code @Validated}.
 */
@Validated
@ConfigurationProperties(prefix = "support.customer")
public record CustomerProperties(
        @Min(1) @DefaultValue("1000") int maxCustomers,
        @NotNull @DefaultValue("STANDARD") Customer.Tier defaultTier) {
}

package com.example.support.ingest;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The Transform step: clean up a {@link RawCustomer} and validate it. Source-specific quirks were already
 * handled by the adapters, so these rules apply to every source alike:
 *
 * <ul>
 *   <li>name: trimmed, runs of whitespace collapsed ({@code "  Ben   Carter "} → {@code "Ben Carter"})</li>
 *   <li>email: trimmed and lower-cased, as {@code Customer} does (the unique constraint relies on it)</li>
 *   <li>tier: case-insensitive; blank means the configured default tier; anything else is rejected</li>
 * </ul>
 *
 * A pure function apart from the injected validator, which makes it easy to unit test.
 */
@Component
public class CustomerNormalizer {

    private final Validator validator;
    private final Customer.Tier defaultTier;

    public CustomerNormalizer(Validator validator, CustomerProperties properties) {
        this.validator = validator;
        this.defaultTier = properties.defaultTier();
    }

    public Normalized normalize(RawCustomer raw) {
        Customer.Tier tier;
        try {
            tier = isBlank(raw.tier()) ? defaultTier : Customer.Tier.valueOf(raw.tier().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return new Rejection(raw.origin(), "tier: unknown value '" + raw.tier().trim() + "'");
        }

        ImportCustomer candidate = new ImportCustomer(raw.origin(), cleanName(raw.name()), cleanEmail(raw.email()), tier);
        var violations = validator.validate(candidate);
        if (!violations.isEmpty()) {
            return new Rejection(raw.origin(), describe(violations));
        }
        return candidate;
    }

    private static String cleanName(String name) {
        return name == null ? null : name.strip().replaceAll("\\s+", " ");
    }

    private static String cleanEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** {@code "email: must be a well-formed email address; name: must not be blank"}, in a stable order. */
    private static String describe(java.util.Set<ConstraintViolation<ImportCustomer>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("; "));
    }
}

package com.example.support.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

/** Plain JUnit: the Transform step is a pure function of its input, so no Spring and no mocks are needed. */
class CustomerNormalizerTest {

    private final CustomerNormalizer normalizer = new CustomerNormalizer(
            Validation.buildDefaultValidatorFactory().getValidator(),
            new CustomerProperties(100, Customer.Tier.STANDARD));

    @Test
    void cleansNameEmailAndTier() {
        Normalized result = normalizer.normalize(new RawCustomer("f:2", "  Ben   Carter ", " BEN.Carter@Example.COM ", "premium "));

        assertThat(result).isEqualTo(new ImportCustomer("f:2", "Ben Carter", "ben.carter@example.com", Customer.Tier.PREMIUM));
    }

    @Test
    void blankTierFallsBackToConfiguredDefault() {
        Normalized result = normalizer.normalize(new RawCustomer("f:3", "Hiro Tanaka", "hiro@example.com", ""));

        assertThat(result).isInstanceOfSatisfying(ImportCustomer.class,
                customer -> assertThat(customer.tier()).isEqualTo(Customer.Tier.STANDARD));
    }

    @Test
    void unknownTierIsRejectedNotGuessed() {
        assertThat(normalizer.normalize(new RawCustomer("f:6", "Jon Park", "jon@example.com", "GOLD")))
                .isEqualTo(new Rejection("f:6", "tier: unknown value 'GOLD'"));
    }

    @Test
    void reportsEveryViolationInAStableOrder() {
        assertThat(normalizer.normalize(new RawCustomer("f:5", " ", "not-an-email", null)))
                .isEqualTo(new Rejection("f:5", "email: must be a well-formed email address; name: must not be blank"));
    }

    @Test
    void enforcesColumnLengths() {
        assertThat(normalizer.normalize(new RawCustomer("f:9", "x".repeat(101), "x@example.com", null)))
                .isInstanceOfSatisfying(Rejection.class,
                        rejection -> assertThat(rejection.reason()).startsWith("name: size must be between"));
    }
}

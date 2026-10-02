package com.example.support.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.customer.config.CustomerProperties;
import com.example.support.customer.domain.Customer;
import com.example.support.customer.repository.InMemoryCustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Plain unit test: because of constructor injection, no Spring container is needed. */
class CustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(
                new InMemoryCustomerRepository(),
                new CustomerProperties(2, Customer.Tier.STANDARD),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createAssignsIdTimestampDefaultTierAndNormalizesEmail() {
        Customer created = service.create(" Asha Rao ", "Asha.Rao@Example.com", null);

        assertThat(created.id()).isEqualTo("C-100");
        assertThat(created.name()).isEqualTo("Asha Rao");
        assertThat(created.email()).isEqualTo("asha.rao@example.com");
        assertThat(created.tier()).isEqualTo(Customer.Tier.STANDARD);
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsDuplicateEmailIgnoringCase() {
        service.create("Asha", "asha@example.com", null);

        assertThatThrownBy(() -> service.create("Other", "ASHA@example.com", null))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void createEnforcesConfiguredLimit() {
        service.create("A", "a@example.com", null);
        service.create("B", "b@example.com", null);

        assertThatThrownBy(() -> service.create("C", "c@example.com", null))
                .isInstanceOf(CustomerLimitExceededException.class);
    }

    @Test
    void updateKeepsTierWhenNotProvidedAndAllowsOwnEmail() {
        Customer created = service.create("Asha", "asha@example.com", Customer.Tier.PREMIUM);

        Customer updated = service.update(created.id(), "Asha R", "asha@example.com", null);

        assertThat(updated.name()).isEqualTo("Asha R");
        assertThat(updated.tier()).isEqualTo(Customer.Tier.PREMIUM);
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void updateRejectsEmailOwnedByAnotherCustomer() {
        service.create("Asha", "asha@example.com", null);
        Customer ben = service.create("Ben", "ben@example.com", null);

        assertThatThrownBy(() -> service.update(ben.id(), "Ben", "asha@example.com", null))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void findAndDeleteUnknownIdThrowNotFound() {
        assertThatThrownBy(() -> service.findById("C-999")).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> service.delete("C-999")).isInstanceOf(CustomerNotFoundException.class);
    }
}

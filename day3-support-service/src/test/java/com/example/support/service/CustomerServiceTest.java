package com.example.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import com.example.support.dto.CustomerRequest;
import com.example.support.dto.CustomerResponse;
import com.example.support.repository.CustomerRepository;
import com.example.support.repository.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Plain unit test, no Spring and no database: the repositories are Mockito mocks. Fast, and focused on the
 * business rules. (Constructor injection is what makes this possible.)
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Mock
    CustomerRepository customers;

    @Mock
    TicketRepository tickets;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customers, tickets, new CustomerProperties(2, Customer.Tier.STANDARD),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesInputAndAppliesDefaultTier() {
        when(customers.save(any(Customer.class))).thenAnswer(call -> withId(call.getArgument(0), 100L));

        CustomerResponse created = service.create(new CustomerRequest(" Asha Rao ", "Asha.Rao@Example.com", null));

        assertThat(created.id()).isEqualTo("C-100");
        assertThat(created.name()).isEqualTo("Asha Rao");
        assertThat(created.email()).isEqualTo("asha.rao@example.com");
        assertThat(created.tier()).isEqualTo(Customer.Tier.STANDARD);
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsDuplicateEmailIgnoringCase() {
        when(customers.findByEmail("asha@example.com")).thenReturn(Optional.of(customer(100L, "asha@example.com")));

        assertThatThrownBy(() -> service.create(new CustomerRequest("Other", "ASHA@example.com", null)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(customers, never()).save(any());
    }

    @Test
    void createEnforcesConfiguredLimit() {
        when(customers.count()).thenReturn(2L);

        assertThatThrownBy(() -> service.create(new CustomerRequest("C", "c@example.com", null)))
                .isInstanceOf(CustomerLimitExceededException.class);
    }

    @Test
    void updateChangesManagedEntityAndKeepsTierWhenOmitted() {
        Customer asha = customer(100L, "asha@example.com");
        when(customers.findById(100L)).thenReturn(Optional.of(asha));
        when(customers.findByEmail("asha@example.com")).thenReturn(Optional.of(asha));

        CustomerResponse updated = service.update("C-100", new CustomerRequest("Asha R", "asha@example.com", null));

        assertThat(updated.name()).isEqualTo("Asha R");
        assertThat(updated.tier()).isEqualTo(Customer.Tier.PREMIUM);
        // No save(): JPA dirty checking writes the change when the transaction commits.
        verify(customers, never()).save(any());
    }

    @Test
    void deleteRefusesCustomerWithTickets() {
        when(customers.findById(100L)).thenReturn(Optional.of(customer(100L, "asha@example.com")));
        when(tickets.existsByCustomerId(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete("C-100")).isInstanceOf(CustomerHasTicketsException.class);
        verify(customers, never()).delete(any());
    }

    @Test
    void malformedOrUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.findById("nope")).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> service.findById("C-999")).isInstanceOf(CustomerNotFoundException.class);
    }

    private static Customer customer(long id, String email) {
        return withId(new Customer("Asha Rao", email, Customer.Tier.PREMIUM, NOW), id);
    }

    /** Simulates the id the database would generate. */
    private static Customer withId(Customer customer, long id) {
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }
}

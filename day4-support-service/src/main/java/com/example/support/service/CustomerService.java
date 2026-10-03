package com.example.support.service;

import com.example.support.config.CustomerProperties;
import com.example.support.domain.Customer;
import com.example.support.domain.CustomerIds;
import com.example.support.dto.CustomerRequest;
import com.example.support.dto.CustomerResponse;
import com.example.support.dto.CustomerTicketSummary;
import com.example.support.repository.CustomerRepository;
import com.example.support.repository.TicketRepository;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for customers.
 *
 * <p><b>Transactions.</b> The class-level {@code @Transactional(readOnly = true)} gives every public method a
 * read-only transaction (Hibernate skips dirty checking; the driver may route to a replica). Methods that
 * write override it with plain {@code @Transactional}: all their SQL commits together or not at all, and a
 * {@code RuntimeException} rolls everything back. Spring applies this through a proxy, so it only works on
 * calls that come from another bean (controller → service), not on {@code this.method()} calls.
 *
 * <p>Entities never leave this class: methods return DTOs, mapped while the transaction is still open.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final TicketRepository tickets;
    private final CustomerProperties properties;
    private final Clock clock;

    public CustomerService(
            CustomerRepository customers, TicketRepository tickets, CustomerProperties properties, Clock clock) {
        this.customers = customers;
        this.tickets = tickets;
        this.properties = properties;
        this.clock = clock;
    }

    /** All customers, or those whose name contains {@code nameQuery} (case-insensitive). */
    public Page<CustomerResponse> search(String nameQuery, Pageable pageable) {
        Page<Customer> page = nameQuery == null || nameQuery.isBlank()
                ? customers.findAll(pageable)
                : customers.findByNameContainingIgnoreCase(nameQuery.trim(), pageable);
        return page.map(CustomerResponse::from);
    }

    public CustomerResponse findById(String id) {
        return CustomerResponse.from(load(id));
    }

    public Page<CustomerTicketSummary> ticketSummary(Pageable pageable) {
        return customers.findTicketCounts(pageable).map(CustomerTicketSummary::from);
    }

    /** Creates a customer; a {@code null} tier falls back to the configured default tier. */
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (customers.count() >= properties.maxCustomers()) {
            throw new CustomerLimitExceededException(properties.maxCustomers());
        }
        ensureEmailAvailable(request.email(), null);
        Customer.Tier tier = request.tier() != null ? request.tier() : properties.defaultTier();
        Customer saved = customers.save(new Customer(request.name(), request.email(), tier, clock.instant()));
        return CustomerResponse.from(saved);
    }

    /**
     * Replaces name and email; a {@code null} tier keeps the current tier. There is no {@code save()} call:
     * the entity is managed, so Hibernate detects the change and issues the UPDATE on commit.
     */
    @Transactional
    public CustomerResponse update(String id, CustomerRequest request) {
        Customer customer = load(id);
        ensureEmailAvailable(request.email(), customer.getId());
        Customer.Tier tier = request.tier() != null ? request.tier() : customer.getTier();
        customer.update(request.name(), request.email(), tier, clock.instant());
        return CustomerResponse.from(customer);
    }

    /** Refuses to delete a customer who still has tickets (the foreign key would refuse anyway). */
    @Transactional
    public void delete(String id) {
        Customer customer = load(id);
        if (tickets.existsByCustomerId(customer.getId())) {
            throw new CustomerHasTicketsException(customer.getPublicId());
        }
        customers.delete(customer);
    }

    /**
     * Friendly 409 for the common case. Two concurrent requests can both pass this check, so the unique
     * constraint {@code uk_customers_email} is the real guarantee; its violation also becomes a 409
     * (see {@code ApiExceptionHandler}).
     */
    private void ensureEmailAvailable(String email, Long ownerId) {
        customers.findByEmail(Customer.normalizeEmail(email))
                .filter(other -> !other.getId().equals(ownerId))
                .ifPresent(other -> {
                    throw new DuplicateEmailException(other.getEmail());
                });
    }

    private Customer load(String id) {
        return CustomerIds.parse(id)
                .flatMap(customers::findById)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
